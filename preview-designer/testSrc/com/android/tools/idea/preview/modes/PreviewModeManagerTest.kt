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
package com.android.tools.idea.preview.modes

import com.android.tools.idea.preview.TestBasePreviewElement
import com.android.tools.idea.preview.TestPreviewElement
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/**
 * A [TestBasePreviewElement] that uses a string as the type for the "Psi" references like the body
 * or the annotation element. This is useful in test as we can use strings to replace cases where a
 * Psi element would be used.
 */
private typealias TestPreviewElementString = TestBasePreviewElement<String>

class PreviewModeManagerTest {

  @Test
  fun modeStaysTheSame() {
    val selected = TestPreviewElement("Selected")
    val mode = PreviewMode.Focus(selected)
    val newElements = listOf(TestPreviewElement("First"), selected, TestPreviewElement("Second"))
    val previousElements =
      setOf(TestPreviewElement("First"), selected, TestPreviewElement("Selected"))
    val newMode = mode.newMode(newElements = newElements, previousElements = previousElements)
    assertEquals(mode, newMode)
  }

  @Test
  fun modeIsUpdated() {
    val selected = TestPreviewElement("Selected")
    val newSelected = TestPreviewElement("NewSelected")
    val newElements = listOf(newSelected, TestPreviewElement("Second"))
    val previousElements =
      setOf(TestPreviewElement("First"), selected, TestPreviewElement("Selected"))
    val newMode =
      PreviewMode.Focus(selected)
        .newMode(newElements = newElements, previousElements = previousElements)
    assertNotNull(newMode)
    assertEquals(newSelected, newMode.selected)
  }

  @Test
  fun modeSelectionIsNull() {
    val selected = TestPreviewElement("Selected")
    val previousElements =
      setOf(TestPreviewElement("First"), selected, TestPreviewElement("Selected"))
    val newMode =
      PreviewMode.Focus(selected)
        .newMode(newElements = emptyList(), previousElements = previousElements)
    assertNull(newMode.selected)
  }

  @Test
  fun modeIsFound() {
    val selected = TestPreviewElement("Selected")
    val newElements = listOf(selected, TestPreviewElement("First"), TestPreviewElement("Selected"))
    val newMode =
      PreviewMode.Focus(selected).newMode(newElements = newElements, previousElements = emptySet())
    assertEquals(selected, newMode.selected)
  }

  @Test
  fun firstElementSelected() {
    val selected = TestPreviewElement("Selected")
    val mode = PreviewMode.Focus(null)
    val newElements = listOf(selected, TestPreviewElement("First"), TestPreviewElement("Second"))
    val newMode = mode.newMode(newElements = newElements, previousElements = emptySet())
    assertEquals(selected, newMode.selected)
  }

  @Test
  fun whenAllDefinitionsChangePreferSamePreviewElementDefinition() {
    val currentSelected =
      TestPreviewElementString("Selected", previewElementDefinition = "definition_selected")
    val newSelected =
      TestPreviewElementString("New Selected", previewElementDefinition = "definition_selected")
    val mode = PreviewMode.Focus(currentSelected)
    val previousElements =
      listOf(
        TestPreviewElementString("First", previewElementDefinition = "first"),
        TestPreviewElementString("Second", previewElementDefinition = "second"),
        currentSelected,
        TestPreviewElementString("Third", previewElementDefinition = "third"),
      )
    // In the new elements, every definition has changed but the selection retains the same
    // previewElementDefinition too so will be preferred.
    val newElements =
      listOf(
        TestPreviewElementString("First_b", previewElementDefinition = "first"),
        newSelected,
        TestPreviewElementString("Second_b", previewElementDefinition = "second"),
        TestPreviewElementString("Third_b", previewElementDefinition = "third"),
      )
    val newMode = mode.newMode(newElements = newElements, previousElements = emptySet())
    assertEquals(newSelected, newMode.selected)
  }
}
