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
import com.android.testutils.delayUntilCondition
import com.android.tools.idea.compose.preview.animation.NoopComposeAnimationTracker
import com.android.tools.idea.compose.preview.animation.state.PickerButtonAction
import com.android.tools.idea.compose.preview.animation.state.PickerState
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class PickerStateTest {
  @get:Rule val projectRule = ProjectRule()

  private val tracker = NoopComposeAnimationTracker

  @Test
  fun testStateHashCode() = runBlocking {
    // Setup
    val pickerState = PickerState(tracker, AndroidCoroutineScope(projectRule.disposable))
    pickerState.updateStates(setOf(1, 2))

    // Verify
    val initialState = AnimationUnit.IntUnit(1)
    val targetState = AnimationUnit.IntUnit(2)
    val expectedHashCode = Pair(initialState.hashCode(), targetState.hashCode()).hashCode()
    delayUntilCondition(200) { pickerState.stateHashCode.value == expectedHashCode }
    assertThat(pickerState.stateHashCode.value).isEqualTo(expectedHashCode)
  }

  @Test
  fun testGetState() {
    // Setup
    val pickerState = PickerState(tracker, AndroidCoroutineScope(projectRule.disposable))
    val initialState = "0ne"
    val targetState = "Two"
    pickerState.updateStates(setOf(initialState, targetState))

    // Verify
    assertThat(pickerState.getState(0)[0]).isEqualTo(initialState)
    assertThat(pickerState.getState(1)[0]).isEqualTo(targetState)
  }

  @Test
  fun testSetStartState() {
    // Setup
    val pickerState = PickerState(tracker, AndroidCoroutineScope(projectRule.disposable))
    val state = 1

    pickerState.updateStates(setOf(2, 3))

    // Act
    pickerState.setStartState(state)

    // Verify
    assertThat(pickerState.getState(0)[0]).isEqualTo(1)
    assertThat(pickerState.getState(1)[0]).isEqualTo(3)
  }

  @Test
  fun testUpdateStates() {
    // Setup
    val pickerState = PickerState(tracker, AndroidCoroutineScope(projectRule.disposable))
    val initial = 1
    val target = 2

    // Act
    pickerState.updateStates(setOf(initial, target))

    // Verify
    assertThat(pickerState.getState(0)[0]).isEqualTo(initial)
    assertThat(pickerState.getState(1)[0]).isEqualTo(target)
  }

  @Test
  fun testChangeStateActions_SwapAction() {
    // Setup
    val pickerState = PickerState(tracker, AndroidCoroutineScope(projectRule.disposable))
    val initial = "one"
    val target = "two"
    pickerState.updateStates(setOf(initial, target))

    val swapAction = pickerState.changeStateActions[0]

    // Act
    swapAction.actionPerformed(TestActionEvent.createTestEvent())

    // Verify
    assertThat(pickerState.getState(0)[0]).isEqualTo(target)
    assertThat(pickerState.getState(1)[0]).isEqualTo(initial)
  }

  @Test
  fun testChangeStateActions_PickerButtonAction() {
    val pickerState = PickerState(tracker, AndroidCoroutineScope(projectRule.disposable))
    assertThat(pickerState.changeStateActions[1] is PickerButtonAction)
  }
}
