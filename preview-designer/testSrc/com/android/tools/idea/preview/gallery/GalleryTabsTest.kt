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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.preview.gallery.GalleryTabs.Companion.truncate
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import java.util.stream.Collectors
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@Deprecated("b/344884593")
@RunsInEdt
class GalleryTabsTest {
  @get:Rule val rule = RuleChain(AndroidProjectRule.inMemory(), EdtRule())

  private var rootComponent = JPanel(BorderLayout())

  private data class TestKey(override val title: String) : TitledKey

  @Test
  fun `first tab is selected`() {
    val selected = TestKey("First Tab")
    val keys = setOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab"))
    val tabs = GalleryTabs(rootComponent, { selected }, { keys }, { _, _ -> })
    FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey("First Tab"), tabs.selectedKey)
  }

  @Test
  fun `second tab is selected`() {
    val selected = TestKey("Second Tab")
    val keys = setOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab"))
    val tabs = GalleryTabs(rootComponent, { selected }, { keys }, { _, _ -> })
    FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey("Second Tab"), tabs.selectedKey)
  }

  @Test
  fun `update selected tab`() {
    var selected = TestKey("First Tab")
    val providedKeys = setOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab"))
    val tabs = GalleryTabs(rootComponent, { selected }, { providedKeys }, { _, _ -> })
    selected = TestKey("Second Tab")
    FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey("Second Tab"), tabs.selectedKey)
  }

  @Test
  fun `update provided tabs`() {
    val keys = setOf(TestKey("Second Tab"), TestKey("Third Tab"))
    var providedKeys = setOf(TestKey("First Tab")) + keys
    val tabs = GalleryTabs(rootComponent, { TestKey("Second Tab") }, { providedKeys }, { _, _ -> })
    providedKeys = keys
    FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey("Second Tab"), tabs.selectedKey)
  }

  @Test
  fun `new tab is added `() {
    val newTab = TestKey("newTab")
    val selected = TestKey("Tab")
    val providedKeys = mutableSetOf(TestKey("Tab"), TestKey("Tab2"), TestKey("Tab3"))
    val tabs = GalleryTabs(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    // Use a direct executor instead of the default (invokeLater) for replacing the toolbar,
    // so the ActionButtonWithText can be found when using TreeWalker.
    tabs.setUpdateToolbarExecutorForTests(MoreExecutors.directExecutor())
    val ui = FakeUi(tabs)
    ui.updateNestedActions()
    assertEquals(3, findAllActionButtons(tabs).size)
    providedKeys += newTab
    ui.updateNestedActions()
    assertEquals(4, findAllActionButtons(tabs).size)
  }

  @Test
  fun `order correct after update`() {
    val keyOne = TestKey("First")
    val keyTwo = TestKey("Second")
    val keyThree = TestKey("Third")
    var providedKeys = setOf(keyTwo)
    val selected = TestKey("First")
    val tabs = GalleryTabs(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    // Use a direct executor instead of the default (invokeLater) for replacing the toolbar,
    // so the ActionButtonWithText can be found when using TreeWalker.
    tabs.setUpdateToolbarExecutorForTests(MoreExecutors.directExecutor())
    providedKeys = setOf(keyOne, keyTwo, keyThree)
    val ui = FakeUi(tabs)
    ui.updateNestedActions()
    val allActions = findAllActionButtons(tabs)
    assertEquals(3, allActions.size)
    assertEquals("First", allActions[0].presentation.text)
    assertEquals("Second", allActions[1].presentation.text)
    assertEquals("Third", allActions[2].presentation.text)
  }

  @Test
  fun `toolbar is not updated`() {
    val selected = TestKey("First Tab")
    val providedKeys = setOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab"))
    val tabs = GalleryTabs(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    val ui = FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    val toolbar = findGalleryTabs(tabs)
    // Update toolbars
    ui.updateNestedActions()
    val updatedToolbar = findGalleryTabs(tabs)
    // Toolbar was not updated, it's same as before.
    assertEquals(toolbar, updatedToolbar)
  }

  @Test
  fun `toolbar is updated with new key`() {
    val selected = TestKey("First Tab")
    val providedKeys =
      mutableSetOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab"))
    val tabs = GalleryTabs(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    val ui = FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    val toolbar = findGalleryTabs(tabs)
    // Set new set of keys.
    providedKeys += TestKey("New Tab")
    ui.updateNestedActions()
    val updatedToolbar = findGalleryTabs(tabs)
    // New toolbar was created.
    assertNotEquals(toolbar, updatedToolbar)
  }

  @Test
  fun `toolbar is updated with removed key`() {
    val keyToRemove = TestKey("Key to remove")
    val selected = TestKey("First Tab")
    val providedKeys =
      mutableSetOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab"), keyToRemove)
    val tabs = GalleryTabs(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    val ui = FakeUi(tabs)
    ui.updateNestedActions()
    val toolbar = findGalleryTabs(tabs)
    // Set updated set of keys
    providedKeys.remove(keyToRemove)
    ui.updateNestedActions()
    val updatedToolbar = findGalleryTabs(tabs)
    // New toolbar was created.
    assertNotEquals(toolbar, updatedToolbar)
  }

  @Ignore
  @Test
  /**
   * This test is used to verify and preview the tabs. It's ignored, so it's only run on demand. See
   * ui.render() to visually verify preview if required - it shows three tabs with first tab
   * selected.
   */
  fun `preview tabs`() {
    val selected = TestKey("First Tab")
    val tabs =
      GalleryTabs(
        rootComponent,
        { selected },
        { setOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab")) },
      ) { _, _ ->
      }
    val root = JPanel(BorderLayout()).apply { size = Dimension(400, 400) }
    root.add(tabs, BorderLayout.NORTH)
    val ui = FakeUi(root)
    ui.updateToolbars()
    ui.render()
  }

  @Test
  fun `click on tabs`() {
    var selected = TestKey("First Tab")
    val tabs =
      GalleryTabs(
        rootComponent,
        { selected },
        { setOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab")) },
      ) { _, key ->
        selected = key!!
      }
    // Use a direct executor instead of the default (invokeLater) for replacing the toolbar,
    // so the ActionButtonWithText can be found when using TreeWalker.
    tabs.setUpdateToolbarExecutorForTests(MoreExecutors.directExecutor())
    val root = JPanel(BorderLayout()).apply { size = Dimension(400, 400) }
    root.add(tabs, BorderLayout.NORTH)
    val ui = FakeUi(root).apply { updateNestedActions() }
    val buttons = findAllActionButtons(root)
    assertEquals("First Tab", selected.title)
    assertEquals("First Tab", tabs.selectedKey?.title)
    ui.clickOn(buttons[1])
    assertEquals("Second Tab", selected.title)
    assertEquals("Second Tab", tabs.selectedKey?.title)
    ui.clickOn(buttons[2])
    assertEquals("Third Tab", selected.title)
    assertEquals("Third Tab", tabs.selectedKey?.title)
    ui.clickOn(buttons[0])
    assertEquals("First Tab", selected.title)
    assertEquals("First Tab", tabs.selectedKey?.title)
  }

  @Test
  fun `selected tab is always visible`() {
    var selected = TestKey("First Tab")
    val tabs =
      GalleryTabs(
        rootComponent,
        { selected },
        { setOf(TestKey("First Tab"), TestKey("Second Tab"), TestKey("Third Tab")) },
      ) { _, key ->
        selected = key!!
      }
    // Use a direct executor instead of the default (invokeLater) for replacing the toolbar,
    // so the ActionButtonWithText can be found when using TreeWalker.
    tabs.setUpdateToolbarExecutorForTests(MoreExecutors.directExecutor())
    // Width is 100, so only first tab is actually visible.
    val root = JPanel(BorderLayout()).apply { size = Dimension(130, 400) }
    root.add(tabs, BorderLayout.NORTH)

    FakeUi(root).apply { updateNestedActions() }
    val buttons = findAllActionButtons(root)
    val scrollPane = findScrollPane(root)
    // Only first button is visible
    assertTrue(scrollPane.bounds.contains(buttons[0].relativeBounds()))
    assertFalse(scrollPane.bounds.contains(buttons[1].relativeBounds()))
    assertFalse(scrollPane.bounds.contains(buttons[2].relativeBounds()))
    // Only second button is visible
    buttons[1].click()
    assertFalse(scrollPane.bounds.contains(buttons[0].relativeBounds()))
    assertTrue(scrollPane.bounds.contains(buttons[1].relativeBounds()))
    assertFalse(scrollPane.bounds.contains(buttons[2].relativeBounds()))
    // Only third button is visible
    buttons[2].click()
    assertFalse(scrollPane.bounds.contains(buttons[0].relativeBounds()))
    assertFalse(scrollPane.bounds.contains(buttons[1].relativeBounds()))
    assertTrue(scrollPane.bounds.contains(buttons[2].relativeBounds()))
    // Only first button is visible
    buttons[0].click()
    assertTrue(scrollPane.bounds.contains(buttons[0].relativeBounds()))
    assertFalse(scrollPane.bounds.contains(buttons[1].relativeBounds()))
    assertFalse(scrollPane.bounds.contains(buttons[2].relativeBounds()))
  }

  @Test
  fun `empty gallery`() {
    var selected: TestKey? = null
    val tabs =
      GalleryTabs<TestKey>(rootComponent, { null }, { emptySet() }) { _, key -> selected = key }
    // Use a direct executor instead of the default (invokeLater) for replacing the toolbar,
    // so the ActionButtonWithText can be found when using TreeWalker.
    tabs.setUpdateToolbarExecutorForTests(MoreExecutors.directExecutor())
    val ui = FakeUi(tabs)
    ui.updateNestedActions()
    assertEquals(0, findAllActionButtons(tabs).size)
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

  private fun ActionButtonWithText.relativeBounds(): Rectangle {
    val bounds = Rectangle(this.bounds)
    bounds.translate(this.parent.parent.location.x, this.parent.parent.location.y)
    return bounds
  }

  private fun findScrollPane(parent: Component): JBScrollPane =
    TreeWalker(parent)
      .descendantStream()
      .filter { it is JBScrollPane }
      .collect(Collectors.toList())
      .first() as JBScrollPane

  private fun findAllActionButtons(parent: Component): List<ActionButtonWithText> =
    TreeWalker(parent)
      .descendantStream()
      .filter { it is ActionButtonWithText }
      .collect(Collectors.toList())
      .map { it as ActionButtonWithText }
}
