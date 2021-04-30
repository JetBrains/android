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

private const val DELTA = 0.001

class DefaultTimelineTest {
  private val timeline = DefaultTimeline()

  @Test
  fun zoomIn() {
    timeline.dataRange.set(0.0, 1.0)
    timeline.viewRange.set(0.0, 1.0)
    timeline.zoomIn()
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(0.125)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(0.875)
  }

  @Test
  fun zoomIn_DoesNotCollapse() {
    // Set to two points very close to each other.
    timeline.viewRange.set(4.499328215754487E12, 4.499328215754489E12)
    timeline.zoomIn()
    assertThat(timeline.viewRange.length).isGreaterThan(0.0)
  }

  @Test
  fun zoomOut() {
    timeline.dataRange.set(0.0, 5.0)
    timeline.viewRange.set(1.0, 2.5)
    timeline.zoomOut()
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(0.75)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(2.75)
  }

  @Test
  fun zoomOut_DataRangeLowerBound() {
    timeline.dataRange.set(0.0, 5.0)
    timeline.viewRange.set(0.5, 3.5)
    timeline.zoomOut()
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(0.0)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(4.0)
  }

  @Test
  fun zoomOut_DataRangeUpperBound() {
    timeline.dataRange.set(0.0, 5.0)
    timeline.viewRange.set(1.75, 4.75)
    timeline.zoomOut()
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(1.25)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(5.0)
  }

  @Test
  fun customZoomRatio() {
    timeline.dataRange.set(0.0, 1.0)
    timeline.viewRange.set(0.0, 1.0)
    timeline.setZoomRatio(0.25)
    timeline.zoomIn()
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(0.375)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(0.625)
  }

  @Test
  fun resetZoom() {
    timeline.dataRange.set(0.0, 5.0)
    timeline.viewRange.set(1.0, 2.0)
    timeline.resetZoom()
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(0.0)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(5.0)
  }

  @Test
  fun frameViewToRange() {
    timeline.dataRange.set(0.0, 5.0)
    timeline.viewRange.set(1.0, 2.0)
    timeline.frameViewToRange(Range(1.5, 2.5))
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(1.5)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(2.5)
  }

  @Test
  fun frameViewToRange_OutOfDataRange() {
    timeline.dataRange.set(1.0, 5.0)
    timeline.viewRange.set(1.0, 2.0)
    timeline.frameViewToRange(Range(0.0, 6.0))
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(1.0)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(5.0)
  }

  @Test
  fun pan() {
    timeline.dataRange.set(0.0, 100.0)
    timeline.viewRange.set(20.0, 30.0)

    timeline.panView(-10.0)
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(10.0)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(20.0)

    timeline.panView(10.0)
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(20.0)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(30.0)

    timeline.panView(-30.0)
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(0.0)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(10.0)

    timeline.panView(130.0)
    assertThat(timeline.viewRange.min).isWithin(DELTA).of(90.0)
    assertThat(timeline.viewRange.max).isWithin(DELTA).of(100.0)
  }
}