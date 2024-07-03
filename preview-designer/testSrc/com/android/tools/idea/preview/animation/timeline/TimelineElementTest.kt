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
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.animation.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimelineElementTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val edtRule = EdtRule()

  @RunsInEdt
  @Test
  fun `create element`() {
    val slider =
      TestUtils.createTestSlider().apply {
        maximum = 600
        // Call layoutAndDispatchEvents() so positionProxy returns correct values
        FakeUi(this.parent).apply { layoutAndDispatchEvents() }
      }
    slider.sliderUI.apply {
      val line = TestUtils.TestTimelineElement(50, 50)
      assertEquals(0, line.offsetPx)
      assertEquals(TimelineElementStatus.Inactive, line.status)
    }
  }

  @RunsInEdt
  @Test
  fun `copy line`() {
    val slider =
      TestUtils.createTestSlider().apply {
        maximum = 600
        // Call layoutAndDispatchEvents() so positionProxy returns correct values
        FakeUi(this.parent).apply { layoutAndDispatchEvents() }
      }
    slider.sliderUI.apply {
      val line = TestUtils.TestTimelineElement(50, 50)
      assertEquals(0, line.offsetPx)
      assertEquals(TimelineElementStatus.Inactive, line.status)
    }
  }

  @RunsInEdt
  @Test
  fun `setNewOffsetCallback invoked`() {
    val slider =
      TestUtils.createTestSlider().apply {
        maximum = 600
        // Call layoutAndDispatchEvents() so positionProxy returns correct values
        FakeUi(this.parent).apply { layoutAndDispatchEvents() }
      }
    slider.sliderUI.apply {
      var newOffset = 0
      val line = TestUtils.TestTimelineElement(50, 50)
      line.setNewOffsetCallback { newOffset = it }
      line.setNewOffset(-100)
      assertEquals(-100, newOffset)
    }
  }

  @RunsInEdt
  @Test
  fun `create empty timeline element`() {
    val slider =
      TestUtils.createTestSlider().apply {
        maximum = 600
        // Call layoutAndDispatchEvents() so positionProxy returns correct values
        FakeUi(this.parent).apply { layoutAndDispatchEvents() }
      }
    slider.sliderUI.apply {
      val parent =
        ParentTimelineElement(0, SupportedAnimationManager.FrozenState(false), emptyList()).apply {
          Disposer.register(projectRule.testRootDisposable, this)
        }
      assertEquals(0, parent.minX)
      assertEquals(0, parent.maxX)
      assertEquals(0, parent.offsetPx)
      assertEquals(0, parent.height)
      assertEquals(0, parent.heightScaled())
    }
  }
}
