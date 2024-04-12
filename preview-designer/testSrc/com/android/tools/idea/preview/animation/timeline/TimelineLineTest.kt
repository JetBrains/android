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
package com.android.tools.idea.preview.animation.timeline

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.preview.animation.TestUtils
import com.android.tools.idea.preview.animation.TestUtils.scanForTooltips
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlin.test.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimelineLineTest {
  @get:Rule val edtRule = EdtRule()

  @RunsInEdt
  @Test
  fun `check line contains points`() {
    val slider = TestUtils.createTestSlider().apply { maximum = 600 }
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    slider.sliderUI.apply {
      val line = TimelineLine(0, null, 50, 150, 50)
      assertFalse(line.contains(30, 85))
      assertTrue(line.contains(51, 85))
      assertTrue(line.contains(150, 85))
      assertFalse(line.contains(160, 85))
      val lineWithOffset = TimelineLine(-100, null, 50, 150, 50)
      assertFalse(lineWithOffset.contains(30 - 100, 85))
      assertTrue(lineWithOffset.contains(50 - 100, 85))
      assertTrue(lineWithOffset.contains(150 - 100, 85))
      assertFalse(lineWithOffset.contains(160 - 100, 85))
      // No tooltips.
      ui.render()
      assertEquals(0, slider.scanForTooltips().size)
    }
  }

  @RunsInEdt
  @Test
  fun `ui with lines renders correctly`() {
    val slider = TestUtils.createTestSlider().apply { value = 1000 }
    slider.sliderUI.apply {
      elements =
        listOf(
          TimelineLine(0, null, 50, 150, 50).apply { status = TimelineElementStatus.Hovered },
          TimelineLine(0, null, 50, 150, 150).apply { status = TimelineElementStatus.Dragged },
          TimelineLine(0, null, 50, 150, 250).apply { status = TimelineElementStatus.Inactive },
          TimelineLine(0, null, 50, 150, 350).apply {},
        )
    }
    // Call layoutAndDispatchEvents() so positionProxy returns correct values
    val ui = FakeUi(slider.parent).apply { layoutAndDispatchEvents() }
    // Uncomment to preview ui.
    // ui.render()
    assertNotNull(ui)
  }
}
