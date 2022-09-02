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
import com.android.tools.idea.compose.preview.animation.TestUtils.scanForTooltips
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.compose.preview.animation.timeline.ParentTimelineElement
import com.android.tools.idea.compose.preview.animation.timeline.TimelineElementStatus
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TimelinePanelTest(private val enableCoordinationDrag: Boolean) {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "Coordination drag is enabled: {0}")
    fun enableCoordinationDrag() = listOf(true, false)
  }

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_ANIMATION_PREVIEW_COORDINATION_DRAG.override(enableCoordinationDrag)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_ANIMATION_PREVIEW_COORDINATION_DRAG.clearOverride()
  }

  @Test
  fun `default labels and tick spacing`() = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider().apply { maximum = 10000 }
    val ui = FakeUi(slider.parent)
    // Tick spacing with default max value and width 300.
    assertEquals(5000, slider.majorTickSpacing)
    assertEquals(listOf("0", "5000", "10000").sorted(), slider.getLabels())
  }

  @Test
  fun `label and tick distance should change after size has changed`() = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider().apply { maximum = 10000 }
    val ui = FakeUi(slider.parent)
    // Tick spacing with default 10_000 as maximum value and width 600
    slider.parent.setSize(600, 500)
    ui.layoutAndDispatchEvents()
    assertEquals(2000, slider.majorTickSpacing)
    assertEquals(listOf("0", "2000", "4000", "6000", "8000", "10000").sorted(), slider.getLabels())
    // Tick spacing with default 10_000 as maximum value and width 1000
    slider.parent.setSize(1000, 500)
    ui.layoutAndDispatchEvents()
    assertEquals(1000, slider.majorTickSpacing)
    assertEquals(
      listOf("0", "1000", "2000", "3000", "4000", "5000", "6000", "7000", "8000", "9000", "10000")
        .sorted(),
      slider.getLabels()
    )

    // Tick spacing with default 10_000 as maximum value and width 150
    slider.parent.setSize(150, 500)
    ui.layoutAndDispatchEvents()
    assertEquals(10000, slider.majorTickSpacing)
    assertEquals(listOf("0", "10000").sorted(), slider.getLabels())
  }

  @Test
  fun `label and tick distance should change after maximum has changed`() = invokeAndWaitIfNeeded {
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
  fun `hovering elements`() = invokeAndWaitIfNeeded {
    // Only if coordination drag is enabled.
    if (!enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val slider = TestUtils.createTestSlider()
    slider.sliderUI.apply {
      elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy))
      elements.add(TestUtils.TestTimelineElement(50, 150, positionProxy))
    }

    val ui = FakeUi(slider.parent)
    // Nothing is selected yet.
    assertNull(slider.sliderUI.activeElement)
    // Hover the first element.
    ui.mouse.moveTo(55, 55)
    assertEquals(slider.sliderUI.elements[0], slider.sliderUI.activeElement)
    assertEquals(TimelineElementStatus.Hovered, slider.sliderUI.activeElement?.status)
    // Hover the second element, first element became inactive.
    ui.mouse.moveTo(55, 155)
    assertEquals(slider.sliderUI.elements[1], slider.sliderUI.activeElement)
    assertEquals(TimelineElementStatus.Hovered, slider.sliderUI.activeElement?.status)
    assertEquals(TimelineElementStatus.Inactive, slider.sliderUI.elements[0].status)
    // Hover the timeline, second element become inactive.
    ui.mouse.moveTo(10, 10)
    assertNull(slider.sliderUI.activeElement)
    assertEquals(TimelineElementStatus.Inactive, slider.sliderUI.elements[0].status)
    assertEquals(TimelineElementStatus.Inactive, slider.sliderUI.elements[1].status)
  }

  @Test
  fun `hovering elements is not enabled`() = invokeAndWaitIfNeeded {
    // Only if coordination drag is disabled.
    if (enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val slider = TestUtils.createTestSlider()
    slider.sliderUI.apply {
      elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy))
      elements.add(TestUtils.TestTimelineElement(50, 150, positionProxy))
    }
    val ui = FakeUi(slider.parent)
    // Hover the first element.
    ui.mouse.moveTo(55, 55)
    assertNull(slider.sliderUI.activeElement)
    // Hover the second element.
    ui.mouse.moveTo(55, 155)
    assertNull(slider.sliderUI.activeElement)
  }

  @Test
  fun `dragging timeline`() = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider()
    slider.sliderUI.apply { elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy)) }
    val ui = FakeUi(slider.parent)
    // Nothing is selected.
    assertNull(slider.sliderUI.activeElement)
    assertEquals(0, slider.sliderUI.elements.first().offsetPx)
    // Drag timeline over the first element, but don't stop on element itself.
    ui.mouse.drag(45, 45, 100, 100)
    // Element hasn't moved.
    assertEquals(0, slider.sliderUI.elements.first().offsetPx)
    assertEquals(TimelineElementStatus.Inactive, slider.sliderUI.elements.first().status)
  }

  @Test
  fun `pressing element`() = invokeAndWaitIfNeeded {
    // Only if coordination drag is enabled.
    if (!enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val slider = TestUtils.createTestSlider()
    slider.sliderUI.apply {
      elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy))
      elements.add(TestUtils.TestTimelineElement(50, 100, positionProxy))
    }
    val ui = FakeUi(slider.parent)
    // Nothing is selected.
    assertNull(slider.sliderUI.activeElement)
    // Press mouse over element
    ui.mouse.moveTo(55, 55)
    ui.mouse.press(55, 55)
    assertEquals(slider.sliderUI.elements.first(), slider.sliderUI.activeElement)
    assertEquals(TimelineElementStatus.Dragged, slider.sliderUI.activeElement?.status)
    // Release element.
    ui.mouse.release()
    assertEquals(TimelineElementStatus.Hovered, slider.sliderUI.activeElement?.status)
  }

  @Test
  fun `dragging element`() = invokeAndWaitIfNeeded {
    // Only if coordination drag is enabled.
    if (!enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val slider = TestUtils.createTestSlider()
    slider.sliderUI.apply {
      elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy))
      elements.add(TestUtils.TestTimelineElement(50, 100, positionProxy))
    }
    val ui = FakeUi(slider.parent)
    // Nothing is selected.
    assertNull(slider.sliderUI.activeElement)
    // Add callback listeners
    var firstElementMoved = false
    var secondElementMoved = false
    var endOfDragCallback = 0
    slider.sliderUI.elements[0].state.addValueOffsetListener { firstElementMoved = true }
    slider.sliderUI.elements[1].state.addValueOffsetListener { secondElementMoved = true }
    slider.dragEndListeners.add { endOfDragCallback++ }
    // Drag element
    ui.mouse.moveTo(55, 55)
    ui.mouse.drag(55, 55, 20, 120)
    // First element has moved, second element stays in place.
    assertEquals(20, slider.sliderUI.elements[0].offsetPx)
    assertEquals(0, slider.sliderUI.elements[1].offsetPx)
    assertTrue(firstElementMoved)
    assertFalse(secondElementMoved)
    assertEquals(1, endOfDragCallback)
    // Drag element back
    ui.mouse.moveTo(75, 55)
    ui.mouse.drag(75, 55, -20, 120)
    assertEquals(2, endOfDragCallback)
    // First element is back to its place.
    assertEquals(0, slider.sliderUI.elements[0].offsetPx)
  }

  @Test
  fun `dragging element is not enabled`() = invokeAndWaitIfNeeded {
    // Only if coordination drag is disabled.
    if (enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val slider = TestUtils.createTestSlider()
    slider.sliderUI.apply {
      elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy))
      elements.add(TestUtils.TestTimelineElement(50, 100, positionProxy))
    }
    val ui = FakeUi(slider.parent)
    // Nothing is selected.
    assertNull(slider.sliderUI.activeElement)
    // Add callback listeners
    var firstElementMoved = false
    var secondElementMoved = false
    var endOfDragCallback = 0
    slider.sliderUI.elements[0].state.addValueOffsetListener { firstElementMoved = true }
    slider.sliderUI.elements[1].state.addValueOffsetListener { secondElementMoved = true }
    slider.dragEndListeners.add { endOfDragCallback++ }
    // Drag element
    ui.mouse.moveTo(55, 55)
    ui.mouse.drag(55, 55, 20, 120)
    // Nothing has moved
    assertEquals(0, slider.sliderUI.elements[0].offsetPx)
    assertEquals(0, slider.sliderUI.elements[1].offsetPx)
    assertFalse(firstElementMoved)
    assertFalse(secondElementMoved)
    assertEquals(0, endOfDragCallback)
  }

  @Test
  fun `hovering group of elements`() = invokeAndWaitIfNeeded {
    // Only if coordination drag is enabled.
    if (!enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val slider = TestUtils.createTestSlider()
    val ui = FakeUi(slider.parent)
    val child1 = TestUtils.TestTimelineElement(50, 50, slider.sliderUI.positionProxy)
    val child2 = TestUtils.TestTimelineElement(100, 100, slider.sliderUI.positionProxy)
    val parent =
      ParentTimelineElement(ElementState(), listOf(child1, child2), slider.sliderUI.positionProxy)
    slider.sliderUI.elements.add(parent)
    // Nothing is selected.
    assertNull(slider.sliderUI.activeElement)
    // Hover the first child - parent and both child are hovered.
    ui.mouse.moveTo(55, 55)
    assertEquals(parent, slider.sliderUI.activeElement)
    assertEquals(TimelineElementStatus.Hovered, parent.status)
    assertEquals(TimelineElementStatus.Hovered, child1.status)
    assertEquals(TimelineElementStatus.Hovered, child2.status)
    // Hover the second child - parent and both child are hovered.
    ui.mouse.moveTo(105, 105)
    assertEquals(parent, slider.sliderUI.activeElement)
    assertEquals(TimelineElementStatus.Hovered, parent.status)
    assertEquals(TimelineElementStatus.Hovered, child1.status)
    assertEquals(TimelineElementStatus.Hovered, child2.status)
    // Hover the first child - parent and both child are inactive.
    ui.mouse.moveTo(10, 10)
    assertNull(slider.sliderUI.activeElement)
    assertEquals(TimelineElementStatus.Inactive, parent.status)
    assertEquals(TimelineElementStatus.Inactive, child1.status)
    assertEquals(TimelineElementStatus.Inactive, child2.status)
  }

  @Test
  fun `dragging element out of slider to the left`(): Unit = invokeAndWaitIfNeeded {
    // Only if coordination drag is enabled.
    if (!enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val slider = TestUtils.createTestSlider()
    slider.sliderUI.apply { elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy)) }
    val ui = FakeUi(slider.parent)
    // Drag element
    ui.mouse.moveTo(149, 55)
    ui.mouse.drag(149, 55, -149, 0)
    // Uncomment to preview ui.
    // ui.render()
    // First element can't move outside of the slider.
    assertEquals(-138, slider.sliderUI.elements[0].offsetPx)
    assertTrue { slider.sliderUI.elements[0].state.valueOffset < 0 }
  }

  @Test
  fun `dragging element out of slider to the right`(): Unit = invokeAndWaitIfNeeded {
    // Only if coordination drag is enabled.
    if (!enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val slider = TestUtils.createTestSlider()
    slider.sliderUI.apply { elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy)) }
    val ui = FakeUi(slider.parent)
    // Drag element
    ui.mouse.moveTo(51, 55)
    ui.mouse.drag(51, 55, 248, 0)
    // Uncomment to preview ui.
    // ui.render()
    // First element can't move outside of the slider.
    assertEquals(237, slider.sliderUI.elements[0].offsetPx)
    assertTrue { slider.sliderUI.elements[0].state.valueOffset > 0 }
  }

  @Test
  fun `tooltips in timeline are available`() {
    invokeAndWaitIfNeeded {
      val slider = TestUtils.createTestSlider()
      // Call layoutAndDispatchEvents() so positionProxy returns correct values
      val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
      slider.sliderUI.apply {
        elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy))
        elements.add(TestUtils.TestTimelineElement(50, 100, positionProxy))
      }
      assertEquals(
        setOf(TooltipInfo("50", "50"), TooltipInfo("50", "100")),
        slider.scanForTooltips()
      )
      assertNotNull(slider.tooltip)
      // Hover first element
      ui.mouse.moveTo(51, 51)
      assertEquals(TooltipInfo("50", "50"), slider.tooltip?.tooltipInfo)
      assertTrue(slider.tooltip!!.isVisible)
      // Move to the empty space.
      ui.mouse.moveTo(51, 70)
      assertNull(slider.tooltip?.tooltipInfo)
      assertFalse(slider.tooltip!!.isVisible)
      // Hover second element
      ui.mouse.moveTo(51, 101)
      assertEquals(TooltipInfo("50", "100"), slider.tooltip?.tooltipInfo)
      assertTrue(slider.tooltip!!.isVisible)
      // Move to the empty space.
      ui.mouse.moveTo(51, 70)
      assertNull(slider.tooltip?.tooltipInfo)
      assertFalse(slider.tooltip!!.isVisible)
    }
  }

  @Test
  fun `ui with frozen elements`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider().apply { value = 1000 }
    (slider.ui as TimelineSliderUI).apply {
      elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy).apply { frozen = true })
      elements.add(TestUtils.TestTimelineElement(50, 150, positionProxy).apply { frozen = false })
      elements.add(TestUtils.TestTimelineElement(50, 250, positionProxy).apply { frozen = true })
      elements.add(TestUtils.TestTimelineElement(50, 350, positionProxy).apply { frozen = false })
    }
    val ui = FakeUi(slider.parent)
    // Uncomment to preview ui.
    // ui.render()
    assertNotNull(ui)
  }

  @Test
  fun `ui with all unfrozen elements`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider().apply { value = 1000 }
    (slider.ui as TimelineSliderUI).apply {
      elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy))
      elements.add(TestUtils.TestTimelineElement(50, 150, positionProxy))
      elements.add(TestUtils.TestTimelineElement(50, 250, positionProxy))
      elements.add(TestUtils.TestTimelineElement(50, 350, positionProxy))
    }
    val ui = FakeUi(slider.parent)
    // Uncomment to preview ui.
    // ui.render()
    assertNotNull(ui)
  }

  @Test
  fun `ui with one unfrozen element`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider().apply { value = 1000 }
    (slider.ui as TimelineSliderUI).apply {
      elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy))
    }
    val ui = FakeUi(slider.parent)
    // Uncomment to preview ui.
    // ui.render()
    assertNotNull(ui)
  }

  @Test
  fun `ui with one frozen element`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider().apply { value = 1000 }
    (slider.ui as TimelineSliderUI).apply {
      elements.add(TestUtils.TestTimelineElement(50, 50, positionProxy).apply { frozen = true })
    }
    val ui = FakeUi(slider.parent)
    // Uncomment to preview ui.
    // ui.render()
    assertNotNull(ui)
  }

  private fun JSlider.getLabels() =
    this.labelTable.elements().asSequence().map { (it as JLabel).text }.toList().sorted()
}
