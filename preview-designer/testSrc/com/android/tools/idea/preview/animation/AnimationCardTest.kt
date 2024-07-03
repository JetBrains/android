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
package com.android.tools.idea.preview.animation

import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.NoopAnimationTracker
import com.android.tools.idea.preview.animation.TestUtils.createTestSlider
import com.android.tools.idea.preview.animation.TestUtils.findExpandButton
import com.android.tools.idea.preview.animation.TestUtils.findToolbar
import com.android.tools.idea.preview.animation.actions.FreezeAction
import com.android.tools.idea.testing.AndroidProjectRule
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class AnimationCardTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val minimumSize = Dimension(10, 10)

  @Test
  fun `create animation card`(): Unit = runBlocking {
    val frozenFlow = MutableStateFlow(SupportedAnimationManager.FrozenState(false))
    val card =
      AnimationCard(
          Mockito.mock(DesignSurface::class.java),
          "Title",
          listOf(FreezeAction(createTestSlider(), frozenFlow, NoopAnimationTracker)),
          NoopAnimationTracker,
        )
        .apply { setDuration(111) }
    card.setSize(300, 300)

    val ui =
      withContext(uiThread) {
        FakeUi(card).apply {
          updateToolbars()
          layout()
          layoutAndDispatchEvents()
        }
      }
    var expendedStateChanges = 0
    val job = launch { card.expanded.collect { expendedStateChanges++ } }
    var frozenStateChanges = 0
    val job2 = launch { frozenFlow.collect { frozenStateChanges++ } }

    // collector above will collect once even without any user action
    delayUntilCondition(200) { expendedStateChanges == 1 && frozenStateChanges == 1 }

    withContext(uiThread) {
      // Expand/collapse button.
      card.findExpandButton().also {
        // Button is here and visible.
        assertTrue(it.isVisible)
        TestUtils.assertBigger(Dimension(10, 10), it.size)
        ui.clickOn(it)
        ui.updateToolbars()
        ui.layoutAndDispatchEvents()
        // Expand/collapse button clicked
        delayUntilCondition(200) { expendedStateChanges == 2 }
        assertEquals(2, expendedStateChanges)
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
      run {
        var freezeButton = findFreezeButton(card)
        // Button is here and visible.
        assertTrue(freezeButton.isVisible)
        assertTrue(freezeButton.isEnabled)
        TestUtils.assertBigger(minimumSize, freezeButton.size)

        // Freeze and unfreeze
        ui.clickOn(freezeButton)
        ui.updateToolbars()
        // Freeze button clicked
        delayUntilCondition(200) { frozenStateChanges == 2 }
        assertEquals(2, frozenStateChanges)
        freezeButton = findFreezeButton(card)
        ui.clickOn(freezeButton)
        // Freeze button clicked
        delayUntilCondition(200) { frozenStateChanges == 3 }
        assertEquals(3, frozenStateChanges)
      }
      // Double click to open in new tab. Use label position just to make sure we are not clicking
      // on any button.
      val label = (card.components[0] as JComponent).components[1]
      var openInTabActions = 0
      card.addOpenInTabListener { openInTabActions++ }
      ui.mouse.doubleClick(label.x + 5, label.y + 5)
      assertEquals(1, openInTabActions)
      assertNotNull(ui)
      job.cancel()
      job2.cancel()
    }
  }

  private fun findFreezeButton(parent: Component): Component {
    return parent.findToolbar("AnimationCard").components[0]
  }
}
