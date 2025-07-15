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

import com.android.flags.junit.FlagRule
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.flags.StudioFlags
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PreviewFlowPaginatorTest {
  @get:Rule val flagRule = FlagRule(StudioFlags.PREVIEW_PAGINATION, true)

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testPageSizeChange() = runTest {
    val content: MutableStateFlow<FlowableCollection<Int>> =
      MutableStateFlow(FlowableCollection.Uninitialized)
    val currentPageContent: MutableStateFlow<FlowableCollection<Int>> =
      MutableStateFlow(FlowableCollection.Uninitialized)
    val previewFlowPaginator = PreviewFlowPaginator(content)

    // backgroundScope is used because this test is not testing the lifecycle of the flow nor
    // its collection. Instead, it only tests its correctness.
    // See backgroundScope documentation for more details.
    backgroundScope.launch {
      previewFlowPaginator.currentPageFlow.collectLatest { currentPageContent.value = it }
    }

    content.value = FlowableCollection.Present(listOf(1, 2, 3, 4, 5))

    previewFlowPaginator.pageSize = 1
    // runCurrent is used instead of advanceUntilIdle() because the scheduler is considered idle
    // if no foreground tasks are pending (i.e. tasks in the backgroundScope don't affect).
    // See backgroundScope documentation for more details.
    runCurrent()
    assertEquals(listOf(1), currentPageContent.value.asCollection())

    previewFlowPaginator.pageSize = 2
    runCurrent()
    assertEquals(listOf(1, 2), currentPageContent.value.asCollection())

    previewFlowPaginator.pageSize = 3
    runCurrent()
    assertEquals(listOf(1, 2, 3), currentPageContent.value.asCollection())

    previewFlowPaginator.pageSize = 4
    runCurrent()
    assertEquals(listOf(1, 2, 3, 4), currentPageContent.value.asCollection())

    previewFlowPaginator.pageSize = 5
    runCurrent()
    assertEquals(listOf(1, 2, 3, 4, 5), currentPageContent.value.asCollection())

    previewFlowPaginator.pageSize = 6
    runCurrent()
    assertEquals(listOf(1, 2, 3, 4, 5), currentPageContent.value.asCollection())
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testSelectedPageChange() = runTest {
    val content: MutableStateFlow<FlowableCollection<Int>> =
      MutableStateFlow(FlowableCollection.Uninitialized)
    val currentPageContent: MutableStateFlow<FlowableCollection<Int>> =
      MutableStateFlow(FlowableCollection.Uninitialized)
    val previewFlowPaginator = PreviewFlowPaginator(content)
    backgroundScope.launch {
      previewFlowPaginator.currentPageFlow.collectLatest { currentPageContent.value = it }
    }
    content.value = FlowableCollection.Present(listOf(1, 2, 3, 4, 5))

    previewFlowPaginator.pageSize = 1
    runCurrent()
    assertEquals(listOf(1), currentPageContent.value.asCollection())

    previewFlowPaginator.selectedPage = 4
    runCurrent()
    assertEquals(listOf(5), currentPageContent.value.asCollection())

    previewFlowPaginator.selectedPage = 3
    runCurrent()
    assertEquals(listOf(4), currentPageContent.value.asCollection())

    previewFlowPaginator.selectedPage = 2
    runCurrent()
    assertEquals(listOf(3), currentPageContent.value.asCollection())

    previewFlowPaginator.selectedPage = 1
    runCurrent()
    assertEquals(listOf(2), currentPageContent.value.asCollection())

    previewFlowPaginator.selectedPage = 0
    runCurrent()
    assertEquals(listOf(1), currentPageContent.value.asCollection())
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testSelectedPageChangesAutomaticallyWhenPageDisappears() = runTest {
    val content: MutableStateFlow<FlowableCollection<Int>> =
      MutableStateFlow(FlowableCollection.Uninitialized)
    val currentPageContent: MutableStateFlow<FlowableCollection<Int>> =
      MutableStateFlow(FlowableCollection.Uninitialized)
    val previewFlowPaginator = PreviewFlowPaginator(content)
    backgroundScope.launch {
      previewFlowPaginator.currentPageFlow.collectLatest { currentPageContent.value = it }
    }
    content.value = FlowableCollection.Present(listOf(1, 2, 3, 4, 5))

    previewFlowPaginator.pageSize = 1
    previewFlowPaginator.selectedPage = 3
    runCurrent()
    assertEquals(listOf(4), currentPageContent.value.asCollection())

    previewFlowPaginator.pageSize = 3
    runCurrent()
    assertEquals(listOf(4, 5), currentPageContent.value.asCollection())
    assertEquals(1, previewFlowPaginator.selectedPage)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testContentChange() = runTest {
    val content: MutableStateFlow<FlowableCollection<Int>> =
      MutableStateFlow(FlowableCollection.Uninitialized)
    val currentPageContent: MutableStateFlow<FlowableCollection<Int>> =
      MutableStateFlow(FlowableCollection.Uninitialized)
    val previewFlowPaginator = PreviewFlowPaginator(content)
    backgroundScope.launch {
      previewFlowPaginator.currentPageFlow.collectLatest { currentPageContent.value = it }
    }

    content.value = FlowableCollection.Present(emptyList())

    previewFlowPaginator.pageSize = 3
    content.value = FlowableCollection.Present(listOf(1, 2, 3))
    runCurrent()
    assertEquals(listOf(1, 2, 3), currentPageContent.value.asCollection())

    content.value = FlowableCollection.Present(listOf(4, 3, 2, 1))
    runCurrent()
    assertEquals(listOf(4, 3, 2), currentPageContent.value.asCollection())

    content.value = FlowableCollection.Present(listOf(5))
    runCurrent()
    assertEquals(listOf(5), currentPageContent.value.asCollection())

    content.value = FlowableCollection.Present(listOf(123, 4, 56, 789, 1011, 12, 13))
    runCurrent()
    assertEquals(listOf(123, 4, 56), currentPageContent.value.asCollection())
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testEmptyAndUninitializedAreDifferent() = runTest {
    val content: MutableStateFlow<FlowableCollection<Int>> =
      MutableStateFlow(FlowableCollection.Uninitialized)
    val currentPageContent: MutableStateFlow<FlowableCollection<Int>> =
      MutableStateFlow(FlowableCollection.Uninitialized)
    val previewFlowPaginator = PreviewFlowPaginator(content)
    backgroundScope.launch {
      previewFlowPaginator.currentPageFlow.collectLatest { currentPageContent.value = it }
    }

    runCurrent()
    assertEquals(FlowableCollection.Uninitialized, currentPageContent.value)

    content.value = FlowableCollection.Present(emptyList())
    runCurrent()
    assertNotEquals(FlowableCollection.Uninitialized, currentPageContent.value)

    content.value = FlowableCollection.Uninitialized
    runCurrent()
    assertEquals(FlowableCollection.Uninitialized, currentPageContent.value)
  }
}
