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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimatedProperty
import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.testutils.delayUntilCondition
import com.android.testutils.retryUntilPassing
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
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import junit.framework.TestCase.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ComposeAnimationManagerTests(private val animationType: ComposeAnimationType) :
  InspectorTests() {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters() =
      listOf(
        arrayOf(ComposeAnimationType.TRANSITION_ANIMATION),
        arrayOf(ComposeAnimationType.ANIMATE_X_AS_STATE),
        arrayOf(ComposeAnimationType.ANIMATED_CONTENT),
      )
  }

  @Test
  fun intTransitionStates() {
    setupAndCheckToolbar(animationType, setOf(10)) { toolbar, ui ->
      // Freeze, swap, picker state.
      assertEquals(3, toolbar.componentCount)
      assertEquals("10 to 10", (toolbar.components[2] as JButton).text)
      ui.clickOn(toolbar.components[1])
      assertEquals("10 to 10", (toolbar.components[2] as JButton).text)
    }
  }

  @Test
  fun booleanTransitionStates() {
    setupAndCheckToolbar(animationType, setOf(true)) { toolbar, ui ->
      // Freeze, swap, from state, label, to state
      assertEquals(5, toolbar.componentCount)
      assertEquals("true", toolbar.components[2].findComboBox().text)
      assertEquals("false", toolbar.components[4].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      retryUntilPassing(5.seconds) {
        assertEquals("false", toolbar.components[2].findComboBox().text)
      }
      assertEquals("true", toolbar.components[4].findComboBox().text)
    }
  }

  @Test
  fun enumTransitionStates() {
    setupAndCheckToolbar(
      animationType,
      setOf(AnimationState.State1, AnimationState.State2, AnimationState.State3),
    ) { toolbar, ui ->
      // Freeze, swap, from state, label, to state
      assertEquals(5, toolbar.componentCount)
      assertEquals("State1", toolbar.components[2].findComboBox().text)
      assertEquals("State2", toolbar.components[4].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      retryUntilPassing(5.seconds) {
        assertEquals("State2", toolbar.components[2].findComboBox().text)
      }
      assertEquals("State1", toolbar.components[4].findComboBox().text)
    }
  }

  @Test
  fun stringTransitionStates() {
    setupAndCheckToolbar(animationType, setOf("a")) { toolbar, ui ->
      // Freeze, swap, picker state.
      assertEquals(3, toolbar.componentCount)
      assertEquals("a to a", (toolbar.components[2] as JButton).text)
      ui.clickOn(toolbar.components[1])
      assertEquals("a to a", (toolbar.components[2] as JButton).text)
    }
  }

  @Test
  fun pairTransitionStates() {
    setupAndCheckToolbar(animationType, setOf(Pair(1, 1), Pair(2, 3), Pair(3, 4))) { toolbar, ui ->
      // Freeze, swap, from state, label, to state
      assertEquals(5, toolbar.componentCount)
      assertEquals("(1, 1)", toolbar.components[2].findComboBox().text)
      assertEquals("(2, 3)", toolbar.components[4].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      retryUntilPassing(5.seconds) {
        assertEquals("(2, 3)", toolbar.components[2].findComboBox().text)
      }
      assertEquals("(1, 1)", toolbar.components[4].findComboBox().text)
    }
  }

  @Test
  fun booleanAnimateXStates() {
    setupAndCheckToolbar(animationType, setOf(false, true)) { toolbar, ui ->
      // Freeze, swap, from state, label, to state
      assertEquals(5, toolbar.componentCount)
      assertEquals("false", toolbar.components[2].findComboBox().text)
      assertEquals("true", toolbar.components[4].findComboBox().text)
      ui.clickOn(toolbar.components[1])
      retryUntilPassing(5.seconds) {
        assertEquals("true", toolbar.components[2].findComboBox().text)
      }
      assertEquals("false", toolbar.components[4].findComboBox().text)
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

        override fun updateFromAndToStates(
          animation: ComposeAnimation,
          fromState: Any,
          toState: Any,
        ) = super.updateFromAndToStates(animation, fromState, toState).also { stateCalls++ }
      }

    setupAndCheckToolbar(animationType, setOf("one", "two"), clock) { toolbar, ui ->
      withContext(workerThread) { delayUntilCondition(200) { transitionCalls == 1 } }
      assertEquals(1, transitionCalls)
      withContext(workerThread) { delayUntilCondition(200) { stateCalls == 1 } }
      assertEquals(1, stateCalls)
      // Swap
      ui.clickOn(toolbar.components[1])
      withContext(workerThread) { delayUntilCondition(200) { transitionCalls == 2 } }
      assertEquals(2, transitionCalls)
      assertEquals(2, stateCalls)
      // Swap again
      ui.clickOn(toolbar.components[1])
      assertEquals(2, transitionCalls)
      withContext(workerThread) { delayUntilCondition(200) { stateCalls == 3 } }
      assertEquals(3, stateCalls)
    }
  }

  @Test
  fun `show error panel on faulty clock`() {
    var numberOfCalls = 0

    val clock =
      object : TestClock() {
        override fun updateFromAndToStates(
          animation: ComposeAnimation,
          fromState: Any,
          toState: Any,
        ) {
          numberOfCalls++
          throw ClassCastException("updateFromAndToStates fails")
        }
      }

    val inspector = createAndOpenInspector()

    val animation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = animationType
        override val states = setOf("one", "two")
      }

    var ui: FakeUi
    runBlocking {
      surface.sceneManagers.forEach { it.render() }
      ComposeAnimationSubscriber.onAnimationSubscribed(clock, animation).join()
      withContext(uiThread) {
        ui = FakeUi(inspector.component.apply { size = Dimension(500, 400) })
        ui.updateToolbars()
        ui.layout()
      }
    }
    retryUntilPassing(10.seconds) {
      assertEquals(
        true,
        TreeWalker(ui.root)
          .descendantStream()
          .filter { it is JPanel && it.name == "Error Panel" }
          .findFirst()
          .isPresent,
      )
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

    setupAndCheckToolbar(animationType, setOf("one", "two"), clock) { _, ui ->
      // one from AnimationManager.setup and one from offset.collect
      withContext(workerThread) { delayUntilCondition(200) { numberOfCalls == 2 } }
      val sliders =
        TreeWalker(ui.root).descendantStream().filter { it is JSlider }.collect(Collectors.toList())
      assertEquals(1, sliders.size)
      val timelineSlider = sliders[0] as JSlider
      timelineSlider.value = 100
      withContext(workerThread) { delayUntilCondition(200) { numberOfCalls == 3 } }
      assertEquals(3, numberOfCalls)
      timelineSlider.value = 200
      withContext(workerThread) { delayUntilCondition(200) { numberOfCalls == 4 } }
      assertEquals(4, numberOfCalls)
    }
  }

  private fun setupAndCheckToolbar(
    type: ComposeAnimationType,
    states: Set<Any>,
    clock: TestClock = TestClock(),
    checkToolbar: suspend (JComponent, FakeUi) -> Unit,
  ) {
    val inspector = createAndOpenInspector()

    val animation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = type
        override val states = states
      }

    runBlocking {
      surface.sceneManagers.forEach { it.render() }
      ComposeAnimationSubscriber.onAnimationSubscribed(clock, animation).join()
      assertTrue("No animation is added", 1 == inspector.animations.size)
      withContext(uiThread) {
        val ui = FakeUi(inspector.component.apply { size = Dimension(500, 400) })
        ui.updateToolbars()
        ui.layout()
        val cards = findAllCards(inspector.component)
        assertEquals(1, cards.size)
        val toolbar = cards.first().component.findToolbar("AnimationCard") as JComponent
        checkToolbar(toolbar, ui)
      }
    }
  }
}
