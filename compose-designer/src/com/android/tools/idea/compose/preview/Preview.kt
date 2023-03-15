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
import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.LayoutlibInteractionHandler
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.compose.preview.PreviewGroup.Companion.ALL_PREVIEW_GROUP
import com.android.tools.idea.compose.preview.analytics.InteractivePreviewUsageTracker
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager
import com.android.tools.idea.compose.preview.designinfo.hasDesignInfoProviders
import com.android.tools.idea.compose.preview.fast.FastPreviewSurface
import com.android.tools.idea.compose.preview.navigation.ComposePreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.util.FpsCalculator
import com.android.tools.idea.compose.preview.util.containsOffset
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.SyntaxErrorUpdate
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.concurrency.conflateLatest
import com.android.tools.idea.concurrency.launchWithProgress
import com.android.tools.idea.concurrency.psiFileChangeFlow
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.concurrency.syntaxErrorFlow
import com.android.tools.idea.concurrency.wrapCompletableDeferredCollection
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.build.PsiCodeFileChangeDetectorService
import com.android.tools.idea.editors.build.outOfDateKtFiles
import com.android.tools.idea.editors.fast.CompilationResult
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.requestFastPreviewRefreshAndTrack
import com.android.tools.idea.editors.powersave.PreviewPowerSaveManager
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LoggerWithFixedInfo
import com.android.tools.idea.preview.NavigatingInteractionHandler
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.actions.BuildAndRefresh
import com.android.tools.idea.preview.lifecycle.PreviewLifecycleManager
import com.android.tools.idea.preview.refreshExistingPreviewElements
import com.android.tools.idea.preview.sortByDisplayAndSourcePosition
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.needsBuild
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.uibuilder.actions.LayoutManagerSwitcher
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
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
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.io.File
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile

/** Background color for the surface while "Interactive" is enabled. */
private val INTERACTIVE_BACKGROUND_COLOR = JBColor(0xF7F8FA, 0x2B2D30)

/** [Notification] group ID. Must match the `groupNotification` entry of `compose-designer.xml`. */
const val PREVIEW_NOTIFICATION_GROUP_ID = "Compose Preview Notification"

/**
 * [NlModel] associated preview data
 *
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
 *
 * @param showDecorations when true, the rendered content will be shown with the full device size
 *   specified in the device configuration and with the frame decorations.
 * @param isInteractive whether the scene displays an interactive preview.
 */
@VisibleForTesting
fun configureLayoutlibSceneManager(
  sceneManager: LayoutlibSceneManager,
  showDecorations: Boolean,
  isInteractive: Boolean,
  requestPrivateClassLoader: Boolean,
  runAtfChecks: Boolean
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
    if (runAtfChecks) {
      setCustomContentHierarchyParser(accessibilityBasedHierarchyParser)
    } else {
      setCustomContentHierarchyParser(null)
    }
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
 * @param preferredInitialVisibility preferred [PreferredVisibility] for this representation.
 * @param composePreviewViewProvider [ComposePreviewView] provider.
 */
class ComposePreviewRepresentation(
  psiFile: PsiFile,
  override val preferredInitialVisibility: PreferredVisibility,
  composePreviewViewProvider: ComposePreviewViewProvider
) :
  PreviewRepresentation,
  ComposePreviewManagerEx,
  UserDataHolderEx by UserDataHolderBase(),
  AndroidCoroutinesAware,
  FastPreviewSurface {

  /**
   * Each instance subscribes itself to the flow when it is activated, and it is automatically
   * unsubscribed when the [lifecycleManager] detects a deactivation (see [onActivate],
   * [initializeFlows] and [onDeactivate])
   */
  private val refreshFlow: MutableSharedFlow<RefreshRequest> = MutableSharedFlow(replay = 1)

  /**
   * Same as [refreshFlow] but only for requests to refresh UI and notifications (without refreshing
   * the preview contents). This allows to bundle notifications and respects the
   * activation/deactivation lifecycle.
   */
  private val refreshNotificationsAndVisibilityFlow: MutableSharedFlow<Unit> =
    MutableSharedFlow(replay = 1)

  private val log = Logger.getInstance(ComposePreviewRepresentation::class.java)
  private val isDisposed = AtomicBoolean(false)

  private val project = psiFile.project
  private val module = runReadAction { psiFile.module }
  private val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }

  private val previewElementsFlow: MutableStateFlow<Set<ComposePreviewElement>> =
    MutableStateFlow(emptySet())

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
  private val fastPreviewCompilationLauncher: UniqueTaskCoroutineLauncher by
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
      UniqueTaskCoroutineLauncher(this, "Compilation Launcher")
    }

  /**
   * This field will be false until the preview has rendered at least once. If the preview has not
   * rendered once we do not have enough information about errors and the rendering to show the
   * preview. Once it has rendered, even with errors, we can display additional information about
   * the state of the preview.
   */
  private val hasRenderedAtLeastOnce = AtomicBoolean(false)

  init {
    val project = psiFile.project
    project.messageBus
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

  private val previewElementProvider =
    PreviewFilters(
      object : PreviewElementProvider<ComposePreviewElement> {
        override suspend fun previewElements(): Sequence<ComposePreviewElement> =
          previewElementsFlow.value.asSequence()
      }
    )

  override var groupFilter: PreviewGroup by
    Delegates.observable(ALL_PREVIEW_GROUP) { _, oldValue, newValue ->
      if (oldValue != newValue) {
        log.debug("New group preview element selection: $newValue")
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

  override suspend fun startInteractivePreview(instance: ComposePreviewElementInstance) {
    if (interactiveMode.isStartingOrReady()) return
    log.debug("New single preview element focus: $instance")
    requestVisibilityAndNotificationsUpdate()
    interactiveMode = ComposePreviewManager.InteractiveMode.STARTING
    // We should call this before assigning newValue to instanceIdFilter
    val quickRefresh = shouldQuickRefresh()
    val peerPreviews = previewElementProvider.previewElements().count()
    previewElementProvider.instanceFilter = instance
    sceneComponentProvider.enabled = false
    val startUpStart = System.currentTimeMillis()
    forceRefresh(quickRefresh).invokeOnCompletion {
      surface.sceneManagers.forEach { it.resetInteractiveEventsCounter() }
      // Currently it will re-create classloader and will be slower that switch from static
      InteractivePreviewUsageTracker.getInstance(surface)
        .logStartupTime((System.currentTimeMillis() - startUpStart).toInt(), peerPreviews)
      fpsCounter.resetAndStart()
      ticker.start()
      delegateInteractionHandler.delegate = interactiveInteractionHandler
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

    log.debug("Stopping interactive")
    onInteractivePreviewStop()
    requestVisibilityAndNotificationsUpdate()
    onStaticPreviewStart()
    forceRefresh().invokeOnCompletion {
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
      if (
        (!animationInspection.get() && value != null) ||
          (animationInspection.get() && value == null)
      ) {
        if (value != null) {
          log.debug("Animation Preview open for preview: $value")
          ComposePreviewAnimationManager.onAnimationInspectorOpened()
          previewElementProvider.instanceFilter = value
          animationInspection.set(true)
          sceneComponentProvider.enabled = false

          // Open the animation inspection panel
          ComposePreviewAnimationManager.createAnimationInspectorPanel(
            surface,
            this,
            psiFilePointer
          ) {
            // Close this inspection panel, making all the necessary UI changes (e.g. changing
            // background and refreshing the preview) before
            // opening a new one.
            animationInspectionPreviewElementInstance = null
            updateAnimationPanelVisibility()
          }
          updateAnimationPanelVisibility()
          surface.background = INTERACTIVE_BACKGROUND_COLOR
        } else {
          onAnimationInspectionStop()
          onStaticPreviewStart()
        }
        forceRefresh().invokeOnCompletion {
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
    updateAnimationPanelVisibility()
    previewElementProvider.instanceFilter = null
  }

  private fun updateAnimationPanelVisibility() {
    if (!hasRenderedAtLeastOnce.get()) return
    composeWorkBench.bottomPanel =
      when {
        status().hasErrors || project.needsBuild -> null
        animationInspection.get() -> ComposePreviewAnimationManager.currentInspector?.component
        else -> null
      }
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

  override var isInspectionTooltipEnabled: Boolean = false

  override var isFilterEnabled: Boolean = false

  override var atfChecksEnabled: Boolean = StudioFlags.NELE_ATF_FOR_COMPOSE.get()

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

  private val composeWorkBench: ComposePreviewView =
    UIUtil.invokeAndWaitIfNeeded(
      Computable {
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
          this
        )
      }
    )

  @VisibleForTesting
  val staticPreviewInteractionHandler =
    ComposeNavigationInteractionHandler(
        composeWorkBench.mainSurface,
        NavigatingInteractionHandler(
          composeWorkBench.mainSurface,
          isSelectionEnabled = { StudioFlags.COMPOSE_PREVIEW_SELECTION.get() }
        )
      )
      .also { delegateInteractionHandler.delegate = it }
  private val interactiveInteractionHandler =
    LayoutlibInteractionHandler(composeWorkBench.mainSurface)

  @get:VisibleForTesting
  val surface: NlDesignSurface
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
   * Callback first time after the preview has loaded the initial state and it's ready to restore
   * any saved state.
   */
  private var onRestoreState: (() -> Unit)? = null

  private val ticker =
    ControllableTicker(
      {
        if (!RenderService.isBusy() && fpsCounter.getFps() <= fpsLimit) {
          fpsCounter.incrementFrameCounter()
          surface.sceneManagers.first().executeCallbacksAndRequestRender(null)
        }
      },
      Duration.ofMillis(5)
    )

  private val psiCodeFileChangeDetectorService =
    PsiCodeFileChangeDetectorService.getInstance(project)

  private val lifecycleManager =
    PreviewLifecycleManager(
      project,
      parentScope = this,
      onInitActivate = { activate(false) },
      onResumeActivate = { activate(true) },
      onDeactivate = {
        log.debug("onDeactivate")
        if (interactiveMode.isStartingOrReady()) {
          pauseInteractivePreview()
        }
        // The editor is scheduled to be deactivated, deactivate its issue model to avoid updating
        // publish the issue update event.
        surface.deactivateIssueModel()
      },
      onDelayedDeactivate = {
        stopInteractivePreview()
        log.debug("Delayed surface deactivation")
        surface.deactivate()
      }
    )

  init {
    Disposer.register(this, ticker)
  }

  override val component: JComponent
    get() = composeWorkBench.component

  private data class RefreshRequest(
    val type: Type,
    val requestSources: List<Throwable>,
    val completableDeferred: CompletableDeferred<Unit>? = null,
    val requestId: String = UUID.randomUUID().toString().substring(0, 5)
  ) {
    enum class Type {
      /**
       * Previews from the same Composable are not re-inflated. See
       * [ComposePreviewRepresentation.requestRefresh].
       */
      QUICK,

      /** Previews are inflated and rendered. */
      NORMAL,

      /**
       * Previews are not rendered or inflated. This mode is just used to trace a request to, for
       * example, ensure there are no pending requests.
       */
      TRACE
    }
  }

  private fun combineRefreshTypes(typeA: RefreshRequest.Type, typeB: RefreshRequest.Type) =
    when {
      // If they are the same type, return it.
      typeA == typeB -> typeA
      // If any of the types is TRACE, we want to retain the most complete rendering type. TRACE
      // does no work, QUICK does a bit
      // and NORMAL does all the work so we retain whichever is not TRACE.
      typeA == RefreshRequest.Type.TRACE -> typeB
      typeB == RefreshRequest.Type.TRACE -> typeA
      // Same as above, if one is QUICK and the other mode is normal, retain that one.
      typeA == RefreshRequest.Type.QUICK || typeB == RefreshRequest.Type.QUICK ->
        RefreshRequest.Type.NORMAL
      else -> throw IllegalStateException("Unexpected states $typeA and $typeB")
    }

  // region Lifecycle handling
  /**
   * Completes the initialization of the preview. This method is only called once after the first
   * [onActivate] happens.
   */
  private fun onInit() {
    log.debug("onInit")
    if (isDisposed.get()) {
      log.info("Preview was closed before the initialization completed.")
    }
    val psiFile = psiFilePointer.element
    requireNotNull(psiFile) { "PsiFile was disposed before the preview initialization completed." }

    setupBuildListener(
      project,
      object : BuildListener {
        /**
         * True if the project had files out of date before the build had triggered. This means we
         * will need a refresh after the build has completed.
         */
        private var hadOutOfDateFiles = false

        /**
         * True if the animation inspection was open at the beginning of the build. If open, we will
         * force a refresh after the build has completed since the animations preview panel
         * refreshes only when a refresh happens.
         */
        private var animationInspectionsEnabled = false

        override fun buildSucceeded() {
          log.debug("buildSucceeded")
          module?.let {
            // When the build completes successfully, we do not need the overlay until a
            // modifications has happened.
            ModuleClassLoaderOverlays.getInstance(it).invalidateOverlayPaths()
          }

          val file = psiFilePointer.element
          if (file == null) {
            log.debug("invalid PsiFile")
            return
          }

          // If Fast Preview is enabled, prefetch the daemon for the current configuration.
          if (module != null && FastPreviewManager.getInstance(project).isEnabled) {
            FastPreviewManager.getInstance(project).preStartDaemon(module)
          }

          afterBuildComplete(
            isSuccessful = true,
            needsRefresh = hadOutOfDateFiles || animationInspectionsEnabled
          )
        }

        override fun buildFailed() {
          log.debug("buildFailed")

          afterBuildComplete(isSuccessful = false, needsRefresh = false)

          // This ensures the animations panel is showed again after the build completes.
          if (animationInspectionsEnabled) requestRefresh()
        }

        override fun buildCleaned() {
          log.debug("buildCleaned")

          buildFailed()
        }

        override fun buildStarted() {
          log.debug("buildStarted")
          hadOutOfDateFiles = psiCodeFileChangeDetectorService.outOfDateFiles.isNotEmpty()
          animationInspectionsEnabled = animationInspection.get()

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
            // Notify on any Fast Preview compilation to ensure we refresh all the previews
            // correctly.
            afterBuildComplete(result == CompilationResult.Success, true)
          }
        }
      )
  }

  /**
   * Called after a project build has completed. If [needsRefresh] is true, the project contained
   * changes before the build that now require a preview refresh.
   */
  private fun afterBuildComplete(isSuccessful: Boolean, needsRefresh: Boolean) {
    if (isSuccessful && needsRefresh) {
      invalidate()
      requestRefresh()
    } else requestVisibilityAndNotificationsUpdate()
  }

  private fun afterBuildStarted() {
    // When building, invalidate the Animation Inspector, since the animations are now obsolete and
    // new ones will be subscribed once
    // build is complete and refresh is triggered.
    ComposePreviewAnimationManager.invalidate(psiFilePointer)
    requestVisibilityAndNotificationsUpdate()
  }

  /** Initializes the flows that will listen to different events and will call [requestRefresh]. */
  @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
  private fun CoroutineScope.initializeFlows() {
    with(this@initializeFlows) {
      // Launch all the listeners that are bound to the current activation.

      // Flow for Preview changes
      launch(workerThread) {
        previewElementFlowForFile(this@ComposePreviewRepresentation, psiFilePointer).collect {
          log.debug("PreviewElements updated $it")
          previewElementsFlow.value = it

          requestRefresh()
        }
      }

      // Flow to collate and process requestRefresh requests.
      launch(workerThread) {
        refreshFlow
          .conflateLatest { accumulator, value ->
            val completableDeferred =
              if (accumulator.completableDeferred == null && value.completableDeferred == null) null
              else
                wrapCompletableDeferredCollection(
                  listOfNotNull(accumulator.completableDeferred, value.completableDeferred)
                )

            // Create a new request source grouping the existing ones.
            RefreshRequest(
                // Quick refresh is only allowed if both the request in the buffer and the new one
                // had requested it.
                type = combineRefreshTypes(accumulator.type, value.type),
                requestSources = accumulator.requestSources + value.requestSources,
                completableDeferred = completableDeferred,
                // We keep the request id from the one in the buffer (the first to arrive).
                requestId = accumulator.requestId
              )
              .also { log.debug("${value.requestId} bundled into ${accumulator.requestId}") }
          }
          .distinctUntilChanged()
          .collect { refreshRequest ->
            log.debug("refreshFlow, request=$refreshRequest")
            refreshFlow
              .resetReplayCache() // Do not keep re-playing after we have received the element.

            val refreshJob = refresh(refreshRequest)
            // Link refreshJob and the completableDeferred so when one is cancelled the other one
            // is too.
            refreshRequest.completableDeferred?.let { completableDeferred ->
              refreshJob.invokeOnCompletion { throwable ->
                if (throwable != null) {
                  completableDeferred.completeExceptionally(throwable)
                } else completableDeferred.complete(Unit)
              }

              completableDeferred.invokeOnCompletion {
                // If the deferred is cancelled, cancel the refresh Job too
                if (it is CancellationException) refreshJob.cancel(it)
              }
            }
            refreshJob.join()
          }
      }

      // Flow to collate and process refreshNotificationsAndVisibilityFlow requests.
      launch(workerThread) {
        refreshNotificationsAndVisibilityFlow.conflate().collect {
          refreshNotificationsAndVisibilityFlow
            .resetReplayCache() // Do not keep re-playing after we have received the element.
          log.debug("refreshNotificationsAndVisibilityFlow, request=$it")
          composeWorkBench.updateVisibilityAndNotifications()
        }
      }

      launch(workerThread) {
        log.debug(
          "smartModeFlow setup status=${projectBuildStatusManager.status}, dumbMode=${DumbService.isDumb(project)}"
        )
        // Flow handling switch to smart mode.
        smartModeFlow(project, this@ComposePreviewRepresentation, log).collectLatest {
          log.debug(
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
        merge(
            psiFileChangeFlow(psiFilePointer.project, this@ComposePreviewRepresentation)
              // filter only for the file we care about
              .filter { it.language == KotlinLanguage.INSTANCE }
              .onEach {
                // Invalidate the preview to detect for changes in any annotation even in other
                // files as long as they are Kotlin.
                // We do not refresh at this point. If the change is in the preview file currently
                // opened, the change flow below will
                // detect the modification and trigger a refresh if needed.
                invalidate()
              }
              .debounce {
                // The debounce timer is smaller when running with Fast Preview so the changes are
                // more responsive to typing.
                if (FastPreviewManager.getInstance(project).isAvailable) 250L else 1000L
              },
            syntaxErrorFlow(project, this@ComposePreviewRepresentation, log, null)
              // Detect when problems disappear
              .filter { it is SyntaxErrorUpdate.Disappeared }
              .map { it.file }
              // We listen for problems disappearing so we know when we need to re-trigger a
              // Fast Preview compile.
              // We can safely ignore this events if:
              //  - No files are out of date or it's not a relevant file
              //  - Fast Preview is not active, we do not need to detect files having
              // problems removed.
              .filter {
                FastPreviewManager.getInstance(project).isAvailable &&
                  psiCodeFileChangeDetectorService.outOfDateFiles.isNotEmpty()
              }
              .filter { file ->
                // We only care about this in Kotlin files when they are out of date.
                psiCodeFileChangeDetectorService.outOfDateKtFiles
                  .map { it.virtualFile }
                  .any { it == file }
              }
          )
          .conflate()
          .collect {
            // If Fast Preview is enabled and there are Kotlin files out of date,
            // trigger a compilation. Otherwise, we will just refresh normally.
            if (
              FastPreviewManager.getInstance(project).isAvailable &&
                psiCodeFileChangeDetectorService.outOfDateKtFiles.isNotEmpty()
            ) {
              try {
                requestFastPreviewRefreshAndTrack()
                return@collect
              } catch (_: Throwable) {
                // Ignore any cancellation exceptions
              }
            }

            if (
              !PreviewPowerSaveManager.isInPowerSaveMode &&
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
    log.debug("onActivate")

    initializeFlows()

    if (!resume) {
      onInit()
    }

    surface.activate()

    if (interactiveMode.isStartingOrReady()) {
      resumeInteractivePreview()
    }

    val anyKtFilesOutOfDate = psiCodeFileChangeDetectorService.outOfDateFiles.any { it is KtFile }
    if (FastPreviewManager.getInstance(project).isAvailable && anyKtFilesOutOfDate) {
      // If any files are out of date, we force a refresh when re-activating. This allows us to
      // compile the changes if Fast Preview is enabled OR to refresh the preview elements in case
      // the annotations have changed.
      launch { requestFastPreviewRefreshAndTrack() }
    } else if (invalidated.get()) requestRefresh()
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
        val filePreviewElements = withContext(workerThread) { previewElementsFlow.value }
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
    isDisposed.set(true)
    if (interactiveMode == ComposePreviewManager.InteractiveMode.READY) {
      logInteractiveSessionMetrics()
    }
    animationInspectionPreviewElementInstance = null
  }

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
        UIUtil.invokeAndWaitIfNeeded(Computable { projectBuildStatusManager.isBuilding }))

    // If we are refreshing, we avoid spending time checking other conditions like errors or if the
    // preview
    // is out of date.
    val newStatus =
      ComposePreviewManager.Status(
        !isRefreshing && hasErrorsAndNeedsBuild(),
        !isRefreshing && hasSyntaxErrors(),
        !isRefreshing &&
          (projectBuildStatusManager.status is ProjectStatus.OutOfDate ||
            projectBuildStatusManager.status is ProjectStatus.NeedsBuild),
        !isRefreshing &&
          (projectBuildStatusManager.status as? ProjectStatus.OutOfDate)?.areResourcesOutOfDate
            ?: false,
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

  /**
   * Method called when the notifications of the [PreviewRepresentation] need to be updated. This is
   * called by the [ComposeNewPreviewNotificationProvider] when the editor needs to refresh the
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
      requestPrivateClassLoader = usePrivateClassLoader(),
      runAtfChecks = runAtfChecks()
    )

  private fun onAfterRender() {
    composeWorkBench.hasRendered = true
    hasRenderedAtLeastOnce.set(true)
    // Some Composables (e.g. Popup) delay their content placement and wrap them into a coroutine
    // controlled by the Compose clock. For that reason, we need to call executeCallbacksAsync()
    // once, to make sure the queued behaviors are triggered and displayed in static preview.
    surface.sceneManagers.forEach { it.executeCallbacksAsync() }
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
    if (log.isDebugEnabled) log.debug("doRefresh of ${filePreviewElements.count()} elements.")
    val psiFile =
      runReadAction {
        val element = psiFilePointer.element

        return@runReadAction if (element == null || !element.isValid) {
          log.warn("doRefresh with invalid PsiFile")
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

    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    val showingPreviewElements =
      surface.updatePreviewsAndRefresh(
        !quickRefresh,
        previewElementProvider,
        log,
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
      log.warn("Some preview elements have failed")
    }
  }

  internal fun requestRefresh(
    quickRefresh: Boolean = false,
    completableDeferred: CompletableDeferred<Unit>? = null
  ) {
    if (isDisposed.get()) {
      completableDeferred?.completeExceptionally(IllegalStateException("Already disposed"))
      return
    }

    val refreshRequest =
      RefreshRequest(
        type = if (quickRefresh) RefreshRequest.Type.QUICK else RefreshRequest.Type.NORMAL,
        requestSources = listOf(Throwable()),
        completableDeferred = completableDeferred
      )
    launch(workerThread) { refreshFlow.emit(refreshRequest) }
  }

  private fun requestVisibilityAndNotificationsUpdate() {
    launch(workerThread) { refreshNotificationsAndVisibilityFlow.emit(Unit) }
    launch(uiThread) { updateAnimationPanelVisibility() }
  }

  /**
   * Requests a refresh the preview surfaces. This will retrieve all the Preview annotations and
   * render those elements. The refresh will only happen if the Preview elements have changed from
   * the last render.
   */
  private fun refresh(refreshRequest: RefreshRequest): Job {
    val requestLogger = LoggerWithFixedInfo(log, mapOf("requestId" to refreshRequest.requestId))
    requestLogger.debug(
      "Refresh triggered editor=${psiFilePointer.containingFile?.name}. quickRefresh: ${refreshRequest.type}"
    )
    val refreshTriggers: List<Throwable> = refreshRequest.requestSources

    if (refreshRequest.type == RefreshRequest.Type.TRACE) {
      refreshTriggers.forEach { requestLogger.debug("Refresh trace, no work being done", it) }
      return CompletableDeferred(Unit)
    }

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
    if (!Disposer.tryRegister(this, refreshProgressIndicator)) {
      refreshProgressIndicator.processFinish()
      return CompletableDeferred<Unit>().also {
        it.completeExceptionally(IllegalStateException("Already disposed"))
      }
    }
    // This is not launched in the activation scope to avoid cancelling the refresh mid-way when the
    // user changes tabs.
    val refreshJob =
      launchWithProgress(refreshProgressIndicator, uiThread) {
        refreshTriggers.forEach {
          requestLogger.debug("Refresh triggered (inside launchWithProgress scope)", it)
        }

        if (DumbService.isDumb(project)) {
          requestLogger.debug("Project is in dumb mode, not able to refresh")
          return@launchWithProgress
        }

        if (projectBuildStatusManager.status == ProjectStatus.NeedsBuild) {
          // Project needs to be built before being able to refresh.
          requestLogger.debug("Project has not build, not able to refresh")
          return@launchWithProgress
        }

        if (Bridge.hasNativeCrash()) {
          composeWorkBench.onLayoutlibNativeCrash { requestRefresh() }
          return@launchWithProgress
        }

        requestVisibilityAndNotificationsUpdate()
        refreshCallsCount.incrementAndGet()

        try {
          refreshProgressIndicator.text = message("refresh.progress.indicator.finding.previews")
          val filePreviewElements =
            withContext(workerThread) {
              previewElementsFlow.value.toList().sortByDisplayAndSourcePosition()
            }

          val needsFullRefresh =
            invalidated.getAndSet(false) || renderedElements != filePreviewElements

          composeWorkBench.hasContent = filePreviewElements.isNotEmpty()
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
              refreshRequest.type == RefreshRequest.Type.QUICK,
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
      log.debug("Completed")
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
      PREVIEW_LAYOUT_MANAGER_OPTIONS.find {
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

      PREVIEW_LAYOUT_MANAGER_OPTIONS.find { it.displayName == previewLayoutName }
        ?.let {
          (surface.sceneViewLayoutManager as LayoutManagerSwitcher).setLayoutManager(
            it.layoutManager
          )
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

  /** Whether to run ATF checks on the preview. Never do it for interactive preview. */
  private fun runAtfChecks() = atfChecksEnabled && !interactiveMode.isStartingOrReady()

  override fun invalidate() {
    invalidated.set(true)
  }

  /** Returns if this representation has been invalidated. Only for use in tests. */
  @TestOnly internal fun isInvalid(): Boolean = invalidated.get()

  /**
   * Same as [requestRefresh] but does a previous [invalidate] to ensure the preview definitions are
   * re-loaded from the files.
   *
   * The return [Deferred] will complete when the refresh finalizes.
   */
  private fun forceRefresh(quickRefresh: Boolean = false): Deferred<Unit> {
    val completableDeferred = CompletableDeferred<Unit>()
    invalidate()
    requestRefresh(quickRefresh, completableDeferred)

    return completableDeferred
  }

  override fun registerShortcuts(applicableTo: JComponent) {
    psiFilePointer.element?.let {
      BuildAndRefresh { it }
        .registerCustomShortcutSet(getBuildAndRefreshShortcut(), applicableTo, this)
    }
  }

  /** We will only do quick refresh if there is a single preview. */
  private fun shouldQuickRefresh() = renderedElements.count() == 1

  /**
   * Waits for any on-going or pending refreshes to complete. It optionally accepts a runnable that
   * can be executed before the next render is executed.
   */
  suspend fun waitForAnyPendingRefresh(runnable: () -> Unit = {}) {
    if (isDisposed.get()) {
      return
    }

    val completableDeferred = CompletableDeferred<Unit>()
    completableDeferred.invokeOnCompletion { if (it == null) runnable() }
    val refreshRequest =
      RefreshRequest(
        type = RefreshRequest.Type.TRACE,
        requestSources = listOf(Throwable()),
        completableDeferred = completableDeferred
      )
    launch(workerThread) { refreshFlow.emit(refreshRequest) }
    completableDeferred.join()
  }

  private suspend fun requestFastPreviewRefreshAndTrack(): CompilationResult {
    val previewFile =
      psiFilePointer.element
        ?: return CompilationResult.RequestException(
          IllegalStateException("Preview File is no valid")
        )
    val previewFileModule =
      runReadAction { previewFile.module }
        ?: return CompilationResult.RequestException(
          IllegalStateException("Preview File does not have a valid module")
        )
    val outOfDateFiles =
      psiCodeFileChangeDetectorService.outOfDateFiles
        .filterIsInstance<KtFile>()
        .filter { modifiedFile ->
          if (modifiedFile.isEquivalentTo(previewFile)) return@filter true
          val modifiedFileModule = runReadAction { modifiedFile.module } ?: return@filter false

          // Keep the file if the file is from this module or from a module we depend on
          modifiedFileModule == previewFileModule ||
            ModuleManager.getInstance(project)
              .isModuleDependent(previewFileModule, modifiedFileModule)
        }
        .toSet()

    // Nothing to compile
    if (outOfDateFiles.isEmpty()) return CompilationResult.Success

    return requestFastPreviewRefreshAndTrack(
      this@ComposePreviewRepresentation,
      previewFileModule,
      outOfDateFiles,
      status(),
      fastPreviewCompilationLauncher
    ) { outputAbsolutePath ->
      waitForAnyPendingRefresh {
        // Wait for any pending refreshes before updating the overlay
        ModuleClassLoaderOverlays.getInstance(previewFileModule)
          .pushOverlayPath(File(outputAbsolutePath).toPath())
      }
      forceRefresh().join()
    }
  }

  override fun requestFastPreviewRefreshAsync(): Deferred<CompilationResult> =
    lifecycleManager.executeIfActive { async { requestFastPreviewRefreshAndTrack() } }
      ?: CompletableDeferred(CompilationResult.CompilationAborted())

  /** Waits for any preview to be populated. */
  @TestOnly
  suspend fun waitForAnyPreviewToBeAvailable() {
    previewElementsFlow.filter { it.isNotEmpty() }.take(1).collect()
  }
}
