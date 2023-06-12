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
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import org.junit.Test

class TimelineElementTest {

  @Test
  fun `create element`(): Unit = invokeAndWaitIfNeeded {
    val slider =
      TestUtils.createTestSlider().apply {
        maximum = 600
        // Call layoutAndDispatchEvents() so positionProxy returns correct values
        FakeUi(this.parent).apply { layoutAndDispatchEvents() }
      }
    slider.sliderUI.apply {
      val line = TestUtils.TestTimelineElement(50, 50, positionProxy)
      assertEquals(0, line.offsetPx)
      assertFalse { line.frozen }
      assertEquals(TimelineElementStatus.Inactive, line.status)
      assertEquals(0, line.state.valueOffset)
    }
  }

  @Test
  fun `copy line`(): Unit = invokeAndWaitIfNeeded {
    val slider =
      TestUtils.createTestSlider().apply {
        maximum = 600
        // Call layoutAndDispatchEvents() so positionProxy returns correct values
        FakeUi(this.parent).apply { layoutAndDispatchEvents() }
      }
    slider.sliderUI.apply {
      val line = TestUtils.TestTimelineElement(50, 50, positionProxy)
      val copy = TestUtils.TestTimelineElement(50, 50, positionProxy, line.state)
      assertEquals(0, copy.offsetPx)
      assertFalse { copy.frozen }
      assertEquals(TimelineElementStatus.Inactive, copy.status)
      assertEquals(0, copy.state.valueOffset)
    }
  }

  @Test
  fun `move to the right and copy line`(): Unit = invokeAndWaitIfNeeded {
    val slider =
      TestUtils.createTestSlider().apply {
        maximum = 600
        // Call layoutAndDispatchEvents() so positionProxy returns correct values
        FakeUi(this.parent).apply { layoutAndDispatchEvents() }
      }
    slider.sliderUI.apply {
      val line = TestUtils.TestTimelineElement(50, 50, positionProxy)
      line.move(100)
      val copy = TestUtils.TestTimelineElement(50, 50, positionProxy, line.state)
      assertEquals(100, copy.offsetPx)
    }
  }

  @Test
  fun `move to the left and copy line`(): Unit = invokeAndWaitIfNeeded {
    val slider =
      TestUtils.createTestSlider().apply {
        maximum = 600
        // Call layoutAndDispatchEvents() so positionProxy returns correct values
        FakeUi(this.parent).apply { layoutAndDispatchEvents() }
      }
    slider.sliderUI.apply {
      val line = TestUtils.TestTimelineElement(50, 50, positionProxy)
      line.move(-100)
      val copy = TestUtils.TestTimelineElement(50, 50, positionProxy, line.state)
      assertEquals(-100, copy.offsetPx)
    }
  }

  @Test
  fun `move and reset line`(): Unit = invokeAndWaitIfNeeded {
    val slider =
      TestUtils.createTestSlider().apply {
        maximum = 600
        // Call layoutAndDispatchEvents() so positionProxy returns correct values
        FakeUi(this.parent).apply { layoutAndDispatchEvents() }
      }
    slider.sliderUI.apply {
      val line = TestUtils.TestTimelineElement(50, 50, positionProxy)
      line.move(-100)
      assertEquals(-100, line.offsetPx)
      assertNotEquals(0, line.state.valueOffset)
      line.reset()
      assertEquals(0, line.offsetPx)
      assertEquals(0, line.state.valueOffset)
    }
  }

  @Test
  fun `move and reset parent line`(): Unit = invokeAndWaitIfNeeded {
    val slider =
      TestUtils.createTestSlider().apply {
        maximum = 600
        // Call layoutAndDispatchEvents() so positionProxy returns correct values
        FakeUi(this.parent).apply { layoutAndDispatchEvents() }
      }
    slider.sliderUI.apply {
      val sharedState = ElementState()
      val line1 = TestUtils.TestTimelineElement(50, 50, positionProxy, sharedState)
      val line2 = TestUtils.TestTimelineElement(50, 50, positionProxy, sharedState)
      val parent = ParentTimelineElement(sharedState, listOf(line1, line2), positionProxy)
      parent.move(100)
      assertEquals(100, line1.offsetPx)
      assertEquals(100, line2.offsetPx)
      assertEquals(100, parent.offsetPx)
      assertNotEquals(0, sharedState.valueOffset)
      parent.reset()
      assertEquals(0, line1.offsetPx)
      assertEquals(0, line2.offsetPx)
      assertEquals(0, parent.offsetPx)
      assertEquals(0, sharedState.valueOffset)
    }
  }
}
