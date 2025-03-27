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
package com.android.tools.idea.wear.preview.animation

import com.android.flags.junit.FlagRule
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.NoopAnimationTracker
import com.android.tools.idea.preview.animation.AnimationTabs
import com.android.tools.idea.preview.animation.PlaybackControls
import com.android.tools.idea.preview.animation.state.SwapAction
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.wear.preview.animation.state.WearTileColorPickerState
import com.android.tools.idea.wear.preview.animation.state.WearTileFloatState
import com.android.tools.idea.wear.preview.animation.state.WearTileIntState
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertThrows
import javax.swing.JComponent
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@RunsInEdt
class SupportedWearTileAnimationManagerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()
  @get:Rule val flagRule = FlagRule(StudioFlags.WEAR_TILE_ANIMATION_INSPECTOR, true)

  private val rootComponent = mock<JComponent>()
  private val tabTitle = "Test Tab"
  private val playbackControls = mock<PlaybackControls>()
  private val updateTimelineElementsCallback = mock<(suspend () -> Unit)>()

  @Test
  fun testLoadTransitionFromLibrary() {
    // Setup
    val animator = TestDynamicTypeAnimator()
    animator.setFloatValues(0f, 100f)
    val animation = ProtoAnimation(animator)

    val manager = createManager(animation)

    // Execute
    val transition = manager.loadTransitionFromLibrary()

    // Verify
    assertThat(transition.properties.size).isEqualTo(1)
    val animatedProperty = transition.properties[1]
    assertThat(animatedProperty).isNotNull()
    assertThat(animatedProperty!!.startMs).isEqualTo(10) // Start delay
    assertThat(transition.startMillis).isEqualTo(10) // Start delay
    assertThat(transition.endMillis).isEqualTo(110) // Duration + start delay
    assertThat(transition.duration).isEqualTo(100)
  }

  @Test
  fun testLoadAnimatedPropertiesAtCurrentTime() = runBlocking {
    // Setup
    val animator = TestDynamicTypeAnimator()
    animator.setFloatValues(0f, 100f)
    val animation = ProtoAnimation(animator)
    animator.setCurrentValue(50f)

    val manager = createManager(animation)

    assertThat(manager.animatedPropertiesAtCurrentTime).isEmpty()

    // Execute
    manager.loadAnimatedPropertiesAtCurrentTime(false)

    // Verify
    assertThat(manager.animatedPropertiesAtCurrentTime[0].unit.components[0]).isEqualTo(50f)
  }

  @Test
  fun testSetupInitialAnimationState() = runBlocking {
    // Setup
    val animator = TestDynamicTypeAnimator()
    animator.setFloatValues(0f, 100f)
    val animation = ProtoAnimation(animator)

    val manager = createManager(animation)

    // Execute
    manager.setupInitialAnimationState()

    // Verify
    assertThat(manager.animationState).isInstanceOf(WearTileFloatState::class.java)
    val floatState = manager.animationState as WearTileFloatState
    assertThat(floatState.state.value.first).isEqualTo(0f)
    assertThat(floatState.state.value.second).isEqualTo(100f)
  }

  @Test
  fun testSyncAnimationWithState() = runBlocking {
    // Setup
    val animator = TestDynamicTypeAnimator()
    animator.setFloatValues(0f, 100f)
    val animation = ProtoAnimation(animator)

    val manager = createManager(animation)
    manager.setupInitialAnimationState() // Initialize animationState

    val floatState = manager.animationState as WearTileFloatState
    floatState.state.value = 50f to 150f // Update the state directly

    // Execute
    manager.syncAnimationWithState()

    // Verify
    assertThat(animation.startValueFloat).isEqualTo(50f)
    assertThat(animation.endValueFloat).isEqualTo(150f)
  }

  @Test
  fun testLoadTransitionFromLibrary_NullValue() {
    // Setup
    val animator = TestDynamicTypeAnimator()
    val animation = ProtoAnimation(animator)

    val manager = createManager(animation)

    assertThat(animator.getCurrentValue()).isNull()
    // Execute & Verify (Expect an exception)
    assertThrows(IllegalStateException::class.java) { manager.loadTransitionFromLibrary() }
  }

  @Test
  fun testLoadTransitionFromLibrary_IntAnimation() = runBlocking {
    // Setup
    val animator = TestDynamicTypeAnimator(ProtoAnimation.TYPE.INT)
    animator.setIntValues(10, 50)
    val animation = ProtoAnimation(animator)

    val manager = createManager(animation)

    // Execute
    val transition = manager.loadTransitionFromLibrary()

    // Verify
    assertThat(transition.properties.size).isEqualTo(1)
    val animatedProperty = transition.properties[1]
    assertThat(animatedProperty).isNotNull()
    assertThat(animatedProperty!!.startMs).isEqualTo(10) // Start delay
    assertThat(transition.startMillis).isEqualTo(10) // Start delay
    assertThat(transition.endMillis).isEqualTo(110) // Duration + start delay
    assertThat(transition.duration).isEqualTo(100)

    val values = animatedProperty.components[0].points
    assertThat(values.size).isEqualTo(100)
    assertThat(values[10]).isEqualTo(10.0) // Start value (converted to Double)
  }

  @Test
  fun testLoadAnimatedPropertiesAtCurrentTime_IntAnimation() = runBlocking {
    // Setup
    val animator = TestDynamicTypeAnimator(ProtoAnimation.TYPE.INT)
    animator.setIntValues(0, 100)
    val animation = ProtoAnimation(animator)
    animator.setCurrentValue(50)

    val manager = createManager(animation)

    assertThat(manager.animatedPropertiesAtCurrentTime).isEmpty()

    // Execute
    manager.loadAnimatedPropertiesAtCurrentTime(false)

    // Verify
    assertThat(manager.animatedPropertiesAtCurrentTime[0].unit.components[0]).isEqualTo(50)
  }

  @Test
  fun testSwapColors() = runBlocking {
    // Setup
    val animator = TestDynamicTypeAnimator(ProtoAnimation.TYPE.COLOR)
    animator.setIntValues(0xFF0000, 0x0000FF) // Red to Blue
    val animation = ProtoAnimation(animator)

    val manager = createManager(animation)
    manager.setupInitialAnimationState() // Initialize animationState
    val colorState = manager.animationState as WearTileColorPickerState

    val swapAction = colorState.changeStateActions[0] as SwapAction
    swapAction.actionPerformed(mock())
    manager.syncAnimationWithState()
    assertThat(animator.getIntValues()[0]).isEqualTo(0x0000FF)
    assertThat(animator.getIntValues()[1]).isEqualTo(0xFF0000)
    // swap again
    swapAction.actionPerformed(mock())
    manager.syncAnimationWithState()
    assertThat(animator.getIntValues()[0]).isEqualTo(0xFF0000)
    assertThat(animator.getIntValues()[1]).isEqualTo(0x0000FF)
  }

  @Test
  fun testSetupInitialAnimationState_IntAnimation() = runBlocking {
    // Setup
    val animator = TestDynamicTypeAnimator(ProtoAnimation.TYPE.INT)
    animator.setIntValues(0, 100)
    val animation = ProtoAnimation(animator)

    val manager = createManager(animation)

    // Execute
    manager.setupInitialAnimationState()

    // Verify
    assertThat(manager.animationState).isInstanceOf(WearTileIntState::class.java)
    val intState = manager.animationState as WearTileIntState
    assertThat(intState.state.value.first).isEqualTo(0)
    assertThat(intState.state.value.second).isEqualTo(100)
  }

  @Test
  fun testSyncAnimationWithState_IntAnimation() = runBlocking {
    // Setup
    val animator = TestDynamicTypeAnimator(ProtoAnimation.TYPE.INT)
    animator.setIntValues(0, 100)
    val animation = ProtoAnimation(animator)

    val manager = createManager(animation)
    manager.setupInitialAnimationState() // Initialize animationState

    val intState = manager.animationState as WearTileIntState
    intState.state.value = 50 to 150 // Update the state directly

    // Execute
    manager.syncAnimationWithState()

    // Verify
    assertThat(animation.startValueInt).isEqualTo(50)
    assertThat(animation.endValueInt).isEqualTo(150)
  }

  @Test
  fun testLoadTransitionFromLibrary_ColorAnimation() = runBlocking {
    // Setup
    val animator = TestDynamicTypeAnimator(ProtoAnimation.TYPE.COLOR)
    animator.setIntValues(0xFF0000, 0x0000FF) // Red to Blue
    val animation = ProtoAnimation(animator)

    val manager = createManager(animation)
    manager.setupInitialAnimationState() // Initialize animationState

    // Execute
    val transition = manager.loadTransitionFromLibrary()

    // Verify
    assertThat(transition.properties.size).isEqualTo(1)
    val animatedProperty = transition.properties[1]
    assertThat(animatedProperty).isNotNull()
    assertThat(animatedProperty!!.startMs).isEqualTo(10) // Start delay
    assertThat(transition.startMillis).isEqualTo(10) // Start delay
    assertThat(transition.endMillis).isEqualTo(110) // Duration + start delay
    assertThat(transition.duration).isEqualTo(100)

    // Check values within the animatedProperty (specific to Color animation)
    val values = animatedProperty.components[0].points // Red color
    assertThat(values.size).isEqualTo(100)
    assertThat(values[10]).isEqualTo(255.0) // Start value (Red chanel of Red)
  }

  private fun createManager(animation: ProtoAnimation) =
    SupportedWearTileAnimationManager(
      animation,
      NoopAnimationTracker,
      getCurrentTime = { 0 }, // Simplified for testing,
      { _, runnable -> runnable.invoke() },
      AnimationTabs(projectRule.project, projectRule.testRootDisposable),
      rootComponent,
      tabTitle,
      playbackControls,
      updateTimelineElementsCallback,
      AndroidCoroutineScope(projectRule.testRootDisposable),
      setClockTime = { _, _ -> },
    )
}
