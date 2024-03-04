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
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.launchWithProgress
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.log.LoggerWithFixedInfo
import com.android.tools.idea.modes.essentials.essentialsModeFlow
import com.android.tools.idea.preview.CommonPreviewRefreshRequest
import com.android.tools.idea.preview.DelegatingPreviewElementModelAdapter
import com.android.tools.idea.preview.MemoizedPreviewElementProvider
import com.android.tools.idea.preview.NavigatingInteractionHandler
import com.android.tools.idea.preview.PreviewBuildListenersManager
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.PreviewRefreshManager
import com.android.tools.idea.preview.PsiPreviewElement
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.flow.CommonPreviewFlowManager
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.gallery.CommonGalleryEssentialsModeManager
import com.android.tools.idea.preview.gallery.GalleryMode
import com.android.tools.idea.preview.groups.PreviewGroupManager
import com.android.tools.idea.preview.interactive.InteractivePreviewManager
import com.android.tools.idea.preview.interactive.analytics.InteractivePreviewUsageTracker
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

private val modelUpdater: NlModel.NlModelUpdaterInterface = DefaultModelUpdater()
val PREVIEW_ELEMENT_INSTANCE = DataKey.create<PsiPreviewElement>("PreviewElement")

/** A generic [PreviewElement] [PreviewRepresentation]. */
open class CommonPreviewRepresentation<T : PsiPreviewElement>(
  adapterViewFqcn: String,
  psiFile: PsiFile,
  previewProvider: PreviewElementProvider<T>,
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
  isEssentialsModeEnabled: () -> Boolean,
  useCustomInflater: Boolean = true,
) :
  PreviewRepresentation,
  AndroidCoroutinesAware,
  UserDataHolderEx by UserDataHolderBase(),
  PreviewModeManager {

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
      NlDesignSurface.builder(project, this)
        .setSceneManagerProvider { surface, model ->
          NlDesignSurface.defaultSceneManagerProvider(surface, model).apply {
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
            else -> null
          }
        }
        .apply { configureDesignSurface() },
      this,
    )
  }

  private val surface: NlDesignSurface
    get() = previewView.mainSurface

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
      isFastPreviewSupported = false,
      isEssentialsModeEnabled,
      ::invalidate,
      ::requestRefresh,
    )

  @TestOnly
  internal fun hasBuildListenerSetupFinishedForTest() =
    previewBuildListenersManager.buildListenerSetupFinished

  private val previewFreshnessTracker =
    CodeOutOfDateTracker.create(module, this) { requestRefresh() }

  private val previewFlowManager =
    CommonPreviewFlowManager(
      filePreviewElementProvider =
        MemoizedPreviewElementProvider(previewProvider, previewFreshnessTracker),
      requestRefresh = ::requestRefresh,
    )

  private var renderedElements: List<T> = emptyList()

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
        isEssentialsModeEnabled = isEssentialsModeEnabled,
        onUpdatedFromStudioEssentialsMode = {},
        onUpdatedFromPreviewEssentialsMode = {},
        requestRefresh = ::requestRefresh,
      )
      .also { Disposer.register(this@CommonPreviewRepresentation, it) }

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
      renderedElements = filePreviewElements
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
        withContext(workerThread) { previewFlowManager.updateFlows() }
        val filePreviewElements =
          previewFlowManager.filteredPreviewElementsFlow.value
            .asCollection()
            .sortByDisplayAndSourcePosition()

        val needsFullRefresh =
          invalidated.getAndSet(false) || renderedElements != filePreviewElements
        invalidateIfCancelled.set(needsFullRefresh)

        previewViewModel.setHasPreviews(filePreviewElements.isNotEmpty())
        if (!needsFullRefresh) {
          requestLogger.debug(
            "No updates on the PreviewElements, just refreshing the existing ones"
          )
          // In this case, there are no new previews. We need to make sure that the surface is still
          // correctly
          // configured and that we are showing the right size for components. For example, if the
          // user switches on/off
          // decorations, that will not generate/remove new PreviewElements but will change the
          // surface settings.
          refreshProgressIndicator.text =
            message("refresh.progress.indicator.reusing.existing.previews")
          surface.refreshExistingPreviewElements(
            refreshProgressIndicator,
            previewElementModelAdapter::modelToElement,
            this@CommonPreviewRepresentation::configureLayoutlibSceneManager,
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
      Disposer.dispose(refreshProgressIndicator)
      previewViewModel.refreshCompleted(it is CancellationException, System.nanoTime() - startTime)
    }
    return refreshJob
  }

  private fun requestRefresh(onRefreshCompleted: CompletableDeferred<Unit>? = null) {
    if (!lifecycleManager.isActive()) {
      onRefreshCompleted?.completeExceptionally(IllegalStateException("Not active"))
      return
    }
    val request =
      CommonPreviewRefreshRequest(
        clientId = this@CommonPreviewRepresentation.hashCode().toString(),
        delegateRefresh = { createRefreshJob(it as CommonPreviewRefreshRequest) },
        onRefreshCompleted = onRefreshCompleted,
      )
    refreshManager.requestRefresh(request)
  }

  private suspend fun invalidateAndRefresh() {
    CompletableDeferred<Unit>().let {
      invalidate()
      requestRefresh(it)
      it.join()
    }
  }

  private fun invalidate() {
    invalidated.set(true)
  }

  private fun onAfterRender() {
    previewViewModel.afterPreviewsRefreshed()
  }

  private fun configureLayoutlibSceneManager(
    displaySettings: PreviewDisplaySettings,
    layoutlibSceneManager: LayoutlibSceneManager,
  ) =
    layoutlibSceneManager.apply {
      interactive = mode.value is PreviewMode.Interactive
      isUsePrivateClassLoader = mode.value is PreviewMode.Interactive
    }

  private fun activate(isResuming: Boolean) {
    initializeFlows()
    if (isResuming) {
      surface.activate()
    } else {
      onInit()
    }

    galleryEssentialsModeManager.activate()
    if (mode.value is PreviewMode.Interactive) {
      interactiveManager.resume()
    }
  }

  private fun CoroutineScope.initializeFlows() {
    with(this@initializeFlows) {
      // Initialize flows
      launch(workerThread) { previewFlowManager.updateFlows() }

      // Launch all the listeners that are bound to the current activation.
      launch(workerThread) {
        smartModeFlow(project, this@CommonPreviewRepresentation, LOG).collectLatest {
          onEnterSmartMode()
        }
      }

      launch(workerThread) {
        // Keep track of the last mode that was set to ensure it is correctly disposed
        var lastMode: PreviewMode? = null

        previewModeManager.mode.collect {
          previewFlowManager.setSingleFilter(it.selected as? T)

          lastMode?.let { last -> onExit(last) }
          // The layout update needs to happen before onEnter, so that any zooming performed
          // in onEnter uses the correct preview layout when measuring scale.
          updateLayoutManager(it)
          onEnter(it)
          lastMode = it
        }
      }
      launch { fpsLimitFlow.collect { interactiveManager.fpsLimit = it } }
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
      else -> {}
    }
    surface.background = mode.backgroundColor
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
}
