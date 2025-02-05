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
package com.android.tools.idea.compose.preview.flow

import com.android.tools.idea.compose.ComposePreviewElementsModel
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.defaultFilePreviewElementFinder
import com.android.tools.idea.compose.preview.util.isFastPreviewAvailable
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.editors.build.PsiCodeFileOutOfDateStatusReporter
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.preview.FilePreviewElementProvider
import com.android.tools.idea.preview.flow.CommonPreviewFlowManager
import com.android.tools.idea.preview.flow.PreviewElementFilter
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Class responsible for handling all the [StateFlow]s related to Compose Previews, e.g. managing
 * the render process and setting the current mode.
 */
internal class ComposePreviewFlowManager(
  private val log: Logger = Logger.getInstance(ComposePreviewFlowManager::class.java),
  /** Delegate [PreviewFlowManager] containing common flow logic with other types of previews. */
  private val delegate: CommonPreviewFlowManager<PsiComposePreviewElementInstance> =
    CommonPreviewFlowManager(log),
) : PreviewFlowManager<PsiComposePreviewElementInstance> by delegate {

  /**
   * Preview element provider corresponding to the current state of the Preview. Different modes
   * might require a different provider to be set, e.g. UI check mode needs a provider that produces
   * previews with reference devices. When exiting the mode and returning to static preview, the
   * element provider should be reset to [defaultPreviewElementProvider].
   */
  val uiCheckFilterFlow = delegate.uiCheckFilterFlow

  /**
   * Only for requests to refresh UI and notifications (without refreshing the preview contents).
   * This allows to bundle notifications and respects the activation/deactivation lifecycle.
   *
   * Each instance subscribes itself to the flow when it is activated, and it is automatically
   * unsubscribed when the [lifecycleManager] detects a deactivation (see [onActivate],
   * [initializeFlows] and [onDeactivate])
   */
  private val refreshNotificationsAndVisibilityFlow: MutableSharedFlow<Unit> =
    MutableSharedFlow(replay = 1)

  /**
   * Gets the current filter applied to the flows as a [PreviewElementFilter.Group] or null if the
   * current filter is of another type.
   */
  fun getCurrentFilterAsGroup(): PreviewElementFilter.Group<PsiComposePreviewElementInstance>? =
    delegate.getCurrentFilterAsGroup()

  /**
   * Returns whether there are previews that have completed the render process, i.e. if
   * [renderedPreviewElementsFlow] has elements.
   */
  fun hasRenderedPreviewElements() =
    (renderedPreviewElementsFlow.value as? FlowableCollection.Present<*>)
      ?.collection
      ?.isNotEmpty() == true

  /**
   * Updates the value of [renderedPreviewElementsInstancesFlow] with the given list of previews.
   */
  override fun updateRenderedPreviews(previewElements: List<PsiComposePreviewElementInstance>) {
    delegate.updateRenderedPreviews(previewElements)
  }

  /** Returns how many previews are available to be rendered in the current file. */
  fun previewsCount() =
    (filteredPreviewElementsFlow.value as? FlowableCollection.Present<*>)?.collection?.size ?: 0

  /** Initializes the flows that will listen to different events and will call [requestRefresh]. */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun CoroutineScope.initializeFlows(
    disposable: Disposable,
    previewModeManager: PreviewModeManager,
    psiCodeFileOutOfDateStatusReporter: PsiCodeFileOutOfDateStatusReporter,
    psiFilePointer: SmartPsiElementPointer<PsiFile>,
    invalidate: () -> Unit,
    requestRefresh: () -> Unit,
    requestFastPreviewRefresh: suspend () -> Unit,
    restorePreviousMode: () -> Unit,
    queryStatus: () -> RenderingBuildStatus,
    updateVisibilityAndNotifications: () -> Unit,
  ) {
    with(this@initializeFlows) {
      val project = psiFilePointer.project

      delegate.run {
        initializeFlows(
          disposable = disposable,
          previewModeManager = previewModeManager,
          psiCodeFileOutOfDateStatusReporter = psiCodeFileOutOfDateStatusReporter,
          psiFilePointer = psiFilePointer,
          invalidate = invalidate,
          requestRefresh = requestRefresh,
          isFastPreviewAvailable = { isFastPreviewAvailable(project) },
          requestFastPreviewRefresh = requestFastPreviewRefresh,
          restorePreviousMode = restorePreviousMode,
          previewElementProvider =
            FilePreviewElementProvider(psiFilePointer, defaultFilePreviewElementFinder),
          toInstantiatedPreviewElementsFlow =
            ComposePreviewElementsModel::instantiatedPreviewElementsFlow,
        )
      }

      // Flow to collate and process refreshNotificationsAndVisibilityFlow requests.
      launch {
        refreshNotificationsAndVisibilityFlow.conflate().collect {
          withContext(workerThread) {
            refreshNotificationsAndVisibilityFlow
              .resetReplayCache() // Do not keep re-playing after we have received the element.
            log.debug("refreshNotificationsAndVisibilityFlow, request=$it")
            updateVisibilityAndNotifications()
          }
        }
      }

      launch(workerThread) {
        log.debug(
          "smartModeFlow setup status=${queryStatus()}, dumbMode=${DumbService.isDumb(project)}"
        )
        // Flow handling switch to smart mode.
        smartModeFlow(project, disposable, log).collectLatest {
          val projectBuildStatus = queryStatus()
          log.debug(
            "smartModeFlow, status change status=${projectBuildStatus}," +
              " dumbMode=${DumbService.isDumb(project)}"
          )
          when (projectBuildStatus) {
            // Do not refresh if we still need to build the project. Instead, only update the
            // empty panel and editor notifications if needed.
            RenderingBuildStatus.NotReady,
            RenderingBuildStatus.NeedsBuild,
            RenderingBuildStatus.Building -> updateVisibilityAndNotifications()
            else -> requestRefresh()
          }
        }
      }
    }
  }

  fun CoroutineScope.updateVisibilityAndNotifications(
    onVisibilityAndNotificationsUpdate: () -> Unit
  ) {
    launch(workerThread) { refreshNotificationsAndVisibilityFlow.emit(Unit) }
    launch(uiThread) { onVisibilityAndNotificationsUpdate() }
  }
}
