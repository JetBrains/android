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
import com.android.tools.idea.compose.preview.animation.AnimationCard
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.TestUtils
import com.android.tools.idea.compose.preview.animation.TestUtils.assertBigger
import com.android.tools.idea.compose.preview.animation.TestUtils.findToolbar
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import java.awt.Color
import java.awt.Dimension
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class ColorPickerStateTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val minimumSize = Dimension(10, 10)

  @Test
  fun statesAreCorrect() {
    val state =
      ColorPickerState({}) {}.apply {
        setStates(ComposeUnit.Color.create(Color.red), ComposeUnit.Color.create(Color.blue))
      }
    assertEquals(listOf(1f, 0f, 0f, 1f), state.getState(0))
    assertEquals(listOf(0f, 0f, 1f, 1f), state.getState(1))
  }

  @Test
  fun createCard() {
    var callbacks = 0
    val state =
      ColorPickerState({}) { callbacks++ }.apply {
        setStates(ComposeUnit.Color.create(Color.red), ComposeUnit.Color.create(Color.blue))
        callbackEnabled = true
      }
    val card =
      AnimationCard(
          TestUtils.testPreviewState(),
          Mockito.mock(DesignSurface::class.java),
          ElementState("Title"),
          state.extraActions
        ) {}
        .apply { size = Dimension(300, 300) }

    invokeAndWaitIfNeeded {
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
      val hash = state.stateHashCode()
      // Swap state.
      ui.clickOn(toolbarComponents[1])
      // State hashCode has changed.
      assertNotEquals(hash, state.stateHashCode())
      // The states swapped.
      assertEquals(1, callbacks)
      assertEquals(listOf(0f, 0f, 1f, 1f), state.getState(0)) // Blue
      assertEquals(listOf(1f, 0f, 0f, 1f), state.getState(1)) // Red
    }
  }
}
