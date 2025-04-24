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
package com.android.tools.idea.preview.focus

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.preview.focus.FocusModeTabs.Companion.truncate
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.PreviewDisplaySettings
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertInstanceOf
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

@RunsInEdt
class FocusModeTabsTest {
  @get:Rule val rule = RuleChain(AndroidProjectRule.inMemory(), EdtRule())

  private var rootComponent = JPanel(BorderLayout())

  private data class TestKey(override val settings: PreviewDisplaySettings) : FocusKey

  private val simpleSettings =
    PreviewDisplaySettings(
      name = "",
      baseName = "",
      parameterName = null,
      group = null,
      showDecoration = false,
      showBackground = false,
      backgroundColor = null,
      organizationGroup = "",
      organizationName = "",
    )

  @Test
  fun `first tab is selected`() {
    val selected = TestKey(simpleSettings.copy(name = "First Tab"))
    val keys =
      setOf(
        TestKey(simpleSettings.copy(name = "First Tab")),
        TestKey(simpleSettings.copy(name = "Second Tab")),
        TestKey(simpleSettings.copy(name = "Third Tab")),
      )
    val tabs = FocusModeTabs(rootComponent, { selected }, { keys }, { _, _ -> })
    FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey(simpleSettings.copy(name = "First Tab")), tabs.selectedKey)
  }

  @Test
  fun `second tab is selected`() {
    val selected = TestKey(simpleSettings.copy(name = "Second Tab"))
    val keys =
      setOf(
        TestKey(simpleSettings.copy(name = "First Tab")),
        TestKey(simpleSettings.copy(name = "Second Tab")),
        TestKey(simpleSettings.copy(name = "Third Tab")),
      )
    val tabs = FocusModeTabs(rootComponent, { selected }, { keys }, { _, _ -> })
    FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey(simpleSettings.copy(name = "Second Tab")), tabs.selectedKey)
  }

  @Test
  fun `update selected tab`() {
    var selected = TestKey(simpleSettings.copy(name = "First Tab"))
    val keys =
      setOf(
        TestKey(simpleSettings.copy(name = "First Tab")),
        TestKey(simpleSettings.copy(name = "Second Tab")),
        TestKey(simpleSettings.copy(name = "Third Tab")),
      )
    val tabs = FocusModeTabs(rootComponent, { selected }, { keys }, { _, _ -> })
    selected = TestKey(simpleSettings.copy(name = "Second Tab"))
    FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey(simpleSettings.copy(name = "Second Tab")), tabs.selectedKey)
  }

  @Test
  fun `update provided tabs`() {
    val keys =
      setOf(
        TestKey(simpleSettings.copy(name = "Second Tab")),
        TestKey(simpleSettings.copy(name = "Third Tab")),
      )
    var providedKeys = setOf(TestKey(simpleSettings.copy(name = "First Tab"))) + keys
    val tabs =
      FocusModeTabs(
        rootComponent,
        { TestKey(simpleSettings.copy(name = "Second Tab")) },
        { providedKeys },
        { _, _ -> },
      )
    providedKeys = keys
    FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(TestKey(simpleSettings.copy(name = "Second Tab")), tabs.selectedKey)
  }

  @Test
  fun `new tab is added `() {
    val newTab = TestKey(simpleSettings.copy(name = "New Tab"))
    val selected = TestKey(simpleSettings.copy(name = "Tab"))
    val providedKeys =
      mutableSetOf(
        TestKey(simpleSettings.copy(name = "First Tab")),
        TestKey(simpleSettings.copy(name = "Second Tab")),
        TestKey(simpleSettings.copy(name = "Third Tab")),
      )
    val tabs = FocusModeTabs(rootComponent, { selected }, { providedKeys }) { _, _ -> }
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
    val keyOne = TestKey(simpleSettings.copy(name = "First Tab"))
    val keyTwo = TestKey(simpleSettings.copy(name = "Second Tab"))
    val keyThree = TestKey(simpleSettings.copy(name = "Third Tab"))
    var providedKeys = setOf(keyTwo)
    val selected = TestKey(simpleSettings.copy(name = "First Tab"))
    val tabs = FocusModeTabs(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    // Use a direct executor instead of the default (invokeLater) for replacing the toolbar,
    // so the ActionButtonWithText can be found when using TreeWalker.
    tabs.setUpdateToolbarExecutorForTests(MoreExecutors.directExecutor())
    providedKeys = setOf(keyOne, keyTwo, keyThree)
    val ui = FakeUi(tabs)
    ui.updateNestedActions()
    val allActions = findAllActionButtons(tabs)
    assertEquals(3, allActions.size)
    assertEquals("First Tab", allActions[0].presentation.text)
    assertEquals("Second Tab", allActions[1].presentation.text)
    assertEquals("Third Tab", allActions[2].presentation.text)
  }

  @Test
  fun `toolbar is not updated`() {
    val selected = TestKey(simpleSettings.copy(name = "First Tab"))
    val providedKeys =
      setOf(
        TestKey(simpleSettings.copy(name = "First Tab")),
        TestKey(simpleSettings.copy(name = "Second Tab")),
        TestKey(simpleSettings.copy(name = "Third Tab")),
      )
    val tabs = FocusModeTabs(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    val ui = FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    val toolbar = findFocusModeTabs(tabs)
    // Update toolbars
    ui.updateNestedActions()
    val updatedToolbar = findFocusModeTabs(tabs)
    // Toolbar was not updated, it's same as before.
    assertEquals(toolbar, updatedToolbar)
  }

  @Test
  fun `toolbar is updated with new key`() {
    val selected = TestKey(simpleSettings.copy(name = "First Tab"))
    val providedKeys =
      mutableSetOf(
        TestKey(simpleSettings.copy(name = "First Tab")),
        TestKey(simpleSettings.copy(name = "Second Tab")),
        TestKey(simpleSettings.copy(name = "Third Tab")),
      )
    val tabs = FocusModeTabs(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    val ui = FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    val toolbar = findFocusModeTabs(tabs)
    // Set new set of keys.
    providedKeys += TestKey(simpleSettings.copy(name = "New Tab"))
    ui.updateNestedActions()
    val updatedToolbar = findFocusModeTabs(tabs)
    // New toolbar was created.
    assertNotEquals(toolbar, updatedToolbar)
  }

  @Test
  fun `more toolbar group created`() {
    val keys =
      mutableSetOf(
        TestKey(
          simpleSettings.copy(
            name = "name - 1",
            parameterName = "1",
            organizationGroup = "group1",
            organizationName = "name1",
          )
        ),
        TestKey(
          simpleSettings.copy(
            name = "name - 2",
            parameterName = "2",
            organizationGroup = "group1",
            organizationName = "name1",
          )
        ),
        TestKey(
          simpleSettings.copy(
            name = "name - 3",
            parameterName = "3",
            organizationGroup = "group1",
            organizationName = "name1",
          )
        ),
        TestKey(
          simpleSettings.copy(
            name = "name - 4",
            parameterName = "4",
            organizationGroup = "no group1",
          )
        ),
        TestKey(
          simpleSettings.copy(
            name = "name - 5",
            parameterName = "5",
            organizationGroup = "no group",
          )
        ),
        TestKey(
          simpleSettings.copy(
            name = "name - 6",
            parameterName = "6",
            organizationGroup = "group2",
            organizationName = "name2",
          )
        ),
        TestKey(
          simpleSettings.copy(
            name = "name - 7",
            parameterName = "7",
            organizationGroup = "group2",
            organizationName = "name2",
          )
        ),
        TestKey(
          simpleSettings.copy(
            name = "name - 8",
            parameterName = "8",
            organizationGroup = "group2",
            organizationName = "name2",
          )
        ),
      )
    val tabs = FocusModeTabs(rootComponent, { keys.first() }, { keys }) { _, _ -> }
    FakeUi(tabs)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(13, tabs.getMoreActionsForTests().count())

    assertInstanceOf<Separator>(tabs.getMoreActionsForTests()[0])
    assertInstanceOf<FocusModeTabs.HeaderAction>(tabs.getMoreActionsForTests()[1])
    assertInstanceOf<FocusModeTabs<*>.TabLabelAction>(tabs.getMoreActionsForTests()[2])
    assertInstanceOf<FocusModeTabs<*>.TabLabelAction>(tabs.getMoreActionsForTests()[3])
    assertInstanceOf<FocusModeTabs<*>.TabLabelAction>(tabs.getMoreActionsForTests()[4])
    assertInstanceOf<Separator>(tabs.getMoreActionsForTests()[5])
    assertInstanceOf<FocusModeTabs<*>.TabLabelAction>(tabs.getMoreActionsForTests()[6])
    assertInstanceOf<FocusModeTabs<*>.TabLabelAction>(tabs.getMoreActionsForTests()[7])
    assertInstanceOf<Separator>(tabs.getMoreActionsForTests()[8])
    assertInstanceOf<FocusModeTabs.HeaderAction>(tabs.getMoreActionsForTests()[9])
    assertInstanceOf<FocusModeTabs<*>.TabLabelAction>(tabs.getMoreActionsForTests()[10])
    assertInstanceOf<FocusModeTabs<*>.TabLabelAction>(tabs.getMoreActionsForTests()[11])
    assertInstanceOf<FocusModeTabs<*>.TabLabelAction>(tabs.getMoreActionsForTests()[12])

    assertEquals("name1", (tabs.getMoreActionsForTests()[1].templateText))
    assertEquals("name2", (tabs.getMoreActionsForTests()[9].templateText))
  }

  @Test
  fun `toolbar is updated with removed key`() {
    val keyToRemove = TestKey(simpleSettings.copy(name = "Key to remove"))
    val selected = TestKey(simpleSettings.copy(name = "First Tab"))
    val providedKeys =
      mutableSetOf(
        TestKey(simpleSettings.copy(name = "First Tab")),
        TestKey(simpleSettings.copy(name = "Second Tab")),
        TestKey(simpleSettings.copy(name = "Third Tab")),
        keyToRemove,
      )
    val tabs = FocusModeTabs(rootComponent, { selected }, { providedKeys }) { _, _ -> }
    val ui = FakeUi(tabs)
    ui.updateNestedActions()
    val toolbar = findFocusModeTabs(tabs)
    // Set updated set of keys
    providedKeys.remove(keyToRemove)
    ui.updateNestedActions()
    val updatedToolbar = findFocusModeTabs(tabs)
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
    val selected = TestKey(simpleSettings.copy(name = "First Tab"))
    val tabs =
      FocusModeTabs(
        rootComponent,
        { selected },
        {
          setOf(
            TestKey(simpleSettings.copy(name = "First Tab")),
            TestKey(simpleSettings.copy(name = "Second Tab")),
            TestKey(simpleSettings.copy(name = "Third Tab")),
          )
        },
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
    var selected = TestKey(simpleSettings.copy(name = "First Tab"))
    val tabs =
      FocusModeTabs(
        rootComponent,
        { selected },
        {
          setOf(
            TestKey(simpleSettings.copy(name = "First Tab")),
            TestKey(simpleSettings.copy(name = "Second Tab")),
            TestKey(simpleSettings.copy(name = "Third Tab")),
          )
        },
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
    assertEquals("First Tab", selected.settings.name)
    assertEquals("First Tab", tabs.selectedKey?.settings?.name)
    ui.clickOn(buttons[1])
    assertEquals("Second Tab", selected.settings.name)
    assertEquals("Second Tab", tabs.selectedKey?.settings?.name)
    ui.clickOn(buttons[2])
    assertEquals("Third Tab", selected.settings.name)
    assertEquals("Third Tab", tabs.selectedKey?.settings?.name)
    ui.clickOn(buttons[0])
    assertEquals("First Tab", selected.settings.name)
    assertEquals("First Tab", tabs.selectedKey?.settings?.name)
  }

  @Test
  fun `clicked tab is always visible`() {
    var selected = TestKey(simpleSettings.copy(name = "First Tab"))
    val tabs =
      FocusModeTabs(
        rootComponent,
        { selected },
        {
          setOf(
            TestKey(simpleSettings.copy(name = "First Tab")),
            TestKey(simpleSettings.copy(name = "Second Tab")),
            TestKey(simpleSettings.copy(name = "Third Tab")),
          )
        },
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
  fun `selected tab is always visible`() {
    val selected = TestKey(simpleSettings.copy(name = "Sixth Tab"))
    val tabs =
      FocusModeTabs(
        rootComponent,
        { selected },
        {
          setOf(
            TestKey(simpleSettings.copy(name = "First Tab")),
            TestKey(simpleSettings.copy(name = "Second Tab")),
            TestKey(simpleSettings.copy(name = "Third Tab")),
            TestKey(simpleSettings.copy(name = "Fourth Tab")),
            TestKey(simpleSettings.copy(name = "Fifth Tab")),
            selected,
          )
        },
      ) { _, _ ->
      }

    // Width is 320 so only four tabs out of six are visible.
    val root = JPanel(BorderLayout()).apply { size = Dimension(320, 40) }
    root.add(tabs, BorderLayout.NORTH)

    val fakeUi = FakeUi(root)
    fakeUi.updateNestedActions()

    val buttons = findAllActionButtons(root)
    val scrollPane = findScrollPane(root).apply { size = Dimension(320, 40) }

    assertEquals(tabs.selectedKey!!.settings.name, "Sixth Tab")
    assertFalse(scrollPane.bounds.contains(buttons[0].relativeBounds()))
    assertFalse(scrollPane.bounds.contains(buttons[1].relativeBounds()))
    assertTrue(scrollPane.bounds.contains(buttons[2].relativeBounds()))
    assertTrue(scrollPane.bounds.contains(buttons[3].relativeBounds()))
    assertTrue(scrollPane.bounds.contains(buttons[4].relativeBounds()))
    assertTrue(scrollPane.bounds.contains(buttons[5].relativeBounds()))
  }

  @Test
  fun `empty focus`() {
    var selected: TestKey? = null
    val tabs =
      FocusModeTabs<TestKey>(rootComponent, { null }, { emptySet() }) { _, key -> selected = key }
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
