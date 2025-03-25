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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.Range
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.systemtrace.CpuSystemTraceData
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.ComboBox
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import javax.swing.JTable

class CpuAnalysisFramesTabTest {
  @Test
  fun tableIsPopulatedByLayer() {
    val traceData: CpuSystemTraceData = Mockito.mock(CpuSystemTraceData::class.java).apply {
      whenever(androidFrameLayers).thenReturn(LAYERS)
    }
    val cpuCapture: CpuCapture = Mockito.mock(CpuCapture::class.java).apply {
      whenever(systemTraceData).thenReturn(traceData)
    }
    val framesTabModel = CpuAnalysisFramesTabModel(Range()).apply {
      dataSeries.add(cpuCapture)
    }
    val framesTab = CpuAnalysisFramesTab(Mockito.mock(StudioProfilersView::class.java), framesTabModel)
    val treeWalker = TreeWalker(framesTab)

    // Table is populated from the first layer.
    val table1 = treeWalker.descendants().filterIsInstance<JTable>().first()
    assertThat(table1.getValueAt(0, 0)).isEqualTo(1)
    // Switch to the second layer.
    val layerDropdown = treeWalker.descendants().filterIsInstance<ComboBox<*>>().first()
    layerDropdown.selectedIndex = 1
    val table2 = treeWalker.descendants().filterIsInstance<JTable>().first()
    assertThat(table2.getValueAt(0, 0)).isEqualTo(3)
  }

  @Test
  fun selectingTableRowUpdatedViewRange() {
    val traceData: CpuSystemTraceData = Mockito.mock(CpuSystemTraceData::class.java).apply {
      whenever(androidFrameLayers).thenReturn(LAYERS.subList(0, 1))
    }
    val cpuCapture: CpuCapture = Mockito.mock(CpuCapture::class.java).apply {
      whenever(systemTraceData).thenReturn(traceData)
    }
    val framesTabModel = CpuAnalysisFramesTabModel(Range()).apply {
      dataSeries.add(cpuCapture)
    }
    val viewRange = Range()
    val studioProfilersView: StudioProfilersView = Mockito.mock(StudioProfilersView::class.java, Mockito.RETURNS_DEEP_STUBS).apply {
      whenever(studioProfilers.stage.timeline.viewRange).thenReturn(viewRange)
    }
    val framesTab = CpuAnalysisFramesTab(studioProfilersView, framesTabModel)
    val treeWalker = TreeWalker(framesTab)
    val table = treeWalker.descendants().filterIsInstance<JTable>().first()
    table.selectionModel.setSelectionInterval(1, 1)
    assertThat(viewRange.isSameAs(Range(10.0, 27.0))).isTrue()
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
                    .addFrameEvent(makeFrame(1, 10000, 17000, 0))
                    .addFrameEvent(makeFrame(2, 27000, 13000, 0)))
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("App")
                    .addFrameEvent(makeFrame(1, 0, 5000, 0))
                    .addFrameEvent(makeFrame(2, 10000, 10000, 0)))
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("GPU")
                    .addFrameEvent(makeFrame(1, 5000, 1000, 0))
                    .addFrameEvent(makeFrame(2, 20000, 2000, 0)))
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("Composition")
                    .addFrameEvent(makeFrame(1, 7000, 3000, 0))
                    .addFrameEvent(makeFrame(2, 25000, 2000, 0)))
        .build(),
      TraceProcessor.AndroidFrameEventsResult.Layer.newBuilder()
        .setLayerName("com.example.NewActivity#0")
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("Display")
                    .addFrameEvent(makeFrame(3, 40000, 10000, 0)))
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("App")
                    .addFrameEvent(makeFrame(3, 20000, 4000, 0)))
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("GPU")
                    .addFrameEvent(makeFrame(3, 25000, 1500, 0)))
        .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                    .setPhaseName("Composition")
                    .addFrameEvent(makeFrame(3, 27000, 2000, 0)))
        .build()
    )
  }
}