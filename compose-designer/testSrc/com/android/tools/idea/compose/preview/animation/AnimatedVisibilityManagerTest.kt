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
import com.android.tools.idea.common.scene.render
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
      assertEquals("Enter", toolbar.components[2].findComboBox().text)
    }
  }

  @Test
  fun transitionIsCached() {
    var transitionCalls = 0
    var stateCalls = 0
    val clock =
      object : TestClock() {
        override fun getTransitions(animation: Any, clockTimeMsStep: Long) =
          super.getTransitions(animation, clockTimeMsStep).also { transitionCalls++ }

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          super.updateAnimatedVisibilityState(animation, state)
          stateCalls++
        }
      }

    setupAndCheckToolbar(clock) { toolbar, ui ->
      assertEquals(1, transitionCalls)
      assertEquals(1, stateCalls)
      // Swap
      ui.clickOn(toolbar.components[1])
      delayUntilCondition(200) { transitionCalls == 2 }
      assertEquals(2, transitionCalls)
      assertEquals(2, stateCalls)
      // Swap again
      ui.clickOn(toolbar.components[1])
      assertEquals(2, transitionCalls)
      delayUntilCondition(200) { stateCalls == 3 }
      assertEquals(3, stateCalls)
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
      // call from SupportedAnimationManager.setup and one from offset.collect in setUp
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
    val inspector = createAndOpenInspector()

    val animation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = ComposeAnimationType.ANIMATED_VISIBILITY
        override val states =
          setOf(TestClock.AnimatedVisibilityState.Enter, TestClock.AnimatedVisibilityState.Exit)
      }

    runBlocking {
      surface.sceneManagers.forEach { it.render() }
      ComposeAnimationSubscriber.onAnimationSubscribed(clock, animation).join()

      withContext(uiThread) {
        val ui = FakeUi(inspector.component.apply { size = Dimension(500, 400) })
        ui.updateToolbars()
        ui.layoutAndDispatchEvents()
        val cards = findAllCards(inspector.component)
        assertEquals(1, cards.size)
        val toolbar = cards.first().component.findToolbar("AnimationCard") as JComponent
        checkToolbar(toolbar, ui)
      }
    }
  }
}
