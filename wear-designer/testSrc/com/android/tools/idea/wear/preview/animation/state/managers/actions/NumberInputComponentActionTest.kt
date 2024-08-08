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
package com.android.tools.idea.wear.preview.animation.state.managers.actions

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import java.awt.event.FocusEvent
import javax.swing.JComponent
import javax.swing.JTextField
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class NumberInputComponentActionTest {

  @get:Rule val projectRule = ProjectRule()

  @get:Rule val disposableRule = DisposableRule()

  @get:Rule val edtRule = EdtRule()

  @Test
  fun testFloatInput_ValidInputUpdatesValue() {
    var newValue = 0f
    val action = FloatInputComponentAction(10f) { newValue = it }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    inputField.requestFocusInWindow()
    assertEquals("10.0", inputField.text)

    // Test valid input
    inputField.text = "20.5"
    inputField.onFocusLost()
    assertEquals(20.5f, newValue)

    // Test invalid input
    inputField.text = "abc"
    inputField.onFocusLost()
    // Value should not change for invalid input
    assertEquals(20.5f, newValue)
  }

  @Test
  fun testFloatInput_InvalidInputDoesNotChangeValue() {
    val action =
      FloatInputComponentAction(10f) { throw AssertionError("Value not changed for invalid input") }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10.0", inputField.text)

    // Test invalid input
    inputField.text = "abc"
    inputField.onFocusLost()
  }

  @Test
  fun testFloatInput_EmptyInputDoesNotChangeValue() {
    var newValue = 0f
    val action = FloatInputComponentAction(10f) { newValue = it }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10.0", inputField.text)

    // Test valid input
    inputField.text = "20.5"
    inputField.onFocusLost()
    assertEquals(20.5f, newValue)

    // Test empty input
    inputField.text = ""
    inputField.onFocusLost()
  }

  @Test
  fun testFloatInput_LargeNumber() {
    var newValue = 0f
    val action = FloatInputComponentAction(10f) { newValue = it }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10.0", inputField.text)

    // Test large number input
    inputField.text = "1.23456789E10" // A large number in scientific notation
    inputField.onFocusLost()
    assertEquals(1.23456789E10f, newValue)
  }

  @Test
  fun testFloatInput_SmallNumber() {
    var newValue = 0f
    val action = FloatInputComponentAction(10f) { newValue = it }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10.0", inputField.text)

    // Test small number input
    inputField.text = "1.23456789E-10" // A small number in scientific notation
    inputField.onFocusLost()
    assertEquals(1.23456789E-10f, newValue)
  }

  @Test
  fun testFloatInput_LeadingAndTrailingSpaces() {
    var newValue = 0f
    val action = FloatInputComponentAction(10f) { newValue = it }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10.0", inputField.text)

    // Test input with leading and trailing spaces
    inputField.text = "   20.5   "
    inputField.onFocusLost()
    assertEquals(20.5f, newValue)
  }

  @Test
  fun testIntInput_ValidInputUpdatesValue() {
    var newValue = 0
    val action = IntInputComponentAction(10) { newValue = it }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10", inputField.text)

    // Test valid input
    inputField.text = "20"
    inputField.onFocusLost()
    assertEquals(20, newValue)

    // Test invalid input
    inputField.text = "abc"
    inputField.onFocusLost()
    // Value should not change for invalid input
    assertEquals(20, newValue)
  }

  @Test
  fun testIntInput_InvalidInputDoesNotChangeValue() {
    val action =
      IntInputComponentAction(10) { throw AssertionError("Value not changed for invalid input") }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10", inputField.text)

    // Test invalid input
    inputField.text = "abc"
    inputField.onFocusLost()
  }

  @Test
  fun testIntInput_EmptyInputDoesNotChangeValue() {
    var newValue = 0
    val action = IntInputComponentAction(10) { newValue = it }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10", inputField.text)

    // Test valid input
    inputField.text = "20"
    inputField.onFocusLost()

    // Test empty input
    inputField.text = ""
    inputField.onFocusLost()
    assertEquals(20, newValue)
  }

  @Test
  fun testIntInput_LargeNumber() {
    var newValue = 0
    val action = IntInputComponentAction(10) { newValue = it }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10", inputField.text)

    // Test large number input (close to the maximum representable integer)
    inputField.text = Integer.MAX_VALUE.toString()
    inputField.onFocusLost()
    assertEquals(Integer.MAX_VALUE, newValue)
  }

  @Test
  fun testIntInput_SmallNumber() {
    var newValue = 0
    val action = IntInputComponentAction(10) { newValue = it }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10", inputField.text)

    // Test small number input (close to the minimum representable integer)
    inputField.text = Integer.MIN_VALUE.toString()
    inputField.onFocusLost()
    assertEquals(Integer.MIN_VALUE, newValue)
  }

  @Test
  fun testIntInput_LeadingAndTrailingSpaces() {
    var newValue = 0
    val action = IntInputComponentAction(10) { newValue = it }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10", inputField.text)

    // Test input with leading and trailing spaces
    inputField.text = "   20   "
    inputField.onFocusLost()
    assertEquals(20, newValue)
  }

  @Test
  fun testIntInput_NumberWithDecimal() {
    val action =
      IntInputComponentAction(10) { throw AssertionError("Value not changed for invalid input") }
    val component = action.createCustomComponent(Presentation(), "")
    val inputField = component.components[0] as JTextField

    // Test initial value
    assertEquals("10", inputField.text)

    inputField.text = "20.5"
    inputField.onFocusLost()
  }

  private fun JComponent.onFocusLost() {
    focusListeners.forEach { it.focusLost(FocusEvent(this, FocusEvent.FOCUS_LOST)) }
  }
}
