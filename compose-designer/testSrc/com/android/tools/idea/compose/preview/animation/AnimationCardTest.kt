/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.animation.TestUtils.findExpandButton
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import java.awt.Component
import java.awt.Dimension
import java.util.stream.Collectors
import javax.swing.JComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class AnimationCardTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val minimumSize = Dimension(10, 10)

  @Test
  fun `create animation card`() {
    val card =
      AnimationCard(
          TestUtils.testPreviewState(),
          Mockito.mock(DesignSurface::class.java),
          ElementState("Title")
        ) {}
        .apply { setDuration(111) }
    card.setSize(300, 300)

    invokeAndWaitIfNeeded {
      val ui = FakeUi(card)
      ui.updateToolbars()
      ui.layout()
      ui.layoutAndDispatchEvents()
      // Expand/collapse button.
      card.findExpandButton().also {
        // Button is here and visible.
        assertTrue(it.isVisible)
        TestUtils.assertBigger(Dimension(10, 10), it.size)
        // After clicking button callback is called.
        var expandCalls = 0
        card.state.addExpandedListener { expandCalls++ }
        ui.clickOn(it)
        ui.updateToolbars()
        assertEquals(1, expandCalls)
      }
      // Transition name label.
      (card.components[0] as JComponent).components[1].also {
        assertTrue(it.isVisible)
        TestUtils.assertBigger(minimumSize, it.size)
      }
      // Transition duration label.
      (card.components[0] as JComponent).components[2].also {
        assertTrue(it.isVisible)
        TestUtils.assertBigger(minimumSize, it.size)
      }

      // Freeze button.
      findFreezeButton(card).also {
        // Button is here and visible.
        assertTrue(it.isVisible)
        assertTrue { it.isEnabled }
        TestUtils.assertBigger(minimumSize, it.size)
        // After clicking button callback is called.
        var freezeCalls = 0
        card.state.addFreezeListener { freezeCalls++ }
        ui.clickOn(it)
        ui.updateToolbars()
        assertEquals(1, freezeCalls)
        card.state.frozen = false
        ui.layout()
        ui.updateToolbars()
        assertEquals(2, freezeCalls)
        // Freeze and unfreeze
        ui.clickOn(it)
        ui.clickOn(it)
        assertEquals(4, freezeCalls)
      }
      // Double click to open in new tab. Use label position just to make sure we are not clicking
      // on any button.
      val label = (card.components[0] as JComponent).components[1]
      var openInTabActions = 0
      card.addOpenInTabListener { openInTabActions++ }
      ui.mouse.doubleClick(label.x + 5, label.y + 5)
      assertEquals(1, openInTabActions)
      assertNotNull(ui)
    }
  }

  @Test
  fun `create animation card if coordination is not available`(): Unit = invokeAndWaitIfNeeded {
    val card =
      AnimationCard(
          TestUtils.testPreviewState(false),
          Mockito.mock(DesignSurface::class.java),
          ElementState("Title")
        ) {}
        .apply {
          setDuration(111)
          setSize(300, 300)
        }
    val ui =
      FakeUi(card).apply {
        updateToolbars()
        layout()
      }

    // Lock button is not available.
    findFreezeButton(card).also {
      // Button is here and visible.
      assertTrue(it.isVisible)
      assertFalse { it.isEnabled }
      TestUtils.assertBigger(minimumSize, it.size)
    }
    // Uncomment to preview ui.
    // ui.render()
  }

  private fun findFreezeButton(parent: Component): Component {
    val frozeToolbar =
      TreeWalker(parent)
        .descendantStream()
        .filter { it is ActionToolbarImpl }
        .collect(Collectors.toList())
        .map { it as ActionToolbarImpl }
        .firstOrNull { it.place == "FreezeAnimationCard" }
    return (frozeToolbar as JComponent).components[0]
  }
}
