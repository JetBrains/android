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
  protected val context: PsContext
) : MasterDetailsComponent(),
    SearchableConfigurable,
    Disposable,
    Place.Navigator {

  private var uiDisposed = true

  private var toolWindowHeader: ToolWindowHeader? = null
  private var loadingPanel: JBLoadingPanel? = null
  private var centerComponent: JComponent? = null

  private var treeInitiated: Boolean = false
  private var treeMinimized: Boolean = false

  // This flag prevents an infinite loop started when a module is selected.
  // When a module is selected, the selected module is recorded in PsContext. PsContext then notifies every configurable about the module
  // selection change. Each configurable adjusts its selected module, but if they notify PsContext about this change, the notification
  // cycle will start all over again.
  private var selectModuleQuietly: Boolean = false

  protected open fun getExtraModules(): List<PsModule> = listOf()
  protected abstract val navigationPathName: String

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
    context.add(object : PsContext.ChangeListener {
      override fun moduleSelectionChanged(moduleName: String) {
        selectModuleQuietly = true
        selectModule(moduleName)?.restoreUiState()
      }
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

    treeMinimized = this.context.uiSettings.MODULES_LIST_MINIMIZE
    if (treeMinimized) {
      myToReInitWholePanel = true
      reInitWholePanelIfNeeded()
    }
    @Suppress("LeakingThis")
    context.uiSettings.addListener(PsUISettings.ChangeListener { settings ->
      if (settings.MODULES_LIST_MINIMIZE != treeMinimized) {
        treeMinimized = settings.MODULES_LIST_MINIMIZE
        myToReInitWholePanel = true
        reInitWholePanelIfNeeded()
      }
    }, this)
  }

  private fun stopSyncAnimation() {
    loadingPanel?.stopLoading()
  }

  private fun selectModule(moduleName: String): BaseNamedConfigurable<*>? =
    findModule(moduleName)
      ?.let { MasterDetailsComponent.findNodeByObject(myRoot, it) }
      ?.let { node ->
        selectNodeInTree(moduleName)
        selectedNode = node
        node.configurable as? BaseNamedConfigurable<*>
      }

  protected fun findModule(moduleName: String): PsModule? =
    context.project.findModuleByName(moduleName) ?: getExtraModules().find { it.name == moduleName }

  override fun updateSelection(configurable: NamedConfigurable<*>?) {
    // UpdateSelection might be expensive as it always rebuilds the element tree.
    if (configurable === myCurrentConfigurable) {
      selectModuleQuietly = false
      return
    }

    if (configurable is BaseNamedConfigurable<*>) {
      // It is essential to restore the state of the UI before updateSelection() to avoid multiple rebuilds of the element tree.
      configurable.restoreUiState()
    }
    super.updateSelection(configurable)
    if (configurable is BaseNamedConfigurable<*>) {
      val module = configurable.editableObject
      if (!selectModuleQuietly) {
        context.setSelectedModule(module.name, this)
      }
      else {
        selectModuleQuietly = false
      }
    }
    myHistory.pushQueryPlace()
  }

  final override fun reInitWholePanelIfNeeded() {
    if (!myToReInitWholePanel) return

    if (treeMinimized) {
      centerComponent = splitter.secondComponent

      if (centerComponent == null) {
        super.reInitWholePanelIfNeeded()
        centerComponent = splitter.secondComponent
      }
      myToReInitWholePanel = false

      splitter.secondComponent = null
      myWholePanel.remove(splitter)
      myWholePanel.add(centerComponent!!, BorderLayout.CENTER)
      revalidateAndRepaint(myWholePanel)
    }
    else {
      if (myWholePanel == null) {
        super.reInitWholePanelIfNeeded()
      }
      myToReInitWholePanel = false

      if (centerComponent !== null && centerComponent !== splitter) {
        myWholePanel.remove(centerComponent!!)
        splitter.secondComponent = centerComponent
        myWholePanel.add(splitter)
        revalidateAndRepaint(myWholePanel)
      }

      centerComponent = splitter

      val first = splitter.firstComponent
      if (first is JPanel) {
        if (toolWindowHeader == null) {
          toolWindowHeader =
            ToolWindowHeader.createAndAdd("Modules", ANDROID_MODULE, first, ToolWindowAnchor.LEFT).apply {
              setPreferredFocusedComponent(myTree)
              addMinimizeListener {
                modulesTreeMinimized()
                reInitWholePanelIfNeeded()
              }
            }
        }
        else if (toolWindowHeader!!.parent !== first) {
          first.add(toolWindowHeader!!, BorderLayout.NORTH)
        }
      }
    }
  }

  private fun modulesTreeMinimized() {
    val settings = context.uiSettings
    myToReInitWholePanel = true
    treeMinimized = myToReInitWholePanel
    settings.MODULES_LIST_MINIMIZE = treeMinimized
    settings.fireUISettingsChanged()
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
    val extraModules = getExtraModules()
    (myTree.model as DefaultTreeModel).reload()
    createModuleNodes(extraModules)
    uiDisposed = false
  }

  override fun createComponent(): JComponent {
    val contents = super.createComponent()
    return JBLoadingPanel(BorderLayout(), this).also {
      loadingPanel = it
      it.setLoadingText("Syncing Project with Gradle")
      it.add(contents, BorderLayout.CENTER)
    }
  }

  private fun createModuleNodes(extraModules: List<PsModule>) {
    context.project.forEachModule(Consumer<PsModule> { this.addConfigurableFor(it) })
    extraModules.forEach(Consumer<PsModule> { this.addConfigurableFor(it) })
  }

  private fun addConfigurableFor(module: PsModule) {
    createConfigurableFor(module)
      ?.let { MasterDetailsComponent.MyNode(it) }
      ?.also { myRoot.add(it) }
  }

  protected abstract fun createConfigurableFor(module: PsModule): NamedConfigurable<out PsModule>?

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
    Disposer.dispose(this)
  }

  override fun dispose() {
    toolWindowHeader?.let { Disposer.dispose(it) }
  }

  override fun enableSearch(option: String): Runnable? = null

  override fun isModified(): Boolean = context.project.isModified

  override fun apply() = context.project.applyChanges()

  override fun setHistory(history: History?) = super<MasterDetailsComponent>.setHistory(history)
}
