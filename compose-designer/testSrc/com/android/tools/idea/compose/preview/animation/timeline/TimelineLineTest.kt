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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class TimelineLineTest {

  @Test
  fun `check line contains points`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider().apply { maximum = 600 }
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    slider.sliderUI.apply {
      val line = TimelineLine(ElementState(), 50, 150, 50, positionProxy)
      assertFalse(line.contains(30, 85))
      assertTrue(line.contains(51, 85))
      assertTrue(line.contains(150, 85))
      assertFalse(line.contains(160, 85))
      line.move(-100)
      assertFalse(line.contains(30 - 100, 85))
      assertTrue(line.contains(50 - 100, 85))
      assertTrue(line.contains(150 - 100, 85))
      assertFalse(line.contains(160 - 100, 85))
      // No tooltips.
      ui.render()
      assertEquals(0, slider.scanForTooltips().size)
    }
  }

  @Test
  fun `ui with lines renders correctly`(): Unit = invokeAndWaitIfNeeded {
    val slider = TestUtils.createTestSlider().apply { value = 1000 }
    slider.sliderUI.apply {
      elements.add(
        TimelineLine(ElementState(), 50, 150, 50, positionProxy).apply {
          status = TimelineElementStatus.Hovered
        }
      )
      elements.add(
        TimelineLine(ElementState(), 50, 150, 150, positionProxy).apply {
          status = TimelineElementStatus.Dragged
        }
      )
      elements.add(
        TimelineLine(ElementState(), 50, 150, 250, positionProxy).apply {
          status = TimelineElementStatus.Inactive
        }
      )
      elements.add(TimelineLine(ElementState(), 50, 150, 350, positionProxy).apply {})
    }
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    // Uncomment to preview ui.
    // ui.render()
    assertNotNull(ui)
  }
}
