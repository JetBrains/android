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
import com.android.tools.idea.compose.preview.animation.TestUtils
import com.android.tools.idea.compose.preview.animation.TestUtils.scanForTooltips
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class UnsupportedLabelTest {
  @Test
  fun `create and dispose labels`() {
    val slider = TestUtils.createTestSlider()

    // Create labels, all are visible.
    val labelOne = UnsupportedLabel(slider, ElementState(""), 0, slider.sliderUI.positionProxy)
    val labelTwo = UnsupportedLabel(slider, ElementState(""), 0, slider.sliderUI.positionProxy)
    assertTrue { slider.components[1].isVisible }
    assertTrue { slider.components[2].isVisible }
    // componentCount is +1 to the number of labels here and checks below because slider also
    // contains Thumb component.
    assertEquals(3, slider.componentCount)

    // Dispose first label
    labelOne.dispose()
    assertFalse { slider.components[1].isVisible }

    // Instead of creating new label, find and enable unused label.
    UnsupportedLabel(slider, ElementState(""), 0, slider.sliderUI.positionProxy)
    assertTrue { slider.components[1].isVisible }
    assertEquals(3, slider.componentCount)

    // Dispose second label
    labelTwo.dispose()
    assertFalse { slider.components[2].isVisible }

    // Instead of creating new label, find and enable unused label.
    UnsupportedLabel(slider, ElementState(""), 0, slider.sliderUI.positionProxy)
    assertTrue { slider.components[2].isVisible }
    assertEquals(3, slider.componentCount)

    // All labels are enabled, create new one.
    val labelThree = UnsupportedLabel(slider, ElementState(""), 0, slider.sliderUI.positionProxy)
    assertEquals(4, slider.componentCount)

    // Dispose all labels
    labelOne.dispose()
    labelTwo.dispose()
    labelThree.dispose()
    assertFalse { slider.components[1].isVisible }
    assertFalse { slider.components[2].isVisible }
    assertFalse { slider.components[3].isVisible }
  }

  @Test
  fun `create ui with labels`() {
    invokeAndWaitIfNeeded {
      val slider = TestUtils.createTestSlider()

      // Call layoutAndDispatchEvents() so positionProxy returns correct values
      val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }

      var height = 25
      for (i in 0..4) {
        slider.sliderUI.elements.add(
          UnsupportedLabel(slider, ElementState(""), height, slider.sliderUI.positionProxy).apply {
            height += this.height
          }
        )
      }

      // Call layoutAndDispatchEvents() so all JComponents are updated and visible.
      ui.layoutAndDispatchEvents()

      // No tooltips.
      assertEquals(0, slider.scanForTooltips().size)
      // Uncomment to preview ui.
      // ui.render()
    }
  }
}
