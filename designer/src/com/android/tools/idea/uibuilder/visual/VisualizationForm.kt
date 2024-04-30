/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.android.resources.ResourceFolderType
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.adtui.common.border
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.editor.PanZoomListener
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.error.IssueListener
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.layout.SceneViewAlignment
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceIssueListenerImpl
import com.android.tools.idea.common.surface.LayoutScannerEnabled
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.visual.analytics.trackOpenConfigSet
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import com.intellij.util.ArrayUtil
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.DefaultFocusTraversalPolicy
import java.awt.event.AdjustmentEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.VisibleForTesting

/**
 * Form of layout visualization which offers multiple previews for different devices in the same
 * time. It provides a convenient way to user to preview the layout in different devices.
 */
class VisualizationForm(
  private val project: Project,
  parentDisposable: Disposable,
  private val initializer: ContentInitializer,
) : VisualizationContent, ConfigurationSetListener, ResourceChangeListener, PanZoomListener {
  private val surface: NlDesignSurface
  private val myWorkBench: WorkBench<DesignSurface<*>>
  private val myRoot = JPanel(BorderLayout())
  private var myFile: VirtualFile? = null
  private val myResourceNotifyingFilesLock = ReentrantLock()

  @GuardedBy("myResourceNotifyingFilesLock")
  private val myResourceNotifyingFiles: MutableSet<VirtualFile> = HashSet()
  private var isActive = false
  private var myContentPanel: JComponent? = null
  private val myActionToolbarPanel: JComponent
  private val myCancelRenderingTaskLock = ReentrantLock()

  @GuardedBy("myCancelRenderingTaskLock") private var myCancelRenderingTask: Runnable? = null

  /**
   * Contains the editor that is currently being loaded. Once the file is loaded, myPendingEditor
   * will be null.
   */
  private var myPendingEditor: FileEditor? = null
  private var myEditor: FileEditor? = null
  private var myCurrentConfigurationSet: ConfigurationSet
  private var myCurrentModelsProvider: VisualizationModelsProvider
  private val myLayoutOption =
    SurfaceLayoutOption(
      "Layout",
      GridSurfaceLayoutManager(
        NlConstants.DEFAULT_SCREEN_OFFSET_X,
        NlConstants.DEFAULT_SCREEN_OFFSET_Y,
        GRID_HORIZONTAL_SCREEN_DELTA,
        VERTICAL_SCREEN_DELTA,
        false,
      ),
      false,
      SceneViewAlignment.LEFT,
    )
  private val myUpdateQueue: MergingUpdateQueue

  /** [CompletableFuture] of the next model load. This is kept so the load can be cancelled. */
  private var myCancelPendingModelLoad = AtomicBoolean(false)
  private val myProgressIndicator = EmptyProgressIndicator()
  private val analyticsManager: NlAnalyticsManager
    get() = surface.analyticsManager

  var editor: FileEditor?
    get() = myEditor
    private set(editor) {
      if (editor !== myEditor) {
        myEditor = editor
        surface.fileEditorDelegate = editor
        updateActionToolbar(myActionToolbarPanel)
      }
    }

  val component: JComponent = myRoot

  private val visualLintHandler: VisualizationFormVisualLintHandler
  private val issueListener: IssueListener

  init {
    Disposer.register(parentDisposable, this)
    myCurrentConfigurationSet =
      VisualizationToolSettings.getInstance().globalState.lastSelectedConfigurationSet
    myCurrentModelsProvider = myCurrentConfigurationSet.createModelsProvider(this)
    val config = LayoutScannerEnabled()
    // Custom issue panel integration used.
    config.isIntegrateWithDefaultIssuePanel = false
    surface =
      NlDesignSurface.builder(project, this@VisualizationForm) { surface: NlDesignSurface, model: NlModel ->
          val sceneManager = LayoutlibSceneManager(model, surface, config)
          sceneManager.setListenResourceChange(false)
          sceneManager.setShowDecorations(
            VisualizationToolSettings.getInstance().globalState.showDecoration
          )
          sceneManager.setUpdateAndRenderWhenActivated(false)
          sceneManager.setUseImagePool(false)
          // 0.5f makes it spend 50% memory.
          sceneManager.setQuality(0.5f)
          sceneManager.setLogRenderErrors(false)
          sceneManager
        }
        .setActionManagerProvider { surface: DesignSurface<*> ->
          VisualizationActionManager((surface as NlDesignSurface?)!!) { myCurrentModelsProvider }
        }
        .setInteractionHandlerProvider { surface: DesignSurface<*> ->
          VisualizationInteractionHandler(surface) { myCurrentModelsProvider }
        }
        .setLayoutOption(myLayoutOption)
        .setMaxScale(4.0)
        .setSupportedActions(VISUALIZATION_SUPPORTED_ACTIONS)
        .setDelegateDataProvider {
          when {
            VIRTUAL_FILE.`is`(it) -> myFile
            VISUALIZATION_FORM.`is`(it) -> this
            else -> null
          }
        }
        .build()
    surface.setSceneViewAlignment(SceneViewAlignment.LEFT)
    surface.addPanZoomListener(this)
    issueListener = DesignSurfaceIssueListenerImpl(surface).apply { surface.addIssueListener(this) }
    updateScreenMode()
    surface.name = VISUALIZATION_DESIGN_SURFACE_NAME
    surface.zoomController.storeId = VISUALIZATION_DESIGN_SURFACE_NAME
    myWorkBench = WorkBench(project, "Visualization", null, this)
    myWorkBench.setLoadingText("Loading...")
    myWorkBench.setToolContext(surface)
    myActionToolbarPanel = createToolbarPanel()
    myRoot.add(myActionToolbarPanel, BorderLayout.NORTH)
    myRoot.add(myWorkBench, BorderLayout.CENTER)
    myRoot.isFocusCycleRoot = true
    myRoot.focusTraversalPolicy = VisualizationTraversalPolicy(surface)
    myUpdateQueue =
      MergingUpdateQueue(
        "visualization.form.update",
        NlModel.DELAY_AFTER_TYPING_MS,
        true,
        null,
        this,
        null,
        Alarm.ThreadToUse.POOLED_THREAD,
      )
    myUpdateQueue.setRestartTimerOnAdd(true)

    visualLintHandler = VisualizationFormVisualLintHandler(this, project, surface.issueModel)
  }

  private fun createToolbarPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    panel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, border)
    updateActionToolbar(panel)
    return panel
  }

  private fun updateActionToolbar(toolbarPanel: JComponent) {
    toolbarPanel.removeAll()
    val group = DefaultActionGroup()
    val virtualFile = myFile
    val fileName = virtualFile?.name ?: ""
    // Add an empty action and disable it permanently for displaying file name.
    group.add(TextLabelAction(fileName))
    group.addSeparator()
    group.add(ConfigurationSetMenuAction(myCurrentConfigurationSet))
    group.addAll(myCurrentModelsProvider.createActions())
    val viewOptions = DropDownAction(null, "View Options", StudioIcons.Common.VISIBILITY_INLINE)
    viewOptions.add(ToggleShowDecorationAction())
    viewOptions.isPopup = true
    group.add(viewOptions)
    group.add(AddCustomConfigurationSetAction())
    group.add(RemoveCustomConfigurationSetAction(myCurrentConfigurationSet))
    // Use ActionPlaces.EDITOR_TOOLBAR as place to update the ui when appearance is changed.
    // In IJ's implementation, only the actions in ActionPlaces.EDITOR_TOOLBAR toolbar will be
    // tweaked when ui is changed.
    // See com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.tweakActionComponentUI()
    val actionToolbar =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, group, true)
    actionToolbar.setTargetComponent(surface)
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
    val toolbarComponent = actionToolbar.component
    toolbarComponent.border = BorderFactory.createEmptyBorder(0, 6, 0, 0)
    toolbarPanel.add(toolbarComponent, BorderLayout.CENTER)
    val lintGroup = DefaultActionGroup()
    lintGroup.add(IssuePanelToggleAction())
    val lintToolbar =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, lintGroup, true)
    lintToolbar.setTargetComponent(surface)
    lintToolbar.updateActionsImmediately()
    ActionToolbarUtil.makeToolbarNavigable(lintToolbar)
    toolbarPanel.add(lintToolbar.component, BorderLayout.EAST)
  }

  private fun updateScreenMode() {
    if (myCurrentConfigurationSet === ConfigurationSet.ColorBlindMode) {
      surface.setScreenViewProvider(NlScreenViewProvider.COLOR_BLIND, false)
    } else {
      surface.setScreenViewProvider(NlScreenViewProvider.VISUALIZATION, false)
    }
  }

  override fun dispose() {
    deactivate()
    val registeredFiles: Set<VirtualFile>
    myResourceNotifyingFilesLock.lock()
    try {
      registeredFiles = HashSet(myResourceNotifyingFiles)
      myResourceNotifyingFiles.clear()
    } finally {
      myResourceNotifyingFilesLock.unlock()
    }
    for (file in registeredFiles) {
      unregisterResourceNotification(file)
    }
    removeAndDisposeModels(surface.models)
    surface.removeIssueListener(issueListener)
  }

  private fun removeAndDisposeModels(models: List<NlModel>) {
    visualLintHandler.clearIssueProvider()
    for (model in models) {
      surface.removeModel(model)
      Disposer.dispose(model)
    }
  }

  /**
   * Specifies the next editor the preview should be shown for. The update of the preview may be
   * delayed.
   *
   * @return true on success. False if the preview update is not possible (e.g. the file for the
   *   editor cannot be found).
   */
  override fun setNextEditor(editor: FileEditor): Boolean {
    if (getFolderType(editor.file) != ResourceFolderType.LAYOUT) {
      return false
    }
    myPendingEditor = editor
    myFile = editor.file
    myCancelPendingModelLoad.set(true)
    if (isActive) {
      if (myContentPanel == null) {
        initializer.initContent(project, this) { initModel() }
      } else {
        initModel()
      }
    }
    return true
  }

  /** Called by [VisualizationForm.ContentInitializer] to initialize the content panel. */
  fun createContentPanel() {
    if (Disposer.isDisposed(this)) {
      return
    }
    if (myContentPanel == null) {
      val panel = JPanel(BorderLayout())
      panel.add(surface, BorderLayout.CENTER)
      myContentPanel = panel
      myWorkBench.init(myContentPanel!!, surface, ImmutableList.of(), false)
      // The toolbar is in the root panel which contains myWorkBench. To traverse to toolbar we need
      // to traverse out from myWorkBench.
      myWorkBench.isFocusCycleRoot = false
    }
  }

  /** Called by [VisualizationForm.ContentInitializer] when content is still loading. */
  fun showLoadingMessage() {
    myWorkBench.setLoadingText("Waiting for build to finish...")
  }

  /**
   * Called by [VisualizationForm.ContentInitializer] when build was either cancelled or there was
   * an error.
   */
  fun showErrorMessage() {
    myWorkBench.loadingStopped("Previews are unavailable until after a successful project sync")
  }

  private fun initModel() {
    DumbService.getInstance(project).smartInvokeLater { initNeleModelWhenSmart() }
  }

  @UiThread
  private fun initNeleModelWhenSmart() {
    setNoActiveModel()
    interruptRendering()
    if (myFile == null) {
      return
    }
    val file = PsiManager.getInstance(project).findFile(myFile!!)
    var facet: AndroidFacet? = null
    updateActionToolbar(myActionToolbarPanel)

    // isRequestCancelled allows us to cancel the ongoing computation if it is not needed anymore.
    // There is no need to hold
    // to the Future since Future.cancel does not really interrupt the work.
    val isRequestCancelled = AtomicBoolean(false)
    myCancelPendingModelLoad = isRequestCancelled
    // Asynchronously load the model and refresh the preview once it's ready
    CompletableFuture.supplyAsync(
        {
          facet =
            (if (file != null) AndroidFacet.getInstance(file) else null)
              ?: return@supplyAsync emptyList()
          // Hide the content while adding the models.
          val models = myCurrentModelsProvider.createNlModels(this, file!!, facet!!)
          if (models.isEmpty()) {
            myWorkBench.showLoading("No Device Found")
            return@supplyAsync null
          }
          models
        },
        AppExecutorUtil.getAppExecutorService(),
      )
      .thenApplyAsync(
        { models: List<NlModel>? ->
          if (models == null || isRequestCancelled.get()) {
            unregisterResourceNotification(myFile)
            return@thenApplyAsync emptyList()
          }
          myWorkBench.showContent()
          interruptRendering()
          ApplicationManager.getApplication().invokeLater {
            surface.registerIndicator(myProgressIndicator)
          }
          models
        },
        EdtExecutorService.getInstance(),
      )
      .thenCompose { models: List<NlModel> ->
        // In visualization tool, we add model and layout the scroll pane before rendering
        CompletableFuture.allOf(
            *models.map { model -> surface.addModelWithoutRender(model) }.toTypedArray()
          )
          .whenCompleteAsync(
            { _, _ ->
              // Re-layout and set scale before rendering. This may be processed delayed but we have
              // known the preview number and sizes because the
              // models are added, so it would layout correctly.
              surface.invalidate()
              val lastScaling =
                VisualizationToolProjectSettings.getInstance(project).projectState.scale
              if (!surface.zoomController.setScale(lastScaling)) {
                // Update scroll area because the scaling doesn't change, which keeps the old scroll
                // area and may not suitable to new
                // configuration set.
                surface.revalidateScrollArea()
              }
            },
            EdtExecutorService.getInstance(),
          )
          .thenCompose { renderCurrentModels() }
          // We render the model sequentially to avoid memory and performance issue.
          .thenRunAsync(
            {
              ApplicationManager.getApplication().invokeLater {
                surface.unregisterIndicator(myProgressIndicator)
              }
              if (!isRequestCancelled.get() && facet?.isDisposed == false) {
                activateEditor(models.isNotEmpty())
              } else {
                removeAndDisposeModels(models)
              }
            },
            EdtExecutorService.getInstance(),
          )
      }
  }

  // A file editor was closed. If our editor no longer exists, cleanup our state.
  override fun fileClosed(editorManager: FileEditorManager, file: VirtualFile) {
    if (myEditor == null) {
      setNoActiveModel()
    } else if (file == myFile) {
      if (ArrayUtil.find(editorManager.getAllEditors(file), myEditor) < 0) {
        setNoActiveModel()
      }
    }
    if (myPendingEditor != null && file == myPendingEditor!!.file) {
      if (ArrayUtil.find(editorManager.getAllEditors(file), myPendingEditor) < 0) {
        myPendingEditor = null
      }
    }
  }

  override fun getConfigurationSet(): ConfigurationSet = myCurrentConfigurationSet

  override fun setConfigurationSet(configurationSet: ConfigurationSet) {
    // TODO: We should avoid calling the callback function actively. ConfigurationSetListener needs
    // to be refactored.
    onSelectedConfigurationSetChanged(configurationSet)
  }

  private fun setNoActiveModel() {
    myCancelPendingModelLoad.set(true)
    editor = null
    myWorkBench.setFileEditor(null)
    removeAndDisposeModels(surface.models)
  }

  private fun activateEditor(hasModel: Boolean) {
    myCancelPendingModelLoad.set(true)
    if (!hasModel) {
      editor = null
      myWorkBench.setFileEditor(null)
    } else {
      editor = myPendingEditor
      myPendingEditor = null
      registerResourceNotification(myFile)
      myWorkBench.setFileEditor(myEditor)
    }
  }

  private fun registerResourceNotification(file: VirtualFile?) {
    if (file == null) {
      return
    }
    val facet =
      SlowOperations.allowSlowOperations(
        ThrowableComputable { AndroidFacet.getInstance(file, project) }
      )
    if (facet != null) {
      myResourceNotifyingFilesLock.lock()
      try {
        if (!myResourceNotifyingFiles.add(file)) {
          // File is registered already.
          return
        }
      } finally {
        myResourceNotifyingFilesLock.unlock()
      }
      val manager = ResourceNotificationManager.getInstance(project)
      manager.addListener(this, facet, file, null)
    }
  }

  private fun unregisterResourceNotification(file: VirtualFile?) {
    if (file == null) {
      return
    }
    val facet =
      SlowOperations.allowSlowOperations(
        ThrowableComputable { AndroidFacet.getInstance(file, project) }
      )
    if (facet != null) {
      myResourceNotifyingFilesLock.lock()
      try {
        if (!myResourceNotifyingFiles.remove(file)) {
          // File is not registered.
          return
        }
      } finally {
        myResourceNotifyingFilesLock.unlock()
      }
      val manager = ResourceNotificationManager.getInstance(project)
      manager.removeListener(this, facet, myFile, null)
    }
  }

  override fun resourcesChanged(reasons: ImmutableSet<ResourceNotificationManager.Reason>) {
    var needsRenderModels = false
    for (reason in reasons) {
      when (reason) {
        ResourceNotificationManager.Reason.RESOURCE_EDIT,
        ResourceNotificationManager.Reason.EDIT,
        ResourceNotificationManager.Reason.IMAGE_RESOURCE_CHANGED,
        ResourceNotificationManager.Reason.GRADLE_SYNC,
        ResourceNotificationManager.Reason.PROJECT_BUILD,
        ResourceNotificationManager.Reason.VARIANT_CHANGED,
        ResourceNotificationManager.Reason.SDK_CHANGED,
        ResourceNotificationManager.Reason.CONFIGURATION_CHANGED -> {
          needsRenderModels = true
          break
        }
      }
    }
    if (needsRenderModels) {
      myUpdateQueue.queue(
        object : Update("update") {
          override fun run() {
            // Show and hide progress indicator during rendering.
            ApplicationManager.getApplication().invokeLater {
              surface.registerIndicator(myProgressIndicator)
            }
            renderCurrentModels().thenRun {
              ApplicationManager.getApplication().invokeLater {
                surface.unregisterIndicator(myProgressIndicator)
              }
            }
          }

          override fun canEat(update: Update): Boolean {
            return true
          }
        }
      )
    }
  }

  private fun renderCurrentModels(): CompletableFuture<Void> {
    interruptRendering()
    val isRenderingCanceled = AtomicBoolean(false)
    val cancelTask = Runnable { isRenderingCanceled.set(true) }
    myCancelRenderingTaskLock.lock()
    myCancelRenderingTask =
      try {
        cancelTask
      } finally {
        myCancelRenderingTaskLock.unlock()
      }
    var renderFuture = CompletableFuture.completedFuture<Void?>(null)
    visualLintHandler.clearIssueProviderAndBaseConfigurationIssue()

    // This render the added components.
    for (manager in surface.sceneManagers) {
      visualLintHandler.setupForLayoutlibSceneManager(manager) {
        !isActive || isRenderingCanceled.get()
      }
      renderFuture =
        renderFuture.thenCompose {
          if (isRenderingCanceled.get()) {
            return@thenCompose CompletableFuture.completedFuture<Void?>(null)
          } else {
            val modelUpdateFuture = manager.updateModelAsync()
            if (isRenderingCanceled.get()) {
              return@thenCompose CompletableFuture.completedFuture<Void?>(null)
            } else {
              return@thenCompose modelUpdateFuture.thenCompose { manager.requestRenderAsync() }
            }
          }
        }
    }
    return renderFuture.thenRun { surface.issueModel.updateErrorsList() }
  }

  private fun interruptRendering() {
    myCancelRenderingTaskLock.lock()
    val task: Runnable? =
      try {
        myCancelRenderingTask
      } finally {
        myCancelRenderingTaskLock.unlock()
      }
    task?.run()
  }

  /** Re-enables updates for this preview form. See [.deactivate] */
  override fun activate() {
    if (isActive) {
      return
    }
    registerResourceNotification(myFile)
    isActive = true
    if (myContentPanel == null) {
      initializer.initContent(project, this) { initModel() }
    } else {
      initModel()
    }
    surface.activate()
    analyticsManager.trackVisualizationToolWindow(true)
    visualLintHandler.onActivate()
    IssuePanelService.getDesignerCommonIssuePanel(project)
      ?.addIssueSelectionListener(surface.issueListener, surface)
  }

  /**
   * Disables the updates for this preview form. Any changes to resources or the layout won't update
   * this preview until [.activate] is called.
   */
  override fun deactivate() {
    interruptRendering()
    if (!isActive) {
      return
    }
    myCancelPendingModelLoad.set(true)
    surface.deactivate()
    isActive = false
    unregisterResourceNotification(myFile)
    if (myContentPanel != null) {
      setNoActiveModel()
    }
    analyticsManager.trackVisualizationToolWindow(false)
    visualLintHandler.onDeactivate()
    IssuePanelService.getDesignerCommonIssuePanel(project)
      ?.removeIssueSelectionListener(surface.issueListener)
    myFile?.let { FileEditorManager.getInstance(project).getSelectedEditor(it)?.selectNotify() }
  }

  override fun onSelectedConfigurationSetChanged(newConfigurationSet: ConfigurationSet) {
    if (myCurrentConfigurationSet !== newConfigurationSet) {
      myCurrentConfigurationSet = newConfigurationSet
      trackOpenConfigSet(surface, myCurrentConfigurationSet)
      VisualizationToolSettings.getInstance().globalState.lastSelectedConfigurationSet =
        newConfigurationSet
      myCurrentModelsProvider = newConfigurationSet.createModelsProvider(this)
      surface.layoutManagerSwitcher?.currentLayout?.value = myLayoutOption
      refresh()
    }
  }

  override fun onCurrentConfigurationSetUpdated() {
    refresh()
  }

  /** Refresh the previews. This recreates the [NlModel]s from the current [ConfigurationSet]. */
  private fun refresh() {
    updateScreenMode()
    updateActionToolbar(myActionToolbarPanel)
    // Dispose old models and create new models with new configuration set.
    initModel()
  }

  override fun zoomChanged(previousScale: Double, newScale: Double) {
    VisualizationToolProjectSettings.getInstance(project).projectState.scale =
      surface.zoomController.scale
  }

  override fun panningChanged(adjustmentEvent: AdjustmentEvent) = Unit

  /** A disabled action for displaying text in action toolbar. It does nothing. */
  private class TextLabelAction(private val text: String) : AnAction(null as String?) {

    override fun actionPerformed(e: AnActionEvent) = Unit

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.setText(text, false)
      e.presentation.isEnabled = false
    }

    override fun displayTextInToolbar() = true
  }

  private inner class ToggleShowDecorationAction : ToggleAction("Show System UI") {
    override fun isSelected(e: AnActionEvent): Boolean =
      VisualizationToolSettings.getInstance().globalState.showDecoration

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      VisualizationToolSettings.getInstance().globalState.showDecoration = state
      val surface = e.getData(DESIGN_SURFACE) as? NlDesignSurface ?: return
      val visualizationForm = e.getData(VISUALIZATION_FORM) ?: return
      surface.models
        .mapNotNull { model: NlModel -> surface.getSceneManager(model) }
        .forEach { manager -> manager.setShowDecorations(state) }
      surface.requestRender().thenRun {
        if (!Disposer.isDisposed(visualizationForm.myWorkBench)) {
          visualizationForm.myWorkBench.showContent()
        }
      }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private class VisualizationTraversalPolicy(private val mySurface: DesignSurface<*>) :
    DefaultFocusTraversalPolicy() {
    override fun getDefaultComponent(aContainer: Container): Component {
      return mySurface.layeredPane
    }
  }

  private class EmptyProgressIndicator : AbstractProgressIndicatorBase() {
    override fun isRunning(): Boolean = true
  }

  interface ContentInitializer {
    fun initContent(project: Project, form: VisualizationForm, onComplete: () -> Unit)
  }

  companion object {
    @VisibleForTesting const val VISUALIZATION_DESIGN_SURFACE_NAME = "Layout Validation"
    private val VISUALIZATION_SUPPORTED_ACTIONS: Set<NlSupportedActions> =
      ImmutableSet.of(NlSupportedActions.TOGGLE_ISSUE_PANEL)

    /** horizontal gap between different previews */
    @SwingCoordinate private val GRID_HORIZONTAL_SCREEN_DELTA = 100

    /** vertical gap between different previews */
    @SwingCoordinate private val VERTICAL_SCREEN_DELTA = 48

    @JvmField
    val VISUALIZATION_FORM = DataKey.create<VisualizationForm>(VisualizationForm::class.java.name)
  }
}
