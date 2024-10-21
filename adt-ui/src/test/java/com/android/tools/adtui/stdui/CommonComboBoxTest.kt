/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.stdui

import org.mockito.kotlin.whenever
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.plaf.basic.BasicComboBoxUI

@RunWith(JUnit4::class)
class CommonComboBoxTest {
  private val model = TestCommonComboBoxModel("t", listOf("one", "two", "three", "four", "five", "six", "t"))
  private val comboBox = CommonComboBox(model)

  @Test
  fun valueChangesArePropagatedToEditor() {
    model.value = "Moonlight"
    assertThat(comboBox.editor.item).isEqualTo("Moonlight")
  }

  @Test
  fun selectionChangesArePropagatedToEditorAndList() {
    model.selectedItem = "six"
    assertThat(comboBox.editor.item).isEqualTo("six")
    assertThat(getList().selectedValuesList).containsExactly("six")
  }

  private fun getList(): JList<*> {
    val field = BasicComboBoxUI::class.java.getDeclaredField("listBox")
    field.isAccessible = true
    return field.get(comboBox.ui) as JList<*>
  }

  @Test
  fun testErrorStateIsSetAndResetOnComboBox() {
    // Only the Darcula UI supplies a ErrorBorderCapable border.
    comboBox.setUI(DarculaComboBoxUI())

    // Show outline based on the value when not editing:
    model.value = "Error"
    assertThat(comboBox.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(ERROR_VALUE)
    model.value = "Warning"
    assertThat(comboBox.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(WARNING_VALUE)
    model.value = "FixedValue"
    assertThat(comboBox.getClientProperty(OUTLINE_PROPERTY)).isNull()

    // Show outline based on the edited text when editing:
    val editor = comboBox.editor.editorComponent as CommonTextField<*>
    val oldManager = acquireFocus(editor)
    try {
      editor.text = "Error"
      assertThat(comboBox.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(ERROR_VALUE)
      editor.text = "Warning"
      assertThat(comboBox.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(WARNING_VALUE)
      editor.text = "FixedText"
      assertThat(comboBox.getClientProperty(OUTLINE_PROPERTY)).isNull()

      // Verify that the model value has not changed:
      assertThat(model.value).isEqualTo("FixedValue")
    }
    finally {
      KeyboardFocusManager.setCurrentKeyboardFocusManager(oldManager)
    }
  }

  @Test
  fun testKeyboardNavigationWithAction() {
    var actionCount = 0
    comboBox.addActionListener { actionCount++ }
    comboBox.setUI(FakeComboBoxUI())
    comboBox.showPopup()
    val editor = comboBox.editor.editorComponent
    val ui = FakeUi(editor)
    ui.keyboard.setFocus(editor)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertThat(comboBox.selectedIndex).isEqualTo(1)
    assertThat(actionCount).isEqualTo(1)
  }

  @Test
  fun testKeyboardNavigationWithoutAction() {
    var actionCount = 0
    comboBox.actionOnKeyNavigation = false
    comboBox.addActionListener { actionCount++ }
    comboBox.setUI(FakeComboBoxUI())
    comboBox.showPopup()
    val editor = comboBox.editor.editorComponent
    val ui = FakeUi(editor)
    ui.keyboard.setFocus(editor)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertThat(comboBox.selectedIndex).isEqualTo(0)
    assertThat(comboBox.popup!!.list.selectedIndex).isEqualTo(1)
    assertThat(actionCount).isEqualTo(0)
  }

  @Test
  fun testTypingWillClosePopup() {
    comboBox.setUI(FakeComboBoxUI())
    comboBox.showPopup()
    val editor = comboBox.editor.editorComponent
    val ui = FakeUi(editor)
    ui.keyboard.setFocus(editor)
    ui.keyboard.type(KeyEvent.VK_A)
    assertThat(comboBox.isPopupVisible).isFalse()
  }

  private fun acquireFocus(component: Component): KeyboardFocusManager {
    val oldManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val manager = Mockito.mock(KeyboardFocusManager::class.java)
    KeyboardFocusManager.setCurrentKeyboardFocusManager(manager)
    whenever(manager.focusOwner).thenReturn(component)
    return oldManager
  }

  private class FakeComboBoxUI : BasicComboBoxUI() {
    private var popupVisible = false

    override fun setPopupVisible(comboBox: JComboBox<*>?, visible: Boolean) {
      popupVisible = visible
    }

    override fun isPopupVisible(comboBox: JComboBox<*>?): Boolean = popupVisible
  }
}
