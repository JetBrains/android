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
import com.android.tools.idea.preview.animation.AnimationUnit
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
    val pickerState = PickerState(tracker, 1, 2)

    // Verify
    val (initial, target) = pickerState.state.value
    val initialState = AnimationUnit.IntUnit(1)
    val targetState = AnimationUnit.IntUnit(2)
    val expectedHashCode = Pair(initialState.hashCode(), targetState.hashCode()).hashCode()
    delayUntilCondition(200) { pickerState.state.value.hashCode() == expectedHashCode }
    assertThat(pickerState.state.value.hashCode()).isEqualTo(expectedHashCode)
  }

  @Test
  fun testGetState() {
    // Setup
    val initialState = "0ne"
    val targetState = "Two"
    val pickerState = PickerState(tracker, initialState, targetState)

    // Verify
    val (initial, target) = pickerState.state.value

    assertThat(initial.components[0]).isEqualTo(initialState)
    assertThat(target.components[0]).isEqualTo(targetState)
  }

  @Test
  fun testChangeStateActions_SwapAction() {
    // Setup
    val initialState = "one"
    val targetState = "two"
    val pickerState = PickerState(tracker, initialState, targetState)

    val swapAction = pickerState.changeStateActions[0]

    // Act
    swapAction.actionPerformed(TestActionEvent.createTestEvent())

    // Verify
    val (initial, target) = pickerState.state.value
    assertThat(initial.components[0]).isEqualTo(targetState)
    assertThat(target.components[0]).isEqualTo(initialState)
  }

  @Test
  fun testChangeStateActions_PickerButtonAction() {
    val pickerState = PickerState(tracker, null, null)
    assertThat(pickerState.changeStateActions[1] is PickerButtonAction)
  }
}
