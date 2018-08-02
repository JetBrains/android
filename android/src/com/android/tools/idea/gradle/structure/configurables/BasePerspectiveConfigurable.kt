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

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.structure.configurables.android.modules.AbstractModuleConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.CrossModuleUiStateComponent
import com.android.tools.idea.gradle.structure.configurables.ui.ModuleSelectorDropDownPanel
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader
import com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint
import com.android.tools.idea.gradle.structure.model.PsModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
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
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import icons.StudioIcons.Shell.Filetree.ANDROID_MODULE
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ToolTipManager
import javax.swing.tree.DefaultTreeModel

abstract class BasePerspectiveConfigurable protected constructor(
  protected val context: PsContext,
  val extraModules: List<PsModule>
) : MasterDetailsComponent(),
    SearchableConfigurable,
    Disposable,
    Place.Navigator,
    CrossModuleUiStateComponent {

  private enum class ModuleSelectorStyle { LIST_VIEW, DROP_DOWN }

  private var uiDisposed = true

  private var toolWindowHeader: ToolWindowHeader? = null
  private var loadingPanel: JBLoadingPanel? = null
  private var centerComponent: JComponent? = null
  private var moduleSelectorDropDownPanel: ModuleSelectorDropDownPanel? = null

  private var treeInitiated: Boolean = false
  private var currentModuleSelectorStyle: ModuleSelectorStyle? = null

  protected abstract val navigationPathName: String
  val selectedModule: PsModule? get() = myCurrentConfigurable?.editableObject as? PsModule
  val selectedModuleName: String? get() = selectedModule?.name

  init {
    (splitter as JBSplitter).splitterProportionKey = "android.psd.proportion.modules"

    @Suppress("LeakingThis")
    context.add(object : GradleSyncListener {
      override fun syncStarted(project: Project, skipped: Boolean, sourceGenerationRequested: Boolean) {
        loadingPanel?.startLoading()
      }

      override fun syncSucceeded(project: Project) = stopSyncAnimation()
      override fun syncFailed(project: Project, errorMessage: String) = stopSyncAnimation()
    }, this)

    @Suppress("LeakingThis")
    context.analyzerDaemon.add(
      {
        if (myTree.isShowing) {
          // If issues are updated and the tree is showing, trigger a repaint so the proper highlight and tooltip is applied.
          invokeLaterIfNeeded { revalidateAndRepaint(myTree) }
        }
        Unit
      }, this)

    @Suppress("LeakingThis")
    context.uiSettings.addListener(PsUISettings.ChangeListener { reconfigureForCurrentSettings() }, this)
  }

  private fun stopSyncAnimation() {
    loadingPanel?.stopLoading()
  }

  fun selectModule(moduleName: String): BaseNamedConfigurable<*>? =
    findModule(moduleName)
      ?.let { MasterDetailsComponent.findNodeByObject(myRoot, it) }
      ?.let { node ->
        selectNodeInTree(moduleName)
        selectedNode = node
        node.configurable as? BaseNamedConfigurable<*>
      }

  protected fun findModule(moduleName: String): PsModule? =
    context.project.findModuleByName(moduleName) ?: extraModules.find { it.name == moduleName }

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
      context.setSelectedModule(module.name, this)
    }
    myHistory.pushQueryPlace()
    moduleSelectorDropDownPanel?.update()
  }


  final override fun reInitWholePanelIfNeeded() {
    if (!myToReInitWholePanel) return
    super.reInitWholePanelIfNeeded()
    currentModuleSelectorStyle = null
    centerComponent = splitter.secondComponent
    val splitterLeftcomponent = (splitter.firstComponent as JPanel)
    toolWindowHeader = ToolWindowHeader.createAndAdd("Modules", ANDROID_MODULE, splitterLeftcomponent, ToolWindowAnchor.LEFT)
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
    myTree.showsRootHandles = false
    loadTree()

    currentModuleSelectorStyle = null
    super<MasterDetailsComponent>.reset()
  }

  override fun initTree() {
    if (treeInitiated) return
    treeInitiated = true
    super.initTree()
    myTree.isRootVisible = false

    TreeSpeedSearch(myTree, { treePath -> (treePath.lastPathComponent as MasterDetailsComponent.MyNode).displayName }, true)
    ToolTipManager.sharedInstance().registerComponent(myTree)
    myTree.cellRenderer = PsModuleCellRenderer(context)
  }

  private fun loadTree() {
    (myTree.model as DefaultTreeModel).reload()
    createModuleNodes()
    uiDisposed = false
  }

  override fun createComponent(): JComponent {
    val contents = super.createComponent()
    reconfigureForCurrentSettings()
    return JBLoadingPanel(BorderLayout(), this).also {
      loadingPanel = it
      it.setLoadingText("Syncing Project with Gradle")
      it.add(contents, BorderLayout.CENTER)
    }
  }

  private fun createModuleNodes() {
    context.project.forEachModule(Consumer<PsModule> { this.addConfigurableFor(it) })
    extraModules.forEach(Consumer<PsModule> { this.addConfigurableFor(it) })
  }

  private fun addConfigurableFor(module: PsModule) {
    createConfigurableFor(module)
      ?.also { it.setHistory(myHistory) }
      ?.let { MasterDetailsComponent.MyNode(it) }
      ?.also { myRoot.add(it) }
  }

  protected abstract fun createConfigurableFor(module: PsModule): AbstractModuleConfigurable<*, *>?

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback {
    fun Place.getModuleName() = (getPath(navigationPathName) as? String)?.takeIf { moduleName -> moduleName.isNotEmpty() }
    return place
             ?.getModuleName()
             ?.let { moduleName ->
               val callback = ActionCallback()
               context.setSelectedModule(moduleName, this)
               selectModule(moduleName)  // TODO(solodkyy): Do not ignore result.
               selectedConfigurable?.let {
                 goFurther(selectedConfigurable, place, requestFocus).notifyWhenDone(callback)
                 callback
               }
             } ?: ActionCallback.DONE
  }

  override fun queryPlace(place: Place) {
    val moduleName = (selectedConfigurable as? BaseNamedConfigurable<*>)?.editableObject?.name
    if (moduleName != null) {
      place.putPath(navigationPathName, moduleName)
      queryFurther(selectedConfigurable, place)
      return
    }
    place.putPath(navigationPathName, "")
  }

  override fun getSelectedConfigurable(): NamedConfigurable<*>? =
    (myTree.selectionPath?.lastPathComponent as? MasterDetailsComponent.MyNode)?.configurable

  fun putNavigationPath(place: Place, moduleName: String, dependency: String) {
    place.putPath(navigationPathName, moduleName)
    val module = findModule(moduleName)!!
    val node = MasterDetailsComponent.findNodeByObject(myRoot, module)!!
    val configurable = node.configurable
    assert(configurable is BaseNamedConfigurable<*>)
    val dependenciesConfigurable = configurable as BaseNamedConfigurable<*>
    dependenciesConfigurable.putNavigationPath(place, dependency)
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

  override fun enableSearch(option: String): Runnable? = null

  override fun isModified(): Boolean = context.project.isModified

  override fun apply() = context.project.applyChanges()

  override fun setHistory(history: History?) = super<MasterDetailsComponent>.setHistory(history)

  override fun restoreUiState() {
    context.selectedModule?.let { selectModule(it)?.restoreUiState() }
  }
}
