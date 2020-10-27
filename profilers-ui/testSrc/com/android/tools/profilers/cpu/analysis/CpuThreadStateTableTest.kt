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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.LazyDataSeries
import com.android.tools.profilers.cpu.ThreadState
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class CpuThreadStateTableTest {
  @get:Rule
  val grpcChannel = FakeGrpcChannel("CpuThreadStateTableTest")

  private val ideServices = FakeIdeProfilerServices()
  private lateinit var profilers: StudioProfilers

  @Before
  fun setUp() {
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideServices)
  }

  @Test
  fun tableIsPopulated() {
    val range = Range(0.0, MICROS_IN_MILLI * 5.0)
    val dataSeries = listOf(LazyDataSeries { THREAD_1_STATES })
    val table = CpuThreadStateTable(profilers, dataSeries, range).table

    assertThat(table.rowCount).isEqualTo(2)
    assertThat(table.columnCount).isEqualTo(4)
    assertThat(table.getColumnName(0)).isEqualTo("Thread State")
    assertThat(table.getColumnName(1)).isEqualTo("Duration")
    assertThat(table.getColumnName(2)).isEqualTo("%")
    assertThat(table.getColumnName(3)).isEqualTo("Occurrences")
    assertThat(table.getValueAt(0, 0)).isEqualTo("Running")
    assertThat(table.getValueAt(0, 1)).isEqualTo(MICROS_IN_MILLI * 3)
    assertThat(table.getValueAt(0, 2)).isEqualTo(0.6)
    assertThat(table.getValueAt(0, 3)).isEqualTo(3)
    assertThat(table.getValueAt(1, 0)).isEqualTo("Sleeping")
    assertThat(table.getValueAt(1, 1)).isEqualTo(MICROS_IN_MILLI * 2)
    assertThat(table.getValueAt(1, 2)).isEqualTo(0.4)
    assertThat(table.getValueAt(1, 3)).isEqualTo(2)
  }

  @Test
  fun tableIsUpdatedOnRangeChange() {
    val range = Range(0.0, MICROS_IN_MILLI * 5.0)
    val dataSeries = listOf(LazyDataSeries { THREAD_1_STATES })
    val table = CpuThreadStateTable(profilers, dataSeries, range).table
    range.set(MICROS_IN_MILLI * 2.0, MICROS_IN_MILLI * 3.0)

    assertThat(table.rowCount).isEqualTo(1)
    assertThat(table.columnCount).isEqualTo(4)
    assertThat(table.getValueAt(0, 0)).isEqualTo("Running")
    assertThat(table.getValueAt(0, 1)).isEqualTo(MICROS_IN_MILLI * 1)
    assertThat(table.getValueAt(0, 2)).isEqualTo(1.0)
    assertThat(table.getValueAt(0, 3)).isEqualTo(1)
  }

  @Test
  fun multipleThreads() {
    val range = Range(0.0, MICROS_IN_MILLI * 5.0)
    val dataSeries = listOf(LazyDataSeries { THREAD_1_STATES }, LazyDataSeries { THREAD_2_STATES })
    val table = CpuThreadStateTable(profilers, dataSeries, range).table

    assertThat(table.rowCount).isEqualTo(3)
    assertThat(table.columnCount).isEqualTo(4)
    assertThat(table.getValueAt(0, 0)).isEqualTo("Running")
    assertThat(table.getValueAt(0, 1)).isEqualTo(MICROS_IN_MILLI * 5)
    assertThat(table.getValueAt(0, 2)).isEqualTo(0.5)
    assertThat(table.getValueAt(0, 3)).isEqualTo(5)
    assertThat(table.getValueAt(1, 0)).isEqualTo("Sleeping")
    assertThat(table.getValueAt(1, 1)).isEqualTo(MICROS_IN_MILLI * 4)
    assertThat(table.getValueAt(1, 2)).isEqualTo(0.4)
    assertThat(table.getValueAt(1, 3)).isEqualTo(4)
    assertThat(table.getValueAt(2, 0)).isEqualTo("Dead")
    assertThat(table.getValueAt(2, 1)).isEqualTo(MICROS_IN_MILLI * 1)
    assertThat(table.getValueAt(2, 2)).isEqualTo(0.1)
    assertThat(table.getValueAt(2, 3)).isEqualTo(1)
  }


  @Test
  fun tableShouldBeSortedByValueNotToString() {
    val range = Range(0.0, MICROS_IN_MILLI * 12.0)
    val dataSeries = listOf(LazyDataSeries { THREAD_3_STATES })
    val table = CpuThreadStateTable(profilers, dataSeries, range).table

    assertThat(table.rowCount).isEqualTo(2)
    assertThat(table.columnCount).isEqualTo(4)
    assertThat(table.getValueAt(0, 1)).isEqualTo(MICROS_IN_MILLI * 10)
    assertThat(table.getValueAt(1, 1)).isEqualTo(MICROS_IN_MILLI * 2)
    table.rowSorter.toggleSortOrder(1)
    assertThat(table.getValueAt(0, 1)).isEqualTo(MICROS_IN_MILLI * 2)
    assertThat(table.getValueAt(1, 1)).isEqualTo(MICROS_IN_MILLI * 10)
  }

  companion object {
    val MICROS_IN_MILLI = TimeUnit.MILLISECONDS.toMicros(1)
    val THREAD_1_STATES = listOf(
      SeriesData(0, ThreadState.RUNNING),
      SeriesData(MICROS_IN_MILLI, ThreadState.SLEEPING),
      SeriesData(MICROS_IN_MILLI * 2, ThreadState.RUNNING),
      SeriesData(MICROS_IN_MILLI * 3, ThreadState.SLEEPING),
      SeriesData(MICROS_IN_MILLI * 4, ThreadState.RUNNING),
      SeriesData(MICROS_IN_MILLI * 6, ThreadState.DEAD)
    )
    val THREAD_2_STATES = listOf(
      SeriesData(0, ThreadState.SLEEPING),
      SeriesData(MICROS_IN_MILLI, ThreadState.RUNNING),
      SeriesData(MICROS_IN_MILLI * 2, ThreadState.SLEEPING),
      SeriesData(MICROS_IN_MILLI * 3, ThreadState.RUNNING),
      SeriesData(MICROS_IN_MILLI * 4, ThreadState.DEAD)
    )
    val THREAD_3_STATES = listOf(
      SeriesData(0, ThreadState.SLEEPING),
      SeriesData(MICROS_IN_MILLI * 10, ThreadState.RUNNING),
      SeriesData(MICROS_IN_MILLI * 12, ThreadState.SLEEPING)
    )
  }
}