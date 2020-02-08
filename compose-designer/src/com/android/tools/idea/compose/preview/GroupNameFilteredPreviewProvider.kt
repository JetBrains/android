/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.google.common.annotations.VisibleForTesting

/**
 * A [PreviewElementProvider] that filters by [PreviewElement.groupName].
 *
 * @param delegate the source [PreviewElementProvider] to be filtered.
 * @param groupName the name of the group that will be used to filter the [PreviewElement]s returned from the [delegate].
 */
@VisibleForTesting
class GroupNameFilteredPreviewProvider(private val delegate: PreviewElementProvider, var groupName: String? = null) :
  PreviewElementProvider {
  private val filteredPreviewElementProvider = FilteredPreviewElementProvider(
    delegate) {
    groupName == null || groupName == it.displaySettings.group
  }

  /**
   * Returns a [Set] with all the available groups in the source [delegate]. Only groups returned can be set on [groupName].
   */
  val availableGroups: Set<String>
    get() = delegate.previewElements.mapNotNull { it.displaySettings.group }.filter { it.isNotBlank() }.toSet()

  override val previewElements: List<PreviewElement>
    get() {
      val filteredList = filteredPreviewElementProvider.previewElements
      return if (filteredList.isEmpty()) {
        delegate.previewElements
      }
      else {
        filteredList
      }
    }
}