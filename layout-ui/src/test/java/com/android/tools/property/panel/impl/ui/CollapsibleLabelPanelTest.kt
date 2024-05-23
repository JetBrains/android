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
package com.android.tools.property.panel.impl.ui

import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.property.panel.impl.model.CollapsibleLabelModel
import com.android.tools.property.ptable.ColumnFraction
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyEvent
import javax.swing.JPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runners.model.Statement

class CollapsibleLabelPanelTest {
  private val disposableRule = DisposableRule()

  @get:Rule val chain = RuleChain(ApplicationRule(), disposableRule)

  @Test
  fun testDoubleClick() {
    val model = CollapsibleLabelModel("Label", null, false, PropertiesComponentMock())
    val panel = CollapsibleLabelPanel(model, UIUtil.FontSize.NORMAL, Font.BOLD)
    val label = panel.label
    panel.size = Dimension(500, 200)
    panel.doLayout()
    val ui = FakeUi(label)
    ui.mouse.doubleClick(400, 100)
    assertThat(model.expanded).isFalse()
    ui.mouse.doubleClick(400, 100)
    assertThat(model.expanded).isTrue()
  }

  @Test
  fun testColumnResize() {
    val columnFraction = ColumnFraction(initialValue = 0.5f, resizeSupported = true)
    val model = CollapsibleLabelModel("Label", null, false, PropertiesComponentMock())
    val panel =
      CollapsibleLabelPanel(
        model,
        UIUtil.FontSize.NORMAL,
        Font.BOLD,
        nameColumnFraction = columnFraction,
      )
    val label = panel.label
    panel.size = Dimension(500, 200)
    panel.doLayout()
    val ui = FakeUi(label)

    assertThat(label.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
    ui.mouse.moveTo(250, 100)
    assertThat(label.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR))
    assertThat(columnFraction.value).isEqualTo(0.5f)
    ui.mouse.press(250, 100)
    ui.mouse.dragTo(300, 100)
    assertThat(label.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR))
    assertThat(columnFraction.value).isWithin(0.01f).of(0.6f)
    ui.mouse.release()
    ui.mouse.moveTo(300, -100)
    assertThat(label.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
    assertThat(columnFraction.value).isWithin(0.01f).of(0.6f)
  }

  @Test
  fun testExpandByKeyboard() {
    val model = CollapsibleLabelModel("Label", null, false, PropertiesComponentMock())
    val panel = CollapsibleLabelPanel(model, UIUtil.FontSize.NORMAL, Font.BOLD)
    val label = panel.label
    panel.size = Dimension(500, 200)
    panel.doLayout()
    val ui = FakeUi(label, createFakeWindow = true)
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    focusManager.focusOwner = UIUtil.findComponentOfType(panel, IconWithFocusBorder::class.java)
    panel.model.makeExpandable(true)
    assertThat(panel.model.expanded).isTrue()

    // Space will toggle:
    ui.keyboard.pressAndRelease(KeyEvent.VK_SPACE)
    assertThat(panel.model.expanded).isFalse()
    ui.keyboard.pressAndRelease(KeyEvent.VK_SPACE)
    assertThat(panel.model.expanded).isTrue()

    // Enter will toggle:
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(panel.model.expanded).isFalse()
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(panel.model.expanded).isTrue()

    // Left will close:
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT)
    assertThat(panel.model.expanded).isFalse()
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT)
    assertThat(panel.model.expanded).isFalse()

    // Right will open:
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT)
    assertThat(panel.model.expanded).isTrue()
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT)
    assertThat(panel.model.expanded).isTrue()
  }

  @Test
  fun testFontUpdate() {
    val model = CollapsibleLabelModel("Label", null, false, PropertiesComponentMock())
    val panel = CollapsibleLabelPanel(model, UIUtil.FontSize.NORMAL, Font.BOLD)
    val size = panel.label.font.size

    PortableUiFontRule(scale = 4f)
      .apply(
        object : Statement() {
          override fun evaluate() {
            panel.updateUI()
            assertThat(panel.label.font.size).isEqualTo(size * 4)
          }
        },
        mock(),
      )
      .evaluate()
  }

  @Test
  fun testButtonsAreVerticallyCentered() {
    val columnFraction = ColumnFraction(initialValue = 0.5f, resizeSupported = true)
    val model = CollapsibleLabelModel("Label", null, false, PropertiesComponentMock())
    val action1 =
      object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) {}
      }
    val action2 =
      object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) {}
      }
    val panel =
      CollapsibleLabelPanel(
        model,
        UIUtil.FontSize.NORMAL,
        Font.BOLD,
        listOf(action1, action2),
        columnFraction,
      )
    panel.size = Dimension(500, 50)
    panel.doLayout()
    panel.components.forEach { it.doLayout() }
    val buttonPanel = panel.components.last() as JPanel
    buttonPanel.components.forEach { button ->
      assertThat(button.y).isEqualTo((50 - button.height) / 2)
    }
  }
}
