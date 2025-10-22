/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimatedProperty
import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.testutils.delayUntilCondition
import com.android.testutils.waitForCondition
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.preview.animation.TestUtils.findComboBox
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.animation.TestUtils.findAllCards
import com.android.tools.idea.preview.animation.TestUtils.findToolbar
import java.awt.Dimension
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JSlider
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedVisibilityManagerTest : InspectorTests() {

  @Test
  fun swapStatesFromStringEnter() = runTest {
    var lastState: Any = TestClock.AnimatedVisibilityState.Enter
    val clock =
      object : TestClock() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) = lastState

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(animationPreview, clock) { toolbar, ui ->
      // Freeze, swap, state.
      assertTrue(lastState is TestClock.AnimatedVisibilityState)
      assertEquals(3, toolbar.componentCount)
      assertEquals("Enter", toolbar.components[2].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      assertTrue(lastState is TestClock.AnimatedVisibilityState)
      delayUntilCondition(200) { toolbar.components[2].findComboBox().text == "Exit" }
      assertEquals("Exit", toolbar.components[2].findComboBox().text)
    }
  }

  @Test
  fun swapStatesFromEnter() = runTest {
    var lastState: Any = TestClock.AnimatedVisibilityState.Enter
    val clock =
      object : TestClock() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) =
          AnimatedVisibilityState.Enter

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(animationPreview, clock) { toolbar, ui ->
      // Freeze, swap, state.
      assertTrue(lastState is TestClock.AnimatedVisibilityState)
      assertEquals(3, toolbar.componentCount)
      assertEquals("Enter", toolbar.components[2].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      assertTrue(lastState is TestClock.AnimatedVisibilityState)
      delayUntilCondition(200) { toolbar.components[2].findComboBox().text == "Exit" }
      assertEquals("Exit", toolbar.components[2].findComboBox().text)
    }
  }

  @Test
  fun swapStateFromStringExit() = runTest {
    var lastState: Any = TestClock.AnimatedVisibilityState.Enter
    val clock =
      object : TestClock() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) = "Exit"

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(animationPreview, clock) { toolbar, ui ->
      // Freeze, swap, state.
      assertTrue(lastState is TestClock.AnimatedVisibilityState)
      assertEquals(3, toolbar.componentCount)
      assertEquals("Exit", toolbar.components[2].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      assertTrue(lastState is TestClock.AnimatedVisibilityState)
      delayUntilCondition(200) { toolbar.components[2].findComboBox().text == "Enter" }
      assertEquals("Enter", toolbar.components[2].findComboBox().text)
    }
  }

  @Test
  fun swapStateFromExit() = runTest {
    var lastState: Any = TestClock.AnimatedVisibilityState.Exit
    val clock =
      object : TestClock() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) =
          AnimatedVisibilityState.Exit

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(animationPreview, clock) { toolbar, ui ->
      // Freeze, swap, state.
      assertTrue(lastState is TestClock.AnimatedVisibilityState)
      assertEquals(3, toolbar.componentCount)
      assertEquals("Exit", toolbar.components[2].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      assertTrue(lastState is TestClock.AnimatedVisibilityState)
      delayUntilCondition(200) { toolbar.components[2].findComboBox().text == "Enter" }
      assertEquals("Enter", toolbar.components[2].findComboBox().text)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun changeTime() = runTest {
    var numberOfCalls = 0
    val clock =
      object : TestClock() {
        override fun getAnimatedProperties(animation: Any): List<ComposeAnimatedProperty> =
          super.getAnimatedProperties(animation).also { numberOfCalls++ }
      }

    val animationPreview = createAnimationPreview(backgroundScope)

    setupAndCheckToolbar(animationPreview, clock) { _, ui ->
      runCurrent()
      waitForCondition(25.seconds) { numberOfCalls == 1 }
      val sliders =
        TreeWalker(ui.root).descendantStream().filter { it is JSlider }.collect(Collectors.toList())
      assertEquals(1, sliders.size)
      val timelineSlider = sliders[0] as JSlider
      // Change time again.
      timelineSlider.value = 100
      runCurrent()
      waitForCondition(10.seconds) { numberOfCalls == 2 }
      assertEquals(2, numberOfCalls)
    }
  }

  private suspend fun setupAndCheckToolbar(
    animationPreview: ComposeAnimationPreview,
    clock: TestClock,
    checkToolbar: suspend (JComponent, FakeUi) -> Unit,
  ) {
    animationPreview.animationClock = AnimationClock(clock)
    val animation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = ComposeAnimationType.ANIMATED_VISIBILITY
        override val states =
          setOf(TestClock.AnimatedVisibilityState.Enter, TestClock.AnimatedVisibilityState.Exit)
      }

    surface.sceneManagers.forEach { it.requestRenderAndWait() }
    animationPreview.addAnimation(animation).join()

    withContext(uiThread) {
      val ui = FakeUi(animationPreview.component.apply { size = Dimension(500, 400) })
      ui.updateToolbars()
      ui.layoutAndDispatchEvents()
      val cards = findAllCards(animationPreview.component)
      assertEquals(1, cards.size)
      val toolbar = cards.first().component.findToolbar("AnimationCard") as JComponent
      checkToolbar(toolbar, ui)
    }
  }
}
