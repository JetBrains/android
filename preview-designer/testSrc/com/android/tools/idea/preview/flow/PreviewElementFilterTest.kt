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
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.preview.TestPreviewElement
import com.android.tools.idea.preview.groups.PreviewGroup
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PreviewElementFilterTest {
  @Test
  fun testPreviewFilters(): Unit = runBlocking {
    val allPreviews =
      listOf(
        TestPreviewElement(methodFqn = "PreviewMethod1", groupName = "GroupA"),
        TestPreviewElement(methodFqn = "SeparatePreview", groupName = "GroupA"),
        TestPreviewElement(methodFqn = "PreviewMethod2", groupName = "GroupB"),
        TestPreviewElement(methodFqn = "AMethod"),
      )

    // Initialize flows
    val filterFlow =
      MutableStateFlow<PreviewElementFilter<TestPreviewElement>>(PreviewElementFilter.Disabled())
    val filteredInstancesFlow =
      filteredPreviewElementsFlow(
        MutableStateFlow(FlowableCollection.Present(allPreviews)),
        filterFlow,
      )

    assertThat(filteredInstancesFlow.first().asCollection().map { it.methodFqn })
      .containsExactly("PreviewMethod1", "SeparatePreview", "PreviewMethod2", "AMethod")
      .inOrder()

    // Set an instance filter
    filterFlow.value = PreviewElementFilter.Single(allPreviews.first())
    assertThat(filteredInstancesFlow.first().asCollection().map { it.methodFqn })
      .containsExactly("PreviewMethod1")

    // Set the group filter
    filterFlow.value = PreviewElementFilter.Group(PreviewGroup.namedGroup("GroupA"))
    assertThat(filteredInstancesFlow.first().asCollection().map { it.methodFqn })
      .containsExactly("PreviewMethod1", "SeparatePreview")
      .inOrder()

    // Remove filter
    filterFlow.value = PreviewElementFilter.Disabled()
    assertThat(filteredInstancesFlow.first().asCollection().map { it.methodFqn })
      .containsExactly("PreviewMethod1", "SeparatePreview", "PreviewMethod2", "AMethod")
      .inOrder()

    // This should filter and keep the group
    filterFlow.value = PreviewElementFilter.Group(PreviewGroup.namedGroup("GroupA"))
    assertThat(
        filteredInstancesFlow.first().asCollection().map {
          "${it.methodFqn} (${it.displaySettings.group})"
        }
      )
      .containsExactly("PreviewMethod1 (GroupA)", "SeparatePreview (GroupA)")
      .inOrder()
  }

  @Test
  fun instanceFilterIsApplied(): Unit = runBlocking {
    val previewElement = TestPreviewElement(methodFqn = "A1", groupName = "GroupA")

    val allPreviews =
      listOf(
        previewElement,
        TestPreviewElement(methodFqn = "A2", groupName = "GroupA"),
        TestPreviewElement(methodFqn = "B1", groupName = "GroupB"),
        TestPreviewElement(methodFqn = "C1", groupName = "GroupC"),
      )

    // Initialize flows
    val filterFlow =
      MutableStateFlow<PreviewElementFilter<TestPreviewElement>>(PreviewElementFilter.Disabled())
    val filteredInstancesFlow =
      filteredPreviewElementsFlow(
        MutableStateFlow(FlowableCollection.Present(allPreviews)),
        filterFlow,
      )

    filterFlow.value = PreviewElementFilter.Single(previewElement)
    assertThat(filteredInstancesFlow.first().asCollection()).containsExactly(previewElement)
  }
}
