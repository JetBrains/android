/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InMemoryDataSeriesTest {
  @Test
  fun emptyDataList() {
    assertThat(TestInMemoryDataSeries(mutableListOf()).getDataForRange(Range(0.0, 100.0))).isEmpty()
  }

  @Test
  fun emptyRange() {
    assertThat(TestInMemoryDataSeries(generateDataList(1L, 10L)).getDataForRange(Range())).isEmpty()
  }

  @Test
  fun fullRange() {
    val dataList = generateDataList(1L, 10L)
    assertThat(TestInMemoryDataSeries(dataList).getDataForRange(Range(0.0, 101.0))).containsExactlyElementsIn(dataList)
  }

  /**
   * 1 2 | 3 4 5 | 6 7
   * should return {2, 3, 4, 5, 6}
   */
  @Test
  fun rangeIntersectsWithDataList() {
    assertThat(TestInMemoryDataSeries(generateDataList(1L, 10L)).getDataForRange(Range(25.0, 55.0))).containsExactly(
      SeriesData(20L, 2L),
      SeriesData(30L, 3L),
      SeriesData(40L, 4L),
      SeriesData(50L, 5L),
      SeriesData(60L, 6L),
    )
  }

  /**
   * 1 2 | | 3 4
   * should return {2, 3}
   */
  @Test
  fun rangeBetweenTwoDataPoints() {
    assertThat(TestInMemoryDataSeries(generateDataList(1L, 4L)).getDataForRange(Range(21.0, 22.0))).containsExactly(
      SeriesData(20L, 2L),
      SeriesData(30L, 3L)
    )
  }

  /**
   * | | 1 2 3
   * should return {1}
   */
  @Test
  fun rangeBeforeAllDataPoints() {
    assertThat(TestInMemoryDataSeries(generateDataList(1L, 3L)).getDataForRange(Range(0.0, 5.0))).containsExactly(
      SeriesData(10L, 1L)
    )
  }

  /**
   * 8 9 10 | |
   * should return {10}
   */
  @Test
  fun rangeAfterAllDataPoints() {
    assertThat(TestInMemoryDataSeries(generateDataList(8L, 10L)).getDataForRange(Range(101.0, 105.0))).containsExactly(
      SeriesData(100L, 10L),
    )
  }

  /**
   *   |   |
   * 1 2 3 4 5
   *   |   |
   * should return {2, 3, 4}
   */
  @Test
  fun rangeRightOnDataPoints() {
    assertThat(TestInMemoryDataSeries(generateDataList(1L, 5L)).getDataForRange(Range(20.0, 40.0))).containsExactly(
      SeriesData(20L, 2L),
      SeriesData(30L, 3L),
      SeriesData(40L, 4L)
    )
  }

  /**
   * | 1 |
   * | | 1
   * 1 | |
   * should all return {1}
   */
  @Test
  fun singleElementList() {
    val dataList = generateDataList(1L, 1L)
    assertThat(TestInMemoryDataSeries(dataList).getDataForRange(Range(0.0, 20.0))).containsExactlyElementsIn(dataList)
    assertThat(TestInMemoryDataSeries(dataList).getDataForRange(Range(0.0, 5.0))).containsExactlyElementsIn(dataList)
    assertThat(TestInMemoryDataSeries(dataList).getDataForRange(Range(20.0, 30.0))).containsExactlyElementsIn(dataList)
  }

  /**
   * | | 1 2
   * should return {1}
   * 1 2 | |
   * should return {2}
   * 1 | | 2
   * | 1 | 2
   * 1 | 2 |
   * | 1 2 |
   * should all return {1, 2}
   */
  @Test
  fun twoElementList() {
    val dataList = generateDataList(1L, 2L)
    assertThat(TestInMemoryDataSeries(dataList).getDataForRange(Range(0.0, 5.0))).containsExactly(SeriesData(10L, 1L))
    assertThat(TestInMemoryDataSeries(dataList).getDataForRange(Range(25.0, 30.0))).containsExactly(SeriesData(20L, 2L))
    assertThat(TestInMemoryDataSeries(dataList).getDataForRange(Range(11.0, 15.0))).containsExactlyElementsIn(dataList)
    assertThat(TestInMemoryDataSeries(dataList).getDataForRange(Range(0.0, 15.0))).containsExactlyElementsIn(dataList)
    assertThat(TestInMemoryDataSeries(dataList).getDataForRange(Range(15.0, 25.0))).containsExactlyElementsIn(dataList)
    assertThat(TestInMemoryDataSeries(dataList).getDataForRange(Range(0.0, 25.0))).containsExactlyElementsIn(dataList)
  }

  private class TestInMemoryDataSeries(val dataList: MutableList<SeriesData<Long>>) : InMemoryDataSeries<Long>() {
    override fun inMemoryDataList(): MutableList<SeriesData<Long>> {
      return dataList
    }
  }

  private companion object {
    fun generateDataList(start: Long, end: Long): MutableList<SeriesData<Long>> {
      return (start..end).map { SeriesData(it * 10, it) }.toMutableList()
    }
  }
}