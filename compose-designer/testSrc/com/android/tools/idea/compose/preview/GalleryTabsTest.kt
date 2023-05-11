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
package com.android.tools.idea.compose.preview

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.util.stream.Collectors
import javax.swing.JPanel
import kotlin.test.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class GalleryTabsTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private var rootComponent = JPanel(BorderLayout())

  private class TestKey(override val title: String) : TitledKey

  @Test
  fun `first tab is selected`() {
    invokeAndWaitIfNeeded {
      val keys = setOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab"))
      val tabs = GalleryTabs(rootComponent, keys) {}
      assertEquals(keys.first(), tabs.selectedKey)
    }
  }

  @Test
  fun `second tab is selected if first removed`() {
    invokeAndWaitIfNeeded {
      val keys = setOf(TestKey("Second Tab"), TestKey("Third Tab"))
      val tabs = GalleryTabs(rootComponent, setOf(TestKey("First Tab")) + keys) {}
      tabs.updateKeys(keys)
      assertEquals(keys.first(), tabs.selectedKey)
    }
  }

  @Test
  fun `new tab is added `() {
    invokeAndWaitIfNeeded {
      val newTab = TestKey("newTab")
      val keys = setOf(TestKey("Tab"), TestKey("Tab2"), TestKey("Tab3"))
      val tabs = GalleryTabs(rootComponent, keys) {}
      val ui = FakeUi(tabs).apply { updateToolbars() }
      assertEquals(3, findAllActionButtons(tabs).size)
      tabs.updateKeys(keys + newTab)
      ui.updateToolbars()
      assertEquals(4, findAllActionButtons(tabs).size)
    }
  }

  @Test
  fun `duplicates are not added`() {
    invokeAndWaitIfNeeded {
      val duplicate = TestKey("duplicate")
      val keys = setOf(TestKey("Tab"), duplicate, duplicate, duplicate, duplicate, duplicate)
      val tabs = GalleryTabs(rootComponent, keys) {}
      assertEquals(keys.first(), tabs.selectedKey)

      FakeUi(tabs).apply { updateToolbars() }
      assertEquals(2, findAllActionButtons(tabs).size)
    }
  }

  @Ignore
  @Test
  /**
   * This test is used to verify and preview the tabs. It's ignored, so it's only run on demand. See
   * ui.render() to visually verify preview if required - it shows three tabs with first tab
   * selected.
   */
  fun `preview tabs`() {
    invokeAndWaitIfNeeded {
      val tabs =
        GalleryTabs(
          rootComponent,
          setOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab"))
        ) {}
      val root = JPanel(BorderLayout()).apply { size = Dimension(400, 400) }
      root.add(tabs, BorderLayout.NORTH)
      val ui = FakeUi(root)
      ui.updateToolbars()
      ui.layout()
      ui.render()
    }
  }

  @Test
  fun `click on tabs`() {
    invokeAndWaitIfNeeded {
      var selectedTab: TestKey? = null
      val tabs =
        GalleryTabs(
          rootComponent,
          setOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab"))
        ) {
          selectedTab = it
        }
      val root = JPanel(BorderLayout()).apply { size = Dimension(400, 400) }
      root.add(tabs, BorderLayout.NORTH)
      val ui =
        FakeUi(root).apply {
          updateToolbars()
          layout()
        }
      val buttons = findAllActionButtons(root)
      assertEquals("First Tab", selectedTab?.title)
      assertEquals("First Tab", tabs.selectedKey?.title)
      ui.clickOn(buttons[1])
      assertEquals("Second Tab", selectedTab?.title)
      assertEquals("Second Tab", tabs.selectedKey?.title)
      ui.clickOn(buttons[2])
      assertEquals("Third Tab", selectedTab?.title)
      assertEquals("Third Tab", tabs.selectedKey?.title)
      ui.clickOn(buttons[0])
      assertEquals("First Tab", selectedTab?.title)
      assertEquals("First Tab", tabs.selectedKey?.title)
    }
  }

  private fun findAllActionButtons(parent: Component): List<ActionButtonWithText> =
    TreeWalker(parent)
      .descendantStream()
      .filter { it is ActionButtonWithText }
      .collect(Collectors.toList())
      .map { it as ActionButtonWithText }
}
