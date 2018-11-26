/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.tools.idea.gradle.structure.IdeSdksConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.CrossModuleUiStateComponent
import com.android.tools.idea.stats.withProjectId
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.MessageType
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
import com.intellij.ui.navigation.Place.goFurther
import com.intellij.util.EventDispatcher
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.ui.UIUtil.SIDE_PANEL_BACKGROUND
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import com.intellij.util.ui.UIUtil.requestFocus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.EventListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class ProjectStructureConfigurable(private val myProject: Project) : SearchableConfigurable, Place.Navigator, Configurable.NoMargin, Configurable.NoScroll {
  private val mySdksConfigurable: IdeSdksConfigurable
  private val myDetails = Wrapper()
  private val myConfigurables = Maps.newLinkedHashMap<Configurable, JComponent>()
  private val myUiState = UIState()

  private var myHistory = History(this)

  private var mySplitter: JBSplitter? = null
  private var mySidePanel: SidePanel? = null
  private val myNotificationPanel: JPanel? = null
  private var myToolbarComponent: JComponent? = null
  private var myErrorsComponent: JBLabel? = null
  private var myToFocus: JComponent? = null

  private var myUiInitialized: Boolean = false
  private var myShowing = false

  private var mySelectedConfigurable: Configurable? = null

  private val myEmptySelection = JLabel("<html><body><center>Select a setting to view or edit its details here</center></body></html>",
                                        SwingConstants.CENTER)
  private val myChangeEventDispatcher = EventDispatcher.create(ProjectStructureChangeListener::class.java)

  private var myDisposable = MyDisposable()
  private var myOpenTimeMs: Long = 0

  init {
    mySdksConfigurable = IdeSdksConfigurable(this, myProject)
    mySdksConfigurable.setHistory(myHistory)

    val propertiesComponent = PropertiesComponent.getInstance(myProject)
    myUiState.lastEditedConfigurable = propertiesComponent.getValue(LAST_EDITED_PROPERTY)
    val proportion = propertiesComponent.getValue(PROPORTION_PROPERTY)
    myUiState.proportion = parseFloatValue(proportion)
    val sideProportion = propertiesComponent.getValue(SIDE_PROPORTION_PROPERTY)
    myUiState.sideProportion = parseFloatValue(sideProportion)
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return myToFocus
  }

  fun showDialog(): Boolean {
    return showDialog(null)
  }

  private fun showDialog(advanceInit: Runnable?): Boolean {
    return ShowSettingsUtil.getInstance().editConfigurable(myProject, this, advanceInit)
  }

  override fun setHistory(history: History) {
    myHistory = history
  }

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback {
    if (place == null) {
      return ActionCallback.DONE
    }

    val toSelect: Configurable?
    val displayName = place.getPath(CATEGORY_NAME)
    if (displayName is String) {
      toSelect = findConfigurable((displayName as String?)!!)
    }
    else {
      toSelect = place.getPath(CATEGORY) as Configurable?
    }

    var detailsContent: JComponent? = myDetails.targetComponent

    if (mySelectedConfigurable !== toSelect) {
      saveSideProportion()
      removeSelected()
    }

    if (toSelect != null) {
      if (toSelect is CrossModuleUiStateComponent) {
        (toSelect as CrossModuleUiStateComponent).restoreUiState()
      }
      detailsContent = myConfigurables[toSelect]
      if (detailsContent == null) {
        detailsContent = toSelect.createComponent()
        myConfigurables[toSelect] = detailsContent
      }
      myDetails.setContent(detailsContent)
    }

    mySelectedConfigurable = toSelect
    if (mySelectedConfigurable != null) {
      myUiState.lastEditedConfigurable = mySelectedConfigurable!!.displayName

      val psdEvent = PSDEvent
        .newBuilder()
        .setGeneration(PSDEvent.PSDGeneration.PROJECT_STRUCTURE_DIALOG_GENERATION_002)
      if (mySelectedConfigurable is TrackedConfigurable) {
        (mySelectedConfigurable as TrackedConfigurable).applyTo(psdEvent)
        UsageTracker.log(AndroidStudioEvent
                           .newBuilder()
                           .setCategory(AndroidStudioEvent.EventCategory.PROJECT_STRUCTURE_DIALOG)
                           .setKind(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_LEFT_NAV_CLICK)
                           .setPsdEvent(psdEvent).withProjectId(
            myProject))
      }
    }

    if (toSelect is MasterDetailsComponent) {
      val masterDetails = toSelect as MasterDetailsComponent?
      if (myUiState.sideProportion > 0) {
        masterDetails!!.splitter.proportion = myUiState.sideProportion
      }
      masterDetails!!.setHistory(myHistory)
    }
    else if (toSelect === mySdksConfigurable) {
      mySdksConfigurable.setHistory(myHistory)
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
      requestFocus(myToFocus!!)
    }

    val result = ActionCallback()
    goFurther(toSelect, place, requestFocus).notifyWhenDone(result)

    myDetails.revalidate()
    myDetails.repaint()

    if (!myHistory.isNavigatingNow && mySelectedConfigurable != null) {
      myHistory.pushQueryPlace()
    }

    return result
  }

  private fun findConfigurable(displayName: String): Configurable? {
    for (configurable in myConfigurables.keys) {
      if (displayName == configurable.displayName) {
        return configurable
      }
    }
    return null
  }

  private fun saveSideProportion() {
    if (mySelectedConfigurable is MasterDetailsComponent) {
      myUiState.sideProportion = (mySelectedConfigurable as MasterDetailsComponent).splitter.proportion
    }
  }

  private fun removeSelected() {
    myDetails.removeAll()
    mySelectedConfigurable = null
    myUiState.lastEditedConfigurable = null

    myDetails.add(myEmptySelection, BorderLayout.CENTER)
  }

  override fun queryPlace(place: Place) {
    if (mySelectedConfigurable != null) {
      place.putPath(CATEGORY, mySelectedConfigurable)
      Place.queryFurther(mySelectedConfigurable, place)
    }
  }

  override fun getId(): String {
    return "android.project.structure"
  }

  override fun enableSearch(option: String): Runnable? {
    return null
  }

  @Nls
  override fun getDisplayName(): String {
    return ProjectBundle.message("project.settings.display.name")
  }

  override fun getHelpTopic(): String? {
    return if (mySelectedConfigurable != null) mySelectedConfigurable!!.helpTopic else ""
  }

  override fun createComponent(): JComponent? {
    val component = MyPanel()
    mySplitter = OnePixelSplitter(false, .15f)
    mySplitter!!.setHonorComponentsMinimumSize(true)

    initSidePanel()

    val left = object : JPanel(BorderLayout()) {
      override fun getMinimumSize(): Dimension {
        val original = super.getMinimumSize()
        return Dimension(Math.max(original.width, 100), original.height)
      }
    }

    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(BackAction(component, myDisposable))
    toolbarGroup.add(ForwardAction(component, myDisposable))

    val toolbar = ActionManager.getInstance().createActionToolbar("AndroidProjectStructure", toolbarGroup, true)
    toolbar.setTargetComponent(component)
    myToolbarComponent = toolbar.component

    left.background = SIDE_PANEL_BACKGROUND
    myToolbarComponent!!.background = SIDE_PANEL_BACKGROUND

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

  private fun initSidePanel() {
    val isDefaultProject = myProject === ProjectManager.getInstance().defaultProject

    mySidePanel = SidePanel(this, myHistory)

    addConfigurable(mySdksConfigurable)

    if (!isDefaultProject) {
      addConfigurables()
    }
  }

  private fun addConfigurables() {
    if (myDisposable.disposed) {
      myDisposable = MyDisposable()
    }
    val additionalConfigurableGroups = Lists.newArrayList<ProjectStructureItemGroup>()
    for (contributor in AndroidConfigurableContributor.EP_NAME.extensions) {
      contributor.getMainConfigurables(myProject, myDisposable).forEach(Consumer<Configurable> { this.addConfigurable(it) })
      additionalConfigurableGroups.addAll(contributor.additionalConfigurableGroups)
    }
    for (group in additionalConfigurableGroups) {
      val name = group.groupName
      mySidePanel!!.addSeparator(name)
      group.items.forEach(Consumer<Configurable> { this.addConfigurable(it) })
    }
  }

  private fun addConfigurable(configurable: Configurable) {
    myConfigurables[configurable] = null
    if (configurable is Place.Navigator) {
      val navigator = configurable as Place.Navigator
      navigator.setHistory(myHistory)
    }
    mySidePanel!!.addPlace(createPlaceFor(configurable), Presentation(configurable.displayName))
    if (configurable is CounterDisplayConfigurable) {
      (configurable as CounterDisplayConfigurable).add(
        CounterDisplayConfigurable.CountChangeListener { invokeLaterIfNeeded { mySidePanel!!.repaint() } }, myDisposable)
    }
  }

  fun <T : Configurable> findConfigurable(type: Class<T>): T? {
    for (configurable in myConfigurables.keys) {
      if (type.isInstance(configurable)) {
        return type.cast(configurable)
      }
    }
    return null
  }

  override fun isModified(): Boolean {
    for (configurable in myConfigurables.keys) {
      if (configurable.isModified) {
        return true
      }
    }
    return false
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    val duration = System.currentTimeMillis() - myOpenTimeMs
    UsageTracker.log(AndroidStudioEvent
                       .newBuilder()
                       .setCategory(AndroidStudioEvent.EventCategory.PROJECT_STRUCTURE_DIALOG)
                       .setKind(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_SAVE)
                       .setPsdEvent(
                         PSDEvent
                           .newBuilder()
                           .setGeneration(PSDEvent.PSDGeneration.PROJECT_STRUCTURE_DIALOG_GENERATION_002)
                           .setDurationMs(duration)
                       ).withProjectId(myProject))
    var applied = false
    for (configurable in myConfigurables.keys) {
      if (configurable.isModified) {
        configurable.apply()
        applied = true
      }
    }
    if (applied) {
      myChangeEventDispatcher.multicaster.projectStructureChanged()
    }
  }

  override fun reset() {
    val token = HeavyProcessLatch.INSTANCE.processStarted("Resetting Project Structure")
    try {
      mySdksConfigurable.reset()

      var toSelect: Configurable? = null
      for (each in myConfigurables.keys) {
        if (myUiState.lastEditedConfigurable != null && myUiState.lastEditedConfigurable == each.displayName) {
          toSelect = each
        }
        if (each is MasterDetailsComponent) {
          each.setHistory(myHistory)
        }
        each.disposeUIResources()
        each.reset()
      }

      myHistory.clear()

      if (toSelect == null && !myConfigurables.isEmpty()) {
        toSelect = myConfigurables.keys.stream().findFirst().orElse(null)
      }

      removeSelected()

      navigateTo(if (toSelect != null) createPlaceFor(toSelect) else null, false)

      if (myUiState.proportion > 0) {
        mySplitter!!.proportion = myUiState.proportion
      }
    }
    finally {
      token.finish()
    }
  }

  override fun disposeUIResources() {
    if (!myUiInitialized) {
      return
    }
    try {
      val propertiesComponent = PropertiesComponent.getInstance(myProject)
      propertiesComponent.setValue(LAST_EDITED_PROPERTY, myUiState.lastEditedConfigurable)
      propertiesComponent.setValue(PROPORTION_PROPERTY, myUiState.proportion.toString())
      propertiesComponent.setValue(SIDE_PROPORTION_PROPERTY, myUiState.sideProportion.toString())

      myUiState.proportion = mySplitter!!.proportion
      saveSideProportion()
      myConfigurables.keys.forEach(Consumer<Configurable> { it.disposeUIResources() })

      Disposer.dispose(myDisposable)
    }
    finally {
      myConfigurables.clear()
      myUiInitialized = false
    }
  }

  fun getHistory(): History? {
    return myHistory
  }

  fun add(listener: ProjectStructureChangeListener, parentDisposable: Disposable) {
    myChangeEventDispatcher.addListener(listener, parentDisposable)
  }

  fun add(listener: ProjectStructureChangeListener) {
    myChangeEventDispatcher.addListener(listener)
  }

  fun remove(listener: ProjectStructureChangeListener) {
    myChangeEventDispatcher.removeListener(listener)
  }

  private inner class MyPanel internal constructor() : JPanel(BorderLayout()), DataProvider {

    override fun getData(@NonNls dataId: String): Any? {
      if (KEY.`is`(dataId)) {
        return this@ProjectStructureConfigurable
      }
      return if (History.KEY.`is`(dataId)) {
        getHistory()
      }
      else null
    }
  }

  class UIState {
    var proportion: Float = 0.toFloat()
    var sideProportion: Float = 0.toFloat()
    var lastEditedConfigurable: String? = null
  }

  private class MyDisposable : Disposable {
    @Volatile
    internal var disposed: Boolean = false

    override fun dispose() {
      disposed = true
    }
  }

  interface ProjectStructureChangeListener : EventListener {
    fun projectStructureChanged()
  }

  fun showPlace(place: Place?) {
    // TODO(IDEA-196602):  Pressing Ctrl+Alt+S or Ctrl+Alt+Shift+S for a little longer shows tens of dialogs. Remove when fixed.
    if (myShowing) return
    if (GradleSyncState.getInstance(myProject).isSyncInProgress) {
      val ideFrame = WindowManager.getInstance().getIdeFrame(myProject)
      if (ideFrame != null) {
        val statusBar = ideFrame.statusBar as StatusBarEx
        statusBar.notifyProgressByBalloon(MessageType.WARNING, "Project Structure is unavailable while sync is in progress.", null, null)
      }
      return
    }
    val needsSync = AtomicBoolean()
    val changeListener = object : ProjectStructureChangeListener {
      override fun projectStructureChanged() {
        needsSync.set(true)
      }
    }
    add(changeListener)
    try {
      myOpenTimeMs = System.currentTimeMillis()
      UsageTracker.log(AndroidStudioEvent
                         .newBuilder()
                         .setCategory(AndroidStudioEvent.EventCategory.PROJECT_STRUCTURE_DIALOG)
                         .setKind(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_OPEN)
                         .setPsdEvent(PSDEvent.newBuilder().setGeneration(
                           PSDEvent.PSDGeneration.PROJECT_STRUCTURE_DIALOG_GENERATION_002)).withProjectId(
          myProject))
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
    }
    finally {
      remove(changeListener)
    }
    if (needsSync.get()) {
      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(myProject, TRIGGER_PROJECT_MODIFIED)
    }
  }

  fun show() {
    showPlace(null)
  }

  companion object {
    @JvmStatic
    val KEY = DataKey.create<ProjectStructureConfigurable>("ProjectStructureConfiguration")

    @NonNls
    const val CATEGORY = "category"

    @NonNls
    const val CATEGORY_NAME = "categoryName"

    @NonNls
    private val LAST_EDITED_PROPERTY = "project.structure.last.edited"

    @NonNls
    private val PROPORTION_PROPERTY = "project.structure.proportion"

    @NonNls
    private val SIDE_PROPORTION_PROPERTY = "project.structure.side.proportion"

    @JvmStatic
    fun getInstance(project: Project): ProjectStructureConfigurable {
      return ServiceManager.getService(project, ProjectStructureConfigurable::class.java)
    }

    private fun parseFloatValue(value: String?): Float {
      if (value != null) {
        try {
          return java.lang.Float.parseFloat(value)
        }
        catch (ignored: NumberFormatException) {
        }

      }
      return 0f
    }

    @JvmStatic
    fun putPath(place: Place, configurable: Configurable) {
      place.putPath(CATEGORY_NAME, configurable.displayName)
    }

    private fun createPlaceFor(configurable: Configurable): Place {
      return Place().putPath(CATEGORY, configurable)
    }
  }
}
