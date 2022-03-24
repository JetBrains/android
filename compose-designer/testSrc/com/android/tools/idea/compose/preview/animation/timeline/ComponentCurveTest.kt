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
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentCurveTest {

  @Test
  fun `create component curve`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }

    val property = AnimatedProperty.Builder()
      .add(0, ComposeUnit.IntSize(0, 0))
      .add(50, ComposeUnit.IntSize(10, 10))
      .add(100, ComposeUnit.IntSize(20, 20))
      .build()!!
    val componentCurve = ComponentCurve.create(
      state = ElementState(),
      property = property, componentId = 0,
      rowMinY = InspectorLayout.timelineHeaderHeightScaled(),
      positionProxy = slider.sliderUI.positionProxy, colorIndex = 0)
    slider.sliderUI.elements.add(componentCurve)
    val curveBaseLine = componentCurve.curveBaseY - 1 // Minus 1 so point is inside the curve.

    assertTrue { componentCurve.height > 0 }
    // Point in the middle of curve baseline
    assertTrue { componentCurve.contains(slider.sliderUI.positionProxy.xPositionForValue(50), curveBaseLine) }
    // Point inside left diamond
    assertTrue { componentCurve.contains(slider.sliderUI.positionProxy.xPositionForValue(0) - 5, curveBaseLine) }
    // Point inside right diamond
    assertTrue { componentCurve.contains(slider.sliderUI.positionProxy.xPositionForValue(100) + 5, curveBaseLine) }
    // Uncomment to preview ui.
    //ui.render() // Curve is from 0ms to 100ms

    val shift50ms = slider.sliderUI.positionProxy.xPositionForValue(50) -
                    slider.sliderUI.positionProxy.xPositionForValue(0)

    componentCurve.move(shift50ms)
    // Point in the middle of curve baseline
    assertTrue { componentCurve.contains(shift50ms + slider.sliderUI.positionProxy.xPositionForValue(50), curveBaseLine) }
    // Point inside left diamond
    assertTrue { componentCurve.contains(shift50ms + slider.sliderUI.positionProxy.xPositionForValue(0) - 5, curveBaseLine) }
    // Point inside right diamond
    assertTrue { componentCurve.contains(shift50ms + slider.sliderUI.positionProxy.xPositionForValue(100) + 5, curveBaseLine) }
    assertEquals(50, componentCurve.state.valueOffset)
    // Uncomment to preview ui.
    //ui.render() // Curve is shifted to the right and starts in 50ms

    componentCurve.move(-2 * shift50ms)
    // Point in the middle of curve baseline
    assertTrue { componentCurve.contains(-shift50ms + slider.sliderUI.positionProxy.xPositionForValue(50), curveBaseLine) }
    // Point inside left diamond
    assertTrue { componentCurve.contains(-shift50ms + slider.sliderUI.positionProxy.xPositionForValue(0) - 5, curveBaseLine) }
    // Point inside right diamond
    assertTrue { componentCurve.contains(-shift50ms + slider.sliderUI.positionProxy.xPositionForValue(100) + 5, curveBaseLine) }
    assertEquals(-50, componentCurve.state.valueOffset)
    // Uncomment to preview ui.
    //ui.render() // Curve is shifted to the left and ends in 50ms
  }
}