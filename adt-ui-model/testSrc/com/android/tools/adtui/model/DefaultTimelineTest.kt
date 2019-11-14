/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultTimelineTest {
  private val timeline = DefaultTimeline()

  @Test
  fun zoomIn() {
    timeline.dataRange.set(0.0, 1.0)
    timeline.viewRange.set(0.0, 1.0)
    timeline.zoomIn()
    assertThat(timeline.viewRange.min).isEqualTo(0.25)
    assertThat(timeline.viewRange.max).isEqualTo(0.75)
  }

  @Test
  fun zoomOut() {
    timeline.dataRange.set(0.0, 5.0)
    timeline.viewRange.set(1.0, 2.0)
    timeline.zoomOut()
    assertThat(timeline.viewRange.min).isEqualTo(0.5)
    assertThat(timeline.viewRange.max).isEqualTo(2.5)
  }

  @Test
  fun zoomOut_DataRangeLowerBound() {
    timeline.dataRange.set(0.0, 5.0)
    timeline.viewRange.set(0.5, 2.5)
    timeline.zoomOut()
    assertThat(timeline.viewRange.min).isEqualTo(0.0)
    assertThat(timeline.viewRange.max).isEqualTo(3.5)
  }

  @Test
  fun zoomOut_DataRangeUpperBound() {
    timeline.dataRange.set(0.0, 5.0)
    timeline.viewRange.set(2.5, 4.5)
    timeline.zoomOut()
    assertThat(timeline.viewRange.min).isEqualTo(1.5)
    assertThat(timeline.viewRange.max).isEqualTo(5.0)
  }

  @Test
  fun customZoomRatio() {
    timeline.dataRange.set(0.0, 1.0)
    timeline.viewRange.set(0.0, 1.0)
    timeline.setZoomRatio(0.25)
    timeline.zoomIn()
    assertThat(timeline.viewRange.min).isEqualTo(0.375)
    assertThat(timeline.viewRange.max).isEqualTo(0.625)
  }

  @Test
  fun resetZoom() {
    timeline.dataRange.set(0.0, 5.0)
    timeline.viewRange.set(1.0, 2.0)
    timeline.resetZoom()
    assertThat(timeline.viewRange.min).isEqualTo(0.0)
    assertThat(timeline.viewRange.max).isEqualTo(5.0)
  }

  @Test
  fun frameViewToRange() {
    timeline.dataRange.set(0.0, 5.0)
    timeline.viewRange.set(1.0, 2.0)
    timeline.frameViewToRange(Range(1.5, 2.5))
    assertThat(timeline.viewRange.min).isEqualTo(1.5)
    assertThat(timeline.viewRange.max).isEqualTo(2.5)
  }

  @Test
  fun frameViewToRange_OutOfDataRange() {
    timeline.dataRange.set(1.0, 5.0)
    timeline.viewRange.set(1.0, 2.0)
    timeline.frameViewToRange(Range(0.0, 6.0))
    assertThat(timeline.viewRange.min).isEqualTo(1.0)
    assertThat(timeline.viewRange.max).isEqualTo(5.0)
  }
}