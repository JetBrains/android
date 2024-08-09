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
package com.android.tools.idea.preview.animation.state

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.preview.animation.AnimationTracker
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.assertInstanceOf
import java.awt.Color
import java.awt.Component
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ColorPickerActionTest {
  @get:Rule val projectRule = ApplicationRule()

  private lateinit var tracker: AnimationTracker
  private var callbackInvoked = false
  private var callbackValue: Color? = null
  private lateinit var action: ColorPickerAction

  @Before
  fun setUp() {
    tracker = mock<AnimationTracker>()
    callbackInvoked = false
    callbackValue = null

    // Initialize the action with the callback
    action =
      ColorPickerAction(tracker, Color.RED) { color ->
        callbackInvoked = true
        callbackValue = color
      }
  }

  @Test
  fun `test createCustomComponent creates action button with color`() {
    val presentation = Presentation()
    val place = ActionPlaces.TOOLBAR

    val component = action.createCustomComponent(presentation, place)

    assertInstanceOf<ActionButton>(component)
  }

  @Test
  fun `test swapWith swaps colors and invokes callbacks`() {
    var otherCallbackInvoked = false
    var otherCallbackValue: Color? = null
    val otherAction =
      ColorPickerAction(tracker, Color.BLUE) { color ->
        otherCallbackInvoked = true
        otherCallbackValue = color
      }

    action.swapWith(otherAction)

    // Verify colors were swapped and callbacks were called
    assertEquals(Color.BLUE, action.currentValue)
    assertEquals(Color.RED, otherAction.currentValue)
    assertTrue(callbackInvoked)
    assertTrue(otherCallbackInvoked)
    assertEquals(Color.BLUE, callbackValue)
    assertEquals(Color.RED, otherCallbackValue)
  }

  @Test
  fun `test actionPerformed calls callback`() {
    val colorPicker =
      object : ColorPicker {
        override fun show(
          initialColor: Color,
          restoreFocusComponent: Component?,
          onColorPicked: (Color) -> Unit,
        ) {
          onColorPicked(Color.GREEN) // Simulate picking a color
        }
      }

    // Create action with the mock ColorPicker
    val action =
      ColorPickerAction(tracker, Color.RED, colorPicker) { color ->
        callbackInvoked = true
        callbackValue = color
      }

    action.actionPerformed(createTestEvent())

    // Verify callback was invoked
    assertTrue(callbackInvoked)
    assertEquals(Color.GREEN, callbackValue)
  }
}
