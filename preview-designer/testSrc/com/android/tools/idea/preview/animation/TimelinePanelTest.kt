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
package com.android.tools.idea.preview.animation

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.animation.TestUtils.scanForTooltips
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ApplicationManager
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimelinePanelTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `default labels and tick spacing`() =
    runBlocking(uiThread) {
      val slider = TestUtils.createTestSlider().apply { maximum = 10000 }
      val ui = FakeUi(slider.parent)
      // Tick spacing with default max value and width 300.
      assertEquals(5000, slider.majorTickSpacing)
      assertEquals(listOf("0", "5000", "10000").sorted(), slider.getLabels())
    }

  @Test
  fun `label and tick distance should change after size has changed`() =
    runBlocking(uiThread) {
      val slider = TestUtils.createTestSlider().apply { maximum = 10000 }
      val ui = FakeUi(slider.parent)
      // Tick spacing with default 10_000 as maximum value and width 600
      slider.parent.setSize(600, 500)
      ui.layoutAndDispatchEvents()
      assertEquals(2000, slider.majorTickSpacing)
      assertEquals(
        listOf("0", "2000", "4000", "6000", "8000", "10000").sorted(),
        slider.getLabels(),
      )
      // Tick spacing with default 10_000 as maximum value and width 1000
      slider.parent.setSize(1000, 500)
      ui.layoutAndDispatchEvents()
      assertEquals(1000, slider.majorTickSpacing)
      assertEquals(
        listOf("0", "1000", "2000", "3000", "4000", "5000", "6000", "7000", "8000", "9000", "10000")
          .sorted(),
        slider.getLabels(),
      )

      // Tick spacing with default 10_000 as maximum value and width 150
      slider.parent.setSize(150, 500)
      ui.layoutAndDispatchEvents()
      assertEquals(10000, slider.majorTickSpacing)
      assertEquals(listOf("0", "10000").sorted(), slider.getLabels())
    }

  @Test
  fun `label and tick distance should change after maximum has changed`() =
    runBlocking(uiThread) {
      val slider = TestUtils.createTestSlider()
      val ui = FakeUi(slider.parent)
      // Tick spacing with 300 as maximum value and width 300
      slider.maximum = 1500
      assertEquals(600, slider.majorTickSpacing)
      assertEquals(listOf("0", "600", "1200").sorted(), slider.getLabels())
      // Tick spacing with 15 as maximum value and width 300
      slider.maximum = 15
      assertEquals(5, slider.majorTickSpacing)
      assertEquals(listOf("0", "5", "10", "15").sorted(), slider.getLabels())
      // Tick spacing with 300 as maximum value and width 300
      slider.maximum = 5
      assertEquals(4, slider.majorTickSpacing)
      assertEquals(listOf("0", "4").sorted(), slider.getLabels())
    }

  @Test
  fun `tooltips in timeline are available`() {
    ApplicationManager.getApplication().invokeAndWait {
      val slider = TestUtils.createTestSlider()
      // Call layoutAndDispatchEvents() so positionProxy returns correct values
      val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
      slider.sliderUI.apply {
        elements =
          listOf(TestUtils.TestTimelineElement(50, 50), TestUtils.TestTimelineElement(50, 100))
      }
      assertEquals(
        setOf(TooltipInfo("50", "50"), TooltipInfo("50", "100")),
        slider.scanForTooltips(),
      )
      assertNotNull(slider.tooltip)
      // Hover first element
      ui.mouse.moveTo(51, 51)
      assertEquals(TooltipInfo("50", "50"), slider.tooltip.tooltipInfo)
      assertTrue(slider.tooltip.isVisible)
      // Move to the empty space.
      ui.mouse.moveTo(51, 70)
      assertNull(slider.tooltip.tooltipInfo)
      assertFalse(slider.tooltip.isVisible)
      // Hover second element
      ui.mouse.moveTo(51, 101)
      assertEquals(TooltipInfo("50", "100"), slider.tooltip.tooltipInfo)
      assertTrue(slider.tooltip.isVisible)
      // Move to the empty space.
      ui.mouse.moveTo(51, 70)
      assertNull(slider.tooltip.tooltipInfo)
      assertFalse(slider.tooltip.isVisible)
    }
  }

  @Test
  fun `ui with frozen elements`(): Unit =
    runBlocking(uiThread) {
      val slider = TestUtils.createTestSlider().apply { value = 1000 }
      (slider.ui as TimelineSliderUI).apply {
        elements =
          listOf(
            TestUtils.TestTimelineElement(
              50,
              50,
              frozenState = SupportedAnimationManager.FrozenState(true, 0),
            ),
            TestUtils.TestTimelineElement(50, 150),
            TestUtils.TestTimelineElement(
              50,
              250,
              frozenState = SupportedAnimationManager.FrozenState(true, 0),
            ),
            TestUtils.TestTimelineElement(50, 350),
          )
      }
      val ui = FakeUi(slider.parent)
      // Uncomment to preview ui.
      // ui.render()
      assertNotNull(ui)
    }

  @Test
  fun `ui with all unfrozen elements`(): Unit =
    runBlocking(uiThread) {
      val slider = TestUtils.createTestSlider().apply { value = 1000 }
      (slider.ui as TimelineSliderUI).apply {
        elements =
          listOf(
            TestUtils.TestTimelineElement(50, 50),
            TestUtils.TestTimelineElement(50, 150),
            TestUtils.TestTimelineElement(50, 250),
            TestUtils.TestTimelineElement(50, 350),
          )
      }
      val ui = FakeUi(slider.parent)
      // Uncomment to preview ui.
      // ui.render()
      assertNotNull(ui)
    }

  @Test
  fun `ui with one unfrozen element`(): Unit =
    runBlocking(uiThread) {
      val slider = TestUtils.createTestSlider().apply { value = 1000 }
      (slider.ui as TimelineSliderUI).apply {
        elements = listOf(TestUtils.TestTimelineElement(50, 50))
      }
      val ui = FakeUi(slider.parent)
      // Uncomment to preview ui.
      // ui.render()
      assertNotNull(ui)
    }

  @Test
  fun `ui with one frozen element`(): Unit =
    runBlocking(uiThread) {
      val slider = TestUtils.createTestSlider().apply { value = 1000 }
      (slider.ui as TimelineSliderUI).apply {
        elements =
          listOf(
            TestUtils.TestTimelineElement(
              50,
              50,
              frozenState = SupportedAnimationManager.FrozenState(true, 0),
            )
          )
      }
      val ui = FakeUi(slider.parent)
      // Uncomment to preview ui.
      // ui.render()
      assertNotNull(ui)
    }

  private fun JSlider.getLabels() =
    this.labelTable.elements().asSequence().map { (it as JLabel).text }.toList().sorted()
}
