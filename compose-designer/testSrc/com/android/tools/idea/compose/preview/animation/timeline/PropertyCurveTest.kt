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
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

class PropertyCurveTest {

  @Test
  fun `create property curves`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider()
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }

    val property =
      AnimatedProperty.Builder()
        .add(0, ComposeUnit.IntSize(0, 0))
        .add(50, ComposeUnit.IntSize(10, 10))
        .add(100, ComposeUnit.IntSize(20, -20))
        .build()!!

    val propertyCurveOne =
      PropertyCurve.create(
          ElementState(),
          property,
          InspectorLayout.timelineHeaderHeightScaled(),
          0,
          slider.sliderUI.positionProxy
        )
        .also { it.timelineUnit = ComposeUnit.TimelineUnit("UnitOne", ComposeUnit.IntSize(1, 2)) }

    val propertyCurveTwo =
      PropertyCurve.create(
        ElementState(),
        property,
        InspectorLayout.timelineHeaderHeightScaled() + propertyCurveOne.height,
        1,
        slider.sliderUI.positionProxy
      )

    slider.sliderUI.elements.add(propertyCurveOne)
    slider.sliderUI.elements.add(propertyCurveTwo)

    // Component has tooltips.
    ui.render() // paint() method within render() should be called to update BoxedLabel positions.
    slider.scanForTooltips().also { tooltip ->
      val widthTooltip = tooltip.firstOrNull { it.description.startsWith("width") }
      val heightTooltip = tooltip.firstOrNull { it.description.startsWith("height") }
      assertNotNull(widthTooltip)
      assertNotNull(heightTooltip)
      assertEquals("UnitOne", widthTooltip.header)
      assertEquals("UnitOne", heightTooltip.header)
      assertEquals("width ( 1 , _ )", widthTooltip.description)
      assertEquals("height ( _ , 2 )", heightTooltip.description)
    }

    // Uncomment to preview ui.
    // ui.render()
  }
}
