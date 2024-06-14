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

import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.util.ui.JBUI
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.JPanel

@RunWith(JUnit4::class)
class CommonTextFieldTest {
  private val model = TestCommonTextFieldModel("")
  private val field = CommonTextField(model)
  private val disposableRule = DisposableRule()
  private val popupRule = JBPopupRule()

  @get:Rule
  val rule = RuleChain(ApplicationRule(), popupRule, disposableRule)

  @Before
  fun setUpFocusManager() {
    FakeKeyboardFocusManager(disposableRule.disposable)
  }

  @Test
  fun testValuePropagatedToTextFieldFromModel() {
    model.value = "Hello"
    assertThat(field.text).isEqualTo("Hello")
  }

  @Test
  fun testTextPropagatedToModelFromTextField() {
    field.text = "World"
    assertThat(model.text).isEqualTo("World")
  }

  @Test
  fun testErrorStateIsSetAndResetOnTextField() {
    // Only the Darcula UI supplies a ErrorBorderCapable border.
    field.setUI(DarculaTextFieldUI())
    field.border = DarculaEditorTextFieldBorder()

    // Show outline based on the value when not editing:
    model.value = "Error"
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(ERROR_VALUE)
    model.value = "Warning"
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(WARNING_VALUE)
    model.value = "FixedValue"
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isNull()

    // Show outline based on the edited text when editing:
    acquireFocus()
    field.text = "Error"
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(ERROR_VALUE)
    field.text = "Warning"
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(WARNING_VALUE)
    field.text = "FixedText"
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isNull()

    // Verify that the model value has not changed:
    assertThat(model.value).isEqualTo("FixedValue")
  }

  @Test
  fun testErrorStateIsSetAndResetOnTextFieldEmbeddedInJPanel() {
    val panel = JPanel(BorderLayout())
    panel.border = DarculaEditorTextFieldBorder()
    panel.add(field, BorderLayout.CENTER)
    field.border = JBUI.Borders.empty()

    // Show outline based on the value when not editing:
    model.value = "Error"
    assertThat(panel.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(ERROR_VALUE)
    model.value = "Warning"
    assertThat(panel.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(WARNING_VALUE)
    model.value = "FixedValue"
    assertThat(panel.getClientProperty(OUTLINE_PROPERTY)).isNull()

    // Show outline based on the edited text when editing:
    acquireFocus()
    field.text = "Error"
    assertThat(panel.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(ERROR_VALUE)
    field.text = "Warning"
    assertThat(panel.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(WARNING_VALUE)
    field.text = "FixedText"
    assertThat(panel.getClientProperty(OUTLINE_PROPERTY)).isNull()

    // Verify that the model value has not changed:
    assertThat(model.value).isEqualTo("FixedValue")
  }

  @Test
  fun testRetainEditingValueIfUpdateIsReceivedDuringEditing() {
    acquireFocus()
    field.text = "Editing the val..."

    // Simulate an update from the model:
    model.value = "Hello World!"

    // Verify that this doesn't effect the text value in the field or model:
    assertThat(field.text).isEqualTo("Editing the val...")
    assertThat(model.text).isEqualTo("Editing the val...")
  }

  @Test
  fun testRetainEditingValueIfUpdateIsReceivedDuringEditingWithLookup() {
    acquireFocus()
    field.text = "Editing the val..."

    // Bring up the lookup:
    val ui = FakeUi(field, createFakeWindow = true)
    ui.keyboard.press(KeyEvent.VK_CONTROL)
    ui.keyboard.pressAndRelease(KeyEvent.VK_SPACE)
    ui.keyboard.release(KeyEvent.VK_CONTROL)

    // Simulate an update from the model:
    model.value = "Hello World!"

    // Verify that this doesn't effect the text value in the field or model:
    assertThat(field.text).isEqualTo("Editing the val...")
    assertThat(model.text).isEqualTo("Editing the val...")
  }

  private fun acquireFocus() {
    (KeyboardFocusManager.getCurrentKeyboardFocusManager() as FakeKeyboardFocusManager).focusOwner = field
  }
}
