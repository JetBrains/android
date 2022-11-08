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

import com.android.ide.common.rendering.api.Bridge
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutlibInteractionHandler
import com.android.tools.idea.common.surface.handleLayoutlibNativeCrash
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.compose.preview.PreviewGroup.Companion.ALL_PREVIEW_GROUP
import com.android.tools.idea.compose.preview.actions.PinAllPreviewElementsAction
import com.android.tools.idea.compose.preview.actions.UnpinAllPreviewElementsAction
import com.android.tools.idea.compose.preview.analytics.InteractivePreviewUsageTracker
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager
import com.android.tools.idea.compose.preview.designinfo.hasDesignInfoProviders
import com.android.tools.idea.compose.preview.fast.FastPreviewSurface
import com.android.tools.idea.compose.preview.navigation.ComposePreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.util.ComposePreviewElement
import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.util.FpsCalculator
import com.android.tools.idea.compose.preview.util.containsOffset
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.concurrency.disposableCallbackFlow
import com.android.tools.idea.concurrency.launchWithProgress
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.documentChangeFlow
import com.android.tools.idea.editors.fast.CompilationResult
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.FastPreviewTrackerManager
import com.android.tools.idea.editors.fast.fastCompile
import com.android.tools.idea.editors.powersave.PreviewPowerSaveManager
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LoggerWithFixedInfo
import com.android.tools.idea.preview.FilteredPreviewElementProvider
import com.android.tools.idea.preview.MemoizedPreviewElementProvider
import com.android.tools.idea.preview.NavigatingInteractionHandler
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.actions.BuildAndRefresh
import com.android.tools.idea.preview.lifecycle.PreviewLifecycleManager
import com.android.tools.idea.preview.refreshExistingPreviewElements
import com.android.tools.idea.preview.sortByDisplayAndSourcePosition
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.CodeOutOfDateTracker
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.uibuilder.actions.LayoutManagerSwitcher
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.util.toDisplayString
import com.intellij.ide.ActivityTracker
import com.intellij.ide.PowerSaveMode
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import java.awt.Color
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import kotlin.properties.Delegates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.util.module

/** Background color for the surface while "Interactive" is enabled. */
private val INTERACTIVE_BACKGROUND_COLOR = MEUI.ourInteractiveBackgroundColor

/** [Notification] group ID. Must match the `groupNotification` entry of `compose-designer.xml`. */
val PREVIEW_NOTIFICATION_GROUP_ID = "Compose Preview Notification"

/**
 * [NlModel] associated preview data
 * @param project the [Project] used by the current view.
 * @param composePreviewManager [ComposePreviewManager] of the Preview.
 * @param previewElement the [ComposePreviewElement] associated to this model
 */
private class PreviewElementDataContext(
  private val project: Project,
  private val composePreviewManager: ComposePreviewManager,
  private val previewElement: ComposePreviewElementInstance
) : DataContext {
  override fun getData(dataId: String): Any? =
    when (dataId) {
      COMPOSE_PREVIEW_MANAGER.name -> composePreviewManager
      COMPOSE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
      CommonDataKeys.PROJECT.name -> project
      else -> null
    }
}

/**
 * Returns true if change of values of any [LayoutlibSceneManager] properties would require
 * necessary re-inflation. Namely, if we change [LayoutlibSceneManager.isShowingDecorations],
 * [LayoutlibSceneManager.isUsePrivateClassLoader] or if we transition from interactive to static
 * preview mode (not the other way around though) we need to re-inflate in order to update the
 * preview layout.
 */
fun LayoutlibSceneManager.changeRequiresReinflate(
  showDecorations: Boolean,
  isInteractive: Boolean,
  usePrivateClassLoader: Boolean
) =
  (showDecorations != isShowingDecorations) ||
    (interactive && !isInteractive) || // transition from interactive to static
    (usePrivateClassLoader != isUsePrivateClassLoader)

/**
 * Sets up the given [sceneManager] with the right values to work on the Compose Preview. Currently,
 * this will configure if the preview elements will be displayed with "full device size" or simply
 * containing the previewed components (shrink mode).
 * @param showDecorations when true, the rendered content will be shown with the full device size
 * specified in the device configuration and with the frame decorations.
 * @param isInteractive whether the scene displays an interactive preview.
 */
@VisibleForTesting
fun configureLayoutlibSceneManager(
  sceneManager: LayoutlibSceneManager,
  showDecorations: Boolean,
  isInteractive: Boolean,
  requestPrivateClassLoader: Boolean
): LayoutlibSceneManager =
  sceneManager.apply {
    val reinflate =
      changeRequiresReinflate(showDecorations, isInteractive, requestPrivateClassLoader)
    setTransparentRendering(!showDecorations)
    setShrinkRendering(!showDecorations)
    interactive = isInteractive
    isUsePrivateClassLoader = requestPrivateClassLoader
    setQuality(if (PreviewPowerSaveManager.isInPowerSaveMode) 0.5f else 0.7f)
    setShowDecorations(showDecorations)
    // The Compose Preview has its own way to track out of date files so we ask the Layoutlib Scene
    // Manager to not
    // report it via the regular log.
    doNotReportOutOfDateUserClasses()
    if (reinflate) {
      forceReinflate()
    }
  }

/** Key for the persistent group state for the Compose Preview. */
private const val SELECTED_GROUP_KEY = "selectedGroup"

/** Key for persisting the selected layout manager. */
private const val LAYOUT_KEY = "previewLayout"

/**
 * A [PreviewRepresentation] that provides a compose elements preview representation of the given
 * `psiFile`.
 *
 * A [component] is implied to display previews for all declared `@Composable` functions that also
 * use the `@Preview` (see [com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN]) annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a
 * `@Composable` functions.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param previewProvider [PreviewElementProvider] to obtain the [ComposePreviewElement]s.
 * @param preferredInitialVisibility preferred [PreferredVisibility] for this representation.
 */
class ComposePreviewRepresentation(
  psiFile: PsiFile,
  previewProvider: PreviewElementProvider<ComposePreviewElement>,
  override val preferredInitialVisibility: PreferredVisibility,
  composePreviewViewProvider: ComposePreviewViewProvider
) :
  PreviewRepresentation,
  ComposePreviewManagerEx,
  UserDataHolderEx by UserDataHolderBase(),
  AndroidCoroutinesAware,
  FastPreviewSurface {

  companion object {
    /**
     * The refresh flow has to be shared across all instances to support viewing multiple files at
     * the same time, because changes in any of the active files could affect the Previews in any of
     * the active representations.
     *
     * Each instance subscribes itself to the flow when it is activated, and it is automatically
     * unsubscribed when the [activationScope] is cancelled (see [onActivate], [initializeFlows] and
     * [onDeactivate])
     */
    private val refreshFlow: MutableSharedFlow<RefreshRequest> = MutableSharedFlow(replay = 1)

    /**
     * Same as [refreshFlow] but only for requests to refresh UI and notifications (without
     * refreshing the preview contents). This allows to bundle notifications and respects the
     * activation/deactivation lifecycle.
     */
    private val refreshNotificationsAndVisibilityFlow: MutableSharedFlow<Unit> =
      MutableSharedFlow(replay = 1)
  }

  /**
   * Fake device id to identify this preview with the live literals service. This allows live
   * literals to track how many "users" it has.
   */
  private val previewDeviceId = "Preview#${UUID.randomUUID()}"
  private val LOG = Logger.getInstance(ComposePreviewRepresentation::class.java)
  private val project = psiFile.project
  private val module = runReadAction { psiFile.module }
  private val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }

  private val projectBuildStatusManager =
    ProjectBuildStatusManager.create(
      this,
      psiFile,
      onReady = {
        // When the preview is opened we must trigger an initial refresh. We wait for the project to
        // be
        // smart and synced to do it.
        when (it) {
          // Do not refresh if we still need to build the project. Instead, only update the empty
          // panel and editor notifications if needed.
          ProjectStatus.NeedsBuild -> requestVisibilityAndNotificationsUpdate()
          else -> requestRefresh()
        }
      }
    )

  /** Frames per second limit for interactive preview. */
  private var fpsLimit = StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT.get()

  /**
   * [UniqueTaskCoroutineLauncher] for ensuring that only one fast preview request is launched at a
   * time.
   */
  private var fastPreviewCompilationLauncher: UniqueTaskCoroutineLauncher? = null

  init {
    val project = psiFile.project
    project
      .messageBus
      .connect(this)
      .subscribe(
        PowerSaveMode.TOPIC,
        PowerSaveMode.Listener {
          fpsLimit =
            if (PreviewPowerSaveManager.isInPowerSaveMode) {
              StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT.get() / 3
            } else {
              StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT.get()
            }
          fpsCounter.resetAndStart()

          // When getting out of power save mode, request a refresh
          if (!PreviewPowerSaveManager.isInPowerSaveMode) requestRefresh()
        }
      )
  }

  /** Whether the preview needs a full refresh or not. */
  private val invalidated = AtomicBoolean(true)

  private val previewFreshnessTracker =
    CodeOutOfDateTracker.create(module, this) {
      invalidate()
      requestRefresh()
    }

  /** [PreviewElementProvider] containing the pinned previews. */
  private val memoizedPinnedPreviewProvider =
    FilteredPreviewElementProvider(PinnedPreviewElementManager.getPreviewElementProvider(project)) {
      !(it.containingFile?.isEquivalentTo(psiFilePointer.containingFile) ?: false)
    }

  /**
   * [PreviewElementProvider] used to save the result of a call to `previewProvider`. Calls to
   * `previewProvider` can potentially be slow. This saves the last result and it is refreshed on
   * demand when we know is not running on the UI thread.
   */
  private val memoizedElementsProvider =
    MemoizedPreviewElementProvider(previewProvider, previewFreshnessTracker)
  private val previewElementProvider = PreviewFilters(memoizedElementsProvider)

  override var groupFilter: PreviewGroup by
    Delegates.observable(ALL_PREVIEW_GROUP) { _, oldValue, newValue ->
      if (oldValue != newValue) {
        LOG.debug("New group preview element selection: $newValue")
        previewElementProvider.groupNameFilter = newValue
        // Force refresh to ensure the new preview elements are picked up
        invalidate()
        requestRefresh()
      }
    }

  @Volatile override var availableGroups: Set<PreviewGroup> = emptySet()

  @Volatile private var interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
  private val navigationHandler = ComposePreviewNavigationHandler()

  private val fpsCounter = FpsCalculator { System.nanoTime() }

  override val interactivePreviewElementInstance: ComposePreviewElementInstance?
    get() = previewElementProvider.instanceFilter

  private val previewElementModelAdapter =
    object : ComposePreviewElementModelAdapter() {
      override fun createDataContext(previewElement: ComposePreviewElementInstance) =
        PreviewElementDataContext(project, this@ComposePreviewRepresentation, previewElement)

      override fun toXml(previewElement: ComposePreviewElementInstance) =
        previewElement
          .toPreviewXml()
          // Whether to paint the debug boundaries or not
          .toolsAttribute("paintBounds", showDebugBoundaries.toString())
          .toolsAttribute("findDesignInfoProviders", hasDesignInfoProviders.toString())
          .apply {
            if (animationInspection.get()) {
              // If the animation inspection is active, start the PreviewAnimationClock with the
              // current epoch time.
              toolsAttribute("animationClockStartTime", System.currentTimeMillis().toString())
            }
          }
          .buildString()
    }

  override suspend fun startInteractivePreview(element: ComposePreviewElementInstance) {
    if (interactiveMode.isStartingOrReady()) return
    LOG.debug("New single preview element focus: $element")
    val isFromAnimationInspection = animationInspection.get()
    // The order matters because we first want to change the composable being previewed and then
    // start interactive loop when enabled
    // but we want to stop the loop first and then change the composable when disabled
    if (isFromAnimationInspection) {
      onAnimationInspectionStop()
    } else {
      requestVisibilityAndNotificationsUpdate()
    }
    interactiveMode = ComposePreviewManager.InteractiveMode.STARTING
    val quickRefresh =
      shouldQuickRefresh() &&
        !isFromAnimationInspection // We should call this before assigning newValue to
    // instanceIdFilter
    val peerPreviews = previewElementProvider.previewElements().count()
    previewElementProvider.instanceFilter = element
    sceneComponentProvider.enabled = false
    val startUpStart = System.currentTimeMillis()
    forceRefresh(quickRefresh)?.invokeOnCompletion {
      surface.sceneManagers.forEach { it.resetInteractiveEventsCounter() }
      if (!isFromAnimationInspection
      ) { // Currently it will re-create classloader and will be slower that switch from static
        InteractivePreviewUsageTracker.getInstance(surface)
          .logStartupTime((System.currentTimeMillis() - startUpStart).toInt(), peerPreviews)
      }
      fpsCounter.resetAndStart()
      ticker.start()
      delegateInteractionHandler.delegate = interactiveInteractionHandler
      composeWorkBench.showPinToolbar = false
      requestVisibilityAndNotificationsUpdate()

      // While in interactive mode, display a small ripple when clicking
      surface.enableMouseClickDisplay()
      surface.background = INTERACTIVE_BACKGROUND_COLOR
      interactiveMode = ComposePreviewManager.InteractiveMode.READY
      ActivityTracker.getInstance().inc()
    }
  }

  override fun stopInteractivePreview() {
    if (interactiveMode.isStoppingOrDisabled()) return

    LOG.debug("Stopping interactive")
    onInteractivePreviewStop()
    requestVisibilityAndNotificationsUpdate()
    onStaticPreviewStart()
    forceRefresh()?.invokeOnCompletion {
      interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
    }
  }

  private fun onStaticPreviewStart() {
    sceneComponentProvider.enabled = true
    surface.background = defaultSurfaceBackground
  }

  private fun onInteractivePreviewStop() {
    interactiveMode = ComposePreviewManager.InteractiveMode.STOPPING
    surface.disableMouseClickDisplay()
    delegateInteractionHandler.delegate = staticPreviewInteractionHandler
    composeWorkBench.showPinToolbar = true
    requestVisibilityAndNotificationsUpdate()
    ticker.stop()
    previewElementProvider.clearInstanceIdFilter()
    logInteractiveSessionMetrics()
  }

  private fun pauseInteractivePreview() {
    ticker.stop()
    surface.sceneManagers.forEach { it.pauseSessionClock() }
  }

  private fun resumeInteractivePreview() {
    fpsCounter.resetAndStart()
    surface.sceneManagers.forEach { it.resumeSessionClock() }
    ticker.start()
  }

  private val animationInspection = AtomicBoolean(false)

  override var animationInspectionPreviewElementInstance: ComposePreviewElementInstance?
    set(value) {
      if ((!animationInspection.get() && value != null) ||
          (animationInspection.get() && value == null)
      ) {
        if (value != null) {
          if (interactiveMode != ComposePreviewManager.InteractiveMode.DISABLED) {
            onInteractivePreviewStop()
          }
          LOG.debug("Animation Preview open for preview: $value")
          ComposePreviewAnimationManager.onAnimationInspectorOpened()
          previewElementProvider.instanceFilter = value
          animationInspection.set(true)
          sceneComponentProvider.enabled = false
          composeWorkBench.showPinToolbar = false

          // Open the animation inspection panel
          composeWorkBench.bottomPanel =
            ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, this) {
                // Close this inspection panel, making all the necessary UI changes (e.g. changing
                // background and refreshing the preview) before
                // opening a new one.
                animationInspectionPreviewElementInstance = null
              }
              .component
          surface.background = INTERACTIVE_BACKGROUND_COLOR
        } else {
          onAnimationInspectionStop()
          onStaticPreviewStart()
        }
        forceRefresh()?.invokeOnCompletion {
          interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
          ActivityTracker.getInstance().inc()
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
    composeWorkBench.showPinToolbar = true
    previewElementProvider.instanceFilter = null
  }

  override val hasDesignInfoProviders: Boolean
    get() = module?.let { hasDesignInfoProviders(it) } ?: false

  override var showDebugBoundaries: Boolean = false
    set(value) {
      field = value
      invalidate()
      requestRefresh()
    }

  override val previewedFile: PsiFile?
    get() = psiFilePointer.element

  private val dataProvider = DataProvider {
    when (it) {
      COMPOSE_PREVIEW_MANAGER.name -> this@ComposePreviewRepresentation
      // The Compose preview NlModels do not point to the actual file but to a synthetic file
      // generated for Layoutlib. This ensures we return the right file.
      CommonDataKeys.VIRTUAL_FILE.name -> psiFilePointer.virtualFile
      CommonDataKeys.PROJECT.name -> project
      else -> null
    }
  }

  private val delegateInteractionHandler = DelegateInteractionHandler()
  private val sceneComponentProvider = ComposeSceneComponentProvider()

  private val composeWorkBench: ComposePreviewView = invokeAndWaitIfNeeded {
    composePreviewViewProvider.invoke(
      project,
      psiFilePointer,
      projectBuildStatusManager,
      dataProvider,
      createMainDesignSurfaceBuilder(
        project,
        navigationHandler,
        delegateInteractionHandler,
        dataProvider, // Will be overridden by the preview provider
        this,
        sceneComponentProvider
      ),
      listOf(
        createPinnedDesignSurfaceBuilder(
          project,
          navigationHandler,
          delegateInteractionHandler,
          dataProvider,
          this,
          sceneComponentProvider
        )
      ),
      this,
      PinAllPreviewElementsAction(
        { PinnedPreviewElementManager.getInstance(project).isPinned(psiFile) },
        previewElementProvider
      ),
      UnpinAllPreviewElementsAction
    )
  }

  private val staticPreviewInteractionHandler =
    NavigatingInteractionHandler(composeWorkBench.mainSurface).also {
      delegateInteractionHandler.delegate = it
    }
  private val interactiveInteractionHandler =
    LayoutlibInteractionHandler(composeWorkBench.mainSurface)

  private val pinnedSurface: NlDesignSurface
    get() = composeWorkBench.pinnedSurface
  private val surface: NlDesignSurface
    get() = composeWorkBench.mainSurface

  /**
   * Default background used by the surface. This is used to restore the state after disabling the
   * interactive preview.
   */
  private val defaultSurfaceBackground: Color = surface.background

  /** List of [ComposePreviewElement] being rendered by this editor */
  private var renderedElements: List<ComposePreviewElement> = emptyList()

  /**
   * Counts the current number of simultaneous executions of [refresh] method. Being inside the
   * [refresh] indicates that the this preview is being refreshed. Even though [requestRefresh]
   * guarantees that only at most a single refresh happens at any point in time, there might be
   * several simultaneous calls to [refresh] method and therefore we need a counter instead of
   * boolean flag.
   */
  private val refreshCallsCount = AtomicInteger(0)

  /**
   * This field will be false until the preview has rendered at least once. If the preview has not
   * rendered once we do not have enough information about errors and the rendering to show the
   * preview. Once it has rendered, even with errors, we can display additional information about
   * the state of the preview.
   */
  private val hasRenderedAtLeastOnce = AtomicBoolean(false)

  /**
   * Callback first time after the preview has loaded the initial state and it's ready to restore
   * any saved state.
   */
  private var onRestoreState: (() -> Unit)? = null

  private val ticker =
    ControllableTicker(
      {
        if (!RenderService.isBusy() && fpsCounter.getFps() <= fpsLimit) {
          fpsCounter.incrementFrameCounter()
          surface.sceneManager?.executeCallbacksAndRequestRender(null)
        }
      },
      Duration.ofMillis(5)
    )

  private val lifecycleManager =
    PreviewLifecycleManager(
      project,
      this,
      this,
      { activate(false) },
      { activate(true) },
      {
        LOG.debug("onDeactivate")
        if (interactiveMode.isStartingOrReady()) {
          pauseInteractivePreview()
        }
        // The editor is scheduled to be deactivated, deactivate its issue model to avoid updating
        // publish the issue update event.
        surface.deactivateIssueModel()
      },
      {
        stopInteractivePreview()
        LOG.debug("Delayed surface deactivation")
        surface.deactivate()
      }
    )

  init {
    Disposer.register(this, ticker)
  }

  override val component: JComponent
    get() = composeWorkBench.component

  private data class RefreshRequest(val quickRefresh: Boolean) {
    val requestId = UUID.randomUUID().toString().substring(0, 5)
  }
  // region Lifecycle handling
  @TestOnly
  fun needsRefreshOnSuccessfulBuild() = previewFreshnessTracker.needsRefreshOnSuccessfulBuild()

  @TestOnly fun buildWillTriggerRefresh() = previewFreshnessTracker.buildWillTriggerRefresh()

  override fun invalidateSavedBuildStatus() {
    previewFreshnessTracker.invalidateSavedBuildStatus()
  }

  /**
   * Completes the initialization of the preview. This method is only called once after the first
   * [onActivate] happens.
   */
  private fun onInit() {
    LOG.debug("onInit")
    if (Disposer.isDisposed(this)) {
      LOG.info("Preview was closed before the initialization completed.")
    }
    val psiFile = psiFilePointer.element
    requireNotNull(psiFile) { "PsiFile was disposed before the preview initialization completed." }

    setupBuildListener(
      project,
      object : BuildListener {
        override fun buildSucceeded() {
          LOG.debug("buildSucceeded")
          module?.let {
            // When the build completes successfully, we do not need the overlay until a
            // modifications has happened.
            ModuleClassLoaderOverlays.getInstance(it).invalidateOverlayPaths()
          }

          val file = psiFilePointer.element
          if (file == null) {
            LOG.debug("invalid PsiFile")
            return
          }

          // If Fast Preview is enabled, prefetch the daemon for the current configuration.
          if (module != null && FastPreviewManager.getInstance(project).isEnabled) {
            FastPreviewManager.getInstance(project).preStartDaemon(module)
          }

          afterBuildComplete(true)
        }

        override fun buildFailed() {
          LOG.debug("buildFailed")

          afterBuildComplete(false)
        }

        override fun buildCleaned() {
          LOG.debug("buildCleaned")

          buildFailed()
        }

        override fun buildStarted() {
          LOG.debug("buildStarted")

          composeWorkBench.updateProgress(message("panel.building"))
          afterBuildStarted()
        }
      },
      this
    )

    FastPreviewManager.getInstance(project)
      .addListener(
        this,
        object : FastPreviewManager.Companion.FastPreviewManagerListener {
          override fun onCompilationStarted(files: Collection<PsiFile>) {
            psiFilePointer.element?.let { editorFile ->
              if (files.any { it.isEquivalentTo(editorFile) }) afterBuildStarted()
            }
          }

          override fun onCompilationComplete(
            result: CompilationResult,
            files: Collection<PsiFile>
          ) {
            psiFilePointer.element?.let { editorFile ->
              if (files.any { it.isEquivalentTo(editorFile) })
                afterBuildComplete(result == CompilationResult.Success)
            }
          }
        }
      )
  }

  private fun afterBuildComplete(isSuccessful: Boolean) {
    requestVisibilityAndNotificationsUpdate()
  }

  private fun afterBuildStarted() {
    // When building, invalidate the Animation Inspector, since the animations are now obsolete and
    // new ones will be subscribed once
    // build is complete and refresh is triggered.
    ComposePreviewAnimationManager.invalidate()
    requestVisibilityAndNotificationsUpdate()
  }

  /** Initializes the flows that will listen to different events and will call [requestRefresh]. */
  @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
  private fun CoroutineScope.initializeFlows() {
    with(this@initializeFlows) {
      // Launch all the listeners that are bound to the current activation.

      // Flow to collate and process requestRefresh requests.
      launch(workerThread) {
        refreshFlow.conflate().collect {
          refreshFlow
            .resetReplayCache() // Do not keep re-playing after we have received the element.
          LOG.debug("refreshFlow, request=$it")
          refresh(it)?.join()
        }
      }

      // Flow to collate and process refreshNotificationsAndVisibilityFlow requests.
      launch(workerThread) {
        refreshNotificationsAndVisibilityFlow.conflate().collect {
          refreshNotificationsAndVisibilityFlow
            .resetReplayCache() // Do not keep re-playing after we have received the element.
          LOG.debug("refreshNotificationsAndVisibilityFlow, request=$it")
          composeWorkBench.updateVisibilityAndNotifications()
        }
      }

      launch(workerThread) {
        LOG.debug(
          "smartModeFlow setup status=${projectBuildStatusManager.status}, dumbMode=${DumbService.isDumb(project)}"
        )
        merge(
            // Flow handling switch to smart mode.
            smartModeFlow(project, this@ComposePreviewRepresentation, LOG),

            // Flow handling pinned elements updates.
            if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
              disposableCallbackFlow("PinnedPreviewsFlow", LOG, this@ComposePreviewRepresentation) {
                val listener = PinnedPreviewElementManager.Listener { trySend(Unit) }
                PinnedPreviewElementManager.getInstance(project).addListener(listener)
                Disposer.register(disposable) {
                  PinnedPreviewElementManager.getInstance(project).removeListener(listener)
                }
              }
            } else emptyFlow(),
          )
          .collectLatest {
            LOG.debug(
              "smartModeFlow, status change status=${projectBuildStatusManager.status}, dumbMode=${DumbService.isDumb(project)}"
            )
            when (projectBuildStatusManager.status) {
              // Do not refresh if we still need to build the project. Instead, only update the
              // empty panel and editor notifications if needed.
              ProjectStatus.NotReady,
              ProjectStatus.NeedsBuild -> requestVisibilityAndNotificationsUpdate()
              else -> requestRefresh()
            }
          }
      }

      // Flow handling file changes and syntax error changes.
      launch(workerThread) {
        val psiFile = psiFilePointer.element ?: return@launch
        merge(
            documentChangeFlow(psiFile, this@ComposePreviewRepresentation, LOG).debounce {
              // The debounce timer is smaller when running with Fast Preview so the changes are
              // more responsive to typing.
              if (FastPreviewManager.getInstance(project).isAvailable) 250L else 1000L
            },
            disposableCallbackFlow<Unit>(
              "SyntaxErrorFlow",
              LOG,
              this@ComposePreviewRepresentation
            ) {
              project
                .messageBus
                .connect(disposable)
                .subscribe(
                  ProblemListener.TOPIC,
                  object : ProblemListener {
                    override fun problemsDisappeared(file: VirtualFile) {
                      if (file != psiFilePointer.virtualFile ||
                          !FastPreviewManager.getInstance(project).isEnabled
                      )
                        return
                      trySend(Unit)
                    }
                  }
                )
            }
          )
          .conflate()
          .collect {
            if (FastPreviewManager.getInstance(project).isEnabled) {
              try {
                requestFastPreviewRefresh()
              } catch (_: Throwable) {
                // Ignore any cancellation exceptions
              }
              return@collect
            }

            if (!PreviewPowerSaveManager.isInPowerSaveMode &&
                interactiveMode.isStoppingOrDisabled() &&
                !animationInspection.get()
            )
              requestRefresh()
          }
      }
    }
  }

  override fun onActivate() {
    lifecycleManager.activate()
  }

  private fun CoroutineScope.activate(resume: Boolean) {
    LOG.debug("onActivate")

    initializeFlows()

    if (!resume) {
      onInit()
    }

    surface.activate()

    if (interactiveMode.isStartingOrReady()) {
      resumeInteractivePreview()
    }
  }

  override fun onDeactivate() {
    lifecycleManager.deactivate()
  }
  // endregion

  override fun onCaretPositionChanged(event: CaretEvent, isModificationTriggered: Boolean) {
    if (PreviewPowerSaveManager.isInPowerSaveMode) return
    if (isModificationTriggered) return // We do not move the preview while the user is typing
    if (!StudioFlags.COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE.get()) return
    if (interactiveMode.isStartingOrReady()) return
    // If we have not changed line, ignore
    if (event.newPosition.line == event.oldPosition.line) return
    val offset = event.editor.logicalPositionToOffset(event.newPosition)

    lifecycleManager.executeIfActive {
      launch(uiThread) {
        val filePreviewElements =
          withContext(workerThread) { memoizedElementsProvider.previewElements() }
        // Workaround for b/238735830: The following withContext(uiThread) should not be needed but
        // the code below ends up being executed
        // in a worker thread under some circumstances so we need to prevent that from happening by
        // forcing the context switch.
        withContext(uiThread) {
          filePreviewElements
            .find { element ->
              element.previewBodyPsi?.psiRange.containsOffset(offset) ||
                element.previewElementDefinitionPsi?.psiRange.containsOffset(offset)
            }
            ?.let { selectedPreviewElement ->
              surface.models.find {
                previewElementModelAdapter.modelToElement(it) == selectedPreviewElement
              }
            }
            ?.let { surface.scrollToVisible(it, true) }
        }
      }
    }
  }

  private fun logInteractiveSessionMetrics() {
    val touchEvents = surface.sceneManagers.map { it.interactiveEventsCount }.sum()
    InteractivePreviewUsageTracker.getInstance(surface)
      .logInteractiveSession(fpsCounter.getFps(), fpsCounter.getDurationMs(), touchEvents)
  }

  override fun dispose() {
    if (interactiveMode == ComposePreviewManager.InteractiveMode.READY) {
      logInteractiveSessionMetrics()
    }
    animationInspectionPreviewElementInstance = null
  }

  private var lastPinsModificationCount = -1L

  private fun hasErrorsAndNeedsBuild(): Boolean =
    renderedElements.isNotEmpty() &&
      (!hasRenderedAtLeastOnce.get() ||
        surface.sceneManagers.any { it.renderResult.isErrorResult(COMPOSE_VIEW_ADAPTER_FQN) })

  private fun hasSyntaxErrors(): Boolean =
    WolfTheProblemSolver.getInstance(project).isProblemFile(psiFilePointer.virtualFile)

  /**
   * Cached previous [ComposePreviewManager.Status] used to trigger notifications if there's been a
   * change.
   */
  private val previousStatusRef: AtomicReference<ComposePreviewManager.Status?> =
    AtomicReference(null)

  override fun status(): ComposePreviewManager.Status {
    val isRefreshing =
      (refreshCallsCount.get() > 0 ||
        DumbService.isDumb(project) ||
        invokeAndWaitIfNeeded { projectBuildStatusManager.isBuilding })

    // If we are refreshing, we avoid spending time checking other conditions like errors or if the
    // preview
    // is out of date.
    val newStatus =
      ComposePreviewManager.Status(
        !isRefreshing && hasErrorsAndNeedsBuild(),
        !isRefreshing && hasSyntaxErrors(),
        !isRefreshing && projectBuildStatusManager.status == ProjectStatus.OutOfDate,
        isRefreshing,
        interactiveMode,
      )

    // This allows us to display notifications synchronized with any other change detection. The
    // moment we detect a difference,
    // we immediately ask the editor to refresh the notifications.
    // For example, IntelliJ will periodically update the toolbar. If one of the actions checks the
    // state and changes its UI, this will
    // allow for notifications to be refreshed at the same time.
    val previousStatus = previousStatusRef.getAndSet(newStatus)
    if (newStatus != previousStatus) {
      requestVisibilityAndNotificationsUpdate()
    }

    return newStatus
  }

  /** Method for tests to access the surfaces managed by this [ComposePreviewRepresentation]. */
  @TestOnly internal fun surfaces() = listOfNotNull(pinnedSurface, surface)

  /**
   * Method called when the notifications of the [PreviewRepresentation] need to be updated. This is
   * called by the [ComposePreviewNotificationProvider] when the editor needs to refresh the
   * notifications.
   */
  override fun updateNotifications(parentEditor: FileEditor) =
    composeWorkBench.updateNotifications(parentEditor)

  private fun configureLayoutlibSceneManagerForPreviewElement(
    displaySettings: PreviewDisplaySettings,
    layoutlibSceneManager: LayoutlibSceneManager
  ) =
    configureLayoutlibSceneManager(
      layoutlibSceneManager,
      showDecorations = displaySettings.showDecoration,
      isInteractive = interactiveMode.isStartingOrReady(),
      requestPrivateClassLoader = usePrivateClassLoader()
    )

  private fun onAfterRender() {
    composeWorkBench.hasRendered = true
    hasRenderedAtLeastOnce.set(true)
  }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those
   * elements. The call will block until all the given [ComposePreviewElement]s have completed
   * rendering. If [quickRefresh] is true the preview surfaces for the same [ComposePreviewElement]s
   * do not get reinflated, this allows to save time for e.g. static to animated preview transition.
   * A [ProgressIndicator] that runs while refresh is in progress is given, and this method should
   * return early if the indicator is cancelled.
   */
  private suspend fun doRefreshSync(
    filePreviewElements: List<ComposePreviewElement>,
    quickRefresh: Boolean,
    progressIndicator: ProgressIndicator
  ) {
    if (LOG.isDebugEnabled) LOG.debug("doRefresh of ${filePreviewElements.count()} elements.")
    val psiFile =
      runReadAction {
        val element = psiFilePointer.element

        return@runReadAction if (element == null || !element.isValid) {
          LOG.warn("doRefresh with invalid PsiFile")
          null
        } else {
          element
        }
      }
        ?: return

    // Cache available groups
    availableGroups = previewElementProvider.allAvailableGroups()

    // Restore
    onRestoreState?.invoke()
    onRestoreState = null

    val arePinsEnabled =
      StudioFlags.COMPOSE_PIN_PREVIEW.get() &&
        interactiveMode.isStoppingOrDisabled() &&
        !animationInspection.get()
    val hasPinnedElements =
      if (arePinsEnabled) {
        memoizedPinnedPreviewProvider.previewElements().any()
      } else false

    composeWorkBench.setPinnedSurfaceVisibility(hasPinnedElements)
    val pinnedManager = PinnedPreviewElementManager.getInstance(project)
    if (hasPinnedElements) {
      pinnedSurface.updatePreviewsAndRefresh(
        true,
        memoizedPinnedPreviewProvider,
        LOG,
        psiFile,
        this,
        progressIndicator,
        this::onAfterRender,
        previewElementModelAdapter,
        this::configureLayoutlibSceneManagerForPreviewElement
      )
    }
    lastPinsModificationCount = pinnedManager.modificationCount
    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    val showingPreviewElements =
      surface.updatePreviewsAndRefresh(
        !quickRefresh,
        previewElementProvider,
        LOG,
        psiFile,
        this,
        progressIndicator,
        this::onAfterRender,
        previewElementModelAdapter,
        this::configureLayoutlibSceneManagerForPreviewElement
      )
    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    if (showingPreviewElements.size >= filePreviewElements.size) {
      renderedElements = filePreviewElements
    } else {
      // Some preview elements did not result in model creations. This could be because of failed
      // PreviewElements instantiation.
      // TODO(b/160300892): Add better error handling for failed instantiations.
      LOG.warn("Some preview elements have failed")
    }
  }

  private fun requestRefresh(quickRefresh: Boolean = false) {
    if (LOG.isDebugEnabled) LOG.debug("requestRefresh", Throwable())
    launch(workerThread) { refreshFlow.emit(RefreshRequest(quickRefresh)) }
  }

  private fun requestVisibilityAndNotificationsUpdate() {
    launch(workerThread) { refreshNotificationsAndVisibilityFlow.emit(Unit) }
  }

  /**
   * Requests a refresh the preview surfaces. This will retrieve all the Preview annotations and
   * render those elements. The refresh will only happen if the Preview elements have changed from
   * the last render.
   */
  private fun refresh(refreshRequest: RefreshRequest): Job? {
    val requestLogger = LoggerWithFixedInfo(LOG, mapOf("requestId" to refreshRequest.requestId))
    requestLogger.debug("Refresh triggered. quickRefresh: ${refreshRequest.quickRefresh}")
    val refreshTrigger: Throwable? = if (LOG.isDebugEnabled) Throwable() else null
    val startTime = System.nanoTime()
    // Start a progress indicator so users are aware that a long task is running. Stop it by calling
    // processFinish() if returning early.
    val refreshProgressIndicator =
      BackgroundableProcessIndicator(
        project,
        message("refresh.progress.indicator.title"),
        "",
        "",
        true
      )
    if (!Disposer.tryRegister(this, refreshProgressIndicator)) return null
    // This is not launched in the activation scope to avoid cancelling the refresh mid-way when the
    // user changes tabs.
    val refreshJob =
      launchWithProgress(refreshProgressIndicator, uiThread) {
        requestLogger.debug("Refresh triggered (inside launchWithProgress scope)", refreshTrigger)

        if (DumbService.isDumb(project)) {
          requestLogger.debug("Project is in dumb mode, not able to refresh")
          return@launchWithProgress
        }

        if (projectBuildStatusManager.status == ProjectStatus.NeedsBuild) {
          // Project needs to be built before being able to refresh.
          requestLogger.debug("Project has not build, not able to refresh")
          return@launchWithProgress
        }

        if (Bridge.hasNativeCrash() && composeWorkBench is ComposePreviewViewImpl) {
          (composeWorkBench.component as WorkBench<DesignSurface<*>>).handleLayoutlibNativeCrash {
            requestRefresh()
          }
          return@launchWithProgress
        }

        requestVisibilityAndNotificationsUpdate()
        refreshCallsCount.incrementAndGet()

        try {
          refreshProgressIndicator.text = message("refresh.progress.indicator.finding.previews")
          val filePreviewElements =
            withContext(workerThread) {
              memoizedElementsProvider.previewElements().toList().sortByDisplayAndSourcePosition()
            }

          val pinnedPreviewElements =
            if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
              refreshProgressIndicator.text =
                message("refresh.progress.indicator.finding.pinned.previews")

              withContext(workerThread) {
                memoizedPinnedPreviewProvider
                  .previewElements()
                  .toList()
                  .sortByDisplayAndSourcePosition()
              }
            } else emptyList()

          val needsFullRefresh =
            invalidated.getAndSet(false) ||
              renderedElements != filePreviewElements ||
              PinnedPreviewElementManager.getInstance(project).modificationCount !=
                lastPinsModificationCount

          composeWorkBench.hasContent =
            filePreviewElements.isNotEmpty() || pinnedPreviewElements.isNotEmpty()
          if (!needsFullRefresh) {
            requestLogger.debug(
              "No updates on the PreviewElements, just refreshing the existing ones"
            )
            // In this case, there are no new previews. We need to make sure that the surface is
            // still correctly
            // configured and that we are showing the right size for components. For example, if the
            // user switches on/off
            // decorations, that will not generate/remove new PreviewElements but will change the
            // surface settings.
            refreshProgressIndicator.text =
              message("refresh.progress.indicator.reusing.existing.previews")
            surface.refreshExistingPreviewElements(
              refreshProgressIndicator,
              previewElementModelAdapter::modelToElement,
              this@ComposePreviewRepresentation::configureLayoutlibSceneManagerForPreviewElement
            )
          } else {
            refreshProgressIndicator.text =
              message("refresh.progress.indicator.refreshing.all.previews")
            composeWorkBench.updateProgress(message("panel.initializing"))
            doRefreshSync(
              filePreviewElements,
              refreshRequest.quickRefresh,
              refreshProgressIndicator
            )
          }
        } catch (t: Throwable) {
          requestLogger.warn("Request failed", t)
        } finally {
          refreshCallsCount.decrementAndGet()
          // Force updating toolbar icons after refresh
          ActivityTracker.getInstance().inc()
        }
      }

    refreshJob.invokeOnCompletion {
      LOG.debug("Completed")
      Disposer.dispose(refreshProgressIndicator)
      if (it is CancellationException) {
        composeWorkBench.onRefreshCancelledByTheUser()
      } else composeWorkBench.onRefreshCompleted()

      launch(uiThread) {
        if (!composeWorkBench.isMessageBeingDisplayed) {
          // Only notify the preview refresh time if there are previews to show.
          val durationString =
            Duration.ofMillis((System.nanoTime() - startTime) / 1_000_000).toDisplayString()
          val notification =
            Notification(
              PREVIEW_NOTIFICATION_GROUP_ID,
              message("event.log.refresh.title"),
              message("event.log.refresh.total.elapsed.time", durationString),
              NotificationType.INFORMATION
            )
          Notifications.Bus.notify(notification, project)
        }
      }
    }
    return refreshJob
  }

  override fun getState(): PreviewRepresentationState {
    val selectedGroupName = previewElementProvider.groupNameFilter.name ?: ""
    val selectedLayoutName =
      PREVIEW_LAYOUT_MANAGER_OPTIONS
        .find {
          (surface.sceneViewLayoutManager as LayoutManagerSwitcher).isLayoutManagerSelected(
            it.layoutManager
          )
        }
        ?.displayName
        ?: ""
    return mapOf(SELECTED_GROUP_KEY to selectedGroupName, LAYOUT_KEY to selectedLayoutName)
  }

  override fun setState(state: PreviewRepresentationState) {
    val selectedGroupName = state[SELECTED_GROUP_KEY]
    val previewLayoutName = state[LAYOUT_KEY]
    onRestoreState = {
      if (!selectedGroupName.isNullOrEmpty()) {
        availableGroups.find { it.name == selectedGroupName }?.let { groupFilter = it }
      }

      PREVIEW_LAYOUT_MANAGER_OPTIONS.find { it.displayName == previewLayoutName }?.let {
        (surface.sceneViewLayoutManager as LayoutManagerSwitcher).setLayoutManager(it.layoutManager)
      }
    }
  }

  /**
   * Whether the scene manager should use a private ClassLoader. Currently, that's done for
   * interactive preview and animation inspector, where it's crucial not to share the state (which
   * includes the compose framework).
   */
  private fun usePrivateClassLoader() =
    interactiveMode.isStartingOrReady() || animationInspection.get() || shouldQuickRefresh()

  private fun invalidate() {
    invalidated.set(true)
  }

  internal fun forceRefresh(quickRefresh: Boolean = false): Job? {
    invalidate()
    return refresh(RefreshRequest(quickRefresh))
  }

  override fun registerShortcuts(applicableTo: JComponent) {
    psiFilePointer.element?.let {
      BuildAndRefresh { it }
        .registerCustomShortcutSet(getBuildAndRefreshShortcut(), applicableTo, this)
    }
  }

  /**
   * We will only do quick refresh if there is a single preview. When live literals is enabled, we
   * want to try to preserve the same class loader as much as possible.
   */
  private fun shouldQuickRefresh() = renderedElements.count() == 1

  private suspend fun requestFastPreviewRefresh(): CompilationResult? {
    val currentStatus = status()
    val launcher =
      fastPreviewCompilationLauncher
        ?: UniqueTaskCoroutineLauncher(this, "Compilation Launcher").also {
          fastPreviewCompilationLauncher = it
        }

    // We delay the reporting of compilationSucceded until we have the amount of time the refresh
    // took. Either refreshSucceeded or
    // refreshFailed should be called.
    val delegateRequestTracker = FastPreviewTrackerManager.getInstance(project).trackRequest()
    val requestTracker =
      object : FastPreviewTrackerManager.Request by delegateRequestTracker {
        private var compilationDurationMs: Long = -1
        private var compiledFiles: Int = -1
        private var compilationSuccess: Boolean? = null
        override fun compilationSucceeded(
          compilationDurationMs: Long,
          compiledFiles: Int,
          refreshTimeMs: Long
        ) {
          compilationSuccess = true
          this.compilationDurationMs = compilationDurationMs
          this.compiledFiles = compiledFiles
        }

        override fun compilationFailed(compilationDurationMs: Long, compiledFiles: Int) {
          compilationSuccess = false
          this.compilationDurationMs = compilationDurationMs
          this.compiledFiles = compiledFiles
        }

        /**
         * Reports that the refresh has completed. If [refreshTimeMs] is -1, the refresh has failed.
         */
        private fun reportRefresh(refreshTimeMs: Long = -1) {
          when (compilationSuccess) {
            true ->
              delegateRequestTracker.compilationSucceeded(
                compilationDurationMs,
                compiledFiles,
                refreshTimeMs
              )
            false -> delegateRequestTracker.compilationFailed(compilationDurationMs, compiledFiles)
            null -> Unit
          }
        }

        fun refreshSucceeded(refreshTimeMs: Long) {
          reportRefresh(refreshTimeMs)
        }

        fun refreshFailed() {
          reportRefresh()
        }
      }

    // We only want the first result sent through the channel
    val deferredCompilationResult = CompletableDeferred<CompilationResult?>(null)

    launcher.launch {
      var refreshJob: Job? = null
      try {
        if (!currentStatus.hasSyntaxErrors) {
          psiFilePointer.element?.let {
            val result =
              fastCompile(this@ComposePreviewRepresentation, it, requestTracker = requestTracker)
            deferredCompilationResult.complete(result)
            if (result is CompilationResult.Success) {
              val refreshStartMs = System.currentTimeMillis()
              refreshJob = forceRefresh()
              refreshJob?.invokeOnCompletion { throwable ->
                when (throwable) {
                  null ->
                    requestTracker.refreshSucceeded(System.currentTimeMillis() - refreshStartMs)
                  is CancellationException ->
                    requestTracker.refreshCancelled(compilationCompleted = true)
                  else -> requestTracker.refreshFailed()
                }
                requestVisibilityAndNotificationsUpdate()
              }
              refreshJob?.join()
            } else {
              if (result is CompilationResult.CompilationAborted) {
                requestTracker.refreshCancelled(compilationCompleted = false)
              } else {
                // Compilation failed, report the refresh as failed too
                requestTracker.refreshFailed()
              }
            }
          }
        }
        // At this point, the compilation result should have already been sent if any compilation
        // was done. So, send null result, that will only succeed when fastCompile was not called.
        deferredCompilationResult.complete(null)
      } catch (e: CancellationException) {
        // Any cancellations during the compilation step are handled by fastCompile, so at
        // this point, the compilation was completed or no compilation was done. Either way,
        // a compilation result was already sent through the channel. However, the refresh
        // may still need to be cancelled.
        // Use NonCancellable to make sure to wait until the cancellation is completed.
        withContext(NonCancellable) {
          deferredCompilationResult.complete(CompilationResult.CompilationAborted())
          refreshJob?.cancelAndJoin()
          throw e
        }
      }
    }
    // wait only for the compilation to finish, not for the whole refresh
    return deferredCompilationResult.await()
  }

  override fun requestFastPreviewRefreshAsync(): Deferred<CompilationResult?> =
    lifecycleManager.executeIfActive { async { requestFastPreviewRefresh() } }
      ?: CompletableDeferred(null)
}
