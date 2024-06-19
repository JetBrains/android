/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.state

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.animation.NoopComposeAnimationTracker
import com.android.tools.idea.compose.preview.animation.TestUtils.assertBigger
import com.android.tools.idea.compose.preview.animation.TestUtils.findComboBox
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.preview.NoopAnimationTracker
import com.android.tools.idea.preview.animation.AnimationCard
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.animation.TestUtils.createTestSlider
import com.android.tools.idea.preview.animation.TestUtils.findToolbar
import com.android.tools.idea.preview.animation.actions.FreezeAction
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.testFramework.RunsInEdt
import java.awt.Dimension
import javax.swing.JPanel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class SingleStateTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val minimumSize = Dimension(10, 10)

  @RunsInEdt
  @Test
  fun createCard() {
    val state =
      SingleState(
          NoopComposeAnimationTracker,
          AndroidCoroutineScope(projectRule.testRootDisposable),
        )
        .apply {
          updateStates(setOf("One", "Two", "Three"))
          setStartState("One")
        }
    val card =
      AnimationCard(
          Mockito.mock(DesignSurface::class.java),
          "Title",
          listOf(
            FreezeAction(
              createTestSlider(),
              MutableStateFlow(SupportedAnimationManager.FrozenState(false)),
              NoopAnimationTracker,
            )
          ) + state.changeStateActions,
          NoopComposeAnimationTracker,
        )
        .apply { size = Dimension(300, 300) }

    val ui =
      FakeUi(card).apply {
        updateToolbars()
        layoutAndDispatchEvents()
      }

    val toolbar = card.findToolbar("AnimationCard")
    assertEquals(3, toolbar.components.size)
    // All components are visible
    toolbar.components.forEach { assertBigger(minimumSize, it.size) }
    // Default state.
    assertEquals("One", (toolbar.components[2] as JPanel).findComboBox().text)
    assertEquals("One", state.getState(0))
    val hash = state.stateHashCode.value
    // Swap state.
    ui.clickOn(toolbar.components[1])
    // State hashCode has changed.
    assertNotEquals(hash, state.stateHashCode.value)
    // The states swapped.
    assertEquals("Two", (toolbar.components[2] as JPanel).findComboBox().text)
    assertEquals("Two", state.getState(0))
    // Update states.
    state.updateStates(setOf("Four", "Five", "Six"))
    state.setStartState("Four")
    ui.updateToolbars()
    assertEquals("Four", (toolbar.components[2] as JPanel).findComboBox().text)
    assertEquals("Four", state.getState(0))
    // Swap state.
    ui.clickOn(toolbar.components[1])
    assertEquals("Five", (toolbar.components[2] as JPanel).findComboBox().text)
    assertEquals("Five", state.getState(0))
  }
}
