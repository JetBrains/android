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
import com.android.tools.idea.preview.animation.InspectorLayout
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.animation.TestUtils
import com.android.tools.idea.preview.animation.TestUtils.scanForTooltips
import com.android.tools.idea.preview.animation.timeline.ComponentCurve
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComponentCurveTest {
  @get:Rule val edtRule = EdtRule()

  @RunsInEdt
  @Test
  fun `create component curve`() {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }

    val property =
      AnimatedProperty.Builder()
        .add(0, ComposeUnit.Color(0.1f, 0.1f, 0.1f, 0.1f))
        .add(50, ComposeUnit.Color(0.2f, 0.2f, 0.2f, 0.2f))
        .add(100, ComposeUnit.Color(0.3f, 0.3f, 0.3f, 0.3f))
        .build()!!

    var componentCurve =
      ComponentCurve.create(
        SupportedAnimationManager.FrozenState(false),
        property = property,
        componentId = 0,
        rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
        positionProxy = slider.sliderUI.positionProxy,
        colorIndex = 0,
      )

    slider.sliderUI.elements = listOf(componentCurve)
    val curveBaseLine = componentCurve.curveBaseY - 1 // Minus 1 so point is inside the curve.

    // No tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    assertEquals(0, slider.scanForTooltips().size)

    assertTrue(componentCurve.height > 0)
    // Point in the middle of curve baseline
    assertTrue(
      componentCurve.contains(slider.sliderUI.positionProxy.xPositionForValue(50), curveBaseLine)
    )
    // Point inside left diamond
    assertTrue(
      componentCurve.contains(slider.sliderUI.positionProxy.xPositionForValue(0) - 5, curveBaseLine)
    )
    // Point inside right diamond
    assertTrue(
      componentCurve.contains(
        slider.sliderUI.positionProxy.xPositionForValue(100) + 5,
        curveBaseLine,
      )
    )
    // Uncomment to preview ui.
    // ui.render() // Curve is from 0ms to 100ms
  }
}
