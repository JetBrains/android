/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.launchWithProgress
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.glance.preview.GlancePreviewBundle.message
import com.android.tools.idea.log.LoggerWithFixedInfo
import com.android.tools.idea.preview.DelegatingPreviewElementModelAdapter
import com.android.tools.idea.preview.MemoizedPreviewElementProvider
import com.android.tools.idea.preview.NavigatingInteractionHandler
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.lifecycle.PreviewLifecycleManager
import com.android.tools.idea.preview.navigation.DefaultNavigationHandler
import com.android.tools.idea.preview.refreshExistingPreviewElements
import com.android.tools.idea.preview.sortByDisplayAndSourcePosition
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.projectsystem.CodeOutOfDateTracker
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import javax.swing.JComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GLANCE_APPWIDGET_SUPPORTED_ACTIONS = setOf(NlSupportedActions.TOGGLE_ISSUE_PANEL)

/** A generic [MethodPreviewElement] [PreviewRepresentation]. */
internal class GlancePreviewRepresentation<T : MethodPreviewElement>(
  adapterViewFqcn: String,
  psiFile: PsiFile,
  previewProvider: PreviewElementProvider<T>,
  previewElementModelAdapterDelegate: GlancePreviewElementModelAdapter<T, NlModel>
) : PreviewRepresentation, AndroidCoroutinesAware, UserDataHolderEx by UserDataHolderBase() {

  private val LOG = Logger.getInstance(GlancePreviewRepresentation::class.java)
  private val project = psiFile.project
  private val module = runReadAction { ModuleUtilCore.findModuleForPsiElement(psiFile) }
  private val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }

  private val projectBuildStatusManager = ProjectBuildStatusManager.create(this, psiFile)

  private val lifecycleManager =
    PreviewLifecycleManager(
      project,
      this,
      this,
      {
        initializeFlows()
        onInit()
      },
      {
        initializeFlows()
        surface.activate()
      },
      {
        LOG.debug("onDeactivate")
        surface.deactivateIssueModel()
      },
      {
        LOG.debug("Delayed surface deactivation")
        surface.deactivate()
      }
    )

  private val previewView = invokeAndWaitIfNeeded {
    GlancePreviewView(
      project,
      NlDesignSurface.builder(project, this)
        .setActionManagerProvider { surface -> GlancePreviewActionManager(surface) }
        .setSceneManagerProvider { surface, model ->
          NlDesignSurface.defaultSceneManagerProvider(surface, model).apply {
            setShrinkRendering(true)
          }
        }
        .setSupportedActions(GLANCE_APPWIDGET_SUPPORTED_ACTIONS)
        .setInteractionHandlerProvider { NavigatingInteractionHandler(it) }
        .setNavigationHandler(DefaultNavigationHandler { _, _, _, _, _ -> null })
        .setDelegateDataProvider {
          when (it) {
            PREVIEW_VIEW_MODEL_STATUS.name -> previewViewModel
            else -> null
          }
        }
        .setScreenViewProvider(GLANCE_SCREEN_VIEW_PROVIDER, false),
      this
    )
  }

  private val surface: NlDesignSurface
    get() = previewView.surface

  private val previewViewModel: GlancePreviewViewModel =
    GlancePreviewViewModel(previewView, projectBuildStatusManager, project, psiFilePointer) {
      surface.sceneManagers.any { it.renderResult.isErrorResult(adapterViewFqcn) }
    }

  private val previewFreshnessTracker =
    CodeOutOfDateTracker.create(module, this) { requestRefresh() }

  private val previewElementProvider =
    MemoizedPreviewElementProvider(previewProvider, previewFreshnessTracker)
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
        object : DataContext {
          private val delegate =
            previewElementModelAdapterDelegate.createDataContext(previewElement)
          override fun getData(dataId: String): Any? =
            when (dataId) {
              CommonDataKeys.PROJECT.name -> project
              else -> delegate.getData(dataId)
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
      }
        ?: return

    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    val showingPreviewElements =
      surface.updatePreviewsAndRefresh(
        true,
        previewElementProvider,
        LOG,
        psiFile,
        this,
        progressIndicator,
        this::onAfterRender,
        previewElementModelAdapter,
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
            this@GlancePreviewRepresentation::configureLayoutlibSceneManager
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
    layoutlibSceneManager: LayoutlibSceneManager
  ) = layoutlibSceneManager

  override fun dispose() {}

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
        smartModeFlow(project, this@GlancePreviewRepresentation, LOG).collectLatest {
          onEnterSmartMode()
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
}
