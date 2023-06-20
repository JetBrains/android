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
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JSlider
import kotlin.test.assertEquals
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AnimationManagerTests(private val animationType: ComposeAnimationType) : InspectorTests() {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters() =
      listOf(
        arrayOf(ComposeAnimationType.TRANSITION_ANIMATION),
        arrayOf(ComposeAnimationType.ANIMATE_X_AS_STATE),
        arrayOf(ComposeAnimationType.ANIMATED_CONTENT)
      )
  }

  @Test
  fun intTransitionStates() {
    setupAndCheckToolbar(animationType, setOf(10)) { toolbar, ui ->
      // Freeze, swap, picker state.
      Assert.assertEquals(3, toolbar.componentCount)
      Assert.assertEquals("10 to 10", (toolbar.components[2] as JButton).text)
      ui.clickOn(toolbar.components[1])
      Assert.assertEquals("10 to 10", (toolbar.components[2] as JButton).text)
    }
  }

  @Test
  fun booleanTransitionStates() {
    setupAndCheckToolbar(animationType, setOf(true)) { toolbar, ui ->
      // Freeze, swap, from state, label, to state
      Assert.assertEquals(5, toolbar.componentCount)
      Assert.assertEquals("true", toolbar.components[2].findComboBox().text)
      Assert.assertEquals("false", toolbar.components[4].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      Assert.assertEquals("false", toolbar.components[2].findComboBox().text)
      Assert.assertEquals("true", toolbar.components[4].findComboBox().text)
    }
  }

  @Test
  fun enumTransitionStates() {
    setupAndCheckToolbar(
      animationType,
      setOf(AnimationState.State1, AnimationState.State2, AnimationState.State3)
    ) { toolbar, ui ->
      // Freeze, swap, from state, label, to state
      Assert.assertEquals(5, toolbar.componentCount)
      Assert.assertEquals("State1", toolbar.components[2].findComboBox().text)
      Assert.assertEquals("State2", toolbar.components[4].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      Assert.assertEquals("State2", toolbar.components[2].findComboBox().text)
      Assert.assertEquals("State1", toolbar.components[4].findComboBox().text)
    }
  }

  @Test
  fun stringTransitionStates() {
    setupAndCheckToolbar(animationType, setOf("a")) { toolbar, ui ->
      // Freeze, swap, picker state.
      Assert.assertEquals(3, toolbar.componentCount)
      Assert.assertEquals("a to a", (toolbar.components[2] as JButton).text)
      ui.clickOn(toolbar.components[1])
      Assert.assertEquals("a to a", (toolbar.components[2] as JButton).text)
    }
  }

  @Test
  fun pairTransitionStates() {
    setupAndCheckToolbar(animationType, setOf(Pair(1, 1), Pair(2, 3), Pair(3, 4))) { toolbar, ui ->
      // Freeze, swap, from state, label, to state
      Assert.assertEquals(5, toolbar.componentCount)
      Assert.assertEquals("(1, 1)", toolbar.components[2].findComboBox().text)
      Assert.assertEquals("(2, 3)", toolbar.components[4].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      Assert.assertEquals("(2, 3)", toolbar.components[2].findComboBox().text)
      Assert.assertEquals("(1, 1)", toolbar.components[4].findComboBox().text)
    }
  }

  @Test
  fun booleanAnimateXStates() {
    setupAndCheckToolbar(animationType, setOf(false, true)) { toolbar, ui ->
      // Freeze, swap, from state, label, to state
      Assert.assertEquals(5, toolbar.componentCount)
      Assert.assertEquals("false", toolbar.components[2].findComboBox().text)
      Assert.assertEquals("true", toolbar.components[4].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      Assert.assertEquals("true", toolbar.components[2].findComboBox().text)
      Assert.assertEquals("false", toolbar.components[4].findComboBox().text)
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

        override fun updateFromAndToStates(
          animation: ComposeAnimation,
          fromState: Any,
          toState: Any
        ) = super.updateFromAndToStates(animation, fromState, toState).also { stateCalls++ }
      }

    setupAndCheckToolbar(animationType, setOf("one", "two"), clock) { toolbar, ui ->
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
  fun faultyClock() {
    val clock =
      object : TestClockWithCoordination() {
        override fun updateFromAndToStates(
          animation: ComposeAnimation,
          fromState: Any,
          toState: Any
        ) {
          throw ClassCastException("")
        }
      }

    setupAndCheckToolbar(animationType, setOf("one", "two"), clock) { toolbar, ui ->
      // updateFromAndToStates will be called with Swapt
      ui.clickOn(toolbar.components[1])
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

    setupAndCheckToolbar(animationType, setOf("one", "two"), clock) { _, ui ->
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
    type: ComposeAnimationType,
    states: Set<Any>,
    clock: TestClock? = null,
    checkToolbar: (JComponent, FakeUi) -> Unit,
  ) {
    val inspector = createAndOpenInspector()

    val animation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = type
        override val states = states
      }

    val clock = clock ?: TestClockWithCoordination()

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
