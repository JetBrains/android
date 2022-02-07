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
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimelineLineTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `check line contains points`() {
    val slider = TestUtils.createTestSlider().apply {
      maximum = 600
    }
    FakeUi(slider.parent).apply { layout() }
    slider.sliderUI.apply {
      val line = TimelineLine(ElementState(), 50, 150, 50, positionProxy)
      assertFalse(line.contains(30, 80))
      assertTrue(line.contains(51, 80))
      assertTrue(line.contains(150, 80))
      assertFalse(line.contains(160, 80))
      line.move(-100)
      assertFalse(line.contains(30 - 100, 80))
      assertTrue(line.contains(50 - 100, 80))
      assertTrue(line.contains(150 - 100, 80))
      assertFalse(line.contains(160 - 100, 80))
    }
  }

  @Test
  fun `ui with lines renders correctly`() {
    invokeAndWaitIfNeeded {
      val slider = TestUtils.createTestSlider().apply {
        value = 1000
      }
      slider.sliderUI.apply {
        elements.add(TimelineLine(ElementState(), 50, 150, 50, positionProxy).apply {
          status = TimelineElementStatus.Hovered
        })
        elements.add(TimelineLine(ElementState(), 50, 150, 150, positionProxy).apply {
          status = TimelineElementStatus.Dragged
        })
        elements.add(TimelineLine(ElementState(), 50, 150, 250, positionProxy).apply {
          status = TimelineElementStatus.Inactive
        })
        elements.add(TimelineLine(ElementState(), 50, 150, 350, positionProxy).apply {
        })
      }
      val ui = FakeUi(slider.parent)
      // Uncomment to preview ui.
      //ui.render()
      assertNotNull(ui)
    }
  }
}