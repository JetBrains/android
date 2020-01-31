/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.observable.ui

import com.android.tools.idea.observable.CountListener
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import javax.swing.ButtonGroup
import javax.swing.JRadioButton

/**
 * Unit tests for [SelectedRadioButtonProperty]
 */
class SelectedRadioButtonPropertyTest {
  @Test
  fun testStateIsInSyncWithJRadioButton() {
    val radioButtons = mapOf(
      "first" to JRadioButton(),
      "second" to JRadioButton(),
      "third" to JRadioButton()
    )
    val buttonGroup = ButtonGroup()
    radioButtons.values.forEach(buttonGroup::add)
    val property = SelectedRadioButtonProperty("first", radioButtons.keys.toTypedArray(), *radioButtons.values.toTypedArray())
    // Add listener to this ObservableValue to make sure the value changes will be notified.
    val listener = CountListener()
    property.addListener(listener)
    // Verify initial selection matches.
    assertThat(property.get()).isEqualTo("first")
    assertThat(listener.count).isEqualTo(0)
    assertThat(radioButtons.getValue("first").isSelected).isTrue()
    assertThat(radioButtons.getValue("second").isSelected).isFalse()
    assertThat(radioButtons.getValue("third").isSelected).isFalse()
    // Update the selection by clicking the radio button.
    radioButtons.getValue("third").isSelected = true
    assertThat(property.get()).isEqualTo("third")
    assertThat(listener.count).isEqualTo(1)
    assertThat(radioButtons.getValue("first").isSelected).isFalse()
    assertThat(radioButtons.getValue("second").isSelected).isFalse()
    assertThat(radioButtons.getValue("third").isSelected).isTrue()
    // Again, update the selection by clicking the radio button.
    radioButtons.getValue("second").isSelected = true
    assertThat(property.get()).isEqualTo("second")
    assertThat(listener.count).isEqualTo(2)
    assertThat(radioButtons.getValue("first").isSelected).isFalse()
    assertThat(radioButtons.getValue("second").isSelected).isTrue()
    assertThat(radioButtons.getValue("third").isSelected).isFalse()
    // Update the selection by ButtonGroup's API.
    buttonGroup.setSelected(radioButtons.getValue("first").model, true)
    assertThat(property.get()).isEqualTo("first")
    assertThat(listener.count).isEqualTo(3)
    assertThat(radioButtons.getValue("first").isSelected).isTrue()
    assertThat(radioButtons.getValue("second").isSelected).isFalse()
    assertThat(radioButtons.getValue("third").isSelected).isFalse()
    // Update the selection by setter.
    property.set("second")
    assertThat(property.get()).isEqualTo("second")
    assertThat(listener.count).isEqualTo(4)
    assertThat(radioButtons.getValue("first").isSelected).isFalse()
    assertThat(radioButtons.getValue("second").isSelected).isTrue()
    assertThat(radioButtons.getValue("third").isSelected).isFalse()
    // Setting the same value shouldn't fire invalidation event.
    property.set("second")
    assertThat(property.get()).isEqualTo("second")
    assertThat(listener.count).isEqualTo(4)
  }

  @Test
  fun testInvalidValueThrowsException() {
    val radioButtons = mapOf(
      "first" to JRadioButton(),
      "second" to JRadioButton(),
      "third" to JRadioButton()
    )
    val buttonGroup = ButtonGroup()
    radioButtons.values.forEach(buttonGroup::add)
    val property = SelectedRadioButtonProperty("first", radioButtons.keys.toTypedArray(), *radioButtons.values.toTypedArray())
    try {
      property.set("zero")
      fail()
    }
    catch (expected: IllegalArgumentException) {
      assertThat(expected).hasMessageThat().contains("Invalid selected value (zero)")
    }
  }

  @Test
  fun testInvalidInitialValueThrowsException() {
    val radioButtons = mapOf(
      "first" to JRadioButton(),
      "second" to JRadioButton(),
      "third" to JRadioButton()
    )
    val buttonGroup = ButtonGroup()
    radioButtons.values.forEach(buttonGroup::add)
    try {
      SelectedRadioButtonProperty("zero", radioButtons.keys.toTypedArray(), *radioButtons.values.toTypedArray())
      fail()
    }
    catch (expected: IllegalArgumentException) {
      assertThat(expected).hasMessageThat().contains("Invalid selected value (zero)")
    }
  }

  @Test
  fun testInsufficientButtonsThrowsException() {
    try {
      SelectedRadioButtonProperty("first", arrayOf("first", "second"), JRadioButton())
      fail()
    }
    catch (expected: IllegalArgumentException) {
      assertThat(expected).hasMessageThat().contains("The number of values (2) doesn't match the number of radio buttons (1)")
    }
  }

  @Test
  fun testTooManyButtonsThrowsException() {
    try {
      SelectedRadioButtonProperty("first", arrayOf("first"), JRadioButton(), JRadioButton())
      fail()
    }
    catch (expected: IllegalArgumentException) {
      assertThat(expected).hasMessageThat().contains("The number of values (1) doesn't match the number of radio buttons (2)")
    }
  }

  // Regression test for b/136112727.
  @Test
  fun testGetDoNotThrowExceptionByRaceCondition() {
    val radioButtons = mapOf(
      "first" to JRadioButton(),
      "second" to JRadioButton()
    )
    val buttonGroup = ButtonGroup()
    radioButtons.values.forEach(buttonGroup::add)
    val property = SelectedRadioButtonProperty("first", radioButtons.keys.toTypedArray(), *radioButtons.values.toTypedArray())
    var callbackIsInvoked = false
    radioButtons.getValue("first").addItemListener {
      property.get()  // This was throwing "java.lang.IllegalStateException: No radio button is selected" before the fix.
      callbackIsInvoked = true
    }
    property.set("second")
    assertThat(callbackIsInvoked).isTrue()
  }
}