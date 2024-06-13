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
package com.android.tools.idea.preview.gallery

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.preview.gallery.GalleryView.Companion.truncate
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class GalleryViewTest {
  @get:Rule val rule = RuleChain(AndroidProjectRule.inMemory(), EdtRule())

  private var rootComponent = JPanel(BorderLayout())

  private data class TestKey(override val title: String) : TitledKey

  @Test
  fun `first key is selected`() {
    val selected = TestKey("First")
    val keys = setOf(TestKey("First"), TestKey("Second"), TestKey("Third"))
    val gallery = GalleryView(rootComponent, { selected }, { keys }, { _, _ -> })
    FakeUi(gallery)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey("First"), gallery.selectedKey)
  }

  @Test
  fun `second key is selected`() {
    val selected = TestKey("Second")
    val keys = setOf(TestKey("First"), TestKey("Second"), TestKey("Third"))
    val gallery = GalleryView(rootComponent, { selected }, { keys }, { _, _ -> })
    FakeUi(gallery)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey("Second"), gallery.selectedKey)
  }

  @Test
  fun `update selected key`() {
    var selected = TestKey("First")
    val providedKeys = setOf(TestKey("First"), TestKey("Second"), TestKey("Third"))
    val gallery = GalleryView(rootComponent, { selected }, { providedKeys }, { _, _ -> })
    selected = TestKey("Second")
    FakeUi(gallery)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey("Second"), gallery.selectedKey)
  }

  @Test
  fun `update provided keys`() {
    val keys = setOf(TestKey("Second"), TestKey("Third"))
    var providedKeys = setOf(TestKey("First")) + keys
    val gallery = GalleryView(rootComponent, { TestKey("Second") }, { providedKeys }, { _, _ -> })
    providedKeys = keys
    FakeUi(gallery)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey("Second"), gallery.selectedKey)
  }

  @Test
  fun `new key is added `() {
    val newKey = TestKey("newKey")
    val selected = TestKey("Selected")
    val providedKeys = mutableSetOf(TestKey("First"), TestKey("Second"), TestKey("Third"))
    val gallery = GalleryView(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    val ui = FakeUi(gallery)
    ui.updateNestedActions()
    assertEquals(3, gallery.testKeys.size)
    providedKeys += newKey
    ui.updateNestedActions()
    assertEquals(4, gallery.testKeys.size)
  }

  @Test
  fun `order correct after update`() {
    val keyOne = TestKey("First")
    val keyTwo = TestKey("Second")
    val keyThree = TestKey("Third")
    var providedKeys = setOf(keyTwo)
    val selected = TestKey("First")
    val gallery = GalleryView(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    providedKeys = setOf(keyOne, keyTwo, keyThree)
    val ui = FakeUi(gallery)
    ui.updateNestedActions()
    val allActions = gallery.testKeys
    assertEquals(3, allActions.size)
    assertEquals("First", allActions[0].title)
    assertEquals("Second", allActions[1].title)
    assertEquals("Third", allActions[2].title)
  }

  @Test
  fun `toolbar is not updated`() {
    val selected = TestKey("First")
    val providedKeys = setOf(TestKey("First"), TestKey("Second"), TestKey("Third"))
    val gallery = GalleryView(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    val ui = FakeUi(gallery)
    UIUtil.dispatchAllInvocationEvents()
    val toolbar = findGalleryView(gallery)
    // Update toolbars
    ui.updateNestedActions()
    val updatedToolbar = findGalleryView(gallery)
    // Toolbar was not updated, it's same as before.
    assertEquals(toolbar, updatedToolbar)
  }

  @Test
  fun `toolbar is updated with new key`() {
    val selected = TestKey("First")
    val providedKeys = mutableSetOf(TestKey("First"), TestKey("Second"), TestKey("Third"))
    val gallery = GalleryView(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    val ui = FakeUi(gallery)
    UIUtil.dispatchAllInvocationEvents()
    val oldActions = gallery.testKeys
    // Set new set of keys.
    providedKeys += TestKey("New")
    ui.updateNestedActions()
    val newActions = gallery.testKeys
    // New toolbar was created.
    assertNotEquals(oldActions, newActions)
  }

  @Test
  fun `toolbar is updated with removed key`() {
    val keyToRemove = TestKey("Key to remove")
    val selected = TestKey("First")
    val providedKeys =
      mutableSetOf(TestKey("First"), TestKey("Second"), TestKey("Third"), keyToRemove)
    val gallery = GalleryView(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    val ui = FakeUi(gallery)
    ui.updateNestedActions()
    val oldActions = gallery.testKeys
    // Set updated set of keys
    providedKeys.remove(keyToRemove)
    ui.updateNestedActions()
    val newActions = gallery.testKeys
    // New toolbar was created.
    assertNotEquals(oldActions, newActions)
  }

  @Ignore("b/345149174")
  @Test
  /**
   * This test is used to verify and preview gallery. It's ignored, so it's only run on demand. See
   * ui.render() to visually verify preview if required - it shows dropdown with first key selected.
   */
  fun `preview gallery`() {
    val selected = TestKey("First")
    val gallery =
      GalleryView(
        rootComponent,
        { selected },
        { setOf(TestKey("First"), TestKey("Second"), TestKey("Third")) },
      ) { _, _ ->
      }
    val root = JPanel(BorderLayout()).apply { size = Dimension(400, 400) }
    root.add(gallery, BorderLayout.NORTH)
    val ui = FakeUi(root)
    ui.updateToolbars()
    ui.render()
  }

  @Test
  fun `empty gallery`() {
    var selected: TestKey? = null
    val gallery =
      GalleryView<TestKey>(rootComponent, { null }, { emptySet() }) { _, key -> selected = key }
    val ui = FakeUi(gallery)
    ui.updateNestedActions()
    assertEquals(0, gallery.testKeys.size)
    assertNull(selected)
  }

  @Test
  fun shortTitle() {
    val result = "shortTitle".truncate()
    assertEquals("shortTitle", result)
  }

  @Test
  fun longTitle() {
    val result = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".truncate()
    assertEquals("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWX...", result)
  }

  private fun FakeUi.updateNestedActions() {
    updateToolbars()
    updateToolbars()
  }
}
