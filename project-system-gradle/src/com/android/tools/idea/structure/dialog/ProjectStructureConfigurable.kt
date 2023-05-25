/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.structure.dialog

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.structure.configurables.ui.CrossModuleUiStateComponent
import com.google.common.collect.Maps
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.ui.JBSplitter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.navigation.BackAction
import com.intellij.ui.navigation.ForwardAction
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place
import com.intellij.util.EventDispatcher
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.util.EventListener
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class ProjectStructureConfigurable(private val myProject: Project) : SearchableConfigurable, Place.Navigator, Configurable.NoMargin,
                                                                     Configurable.NoScroll {
  private var myHistory = History(this)
  private val myDetails = Wrapper()
  private val myConfigurables = Maps.newLinkedHashMap<Configurable, JComponent>()
  private val myUiState = UIState().also { it.load(myProject) }
  private val myEmptySelection = JLabel(
    "<html><body><center>Select a setting to view or edit its details here</center></body></html>",
    SwingConstants.CENTER
  )
  private val myProjectStructureEventDispatcher = EventDispatcher.create(ProjectStructureListener::class.java)

  private var mySplitter: JBSplitter? = null
  private var mySidePanel: SidePanel? = null
  private var myToolbarComponent: JComponent? = null
  private var myErrorsComponent: JBLabel? = null
  private var myToFocus: JComponent? = null

  private var myUiInitialized: Boolean = false
  private var myShowing = false

  private var mySelectedConfigurable: Configurable? = null

  private var myDisposable = MyDisposable()
  private var myOpenTimeMs: Long = 0
  private var inDoOK = false
  private var needsSync = false

  override fun getPreferredFocusedComponent(): JComponent? = myToFocus

  override fun setHistory(history: History) {
    myHistory = history
  }

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback {
    val displayName = place?.getPath(CATEGORY_NAME) as? String ?: return ActionCallback.REJECTED

    val toSelect = findConfigurable(displayName)

    var detailsContent: JComponent? = myDetails.targetComponent

    if (mySelectedConfigurable !== toSelect) {
      (mySelectedConfigurable as? MasterDetailsComponent)?.saveSideProportion()
      removeSelected()
    }

    if (toSelect != null) {
      (toSelect as? CrossModuleUiStateComponent)?.restoreUiState()
      detailsContent = myConfigurables[toSelect]
      if (detailsContent == null) {
        detailsContent = toSelect.createComponent()
        myConfigurables[toSelect] = detailsContent
      }
      myDetails.setContent(detailsContent)
      myUiState.lastEditedConfigurable = toSelect.displayName

      myProject.logUsageLeftNavigateTo(toSelect)
    }
    mySelectedConfigurable = toSelect

    if (toSelect is MasterDetailsComponent) {
      val masterDetails = toSelect as MasterDetailsComponent?
      if (myUiState.sideProportion > 0) {
        masterDetails!!.splitter.proportion = myUiState.sideProportion
      }
      masterDetails!!.setHistory(myHistory)
    }
    else if (toSelect is Place.Navigator) {
      toSelect.setHistory(myHistory)
    }

    if (toSelect != null) {
      mySidePanel!!.select(createPlaceFor(toSelect))
    }

    var toFocus: JComponent? = if (mySelectedConfigurable == null) null else mySelectedConfigurable!!.preferredFocusedComponent
    if (toFocus == null && mySelectedConfigurable is MasterDetailsComponent) {
      toFocus = (mySelectedConfigurable as MasterDetailsComponent).master
    }

    if (toFocus == null && detailsContent != null) {
      toFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(detailsContent)
      if (toFocus == null) {
        toFocus = detailsContent
      }
    }
    myToFocus = toFocus
    if (myToFocus != null) {
      @Suppress("DEPRECATION")
      if (requestFocus) UIUtil.requestFocus(myToFocus!!)
    }

    val result = ActionCallback()
    Place.goFurther(toSelect, place, requestFocus).notifyWhenDone(result)

    myDetails.revalidate()
    myDetails.repaint()

    if (!myHistory.isNavigatingNow && mySelectedConfigurable != null) {
      myHistory.pushQueryPlace()
    }

    return result
  }

  private fun MasterDetailsComponent.saveSideProportion() {
    myUiState.sideProportion = this.splitter.proportion
  }

  private fun removeSelected() {
    myDetails.removeAll()
    mySelectedConfigurable = null
    myUiState.lastEditedConfigurable = null

    myDetails.add(myEmptySelection, BorderLayout.CENTER)
  }

  override fun queryPlace(place: Place) {
    mySelectedConfigurable?.let {
      place.putPath(CATEGORY_NAME, it.displayName)
      Place.queryFurther(it, place)
    }
  }

  override fun getId(): String = "android.project.structure"

  @Nls
  override fun getDisplayName(): String = if (myProject.isDefault) "Default Project Structure" else JavaUiBundle.message("project.settings.display.name")

  override fun getHelpTopic(): String? = mySelectedConfigurable?.helpTopic

  override fun createComponent(): JComponent? {
    val component = MyPanel()
    mySplitter = OnePixelSplitter(false, .17f)
    mySplitter!!.setHonorComponentsMinimumSize(true)

    initSidePanel()

    val left = object : JPanel(BorderLayout()) {
      override fun getMinimumSize(): Dimension {
        val original = super.getMinimumSize()
        return Dimension(Math.max(original.width, JBUI.scale(150)), original.height)
      }
    }

    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(BackAction(component, myDisposable))
    toolbarGroup.add(ForwardAction(component, myDisposable))

    val toolbar = ActionManager.getInstance().createActionToolbar("AndroidProjectStructure", toolbarGroup, true)
    toolbar.setTargetComponent(component)
    myToolbarComponent = toolbar.component

    left.background = UIUtil.SIDE_PANEL_BACKGROUND
    myToolbarComponent!!.background = UIUtil.SIDE_PANEL_BACKGROUND

    left.add(myToolbarComponent!!, BorderLayout.NORTH)
    left.add(mySidePanel!!, BorderLayout.CENTER)

    mySplitter!!.firstComponent = left
    mySplitter!!.secondComponent = myDetails

    component.add(mySplitter!!, BorderLayout.CENTER)
    myErrorsComponent = JBLabel() // TODO(solodkyy): Configure for (multi-line?) HTML.
    component.add(myErrorsComponent!!, BorderLayout.SOUTH)

    myUiInitialized = true

    return component
  }

  fun showPlace(place: Place?) {
    // TODO(IDEA-196602):  Pressing Ctrl+Alt+S or Ctrl+Alt+Shift+S for a little longer shows tens of dialogs. Remove when fixed.
    if (myShowing) return
    if (!canShowPsdOrWarnUser(myProject)) return
    myOpenTimeMs = System.currentTimeMillis()
    logUsageOpen()
    needsSync = false
    myShowing = true
    try {
      showDialog(Runnable {
        if (place != null) {
          navigateTo(place, true)
        }
      })
    }
    finally {
      myShowing = false
    }
    if (needsSync) {
      // NOTE: If the user applied the changes in the dialog and then cancelled the dialog, sync still needs to happen here since
      //       we do not perform a sync when applying changes on "apply".
      GradleSyncInvoker.getInstance().requestProjectSync(myProject, GradleSyncStats.Trigger.TRIGGER_PSD_CHANGES)
    }
  }

  fun show() = showPlace(null)

  private fun showDialog(advanceInit: Runnable) {
    val dialog = object : SettingsDialog(myProject, "#PSD", this, true, false) {
      override fun doOKAction() {
        inDoOK = true
        try {
          super.doOKAction()
        }
        finally {
          inDoOK = false
        }
      }

      override fun doCancelAction() {
        // Ask for confirmation to close on ESC with not apllied changes.
        if (IdeEventQueue.getInstance().trueCurrentEvent.safeAs<KeyEvent>()?.keyCode == KeyEvent.VK_ESCAPE) {
          if (isModified) {
            if (Messages.showDialog(
                myProject,
                "You have made changes that have not been applied.",
                "Discard changes?",
                arrayOf("Discard changes", "Continue editing"),
                0,
                UIUtil.getQuestionIcon()
              ) != 0) {
              return
            }
          }
        }
        super.doCancelAction()
      }

      init {
        contentPanel.preferredSize = Dimension(JBUI.scale(950), JBUI.scale(500))
        contentPanel.minimumSize = Dimension(JBUI.scale(900), JBUI.scale(400))
      }
    }
    UiNotifyConnector.Once.installOn(dialog.contentPane, object : Activatable {
      override fun showNotify() {
        advanceInit.run()
      }
    })
    invokeLater(ModalityState.stateForComponent(myDetails)) {
      myProjectStructureEventDispatcher.multicaster.projectStructureInitializing()
    }
    dialog.showAndGet()
  }


  private fun initSidePanel() {

    mySidePanel = SidePanel(this, myHistory)

    if (myDisposable.disposed) myDisposable = MyDisposable()

    addConfigurables()
  }

  private fun addConfigurables() {
    val configurables =
      AndroidConfigurableContributor.EP_NAME.extensions
        .asSequence()
        .flatMap { it.getConfigurables(myProject, myDisposable).asSequence() }
        .groupBy { it.groupName }
        .mapValues { entry -> entry.value.flatMap { it.items } }

    configurables.entries.forEachIndexed { index, (_, items) ->
      if (index > 0) mySidePanel!!.addSeparator("--")
      items.forEach { addConfigurable(it) }
    }
  }

  private fun addConfigurable(configurable: Configurable) {
    myConfigurables[configurable] = null
    (configurable as? Place.Navigator)?.setHistory(myHistory)
    val counterDisplayConfigurable = configurable.safeAs<CounterDisplayConfigurable>()
    mySidePanel!!.addPlace(
      createPlaceFor(configurable),
      Presentation(configurable.displayName),
      counterDisplayConfigurable?.let { { SidePanel.ProblemStats(it.count, it.containsErrors()) } })
    counterDisplayConfigurable?.add(
      CounterDisplayConfigurable.CountChangeListener { UIUtil.invokeLaterIfNeeded { mySidePanel!!.repaint() } }, myDisposable)
  }

  fun <T : Configurable> findConfigurable(type: Class<T>): T? = myConfigurables.keys.filterIsInstance(type).firstOrNull()

  private fun findConfigurable(displayName: String): Configurable? = myConfigurables.keys.firstOrNull { it.displayName == displayName }

  override fun isModified(): Boolean = myConfigurables.keys.any { it.isModified }

  @Throws(ConfigurationException::class)
  override fun apply() {
    logUsageApply()
    val modifiedConfigurables = myConfigurables.keys.filter { it.isModified }
    if (modifiedConfigurables.isEmpty()) return
    modifiedConfigurables.forEach { it.apply() }
    // If we successfully applied changes there is none to notify about the changes since the dialog is being closed.
    if (!inDoOK) myProjectStructureEventDispatcher.multicaster.projectStructureChanged()
    needsSync = true
  }

  override fun reset() {
    HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Processing, "Resetting Project Structure") {
      val configurables = myConfigurables.keys

      for (each in configurables) {
        each.disposeUIResources()
        each.reset()
        (each as? MasterDetailsComponent)?.setHistory(myHistory)
      }

      myHistory.clear()

      val toSelect = myUiState.lastEditedConfigurable?.let { lastConfigurableDisplayname -> configurables.firstOrNull { it.displayName == lastConfigurableDisplayname } }
                     ?: configurables.firstOrNull()

      removeSelected()

      navigateTo(if (toSelect != null) createPlaceFor(toSelect) else null, false)

      if (myUiState.proportion > 0) {
        mySplitter!!.proportion = myUiState.proportion
      }
    }
  }

  override fun disposeUIResources() {
    if (!myUiInitialized) return
    try {

      myUiState.proportion = mySplitter!!.proportion
      (mySelectedConfigurable as? MasterDetailsComponent)?.saveSideProportion()
      myConfigurables.keys.forEach(Consumer<Configurable> { it.disposeUIResources() })

      myUiState.save(myProject)

      Disposer.dispose(myDisposable)
    }
    finally {
      myConfigurables.clear()
      myUiInitialized = false
    }
  }

  fun getHistory(): History? = myHistory

  fun add(listener: ProjectStructureListener, parentDisposable: Disposable) =
    myProjectStructureEventDispatcher.addListener(listener, parentDisposable)

  fun add(listener: ProjectStructureListener) = myProjectStructureEventDispatcher.addListener(listener)

  fun remove(listener: ProjectStructureListener) = myProjectStructureEventDispatcher.removeListener(listener)

  private inner class MyPanel internal constructor() : JPanel(BorderLayout()), DataProvider {
    override fun getData(@NonNls dataId: String): Any? = if (History.KEY.`is`(dataId)) getHistory() else null

  }

  private class UIState {
    var proportion: Float = 0.toFloat()
    var sideProportion: Float = 0.toFloat()
    var lastEditedConfigurable: String? = null

    fun save(project: Project) {
      val propertiesComponent = PropertiesComponent.getInstance(project)
      propertiesComponent.setValue(LAST_EDITED_PROPERTY, lastEditedConfigurable)
      propertiesComponent.setValue(PROPORTION_PROPERTY, proportion.toString())
      propertiesComponent.setValue(SIDE_PROPORTION_PROPERTY, sideProportion.toString())
    }

    fun load(project: Project) {
      val propertiesComponent = PropertiesComponent.getInstance(project)
      lastEditedConfigurable = propertiesComponent.getValue(LAST_EDITED_PROPERTY)
      proportion = parseFloatValue(propertiesComponent.getValue(PROPORTION_PROPERTY))
      sideProportion = parseFloatValue(propertiesComponent.getValue(SIDE_PROPORTION_PROPERTY))
    }
  }

  private class MyDisposable : Disposable {
    @Volatile
    internal var disposed: Boolean = false

    override fun dispose() {
      disposed = true
    }
  }

  interface ProjectStructureListener : EventListener {
    fun projectStructureInitializing()
    fun projectStructureChanged()
  }

  private fun logUsageOpen() {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.PROJECT_STRUCTURE_DIALOG)
        .setKind(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_OPEN)
        .setPsdEvent(PSDEvent.newBuilder().setGeneration(PSDEvent.PSDGeneration.PROJECT_STRUCTURE_DIALOG_GENERATION_002))
        .withProjectId(myProject)
    )
  }

  private fun logUsageApply() {
    val duration = System.currentTimeMillis() - myOpenTimeMs
    val psdEvent =
      PSDEvent.newBuilder()
        .setGeneration(PSDEvent.PSDGeneration.PROJECT_STRUCTURE_DIALOG_GENERATION_002)
        .setDurationMs(duration)
    myConfigurables.keys.filterIsInstance<TrackedConfigurable>().forEach { it.copyEditedFieldsTo(psdEvent) }
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.PROJECT_STRUCTURE_DIALOG)
        .setKind(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_SAVE)
        .setPsdEvent(psdEvent)
        .withProjectId(myProject)
    )
  }

  companion object {
    @NonNls
    const val CATEGORY_NAME = "categoryName"

    @NonNls
    private const val LAST_EDITED_PROPERTY = "project.structure.last.edited"

    @NonNls
    private const val PROPORTION_PROPERTY = "project.structure.proportion"

    @NonNls
    private const val SIDE_PROPORTION_PROPERTY = "project.structure.side.proportion"

    @JvmStatic
    fun getInstance(project: Project): ProjectStructureConfigurable =
      project.getService(ProjectStructureConfigurable::class.java)

    private fun parseFloatValue(value: String?): Float = value?.toFloatOrNull() ?: 0f

    @JvmStatic
    fun putPath(place: Place, configurable: Configurable) {
      place.putPath(CATEGORY_NAME, configurable.displayName)
    }

    private fun createPlaceFor(configurable: Configurable): Place = Place().putPath(CATEGORY_NAME, configurable.displayName)
  }
}

fun canShowPsdOrWarnUser(project: Project): Boolean {
  if (canShowPsd(project)) {
    return true
  }
  val ideFrame = WindowManager.getInstance().getIdeFrame(project)
  if (ideFrame != null) {
    val statusBar = ideFrame.statusBar as StatusBarEx
    statusBar.notifyProgressByBalloon(MessageType.WARNING, "Project Structure is unavailable while sync is in progress.", null, null)
  }
  return false
}

fun canShowPsd(project: Project) = !GradleSyncState.getInstance(project).isSyncInProgress