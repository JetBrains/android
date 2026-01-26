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
import com.android.tools.idea.preview.animation.TestUtils.findAllCards
import com.android.tools.idea.preview.animation.TestUtils.findToolbar
import com.intellij.openapi.application.EDT
import java.awt.Dimension
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JSlider
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedVisibilityManagerTest : AnimationPreviewTests() {

  @Test fun swapStatesFromStringEnter() = runTest { swapStates("Enter", "Enter", "Exit") }

  @Test
  fun swapStatesFromEnter() = runTest {
    swapStates(TestClock.AnimatedVisibilityState.Enter, "Enter", "Exit")
  }

  @Test fun swapStateFromStringExit() = runTest { swapStates("Exit", "Exit", "Enter") }

  @Test
  fun swapStateFromExit() = runTest {
    swapStates(TestClock.AnimatedVisibilityState.Exit, "Exit", "Enter")
  }

  private fun swapStates(initialState: Any, initialText: String, newText: String) = runTest {
    var lastState = initialState
    val clock =
      object : TestClock() {
        override fun `getAnimatedVisibilityState-xga21d`(animation: Any) = initialState

        override fun updateAnimatedVisibilityState(animation: Any, state: Any) {
          lastState = state
          super.updateAnimatedVisibilityState(animation, state)
        }
      }
    setupAndCheckToolbar(animationPreview, clock) { toolbar, ui ->
      assertTrue(lastState is TestClock.AnimatedVisibilityState)
      assertEquals(3, toolbar.componentCount)
      assertEquals(initialText, toolbar.components[2].findComboBox().text)

      // Swap states
      ui.clickOn(toolbar.components[1])
      ui.layoutAndDispatchEvents()
      ui.updateToolbarsIfNecessary()
      assertTrue(lastState is TestClock.AnimatedVisibilityState)
      delayUntilCondition(200) { toolbar.components[2].findComboBox().text == newText }
      assertEquals(newText, toolbar.components[2].findComboBox().text)
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
      advanceUntilIdle()
      waitForCondition(60.seconds) { numberOfCalls == 1 }
      val sliders =
        TreeWalker(ui.root).descendantStream().filter { it is JSlider }.collect(Collectors.toList())
      assertEquals(1, sliders.size)
      val timelineSlider = sliders[0] as JSlider
      // Change time again.
      timelineSlider.value = 100
      ui.updateToolbarsIfNecessary()
      ui.layoutAndDispatchEvents()
      runCurrent()
      advanceUntilIdle()
      waitForCondition(10.seconds) { numberOfCalls == 2 }
      assertEquals(2, numberOfCalls)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
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

    withContext(Dispatchers.EDT) {
      val ui = FakeUi(animationPreview.component.apply { size = Dimension(500, 400) })
      ui.layoutAndDispatchEvents()
      ui.updateToolbarsIfNecessary()
      val cards = findAllCards(animationPreview.component)
      assertEquals(1, cards.size)
      val toolbar = cards.first().component.findToolbar("AnimationCard") as JComponent
      checkToolbar(toolbar, ui)
    }
  }
}
