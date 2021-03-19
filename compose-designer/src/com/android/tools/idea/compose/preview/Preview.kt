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

import com.android.annotations.concurrency.GuardedBy
import com.android.ide.common.rendering.api.Bridge
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.handleLayoutlibNativeCrash
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.compose.preview.PreviewGroup.Companion.ALL_PREVIEW_GROUP
import com.android.tools.idea.compose.preview.actions.ForceCompileAndRefreshAction
import com.android.tools.idea.compose.preview.actions.PinAllPreviewElementsAction
import com.android.tools.idea.compose.preview.actions.UnpinAllPreviewElementsAction
import com.android.tools.idea.compose.preview.actions.requestBuildForSurface
import com.android.tools.idea.compose.preview.analytics.InteractivePreviewUsageTracker
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager
import com.android.tools.idea.compose.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.compose.preview.util.FpsCalculator
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.android.tools.idea.compose.preview.util.containsOffset
import com.android.tools.idea.compose.preview.util.isComposeErrorResult
import com.android.tools.idea.compose.preview.util.layoutlibSceneManagers
import com.android.tools.idea.compose.preview.util.sortByDisplayAndSourcePosition
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.android.tools.idea.editors.setupChangeListener
import com.android.tools.idea.editors.setupOnSaveListener
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.COMPOSE_PREVIEW_BUILD_ON_SAVE
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.classloading.CooperativeInterruptTransform
import com.android.tools.idea.rendering.classloading.HasLiveLiteralsTransform
import com.android.tools.idea.rendering.classloading.LiveLiteralsTransform
import com.android.tools.idea.rendering.classloading.toClassTransform
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.application.subscribe
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.swing.JComponent
import kotlin.concurrent.withLock
import kotlin.properties.Delegates

/**
 * Background color for the surface while "Interactive" is enabled.
 */
private val INTERACTIVE_BACKGROUND_COLOR = JBColor(Color(203, 210, 217), MEUI.ourInteractiveBackgroundColor)

/**
 * [NlModel] associated preview data
 * @param project the [Project] used by the current view.
 * @param composePreviewManager [ComposePreviewManager] of the Preview.
 * @param previewElement the [PreviewElement] associated to this model
 */
private class PreviewElementDataContext(private val project: Project,
                                        private val composePreviewManager: ComposePreviewManager,
                                        private val previewElement: PreviewElement) : DataContext {
  override fun getData(dataId: String): Any? = when (dataId) {
    COMPOSE_PREVIEW_MANAGER.name -> composePreviewManager
    COMPOSE_PREVIEW_ELEMENT.name -> previewElement
    CommonDataKeys.PROJECT.name -> project
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
 * @param isLiveLiteralsEnabled if true, the classes will be instrumented with live literals support.
 * @param onLiveLiteralsFound callback called when the classes have compiler live literals support. This callback will only be called if
 *  [isLiveLiteralsEnabled] is false. If true, the classes are assumed to have this support.
 * @param resetLiveLiteralsFound callback called when the classes are about to be reloaded so the live literals state can be discarded.
 */
private fun configureLayoutlibSceneManager(sceneManager: LayoutlibSceneManager,
                                           showDecorations: Boolean,
                                           isInteractive: Boolean,
                                           requestPrivateClassLoader: Boolean,
                                           isLiveLiteralsEnabled: Boolean,
                                           onLiveLiteralsFound: () -> Unit,
                                           resetLiveLiteralsFound: () -> Unit): LayoutlibSceneManager =
  sceneManager.apply {
    val reinflate = changeRequiresReinflate(showDecorations, isInteractive, requestPrivateClassLoader)
    setTransparentRendering(!showDecorations)
    setShrinkRendering(!showDecorations)
    setUseImagePool(false)
    interactive = isInteractive
    isUsePrivateClassLoader = requestPrivateClassLoader
    setOnNewClassLoader(resetLiveLiteralsFound)
    if (isLiveLiteralsEnabled) {
      setProjectClassesTransform(
        toClassTransform(
          { if (StudioFlags.COMPOSE_PREVIEW_INTERRUPTIBLE.get()) CooperativeInterruptTransform(it) else it },
          { LiveLiteralsTransform(it) }
        ))
    }
    else {
      setProjectClassesTransform(
        toClassTransform(
          { if (StudioFlags.COMPOSE_PREVIEW_INTERRUPTIBLE.get()) CooperativeInterruptTransform(it) else it },
          // Live literals is not enabled but we pass the [HasLiveLiteralsTransform] to identify if the current project
          // has live literals enabled.
          { HasLiveLiteralsTransform(it, onLiveLiteralsFound = onLiveLiteralsFound) }
        )
      )
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
 * Frames per second limit for interactive preview
 */
private const val FPS_LIMIT = 60

/**
 * A [PreviewRepresentation] that provides a compose elements preview representation of the given `psiFile`.
 *
 * A [component] is implied to display previews for all declared `@Composable` functions that also use the `@Preview` (see
 * [com.android.tools.idea.compose.preview.util.PREVIEW_ANNOTATION_FQN]) annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a `@Composable` functions.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param previewProvider [PreviewElementProvider] to obtain the [PreviewElement]s.
 * @param preferredInitialVisibility preferred [PreferredVisibility] for this representation.
 */
class ComposePreviewRepresentation(psiFile: PsiFile,
                                   previewProvider: PreviewElementProvider<PreviewElement>,
                                   override val preferredInitialVisibility: PreferredVisibility,
                                   composePreviewViewProvider: ComposePreviewViewProvider) :
  PreviewRepresentation, ComposePreviewManagerEx, UserDataHolderEx by UserDataHolderBase(), AndroidCoroutinesAware {
  /**
   * Fake device id to identify this preview with the live literals service. This allows live literals to track how
   * many "users" it has.
   */
  private val previewDeviceId = "Preview#${UUID.randomUUID()}"
  private val LOG = Logger.getInstance(ComposePreviewRepresentation::class.java)
  private val project = psiFile.project
  private val psiFilePointer = SmartPointerManager.createPointer(psiFile)

  private val projectBuildStatusManager = ProjectBuildStatusManager(this, psiFile)

  /**
   * [PreviewElementProvider] containing the pinned previews.
   */
  private val memoizedPinnedPreviewProvider = FilteredPreviewElementProvider<PreviewElementInstance>(PinnedPreviewElementManager.getPreviewElementProvider(project)) {
    !(it.previewBodyPsi?.containingFile?.isEquivalentTo(psiFilePointer.containingFile) ?: false)
  }

  /**
   * [PreviewElementProvider] used to save the result of a call to `previewProvider`. Calls to `previewProvider` can potentially
   * be slow. This saves the last result and it is refreshed on demand when we know is not running on the UI thread.
   */
  private val memoizedElementsProvider = MemoizedPreviewElementProvider<PreviewElement>(previewProvider) {
    ReadAction.compute<Long, Throwable> {
      psiFilePointer.element?.modificationStamp ?: -1
    }
  }
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

  override var interactivePreviewElementInstance: PreviewElementInstance?
    set(value) {
      if ((interactiveMode == ComposePreviewManager.InteractiveMode.DISABLED && value != null) ||
          (interactiveMode == ComposePreviewManager.InteractiveMode.READY && value == null)) {
        LOG.debug("New single preview element focus: $value")
        val isInteractive = value != null
        val isFromAnimationInspection = animationInspection.get()
        // The order matters because we first want to change the composable being previewed and then start interactive loop when enabled
        // but we want to stop the loop first and then change the composable when disabled
        if (isInteractive) { // Enable interactive
          if (isFromAnimationInspection) {
            onAnimationInspectionStop()
          } else {
            EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
          }
          interactiveMode = ComposePreviewManager.InteractiveMode.STARTING
          val quickRefresh = shouldQuickRefresh() && !isFromAnimationInspection// We should call this before assigning newValue to instanceIdFilter
          val peerPreviews = previewElementProvider.previewElements.count()
          previewElementProvider.instanceFilter = value
          composeWorkBench.hasComponentsOverlay = false
          val startUpStart = System.currentTimeMillis()
          forceRefresh(quickRefresh).invokeOnCompletion {
            surface.layoutlibSceneManagers.forEach { it.resetTouchEventsCounter() }
            if (!isFromAnimationInspection) { // Currently it will re-create classloader and will be slower that switch from static
              InteractivePreviewUsageTracker.getInstance(surface).logStartupTime(
                (System.currentTimeMillis() - startUpStart).toInt(), peerPreviews)
            }
            fpsCounter.resetAndStart()
            ticker.start()
            composeWorkBench.isInteractive = true

            if (StudioFlags.COMPOSE_ANIMATED_PREVIEW_SHOW_CLICK.get()) {
              // While in interactive mode, display a small ripple when clicking
              surface.enableMouseClickDisplay()
            }
            surface.background = INTERACTIVE_BACKGROUND_COLOR
            interactiveMode = ComposePreviewManager.InteractiveMode.READY
          }
        }
        else { // Disable interactive
          onInteractivePreviewStop()
          EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
          onStaticPreviewStart()
          forceRefresh().invokeOnCompletion {
            interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
          }
        }
      }
    }
    get() = if (interactiveMode == ComposePreviewManager.InteractiveMode.READY) previewElementProvider.instanceFilter else null

  private fun onStaticPreviewStart() {
    composeWorkBench.hasComponentsOverlay = true
    surface.background = defaultSurfaceBackground
  }

  private fun onInteractivePreviewStop() {
    interactiveMode = ComposePreviewManager.InteractiveMode.STOPPING
    surface.disableMouseClickDisplay()
    composeWorkBench.isInteractive = false
    ticker.stop()
    previewElementProvider.clearInstanceIdFilter()
    logInteractiveSessionMetrics()
  }

  private fun pauseInteractivePreview() {
    ticker.stop()
    surface.layoutlibSceneManagers.forEach { it.pauseSessionClock() }
  }

  private fun resumeInteractivePreview() {
    fpsCounter.resetAndStart()
    surface.layoutlibSceneManagers.forEach { it.resumeSessionClock() }
    ticker.start()
  }

  private val animationInspection = AtomicBoolean(false)

  override var animationInspectionPreviewElementInstance: PreviewElementInstance?
    set(value) {
      if ((!animationInspection.get() && value != null) || (animationInspection.get() && value == null)) {
        if (value != null) {
          if (interactiveMode != ComposePreviewManager.InteractiveMode.DISABLED) {
            onInteractivePreviewStop()
          }
          LOG.debug("Animation Preview open for preview: $value")
          ComposePreviewAnimationManager.onAnimationInspectorOpened()
          previewElementProvider.instanceFilter = value
          animationInspection.set(true)
          composeWorkBench.hasComponentsOverlay = false
          composeWorkBench.isAnimationPreview = true

          // Open the animation inspection panel
          composeWorkBench.bottomPanel = ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, this) {
            // Close this inspection panel, making all the necessary UI changes (e.g. changing background and refreshing the preview) before
            // opening a new one.
            animationInspectionPreviewElementInstance = null
          }
          surface.background = INTERACTIVE_BACKGROUND_COLOR
        }
        else {
          onAnimationInspectionStop()
          onStaticPreviewStart()
        }
        forceRefresh().invokeOnCompletion {
          interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
        }
      }
    }
    get() = if (animationInspection.get()) previewElementProvider.instanceFilter else null

  private fun onAnimationInspectionStop() {
    animationInspection.set(false)
    // Close the animation inspection panel
    ComposePreviewAnimationManager.closeCurrentInspector()
    // Swap the components back
    composeWorkBench.bottomPanel = null
    composeWorkBench.isAnimationPreview = false
    previewElementProvider.instanceFilter = null
  }

  /**
   * Counter used to generate unique push ids.
   */
  private val pushIdCounter = AtomicLong()
  private val liveLiteralsManager = LiveLiteralsService.getInstance(project).apply {
    addOnLiteralsChangedListener(this@ComposePreviewRepresentation) {
      // We generate an id for the push of the new literals so it can be tracked by the metrics stats.
      val pushId = pushIdCounter.getAndIncrement().toString(16)
      LiveLiteralsService.getInstance(project).liveLiteralPushStarted(previewDeviceId, pushId)
      surface.layoutlibSceneManagers.forEach {
        it.forceReinflate()
        it.requestRender().whenComplete { _, _ ->
          LiveLiteralsService.getInstance(project).liveLiteralPushed(previewDeviceId, pushId, listOf())
        }
      }
    }
  }

  override var hasLiveLiterals: Boolean = false
    private set(value) {
      field = value
      LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(previewDeviceId, LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }

  override val isLiveLiteralsEnabled: Boolean
    get() = liveLiteralsManager.isAvailable

  override var showDebugBoundaries: Boolean = false
    set(value) {
      field = value
      forceRefresh()
    }

  private val composeWorkBench: ComposePreviewView = composePreviewViewProvider.invoke(
    project,
    psiFilePointer,
    projectBuildStatusManager,
    navigationHandler, {
      return@invoke when (it) {
        COMPOSE_PREVIEW_MANAGER.name -> this@ComposePreviewRepresentation
        // The Compose preview NlModels do not point to the actual file but to a synthetic file
        // generated for Layoutlib. This ensures we return the right file.
        CommonDataKeys.VIRTUAL_FILE.name -> psiFilePointer.virtualFile
        CommonDataKeys.PROJECT.name -> project
        else -> null
      }
    }, this,
    PinAllPreviewElementsAction(
      {
        PinnedPreviewElementManager.getInstance(project).isPinned(psiFile)
      }, previewElementProvider),
    UnpinAllPreviewElementsAction)

  private val pinnedSurface: NlDesignSurface
    get() = composeWorkBench.pinnedSurface
  private val surface: NlDesignSurface
    get() = composeWorkBench.mainSurface

  /**
   * Default background used by the surface. This is used to restore the state after disabling the interactive preview.
   */
  private val defaultSurfaceBackground: Color = surface.background

  /**
   * List of [PreviewElement] being rendered by this editor
   */
  private var previewElements: List<PreviewElement> = emptyList()

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

  private val ticker = ControllableTicker({
                                            if (!RenderService.isBusy() && fpsCounter.getFps() <= FPS_LIMIT) {
                                              fpsCounter.incrementFrameCounter()
                                              surface.layoutlibSceneManagers.firstOrNull()?.executeCallbacksAndRequestRender(null)
                                            }
                                          }, Duration.ofMillis(5))

  // region Lifecycle handling
  /**
   * True if the preview has received a [refresh] call while it was deactivated from, for example, a build listener.
   * When the preview becomes active again, a refresh will be issued.
   */
  private val refreshedWhileDeactivated = AtomicBoolean(false)

  /**
   * Lock used during the [onActivate]/[onDeactivate]/[onDeactivationTimeout] to avoid activations happening in the middle.
   */
  private val activationLock = ReentrantLock()

  /**
   * Tracks whether this preview is active or not. The value tracks the [onActivate] and [onDeactivate] calls.
   */
  private val isActive = AtomicBoolean(false)

  /**
   * Tracks whether the preview has received an [onActivate] call before or not. This is used to decide whether
   * [onInit] must be called.
   */
  @GuardedBy("activationLock")
  private var isFirstActivation = true
  // endregion

  init {
    Disposer.register(this, ticker)
  }

  override val component: JComponent = composeWorkBench.component

  // region Lifecycle handling
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

        if (hasLiveLiterals) {
          LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(previewDeviceId, LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
        }

        EditorNotifications.getInstance(project).updateNotifications(file.virtualFile!!)
        forceRefresh()
      }

      override fun buildFailed() {
        LOG.debug("buildFailed")
        composeWorkBench.updateVisibilityAndNotifications()
      }

      override fun buildStarted() {
        // Stop live literals monitoring for this preview. If the new build has live literals, they will
        // be re-enabled later automatically via the HasLiveLiterals check.
        LiveLiteralsService.getInstance(project).liveLiteralsMonitorStopped(previewDeviceId)
        composeWorkBench.updateProgress(message("panel.building"))
        // When building, invalidate the Animation Inspector, since the animations are now obsolete and new ones will be subscribed once
        // build is complete and refresh is triggered.
        ComposePreviewAnimationManager.invalidate()
        EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
      }
    }, this)

    if (COMPOSE_PREVIEW_BUILD_ON_SAVE.get()) {
      setupOnSaveListener(project, psiFile,
                          {
                            if (isBuildOnSaveEnabled
                                && isActive.get()
                                && !hasSyntaxErrors()) requestBuildForSurface(surface, false)
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
    project.runWhenSmartAndSyncedOnEdt(this, {
      refresh()
    })

    DumbService.DUMB_MODE.subscribe(this, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        refresh()
      }
    })

    if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
      val listener = PinnedPreviewElementManager.Listener {
        refresh()
      }
      PinnedPreviewElementManager.getInstance(project).addListener(listener)
      Disposer.register(this) {
        PinnedPreviewElementManager.getInstance(project).removeListener(listener)
      }
    }
  }

  override fun onActivate() {
    activationLock.withLock {
      LOG.debug("onActivate")

      if (hasLiveLiterals) {
        LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(previewDeviceId, LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
      }

      isActive.set(true)
      if (isFirstActivation) {
        isFirstActivation = false
        onInit()
      }
      else surface.activate()

      if (interactiveMode.isStartingOrReady()) {
        resumeInteractivePreview()
      }

      if (refreshedWhileDeactivated.getAndSet(false)) {
        // Refresh has been called while we were deactivated, issue a refresh on activation.
        LOG.debug("Pending refresh")
        refresh()
      }
    }
  }

  /**
   * This method will be called by [onDeactivate] after the deactivation timeout expires or the LRU queue is full.
   */
  private fun onDeactivationTimeout() {
    activationLock.withLock {
      // If the preview is still not active, deactivate the surface.
      if (!isActive.get()) {
        interactivePreviewElementInstance = null
        LOG.debug("Delayed surface deactivation")
        surface.deactivate()
      }
    }
  }

  override fun onDeactivate() {
    activationLock.withLock {
      LOG.debug("onDeactivate")
      if (interactiveMode.isStartingOrReady()) {
        pauseInteractivePreview()
      }
      LiveLiteralsService.getInstance(project).liveLiteralsMonitorStopped(previewDeviceId)
      isActive.set(false)

      project.getService(PreviewProjectService::class.java).deactivationQueue.addDelayedAction(this, this::onDeactivationTimeout)
    }
  }
  // endregion

  override fun onCaretPositionChanged(event: CaretEvent) {
    if (!StudioFlags.COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE.get()) return
    if (!isActive.get()) return
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
        surface.scrollToVisible(it, true)
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

  override var isBuildOnSaveEnabled: Boolean = false
    get() = COMPOSE_PREVIEW_BUILD_ON_SAVE.get() && field

  private var lastPinsModificationCount = -1L

  private fun hasErrorsAndNeedsBuild(): Boolean =
    previewElements.isNotEmpty() &&
    (!hasRenderedAtLeastOnce.get() || surface.layoutlibSceneManagers.any { it.renderResult.isComposeErrorResult() })

  private fun hasSyntaxErrors(): Boolean = WolfTheProblemSolver.getInstance(project).isProblemFile(psiFilePointer.virtualFile)

  override fun status(): ComposePreviewManager.Status {
    val isRefreshing = (refreshCallsCount.get() > 0 ||
                        DumbService.isDumb(project) ||
                        projectBuildStatusManager.isBuilding)

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
   * Method called when the notifications of the [PreviewRepresentation] need to be updated. This is called by the
   * [ComposePreviewNotificationProvider] when the editor needs to refresh the notifications.
   */
  override fun updateNotifications(parentEditor: FileEditor) = composeWorkBench.updateNotifications(parentEditor)

  private fun toPreviewXmlString(instance: PreviewElementInstance): String =
    instance.toPreviewXml()
      // Whether to paint the debug boundaries or not
      .toolsAttribute("paintBounds", showDebugBoundaries.toString())
      .toolsAttribute("forceCompositionInvalidation", isLiveLiteralsEnabled.toString())
      .toolsAttribute("findDesignInfoProviders", StudioFlags.COMPOSE_CONSTRAINT_VISUALIZATION.get().toString())
      .apply {
        if (animationInspection.get()) {
          // If the animation inspection is active, start the PreviewAnimationClock with the current epoch time.
          toolsAttribute("animationClockStartTime", System.currentTimeMillis().toString())
        }
      }
      .buildString()

  private fun getPreviewDataContextForPreviewElement(previewElement: PreviewElement) =
    PreviewElementDataContext(project, this@ComposePreviewRepresentation, previewElement)

  private fun configureLayoutlibSceneManagerForPreviewElement(previewElement: PreviewElement,
                                                              layoutlibSceneManager: LayoutlibSceneManager) =
    configureLayoutlibSceneManager(layoutlibSceneManager,
                                   showDecorations = previewElement.displaySettings.showDecoration,
                                   isInteractive = interactiveMode.isStartingOrReady(),
                                   requestPrivateClassLoader = usePrivateClassLoader(),
                                   isLiveLiteralsEnabled = isLiveLiteralsEnabled,
                                   onLiveLiteralsFound = { hasLiveLiterals = true },
                                   resetLiveLiteralsFound = { hasLiveLiterals = isLiveLiteralsEnabled })

  private fun onAfterRender() {
    composeWorkBench.hasRendered = true
    if (!hasRenderedAtLeastOnce.get()) {
      // We zoom to fit to have better initial zoom level when first build is completed
      UIUtil.invokeLaterIfNeeded {
        surface.zoomToFit()
        hasRenderedAtLeastOnce.set(true)
      }
    }
  }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The call will block until all the given [PreviewElement]s have completed rendering. If [quickRefresh]
   * is true the preview surfaces for the same [PreviewElement]s do not get reinflated, this allows to save
   * time for e.g. static to animated preview transition.
   */
  private suspend fun doRefreshSync(filePreviewElements: List<PreviewElement>, quickRefresh: Boolean) {
    if (LOG.isDebugEnabled) LOG.debug("doRefresh of ${filePreviewElements.count()} elements.")
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

    // The surface might have been deactivated while we waited for the read lock, check again.
    if (!isActive.get()) {
      refreshedWhileDeactivated.set(true)
      return
    }

    // Cache available groups
    availableGroups = previewElementProvider.allAvailableGroups

    // Restore
    onRestoreState?.invoke()
    onRestoreState = null

    val arePinsEnabled = StudioFlags.COMPOSE_PIN_PREVIEW.get() && !interactiveMode.isStartingOrReady()
    val hasPinnedElements = if (arePinsEnabled) {
      memoizedPinnedPreviewProvider.previewElements.any()
    } else false

    composeWorkBench.setPinnedSurfaceVisibility(hasPinnedElements)
    val pinnedManager = PinnedPreviewElementManager.getInstance(project)
    if (hasPinnedElements) {
      pinnedSurface.updatePreviewsAndRefresh(
        false,
        memoizedPinnedPreviewProvider,
        LOG,
        psiFile,
        this,
        this::onAfterRender,
        this::toPreviewXmlString,
        this::getPreviewDataContextForPreviewElement,
        this::configureLayoutlibSceneManagerForPreviewElement
      ).isNotEmpty()
    }
    lastPinsModificationCount = pinnedManager.modificationCount

    val showingPreviewElements = surface.updatePreviewsAndRefresh(
      quickRefresh,
      previewElementProvider,
      LOG,
      psiFile,
      this,
      this::onAfterRender,
      this::toPreviewXmlString,
      this::getPreviewDataContextForPreviewElement,
      this::configureLayoutlibSceneManagerForPreviewElement
    )

    if (showingPreviewElements.size >= filePreviewElements.size) {
      previewElements = filePreviewElements
    }
    else {
      // Some preview elements did not result in model creations. This could be because of failed PreviewElements instantiation.
      // TODO(b/160300892): Add better error handling for failed instantiations.
      LOG.warn("Some preview elements have failed")
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

      if (!isActive.get()) {
        LOG.debug("Refresh, the preview is not active, scheduling for later.")
        refreshedWhileDeactivated.set(true)
        return@launch
      }

      if (DumbService.isDumb(project)) {
        LOG.debug("Project is in dumb mode, not able to refresh")
        return@launch
      }

      if (Bridge.hasNativeCrash() && composeWorkBench is ComposePreviewViewImpl) {
        composeWorkBench.handleLayoutlibNativeCrash { refresh() }
        return@launch
      }

      refreshCallsCount.incrementAndGet()
      try {
        val filePreviewElements = withContext(workerThread) {
          memoizedElementsProvider.previewElements
            .toList()
            .sortByDisplayAndSourcePosition()
        }

        val pinnedPreviewElements = if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
          withContext(workerThread) {
            memoizedPinnedPreviewProvider.previewElements
              .toList()
              .sortByDisplayAndSourcePosition()
          }
        } else emptyList()

        val needsFullRefresh = filePreviewElements != previewElements ||
                               PinnedPreviewElementManager.getInstance(project).modificationCount != lastPinsModificationCount

        composeWorkBench.hasContent = filePreviewElements.isNotEmpty() || pinnedPreviewElements.isNotEmpty()
        if (!needsFullRefresh) {
          LOG.debug("No updates on the PreviewElements, just refreshing the existing ones")
          // In this case, there are no new previews. We need to make sure that the surface is still correctly
          // configured and that we are showing the right size for components. For example, if the user switches on/off
          // decorations, that will not generate/remove new PreviewElements but will change the surface settings.
          uniqueRefreshLauncher.launch {
            surface.refreshExistingPreviewElements { previewElement, sceneManager ->
              // When showing decorations, show the full device size
              configureLayoutlibSceneManager(sceneManager,
                                             showDecorations = previewElement.displaySettings.showDecoration,
                                             isInteractive = interactiveMode.isStartingOrReady(),
                                             requestPrivateClassLoader = usePrivateClassLoader(),
                                             isLiveLiteralsEnabled = isLiveLiteralsEnabled,
                                             onLiveLiteralsFound = { hasLiveLiterals = true },
                                             resetLiveLiteralsFound = { hasLiveLiterals = isLiveLiteralsEnabled })
            }
          }?.join()
        }
        else {
          uniqueRefreshLauncher.launch {
            composeWorkBench.updateProgress(message("panel.initializing"))
            doRefreshSync(filePreviewElements, quickRefresh)
          }?.join()
        }
      }
      catch (t: Throwable) {
        LOG.warn("Refresh request failed", t)
      }
      finally {
        refreshCallsCount.decrementAndGet()
        composeWorkBench.updateVisibilityAndNotifications()
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
