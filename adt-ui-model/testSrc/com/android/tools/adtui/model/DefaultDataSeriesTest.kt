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
package com.android.tools.adtui.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultDataSeriesTest {
  private val data = listOf(
    SeriesData(0, "A"),
    SeriesData(1, "B"),
    SeriesData(2, "C"),
    SeriesData(3, "D"),
  )

  @Test
  fun `returns empty list for empty series`() {
    val series = DefaultDataSeries<String>()
    assertThat(series.getDataForRange(Range(0.0, 3.0))).isEmpty()
  }

  @Test
  fun `returns empty list for empty range`() {
    val series = DefaultDataSeries<String>()
    data.forEach { series.add(it.x, it.value) }

    // According to Range(), it is only empty if the min is greater than the max. The min and max
    // being equal does not count as empty.
    assertThat(series.getDataForRange(Range(2.0, 1.0))).isEmpty()
  }

  @Test
  fun `returns partial list`() {
    val series = DefaultDataSeries<String>()
    data.forEach { series.add(it.x, it.value) }

    assertThat(series.getDataForRange(Range(1.0, 2.0))).containsExactly(data[1], data[2])
  }

  @Test
  fun `inclusive left and inclusive right`() {
    val series = DefaultDataSeries<String>()
    data.forEach { series.add(it.x, it.value) }

    assertThat(series.getDataForRange(Range(0.0, 3.0))).containsAllIn(data)
  }

  @Test
  fun `right bounds too wide`() {
    val series = DefaultDataSeries<String>()
    data.forEach { series.add(it.x, it.value) }

    assertThat(series.getDataForRange(Range(0.0, 6.0))).containsAllIn(data)
  }

  @Test
  fun `left bounds too wide`() {
    val series = DefaultDataSeries<String>()
    data.forEach { series.add(it.x, it.value) }

    assertThat(series.getDataForRange(Range(-3.0, 3.0))).containsAllIn(data)
  }

  @Test
  fun `left equals right returns single value`() {
    val series = DefaultDataSeries<String>()
    data.forEach { series.add(it.x, it.value) }

    assertThat(series.getDataForRange(Range(data[2].x.toDouble(), data[2].x.toDouble()))).containsExactly(data[2])
  }
}