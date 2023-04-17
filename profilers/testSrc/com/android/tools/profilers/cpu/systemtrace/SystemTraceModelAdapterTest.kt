/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.SeriesData
import com.android.tools.profilers.cpu.systemtrace.CounterDataUtils.convertSeriesDataToDeltaSeries
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SystemTraceModelAdapterTest {
  @Test
  fun `padding fills gaps`() {
    val events = listOf(1, 2, 3, 5, 8)
    val paddedEvents = events.padded({ it.toLong() }, { it.toLong() + 1 }, { it }, ::Pair)
    assertThat(paddedEvents).isEqualTo(listOf(SeriesData(0, 0L to 1L),
                                              SeriesData(1, 1),
                                              SeriesData(2, 2),
                                              SeriesData(3, 3),
                                              SeriesData(4, 4L to 5L),
                                              SeriesData(5, 5),
                                              SeriesData(6, 6L to 8L),
                                              SeriesData(8, 8),
                                              SeriesData(9, 9L to Long.MAX_VALUE)))
  }

  @Test
  fun `test transforming counter data to delta series`() {
    val sortedData = sortedMapOf(
      "foo" to listOf(SeriesData(1L, 100L), SeriesData(2L, 125L), SeriesData(3L, 225L), SeriesData(4L, 250L),
                      SeriesData(5L, 350L), SeriesData(6L, 375L))
    )
    val deltaDataMap = convertSeriesDataToDeltaSeries(sortedData)

    assertThat(deltaDataMap.keys.size).isEqualTo(1)
    assertThat(deltaDataMap.containsKey("foo")).isTrue()

    val aggregatedCounter = deltaDataMap["foo"]!!
    val expectedCounter = listOf<SeriesData<Long>>(SeriesData(2L, 25L), SeriesData(3L, 100L), SeriesData(4L, 25L),
                                                    SeriesData(5L, 100L), SeriesData(6L, 25L))

    assertThat(aggregatedCounter).isEqualTo(expectedCounter)
  }
}