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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.systemtrace.CpuSystemTraceData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import javax.swing.RowSorter
import javax.swing.SortOrder

class CpuAnalysisFramesTabModelTest {
  @Test
  fun tableModelIsPopulated() {
    val framesTabModel = CpuAnalysisFramesTabModel(Range())
    framesTabModel.dataSeries.add(CPU_CAPTURE)
    val tableModel = framesTabModel.tableModels[0]

    assertThat(tableModel.rowCount).isEqualTo(4)
    assertThat(tableModel.columnCount).isEqualTo(5)
    assertThat(tableModel.frameEvents).containsExactly(
      FrameEventRow(1, 0, 10, 5, 1, 3),
      FrameEventRow(2, 10, 27, 10, 2, 2),
      FrameEventRow(3, 20, 33, 3, 3, 3),
      FrameEventRow(4, 30, 42, 4, 4, 2)
    )
    assertThat(tableModel.getColumnName(0)).isEqualTo("Frame #")
    assertThat(tableModel.getColumnName(1)).isEqualTo("Frame Duration")
    assertThat(tableModel.getColumnName(2)).isEqualTo("Application")
    assertThat(tableModel.getColumnName(3)).isEqualTo("Wait for GPU")
    assertThat(tableModel.getColumnName(4)).isEqualTo("Composition")
    assertThat(tableModel.getValueAt(0, 0)).isEqualTo(1)
    assertThat(tableModel.getValueAt(0, 1)).isEqualTo(10)
    assertThat(tableModel.getValueAt(0, 2)).isEqualTo(5)
    assertThat(tableModel.getValueAt(0, 3)).isEqualTo(1)
    assertThat(tableModel.getValueAt(0, 4)).isEqualTo(3)
  }

  @Test
  fun tableShouldBeSortedByValue() {
    val framesTabModel = CpuAnalysisFramesTabModel(Range())
    framesTabModel.dataSeries.add(CPU_CAPTURE)
    val tableModel = framesTabModel.tableModels[0]

    // Sort by Frame number ascending.
    tableModel.sortData(listOf(RowSorter.SortKey(0, SortOrder.ASCENDING)))
    assertThat(tableModel.getValueAt(0, 0)).isEqualTo(1)
    assertThat(tableModel.getValueAt(1, 0)).isEqualTo(2)
    assertThat(tableModel.getValueAt(2, 0)).isEqualTo(3)
    assertThat(tableModel.getValueAt(3, 0)).isEqualTo(4)

    // Sort by application time ascending.
    tableModel.sortData(listOf(RowSorter.SortKey(2, SortOrder.ASCENDING)))
    assertThat(tableModel.getValueAt(0, 0)).isEqualTo(3)
    assertThat(tableModel.getValueAt(1, 0)).isEqualTo(4)
    assertThat(tableModel.getValueAt(2, 0)).isEqualTo(1)
    assertThat(tableModel.getValueAt(3, 0)).isEqualTo(2)

    // Sort by GPU time descending.
    tableModel.sortData(listOf(RowSorter.SortKey(3, SortOrder.DESCENDING)))
    assertThat(tableModel.getValueAt(0, 0)).isEqualTo(4)
    assertThat(tableModel.getValueAt(1, 0)).isEqualTo(3)
    assertThat(tableModel.getValueAt(2, 0)).isEqualTo(2)
    assertThat(tableModel.getValueAt(3, 0)).isEqualTo(1)
  }

  private companion object {
    fun makeFrame(frameNumber: Int, timestamp: Long, duration: Long, depth: Int): TraceProcessor.AndroidFrameEventsResult.FrameEvent =
      TraceProcessor.AndroidFrameEventsResult.FrameEvent.newBuilder()
        .setFrameNumber(frameNumber)
        .setTimestampNanoseconds(timestamp)
        .setDurationNanoseconds(duration)
        .setDepth(depth)
        .build()

    val LAYERS = listOf(
      TraceProcessor.AndroidFrameEventsResult.Layer.newBuilder()
        .setLayerName("com.example.MainActivity#0")
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("Display")
                    .addFrameEvent(makeFrame(1, 10000, 27000, 0))
                    .addFrameEvent(makeFrame(2, 27000, 33000, 0))
                    .addFrameEvent(makeFrame(3, 33000, 42000, 0))
                    .addFrameEvent(makeFrame(4, 42000, 50000, 0)))
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("App")
                    .addFrameEvent(makeFrame(1, 0, 5000, 0))
                    .addFrameEvent(makeFrame(2, 10000, 10000, 0))
                    .addFrameEvent(makeFrame(3, 20000, 3000, 0))
                    .addFrameEvent(makeFrame(4, 30000, 4000, 0)))
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("GPU")
                    .addFrameEvent(makeFrame(1, 5000, 1000, 0))
                    .addFrameEvent(makeFrame(2, 20000, 2000, 0))
                    .addFrameEvent(makeFrame(3, 25000, 3000, 0))
                    .addFrameEvent(makeFrame(4, 35000, 4000, 0)))
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("Composition")
                    .addFrameEvent(makeFrame(1, 7000, 3000, 0))
                    .addFrameEvent(makeFrame(2, 25000, 2000, 0))
                    .addFrameEvent(makeFrame(3, 30000, 3000, 0))
                    .addFrameEvent(makeFrame(4, 40000, 2000, 0)))
        .build()
    )

    val SYSTEM_TRACE_DATA: CpuSystemTraceData = Mockito.mock(CpuSystemTraceData::class.java).apply {
      Mockito.`when`(getAndroidFrameLayers()).thenReturn(LAYERS)
    }
    val CPU_CAPTURE: CpuCapture = Mockito.mock(CpuCapture::class.java).apply {
      Mockito.`when`(systemTraceData).thenReturn(SYSTEM_TRACE_DATA)
    }
  }
}