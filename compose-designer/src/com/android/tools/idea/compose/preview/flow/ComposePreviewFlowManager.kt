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
import com.android.tools.idea.compose.UiCheckModeFilter
import com.android.tools.idea.compose.preview.PreviewGroup
import com.android.tools.idea.compose.preview.essentials.ComposePreviewEssentialsModeManager
import com.android.tools.idea.compose.preview.util.isFastPreviewAvailable
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.SyntaxErrorUpdate
import com.android.tools.idea.concurrency.psiFileChangeFlow
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.concurrency.syntaxErrorFlow
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.build.PsiCodeFileChangeDetectorService
import com.android.tools.idea.editors.build.outOfDateKtFiles
import com.android.tools.idea.modes.essentials.EssentialsMode
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.sortByDisplayAndSourcePosition
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Class responsible for handling all the [StateFlow]s related to Compose Previews, e.g. managing
 * the render process and setting the current mode.
 */
internal class ComposePreviewFlowManager {

  private val log = Logger.getInstance(ComposePreviewFlowManager::class.java)

  /**
   * Flow containing all the [ComposePreviewElement]s available in the current file. This flow is
   * only updated when this Compose Preview representation is active.
   */
  val allPreviewElementsInFileFlow: MutableStateFlow<Collection<ComposePreviewElementInstance>> =
    MutableStateFlow(emptyList())

  /**
   * [StateFlow] of available named groups in this preview. The editor can contain multiple groups
   * and only one will be displayed at a given time.
   */
  val availableGroupsFlow: MutableStateFlow<Set<PreviewGroup.Named>> = MutableStateFlow(emptySet())

  /**
   * Flow containing all the [ComposePreviewElementInstance]s available in the current file to be
   * rendered. These are all the previews in [allPreviewElementsInFileFlow] filtered using
   * [filterFlow]. This flow is only updated when this Compose Preview representation is active.
   */
  val filteredPreviewElementsInstancesFlow:
    MutableStateFlow<Collection<ComposePreviewElementInstance>> =
    MutableStateFlow(emptyList())

  /**
   * Flow containing all the [ComposePreviewElementInstance]s that have completed rendering. These
   * are all the [filteredPreviewElementsInstancesFlow] that have rendered.
   */
  private val renderedPreviewElementsInstancesFlow:
    MutableStateFlow<Collection<ComposePreviewElementInstance>> =
    MutableStateFlow(emptyList())

  /**
   * Current filter being applied to the preview. The filter allows to select one element or a group
   * of them.
   */
  private val filterFlow: MutableStateFlow<ComposePreviewElementsModel.Filter> =
    MutableStateFlow(ComposePreviewElementsModel.Filter.Disabled)

  /**
   * Preview element provider corresponding to the current state of the Preview. Different modes
   * might require a different provider to be set, e.g. UI check mode needs a provider that produces
   * previews with reference devices. When exiting the mode and returning to static preview, the
   * element provider should be reset to [defaultPreviewElementProvider].
   */
  val uiCheckFilterFlow = MutableStateFlow<UiCheckModeFilter>(UiCheckModeFilter.Disabled)

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
   * Sets the [filterFlow] value to a [ComposePreviewElementsModel.Filter.Group] if possible,
   * otherwise set it to [ComposePreviewElementsModel.Filter.Disabled].
   */
  fun setGroupFilter(group: PreviewGroup, forceApply: Boolean = false) {
    val currentFilter = filterFlow.value
    // We can only apply a group filter if no filter existed before or if the current one is
    // already a group filter.
    val canApplyGroupFilter =
      forceApply ||
        currentFilter == ComposePreviewElementsModel.Filter.Disabled ||
        currentFilter is ComposePreviewElementsModel.Filter.Group
    filterFlow.value =
      if (group is PreviewGroup.Named && canApplyGroupFilter) {
        ComposePreviewElementsModel.Filter.Group(group)
      } else {
        ComposePreviewElementsModel.Filter.Disabled
      }
  }

  /**
   * Filter that can be applied to select a single instance. Setting this filter will trigger a
   * refresh.
   */
  fun setSingleFilter(newValue: ComposePreviewElementInstance?) {
    filterFlow.value =
      if (newValue != null) {
        ComposePreviewElementsModel.Filter.Single(newValue)
      } else {
        ComposePreviewElementsModel.Filter.Disabled
      }
  }

  /**
   * Gets the current value of [filterFlow] as a [ComposePreviewElementsModel.Filter.Group] or null
   * if the current filter is of another type.
   */
  fun getCurrentFilterAsGroup(): ComposePreviewElementsModel.Filter.Group? =
    filterFlow.value as? ComposePreviewElementsModel.Filter.Group

  /**
   * Returns whether there are previews that have completed the render process, i.e. if
   * [renderedPreviewElementsInstancesFlow] has elements.
   */
  fun hasRenderedPreviewElements() = renderedPreviewElementsInstancesFlow.value.isNotEmpty()

  /**
   * Updates the value of [renderedPreviewElementsInstancesFlow] with the given list of previews.
   */
  fun updateRenderedPreviews(previewElements: List<ComposePreviewElementInstance>) {
    renderedPreviewElementsInstancesFlow.value = previewElements
  }

  /** Returns how many previews are available to be rendered in the current file. */
  fun previewsCount() = filteredPreviewElementsInstancesFlow.value.size

  /** Initializes the flows that will listen to different events and will call [requestRefresh]. */
  @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
  fun CoroutineScope.initializeFlows(
    disposable: Disposable,
    previewModeManager: PreviewModeManager,
    psiCodeFileChangeDetectorService: PsiCodeFileChangeDetectorService,
    psiFilePointer: SmartPsiElementPointer<PsiFile>,
    invalidate: () -> Unit,
    requestRefresh: () -> Unit,
    requestFastPreviewRefresh: suspend () -> Unit,
    restorePreviousMode: () -> Unit,
    queryStatus: () -> ProjectStatus,
    updateVisibilityAndNotifications: () -> Unit,
  ) {
    with(this@initializeFlows) {
      val project = psiFilePointer.project

      launch(workerThread) {
        // Launch all the listeners that are bound to the current activation.
        ComposePreviewElementsModel.instantiatedPreviewElementsFlow(
            previewElementFlowForFile(psiFilePointer).map { it.sortByDisplayAndSourcePosition() },
          )
          .collectLatest {
            val previousElements = allPreviewElementsInFileFlow.value.toSet()
            allPreviewElementsInFileFlow.value = it
            (previewModeManager.mode.value as? PreviewMode.Gallery)?.let { oldMode ->
              oldMode.newMode(allPreviewElementsInFileFlow.value, previousElements)?.let { newMode
                ->
                previewModeManager.setMode(newMode)
              }
            }
          }
      }

      launch(workerThread) {
        val filteredPreviewsFlow =
          ComposePreviewElementsModel.filteredPreviewElementsFlow(
            allPreviewElementsInFileFlow,
            filterFlow,
          )

        // Flow for Preview changes
        combine(
            allPreviewElementsInFileFlow,
            filteredPreviewsFlow,
            uiCheckFilterFlow,
          ) { allAvailablePreviews, filteredPreviews, uiCheckFilter ->
            // Calculate groups
            val allGroups =
              allAvailablePreviews
                .mapNotNull {
                  it.displaySettings.group?.let { group -> PreviewGroup.namedGroup(group) }
                }
                .toSet()

            // UI Check works in the output of one particular instance (similar to interactive
            // preview).
            // When enabled, UI Check will generate here a number of previews in different reference
            // devices so, when filterPreviewInstances is called and uiCheck filter is Enabled, for
            // 1 preview, multiple will be returned.
            availableGroupsFlow.value = uiCheckFilter.filterGroups(allGroups)
            uiCheckFilter.filterPreviewInstances(filteredPreviews)
          }
          .collectLatest { filteredPreviewElementsInstancesFlow.value = it }
      }

      // Trigger refreshes on available previews changes
      launch(workerThread) {
        // When "subscribing" to the changes of a MutableStateFlow like this one, it will always
        // immediately collect an initial value, but we want to ignore this first value in order
        // to avoid invalidating files in every tab change.
        val isInitialValue = AtomicBoolean(true)
        filteredPreviewElementsInstancesFlow
          .filter {
            return@filter if (
              it.isEmpty() && previewModeManager.mode.value is PreviewMode.UiCheck
            ) {
              // If there are no previews for UI Check mode, then the original composable
              // was renamed or removed. We should quit UI Check mode and filter out this
              // value from the flow
              restorePreviousMode()
              isInitialValue.set(false)
              false
            } else !isInitialValue.getAndSet(false)
          }
          .collectLatest {
            invalidate()
            requestRefresh()
          }
      }

      // Flow to collate and process refreshNotificationsAndVisibilityFlow requests.
      launch(workerThread) {
        refreshNotificationsAndVisibilityFlow.conflate().collect {
          refreshNotificationsAndVisibilityFlow
            .resetReplayCache() // Do not keep re-playing after we have received the element.
          log.debug("refreshNotificationsAndVisibilityFlow, request=$it")
          updateVisibilityAndNotifications()
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
            ProjectStatus.NotReady,
            ProjectStatus.NeedsBuild,
            ProjectStatus.Building -> updateVisibilityAndNotifications()
            else -> requestRefresh()
          }
        }
      }

      // Flow handling file changes and syntax error changes.
      launch(workerThread) {
        merge(
            psiFileChangeFlow(project, this@launch)
              // filter only for the file we care about
              .filter { it.language == KotlinLanguage.INSTANCE }
              .onEach {
                // Invalidate the preview to detect for changes in any annotation even in
                // other files as long as they are Kotlin.
                // We do not refresh at this point. If the change is in the preview file
                // currently
                // opened, the change flow below will
                // detect the modification and trigger a refresh if needed.
                invalidate()
              }
              .debounce {
                // The debounce timer is smaller when running with Fast Preview so the changes
                // are more responsive to typing.
                if (isFastPreviewAvailable(project)) 250L else 1000L
              },
            syntaxErrorFlow(project, disposable, log, null)
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
                isFastPreviewAvailable(project) &&
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
              isFastPreviewAvailable(project) &&
                psiCodeFileChangeDetectorService.outOfDateKtFiles.isNotEmpty()
            ) {
              try {
                requestFastPreviewRefresh()
                return@collect
              } catch (_: Throwable) {
                // Ignore any cancellation exceptions
              }
            }

            if (
              !EssentialsMode.isEnabled() &&
                previewModeManager.mode.value !is PreviewMode.Interactive &&
                previewModeManager.mode.value !is PreviewMode.AnimationInspection &&
                !ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
            )
              requestRefresh()
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
