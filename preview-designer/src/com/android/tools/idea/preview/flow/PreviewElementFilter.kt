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

import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.filter
import com.android.tools.idea.preview.groups.PreviewGroup
import com.android.tools.preview.PreviewElement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Filter mode that can be set in the preview. The preview will accept any of these types of filters
 * and will apply it to the input [PreviewElement] from the file.
 */
sealed class PreviewElementFilter<T : PreviewElement<*>> {
  /**
   * Filters the given input [PreviewElement]s using the given [filterGroup] and returns only the
   * ones that belong to that group.
   */
  class Group<T : PreviewElement<*>>(val filterGroup: PreviewGroup.Named) :
    PreviewElementFilter<T>() {
    override fun filter(input: FlowableCollection<T>): FlowableCollection<T> =
      input.filter inner@{
        PreviewGroup.namedGroup(it.displaySettings.group ?: return@inner false) == filterGroup
      }
  }

  /** Filter that selects a single element. */
  data class Single<T : PreviewElement<*>>(val instance: T) : PreviewElementFilter<T>() {
    override fun filter(input: FlowableCollection<T>): FlowableCollection<T> =
      input.filter { it == instance }
  }

  /** Filtering disabled. */
  class Disabled<T : PreviewElement<*>> : PreviewElementFilter<T>() {
    override fun filter(input: FlowableCollection<T>): FlowableCollection<T> = input
  }

  abstract fun filter(input: FlowableCollection<T>): FlowableCollection<T>
}

/** Filters [allPreviewInstancesFlow] using the given [filterFlow]. */
fun <T : PreviewElement<*>> filteredPreviewElementsFlow(
  allPreviewInstancesFlow: Flow<FlowableCollection<T>>,
  filterFlow: Flow<PreviewElementFilter<T>>,
): Flow<FlowableCollection<T>> =
  combine(allPreviewInstancesFlow, filterFlow) { allPreviewInstances, filter ->
    when (allPreviewInstances) {
      is FlowableCollection.Uninitialized -> FlowableCollection.Uninitialized
      is FlowableCollection.Present -> filter.filter(allPreviewInstances)
    }
  }
