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
package com.android.tools.idea.compose.preview

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.model.*
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutlibInteractionHandler
import com.android.tools.idea.common.util.*
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.compose.preview.PreviewGroup.Companion.ALL_PREVIEW_GROUP
import com.android.tools.idea.compose.preview.actions.ForceCompileAndRefreshAction
import com.android.tools.idea.compose.preview.actions.PreviewSurfaceActionManager
import com.android.tools.idea.compose.preview.actions.requestBuildForSurface
import com.android.tools.idea.compose.preview.analytics.InteractivePreviewUsageTracker
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager
import com.android.tools.idea.compose.preview.literals.LiveLiteralsManager
import com.android.tools.idea.compose.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeSceneUpdateListener
import com.android.tools.idea.compose.preview.util.*
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.COMPOSE_PREVIEW_BUILD_ON_SAVE
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.classloading.LiveLiteralsTransform
import com.android.tools.idea.run.util.StopWatch
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.application.subscribe
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.Color
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiFunction
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.OverlayLayout
import kotlin.properties.Delegates

/**
 * Background color for the surface while "Interactive" is enabled.
 */
private val INTERACTIVE_BACKGROUND_COLOR = JBColor(Color(203, 210, 217),
                                                   Color(70, 69, 77))

/**
 * [NlModel] associated preview data
 *
 * @param previewElement the [PreviewElement] associated to this model
 */
private class ModelDataContext(private val composePreviewManager: ComposePreviewManager,
                               private val previewElement: PreviewElement) : DataContext {
  override fun getData(dataId: String): Any? = when (dataId) {
    COMPOSE_PREVIEW_MANAGER.name -> composePreviewManager
    COMPOSE_PREVIEW_ELEMENT.name -> previewElement
    else -> null
  }
}

/**
 * Returns true if change of values of any [LayoutlibSceneManager] properties would require necessary re-inflation. Namely, if we change
 * [LayoutlibSceneManager.isShowingDecorations], [LayoutlibSceneManager.isUsePrivateClassLoader] or if we transition from interactive to
 * static preview mode (not the other way around though) we need to re-inflate in order to update the preview layout.
 */
fun LayoutlibSceneManager.changeRequiresReinflate(showDecorations: Boolean, isInteractive: Boolean, usePrivateClassLoader: Boolean) =
  (showDecorations != isShowingDecorations) ||
  (interactive && !isInteractive) || // transition from interactive to static
  (usePrivateClassLoader != isUsePrivateClassLoader)


/**
 * Sets up the given [sceneManager] with the right values to work on the Compose Preview. Currently, this
 * will configure if the preview elements will be displayed with "full device size" or simply containing the
 * previewed components (shrink mode).
 * @param showDecorations when true, the rendered content will be shown with the full device size specified in
 * the device configuration and with the frame decorations.
 * @param isInteractive whether the scene displays an interactive preview.
 * @param requestPrivateClassLoader whether the scene manager should use a private ClassLoader.
 */
private fun configureLayoutlibSceneManager(sceneManager: LayoutlibSceneManager,
                                           showDecorations: Boolean,
                                           isInteractive: Boolean,
                                           requestPrivateClassLoader: Boolean,
                                           liveLiteralsClasses: Set<String>,
                                           forceReinflate: Boolean = true): LayoutlibSceneManager =
  sceneManager.apply {
    val isLiveLiteralsEnabled = liveLiteralsClasses.isNotEmpty()
    val usePrivateClassLoader = requestPrivateClassLoader || isLiveLiteralsEnabled
    val reinflate = forceReinflate || changeRequiresReinflate(showDecorations, isInteractive, usePrivateClassLoader)
    setTransparentRendering(!showDecorations)
    setShrinkRendering(!showDecorations)
    setUseImagePool(false)
    interactive = isInteractive
    isUsePrivateClassLoader = usePrivateClassLoader
    if (isLiveLiteralsEnabled) {
      setProjectClassesTransform {
        LiveLiteralsTransform(it) {
          // For now, we only instrument the currently open class and inner classes. However, if we want to allow changes in
          // other files, we would need to instrument all.
          className, _ -> liveLiteralsClasses.contains(className.substringBefore('$'))
        }
      }
    }
    setQuality(0.7f)
    setShowDecorations(showDecorations)
    if (reinflate) {
      forceReinflate()
    }
  }

/**
 * Key for the persistent group state for the Compose Preview.
 */
private const val SELECTED_GROUP_KEY = "selectedGroup"

/**
 * Key for the persistent build on save state for the Compose Preview.
 */
private const val BUILD_ON_SAVE_KEY = "buildOnSave"

/**
 * A [PreviewRepresentation] that provides a compose elements preview representation of the given `psiFile`.
 *
 * A [component] is implied to display previews for all declared `@Composable` functions that also use the `@Preview` (see
 * [com.android.tools.idea.compose.preview.util.PREVIEW_ANNOTATION_FQN]) annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a `@Composable` functions.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param previewProvider [PreviewElementProvider] to obtain the [PreviewElement]s.
 */
class ComposePreviewRepresentation(psiFile: PsiFile,
                                   previewProvider: PreviewElementProvider) :
  PreviewRepresentation, ComposePreviewManagerEx, UserDataHolderEx by UserDataHolderBase(), AndroidCoroutinesAware {
  private val LOG = Logger.getInstance(ComposePreviewRepresentation::class.java)
  private val project = psiFile.project
  private val psiFilePointer = SmartPointerManager.createPointer(psiFile)

  private val projectBuildStatusManager = ProjectBuildStatusManager(this, psiFile)

  /**
   * [PreviewElementProvider] used to save the result of a call to `previewProvider`. Calls to `previewProvider` can potentially
   * be slow. This saves the last result and it is refreshed on demand when we know is not running on the UI thread.
   */
  private val memoizedElementsProvider = MemoizedPreviewElementProvider(previewProvider,
                                                                        ModificationTracker {
                                                                          ReadAction.compute<Long, Throwable> {
                                                                            psiFilePointer.element?.modificationStamp ?: -1
                                                                          }
                                                                        })
  private val previewElementProvider = PreviewFilters(memoizedElementsProvider)

  /**
   * A [UniqueTaskCoroutineLauncher] used to run the image rendering. This ensures that only one image rendering is running at time.
   */
  private val uniqueRefreshLauncher = UniqueTaskCoroutineLauncher(this, "Compose Preview refresh")

  override var groupFilter: PreviewGroup by Delegates.observable(ALL_PREVIEW_GROUP) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      LOG.debug("New group preview element selection: $newValue")
      previewElementProvider.groupNameFilter = newValue
      // Force refresh to ensure the new preview elements are picked up
      forceRefresh()
    }
  }

  @Volatile
  override var availableGroups: Set<PreviewGroup> = emptySet()

  @Volatile
  private var interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
  private val navigationHandler = PreviewNavigationHandler()

  private val fpsCounter = FpsCalculator { System.nanoTime() }

  override fun setInteractivePreviewElementInstance(previewElement: PreviewElementInstance?) {
    if (previewElementProvider.instanceFilter != previewElement) {
      LOG.debug("New single preview element focus: $previewElement")
      val isInteractive = previewElement != null
      // The order matters because we first want to change the composable being previewed and then start interactive loop when enabled
      // but we want to stop the loop first and then change the composable when disabled
      if (isInteractive) { // Enable interactive
        interactiveMode = ComposePreviewManager.InteractiveMode.STARTING
        val quickRefresh = shouldQuickRefresh() // We should call this before assigning newValue to instanceIdFilter
        val peerPreviews = previewElementProvider.previewElements.count()
        previewElementProvider.instanceFilter = previewElement
        sceneComponentProvider.enabled = false
        val startUpStart = System.currentTimeMillis()
        forceRefresh(quickRefresh).invokeOnCompletion {
          surface.layoutlibSceneManagers.forEach { it.resetTouchEventsCounter() }
          InteractivePreviewUsageTracker.getInstance(surface).logStartupTime(
            (System.currentTimeMillis() - startUpStart).toInt(), peerPreviews)
          fpsCounter.resetAndStart()
          ticker.start()
          delegateInteractionHandler.delegate = interactiveInteractionHandler

          if (StudioFlags.COMPOSE_ANIMATED_PREVIEW_SHOW_CLICK.get()) {
            // While in interactive mode, display a small ripple when clicking
            surface.enableMouseClickDisplay()
          }
          surface.background = INTERACTIVE_BACKGROUND_COLOR
          interactiveMode = ComposePreviewManager.InteractiveMode.READY
        }
      }
      else { // Disable interactive
        interactiveMode = ComposePreviewManager.InteractiveMode.STOPPING
        surface.background = defaultSurfaceBackground
        surface.disableMouseClickDisplay()
        delegateInteractionHandler.delegate = staticPreviewInteractionHandler
        ticker.stop()
        sceneComponentProvider.enabled = true
        previewElementProvider.clearInstanceIdFilter()
        logInteractiveSessionMetrics()
        forceRefresh().invokeOnCompletion {
          interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
        }
      }
    }
  }

  private val animationInspection = AtomicBoolean(false)

  override var animationInspectionPreviewElementInstance:
    PreviewElementInstance? by Delegates.observable(null as PreviewElementInstance?) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      animationInspection.set(newValue != null)
      if (animationInspection.get()) {
        LOG.debug("Animation Preview open for preview: $newValue")
        previewElementProvider.instanceFilter = newValue
        sceneComponentProvider.enabled = false
        // Open the animation inspection panel
        mainPanelSplitter.secondComponent = ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, this) {
          // Close this inspection panel, making all the necessary UI changes (e.g. changing background and refreshing the preview) before
          // opening a new one.
          animationInspectionPreviewElementInstance = null
        }
        surface.background = INTERACTIVE_BACKGROUND_COLOR
      }
      else {
        // Close the animation inspection panel
        surface.background = defaultSurfaceBackground
        ComposePreviewAnimationManager.closeCurrentInspector()
        mainPanelSplitter.secondComponent = null
        sceneComponentProvider.enabled = true
        previewElementProvider.instanceFilter = null
      }
      forceRefresh()
    }
  }

  private val liveLiteralsManager = LiveLiteralsManager(project, this, psiFilePointer) {
    surface.layoutlibSceneManagers.forEach {
      it.forceReinflate()
      it.requestRender()
    }
  }

  override var isLiveLiteralsEnabled: Boolean
    get() = liveLiteralsManager.isEnabled
    set(value) {
      liveLiteralsManager.isEnabled = value
      forceRefresh()
    }

  /**
   * List of classes to instrument for Live Literals. For now, we only instrument the classes that are part of the current editor.
   */
  private var cachedLiveLiteralsClasses = emptySet<String>()

  override var showDebugBoundaries: Boolean = false
    set(value) {
      field = value
      forceRefresh()
    }

  private val sceneComponentProvider = ComposeSceneComponentProvider()
  private val delegateInteractionHandler = DelegateInteractionHandler()
  private val surface = NlDesignSurface.builder(project, this)
    .setIsPreview(true)
    .showModelNames()
    .setNavigationHandler(navigationHandler)
    .setLayoutManager(DEFAULT_PREVIEW_LAYOUT_MANAGER)
    .setActionManagerProvider { surface -> PreviewSurfaceActionManager(surface) }
    .setInteractionHandlerProvider { delegateInteractionHandler }
    .setActionHandler { surface -> PreviewSurfaceActionHandler(surface) }
    .setSceneManagerProvider { surface, model ->
      LayoutlibSceneManager(model, surface, sceneComponentProvider, ComposeSceneUpdateListener())
    }
    .setEditable(true)
    .setDelegateDataProvider {
      return@setDelegateDataProvider when (it) {
        COMPOSE_PREVIEW_MANAGER.name -> this
        // The Compose preview NlModels do not point to the actual file but to a synthetic file
        // generated for Layoutlib. This ensures we return the right file.
        CommonDataKeys.VIRTUAL_FILE.name -> psiFilePointer.virtualFile
        else -> null
      }
    }
    .setSelectionModel(NopSelectionModel)
    .build()
    .apply {
      setScreenViewProvider(NlScreenViewProvider.COMPOSE, false)
      setMaxFitIntoZoomLevel(2.0) // Set fit into limit to 200%
    }
  private val staticPreviewInteractionHandler = NlInteractionHandler(surface)
  private val interactiveInteractionHandler by lazy { LayoutlibInteractionHandler(surface) }

  /**
   * Default background used by the surface. This is used to restore the state after disabling the interactive preview.
   */
  private val defaultSurfaceBackground: Color = surface.background

  private val modelUpdater: NlModel.NlModelUpdaterInterface = DefaultModelUpdater()

  /**
   * List of [PreviewElement] being rendered by this editor
   */
  var previewElements: List<PreviewElement> = emptyList()

  /**
   * Counts the current number of simultaneous executions of [refresh] method. Being inside the [refresh] indicates that the this preview
   * is being refreshed. Even though [uniqueRefreshLauncher] guarantees that only at most a single refresh happens at any point in time,
   * there might be several simultaneous calls to [refresh] method and therefore we need a counter instead of boolean flag.
   */
  private val refreshCallsCount = AtomicInteger(0)

  /**
   * This field will be false until the preview has rendered at least once. If the preview has not rendered once
   * we do not have enough information about errors and the rendering to show the preview. Once it has rendered,
   * even with errors, we can display additional information about the state of the preview.
   */
  private val hasRenderedAtLeastOnce = AtomicBoolean(false)

  /**
   * Callback first time after the preview has loaded the initial state and it's ready to restore
   * any saved state.
   */
  private var onRestoreState: (() -> Unit)? = null

  private val notificationsPanel = NotificationPanel(
    ExtensionPointName.create("com.android.tools.idea.compose.preview.composeEditorNotificationProvider"))

  private val actionsToolbar = ActionsToolbar(this@ComposePreviewRepresentation, surface)

  /**
   * Vertical splitter where the top component is the main Compose Preview panel and the bottom component, when visible, is an auxiliary
   * panel associated with the preview. For example, it can be an animation inspector that lists all the animations the preview has.
   */
  private val mainPanelSplitter = JBSplitter(true, 0.7f).apply { dividerWidth = 3 }

  /**
   * [WorkBench] used to contain all the preview elements.
   */
  private val workbench = WorkBench<DesignSurface>(project, "Compose Preview", null, this).apply {
    val contentPanel = JPanel(BorderLayout()).apply {
      add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)

      val overlayPanel = object : JPanel() {
        // Since the overlay panel is transparent, we can not use optimized drawing or it will produce rendering artifacts.
        override fun isOptimizedDrawingEnabled(): Boolean = false
      }

      overlayPanel.apply {
        layout = OverlayLayout(this)

        add(notificationsPanel)
        add(surface)
      }

      mainPanelSplitter.firstComponent = overlayPanel
      add(mainPanelSplitter, BorderLayout.CENTER)
    }

    val issueErrorSplitter = IssuePanelSplitter(surface, contentPanel)

    init(issueErrorSplitter, surface, listOf(), false)
    showLoading(message("panel.building"))
  }

  private val ticker = ControllableTicker({
                                            if (!RenderService.isBusy()) {
                                              fpsCounter.incrementFrameCounter()
                                              surface.layoutlibSceneManagers.firstOrNull()?.executeCallbacksAndRequestRender(null)
                                            }
                                          }, Duration.ofMillis(5))

  /**
   * Tracks whether the preview has received an [onActivate] call before or not. This is used to decide whether
   * [onInit] must be called.
   */
  private val isFirstActivation = AtomicBoolean(true)

  init {
    Disposer.register(this, ticker)

    // Start handling events for the static preview.
    delegateInteractionHandler.delegate = staticPreviewInteractionHandler
  }

  override val component = workbench

  /**
   * Completes the initialization of the preview. This method is only called once after the first [onActivate]
   * happens.
   */
  private fun onInit() {
    LOG.debug("onInit")
    if (Disposer.isDisposed(this)) {
      LOG.info("Preview was closed before the initialization completed.")
    }
    val psiFile = psiFilePointer.element
    requireNotNull(psiFile) { "PsiFile was disposed before the preview initialization completed." }
    setupBuildListener(project, object : BuildListener {
      override fun buildSucceeded() {
        val file = psiFilePointer.element
        if (file == null) {
          LOG.debug("invalid PsiFile")
          return
        }

        EditorNotifications.getInstance(project).updateNotifications(file.virtualFile!!)
        forceRefresh()
      }

      override fun buildFailed() {
        LOG.debug("buildFailed")
        updateSurfaceVisibilityAndNotifications()
      }

      override fun buildStarted() {
        if (workbench.isMessageVisible) {
          workbench.showLoading(message("panel.building"))
          workbench.hideContent()
        }
        // When building, invalidate the Animation Inspector, since the animations are now obsolete and new ones will be subscribed once
        // build is complete and refresh is triggered.
        ComposePreviewAnimationManager.invalidate()
        EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
      }
    }, this)

    if (COMPOSE_PREVIEW_BUILD_ON_SAVE.get()) {
      setupOnSaveListener(project, psiFile,
                          {
                            if (isBuildOnSaveEnabled) requestBuildForSurface(surface)
                          }, this)
    }

    setupChangeListener(
      project,
      psiFile,
      {
        ApplicationManager.getApplication().invokeLater {
          // When changes are made to the file, the animations become obsolete, so we invalidate the Animation Inspector and only display
          // the new ones after a successful build.
          ComposePreviewAnimationManager.invalidate()
          refresh()
        }
      },
      this)

    // When the preview is opened we must trigger an initial refresh. We wait for the project to be smart and synched to do it.
    project.runWhenSmartAndSyncedOnEdt(this, Consumer {
      refresh()
    })

    DumbService.DUMB_MODE.subscribe(this, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        refresh()
      }
    })
  }

  override fun onActivate() {
    LOG.debug("onActivate")
    if (isFirstActivation.getAndSet(false)) onInit()
  }

  override fun onCaretPositionChanged(event: CaretEvent) {
    if (!StudioFlags.COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE.get()) return
    // If we have not changed line, ignore
    if (event.newPosition.line == event.oldPosition.line) return
    val offset = event.editor.logicalPositionToOffset(event.newPosition)

    launch(uiThread) {
      val filePreviewElements = withContext(workerThread) {
        memoizedElementsProvider.previewElements
      }

      filePreviewElements.find { element ->
        element.previewBodyPsi?.psiRange.containsOffset(offset) || element.previewElementDefinitionPsi?.psiRange.containsOffset(offset)
      }?.let { selectedPreviewElement ->
        surface.models.find { it.dataContext.getData(COMPOSE_PREVIEW_ELEMENT) == selectedPreviewElement }
      }?.let {
        surface.scrollToVisible(it)
      }
    }
  }

  private fun logInteractiveSessionMetrics() {
    val touchEvents = surface.layoutlibSceneManagers.map { it.touchEventsCount }.sum()
    InteractivePreviewUsageTracker.getInstance(surface).logInteractiveSession(fpsCounter.getFps(), fpsCounter.getDurationMs(), touchEvents)
  }

  override fun dispose() {
    if (interactiveMode == ComposePreviewManager.InteractiveMode.READY) {
      logInteractiveSessionMetrics()
    }
    animationInspectionPreviewElementInstance = null
  }

  override var isBuildOnSaveEnabled: Boolean = COMPOSE_PREVIEW_BUILD_ON_SAVE.get()
    get() = COMPOSE_PREVIEW_BUILD_ON_SAVE.get() && field

  private fun hasErrorsAndNeedsBuild(): Boolean = !hasRenderedAtLeastOnce.get() || surface.layoutlibSceneManagers
    .any { it.renderResult.isComposeErrorResult() }

  private fun hasSyntaxErrors(): Boolean = WolfTheProblemSolver.getInstance(project).isProblemFile(psiFilePointer.virtualFile)

  override fun status(): ComposePreviewManager.Status {
    val isRefreshing = (refreshCallsCount.get() > 0 ||
                        DumbService.isDumb(project) ||
                        GradleBuildState.getInstance(project).isBuildInProgress)

    // If we are refreshing, we avoid spending time checking other conditions like errors or if the preview
    // is out of date.
    return ComposePreviewManager.Status(
      !isRefreshing && hasErrorsAndNeedsBuild(),
      !isRefreshing && hasSyntaxErrors(),
      !isRefreshing && projectBuildStatusManager.status == OutOfDate,
      isRefreshing,
      interactiveMode)
  }

  /**
   * Hides the preview content and shows an error message on the surface.
   */
  private fun showModalErrorMessage(message: String) = UIUtil.invokeLaterIfNeeded {
    LOG.debug("showModelErrorMessage: $message")
    workbench.loadingStopped(message)
  }

  /**
   * Method called when the notifications of the [PreviewRepresentation] need to be updated. This is called by the
   * [ComposePreviewNotificationProvider] when the editor needs to refresh the notifications.
   */
  override fun updateNotifications(parentEditor: FileEditor) = UIUtil.invokeLaterIfNeeded {
    if (Disposer.isDisposed(this) || project.isDisposed || !parentEditor.isValid) return@invokeLaterIfNeeded

    notificationsPanel.updateNotifications(psiFilePointer.virtualFile, parentEditor, project)
  }

  private fun updateNotifications() = UIUtil.invokeLaterIfNeeded {
    // Make sure all notifications are cleared-up
    if (!project.isDisposed) {
      EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile)
    }
  }

  /**
   * Updates the surface visibility and displays the content or an error message depending on the build state. This method is called after
   * certain updates like a build or a preview refresh has happened.
   * Calling this method will also update the FileEditor notifications.
   */
  private fun updateSurfaceVisibilityAndNotifications() = UIUtil.invokeLaterIfNeeded {
      if (workbench.isMessageVisible && projectBuildStatusManager.status == NeedsBuild) {
        LOG.debug("Needs successful build")
        showModalErrorMessage(message("panel.needs.build"))
      }
      else {
        LOG.debug("Show content")
        workbench.hideLoading()
        workbench.showContent()
      }

      updateNotifications()
    }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The call will block until all the given [PreviewElement]s have completed rendering. If [quickRefresh]
   * is true the preview surfaces for the same [PreviewElement]s do not get reinflated, this allows to save
   * time for e.g. static to animated preview transition.
   */
  private suspend fun doRefreshSync(filePreviewElements: List<PreviewElement>, quickRefresh: Boolean) {
    if (LOG.isDebugEnabled) LOG.debug("doRefresh of ${filePreviewElements.count()} elements.")
    val stopwatch = if (LOG.isDebugEnabled) StopWatch() else null
    val psiFile = ReadAction.compute<PsiFile?, Throwable> {
      val element = psiFilePointer.element

      return@compute if (element == null || !element.isValid) {
        LOG.warn("doRefresh with invalid PsiFile")
        null
      }
      else {
        element
      }
    } ?: return

    // If Live Literals is enabled, fine the list of classes in the current open file and cached them.
    // We will only instrument these classes. Changes in literals in other classes will not refresh the preview.
    cachedLiveLiteralsClasses = if (isLiveLiteralsEnabled && psiFile is PsiClassOwner) {
      ReadAction.compute<Set<String>, Throwable> {
        (psiFile as? PsiClassOwner)?.classes?.mapNotNull { it.qualifiedName?.replace('.', '/') }?.toSet() ?: emptySet()
      }
    }
    else emptySet()

    // Cache available groups
    availableGroups = previewElementProvider.allAvailableGroups

    // Restore
    onRestoreState?.invoke()
    onRestoreState = null

    val facet = AndroidFacet.getInstance(psiFile)!!
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)

    val isFirstRender = !hasRenderedAtLeastOnce.get()
    // Retrieve the models that were previously displayed so we can reuse them instead of creating new ones.
    val existingModels = surface.models.toMutableList()
    val previewElementsList = previewElementProvider.previewElements.toList()

    val modelIndices = matchElementsToModels(existingModels, previewElementsList)
    // Now we generate all the models (or reuse) for the PreviewElements.
    val models = previewElementsList
      .map {
        val xmlOutput = it.toPreviewXml()
          // Whether to paint the debug boundaries or not
          .toolsAttribute("paintBounds", showDebugBoundaries.toString())
          .apply {
            if (animationInspection.get()) {
              // If the animation inspection is active, start the PreviewAnimationClock with the current epoch time.
              toolsAttribute("animationClockStartTime", System.currentTimeMillis().toString())
            }
          }
          .buildString()

        Pair(it, xmlOutput)
      }
      .mapIndexed { idx, it ->
        val (previewElement, fileContents) = it

        if (LOG.isDebugEnabled) {
          LOG.debug("""Preview found at ${stopwatch?.duration?.toMillis()}ms
              displayName=${previewElement.displaySettings.name}
              methodName=${previewElement.composableMethodFqn}

              ${fileContents}
          """.trimIndent())
        }

        val model = if (modelIndices[idx] >= 0) {
          // If model index for this preview element >= 0 then an existing model that can be reused is found. See matchElementsToModels for
          // more details.
          val reusedModel = existingModels[modelIndices[idx]]
          val affinity = modelAffinity(reusedModel.dataContext, previewElement)
          // If the model is for the same element (affinity=0) and we know that it is not spoiled by previous actions (quickRefresh)
          // we can skip reinflate and therefore refresh much quicker
          val forceReinflate = !(affinity == 0 && quickRefresh)

          LOG.debug("Re-using model ${reusedModel.virtualFile.name}")
          reusedModel.updateFileContentBlocking(fileContents)
          // Reconfigure the model by setting the new display name and applying the configuration values
          reusedModel.modelDisplayName = previewElement.displaySettings.name
          reusedModel.dataContext = ModelDataContext(this, previewElement)
          // We call addModel even though the model might not be new. If we try to add an existing model,
          // this will trigger a new render which is exactly what we want.
          configureLayoutlibSceneManager(surface.addModelWithoutRender(reusedModel) as LayoutlibSceneManager,
                                         showDecorations = previewElement.displaySettings.showDecoration,
                                         isInteractive = interactiveMode.isStartingOrReady(),
                                         requestPrivateClassLoader = usePrivateClassLoader(),
                                         liveLiteralsClasses = cachedLiveLiteralsClasses,
                                         forceReinflate = forceReinflate)
          reusedModel
        }
        else {
          val now = System.currentTimeMillis()
          LOG.debug("No models to reuse were found. New model $now.")
          val file = ComposeAdapterLightVirtualFile("compose-model-$now.xml", fileContents) { psiFilePointer.virtualFile }
          val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
          val newModel = NlModel.builder(facet, file, configuration)
            .withParentDisposable(this@ComposePreviewRepresentation)
            .withModelDisplayName(previewElement.displaySettings.name)
            .withModelUpdater(modelUpdater)
            .withComponentRegistrar(surface.componentRegistrar)
            .withDataContext(ModelDataContext(this, previewElement))
            .withXmlProvider(BiFunction<Project, VirtualFile, XmlFile> { project, virtualFile ->
              NlModelBuilder.getDefaultFile(project, virtualFile).also {
                it.putUserData(ModuleUtilCore.KEY_MODULE, facet.module)
              }
            })
            .build()
          configureLayoutlibSceneManager(surface.addModelWithoutRender(newModel) as LayoutlibSceneManager,
                                         showDecorations = previewElement.displaySettings.showDecoration,
                                         isInteractive = interactiveMode.isStartingOrReady(),
                                         liveLiteralsClasses = cachedLiveLiteralsClasses,
                                         requestPrivateClassLoader = usePrivateClassLoader())
          newModel
        }

        val offset = ReadAction.compute<Int, Throwable> {
          previewElement.previewElementDefinitionPsi?.element?.textOffset ?: 0
        }

        navigationHandler.setDefaultLocation(model, psiFile, offset)

        previewElement.configuration.applyTo(model.configuration)

        model to previewElement
      }
      .toList()

    existingModels.removeAll(models.map { it.first })

    // Remove and dispose pre-existing models that were not used.
    // This will happen if the user removes one or more previews.
    if (LOG.isDebugEnabled) LOG.debug("Removing ${existingModels.size} model(s)")
    existingModels.forEach {
      surface.removeModel(it)
      Disposer.dispose(it)
    }
    val newSceneManagers = models
      .map {
        val (model, previewElement) = it
        surface.getSceneManager(model) as LayoutlibSceneManager
      }

    surface.repaint()
    if (newSceneManagers.isNotEmpty()) {
      CompletableFuture.allOf(*newSceneManagers
        .map { it.requestComposeRender() }
        .toTypedArray())
        .await()
      hasRenderedAtLeastOnce.set(true)
    }
    else {
      showModalErrorMessage(message("panel.no.previews.defined"))
    }

    if (LOG.isDebugEnabled) {
      LOG.debug("Render completed in ${stopwatch?.duration?.toMillis()}ms")

      // Log any rendering errors
      surface.layoutlibSceneManagers
        .forEach {
          val modelName = it.model.modelDisplayName
          it.renderResult?.let { result ->
            val logger = result.logger
            LOG.debug("""modelName="$modelName" result
                  | ${result}
                  | hasErrors=${logger.hasErrors()}
                  | missingClasses=${logger.missingClasses}
                  | messages=${logger.messages.asLogString()}
                  | exceptions=${logger.brokenClasses.values + logger.classesWithIncorrectFormat.values}
                """.trimMargin())
          }
        }
    }

    if (models.size >= filePreviewElements.size) {
      previewElements = filePreviewElements
    }
    else {
      // Some preview elements did not result in model creations. This could be because of failed PreviewElements instantiation.
      // TODO(b/160300892): Add better error handling for failed instantiations.
      LOG.warn("Some preview elements have failed")
    }

    // We zoom to fit to have better initial zoom level when first build is completed
    if (isFirstRender) {
      withContext(uiThread) {
        surface.zoomToFit()
      }
    }
  }

  /**
   * Requests a refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The refresh will only happen if the Preview elements have changed from the last render.
   */
  private fun refresh(quickRefresh: Boolean = false): Job {
    var refreshTrigger: Throwable? = if (LOG.isDebugEnabled) Throwable() else null
    return launch(uiThread) {
      LOG.debug("Refresh triggered", refreshTrigger)
      if (DumbService.isDumb(project)) {
        LOG.debug("Project is in dumb mode, not able to refresh")
        return@launch
      }

      refreshCallsCount.incrementAndGet()
      try {
        val filePreviewElements = withContext(workerThread) {
          memoizedElementsProvider.previewElements
            .toList()
            .sortBySourcePosition()
        }

        if (filePreviewElements == previewElements) {
          LOG.debug("No updates on the PreviewElements, just refreshing the existing ones")
          // In this case, there are no new previews. We need to make sure that the surface is still correctly
          // configured and that we are showing the right size for components. For example, if the user switches on/off
          // decorations, that will not generate/remove new PreviewElements but will change the surface settings.
          uniqueRefreshLauncher.launch {
            surface.models
              .mapNotNull {
                val sceneManager = surface.getSceneManager(it) as? LayoutlibSceneManager ?: return@mapNotNull null
                val previewElement = it.dataContext.getData(COMPOSE_PREVIEW_ELEMENT) ?: return@mapNotNull null
                previewElement to sceneManager
              }
              .forEach {
                val (previewElement, sceneManager) = it
                // When showing decorations, show the full device size
                configureLayoutlibSceneManager(sceneManager,
                                               showDecorations = previewElement.displaySettings.showDecoration,
                                               isInteractive = interactiveMode.isStartingOrReady(),
                                               requestPrivateClassLoader = usePrivateClassLoader(),
                                               liveLiteralsClasses = cachedLiveLiteralsClasses,
                                               forceReinflate = false)
                  .requestComposeRender()
                  .await()
              }
          }?.join()
        }
        else {
          uniqueRefreshLauncher.launch {
            doRefreshSync(filePreviewElements, quickRefresh)
          }?.join()
        }
      }
      catch (t: Throwable) {
        LOG.warn("Refresh request failed", t)
      }
      finally {
        refreshCallsCount.decrementAndGet()
        updateSurfaceVisibilityAndNotifications()
      }
    }
  }

  override fun getState(): PreviewRepresentationState? {
    val selectedGroupName = previewElementProvider.groupNameFilter.name ?: ""
    return mapOf(
      SELECTED_GROUP_KEY to selectedGroupName,
      BUILD_ON_SAVE_KEY to isBuildOnSaveEnabled.toString())
  }

  override fun setState(state: PreviewRepresentationState) {
    val selectedGroupName = state[SELECTED_GROUP_KEY]
    val buildOnSave = state[BUILD_ON_SAVE_KEY]?.toBoolean()
    onRestoreState = {
      if (!selectedGroupName.isNullOrEmpty()) {
        availableGroups.find { it.name == selectedGroupName }?.let {
          groupFilter = it
        }
      }

      buildOnSave?.let { isBuildOnSaveEnabled = it }
    }
  }

  /**
   * Whether the scene manager should use a private ClassLoader. Currently, that's done for interactive preview and animation inspector,
   * where it's crucial not to share the state (which includes the compose framework).
   */
  private fun usePrivateClassLoader() = interactiveMode.isStartingOrReady() || animationInspection.get() || shouldQuickRefresh()

  private fun forceRefresh(quickRefresh: Boolean = false): Job {
    previewElements = emptyList() // This will just force a refresh
    return refresh(quickRefresh)
  }

  override fun registerShortcuts(applicableTo: JComponent) {
    ForceCompileAndRefreshAction(surface).registerCustomShortcutSet(getBuildAndRefreshShortcut(), applicableTo, this)
  }

  /**
   * We will only do quick refresh if there is a single preview.
   * When live literals is enabled, we want to try to preserve the same class loader as much as possible.
   */
  private fun shouldQuickRefresh() =
    !isLiveLiteralsEnabled && StudioFlags.COMPOSE_QUICK_ANIMATED_PREVIEW.get() && previewElementProvider.previewElements.count() == 1
}
