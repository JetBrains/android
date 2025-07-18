/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.preview.representation

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NlModelUpdaterInterface
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.launchWithProgress
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.editors.build.PsiCodeFileOutOfDateStatusReporter
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.editors.build.RenderingBuildStatusManager
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LoggerWithFixedInfo
import com.android.tools.idea.preview.CommonPreviewRefreshRequest
import com.android.tools.idea.preview.CommonPreviewRefreshType
import com.android.tools.idea.preview.DefaultRenderQualityManager
import com.android.tools.idea.preview.DefaultRenderQualityPolicy
import com.android.tools.idea.preview.DelegatingPreviewElementModelAdapter
import com.android.tools.idea.preview.NavigatingInteractionHandler
import com.android.tools.idea.preview.PreviewBuildListenersManager
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewInvalidationManager
import com.android.tools.idea.preview.PreviewPreloadClasses.INTERACTIVE_CLASSES_TO_PRELOAD
import com.android.tools.idea.preview.PreviewRefreshManager
import com.android.tools.idea.preview.PsiPreviewElementInstance
import com.android.tools.idea.preview.RenderQualityManager
import com.android.tools.idea.preview.RenderQualityPolicy
import com.android.tools.idea.preview.SimpleRenderQualityManager
import com.android.tools.idea.preview.analytics.InteractivePreviewUsageTracker
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.animation.AnimationPreview
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.preview.essentials.essentialsModeFlow
import com.android.tools.idea.preview.fast.CommonFastPreviewSurface
import com.android.tools.idea.preview.fast.FastPreviewSurface
import com.android.tools.idea.preview.find.MemoizedPreviewElementProvider
import com.android.tools.idea.preview.find.PreviewElementProvider
import com.android.tools.idea.preview.flow.CommonPreviewFlowManager
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.focus.CommonFocusEssentialsModeManager
import com.android.tools.idea.preview.focus.FocusMode
import com.android.tools.idea.preview.getDefaultPreviewQuality
import com.android.tools.idea.preview.groups.PreviewGroupManager
import com.android.tools.idea.preview.interactive.InteractivePreviewManager
import com.android.tools.idea.preview.interactive.fpsLimitFlow
import com.android.tools.idea.preview.lifecycle.PreviewLifecycleManager
import com.android.tools.idea.preview.modes.CommonPreviewModeManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewView
import com.android.tools.idea.preview.navigation.DefaultNavigationHandler
import com.android.tools.idea.preview.pagination.PreviewPaginationManager
import com.android.tools.idea.preview.refreshExistingPreviewElements
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.preview.viewmodels.CommonPreviewViewModel
import com.android.tools.idea.preview.views.CommonNlDesignSurfacePreviewView
import com.android.tools.idea.projectsystem.needsBuild
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.rendering.setupBuildListener
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.surface.PreviewNavigatableWrapper
import com.android.tools.idea.uibuilder.surface.defaultSceneManagerProvider
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.android.tools.preview.PreviewElement
import com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtFile

private val modelUpdater: NlModelUpdaterInterface = DefaultModelUpdater()
val PREVIEW_ELEMENT_INSTANCE = DataKey.create<PsiPreviewElementInstance>("PreviewElement")

/**
 * A generic [PreviewElement] [PreviewRepresentation], that can be configured and adapted to the
 * needs of a given preview tool by the constructor parameters.
 *
 * @param adapterViewFqcn the fully qualified name of the view adapter associated with the previews.
 * @param psiFile the file containing the code to preview.
 * @param previewProviderConstructor the function to get a [PreviewElementProvider] to be used for
 *   finding the previews.
 * @param previewElementModelAdapterDelegate the [PreviewElementModelAdapter] to be used when
 *   rendering previews.
 * @param viewConstructor the function to get a [CommonNlDesignSurfacePreviewView] to be used for
 *   displaying the previews.
 * @param viewModelConstructor the function to get a [CommonPreviewViewModel] to be used for
 *   tracking big part of the state of the previews.
 * @param configureDesignSurface the function to configure the [NlDesignSurface] that is used for
 *   displaying the previews.
 * @param renderingTopic the [RenderingTopic] under which the preview renderings will be executed.
 * @param useCustomInflater a configuration to apply when rendering the previews.
 * @param createRefreshEventBuilder the function to get a [PreviewRefreshEventBuilder] to be used
 *   for tracking refresh metrics.
 * @param onAfterRender the function to be called after preview rendering completed for each scene.
 */
open class CommonPreviewRepresentation<T : PsiPreviewElementInstance>(
  adapterViewFqcn: String,
  psiFile: PsiFile,
  previewProviderConstructor: (SmartPsiElementPointer<PsiFile>) -> PreviewElementProvider<T>,
  previewElementModelAdapterDelegate: PreviewElementModelAdapter<T, NlModel>,
  viewConstructor:
    (
      project: Project, surfaceBuilder: NlSurfaceBuilder, parentDisposable: Disposable,
    ) -> CommonNlDesignSurfacePreviewView,
  viewModelConstructor:
    (
      previewView: PreviewView,
      renderingBuildStatusManager: RenderingBuildStatusManager,
      refreshManager: PreviewRefreshManager,
      project: Project,
      psiFilePointer: SmartPsiElementPointer<PsiFile>,
      hasRenderErrors: () -> Boolean,
    ) -> CommonPreviewViewModel,
  configureDesignSurface: NlSurfaceBuilder.(NavigationHandler) -> Unit,
  renderingTopic: RenderingTopic,
  useCustomInflater: Boolean = true,
  private val createRefreshEventBuilder: (NlDesignSurface) -> PreviewRefreshEventBuilder? = {
    null
  },
  private val onAfterRender: (LayoutlibSceneManager) -> Unit = {},
) :
  PreviewRepresentation,
  AndroidCoroutinesAware,
  UserDataHolderEx by UserDataHolderBase(),
  PreviewModeManager,
  FastPreviewSurface,
  PreviewInvalidationManager {

  private val LOG = Logger.getInstance(CommonPreviewRepresentation::class.java)
  protected val project = psiFile.project
  private val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }
  private val buildTargetReference =
    BuildTargetReference.from(psiFile) ?: error("Cannot obtain build reference to: $psiFile")

  private val renderingBuildStatusManager = RenderingBuildStatusManager.create(this, psiFile)

  @TestOnly internal fun getProjectBuildStatusForTest() = renderingBuildStatusManager.status

  private val lifecycleManager =
    PreviewLifecycleManager(
      project,
      parentScope = this,
      onInitActivate = { activate(isResuming = false) },
      onResumeActivate = { activate(isResuming = true) },
      onDeactivate = {
        LOG.debug("onDeactivate")
        qualityPolicy.deactivate()
        allowQualityChangeIfInactive.set(true)
        requestRefresh(type = CommonPreviewRefreshType.QUALITY)
        if (mode.value is PreviewMode.Interactive) {
          interactiveManager.pause()
        }
        surface.deactivateIssueModel()
      },
      onDelayedDeactivate = {
        LOG.debug("Delayed surface deactivation")
        surface.deactivate()
      },
    )

  private val delegateInteractionHandler = DelegateInteractionHandler()

  @VisibleForTesting
  val navigationHandler =
    DefaultNavigationHandler(
        componentNavigationDelegate = { sceneView, _, _, _, _, _ ->
          val model = sceneView.sceneManager.model
          val previewElement = model.dataProvider?.getData(PREVIEW_ELEMENT_INSTANCE)
          val navigatableElement =
            previewElement?.previewElementDefinition?.element?.navigationElement
              as? NavigatablePsiElement
          if (navigatableElement == null) emptyList<PreviewNavigatableWrapper>()
          listOf(PreviewNavigatableWrapper("", navigatableElement!!))
        },
        componentNavigationDelegateTwo = { sceneView, _, _ -> listOf() },
      )
      .apply { Disposer.register(this@CommonPreviewRepresentation, this) }

  @VisibleForTesting
  val previewView = invokeAndWaitIfNeeded {
    viewConstructor(
        project,
        NlSurfaceBuilder.builder(project, this) { surface, model ->
            defaultSceneManagerProvider(surface, model).apply {
              sceneRenderConfiguration.let { config ->
                config.useCustomInflater = useCustomInflater
                config.useShrinkRendering = true
                config.renderingTopic = renderingTopic
              }
              listenResourceChange = false // don't re-render on resource changes
              updateAndRenderWhenActivated = false // don't re-render on activation
            }
          }
          .setInteractionHandlerProvider {
            delegateInteractionHandler.apply {
              delegate = NavigatingInteractionHandler(it, navigationHandler)
            }
          }
          .waitForRenderBeforeRestoringZoom(true)
          .setDelegateDataProvider {
            when (it) {
              PREVIEW_VIEW_MODEL_STATUS.name -> previewViewModel
              PreviewModeManager.KEY.name -> this@CommonPreviewRepresentation
              PreviewPaginationManager.KEY.name -> previewFlowManager.previewFlowPaginator
              PreviewGroupManager.KEY.name,
              PreviewFlowManager.KEY.name -> previewFlowManager
              FastPreviewSurface.KEY.name -> this@CommonPreviewRepresentation
              PreviewInvalidationManager.KEY.name -> this@CommonPreviewRepresentation
              else -> null
            }
          }
          .apply { configureDesignSurface(navigationHandler) },
        this,
      )
      .also {
        it.mainSurface.analyticsManager.setEditorFileTypeWithoutTracking(
          psiFilePointer.virtualFile,
          project,
        )
      }
  }

  protected val surface: NlDesignSurface
    get() = previewView.mainSurface

  /** Used for allowing to decrease qualities of previews after deactivating this representation */
  private val allowQualityChangeIfInactive = AtomicBoolean(false)

  /** [RenderQualityPolicy] used to configure the [qualityManager] */
  private val qualityPolicy = DefaultRenderQualityPolicy {
    surface.zoomController.screenScalingFactor
  }

  /**
   * Used for defining the target render quality of each preview and detecting the need of changing
   * the quality the previews. See [RenderQualityManager] for more details.
   */
  private val qualityManager: RenderQualityManager =
    if (StudioFlags.PREVIEW_RENDER_QUALITY.get())
      DefaultRenderQualityManager(surface, qualityPolicy) {
        requestRefresh(type = CommonPreviewRefreshType.QUALITY)
      }
    else SimpleRenderQualityManager { getDefaultPreviewQuality() }

  /** Whether the preview needs a full refresh or not. */
  private val invalidated = AtomicBoolean(true)

  @TestOnly internal fun isInvalidatedForTest() = invalidated.get()

  private val refreshManager = PreviewRefreshManager.getInstance(renderingTopic)

  @VisibleForTesting
  val previewViewModel: CommonPreviewViewModel =
    viewModelConstructor(
      previewView,
      renderingBuildStatusManager,
      refreshManager,
      project,
      psiFilePointer,
    ) {
      surface.sceneManagers.any { it.renderResult.isErrorResult(adapterViewFqcn) }
    }

  private val previewBuildListenersManager =
    PreviewBuildListenersManager(
      isFastPreviewSupported = isFastPreviewAvailable(),
      ::invalidate,
      ::requestRefresh,
      ::updateAnimationPanelVisibility,
    )

  @TestOnly
  internal fun hasBuildListenerSetupFinishedForTest() =
    previewBuildListenersManager.buildListenerSetupFinished

  @TestOnly
  internal fun hasFlowInitializationFinishedForTest() =
    previewFlowManager.toRenderPreviewElementsFlow.value != FlowableCollection.Uninitialized

  private val previewFreshnessTracker =
    CodeOutOfDateTracker.create(buildTargetReference, this) { requestRefresh() }

  private val myPsiCodeFileOutOfDateStatusReporter =
    PsiCodeFileOutOfDateStatusReporter.getInstance(project)

  private val previewFlowManager = CommonPreviewFlowManager<T>()

  private val previewElementProvider =
    MemoizedPreviewElementProvider(
      previewProviderConstructor(psiFilePointer),
      previewFreshnessTracker,
    )

  private val previewElementModelAdapter =
    object : DelegatingPreviewElementModelAdapter<T, NlModel>(previewElementModelAdapterDelegate) {
      override fun createDataProvider(previewElement: T): NlDataProvider {
        val delegatedProvider =
          previewElementModelAdapterDelegate.createDataProvider(previewElement)
        val keys =
          mutableSetOf(
            PREVIEW_ELEMENT_INSTANCE,
            CommonDataKeys.PROJECT,
            PreviewModeManager.KEY,
            PreviewGroupManager.KEY,
            PreviewPaginationManager.KEY,
            PreviewFlowManager.KEY,
            FastPreviewSurface.KEY,
            PreviewInvalidationManager.KEY,
          )
        delegatedProvider?.let { keys.addAll(it.keys) }
        return object : NlDataProvider(keys) {
          override fun getData(dataId: String): Any? =
            when (dataId) {
              PREVIEW_ELEMENT_INSTANCE.name -> previewElement
              CommonDataKeys.PROJECT.name -> project
              PreviewModeManager.KEY.name -> this@CommonPreviewRepresentation
              PreviewPaginationManager.KEY.name -> previewFlowManager.previewFlowPaginator
              PreviewGroupManager.KEY.name -> previewFlowManager
              PreviewFlowManager.KEY.name -> previewFlowManager
              FastPreviewSurface.KEY.name -> this@CommonPreviewRepresentation
              PreviewInvalidationManager.KEY.name -> this@CommonPreviewRepresentation
              else -> delegatedProvider?.getData(dataId)
            }
        }
      }
    }

  private val previewModeManager = CommonPreviewModeManager()

  private val fpsLimitFlow =
    essentialsModeFlow(project, this).fpsLimitFlow(this, standardFpsLimit = 30)

  @VisibleForTesting
  val interactiveManager =
    InteractivePreviewManager(
        surface,
        fpsLimitFlow.value,
        { surface.sceneManagers },
        { InteractivePreviewUsageTracker.getInstance(surface) },
        delegateInteractionHandler,
      )
      .also { Disposer.register(this@CommonPreviewRepresentation, it) }

  private val focusEssentialsModeManager =
    CommonFocusEssentialsModeManager(
        project = psiFile.project,
        lifecycleManager = lifecycleManager,
        previewFlowManager = previewFlowManager,
        previewModeManager = previewModeManager,
        onUpdatedFromPreviewEssentialsMode = {},
        requestRefresh = ::requestRefresh,
      )
      .also { Disposer.register(this@CommonPreviewRepresentation, it) }

  private val delegateFastPreviewSurface =
    CommonFastPreviewSurface(
      parentDisposable = this,
      lifecycleManager = lifecycleManager,
      psiFilePointer = psiFilePointer,
      previewStatusProvider = ::previewViewModel,
      delegateRefresh = ::invalidateAndRefresh,
    )

  private val stateManager =
    CommonPreviewStateManager(
      surfaceProvider = { surface },
      currentGroupFilterProvider = { previewFlowManager.getCurrentFilterAsGroup() },
      previewFlowManager = previewFlowManager,
      previewModeManager = previewModeManager,
    )

  @VisibleForTesting
  var currentAnimationPreview: AnimationPreview<*>? = null
    private set

  override val component: JComponent
    get() = previewView.component

  override val preferredInitialVisibility: PreferredVisibility? = null

  override val caretNavigationHandler =
    PreviewRepresentation.CaretNavigationHandler.NoopCaretNavigationHandler()

  override val mode = previewModeManager.mode

  override fun dispose() {
    if (mode.value is PreviewMode.Interactive) {
      interactiveManager.stop()
    }
  }

  override fun onActivate() = lifecycleManager.activate()

  override fun onDeactivate() = lifecycleManager.deactivate()

  /**
   * Same as [onDeactivate] but forces an immediate deactivation without any delay. Only for
   * testing.
   */
  @TestOnly internal fun onDeactivateImmediately() = lifecycleManager.deactivateImmediately()

  override fun restorePrevious() = previewModeManager.restorePrevious()

  override fun setMode(mode: PreviewMode) {
    previewModeManager.setMode(mode)
  }

  override fun requestFastPreviewRefreshAsync() =
    delegateFastPreviewSurface.requestFastPreviewRefreshAsync()

  override fun invalidate() {
    invalidated.set(true)
  }

  override fun getState() = stateManager.getState()

  override fun setState(state: PreviewRepresentationState) = stateManager.setState(state)

  private fun onInit() {
    LOG.debug("onInit")
    if (Disposer.isDisposed(this)) {
      LOG.info("Preview was closed before the initialization completed.")
    }
    val psiFile = psiFilePointer.element
    requireNotNull(psiFile) { "PsiFile was disposed before the preview initialization completed." }

    setupBuildListener(buildTargetReference, previewViewModel, this)
    previewBuildListenersManager.setupPreviewBuildListeners(disposable = this, psiFilePointer)

    project.runWhenSmartAndSyncedOnEdt(this, { onEnterSmartMode() })
  }

  private suspend fun doRefreshSync(
    filePreviewElements: List<T>,
    progressIndicator: ProgressIndicator,
    refreshEventBuilder: PreviewRefreshEventBuilder?,
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
      } ?: return

    stateManager.restoreState()

    val showingPreviewElements =
      surface.updatePreviewsAndRefresh(
        reinflate = true,
        filePreviewElements,
        LOG,
        psiFile,
        this,
        progressIndicator,
        previewElementModelAdapter,
        modelUpdater,
        navigationHandler,
        { _, layoutLibSceneManager -> configureLayoutlibSceneManager(layoutLibSceneManager) },
        refreshEventBuilder,
      )

    onAfterRender()

    if (showingPreviewElements.size >= filePreviewElements.size) {
      previewFlowManager.updateRenderedPreviews(filePreviewElements)
    } else {
      // Some preview elements did not result in model creations. This could be because of failed
      // PreviewElements instantiation.
      // TODO(b/160300892): Add better error handling for failed instantiations.
      LOG.warn("Some preview elements have failed")
    }
  }

  private fun createRefreshJob(
    request: CommonPreviewRefreshRequest,
    refreshProgressIndicator: BackgroundableProcessIndicator,
  ) =
    launchWithProgress(refreshProgressIndicator, workerThread) {
      val requestLogger = LoggerWithFixedInfo(LOG, mapOf("requestId" to request.requestId))
      val invalidateIfCancelled = AtomicBoolean(false)

      if (DumbService.isDumb(project)) {
        return@launchWithProgress
      }

      if (renderingBuildStatusManager.status == RenderingBuildStatus.NeedsBuild) {
        // Project needs to be built before being able to refresh.
        requestLogger.debug("Project has not build, not able to refresh")
        return@launchWithProgress
      }

      if (previewViewModel.checkForNativeCrash { requestRefresh() }) {
        return@launchWithProgress
      }

      previewViewModel.refreshStarted()

      try {
        refreshProgressIndicator.text = message("refresh.progress.indicator.finding.previews")
        val filePreviewElements =
          previewFlowManager.toRenderPreviewElementsFlow.value.asCollection().toList()

        val needsFullRefresh =
          request.refreshType != CommonPreviewRefreshType.QUALITY && invalidated.getAndSet(false)

        invalidateIfCancelled.set(needsFullRefresh)

        previewViewModel.setHasPreviews(filePreviewElements.isNotEmpty())
        if (!needsFullRefresh) {
          requestLogger.debug(
            "No updates on the PreviewElements, just refreshing the existing ones"
          )
          // In this case, there are no new previews. We need to make sure that the surface is still
          // correctly configured and that we are showing the right size for components. For
          // example, if the user switches on/off decorations, that will not generate/remove new
          // PreviewElements but will change the surface settings.
          refreshProgressIndicator.text =
            message("refresh.progress.indicator.reusing.existing.previews")
          surface.refreshExistingPreviewElements(
            refreshProgressIndicator,
            previewElementModelAdapter::modelToElement,
            { _, layoutLibSceneManager -> configureLayoutlibSceneManager(layoutLibSceneManager) },
            refreshFilter = { sceneManager ->
              // For quality change requests, only re-render those that need a quality change.
              // For other types of requests, re-render every preview.
              request.refreshType != CommonPreviewRefreshType.QUALITY ||
                qualityManager.needsQualityChange(sceneManager)
            },
            refreshOrder = { sceneManager ->
              // decreasing quality before increasing
              qualityManager
                .getTargetQuality(sceneManager)
                .compareTo(sceneManager.lastRenderQuality)
            },
            refreshEventBuilder = request.refreshEventBuilder,
          )
        } else {
          refreshProgressIndicator.text =
            message("refresh.progress.indicator.refreshing.all.previews")
          previewViewModel.beforePreviewsRefreshed()
          doRefreshSync(filePreviewElements, refreshProgressIndicator, request.refreshEventBuilder)
        }
      } catch (t: Throwable) {
        if (t is CancellationException) {
          // We want to make sure the next refresh invalidates if this invalidation didn't happen
          // Careful though, this needs to be performed here and not in the invokeOnCompletion of
          // the job that is returned as the invokeOnComplete is run concurrently with the next
          // refresh request and there can be race conditions.
          if (invalidateIfCancelled.get()) {
            invalidate()
          }
          // Make sure to propagate cancellations
          throw t
        } else requestLogger.warn("Request failed", t)
      } finally {
        previewViewModel.refreshFinished()
      }
    }

  private fun createRefreshJob(request: CommonPreviewRefreshRequest): Job {
    if (project.isDisposed) {
      return CompletableDeferred<Unit>().also {
        it.completeExceptionally(IllegalStateException("Already disposed"))
      }
    }

    // Make sure not to start refreshes when deactivated, unless it is the first quality refresh
    // that happens since deactivation. This is expected to happen to decrease the quality of its
    // previews when deactivating.
    val shouldProcessRequest =
      when {
        lifecycleManager.isActive() -> true
        request.refreshType != CommonPreviewRefreshType.QUALITY -> false
        else -> allowQualityChangeIfInactive.getAndSet(false)
      }
    if (!shouldProcessRequest) {
      return CompletableDeferred(Unit)
    }

    // Return early when quality refresh won't actually refresh anything
    if (
      request.refreshType == CommonPreviewRefreshType.QUALITY &&
        !qualityManager.needsQualityChange(surface)
    ) {
      return CompletableDeferred(Unit)
    }

    val startTime = System.nanoTime()
    val refreshProgressIndicator =
      BackgroundableProcessIndicator(
        project,
        message("refresh.progress.indicator.title"),
        "",
        "",
        true,
      )
    if (!Disposer.tryRegister(this, refreshProgressIndicator)) {
      refreshProgressIndicator.processFinish()
      return CompletableDeferred<Unit>().also {
        it.completeExceptionally(IllegalStateException("Already disposed"))
      }
    }

    val refreshJob = createRefreshJob(request, refreshProgressIndicator)
    refreshJob.invokeOnCompletion {
      LOG.debug("Completed")
      // Progress indicators must be disposed in the ui thread
      launch(Dispatchers.Main) { Disposer.dispose(refreshProgressIndicator) }
      previewViewModel.refreshCompleted(it is CancellationException, System.nanoTime() - startTime)
    }
    return refreshJob
  }

  private fun requestRefresh(
    type: CommonPreviewRefreshType = CommonPreviewRefreshType.NORMAL,
    onRefreshCompleted: CompletableDeferred<Unit>? = null,
  ) {
    // Make sure not to allow quality change refreshes when the flag is disabled
    if (type == CommonPreviewRefreshType.QUALITY && !StudioFlags.PREVIEW_RENDER_QUALITY.get()) {
      onRefreshCompleted?.completeExceptionally(IllegalStateException("Not enabled"))
      return
    }
    // Make sure not to request refreshes when deactivated, unless it is an allowed quality refresh,
    // which is expected to happen to decrease the quality of the previews when deactivating.
    val shouldEnqueueRequest =
      when {
        lifecycleManager.isActive() -> true
        type != CommonPreviewRefreshType.QUALITY -> false
        else -> allowQualityChangeIfInactive.get()
      }
    if (!shouldEnqueueRequest) {
      onRefreshCompleted?.completeExceptionally(IllegalStateException("Not active"))
      return
    }

    val request =
      CommonPreviewRefreshRequest(
        clientId = this@CommonPreviewRepresentation.hashCode().toString(),
        refreshType = type,
        delegateRefresh = { createRefreshJob(it as CommonPreviewRefreshRequest) },
        onRefreshCompleted = onRefreshCompleted,
        refreshEventBuilder = createRefreshEventBuilder(surface),
      )
    refreshManager.requestRefresh(request)
  }

  private suspend fun invalidateAndRefresh() {
    CompletableDeferred<Unit>().let {
      invalidate()
      requestRefresh(onRefreshCompleted = it)
      it.join()
    }
  }

  private fun onAfterRender() {
    try {
      surface.sceneManagers.forEach { onAfterRender(it) }
    } finally {
      // this should be run even if an onAfterRender throws an exception
      previewViewModel.afterPreviewsRefreshed()
      updateAnimationPanelVisibility()
    }
  }

  private fun configureLayoutlibSceneManager(layoutlibSceneManager: LayoutlibSceneManager) =
    layoutlibSceneManager.apply {
      sceneRenderConfiguration.let { config ->
        // When the cache successful render image is enabled, the scene manager will retain the last
        // valid image even if subsequent renders fail. But do not cache in interactive mode as it
        // does not help, and it would make unnecessary copies of the bitmap.
        config.cacheSuccessfulRenderImage = mode.value !is PreviewMode.Interactive
        config.classesToPreload =
          if (mode.value is PreviewMode.Interactive) INTERACTIVE_CLASSES_TO_PRELOAD else emptyList()
        config.usePrivateClassLoader = mode.value is PreviewMode.Interactive
        config.quality = qualityManager.getTargetQuality(this@apply)
      }
    }

  private fun CoroutineScope.activate(isResuming: Boolean) {
    qualityPolicy.activate()
    requestRefresh(type = CommonPreviewRefreshType.QUALITY)

    initializeFlows()
    if (!isResuming) {
      onInit()
    }

    surface.activate()

    if (mode.value is PreviewMode.Interactive) {
      interactiveManager.resume()
    }

    // At this point everything have been initialized or re-activated. Now we need to check whether
    // a full refresh is needed or could be avoided, and all considered scenarios are listed below:
    // - First activation: initial state is invalidated=true, so a full refresh will happen
    // - Re-activation and build or fast compile happened while deactivated: build listeners should
    //   have invalidated this, and then a full refresh will happen
    // - Re-activation and any kotlin file out of date: fast compile will happen if fast preview is
    //   enabled, and then a full refresh will happen.
    // - Re-activation and any non-kotlin file out of date: manual invalidation done here and then
    //   a full refresh will happen
    if (myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles.isNotEmpty()) invalidate()
    val anyKtFilesOutOfDate =
      myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles.any { it is KtFile }
    if (isFastPreviewAvailable() && anyKtFilesOutOfDate) {
      // If any files are out of date, we force a refresh when re-activating. This allows us to
      // compile the changes if Fast Preview is enabled OR to refresh the preview elements in case
      // the annotations have changed.
      launch { delegateFastPreviewSurface.requestFastPreviewRefreshSync() }
    } else if (invalidated.get()) requestRefresh()

    // Focus mode should be updated only if Preview is active / in foreground.
    // It will help to avoid enabling focus mode while Preview is inactive, as it will also save
    // this state for later to restore.
    focusEssentialsModeManager.activate()
  }

  private fun CoroutineScope.initializeFlows() {
    with(this@initializeFlows) {
      // Initialize flows
      previewFlowManager.run {
        initializeFlows(
          disposable = this@CommonPreviewRepresentation,
          previewModeManager = previewModeManager,
          psiCodeFileOutOfDateStatusReporter = myPsiCodeFileOutOfDateStatusReporter,
          psiFilePointer = psiFilePointer,
          invalidate = ::invalidate,
          requestRefresh = ::requestRefresh,
          isFastPreviewAvailable = ::isFastPreviewAvailable,
          requestFastPreviewRefresh = delegateFastPreviewSurface::requestFastPreviewRefreshSync,
          restorePreviousMode = ::restorePrevious,
          previewElementProvider = previewElementProvider,
        ) {
          it
        }
      }

      // Launch all the listeners that are bound to the current activation.
      launch(workerThread) {
        smartModeFlow(project, this@CommonPreviewRepresentation, LOG).collectLatest {
          onEnterSmartMode()
        }
      }
    }
  }

  init {
    launch(workerThread) {
      // Keep track of the last mode that was set to ensure it is correctly disposed
      var lastMode: PreviewMode? = null

      previewModeManager.mode.collect {
        (it.selected as? T).let { element -> previewFlowManager.setSingleFilter(element) }

        if (PreviewModeManager.areModesOfDifferentType(lastMode, it)) {
          lastMode?.let { last -> onExit(last) }
          // The layout update needs to happen before onEnter, so that any zooming performed
          // in onEnter uses the correct preview layout when measuring scale.
          updateLayoutManager(it)
          onEnter(it)
        } else {
          updateLayoutManager(it)
        }
        lastMode = it
      }
    }
    launch {
      fpsLimitFlow.collect {
        interactiveManager.fpsLimit = it
        // When getting out of Essentials Mode, request a refresh
        if (!PreviewEssentialsModeManager.isEssentialsModeEnabled) requestRefresh()
      }
    }
  }

  private fun onEnterSmartMode() {
    when (renderingBuildStatusManager.status) {
      // Do not refresh if we still need to build the project. Instead, only update the empty panel
      // and editor notifications if needed.
      RenderingBuildStatus.NeedsBuild -> previewViewModel.onEnterSmartMode()
      // We try to refresh even if the status is ProjectStatus.NotReady. This may not result into a
      // successful preview, since the project
      // might not have been built, but it's worth to try.
      else -> requestRefresh()
    }
  }

  private suspend fun onExit(mode: PreviewMode) {
    when (mode) {
      is PreviewMode.Default -> {}
      is PreviewMode.Interactive -> {
        stopInteractivePreview()
      }
      is PreviewMode.Focus -> {
        withContext(Dispatchers.Main) { previewView.focusMode = null }
      }
      is PreviewMode.AnimationInspection -> {
        stopAnimationInspector()
        updateAnimationPanelVisibility()
      }
      else -> {}
    }
  }

  private suspend fun onEnter(mode: PreviewMode) {
    when (mode) {
      is PreviewMode.Default -> {
        invalidateAndRefresh()
        surface.repaint()
      }
      is PreviewMode.Interactive -> {
        startInteractivePreview(mode.selected)
      }
      is PreviewMode.Focus -> {
        invalidateAndRefresh()
        surface.repaint()
        withContext(Dispatchers.Main) { previewView.focusMode = FocusMode(surface) }
      }
      is PreviewMode.AnimationInspection -> {
        startAnimationInspector(mode.selected)
        updateAnimationPanelVisibility()
      }
      else -> {}
    }
    surface.background = mode.backgroundColor
  }

  private suspend fun startAnimationInspector(element: PreviewElement<*>) {
    LOG.debug("Starting animation inspector mode on: $element")
    invalidateAndRefresh()
    withContext(Dispatchers.Main) {
      createAnimationInspector(element)?.also {
        Disposer.register(this@CommonPreviewRepresentation, it)
        currentAnimationPreview = it
      }
    }
    ActivityTracker.getInstance().inc()
  }

  @UiThread
  protected open fun createAnimationInspector(element: PreviewElement<*>): AnimationPreview<*>? {
    return null
  }

  private suspend fun stopAnimationInspector() {
    LOG.debug("Stopping animation inspector mode")
    currentAnimationPreview?.let {
      // The animation inspector should be disposed on the Dispatchers.Main
      withContext(Dispatchers.Main) { Disposer.dispose(it) }
    }
    currentAnimationPreview = null
    invalidateAndRefresh()
  }

  private suspend fun startInteractivePreview(element: PreviewElement<*>) {
    LOG.debug("Starting interactive preview mode on: $element")
    invalidateAndRefresh()
    interactiveManager.start()
    ActivityTracker.getInstance().inc()
  }

  private fun stopInteractivePreview() {
    LOG.debug("Stopping interactive preview mode")
    interactiveManager.stop()
    invalidate()
  }

  private suspend fun updateLayoutManager(mode: PreviewMode) {
    withContext(Dispatchers.Main) {
      surface.layoutManagerSwitcher?.currentLayoutOption?.value = mode.layoutOption
    }
  }

  /**
   * Whether fast preview is available. In addition to checking its normal availability from
   * [FastPreviewManager], we also verify that essentials mode is not enabled, because fast preview
   * should not be available in this case.
   */
  private fun isFastPreviewAvailable() =
    FastPreviewManager.getInstance(project).isAvailable &&
      !PreviewEssentialsModeManager.isEssentialsModeEnabled

  /**
   * Updates the visibility of the animation panel. The panel should be visible only when the
   * current mode is [PreviewMode.AnimationInspection] and there are no errors or the project needs
   * a build.
   */
  private fun updateAnimationPanelVisibility() {
    runInEdt {
      if (mode.value !is PreviewMode.AnimationInspection) {
        previewView.bottomPanel = null
        return@runInEdt
      }

      previewView.bottomPanel =
        when {
          project.needsBuild ||
            previewViewModel.hasSyntaxErrors ||
            previewViewModel.hasRenderErrors ||
            previewViewModel.isOutOfDate -> null
          mode.value is PreviewMode.AnimationInspection -> currentAnimationPreview?.component
          else -> null
        }
    }
  }

  /**
   * Returns the list of [PreviewFlowManager.toRenderPreviewElementsFlow] that has been rendered.
   * This method is for testing purposes only and should not be used outside of tests.
   */
  @TestOnly
  fun renderedPreviewElementsFlowForTest() = previewFlowManager.renderedPreviewElementsFlow

  @TestOnly fun requestRefreshForTest() = requestRefresh()
}
