/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.flow

import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.PsiPreviewElement
import com.android.tools.idea.preview.PsiPreviewElementInstance
import com.android.tools.idea.preview.groups.PreviewGroup
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.sortByDisplayAndSourcePosition
import com.android.tools.idea.preview.uicheck.UiCheckModeFilter
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Common implementation of a [PreviewFlowManager] of type [T]. The method [initializeFlows] must be
 * invoked for the flows to update themselves and to request refreshes. Refreshes will be requested
 * whenever a file in the project changes and the resulting previews are different from those in
 * [renderedPreviewElementsFlow].
 *
 * When a single non-null preview element is set through [setSingleFilter], that element will be
 * returned by [filteredPreviewElementsFlow], otherwise the [groupFilter] will be used for
 * filtering.
 */
class CommonPreviewFlowManager<T : PsiPreviewElementInstance>(
  private val renderedPreviewElementsFlow: StateFlow<FlowableCollection<T>>
) : PreviewFlowManager<T> {

  /**
   * Flow containing all the [PsiPreviewElementInstance]s available in the current file. This flow
   * is only updated when the Preview representation is active.
   */
  override val allPreviewElementsFlow =
    MutableStateFlow<FlowableCollection<T>>(FlowableCollection.Uninitialized)

  override val availableGroupsFlow: MutableStateFlow<Set<PreviewGroup.Named>> =
    MutableStateFlow(emptySet())

  override var groupFilter: PreviewGroup
    get() = getCurrentFilterAsGroup()?.filterGroup ?: PreviewGroup.All
    set(group) {
      val currentFilter = filterFlow.value
      // We can only apply a group filter if no filter existed before or if the current one is
      // already a group filter.
      val canApplyGroupFilter =
        currentFilter is PreviewElementFilter.Disabled<T> ||
          currentFilter is PreviewElementFilter.Group<T>
      filterFlow.value =
        if (group is PreviewGroup.Named && canApplyGroupFilter) {
          PreviewElementFilter.Group(group)
        } else {
          PreviewElementFilter.Disabled()
        }
    }

  /**
   * Flow containing all the [PsiPreviewElementInstance]s available in the current file to be
   * rendered. These are all the previews in [allPreviewElementsFlow] filtered using [filterFlow].
   * This flow is only updated when the Preview representation is active.
   */
  override val filteredPreviewElementsFlow =
    MutableStateFlow<FlowableCollection<T>>(FlowableCollection.Uninitialized)

  /**
   * Current filter being applied to the preview. The filter allows to select one element or a group
   * of them.
   */
  private val filterFlow: MutableStateFlow<PreviewElementFilter<T>> =
    MutableStateFlow(PreviewElementFilter.Disabled())

  /**
   * Preview element provider corresponding to the current state of the Preview. Different modes
   * might require a different provider to be set, e.g. UI check mode needs a provider that produces
   * previews with reference devices. When exiting the mode and returning to static preview, the
   * element provider should be reset to the default [PreviewElementProvider].
   */
  val uiCheckFilterFlow = MutableStateFlow<UiCheckModeFilter<T>>(UiCheckModeFilter.Disabled())

  /**
   * Filter that can be applied to select a single instance. Setting this filter will trigger a
   * refresh.
   */
  override fun setSingleFilter(previewElement: T?) {
    filterFlow.value =
      if (previewElement != null) {
        PreviewElementFilter.Single(previewElement)
      } else {
        PreviewElementFilter.Disabled()
      }
  }

  /**
   * Gets the current value of [filterFlow] as a [PreviewElementFilter.Group] or null if the current
   * filter is of another type.
   */
  fun getCurrentFilterAsGroup(): PreviewElementFilter.Group<T>? =
    filterFlow.value as? PreviewElementFilter.Group<T>

  /** Initializes the flows that will listen to different events and will call [requestRefresh]. */
  fun <K : PsiPreviewElement> CoroutineScope.initializeFlows(
    previewModeManager: PreviewModeManager,
    psiFilePointer: SmartPsiElementPointer<PsiFile>,
    invalidate: () -> Unit,
    requestRefresh: () -> Unit,
    restorePreviousMode: () -> Unit,
    previewElementProvider: PreviewElementProvider<K>,
    toInstantiatedPreviewElementsFlow: (Flow<FlowableCollection<K>>) -> Flow<FlowableCollection<T>>,
  ) {
    with(this@initializeFlows) {
      val project = psiFilePointer.project
      launch(workerThread) {
        // Launch all the listeners that are bound to the current activation.
        previewElementsOnFileChangesFlow(project) { previewElementProvider }
          .map {
            when (it) {
              is FlowableCollection.Uninitialized -> FlowableCollection.Uninitialized
              is FlowableCollection.Present ->
                FlowableCollection.Present(it.collection.sortByDisplayAndSourcePosition())
            }
          }
          .let { toInstantiatedPreviewElementsFlow(it) }
          .collectLatest {
            val previousElements = allPreviewElementsFlow.value
            allPreviewElementsFlow.value = it
            (previewModeManager.mode.value as? PreviewMode.Gallery)?.let { oldMode ->
              oldMode
                .newMode(it.asCollection().toSet(), previousElements.asCollection().toSet())
                .let { newMode -> previewModeManager.setMode(newMode) }
            }
          }
      }

      launch(workerThread) {
        val filteredPreviewsFlow = filteredPreviewElementsFlow(allPreviewElementsFlow, filterFlow)

        // Flow for Preview changes
        combine(allPreviewElementsFlow, filteredPreviewsFlow, uiCheckFilterFlow) {
            allAvailablePreviews,
            filteredPreviews,
            uiCheckFilter ->
            // Calculate groups
            val allGroups =
              allAvailablePreviews
                .asCollection()
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
          .collectLatest { filteredPreviewElementsFlow.value = it }
      }

      // Trigger refreshes on available previews changes
      launch(workerThread) {
        filteredPreviewElementsFlow
          .filter {
            return@filter when (it) {
              is FlowableCollection.Uninitialized -> false
              is FlowableCollection.Present ->
                if (
                  it.collection.isEmpty() && previewModeManager.mode.value is PreviewMode.UiCheck
                ) {
                  // If there are no previews for UI Check mode, then the original composable
                  // was renamed or removed. We should quit UI Check mode and filter out this
                  // value from the flow
                  restorePreviousMode()
                  false
                } else true
            }
          }
          .filter {
            // Filter the render if the rendered previews are already the same. This prevents
            // the preview from refreshing again when the user is just switching back and forth
            // the tab.
            renderedPreviewElementsFlow.value != it
          }
          .collectLatest {
            invalidate()
            requestRefresh()
          }
      }
    }
  }
}
