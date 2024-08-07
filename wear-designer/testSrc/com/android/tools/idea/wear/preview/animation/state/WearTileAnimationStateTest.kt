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
package com.android.tools.idea.wear.preview.animation.state

import com.android.tools.idea.preview.NoopAnimationTracker
import com.android.tools.idea.preview.animation.state.ColorPickerAction
import com.android.tools.idea.preview.animation.state.SwapAction
import com.android.tools.idea.preview.animation.state.ToolbarLabel
import com.android.tools.idea.wear.preview.animation.ColorUnit
import com.android.tools.idea.wear.preview.animation.ProtoAnimation
import com.android.tools.idea.wear.preview.animation.TestDynamicTypeAnimator
import com.android.tools.idea.wear.preview.animation.state.managers.actions.FloatInputComponentAction
import com.android.tools.idea.wear.preview.animation.state.managers.actions.IntInputComponentAction
import java.awt.Color
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class WearTileAnimationStateTest {

  @Test
  fun testWearTileFloatState_updateAnimation() {
    val initialState = 10f
    val targetState = 20f
    val state = WearTileFloatState(initialState, targetState)
    val animator = TestDynamicTypeAnimator()
    val animation = ProtoAnimation(animator)

    state.updateAnimation(animation)

    assertEquals(initialState, animator.getFloatValues()[0])
    assertEquals(targetState, animator.getFloatValues()[1])
  }

  @Test
  fun testWearTileIntState_updateAnimation() {
    val initialState = 5
    val targetState = 15
    val state = WearTileIntState(initialState, targetState)
    val animator = TestDynamicTypeAnimator()
    val animation = ProtoAnimation(animator)
    state.updateAnimation(animation)

    assertEquals(initialState, animator.getIntValues()[0])
    assertEquals(targetState, animator.getIntValues()[1])
  }

  @Test
  fun testWearTileColorPickerState_updateAnimation() {
    val initialColor = ColorUnit(Color.RED)
    val targetColor = ColorUnit(Color.BLUE)
    val state = WearTileColorPickerState(NoopAnimationTracker, initialColor, targetColor)
    val animator = TestDynamicTypeAnimator()
    val animation = ProtoAnimation(animator)
    state.updateAnimation(animation)

    assertEquals(initialColor.color.rgb, animator.getIntValues()[0])
    assertEquals(targetColor.color.rgb, animator.getIntValues()[1])
  }

  @Test
  fun testWearTileFloatState_changeStateActions() {
    val initialState = 10f
    val targetState = 20f
    val state = WearTileFloatState(initialState, targetState)

    val actions = state.changeStateActions
    assertEquals(3, actions.size)

    assertTrue(actions[0] is FloatInputComponentAction)

    assertTrue(actions[1] is ToolbarLabel)
    assertEquals("to", actions[1].templateText)

    assertTrue(actions[2] is FloatInputComponentAction)
  }

  @Test
  fun testWearTileIntState_changeStateActions() {
    val initialState = 5
    val targetState = 15
    val state = WearTileIntState(initialState, targetState)

    val actions = state.changeStateActions
    assertEquals(3, actions.size)

    assertTrue(actions[0] is IntInputComponentAction)

    assertTrue(actions[1] is ToolbarLabel)
    assertEquals("to", (actions[1] as ToolbarLabel).templateText)

    assertTrue(actions[2] is IntInputComponentAction)
  }

  @Test
  fun testWearTileColorPickerState_changeStateActions() {
    val initialColor = ColorUnit(Color.RED)
    val targetColor = ColorUnit(Color.BLUE)
    val state = WearTileColorPickerState(NoopAnimationTracker, initialColor, targetColor)

    val actions = state.changeStateActions
    assertEquals(4, actions.size)

    // Swap Action
    assertTrue(actions[0] is SwapAction)

    // Initial ColorPickerAction
    assertTrue(actions[1] is ColorPickerAction)
    val initialPickerAction = actions[1] as ColorPickerAction
    assertEquals(initialColor.color, initialPickerAction.currentValue)

    // ToolbarLabel
    assertTrue(actions[2] is ToolbarLabel)
    assertEquals("to", (actions[2] as ToolbarLabel).templateText)

    // Target ColorPickerAction
    assertTrue(actions[3] is ColorPickerAction)
    val targetPickerAction = actions[3] as ColorPickerAction
    assertEquals(targetColor.color, targetPickerAction.currentValue)
  }

  @Test
  fun testNoopAnimationState_updateAnimation() {
    val state = NoopAnimationState
    val animator = TestDynamicTypeAnimator()
    val animation = ProtoAnimation(animator)
    state.updateAnimation(animation)
  }
}
