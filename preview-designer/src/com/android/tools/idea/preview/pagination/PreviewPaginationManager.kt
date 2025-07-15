/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.preview.pagination

import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.intellij.openapi.actionSystem.DataKey

const val DEFAULT_PAGE_SIZE = 50

/** Interface used for [PreviewRepresentation]s that support paginating its previews. */
interface PreviewPaginationManager {
  /** Number of previews to be shown in each page. */
  var pageSize: Int

  /** The number (0-indexed) of the currently selected page. */
  var selectedPage: Int

  /**
   * Returns the total number of currently available pages, or null if unknown yet due to some
   * initializations still running.
   */
  fun getTotalPages(): Int?

  /**
   * Returns the total number of elements available across all pages, or null if unknown yet due to
   * some initializations still running.
   */
  fun getTotalElements(): Int?

  companion object {
    val KEY = DataKey.create<PreviewPaginationManager>("PreviewPaginationManager")
  }
}
