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
package com.android.tools.idea.preview.flow

import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.chunked
import com.android.tools.idea.concurrency.getOrNull
import com.android.tools.idea.concurrency.sizeOrNull
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.pagination.DEFAULT_PAGE_SIZE
import com.android.tools.idea.preview.pagination.PreviewPaginationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Class to paginate a given [inboundFlow] into different pages and keeping the content of the
 * selected page flowing towards the [currentPageFlow].
 */
class PreviewFlowPaginator<T>(private val inboundFlow: StateFlow<FlowableCollection<T>>) :
  PreviewPaginationManager {

  /** State flow containing the current page size. */
  private val pageSizeFlow = MutableStateFlow(DEFAULT_PAGE_SIZE)

  /** State flow containing the index of the selected page */
  private val selectedPageFlow = MutableStateFlow(0)

  private var totalElements: Int? = null

  private var totalPages: Int? = null

  override fun getTotalPages(): Int? = totalPages

  override fun getTotalElements(): Int? = totalElements

  override var pageSize: Int by pageSizeFlow::value

  override var selectedPage: Int by selectedPageFlow::value

  /**
   * Flow containing the content of the given [inboundFlow] split in pages according to the current
   * [pageSize].
   */
  private val pagesFlow =
    inboundFlow
      .combine(pageSizeFlow) { content, pageSize ->
        val pages =
          if (StudioFlags.PREVIEW_PAGINATION.get()) content.chunked(pageSize)
          else content.chunked(maxOf(1, content.sizeOrNull() ?: 1))
        totalElements = content.sizeOrNull()
        totalPages = pages.sizeOrNull()
        // Set selected page to last page if the previously selected one doesn't exist anymore
        selectedPage = selectedPage.coerceIn(0, maxOf(0, (totalPages ?: 1) - 1))
        return@combine pages
      }
      .distinctUntilChanged()
      .conflate()

  /**
   * Flow containing the content of the [inboundFlow] corresponding to the currently [selectedPage].
   */
  val currentPageFlow =
    pagesFlow
      .combine(selectedPageFlow) { pages, index ->
        if (pages is FlowableCollection.Uninitialized) FlowableCollection.Uninitialized
        else
          pages.getOrNull(index)?.let { FlowableCollection.Present(it) }
            ?: FlowableCollection.Present(emptyList())
      }
      .distinctUntilChanged()
      .conflate()
}
