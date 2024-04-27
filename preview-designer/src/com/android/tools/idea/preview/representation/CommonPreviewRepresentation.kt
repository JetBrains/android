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
import com.android.tools.idea.concurrency.launchWithProgress
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.log.LoggerWithFixedInfo
import com.android.tools.idea.preview.DelegatingPreviewElementModelAdapter
import com.android.tools.idea.preview.MemoizedPreviewElementProvider
import com.android.tools.idea.preview.NavigatingInteractionHandler
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.interactive.InteractivePreviewManager
import com.android.tools.idea.preview.interactive.analytics.InteractivePreviewUsageTracker
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
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.CustomizedDataContext
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JComponent

private val modelUpdater: NlModel.NlModelUpdaterInterface = DefaultModelUpdater()
val PREVIEW_ELEMENT_INSTANCE = DataKey.create<PreviewElement>("PreviewElement")

/** A generic [PreviewElement] [PreviewRepresentation]. */
open class CommonPreviewRepresentation<T : PreviewElement>(
  adapterViewFqcn: String,
  psiFile: PsiFile,
  previewProvider: PreviewElementProvider<T>,
  previewElementModelAdapterDelegate: PreviewElementModelAdapter<T, NlModel>,
  viewConstructor:
    (
      project: Project, surfaceBuilder: NlDesignSurface.Builder, parentDisposable: Disposable
    ) -> CommonNlDesignSurfacePreviewView,
  viewModelConstructor:
    (
      previewView: PreviewView,
      projectBuildStatusManager: ProjectBuildStatusManager,
      project: Project,
      psiFilePointer: SmartPsiElementPointer<PsiFile>,
      hasRenderErrors: () -> Boolean
    ) -> CommonPreviewViewModel,
  configureDesignSurface: NlDesignSurface.Builder.() -> Unit,
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

  private val lifecycleManager =
    PreviewLifecycleManager(
      project,
      parentScope = this,
      onInitActivate = {
        initializeFlows()
        onInit()
        if (mode.value is PreviewMode.Interactive) {
          interactiveManager.resume()
        }
      },
      onResumeActivate = {
        initializeFlows()
        surface.activate()
        if (mode.value is PreviewMode.Interactive) {
          interactiveManager.resume()
        }
      },
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

  private val navigationHandler =
    DefaultNavigationHandler { _, _, _, _, _ -> null }
      .apply { Disposer.register(this@CommonPreviewRepresentation, this) }

  private val previewView = invokeAndWaitIfNeeded {
    viewConstructor(
      project,
      NlDesignSurface.builder(project, this)
        .setSceneManagerProvider { surface, model ->
          NlDesignSurface.defaultSceneManagerProvider(surface, model).apply {
            setUseCustomInflater(useCustomInflater)
            setShrinkRendering(true)
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
            else -> null
          }
        }
        .apply { configureDesignSurface() },
      this
    )
  }

  private val surface: NlDesignSurface
    get() = previewView.surface

  private val previewViewModel: CommonPreviewViewModel =
    viewModelConstructor(previewView, projectBuildStatusManager, project, psiFilePointer) {
      surface.sceneManagers.any { it.renderResult.isErrorResult(adapterViewFqcn) }
    }

  private val previewFreshnessTracker =
    CodeOutOfDateTracker.create(module, this) { requestRefresh() }

  private val singleElementFlow = MutableStateFlow<T?>(null)

  private val memoizedPreviewElementProvider: PreviewElementProvider<T> =
    MemoizedPreviewElementProvider(previewProvider, previewFreshnessTracker)

  private val previewElementProvider: PreviewElementProvider<T> =
    object : PreviewElementProvider<T> {
      override suspend fun previewElements(): Sequence<T> {
        return singleElementFlow.value?.let { sequenceOf(it) }
          ?: memoizedPreviewElementProvider.previewElements()
      }
    }
  private var renderedElements: List<T> = emptyList()

  // TODO(b/239802877): We need to cover the case where the RefreshRequest with invalidate=true gets
  // called but also gets cancelled early (because of the next RefreshRequest gets collected) and
  // the "invalidate" action does not happen. In that case all the next requests should be with
  // invalidate=true no matter what until any of them succeeds.
  private class RefreshRequest(val invalidate: Boolean)

  private val refreshFlow: MutableStateFlow<RefreshRequest> = MutableStateFlow(RefreshRequest(true))

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
            else -> null
          }
        }
    }

  private fun requestRefresh(invalidate: Boolean = true) =
    launch(workerThread) { refreshFlow.emit(RefreshRequest(invalidate)) }

  private fun onInit() {
    LOG.debug("onInit")
    if (Disposer.isDisposed(this)) {
      LOG.info("Preview was closed before the initialization completed.")
    }
    val psiFile = psiFilePointer.element
    requireNotNull(psiFile) { "PsiFile was disposed before the preview initialization completed." }

    setupBuildListener(project, previewViewModel, this)

    project.runWhenSmartAndSyncedOnEdt(this, { onEnterSmartMode() })
  }

  private suspend fun doRefreshSync(
    filePreviewElements: List<T>,
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
      } ?: return

    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    val showingPreviewElements =
      surface.updatePreviewsAndRefresh(
        tryReusingModels = true,
        reinflate = true,
        previewElementProvider.previewElements().toList(),
        LOG,
        psiFile,
        this,
        progressIndicator,
        { _ -> onAfterRender() },
        previewElementModelAdapter,
        modelUpdater,
        navigationHandler,
        this::configureLayoutlibSceneManager
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
    invalidate: Boolean,
    refreshProgressIndicator: BackgroundableProcessIndicator
  ) =
    launchWithProgress(refreshProgressIndicator, uiThread) {
      val requestLogger = LoggerWithFixedInfo(LOG, mapOf())

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
          withContext(workerThread) {
            previewElementProvider.previewElements().toList().sortByDisplayAndSourcePosition()
          }

        val needsFullRefresh = invalidate || renderedElements != filePreviewElements

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
            this@CommonPreviewRepresentation::configureLayoutlibSceneManager
          )
        } else {
          refreshProgressIndicator.text =
            message("refresh.progress.indicator.refreshing.all.previews")
          previewViewModel.beforePreviewsRefreshed()
          doRefreshSync(filePreviewElements, refreshProgressIndicator)
        }
      } catch (t: Throwable) {
        requestLogger.warn("Request failed", t)
      } finally {
        previewViewModel.refreshFinished()
      }
    }

  fun createRefreshJob(invalidate: Boolean): Job? {
    val startTime = System.nanoTime()
    val refreshProgressIndicator =
      BackgroundableProcessIndicator(
        project,
        message("refresh.progress.indicator.title"),
        "",
        "",
        true
      )
    if (!Disposer.tryRegister(this, refreshProgressIndicator)) return null

    val refreshJob = createRefreshJob(invalidate, refreshProgressIndicator)

    refreshJob.invokeOnCompletion {
      LOG.debug("Completed")
      Disposer.dispose(refreshProgressIndicator)
      previewViewModel.refreshCompleted(it is CancellationException, System.nanoTime() - startTime)
    }
    return refreshJob
  }

  override val component: JComponent
    get() = previewView.component

  override val preferredInitialVisibility: PreferredVisibility? = null

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

  override fun dispose() {
    if (mode.value is PreviewMode.Interactive) {
      interactiveManager.stop()
    }
  }

  override fun onActivate() = lifecycleManager.activate()

  override fun onDeactivate() = lifecycleManager.deactivate()

  private fun CoroutineScope.initializeFlows() {
    with(this@initializeFlows) {
      // Launch all the listeners that are bound to the current activation.
      launch(workerThread) {
        // TODO(b/239802877): We can optimize this with collectLatest (cancelling the previous) but
        // we need to do it carefully.
        refreshFlow.collect { createRefreshJob(it.invalidate)?.join() }
      }

      launch(workerThread) {
        smartModeFlow(project, this@CommonPreviewRepresentation, LOG).collectLatest {
          onEnterSmartMode()
        }
      }

      launch(workerThread) {
        // Keep track of the last mode that was set to ensure it is correctly disposed
        var lastMode: PreviewMode? = null

        previewModeManager.mode.collect {
          lastMode?.let { last -> onExit(last) }
          onEnter(it)
          lastMode = it
        }
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

  private val previewModeManager = CommonPreviewModeManager()

  private val interactiveManager =
    InteractivePreviewManager(
        surface,
        fpsLimit = 30,
        { surface.sceneManagers },
        { InteractivePreviewUsageTracker.getInstance(surface) },
        delegateInteractionHandler
      )
      .also { Disposer.register(this@CommonPreviewRepresentation, it) }

  override val mode = previewModeManager.mode

  override fun restorePrevious() = previewModeManager.restorePrevious()

  override fun setMode(mode: PreviewMode) {
    previewModeManager.setMode(mode)
  }

  private suspend fun onExit(mode: PreviewMode) {
    when (mode) {
      is PreviewMode.Default -> {}
      is PreviewMode.Interactive -> {
        stopInteractivePreview()
      }
      else -> {}
    }
  }

  private suspend fun onEnter(mode: PreviewMode) {
    when (mode) {
      is PreviewMode.Default -> {
        createRefreshJob(invalidate = true)?.join()
        surface.repaint()
      }
      is PreviewMode.Interactive -> {
        startInteractivePreview(mode.selected)
      }
      else -> {}
    }
    surface.background = mode.backgroundColor
  }

  private suspend fun startInteractivePreview(element: PreviewElement) {
    LOG.debug("Starting interactive preview mode on: $element")
    singleElementFlow.value = element as T
    createRefreshJob(invalidate = true)?.join()
    interactiveManager.start()
    ActivityTracker.getInstance().inc()
  }

  private suspend fun stopInteractivePreview() {
    LOG.debug("Stopping interactive preview mode")
    singleElementFlow.value = null
    interactiveManager.stop()
    createRefreshJob(invalidate = true)?.join()
  }
}
