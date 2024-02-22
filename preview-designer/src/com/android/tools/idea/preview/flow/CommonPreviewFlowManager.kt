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

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.preview.GroupFilteredPreviewElementProvider
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.groups.PreviewGroup
import com.android.tools.preview.PreviewElement
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Common implementation of a [PreviewFlowManager] of type [T]. The flows do not update themselves.
 * They must be updated by calling [updateFlows]. The [filePreviewElementProvider] parameter must
 * provide all [PreviewElement]s in the file.
 *
 * When a single non-null preview element is set through [setSingleFilter], that element will be
 * returned by [filteredPreviewElementsFlow], otherwise the [groupFilter] will be used for
 * filtering.
 *
 * When setting a new [groupFilter], the [CommonPreviewFlowManager] will request a new refresh by
 * invoking [requestRefresh].
 */
class CommonPreviewFlowManager<T : PreviewElement>(
  private val filePreviewElementProvider: PreviewElementProvider<T>,
  private val requestRefresh: () -> Unit,
) : PreviewFlowManager<T> {

  private val singleElementFlow = MutableStateFlow<T?>(null)

  override val allPreviewElementsFlow =
    MutableStateFlow<FlowableCollection<T>>(FlowableCollection.Uninitialized)

  override val filteredPreviewElementsFlow =
    MutableStateFlow<FlowableCollection<T>>(FlowableCollection.Uninitialized)

  override fun setSingleFilter(previewElement: T?) {
    singleElementFlow.value = previewElement
  }

  override val availableGroupsFlow = MutableStateFlow<Set<PreviewGroup.Named>>(emptySet())

  override var groupFilter: PreviewGroup = PreviewGroup.All
    set(value) {
      field = value
      requestRefresh()
    }

  @Slow
  // TODO(b/326412365): remove this method and make this class update itself autonomously like
  // ComposePreviewFlowManager
  suspend fun updateFlows() {
    allPreviewElementsFlow.value =
      FlowableCollection.Present(filePreviewElementProvider.previewElements().toList())

    availableGroupsFlow.value =
      allPreviewElementsFlow.value
        .asCollection()
        .mapNotNull {
          it.displaySettings.group?.let { groupName -> PreviewGroup.namedGroup(groupName) }
        }
        .toSet()

    filteredPreviewElementsFlow.value =
      FlowableCollection.Present(filteredPreviewElementProvider.previewElements().toList())
  }

  private val groupFilteredPreviewElementProvider =
    GroupFilteredPreviewElementProvider(
      previewGroupManager = this,
      delegate = filePreviewElementProvider,
    )

  private val filteredPreviewElementProvider: PreviewElementProvider<T> =
    object : PreviewElementProvider<T> {
      override suspend fun previewElements(): Sequence<T> {
        return singleElementFlow.value?.let { sequenceOf(it) }
          ?: groupFilteredPreviewElementProvider.previewElements()
      }
    }
}
