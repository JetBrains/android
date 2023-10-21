/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.compose.ComposePreviewElementsModel
import com.android.tools.idea.compose.preview.PreviewGroup.Companion.namedGroup
import com.android.tools.preview.ComposePreviewElement
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ComposePreviewElementsModelTest {
  @Test
  fun testPreviewFilters(): Unit = runBlocking {
    val basePreviewElement =
      SingleComposePreviewElementInstance.forTesting("Template", groupName = "TemplateGroup")

    // Fake PreviewElementTemplate that generates a couple of instances
    val template =
      object : ComposePreviewElement by basePreviewElement {
        private val templateInstances =
          listOf(
            SingleComposePreviewElementInstance.forTesting(
              "Instance1",
              groupName = "TemplateGroup"
            ),
            SingleComposePreviewElementInstance.forTesting("Instance2", groupName = "TemplateGroup")
          )

        override fun resolve(): Sequence<ComposePreviewElementInstance> =
          templateInstances.asSequence()
      }
    val allPreviews =
      listOf(
        SingleComposePreviewElementInstance.forTesting("PreviewMethod1", groupName = "GroupA"),
        SingleComposePreviewElementInstance.forTesting("SeparatePreview", groupName = "GroupA"),
        SingleComposePreviewElementInstance.forTesting("PreviewMethod2", groupName = "GroupB"),
        SingleComposePreviewElementInstance.forTesting("AMethod"),
        template
      )

    // Initialize flows
    val filterFlow =
      MutableStateFlow<ComposePreviewElementsModel.Filter>(
        ComposePreviewElementsModel.Filter.Disabled
      )
    val filteredInstancesFlow =
      ComposePreviewElementsModel.filteredPreviewElementsFlow(
        ComposePreviewElementsModel.instantiatedPreviewElementsFlow(MutableStateFlow(allPreviews)),
        filterFlow
      )

    assertThat(filteredInstancesFlow.first().map { it.methodFqn })
      .containsExactly(
        "PreviewMethod1",
        "SeparatePreview",
        "PreviewMethod2",
        "AMethod",
        "Instance1",
        "Instance2"
      )
      .inOrder()

    // Set an instance filter
    filterFlow.value =
      ComposePreviewElementsModel.Filter.Single(
        allPreviews.first() as SingleComposePreviewElementInstance
      )
    assertThat(filteredInstancesFlow.first().map { it.methodFqn }).containsExactly("PreviewMethod1")

    // Set the group filter
    filterFlow.value = ComposePreviewElementsModel.Filter.Group(namedGroup("GroupA"))
    assertThat(filteredInstancesFlow.first().map { it.methodFqn })
      .containsExactly("PreviewMethod1", "SeparatePreview")
      .inOrder()

    // Remove instance filter
    filterFlow.value = ComposePreviewElementsModel.Filter.Disabled
    assertThat(filteredInstancesFlow.first().map { it.methodFqn })
      .containsExactly(
        "PreviewMethod1",
        "SeparatePreview",
        "PreviewMethod2",
        "AMethod",
        "Instance1",
        "Instance2"
      )
      .inOrder()

    // This should filter and keep the group
    filterFlow.value = ComposePreviewElementsModel.Filter.Group(namedGroup("GroupA"))
    assertThat(
        filteredInstancesFlow.first().map { "${it.instanceId} (${it.displaySettings.group})" }
      )
      .containsExactly(
        "PreviewMethod1 (GroupA)",
        "SeparatePreview (GroupA)",
      )
      .inOrder()
  }

  @Test
  fun instanceFilterIsApplied(): Unit = runBlocking {
    val previewElement = SingleComposePreviewElementInstance.forTesting("A1", groupName = "GroupA")
    val allPreviews =
      listOf(
        previewElement,
        SingleComposePreviewElementInstance.forTesting("A2", groupName = "GroupA"),
        SingleComposePreviewElementInstance.forTesting("B1", groupName = "GroupB"),
        SingleComposePreviewElementInstance.forTesting("C1", groupName = "GroupC")
      )

    // Initialize flows
    val filterFlow =
      MutableStateFlow<ComposePreviewElementsModel.Filter>(
        ComposePreviewElementsModel.Filter.Disabled
      )
    val filteredInstancesFlow =
      ComposePreviewElementsModel.filteredPreviewElementsFlow(
        MutableStateFlow(allPreviews),
        filterFlow
      )

    filterFlow.value = ComposePreviewElementsModel.Filter.Single(previewElement)
    assertThat(filteredInstancesFlow.first()).containsExactly(previewElement)
  }
}
