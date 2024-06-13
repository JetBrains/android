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
import com.android.tools.idea.preview.groups.PreviewGroupManager
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface used for [PreviewRepresentation]s to manage flows of [PreviewElement]s. It allows
 * retrieving all preview elements available for a [PreviewRepresentation], as well as the same
 * elements but filtered, to be used for rendering purposes.
 *
 * @see [FlowableCollection]
 */
interface PreviewFlowManager<T : PreviewElement<*>> : PreviewGroupManager {
  /** Flow containing all the available [T]s for this manager. */
  val allPreviewElementsFlow: StateFlow<FlowableCollection<T>>

  /** Flow containing the filtered [T]s from [allPreviewElementsFlow]. */
  val filteredPreviewElementsFlow: StateFlow<FlowableCollection<T>>

  /**
   * Selects a single [T] preview element. If the value is non-null, then
   * [filteredPreviewElementsFlow] will be a flow of a singleton containing that preview element. If
   * the value is null, then the single filter is removed.
   */
  fun setSingleFilter(previewElement: T?)

  companion object {
    val KEY = DataKey.create<PreviewFlowManager<*>>("PreviewFlowManager")
  }
}
