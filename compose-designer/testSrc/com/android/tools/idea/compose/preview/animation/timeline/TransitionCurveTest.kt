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
package com.android.tools.idea.compose.preview.animation.timeline

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.preview.animation.AnimatedProperty
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.InspectorLayout
import com.android.tools.idea.compose.preview.animation.TestUtils
import com.android.tools.idea.compose.preview.animation.TestUtils.scanForTooltips
import com.android.tools.idea.compose.preview.animation.TooltipInfo
import com.android.tools.idea.compose.preview.animation.Transition
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import kotlin.test.assertEquals
import org.junit.Test

class TransitionCurveTest {

  private val property =
    AnimatedProperty.Builder()
      .add(0, ComposeUnit.IntSize(0, 0))
      .add(50, ComposeUnit.IntSize(10, 10))
      .add(100, ComposeUnit.IntSize(20, -20))
      .build()!!

  @Test
  fun `create transition curves`(): Unit {
    invokeAndWaitIfNeeded {
      val slider = TestUtils.createTestSlider()
      // Call layoutAndDispatchEvents() so positionProxy returns correct values
      val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }

      val transition = Transition(mutableMapOf(0 to property, 1 to property))
      val transitionCurveOne =
        TransitionCurve.create(
          state = ElementState(),
          transition = transition,
          rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
          positionProxy = slider.sliderUI.positionProxy
        )
      val transitionCurveTwo =
        TransitionCurve.create(
          state = ElementState(),
          transition = transition,
          rowMinY = InspectorLayout.timelineHeaderHeightScaled() + transitionCurveOne.height,
          positionProxy = slider.sliderUI.positionProxy
        )

      transitionCurveOne.timelineUnits =
        listOf(
          ComposeUnit.TimelineUnit("Property One", ComposeUnit.IntSize(0, 0)),
          ComposeUnit.TimelineUnit("Property Two", ComposeUnit.IntSize(5, 5))
        )

      slider.sliderUI.elements.add(transitionCurveOne)
      slider.sliderUI.elements.add(transitionCurveTwo)

      // Timeline has tooltips
      ui.render() // paint() method within render() should be called to update BoxedLabel positions.
      val tooltips = slider.scanForTooltips()
      val expected =
        setOf(
          TooltipInfo("Property One", "width ( 0 , _ )"),
          TooltipInfo("Property One", "height ( _ , 0 )"),
          TooltipInfo("Property Two", "width ( 5 , _ )"),
          TooltipInfo("Property Two", "height ( _ , 5 )")
        )
      assertEquals(expected, tooltips)

      // Uncomment to preview ui.
      // ui.render()
    }
  }

  @Test
  fun `create transition curves with null properties`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    val transition = Transition(mutableMapOf(0 to property, 1 to null, 2 to property, 3 to null))
    val transitionCurve =
      TransitionCurve.create(
        state = ElementState(),
        transition = transition,
        rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
        positionProxy = slider.sliderUI.positionProxy
      )
    slider.sliderUI.elements.add(transitionCurve)
    // No tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    assertEquals(0, slider.scanForTooltips().size)
    // Uncomment to preview ui.
    // ui.render()
  }

  @Test
  fun `create transition curve with null timeline units`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    val transition = Transition(mutableMapOf(0 to property, 1 to property, 2 to property))
    val transitionCurve =
      TransitionCurve.create(
        state = ElementState(),
        transition = transition,
        rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
        positionProxy = slider.sliderUI.positionProxy
      )
    transitionCurve.timelineUnits =
      listOf(null, null, ComposeUnit.TimelineUnit("Property", ComposeUnit.IntSize(5, 5)))
    slider.sliderUI.elements.add(transitionCurve)
    // Timeline has tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    val tooltips = slider.scanForTooltips()
    val expected =
      setOf(TooltipInfo("Property", "width ( 5 , _ )"), TooltipInfo("Property", "height ( _ , 5 )"))
    assertEquals(expected, tooltips)
    // Uncomment to preview ui.
    // ui.render()
  }

  @Test
  fun `create transition curve with less timeline units`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    val transition = Transition(mutableMapOf(0 to property, 1 to property))
    val transitionCurve =
      TransitionCurve.create(
        state = ElementState(),
        transition = transition,
        rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
        positionProxy = slider.sliderUI.positionProxy
      )
    transitionCurve.timelineUnits =
      listOf(ComposeUnit.TimelineUnit("Property Two", ComposeUnit.IntSize(5, 5)))
    slider.sliderUI.elements.add(transitionCurve)
    // Timeline has tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    val tooltips = slider.scanForTooltips()
    val expected =
      setOf(
        TooltipInfo("Property Two", "width ( 5 , _ )"),
        TooltipInfo("Property Two", "height ( _ , 5 )")
      )
    assertEquals(expected, tooltips)
    // Uncomment to preview ui.
    // ui.render()
  }

  @Test
  fun `create transition curve with more timeline units`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    val transition = Transition(mutableMapOf(0 to property, 1 to property))
    val transitionCurve =
      TransitionCurve.create(
        state = ElementState(),
        transition = transition,
        rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
        positionProxy = slider.sliderUI.positionProxy
      )
    transitionCurve.timelineUnits =
      listOf(
        ComposeUnit.TimelineUnit("Property One", ComposeUnit.IntSize(0, 0)),
        ComposeUnit.TimelineUnit("Property Two", ComposeUnit.IntSize(0, 0)),
        ComposeUnit.TimelineUnit("Property Three", ComposeUnit.IntSize(5, 5))
      )
    slider.sliderUI.elements.add(transitionCurve)
    slider.sliderUI.elements.add(transitionCurve)
    // Timeline has tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    val tooltips = slider.scanForTooltips()
    val expected =
      setOf(
        TooltipInfo("Property One", "width ( 0 , _ )"),
        TooltipInfo("Property One", "height ( _ , 0 )"),
        TooltipInfo("Property Two", "width ( 0 , _ )"),
        TooltipInfo("Property Two", "height ( _ , 0 )")
      )
    assertEquals(expected, tooltips)
    // Uncomment to preview ui.
    // ui.render()
  }
}
