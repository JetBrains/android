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
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

class ColorPickerActionTest {
  @get:Rule val projectRule = ApplicationRule()

  private lateinit var tracker: AnimationTracker
  private lateinit var flow: MutableStateFlow<Color>
  private lateinit var action: ColorPickerAction

  @Before
  fun setUp() {
    tracker = mock<AnimationTracker>()
    flow = MutableStateFlow(Color.RED)
    action = ColorPickerAction(tracker, flow)
  }

  @Test
  fun `test createCustomComponent creates action button with color`() {
    val presentation = Presentation()
    val place = ActionPlaces.TOOLBAR

    val component = action.createCustomComponent(presentation, place)

    assertInstanceOf<ActionButton>(component)
  }

  @Test
  fun `test swapWith swaps colors between actions`() {
    val otherFlow = MutableStateFlow(Color.BLUE)
    val otherAction = ColorPickerAction(tracker, otherFlow)

    action.swapWith(otherAction)

    assertEquals(flow.value, Color.BLUE)
    assertEquals(otherFlow.value, Color.RED)
  }

  @Test
  fun `test on color picked callback should update flow and notify tracker`() {
    var capturedCallback: ((Color) -> Unit)? = null

    val colorPicker =
      object : ColorPicker {
        override fun show(
          initialColor: Color,
          restoreFocusComponent: Component?,
          onColorPicked: (Color) -> Unit,
        ) {
          capturedCallback = onColorPicked
        }
      }

    val action = ColorPickerAction(tracker, flow, colorPicker)
    action.actionPerformed(createTestEvent())

    // Verify initial flow value
    assertEquals(Color.RED, flow.value)

    // Simulate picking a new color
    capturedCallback?.invoke(Color.GREEN) // Invoke the captured lambda

    // Verify flow value has been updated
    assertEquals(Color.GREEN, flow.value)

    // Verify that the color picker was opened and the flow value was updated
    verify(tracker).openPicker()
  }
}
