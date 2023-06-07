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
package com.android.tools.idea.compose.preview.lite

import com.android.tools.idea.compose.preview.SingleComposePreviewElementInstance
import com.android.tools.idea.testing.AndroidProjectRule
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComposeGalleryTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val element1 =
    SingleComposePreviewElementInstance.forTesting("PreviewMethod1", groupName = "GroupA")
  private val element2 =
    SingleComposePreviewElementInstance.forTesting("PreviewMethod2", groupName = "GroupA")
  private val element3 =
    SingleComposePreviewElementInstance.forTesting("PreviewMethod3", groupName = "GroupB")

  @Test
  fun selectedComponent() {
    var refreshNeededCalls = 0
    val gallery = ComposeGallery(JPanel(), JPanel()) { refreshNeededCalls++ }
    val selected = gallery.updateAndGetSelected(sequenceOf(element1, element2, element3))
    assertEquals(element1, selected)
    assertEquals(1, refreshNeededCalls)
  }

  @Test
  fun selectedComponentRemoved() {
    var refreshNeededCalls = 0
    val gallery = ComposeGallery(JPanel(), JPanel()) { refreshNeededCalls++ }
    var selected = gallery.updateAndGetSelected(sequenceOf(element1, element2, element3))
    assertEquals(element1, selected)
    assertEquals(1, refreshNeededCalls)
    selected = gallery.updateAndGetSelected(sequenceOf(element2, element3))
    assertEquals(element2, selected)
    assertEquals(2, refreshNeededCalls)
  }

  @Test
  fun sameSequenceSet() {
    var refreshNeededCalls = 0
    val gallery = ComposeGallery(JPanel(), JPanel()) { refreshNeededCalls++ }
    var selected = gallery.updateAndGetSelected(sequenceOf(element1, element2, element3))
    assertEquals(element1, selected)
    assertEquals(1, refreshNeededCalls)
    selected = gallery.updateAndGetSelected(sequenceOf(element1, element2, element3))
    assertEquals(element1, selected)
    // No refresh requested.
    assertEquals(1, refreshNeededCalls)
  }
}
