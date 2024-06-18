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
import com.android.tools.idea.compose.preview.animation.NoopComposeAnimationTracker
import com.android.tools.idea.compose.preview.animation.state.PickerButtonAction
import com.android.tools.idea.compose.preview.animation.state.PickerState
import com.android.tools.idea.preview.animation.AnimationUnit
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class PickerStateTest {
  @get:Rule val projectRule = ProjectRule()

  private val tracker = NoopComposeAnimationTracker
  private val mockCallback = mock<() -> Unit>()
  private val pickerState = PickerState(tracker, mockCallback).apply { callbackEnabled = true }

  @Test
  fun testStateHashCode() {
    // Setup
    val initialState = AnimationUnit.IntUnit(1)
    val targetState = AnimationUnit.IntUnit(2)
    pickerState.updateStates(setOf(initialState, targetState))

    // Verify
    val expectedHashCode = Pair(initialState.hashCode(), targetState.hashCode()).hashCode()
    assert(pickerState.stateHashCode() == expectedHashCode)
  }

  @Test
  fun testGetState() {
    // Setup
    val initialState = "0ne"
    val targetState = "Two"
    pickerState.updateStates(setOf(initialState, targetState))

    // Verify
    assert(pickerState.getState(0)[0] == initialState)
    assert(pickerState.getState(1)[0] == targetState)
  }

  @Test
  fun testSetStartState() {
    // Setup
    val state = 1

    pickerState.updateStates(setOf(2, 3))

    // Act
    pickerState.setStartState(state)

    // Verify
    verify(mockCallback).invoke() // Ensure the callback is invoked
    assert(pickerState.getState(0)[0] == 1)
    assert(pickerState.getState(1)[0] == 3)
  }

  @Test
  fun testUpdateStates() {
    // Setup
    val initial = 1
    val target = 2

    // Act
    pickerState.updateStates(setOf(initial, target))

    // Verify
    assert(pickerState.getState(0)[0] == initial)
    assert(pickerState.getState(1)[0] == target)
  }

  @Test
  fun testChangeStateActions_SwapAction() {
    // Setup
    val initial = "one"
    val target = "two"
    pickerState.updateStates(setOf(initial, target))

    val swapAction = pickerState.changeStateActions[0]

    // Act
    swapAction.actionPerformed(TestActionEvent.createTestEvent())

    // Verify
    verify(mockCallback).invoke()
    assert(pickerState.getState(0)[0] == target)
    assert(pickerState.getState(1)[0] == initial)
  }

  @Test
  fun testChangeStateActions_PickerButtonAction() {
    assert(pickerState.changeStateActions[1] is PickerButtonAction)
  }
}
