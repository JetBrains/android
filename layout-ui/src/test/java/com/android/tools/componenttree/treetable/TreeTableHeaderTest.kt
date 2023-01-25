/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.componenttree.treetable

import com.android.tools.adtui.FocusableIcon
import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.adtui.swing.laf.HeadlessTreeUI
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.createIntColumn
import com.android.tools.componenttree.util.ItemNodeType
import com.android.tools.componenttree.util.StyleNodeType
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.ui.components.JBLabel
import icons.StudioIcons
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.AWTEvent
import java.awt.AWTKeyStroke
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.table.TableCellRenderer

class TreeTableHeaderTest {
  private val disposableRule = DisposableRule()

  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @get:Rule
  val chain = RuleChain
    .outerRule(EdtRule())
    .around(IconLoaderRule())
    .around(disposableRule)!!

  private val column1 = ColumnDefinition()
  private val column2 = ColumnDefinition()

  @Test
  fun testMouseMove() {
    val scrollPane = createTreeTable()
    scrollPane.setSize(800, 1000)
    val ui = FakeUi(scrollPane, createFakeWindow = true)
    val header = scrollPane.columnHeader.view as TreeTableHeader
    val bounds1 = header.getHeaderRect(0)
    val bounds2 = header.getHeaderRect(1)
    column1.text.cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
    column1.icon.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    column2.text.cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
    column2.icon.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    column1.text.toolTipText = "text1"
    column1.icon.toolTipText = "icon1"
    column2.text.toolTipText = "text2"
    column2.icon.toolTipText = "icon2"
    val manager = ToolTipManager(disposableRule.disposable)
    ui.mouse.moveTo(8, 8)
    assertThat(header.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
    assertThat(manager.lastToolTip).isEqualTo("text1")
    ui.mouse.moveTo(bounds1.width - 8, 8)
    assertThat(header.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    assertThat(manager.lastToolTip).isEqualTo("icon1")
    ui.mouse.moveTo(bounds2.x + 8, 8)
    assertThat(header.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
    assertThat(manager.lastToolTip).isEqualTo("text2")
    ui.mouse.moveTo(bounds2.x + bounds2.width - 8, 8)
    assertThat(header.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    assertThat(manager.lastToolTip).isEqualTo("icon2")
  }

  @Test
  fun testMouseClicks() {
    val scrollPane = createTreeTable()
    scrollPane.setSize(800, 1000)
    val ui = FakeUi(scrollPane, createFakeWindow = true)
    val header = scrollPane.columnHeader.view as TreeTableHeader
    val bounds1 = header.getHeaderRect(0)
    val bounds2 = header.getHeaderRect(1)
    ui.mouse.click(8, 8)
    repeat(2) { ui.mouse.click(bounds1.width - 8, 8) }
    repeat(3) { ui.mouse.click(bounds2.x + 8, 8) }
    repeat(4) { ui.mouse.click(bounds2.x + bounds2.width - 8, 8) }
    assertThat(column1.icon.pressCount).isEqualTo(2)
    assertThat(column1.icon.releaseCount).isEqualTo(2)
    assertThat(column1.icon.clickCount).isEqualTo(2)
    assertThat(column2.text.pressCount).isEqualTo(3)
    assertThat(column2.text.releaseCount).isEqualTo(3)
    assertThat(column2.text.clickCount).isEqualTo(3)
    assertThat(column2.icon.pressCount).isEqualTo(4)
    assertThat(column2.icon.releaseCount).isEqualTo(4)
    assertThat(column2.icon.clickCount).isEqualTo(4)
  }

  @Test
  fun testKeyboardNavigation() {
    val scrollPane = createTreeTable()
    scrollPane.setSize(800, 1000)
    val panel = JPanel(BorderLayout())
    val icon1 = FocusableIcon()
    val icon2 = FocusableIcon()
    icon1.icon = StudioIcons.Common.ERROR
    icon2.icon = StudioIcons.Common.CLEAR
    panel.add(icon1, BorderLayout.NORTH)
    panel.add(scrollPane, BorderLayout.CENTER)
    panel.add(icon2, BorderLayout.SOUTH)
    panel.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, setOf(AWTKeyStroke.getAWTKeyStroke("shift TAB")))
    panel.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, setOf(AWTKeyStroke.getAWTKeyStroke("TAB")))
    val ui = FakeUi(panel, createFakeWindow = true)
    val manager = FakeKeyboardFocusManager(disposableRule.disposable)
    manager.focusOwner = icon1
    ui.keyboard.pressTab()
    assertThat(manager.focusOwner).isSameAs(icon2)
    ui.keyboard.pressTab()
    assertThat(manager.focusOwner).isSameAs(icon1)

    // Make the icon in column2 focusable and try again:
    column2.icon.isFocusable = true
    ui.keyboard.pressBackTab()
    assertThat(manager.focusOwner).isSameAs(icon2)
    ui.keyboard.pressBackTab()
    assertThat(manager.focusOwner).isSameAs(column2.icon)
    ui.keyboard.pressBackTab()
    assertThat(manager.focusOwner).isSameAs(icon1)

    // Now also make icon from column1 and text from column2 focusable and try again:
    column1.icon.isFocusable = true
    column2.text.isFocusable = true
    ui.keyboard.pressTab()
    assertThat(manager.focusOwner).isSameAs(column1.icon)
    ui.keyboard.pressTab()
    assertThat(manager.focusOwner).isSameAs(column2.text)
    ui.keyboard.pressTab()
    assertThat(manager.focusOwner).isSameAs(column2.icon)
    ui.keyboard.pressTab()
    assertThat(manager.focusOwner).isSameAs(icon2)
    ui.keyboard.pressTab()
    assertThat(manager.focusOwner).isSameAs(icon1)
  }

  private fun FakeKeyboard.pressTab() = pressAndRelease(KeyEvent.VK_TAB)
  private fun FakeKeyboard.pressBackTab() = with(this) {
    press(KeyEvent.VK_SHIFT)
    pressAndRelease(KeyEvent.VK_TAB)
    release(KeyEvent.VK_SHIFT)
  }

  private fun createTreeTable(): JScrollPane {
    val result = ComponentTreeBuilder()
      .withNodeType(ItemNodeType())
      .withNodeType(StyleNodeType())
      .withColumn(createIntColumn<Int>("c1", { 4 }, headerRenderer = column2.renderer))
      .withoutTreeSearch()
      .withInvokeLaterOption { it.run() }
      .withHeaderRenderer(column1.renderer)
      .build()
    val table = result.focusComponent as TreeTableImpl
    table.setUI(HeadlessTableUI())
    table.tree.setUI(HeadlessTreeUI())
    result.interactions.setHeaderVisibility(true)
    return result.component as JScrollPane
  }

  private class ColumnDefinition {
    val panel = JPanel(BorderLayout())
    val text = LabelWithMouseListener("Some text")
    val icon = LabelWithMouseListener(StudioIcons.Common.CLOSE)

    init {
      panel.add(text, BorderLayout.CENTER)
      panel.add(icon, BorderLayout.EAST)
    }

    val renderer = TableCellRenderer { _, _, _, _, _, _ -> panel }
  }

  private class LabelWithMouseListener : JBLabel {
    constructor(text: String) {
      this.text = text
    }
    constructor(icon: Icon) {
      this.icon = icon
    }

    var clickCount = 0
    var pressCount = 0
    var releaseCount = 0
    private val listener = object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent) { pressCount++ }
      override fun mouseReleased(event: MouseEvent) { releaseCount++ }
      override fun mouseClicked(event: MouseEvent) { clickCount++ }
    }

    init {
      addMouseListener(listener)
    }
  }

  /**
   * This mimics what the IdeTooltipManager does...
   */
  private class ToolTipManager(disposable: Disposable) {
    var lastToolTip: String? = null
      private set

    private val listener = AWTEventListener {
      val event = it as? MouseEvent
      val source = event?.source as? JComponent ?: return@AWTEventListener
      lastToolTip = source.getToolTipText(event)
    }

    init {
      Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
      Disposer.register(disposable) { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
    }
  }
}
