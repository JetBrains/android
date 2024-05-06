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
package com.android.tools.idea.compose

import com.android.tools.idea.compose.preview.PreviewGroup
import com.android.tools.preview.ComposePreviewElement
import com.android.tools.preview.ComposePreviewElementInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Class containing all the support methods that provide the model for the [ComposePreviewMananger].
 * These methods are responsible for the flow transformation from the initial
 * [ComposePreviewElement]s in the file to the output [ComposePreviewElementInstance].
 */
object ComposePreviewElementsModel {
  /** Instantiates all the given [ComposePreviewElement] into [ComposePreviewElementInstance]s. */
  fun instantiatedPreviewElementsFlow(
    input: Flow<Collection<ComposePreviewElement>>,
  ): Flow<Collection<ComposePreviewElementInstance>> =
    input.map { inputPreviews -> inputPreviews.flatMap { it.resolve() } }

  /**
   * Filter mode that can be set in the preview. The preview will accept any of these types of
   * filters and will apply it to the input [ComposePreviewElement] from the file.
   */
  sealed class Filter {
    /**
     * Filters the given input [ComposePreviewElementInstance]s using the given [filterGroup] and
     * returns only the ones that belong to that group.
     */
    class Group(val filterGroup: PreviewGroup.Named) : Filter() {
      override fun filter(
        input: Collection<ComposePreviewElementInstance>
      ): Collection<ComposePreviewElementInstance> =
        input.filter inner@{
          PreviewGroup.namedGroup(it.displaySettings.group ?: return@inner false) == filterGroup
        }
    }

    /** Filter that selects a single element. */
    data class Single(val instance: ComposePreviewElementInstance) : Filter() {
      override fun filter(
        input: Collection<ComposePreviewElementInstance>
      ): Collection<ComposePreviewElementInstance> = input.filter { it == instance }
    }

    /** Filtering disabled. */
    object Disabled : Filter() {
      override fun filter(
        input: Collection<ComposePreviewElementInstance>
      ): Collection<ComposePreviewElementInstance> = input
    }

    abstract fun filter(
      input: Collection<ComposePreviewElementInstance>
    ): Collection<ComposePreviewElementInstance>
  }

  /** Filters [allPreviewInstancesFlow] using the given [filterFlow]. */
  fun filteredPreviewElementsFlow(
    allPreviewInstancesFlow: Flow<Collection<ComposePreviewElementInstance>>,
    filterFlow: Flow<Filter>
  ): Flow<Collection<ComposePreviewElementInstance>> =
    combine(allPreviewInstancesFlow, filterFlow) { allPreviewInstances, filter ->
      filter.filter(allPreviewInstances).ifEmpty { allPreviewInstances }
    }
}
