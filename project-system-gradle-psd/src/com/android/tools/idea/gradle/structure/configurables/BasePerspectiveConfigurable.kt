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
package com.android.tools.idea.gradle.structure.configurables

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.gradle.structure.configurables.android.modules.AbstractModuleConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.ModuleSelectorDropDownPanel
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader
import com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.showDefaultWizard
import com.android.tools.idea.structure.configurables.ui.CrossModuleUiStateComponent
import com.android.tools.idea.structure.dialog.TrackedConfigurable
import com.android.tools.idea.structure.dialog.logUsagePsdAction
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.JBSplitter
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place
import com.intellij.ui.navigation.Place.goFurther
import com.intellij.ui.navigation.Place.queryFurther
import com.intellij.util.IconUtil
import com.intellij.util.ui.tree.TreeUtil
import icons.StudioIcons.Shell.Filetree.ANDROID_MODULE
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ToolTipManager

const val BASE_PERSPECTIVE_MODULE_PLACE_NAME = "base_perspective.module"

abstract class BasePerspectiveConfigurable protected constructor(
  protected val context: PsContext,
  val extraModules: List<PsModule>
) : MasterDetailsComponent(),
    SearchableConfigurable,
    Disposable,
    Place.Navigator,
    TrackedConfigurable,
    CrossModuleUiStateComponent {

  private enum class ModuleSelectorStyle { LIST_VIEW, DROP_DOWN }

  private var uiDisposed = true

  private var toolWindowHeader: ToolWindowHeader? = null
  private var loadingPanel: JBLoadingPanel? = null
  private var loadingPanelVisible = false
  private var centerComponent: JComponent? = null
  private var moduleSelectorDropDownPanel: ModuleSelectorDropDownPanel? = null

  private var treeInitiated: Boolean = false
  private var currentModuleSelectorStyle: ModuleSelectorStyle? = null

  private val navigationPathName: String = BASE_PERSPECTIVE_MODULE_PLACE_NAME
  val selectedModule: PsModule? get() = myCurrentConfigurable?.editableObject as? PsModule

  init {
    (splitter as JBSplitter).splitterProportionKey = "android.psd.proportion.modules"

    @Suppress("LeakingThis")
    context.add(object : PsContext.SyncListener {
      override fun started() {
        loadingPanel?.startLoading()
        loadingPanelVisible = true
      }

      override fun ended() {
        loadingPanelVisible = false
        stopSyncAnimation()
      }
    }, this)

    @Suppress("LeakingThis")
    context.analyzerDaemon.onIssuesChange(this) @UiThread {
      if (myTree.isShowing) {
        // If issues are updated and the tree is showing, trigger a repaint so the proper highlight and tooltip is applied.
        revalidateAndRepaint(myTree)
      }
      Unit
    }

    @Suppress("LeakingThis")
    context.uiSettings.addListener(PsUISettings.ChangeListener { reconfigureForCurrentSettings() }, this)
  }

  private fun stopSyncAnimation() {
    loadingPanel?.stopLoading()
  }

  fun selectModule(gradlePath: String): BaseNamedConfigurable<*>? =
    findModuleByGradlePath(gradlePath)
      ?.let { MasterDetailsComponent.findNodeByObject(myRoot, it) }
      ?.let { node ->
        selectNodeInTree(node)
        selectedNode = node
        node.configurable as? BaseNamedConfigurable<*>
      }

  protected fun findModuleByGradlePath(gradlePath: String): PsModule? =
    context.project.findModuleByGradlePath(gradlePath) ?: extraModules.find { it.gradlePath == gradlePath }

  override fun updateSelection(configurable: NamedConfigurable<*>?) {
    // UpdateSelection might be expensive as it always rebuilds the element tree.
    if (configurable === myCurrentConfigurable) return

    if (configurable is BaseNamedConfigurable<*>) {
      // It is essential to restore the state of the UI before updateSelection() to avoid multiple rebuilds of the element tree.
      configurable.restoreUiState()
    }
    super.updateSelection(configurable)
    if (configurable is BaseNamedConfigurable<*>) {
      val module = configurable.editableObject
      context.setSelectedModule(module.gradlePath, this)
    }
    myHistory.pushQueryPlace()
    moduleSelectorDropDownPanel?.update()
  }


  final override fun reInitWholePanelIfNeeded() {
    if (!myToReInitWholePanel) return
    super.reInitWholePanelIfNeeded()
    currentModuleSelectorStyle = null
    centerComponent = splitter.secondComponent
    val splitterLeftComponent = (splitter.firstComponent as JPanel)
    toolWindowHeader = ToolWindowHeader.createAndAdd("Modules", ANDROID_MODULE, splitterLeftComponent, ToolWindowAnchor.LEFT)
      .also {
        it.setPreferredFocusedComponent(myTree)
        it.addMinimizeListener { modulesTreeMinimized() }
        Disposer.register(this@BasePerspectiveConfigurable, it)
      }
  }

  private fun reconfigureForCurrentSettings() {
    reconfigureFor(if (context.uiSettings.MODULES_LIST_MINIMIZE) ModuleSelectorStyle.DROP_DOWN else ModuleSelectorStyle.LIST_VIEW)
  }

  private fun reconfigureFor(moduleSelectorStyle: ModuleSelectorStyle) {
    if (currentModuleSelectorStyle == moduleSelectorStyle) return
    if (myWholePanel == null) {
      currentModuleSelectorStyle = null
      myToReInitWholePanel = true
      reInitWholePanelIfNeeded()
    }

    when (moduleSelectorStyle) {
      ModuleSelectorStyle.DROP_DOWN -> {
        splitter.secondComponent = null
        myWholePanel.remove(splitter)
        myWholePanel.add(centerComponent!!, BorderLayout.CENTER)
        moduleSelectorDropDownPanel = ModuleSelectorDropDownPanel(context, this)
        myWholePanel.add(moduleSelectorDropDownPanel, BorderLayout.NORTH)
      }
      ModuleSelectorStyle.LIST_VIEW -> {
        splitter.secondComponent = centerComponent
        myWholePanel.add(splitter)
        moduleSelectorDropDownPanel?.let { it.parent.remove(it) }
        moduleSelectorDropDownPanel = null
      }
    }
    currentModuleSelectorStyle = moduleSelectorStyle
    revalidateAndRepaint(myWholePanel)
  }

  private fun modulesTreeMinimized() =
    with(context.uiSettings) {
      MODULES_LIST_MINIMIZE = true
      fireUISettingsChanged()
    }

  override fun reset() {
    uiDisposed = false

    if (!treeInitiated) {
      initTree()
    }
    else {
      super<MasterDetailsComponent>.disposeUIResources()
    }
    loadTree()

    currentModuleSelectorStyle = null
    super<MasterDetailsComponent>.reset()
    TreeUtil.expandAll(myTree)
  }

  override fun initTree() {
    if (treeInitiated) return
    treeInitiated = true
    super.initTree()
    myTree.isRootVisible = false

    TreeSpeedSearch.installOn(myTree, true) { treePath -> (treePath.lastPathComponent as MasterDetailsComponent.MyNode).displayName }
    ToolTipManager.sharedInstance().registerComponent(myTree)
    myTree.cellRenderer = PsModuleCellRenderer(context)
  }

  private fun loadTree() {
    myTree.model =
      createTreeModel(
        object : NamedContainerConfigurableBase<PsModule>("root") {
          private val hasRootGradleModule = context.project.modules.any { it.gradlePath == ":" }
          override fun getChildrenModels(): Collection<PsModule> = extraModules + context.project.modules
            .filter { filterRootLevelModules(it) }
          override fun createChildConfigurable(model: PsModule) = this@BasePerspectiveConfigurable.createChildConfigurable(model)
          override fun onChange(disposable: Disposable, listener: () -> Unit) = context.project.modules.onChange(disposable, listener)
          override fun dispose() = Unit

          private fun filterRootLevelModules(module: PsModule): Boolean {
            return when {
              // Fake modules should always be displayed under the tree root.
              module.gradlePath == null -> true
              // If gradle root is among modules add it to the tree root node
              hasRootGradleModule -> module.gradlePath == ":"
              // otherwise flatten the Gradle root project and add its children directly under the tree root.
              else -> GradleUtil.isDirectChild(module.gradlePath, ":")
            }
          }
        }.also { Disposer.register(this, it) })
    myRoot = myTree.model.root as MyNode
    uiDisposed = false
  }

  override fun createComponent(): JComponent {
    val contents = super.createComponent()
    reconfigureForCurrentSettings()
    return JBLoadingPanel(BorderLayout(), this).also {
      loadingPanel = it
      it.setLoadingText("Fetching Gradle build models")
      it.add(contents, BorderLayout.CENTER)
      if (loadingPanelVisible) {
        it.startLoading()
      }
    }
  }

  fun createChildConfigurable(model: PsModule) : NamedConfigurable<out PsModule> =
    createConfigurableFor(model).also { it.setHistory(myHistory) }

  protected abstract fun createConfigurableFor(module: PsModule): AbstractModuleConfigurable<out PsModule, *>

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback {
    fun Place.getModuleGradlePath() = (getPath(navigationPathName) as? String)?.takeIf { moduleName -> moduleName.isNotEmpty() }
    return place
             ?.getModuleGradlePath()
             ?.let { moduleGradlePath ->
               val callback = ActionCallback()
               context.setSelectedModule(moduleGradlePath, this)
               selectModule(moduleGradlePath)  // TODO(solodkyy): Do not ignore result.
               selectedConfigurable?.let {
                 goFurther(selectedConfigurable, place, requestFocus).notifyWhenDone(callback)
                 callback
               }
             } ?: ActionCallback.DONE
  }

  override fun queryPlace(place: Place) {
    val moduleName = (selectedConfigurable as? BaseNamedConfigurable<*>)?.editableObject?.gradlePath
    if (moduleName != null) {
      place.putPath(navigationPathName, moduleName)
      queryFurther(selectedConfigurable, place)
      return
    }
    place.putPath(navigationPathName, "")
  }

  override fun getSelectedConfigurable(): NamedConfigurable<*>? =
    (myTree.selectionPath?.lastPathComponent as? MasterDetailsComponent.MyNode)?.configurable

  fun putNavigationPath(place: Place, gradlePath: String) {
    place.putPath(navigationPathName, gradlePath)
    val module = findModuleByGradlePath(gradlePath) ?: error("Cannot find module with gradle path: $gradlePath")
    val node = MasterDetailsComponent.findNodeByObject(myRoot, module)!!
    val configurable = node.configurable
    assert(configurable is BaseNamedConfigurable<*>)
  }

  override fun createActions(fromPopup: Boolean): List<AnAction> {
    val addNewModuleAction = object : DumbAwareAction("New Module", "Add new module", IconUtil.addIcon) {
      override fun actionPerformed(e: AnActionEvent) {
        if (!context.project.isModified ||
            Messages.showYesNoDialog(
                e.project,
                "Pending changes will be applied to the project. Continue?",
                "Add Module",
                Messages.getQuestionIcon()) == Messages.YES
        ) {
          context.project.ideProject.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_MODULES_ADD)
          var synced = false
          context.applyRunAndReparse {
            // TODO(b/134652202)
            showDefaultWizard(context.project.ideProject, ":", object: ProjectSyncInvoker {
              override fun syncProject(project: Project) { synced = true }
            })
            synced  // Tells whether the context needs to reparse the config.
          }
        }
      }
    }
    val removeModuleAction = object : DumbAwareAction("Remove Module", "Remove module", IconUtil.removeIcon) {
      override fun update(e: AnActionEvent) {
        if (uiDisposed) return
        super.update(e)
        e.presentation.isEnabled = (selectedObject as? PsModule)?.gradlePath != null
      }

      override fun actionPerformed(e: AnActionEvent) {
        val module = (selectedObject as? PsModule) ?: return
        if (Messages.showYesNoDialog(
                e.project,
                buildString {
                  append(when {
                           module.parent.modelCount == 1 -> "Are you sure you want to remove the only module form the project?"
                           else -> "Remove module '${module.name}' from the project?"
                         })
                  append("\n")
                  append("No files will be deleted on disk.")
                },
                "Remove Module",
                Messages.getQuestionIcon()
            ) == Messages.YES) {
          context.project.ideProject.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_MODULES_REMOVE)
          module.parent.removeModule(module.gradlePath!!)
        }
      }
    }

    fun AnAction.withShortcuts(action: String) = apply {
      registerCustomShortcutSet(ActionManager.getInstance().getAction(action).shortcutSet, tree)
    }

    return listOf(
        addNewModuleAction.withShortcuts(IdeActions.ACTION_NEW_ELEMENT),
        removeModuleAction.withShortcuts(IdeActions.ACTION_DELETE)
    )
  }

  override fun disposeUIResources() {
    if (uiDisposed) return
    super<MasterDetailsComponent>.disposeUIResources()
    uiDisposed = true
    myAutoScrollHandler.cancelAllRequests()
    currentModuleSelectorStyle = null
    Disposer.dispose(this)
  }

  override fun dispose() {
    toolWindowHeader?.let { Disposer.dispose(it) }
    toolWindowHeader = null
  }

  override fun isModified(): Boolean = context.project.isModified

  final override fun apply() = context.applyChanges()

  override fun copyEditedFieldsTo(builder: PSDEvent.Builder) {
    builder.addAllModifiedFields(context.getEditedFieldsAndClear())
  }

  override fun setHistory(history: History?) = super<MasterDetailsComponent>.setHistory(history)

  override fun restoreUiState() {
    context.selectedModule?.let { selectModule(it)?.restoreUiState() }
  }
}
