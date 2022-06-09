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

import com.android.testutils.MockitoKt.whenever
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI
import com.intellij.util.ui.JBUI
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import javax.swing.JPanel

@RunWith(JUnit4::class)
class CommonTextFieldTest {
  private val model = TestCommonTextFieldModel("")
  private val field = CommonTextField(model)

  @After
  fun cleanUpFocusManager() {
    KeyboardFocusManager.setCurrentKeyboardFocusManager(null)
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

  private fun acquireFocus() {
    val manager = mock(KeyboardFocusManager::class.java)
    KeyboardFocusManager.setCurrentKeyboardFocusManager(manager)
    whenever(manager.focusOwner).thenReturn(field)
  }
}
