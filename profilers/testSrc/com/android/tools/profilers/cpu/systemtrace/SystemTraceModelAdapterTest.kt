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
import com.android.tools.profilers.cpu.systemtrace.CounterDataUtils.aggregateCounters
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
  fun `non-grouped counter name is filtered out during aggregation`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(1L, 100.0), Pair(3L, 200.0), Pair(5L, 300.0))),
      CounterModel("2", sortedMapOf(Pair(2L, 25.0), Pair(4L, 50.0), Pair(6L, 75.0)))
    )
    val groupMapping = mapOf("1" to "foo")
    val aggregatedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = false)

    assertThat(aggregatedCounters.keys.size).isEqualTo(1)
    assertThat(aggregatedCounters.containsKey("foo")).isTrue()

    val aggregatedCounter = aggregatedCounters["foo"]!!
    // Expected results should only have counter "1" data as it is the only one with a grouping (defined in 'groupMapping').
    val expectedCounter = listOf<SeriesData<Long>>(SeriesData(1L, 100), SeriesData(3L, 200), SeriesData(5L, 300))

    assertThat(aggregatedCounter).isEqualTo(expectedCounter)
  }

  @Test
  fun `test aggregating counter data with non-overlapping staggered timestamps, non-normalized start time`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(1L, 100.0), Pair(3L, 200.0), Pair(5L, 300.0))),
      CounterModel("2", sortedMapOf(Pair(2L, 25.0), Pair(4L, 50.0), Pair(6L, 75.0)))
    )
    val groupMapping = mapOf("1" to "foo", "2" to "foo")
    val aggregatedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = false)

    assertThat(aggregatedCounters.keys.size).isEqualTo(1)
    assertThat(aggregatedCounters.containsKey("foo")).isTrue()

    val aggregatedCounter = aggregatedCounters["foo"]!!
    val expectedCounter = listOf<SeriesData<Long>>(SeriesData(1L, 100), SeriesData(2L, 125), SeriesData(3L, 225), SeriesData(4L, 250),
                                                   SeriesData(5L, 350), SeriesData(6L, 375))

    assertThat(aggregatedCounter).isEqualTo(expectedCounter)
  }

  @Test
  fun `test aggregating counter data with non-overlapping non-staggered timestamps, non-normalized start time`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(1L, 100.0), Pair(2L, 200.0), Pair(3L, 300.0))),
      CounterModel("2", sortedMapOf(Pair(4L, 25.0), Pair(5L, 50.0), Pair(6L, 75.0)))
    )
    val groupMapping = mapOf("1" to "foo", "2" to "foo")
    val aggregatedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = false)

    assertThat(aggregatedCounters.keys.size).isEqualTo(1)
    assertThat(aggregatedCounters.containsKey("foo")).isTrue()

    val aggregatedCounter = aggregatedCounters["foo"]!!
    val expectedCounter = listOf<SeriesData<Long>>(SeriesData(1L, 100), SeriesData(2L, 200), SeriesData(3L, 300), SeriesData(4L, 325),
                                                   SeriesData(5L, 350), SeriesData(6L, 375))

    assertThat(aggregatedCounter).isEqualTo(expectedCounter)
  }

  @Test
  fun `test aggregating counter data with non-overlapping non-staggered timestamps (later timestamps first), non-normalized start time`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(4L, 25.0), Pair(5L, 50.0), Pair(6L, 75.0))),
      CounterModel("2", sortedMapOf(Pair(1L, 100.0), Pair(2L, 200.0), Pair(3L, 300.0)))
    )
    val groupMapping = mapOf("1" to "foo", "2" to "foo")
    val aggregatedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = false)

    assertThat(aggregatedCounters.keys.size).isEqualTo(1)
    assertThat(aggregatedCounters.containsKey("foo")).isTrue()

    val aggregatedCounter = aggregatedCounters["foo"]!!
    val expectedCounter = listOf<SeriesData<Long>>(SeriesData(1L, 100), SeriesData(2L, 200), SeriesData(3L, 300), SeriesData(4L, 325),
                                                   SeriesData(5L, 350), SeriesData(6L, 375))

    assertThat(aggregatedCounter).isEqualTo(expectedCounter)
  }

  @Test
  fun `test aggregating counter data with overlapping timestamps, non-normalized start time`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(1L, 100.0), Pair(3L, 200.0), Pair(5L, 300.0))),
      CounterModel("2", sortedMapOf(Pair(2L, 25.0), Pair(3L, 50.0), Pair(6L, 75.0)))
    )
    val groupMapping = mapOf("1" to "foo", "2" to "foo")
    val aggregatedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = false)

    assertThat(aggregatedCounters.keys.size).isEqualTo(1)
    assertThat(aggregatedCounters.containsKey("foo")).isTrue()

    val aggregatedCounter = aggregatedCounters["foo"]!!
    val expectedCounter = listOf<SeriesData<Long>>(SeriesData(1L, 100), SeriesData(2L, 125), SeriesData(3L, 250),
                                                   SeriesData(5L, 350), SeriesData(6L, 375))

    assertThat(aggregatedCounter).isEqualTo(expectedCounter)
  }

  @Test
  fun `test aggregating counter data with non-grouped counter, non-normalized start time`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(1L, 100.0), Pair(3L, 200.0), Pair(5L, 300.0))),
      CounterModel("2", sortedMapOf(Pair(2L, 25.0), Pair(4L, 50.0), Pair(6L, 75.0))),
      CounterModel("3", sortedMapOf(Pair(2L, 25.0), Pair(4L, 50.0), Pair(6L, 75.0)))
    )
    val groupMapping = mapOf("1" to "foo", "2" to "foo")
    val aggregatedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = false)

    assertThat(aggregatedCounters.keys.size).isEqualTo(1)
    assertThat(aggregatedCounters.containsKey("foo")).isTrue()

    val aggregatedCounter = aggregatedCounters["foo"]!!
    val expectedCounter = listOf<SeriesData<Long>>(SeriesData(1L, 100), SeriesData(2L, 125), SeriesData(3L, 225), SeriesData(4L, 250),
                                                   SeriesData(5L, 350), SeriesData(6L, 375))

    assertThat(aggregatedCounter).isEqualTo(expectedCounter)
  }


  @Test
  fun `test combining counter data with non-overlapping staggered timestamps, normalized start time`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(1L, 100.0), Pair(3L, 200.0), Pair(5L, 300.0))),
      CounterModel("2", sortedMapOf(Pair(2L, 25.0), Pair(4L, 50.0), Pair(6L, 75.0)))
    )
    val groupMapping = mapOf("1" to "foo", "2" to "foo")
    val groupedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = true)

    assertThat(groupedCounters.keys.size).isEqualTo(1)
    assertThat(groupedCounters.containsKey("foo")).isTrue()

    val combinedCounters = groupedCounters["foo"]!!
    val expectedCounters = listOf<SeriesData<Long>>(SeriesData(2L, 125), SeriesData(3L, 225), SeriesData(4L, 250),
                                                    SeriesData(5L, 350), SeriesData(6L, 375))

    assertThat(combinedCounters).isEqualTo(expectedCounters)
  }

  @Test
  fun `test combining counter data with non-overlapping non-staggered timestamps, normalized start time`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(1L, 100.0), Pair(2L, 200.0), Pair(3L, 300.0))),
      CounterModel("2", sortedMapOf(Pair(4L, 25.0), Pair(5L, 50.0), Pair(6L, 75.0)))
    )
    val groupMapping = mapOf("1" to "foo", "2" to "foo")
    val groupedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = true)

    assertThat(groupedCounters.keys.size).isEqualTo(1)
    assertThat(groupedCounters.containsKey("foo")).isTrue()

    val combinedCounters = groupedCounters["foo"]!!
    val expectedCounters = listOf<SeriesData<Long>>(SeriesData(4L, 325), SeriesData(5L, 350), SeriesData(6L, 375))

    assertThat(combinedCounters).isEqualTo(expectedCounters)
  }

  @Test
  fun `test combining counter data with non-overlapping non-staggered timestamps (later timestamps first), normalized start time`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(4L, 25.0), Pair(5L, 50.0), Pair(6L, 75.0))),
      CounterModel("2", sortedMapOf(Pair(1L, 100.0), Pair(2L, 200.0), Pair(3L, 300.0)))
    )
    val groupMapping = mapOf("1" to "foo", "2" to "foo")
    val groupedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = true)

    assertThat(groupedCounters.keys.size).isEqualTo(1)
    assertThat(groupedCounters.containsKey("foo")).isTrue()

    val combinedCounters = groupedCounters["foo"]!!
    val expectedCounters = listOf<SeriesData<Long>>(SeriesData(4L, 325), SeriesData(5L, 350), SeriesData(6L, 375))

    assertThat(combinedCounters).isEqualTo(expectedCounters)
  }

  @Test
  fun `test combining counter data with overlapping timestamps, normalized start time`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(1L, 100.0), Pair(3L, 200.0), Pair(5L, 300.0))),
      CounterModel("2", sortedMapOf(Pair(2L, 25.0), Pair(3L, 50.0), Pair(6L, 75.0)))
    )
    val groupMapping = mapOf("1" to "foo", "2" to "foo")
    val groupedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = true)

    assertThat(groupedCounters.keys.size).isEqualTo(1)
    assertThat(groupedCounters.containsKey("foo")).isTrue()

    val combinedCounters = groupedCounters["foo"]!!
    val expectedCounters = listOf<SeriesData<Long>>(SeriesData(2L, 125), SeriesData(3L, 250), SeriesData(5L, 350), SeriesData(6L, 375))

    assertThat(combinedCounters).isEqualTo(expectedCounters)
  }

  @Test
  fun `test combining counter data with non-grouped counter, normalized start time`() {
    val powerRails = listOf(
      CounterModel("1", sortedMapOf(Pair(1L, 100.0), Pair(3L, 200.0), Pair(5L, 300.0))),
      CounterModel("2", sortedMapOf(Pair(2L, 25.0), Pair(4L, 50.0), Pair(6L, 75.0))),
      CounterModel("3", sortedMapOf(Pair(2L, 25.0), Pair(4L, 50.0), Pair(6L, 75.0)))
    )
    val groupMapping = mapOf("1" to "foo", "2" to "foo")
    val groupedCounters = aggregateCounters(powerRails, groupMapping, normalizeStartTime = true)

    assertThat(groupedCounters.keys.size).isEqualTo(1)
    assertThat(groupedCounters.containsKey("foo")).isTrue()

    val combinedCounters = groupedCounters["foo"]!!
    val expectedCounters = listOf<SeriesData<Long>>(SeriesData(2L, 125), SeriesData(3L, 225), SeriesData(4L, 250),
                                                    SeriesData(5L, 350), SeriesData(6L, 375))

    assertThat(combinedCounters).isEqualTo(expectedCounters)
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