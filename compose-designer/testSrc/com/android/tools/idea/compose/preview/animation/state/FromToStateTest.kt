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

class FromToStateTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val minimumSize = Dimension(10, 10)

  @RunsInEdt
  @Test
  fun createCard() {
    val state =
      FromToStateComboBox(NoopComposeAnimationTracker, setOf("One", "Two", "Three"), "One")
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
    assertEquals(5, toolbar.components.size)
    // All components are visible
    toolbar.components.forEach { assertBigger(minimumSize, it.size) }
    // Default state.
    assertEquals("One", (toolbar.components[2] as JPanel).findComboBox().text)
    assertEquals("Two", (toolbar.components[4] as JPanel).findComboBox().text)
    val (initial, target) = state.state.value

    assertEquals("One", initial)
    assertEquals("Two", target)
    val hash = state.state.value.hashCode()
    // Swap state.
    ui.clickOn(toolbar.components[1])
    // State hashCode has changed.
    assertNotEquals(hash, state.state.value.hashCode())
    // The states swapped.
    assertEquals("Two", (toolbar.components[2] as JPanel).findComboBox().text)
    assertEquals("One", (toolbar.components[4] as JPanel).findComboBox().text)
    val (initial1, target1) = state.state.value

    assertEquals("Two", initial1)
    assertEquals("One", target1)
  }
}
