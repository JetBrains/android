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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnimationCardTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val minimumSize = Dimension(10, 10)

  @Test
  fun `create animation card`() {
    val card = AnimationCard(Mockito.mock(DesignSurface::class.java), ElementState("Title")) { }.apply { setDuration(111) }
    card.setSize(300, 300)

    invokeAndWaitIfNeeded {
      val ui = FakeUi(card)
      ui.updateToolbars()
      ui.layout()
      ui.layoutAndDispatchEvents()
      // Expand/collapse button.
      (card.components[0] as Container).components[0].also {
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

      // Lock button.
      (card.components[1] as JComponent).components[0].also {
        // Button is here and visible.
        assertTrue(it.isVisible)
        TestUtils.assertBigger(minimumSize, it.size)
        // After clicking button callback is called.
        var lockCalls = 0
        card.state.addLockedListener { lockCalls++ }
        ui.clickOn(it)
        ui.updateToolbars()
        assertEquals(1, lockCalls)
        card.state.locked = false
        ui.layout()
        ui.updateToolbars()
        assertEquals(2, lockCalls)
      }
      // Double click to open in new tab. Use label position just to make sure we are not clicking on any button.
      val label = (card.components[0] as JComponent).components[1]
      var openInTabActions = 0
      card.addOpenInTabListener { openInTabActions++ }
      ui.mouse.doubleClick(label.x + 5, label.y + 5)
      assertEquals(1, openInTabActions)
      assertNotNull(ui)
    }
  }
}