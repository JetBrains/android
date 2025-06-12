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
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.preview.animation.TestUtils.findComboBox
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.preview.animation.TestUtils.findAllCards
import com.android.tools.idea.preview.animation.TestUtils.findToolbar
import java.awt.Dimension
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JSlider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedVisibilityManagerTest : InspectorTests() {

  @Test
  fun swapStatesFromStringEnter() {
    var lastState: Any = TestClock.AnimatedVisibilityState.Enter
    val clock =
      object : TestClock() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) = lastState

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(clock) { toolbar, ui ->
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
  fun swapStatesFromEnter() {
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
    setupAndCheckToolbar(clock) { toolbar, ui ->
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
  fun swapStateFromStringExit() {
    var lastState: Any = TestClock.AnimatedVisibilityState.Enter
    val clock =
      object : TestClock() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) = "Exit"

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(clock) { toolbar, ui ->
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
  fun swapStateFromExit() {
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
    setupAndCheckToolbar(clock) { toolbar, ui ->
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
  fun changeTime() {
    var numberOfCalls = 0
    val clock =
      object : TestClock() {
        override fun getAnimatedProperties(animation: Any): List<ComposeAnimatedProperty> =
          super.getAnimatedProperties(animation).also { numberOfCalls++ }
      }

    setupAndCheckToolbar(clock) { _, ui ->
      // 2 calls from SupportedAnimationManager.setup
      withContext(workerThread) { delayUntilCondition(200) { numberOfCalls == 2 } }
      val sliders =
        TreeWalker(ui.root).descendantStream().filter { it is JSlider }.collect(Collectors.toList())
      assertEquals(1, sliders.size)
      val timelineSlider = sliders[0] as JSlider
      timelineSlider.value = 100
      withContext(workerThread) { delayUntilCondition(200) { numberOfCalls == 3 } }
      assertEquals(3, numberOfCalls)
    }
  }

  private fun setupAndCheckToolbar(
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

    runBlocking {
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
}
