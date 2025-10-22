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
import com.android.testutils.retryUntilPassing
import com.android.testutils.waitForCondition
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.preview.animation.TestUtils.findComboBox
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ComposeAnimationTypeTests(private val animationType: ComposeAnimationType) :
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
  fun intTransitionStates() = runTest {
    setupAndCheckToolbar(animationPreview, animationType, setOf(10)) { toolbar, ui ->
      // Freeze, swap, picker state.
      assertEquals(3, toolbar.componentCount)
      assertEquals("10 to 10", (toolbar.components[2] as JButton).text)
      ui.clickOn(toolbar.components[1])
      assertEquals("10 to 10", (toolbar.components[2] as JButton).text)
    }
  }

  @Test
  fun booleanTransitionStates() = runTest {
    setupAndCheckToolbar(animationPreview, animationType, setOf(true)) { toolbar, ui ->
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
  fun enumTransitionStates() = runTest {
    setupAndCheckToolbar(
      animationPreview,
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
  fun stringTransitionStates() = runTest {
    setupAndCheckToolbar(animationPreview, animationType, setOf("a")) { toolbar, ui ->
      // Freeze, swap, picker state.
      assertEquals(3, toolbar.componentCount)
      assertEquals("a to a", (toolbar.components[2] as JButton).text)
      ui.clickOn(toolbar.components[1])
      assertEquals("a to a", (toolbar.components[2] as JButton).text)
    }
  }

  @Test
  fun pairTransitionStates() = runTest {
    setupAndCheckToolbar(
      animationPreview,
      animationType,
      setOf(Pair(1, 1), Pair(2, 3), Pair(3, 4)),
    ) { toolbar, ui ->
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
  fun booleanAnimateXStates() = runTest {
    setupAndCheckToolbar(animationPreview, animationType, setOf(false, true)) { toolbar, ui ->
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

    animationPreview.animationClock = AnimationClock(clock)

    val animation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = animationType
        override val states = setOf("one", "two")
      }

    var ui: FakeUi
    runBlocking {
      surface.sceneManagers.forEach { it.requestRenderAndWait() }
      animationPreview.addAnimation(animation).join()
      withContext(uiThread) {
        ui = FakeUi(animationPreview.component.apply { size = Dimension(500, 400) })
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

    setupAndCheckToolbar(animationPreview, animationType, setOf("one", "two"), clock) { _, ui ->
      // Two calls from AnimationManager.setup
      runCurrent()
      waitForCondition(10.seconds) { numberOfCalls == 1 }

      val sliders =
        TreeWalker(ui.root).descendantStream().filter { it is JSlider }.collect(Collectors.toList())
      assertEquals(1, sliders.size)
      // Change time
      val timelineSlider = sliders[0] as JSlider
      timelineSlider.value = 100
      runCurrent()
      waitForCondition(10.seconds) { numberOfCalls == 2 }
      assertEquals(2, numberOfCalls)
      // Change time again.
      timelineSlider.value = 200
      runCurrent()
      waitForCondition(10.seconds) { numberOfCalls == 3 }
      assertEquals(3, numberOfCalls)
    }
  }

  private suspend fun setupAndCheckToolbar(
    animationPreview: ComposeAnimationPreview,
    type: ComposeAnimationType,
    states: Set<Any>,
    clock: TestClock = TestClock(),
    checkToolbar: suspend (JComponent, FakeUi) -> Unit,
  ) {
    animationPreview.animationClock = AnimationClock(clock)

    val animation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = type
        override val states = states
      }

    surface.sceneManagers.forEach { it.requestRenderAndWait() }
    animationPreview.addAnimation(animation).join()
    assertTrue("No animation is added", 1 == animationPreview.animations.size)
    withContext(uiThread) {
      val ui = FakeUi(animationPreview.component.apply { size = Dimension(500, 400) })
      ui.updateToolbars()
      ui.layout()
      val cards = findAllCards(animationPreview.component)
      assertEquals(1, cards.size)
      val toolbar = cards.first().component.findToolbar("AnimationCard") as JComponent
      checkToolbar(toolbar, ui)
    }
  }
}
