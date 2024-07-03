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
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.preview.animation.AnimatedProperty
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.preview.animation.InspectorLayout
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.animation.TestUtils
import com.android.tools.idea.preview.animation.TestUtils.scanForTooltips
import com.android.tools.idea.preview.animation.TooltipInfo
import com.android.tools.idea.preview.animation.Transition
import com.android.tools.idea.preview.animation.timeline.TransitionCurve
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import junit.framework.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransitionCurveTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val property =
    AnimatedProperty.Builder()
      .add(0, ComposeUnit.IntSize(0, 0))
      .add(50, ComposeUnit.IntSize(10, 10))
      .add(100, ComposeUnit.IntSize(20, -20))
      .build()!!

  @get:Rule val edtRule = EdtRule()

  @RunsInEdt
  @Test
  fun `create transition curves`() {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }

    val transition = Transition(mutableMapOf(0 to property, 1 to property))
    val transitionCurveOne =
      TransitionCurve.create(
          0,
          SupportedAnimationManager.FrozenState(false),
          transition = transition,
          rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
          positionProxy = slider.sliderUI.positionProxy,
        )
        .apply { Disposer.register(projectRule.testRootDisposable, this) }
    val transitionCurveTwo =
      TransitionCurve.create(
          0,
          SupportedAnimationManager.FrozenState(false),
          transition = transition,
          rowMinY = InspectorLayout.timelineHeaderHeightScaled() + transitionCurveOne.height,
          positionProxy = slider.sliderUI.positionProxy,
        )
        .apply { Disposer.register(projectRule.testRootDisposable, this) }

    transitionCurveOne.timelineUnits =
      listOf(
        AnimationUnit.TimelineUnit("Property One", ComposeUnit.IntSize(0, 0)),
        AnimationUnit.TimelineUnit("Property Two", ComposeUnit.IntSize(5, 5)),
      )

    slider.sliderUI.elements = listOf(transitionCurveOne, transitionCurveTwo)

    // Timeline has tooltips
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    val tooltips = slider.scanForTooltips()
    val expected =
      setOf(
        TooltipInfo("Property One", "width ( 0 , _ )"),
        TooltipInfo("Property One", "height ( _ , 0 )"),
        TooltipInfo("Property Two", "width ( 5 , _ )"),
        TooltipInfo("Property Two", "height ( _ , 5 )"),
      )
    assertEquals(expected, tooltips)

    // Uncomment to preview ui.
    // ui.render()
  }

  @RunsInEdt
  @Test
  fun `create transition curves with null properties`() {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    val transition = Transition(mutableMapOf(0 to property, 1 to null, 2 to property, 3 to null))
    val transitionCurve =
      TransitionCurve.create(
          0,
          SupportedAnimationManager.FrozenState(false),
          transition = transition,
          rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
          positionProxy = slider.sliderUI.positionProxy,
        )
        .apply { Disposer.register(projectRule.testRootDisposable, this) }
    slider.sliderUI.elements = listOf(transitionCurve)
    // No tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    assertEquals(0, slider.scanForTooltips().size)
    // Uncomment to preview ui.
    // ui.render()
  }

  @RunsInEdt
  @Test
  fun `create transition curve with null timeline units`() {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    val transition = Transition(mutableMapOf(0 to property, 1 to property, 2 to property))
    val transitionCurve =
      TransitionCurve.create(
          0,
          SupportedAnimationManager.FrozenState(false),
          transition = transition,
          rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
          positionProxy = slider.sliderUI.positionProxy,
        )
        .apply { Disposer.register(projectRule.testRootDisposable, this) }
    transitionCurve.timelineUnits =
      listOf(null, null, AnimationUnit.TimelineUnit("Property", ComposeUnit.IntSize(5, 5)))
    slider.sliderUI.elements = listOf(transitionCurve)
    // Timeline has tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    val tooltips = slider.scanForTooltips()
    val expected =
      setOf(TooltipInfo("Property", "width ( 5 , _ )"), TooltipInfo("Property", "height ( _ , 5 )"))
    assertEquals(expected, tooltips)
    // Uncomment to preview ui.
    // ui.render()
  }

  @RunsInEdt
  @Test
  fun `create transition curve with less timeline units`() {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    val transition = Transition(mutableMapOf(0 to property, 1 to property))
    val transitionCurve =
      TransitionCurve.create(
          0,
          SupportedAnimationManager.FrozenState(false),
          transition = transition,
          rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
          positionProxy = slider.sliderUI.positionProxy,
        )
        .apply { Disposer.register(projectRule.testRootDisposable, this) }
    transitionCurve.timelineUnits =
      listOf(AnimationUnit.TimelineUnit("Property Two", ComposeUnit.IntSize(5, 5)))
    slider.sliderUI.elements = listOf(transitionCurve)
    // Timeline has tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    val tooltips = slider.scanForTooltips()
    val expected =
      setOf(
        TooltipInfo("Property Two", "width ( 5 , _ )"),
        TooltipInfo("Property Two", "height ( _ , 5 )"),
      )
    assertEquals(expected, tooltips)
    // Uncomment to preview ui.
    // ui.render()
  }

  @RunsInEdt
  @Test
  fun `create transition curve with more timeline units`() {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    val transition = Transition(mutableMapOf(0 to property, 1 to property))
    val transitionCurve =
      TransitionCurve.create(
          0,
          SupportedAnimationManager.FrozenState(false),
          transition = transition,
          rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
          positionProxy = slider.sliderUI.positionProxy,
        )
        .apply { Disposer.register(projectRule.testRootDisposable, this) }
    transitionCurve.timelineUnits =
      listOf(
        AnimationUnit.TimelineUnit("Property One", ComposeUnit.IntSize(0, 0)),
        AnimationUnit.TimelineUnit("Property Two", ComposeUnit.IntSize(0, 0)),
        AnimationUnit.TimelineUnit("Property Three", ComposeUnit.IntSize(5, 5)),
      )
    slider.sliderUI.elements = listOf(transitionCurve, transitionCurve)
    // Timeline has tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    val tooltips = slider.scanForTooltips()
    val expected =
      setOf(
        TooltipInfo("Property One", "width ( 0 , _ )"),
        TooltipInfo("Property One", "height ( _ , 0 )"),
        TooltipInfo("Property Two", "width ( 0 , _ )"),
        TooltipInfo("Property Two", "height ( _ , 0 )"),
      )
    assertEquals(expected, tooltips)
    // Uncomment to preview ui.
    // ui.render()
  }

  @RunsInEdt
  @Test
  fun `create empty transition curves`() {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }

    val transitionCurve =
      TransitionCurve.create(
          0,
          SupportedAnimationManager.FrozenState(false),
          transition = Transition(emptyMap()),
          rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
          positionProxy = slider.sliderUI.positionProxy,
        )
        .apply { Disposer.register(projectRule.testRootDisposable, this) }

    slider.sliderUI.elements = listOf(transitionCurve)

    // There are no tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    val tooltips = slider.scanForTooltips()
    assertEquals(emptySet<TooltipInfo>(), tooltips)

    // Even though transition is empty, it should have one element.
    assertTrue(transitionCurve.height > 10)

    // Uncomment to preview ui.
    // ui.render()
  }
}
