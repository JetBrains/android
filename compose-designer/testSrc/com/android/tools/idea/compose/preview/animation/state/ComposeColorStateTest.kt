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
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.NoopComposeAnimationTracker
import com.android.tools.idea.compose.preview.animation.TestUtils.assertBigger
import com.android.tools.idea.preview.NoopAnimationTracker
import com.android.tools.idea.preview.animation.AnimationCard
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.animation.TestUtils.createTestSlider
import com.android.tools.idea.preview.animation.TestUtils.findToolbar
import com.android.tools.idea.preview.animation.actions.FreezeAction
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.testFramework.RunsInEdt
import java.awt.Color
import java.awt.Dimension
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class ComposeColorStateTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val minimumSize = Dimension(10, 10)

  @RunsInEdt
  @Test
  fun statesAreCorrect() {
    val state =
      ComposeColorState(
        NoopComposeAnimationTracker,
        ComposeUnit.Color.create(Color.red),
        ComposeUnit.Color.create(Color.blue),
      )
    val (initial, target) = state.state.value
    assertEquals(listOf(1f, 0f, 0f, 1f), initial.components)
    assertEquals(listOf(0f, 0f, 1f, 1f), target.components)
  }

  @RunsInEdt
  @Test
  fun createCard() {
    val state =
      ComposeColorState(
        NoopComposeAnimationTracker,
        ComposeUnit.Color.create(Color.red),
        ComposeUnit.Color.create(Color.blue),
      )

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

    val toolbarComponents = card.findToolbar("AnimationCard").component.components
    assertEquals(5, toolbarComponents.size)
    // All components are visible
    toolbarComponents.forEach { assertBigger(minimumSize, it.size) }
    // Default hash.
    val hash = state.state.value.hashCode()
    // Swap state.
    ui.clickOn(toolbarComponents[1])
    // State hashCode has changed.
    assertNotEquals(hash, state.state.value.hashCode())
    // The states swapped.
    val (initial, target) = state.state.value
    assertEquals(listOf(0f, 0f, 1f, 1f), initial.components) // Blue
    assertEquals(listOf(1f, 0f, 0f, 1f), target.components) // Red
  }
}
