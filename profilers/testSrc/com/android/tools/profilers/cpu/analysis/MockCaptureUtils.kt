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

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.android.tools.profilers.cpu.systemtrace.CpuSystemTraceData
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import perfetto.protos.PerfettoTrace

object MockCaptureUtils {
  fun frame(frameNumber: Int, timestamp: Long, duration: Long, depth: Int): TraceProcessor.AndroidFrameEventsResult.FrameEvent =
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
                  .addFrameEvent(frame(1, 10000, 27000, 0))
                  .addFrameEvent(frame(2, 27000, 33000, 0))
                  .addFrameEvent(frame(3, 33000, 42000, 0))
                  .addFrameEvent(frame(4, 42000, 50000, 0)))
      .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                  .setPhaseName("App")
                  .addFrameEvent(frame(1, 0, 5000, 0))
                  .addFrameEvent(frame(2, 10000, 10000, 0))
                  .addFrameEvent(frame(3, 20000, 3000, 0))
                  .addFrameEvent(frame(4, 30000, 4000, 0)))
      .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                  .setPhaseName("GPU")
                  .addFrameEvent(frame(1, 5000, 1000, 0))
                  .addFrameEvent(frame(2, 20000, 2000, 0))
                  .addFrameEvent(frame(3, 25000, 3000, 0))
                  .addFrameEvent(frame(4, 35000, 4000, 0)))
      .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                  .setPhaseName("Composition")
                  .addFrameEvent(frame(1, 7000, 3000, 0))
                  .addFrameEvent(frame(2, 25000, 2000, 0))
                  .addFrameEvent(frame(3, 30000, 3000, 0))
                  .addFrameEvent(frame(4, 40000, 2000, 0)))
      .build()
  )

  val TIMELINE_EVENTS = listOf(
    AndroidFrameTimelineEvent(1, 1,
                              0L, 1000L, 1500L, "",
                              PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_LATE,
                              PerfettoTrace.FrameTimelineEvent.JankType.JANK_APP_DEADLINE_MISSED,
                              onTimeFinish = false, gpuComposition = false, layoutDepth = 0),
    AndroidFrameTimelineEvent(2, 2,
                              2000L, 3000L, 2900L, "",
                              PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_ON_TIME,
                              PerfettoTrace.FrameTimelineEvent.JankType.JANK_NONE,
                              onTimeFinish = true, gpuComposition = false, layoutDepth = 0)
  )

  val SYSTEM_TRACE_DATA = Mockito.mock(CpuSystemTraceData::class.java).apply {
    whenever(androidFrameLayers).thenReturn(LAYERS)
    whenever(androidFrameTimelineEvents).thenReturn(TIMELINE_EVENTS)
  }
  val CPU_CAPTURE = Mockito.mock(CpuCapture::class.java).apply {
    whenever(systemTraceData).thenReturn(SYSTEM_TRACE_DATA)
  }
}