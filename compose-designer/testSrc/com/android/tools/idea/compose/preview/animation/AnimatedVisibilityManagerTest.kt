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
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.preview.animation.TestUtils.findAllCards
import com.android.tools.idea.compose.preview.animation.TestUtils.findComboBox
import com.android.tools.idea.compose.preview.animation.TestUtils.findToolbar
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JSlider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assert
import org.junit.Test

class AnimatedVisibilityManagerTest : InspectorTests() {

  @Test
  fun swapStatesFromStringEnter() {
    var lastState: Any? = null
    val clock =
      object : TestClockWithCoordination() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) = "Enter"

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(clock) { toolbar, ui ->
      // Freeze, swap, state.
      assertTrue { lastState is TestClock.AnimatedVisibilityState }
      Assert.assertEquals(3, toolbar.componentCount)
      Assert.assertEquals("Enter", toolbar.components[2].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      assertTrue { lastState is TestClock.AnimatedVisibilityState }
      Assert.assertEquals("Exit", toolbar.components[2].findComboBox().text)
    }
  }

  @Test
  fun swapStatesFromEnter() {
    var lastState: Any? = null
    val clock =
      object : TestClockWithCoordination() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) =
          AnimatedVisibilityState.Enter

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(clock) { toolbar, ui ->
      // Freeze, swap, state.
      assertTrue { lastState is TestClock.AnimatedVisibilityState }
      Assert.assertEquals(3, toolbar.componentCount)
      Assert.assertEquals("Enter", toolbar.components[2].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      assertTrue { lastState is TestClock.AnimatedVisibilityState }
      Assert.assertEquals("Exit", toolbar.components[2].findComboBox().text)
    }
  }

  @Test
  fun swapStateFromStringExit() {
    var lastState: Any? = null
    val clock =
      object : TestClockWithCoordination() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) = "Exit"

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(clock) { toolbar, ui ->
      // Freeze, swap, state.
      assertTrue { lastState is TestClock.AnimatedVisibilityState }
      Assert.assertEquals(3, toolbar.componentCount)
      Assert.assertEquals("Exit", toolbar.components[2].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      assertTrue { lastState is TestClock.AnimatedVisibilityState }
      Assert.assertEquals("Enter", toolbar.components[2].findComboBox().text)
    }
  }

  @Test
  fun swapStateFromExit() {
    var lastState: Any? = null
    val clock =
      object : TestClockWithCoordination() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) =
          AnimatedVisibilityState.Exit

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(clock) { toolbar, ui ->
      // Freeze, swap, state.
      assertTrue { lastState is TestClock.AnimatedVisibilityState }
      Assert.assertEquals(3, toolbar.componentCount)
      Assert.assertEquals("Exit", toolbar.components[2].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      assertTrue { lastState is TestClock.AnimatedVisibilityState }
      Assert.assertEquals("Enter", toolbar.components[2].findComboBox().text)
    }
  }

  @Test
  fun transitionIsCached() {
    var transitionCalls = 0
    var stateCalls = 0
    val clock =
      object : TestClockWithCoordination() {
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
      assertEquals(2, transitionCalls)
      assertEquals(2, stateCalls)
      // Swap again
      ui.clickOn(toolbar.components[1])
      assertEquals(2, transitionCalls)
      assertEquals(3, stateCalls)
    }
  }

  @Test
  fun changeTime() {
    var numberOfCalls = 0
    val clock =
      object : TestClockWithCoordination() {
        override fun getAnimatedProperties(animation: Any): List<ComposeAnimatedProperty> =
          super.getAnimatedProperties(animation).also { numberOfCalls++ }
      }

    setupAndCheckToolbar(clock) { _, ui ->
      assertEquals(1, numberOfCalls)
      val sliders =
        TreeWalker(ui.root).descendantStream().filter { it is JSlider }.collect(Collectors.toList())
      Assert.assertEquals(1, sliders.size)
      val timelineSlider = sliders[0] as JSlider
      timelineSlider.value = 100
      assertEquals(2, numberOfCalls)
      timelineSlider.value = 200
      assertEquals(3, numberOfCalls)
    }
  }

  private fun setupAndCheckToolbar(
    clock: TestClock,
    checkToolbar: (JComponent, FakeUi) -> Unit,
  ) {
    val inspector = createAndOpenInspector()

    val animation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = ComposeAnimationType.ANIMATED_VISIBILITY
        override val states =
          setOf(TestClock.AnimatedVisibilityState.Enter, TestClock.AnimatedVisibilityState.Exit)
      }

    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    invokeAndWaitIfNeeded {
      val ui = FakeUi(inspector.component.apply { size = Dimension(500, 400) })
      ui.updateToolbars()
      ui.layoutAndDispatchEvents()
      val cards = findAllCards(inspector.component)
      Assert.assertEquals(1, cards.size)
      val toolbar = cards.first().component.findToolbar("AnimationCard") as JComponent
      checkToolbar(toolbar, ui)
    }
  }
}
