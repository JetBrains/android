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

import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NlModelUpdaterInterface
import com.android.tools.idea.common.scene.SceneUpdateListener
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.launchWithProgress
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.build.PsiCodeFileOutOfDateStatusReporter
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LoggerWithFixedInfo
import com.android.tools.idea.preview.CommonPreviewRefreshRequest
import com.android.tools.idea.preview.CommonPreviewRefreshType
import com.android.tools.idea.preview.DefaultRenderQualityManager
import com.android.tools.idea.preview.DefaultRenderQualityPolicy
import com.android.tools.idea.preview.DelegatingPreviewElementModelAdapter
import com.android.tools.idea.preview.MemoizedPreviewElementProvider
import com.android.tools.idea.preview.NavigatingInteractionHandler
import com.android.tools.idea.preview.PreviewBuildListenersManager
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.PreviewInvalidationManager
import com.android.tools.idea.preview.PreviewRefreshManager
import com.android.tools.idea.preview.PsiPreviewElementInstance
import com.android.tools.idea.preview.RenderQualityManager
import com.android.tools.idea.preview.RenderQualityPolicy
import com.android.tools.idea.preview.SimpleRenderQualityManager
import com.android.tools.idea.preview.ZoomConstants
import com.android.tools.idea.preview.analytics.InteractivePreviewUsageTracker
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.animation.AnimationPreview
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.preview.essentials.essentialsModeFlow
import com.android.tools.idea.preview.fast.CommonFastPreviewSurface
import com.android.tools.idea.preview.fast.FastPreviewSurface
import com.android.tools.idea.preview.flow.CommonPreviewFlowManager
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.gallery.CommonGalleryEssentialsModeManager
import com.android.tools.idea.preview.gallery.GalleryMode
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
import com.android.tools.idea.preview.refreshExistingPreviewElements
import com.android.tools.idea.preview.sortByDisplayAndSourcePosition
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.preview.viewmodels.CommonPreviewViewModel
import com.android.tools.idea.preview.views.CommonNlDesignSurfacePreviewView
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.PreviewElement
import com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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
 * @param sceneUpdateListener the listener to be notified whenever the scene of a preview element is
 *   updated.
 * @param createRefreshEventBuilder the function to get a [PreviewRefreshEventBuilder] to be used
 *   for tracking refresh metrics.
 */
open class CommonPreviewRepresentation<T : PsiPreviewElementInstance>(
  adapterViewFqcn: String,
  psiFile: PsiFile,
  previewProviderConstructor: (SmartPsiElementPointer<PsiFile>) -> PreviewElementProvider<T>,
  previewElementModelAdapterDelegate: PreviewElementModelAdapter<T, NlModel>,
  viewConstructor:
    (
      project: Project, surfaceBuilder: NlDesignSurface.Builder, parentDisposable: Disposable,
    ) -> CommonNlDesignSurfacePreviewView,
  viewModelConstructor:
    (
      previewView: PreviewView,
      projectBuildStatusManager: ProjectBuildStatusManager,
      refreshManager: PreviewRefreshManager,
      project: Project,
      psiFilePointer: SmartPsiElementPointer<PsiFile>,
      hasRenderErrors: () -> Boolean,
    ) -> CommonPreviewViewModel,
  configureDesignSurface: NlDesignSurface.Builder.() -> Unit,
  renderingTopic: RenderingTopic,
  useCustomInflater: Boolean = true,
  sceneUpdateListener: SceneUpdateListener? = null,
  private val createRefreshEventBuilder: (NlDesignSurface) -> PreviewRefreshEventBuilder? = { null },
) :
  PreviewRepresentation,
  AndroidCoroutinesAware,
  UserDataHolderEx by UserDataHolderBase(),
  PreviewModeManager,
  FastPreviewSurface,
  PreviewInvalidationManager {

  private val LOG = Logger.getInstance(CommonPreviewRepresentation::class.java)
  private val project = psiFile.project
  private val module = runReadAction { ModuleUtilCore.findModuleForPsiElement(psiFile) }
  private val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }

  private val projectBuildStatusManager = ProjectBuildStatusManager.create(this, psiFile)

  @TestOnly internal fun getProjectBuildStatusForTest() = projectBuildStatusManager.status

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
    DefaultNavigationHandler { _, _, _, _, _ -> null }
      .apply { Disposer.register(this@CommonPreviewRepresentation, this) }

  @VisibleForTesting
  val previewView = invokeAndWaitIfNeeded {
    viewConstructor(
        project,
        NlDesignSurface.builder(project, this) { surface, model ->
            NlDesignSurface.defaultSceneManagerProvider(surface, model, sceneUpdateListener).apply {
              setUseCustomInflater(useCustomInflater)
              setShrinkRendering(true)
              setRenderingTopic(renderingTopic)
              setListenResourceChange(false) // don't re-render on resource changes
              setUpdateAndRenderWhenActivated(false) // don't re-render on activation
            }
          }
          .setInteractionHandlerProvider {
            delegateInteractionHandler.apply {
              delegate = NavigatingInteractionHandler(it, navigationHandler)
            }
          }
          .setDelegateDataProvider {
            when (it) {
              PREVIEW_VIEW_MODEL_STATUS.name -> previewViewModel
              PreviewModeManager.KEY.name -> this@CommonPreviewRepresentation
              PreviewGroupManager.KEY.name,
              PreviewFlowManager.KEY.name -> previewFlowManager
              FastPreviewSurface.KEY.name -> this@CommonPreviewRepresentation
              PreviewInvalidationManager.KEY.name -> this@CommonPreviewRepresentation
              else -> null
            }
          }
          .apply {
            setMaxZoomToFitLevel(ZoomConstants.MAX_ZOOM_TO_FIT_LEVEL)
            setMinScale(ZoomConstants.MIN_SCALE)
            setMaxScale(ZoomConstants.MAX_SCALE)
            configureDesignSurface()
          },
        this,
      )
      .also {
        it.mainSurface.analyticsManager.setEditorFileTypeWithoutTracking(
          psiFilePointer.virtualFile,
          project,
        )
      }
  }

  private val surface: NlDesignSurface
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
      projectBuildStatusManager,
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
    )

  @TestOnly
  internal fun hasBuildListenerSetupFinishedForTest() =
    previewBuildListenersManager.buildListenerSetupFinished

  @TestOnly
  internal fun hasFlowInitializationFinishedForTest() =
    previewFlowManager.filteredPreviewElementsFlow.value != FlowableCollection.Uninitialized

  private val previewFreshnessTracker =
    CodeOutOfDateTracker.create(module, this) { requestRefresh() }

  private val myPsiCodeFileOutOfDateStatusReporter =
    PsiCodeFileOutOfDateStatusReporter.getInstance(project)

  private var renderedElementsFlow =
    MutableStateFlow<FlowableCollection<T>>(FlowableCollection.Uninitialized)

  private val previewFlowManager = CommonPreviewFlowManager(renderedElementsFlow)

  private val previewElementProvider =
    MemoizedPreviewElementProvider(
      previewProviderConstructor(psiFilePointer),
      previewFreshnessTracker,
    )

  private val previewElementModelAdapter =
    object : DelegatingPreviewElementModelAdapter<T, NlModel>(previewElementModelAdapterDelegate) {
      override fun createDataContext(previewElement: T) =
        CustomizedDataContext.create(
          previewElementModelAdapterDelegate.createDataContext(previewElement)
        ) { dataId ->
          when (dataId) {
            PREVIEW_ELEMENT_INSTANCE.name -> previewElement
            CommonDataKeys.PROJECT.name -> project
            PreviewModeManager.KEY.name -> this@CommonPreviewRepresentation
            PreviewGroupManager.KEY.name,
            PreviewFlowManager.KEY.name -> previewFlowManager
            FastPreviewSurface.KEY.name -> this@CommonPreviewRepresentation
            PreviewInvalidationManager.KEY.name -> this@CommonPreviewRepresentation
            else -> null
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

  private val galleryEssentialsModeManager =
    CommonGalleryEssentialsModeManager(
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

  var currentInspector: AnimationPreview<*>? = null
    private set

  override val component: JComponent
    get() = previewView.component

  override val preferredInitialVisibility: PreferredVisibility? = null

  override val mode = previewModeManager.mode

  override fun dispose() {
    if (mode.value is PreviewMode.Interactive) {
      interactiveManager.stop()
    }
  }

  override fun onActivate() = lifecycleManager.activate()

  override fun onDeactivate() = lifecycleManager.deactivate()

  override fun restorePrevious() = previewModeManager.restorePrevious()

  override fun setMode(mode: PreviewMode) {
    previewModeManager.setMode(mode)
  }

  override fun requestFastPreviewRefreshAsync() =
    delegateFastPreviewSurface.requestFastPreviewRefreshAsync()

  override fun invalidate() {
    invalidated.set(true)
  }

  private fun onInit() {
    LOG.debug("onInit")
    if (Disposer.isDisposed(this)) {
      LOG.info("Preview was closed before the initialization completed.")
    }
    val psiFile = psiFilePointer.element
    requireNotNull(psiFile) { "PsiFile was disposed before the preview initialization completed." }

    setupBuildListener(project, previewViewModel, this)
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

    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    val showingPreviewElements =
      surface.updatePreviewsAndRefresh(
        tryReusingModels = true,
        reinflate = true,
        filePreviewElements,
        LOG,
        psiFile,
        this,
        progressIndicator,
        { _ -> onAfterRender() },
        previewElementModelAdapter,
        modelUpdater,
        navigationHandler,
        this::configureLayoutlibSceneManager,
        refreshEventBuilder,
      )

    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    if (showingPreviewElements.size >= filePreviewElements.size) {
      renderedElementsFlow.value = FlowableCollection.Present(filePreviewElements)
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
    invalidateIfCancelled: AtomicBoolean,
  ) =
    launchWithProgress(refreshProgressIndicator, uiThread) {
      val requestLogger = LoggerWithFixedInfo(LOG, mapOf("requestId" to request.requestId))

      if (DumbService.isDumb(project)) {
        return@launchWithProgress
      }

      if (projectBuildStatusManager.status == ProjectStatus.NeedsBuild) {
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
          previewFlowManager.filteredPreviewElementsFlow.value
            .asCollection()
            .sortByDisplayAndSourcePosition()

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
            this@CommonPreviewRepresentation::configureLayoutlibSceneManager,
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
        requestLogger.warn("Request failed", t)
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

    val invalidateIfCancelled = AtomicBoolean(false)
    val refreshJob = createRefreshJob(request, refreshProgressIndicator, invalidateIfCancelled)
    refreshJob.invokeOnCompletion {
      LOG.debug("Completed")
      if (it is CancellationException && invalidateIfCancelled.get()) {
        invalidate()
      }
      // Progress indicators must be disposed in the ui thread
      launch(uiThread) { Disposer.dispose(refreshProgressIndicator) }
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
    // We need to run any callbacks that have been registered during the rendering of the preview
    surface.sceneManagers.forEach { it.executeCallbacksAndRequestRender() }
    previewViewModel.afterPreviewsRefreshed()
  }

  private fun configureLayoutlibSceneManager(
    displaySettings: PreviewDisplaySettings,
    layoutlibSceneManager: LayoutlibSceneManager,
  ) =
    layoutlibSceneManager.apply {
      interactive = mode.value is PreviewMode.Interactive
      isUsePrivateClassLoader = mode.value is PreviewMode.Interactive
      quality = qualityManager.getTargetQuality(this@apply)
    }

  private fun activate(isResuming: Boolean) {
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

    // Gallery mode should be updated only if Preview is active / in foreground.
    // It will help to avoid enabling gallery mode while Preview is inactive, as it will also save
    // this state for later to restore.
    galleryEssentialsModeManager.activate()
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
    when (projectBuildStatusManager.status) {
      // Do not refresh if we still need to build the project. Instead, only update the empty panel
      // and editor notifications if needed.
      ProjectStatus.NeedsBuild -> previewViewModel.onEnterSmartMode()
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
      is PreviewMode.Gallery -> {
        withContext(uiThread) { previewView.galleryMode = null }
      }
      is PreviewMode.AnimationInspection -> {
        stopAnimationInspector()
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
      is PreviewMode.Gallery -> {
        invalidateAndRefresh()
        surface.repaint()
        withContext(uiThread) { previewView.galleryMode = GalleryMode(surface) }
      }
      is PreviewMode.AnimationInspection -> {
        startAnimationInspector(mode.selected)
      }
      else -> {}
    }
    surface.background = mode.backgroundColor
  }

  private suspend fun startAnimationInspector(element: PreviewElement<*>) {
    LOG.debug("Starting animation inspector mode on: $element")
    invalidateAndRefresh()
    createAnimationInspector()?.also {
      Disposer.register(this@CommonPreviewRepresentation, it)
      withContext(uiThread) { previewView.bottomPanel = it.component }
    }
    ActivityTracker.getInstance().inc()
  }

  protected open fun createAnimationInspector(): AnimationPreview<*>? {
    return null
  }

  private suspend fun stopAnimationInspector() {
    LOG.debug("Stopping animation inspector mode")
    currentInspector?.dispose()
    withContext(uiThread) { previewView.bottomPanel = null }
    invalidateAndRefresh()
  }

  private suspend fun startInteractivePreview(element: PreviewElement<*>) {
    LOG.debug("Starting interactive preview mode on: $element")
    invalidateAndRefresh()
    interactiveManager.start()
    ActivityTracker.getInstance().inc()
  }

  private suspend fun stopInteractivePreview() {
    LOG.debug("Stopping interactive preview mode")
    interactiveManager.stop()
    invalidateAndRefresh()
  }

  private suspend fun updateLayoutManager(mode: PreviewMode) {
    withContext(uiThread) {
      surface.layoutManagerSwitcher?.currentLayout?.value = mode.layoutOption
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
}
