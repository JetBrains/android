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

import com.android.tools.idea.compose.preview.PreviewGroup.Companion.ALL_PREVIEW_GROUP
import com.android.tools.idea.compose.preview.PreviewGroup.Companion.namedGroup
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.android.tools.idea.compose.preview.util.PreviewElementTemplate
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Utility method to get the array of Composable FQN from a given [PreviewElementProvider].
 */
private fun PreviewElementProvider.previewNamesArray(): Array<String> =
  previewElements.map { it.composableMethodFqn }.toList().toTypedArray()

/**
 * Utility method to get the array of names of the available groups. See [GroupNameFilteredPreviewProvider#allAvailableGroups].
 */
private fun PreviewFilters.allAvailableGroupNamesArray(): Array<String> =
  allAvailableGroups.mapNotNull { it.name }.toTypedArray()

/**
 * Utility method to get the array of names of the available groups after filtering.
 */
private fun PreviewFilters.filteredGroupNamesArray(): Array<String> =
  previewElements.groupNames.toTypedArray()

class PreviewFiltersTest {
  @Test
  fun testPreviewFilters() {
    val basePreviewElement =
      SinglePreviewElementInstance.forTesting("Template", groupName = "TemplateGroup")

    // Fake PreviewElementTemplate that generates a couple of instances
    val template = object: PreviewElementTemplate, PreviewElement by basePreviewElement {
      private val templateInstances = listOf(
        SinglePreviewElementInstance.forTesting("Instance1", groupName = "TemplateGroup"),
        SinglePreviewElementInstance.forTesting("Instance2", groupName = "TemplateGroup"))

      override fun instances(): Sequence<PreviewElementInstance> = templateInstances.asSequence()
    }
    val staticPreviewProvider = StaticPreviewProvider(listOf(
      SinglePreviewElementInstance.forTesting("PreviewMethod1", groupName = "GroupA"),
      SinglePreviewElementInstance.forTesting("SeparatePreview", groupName = "GroupA"),
      SinglePreviewElementInstance.forTesting("PreviewMethod2", groupName = "GroupB"),
      SinglePreviewElementInstance.forTesting("AMethod"),
      template
    ))

    val previewFilters = PreviewFilters(staticPreviewProvider)
    assertArrayEquals(arrayOf("GroupA", "GroupB", "TemplateGroup"), previewFilters.filteredGroupNamesArray())

    // Set an instance filter
    assertArrayEquals(arrayOf("PreviewMethod1", "SeparatePreview", "PreviewMethod2", "AMethod", "Instance1", "Instance2"),
                      previewFilters.previewNamesArray())
    previewFilters.instanceFilter = (staticPreviewProvider.previewElements.first() as SinglePreviewElementInstance)
    assertEquals("PreviewMethod1", previewFilters.previewElements.single().instanceId)
    assertArrayEquals(arrayOf("GroupA"), previewFilters.filteredGroupNamesArray())

    // Set the group filter
    previewFilters.groupNameFilter = PreviewGroup.namedGroup("GroupA")
    assertEquals("PreviewMethod1", previewFilters.previewElements.single().instanceId)
    // Setting the ALL_PREVIEW_GROUP should keep the instance filter
    previewFilters.groupNameFilter = ALL_PREVIEW_GROUP
    assertEquals("PreviewMethod1", previewFilters.previewElements.single().instanceId)

    // Remove instance filter
    previewFilters.instanceFilter = null
    assertArrayEquals(arrayOf("PreviewMethod1", "SeparatePreview", "PreviewMethod2", "AMethod", "Instance1", "Instance2"),
                      previewFilters.previewNamesArray())

    // Now filter a group
    previewFilters.groupNameFilter = PreviewGroup.namedGroup("GroupA")
    assertArrayEquals(arrayOf("PreviewMethod1", "SeparatePreview"), previewFilters.previewNamesArray())
    previewFilters.instanceFilter =
      (staticPreviewProvider.previewElements.first { it.composableMethodFqn == "SeparatePreview" } as SinglePreviewElementInstance)
    // This should filter and keep the group
    assertEquals("SeparatePreview", previewFilters.previewElements.single().instanceId)
    assertEquals("GroupA", previewFilters.groupNameFilter.name)
    previewFilters.instanceFilter = null
    assertEquals("GroupA", previewFilters.groupNameFilter.name)
    assertArrayEquals(arrayOf("PreviewMethod1", "SeparatePreview"), previewFilters.previewNamesArray())
  }

  /**
   * Regression test for b/158038420.
   */
  @Test
  fun `when a group filter is applied, availableGroups still contains all the options`() {
    val previewFilters = PreviewFilters(StaticPreviewProvider(listOf(
      SinglePreviewElementInstance.forTesting("A1", groupName = "GroupA"),
      SinglePreviewElementInstance.forTesting("A2", groupName = "GroupA"),
      SinglePreviewElementInstance.forTesting("B1", groupName = "GroupB"),
      SinglePreviewElementInstance.forTesting("C1", groupName = "GroupC")
    )))

    assertArrayEquals(arrayOf("GroupA", "GroupB", "GroupC"), previewFilters.allAvailableGroupNamesArray())
    previewFilters.groupNameFilter = namedGroup("GroupA")
    assertArrayEquals(arrayOf("GroupA", "GroupB", "GroupC"), previewFilters.allAvailableGroupNamesArray())
    assertArrayEquals(arrayOf("GroupA"), previewFilters.filteredGroupNamesArray())
  }
}