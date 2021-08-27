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

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidFrameEventTrackModelTest {
  @Test
  fun eventsAreGroupedByDepth() {
    val trackModel = AndroidFrameEventTrackModel(PHASE_PROTO, Range(0.0, 10.0), listOf())
    assertThat(trackModel.series.size).isEqualTo(2)
    assertThat(trackModel.series[0].series).containsExactly(
      SeriesData(0L, AndroidFrameEvent.Padding),
      SeriesData(2L, AndroidFrameEvent.Data(3, 2, 1)),
      SeriesData(3L, AndroidFrameEvent.Padding)
    ).inOrder()
    assertThat(trackModel.series[1].series).containsExactly(
      SeriesData(0L, AndroidFrameEvent.Data(0, 0, 1)),
      SeriesData(1L, AndroidFrameEvent.Data(1, 1, 1)),
      SeriesData(2L, AndroidFrameEvent.Data(2, 2, 1)),
      SeriesData(3L, AndroidFrameEvent.Padding)
    )
  }

  @Test
  fun ongoingEventsShouldHaveMaxEndTimestamp() {
    val phase = TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
      .setPhaseName("App")
      .addAllFrameEvent(listOf(
        makeFrame(0, 0, 1000, 0),
        makeFrame(1, 5000, -1, 0)))
      .build()
    val trackModel = AndroidFrameEventTrackModel(phase, Range(0.0, 10.0), listOf())
    assertThat(trackModel.series[0].series).containsExactly(
      SeriesData(0L, AndroidFrameEvent.Data(0, 0, 1)),
      SeriesData(1L, AndroidFrameEvent.Padding),
      SeriesData(5L, AndroidFrameEvent.Data(1, 5, Long.MAX_VALUE))
    ).inOrder()
  }

  private companion object {
    fun makeFrame(frameNumber: Int, timestamp: Long, duration: Long, depth: Int
    ): TraceProcessor.AndroidFrameEventsResult.FrameEvent =
      TraceProcessor.AndroidFrameEventsResult.FrameEvent.newBuilder()
        .setFrameNumber(frameNumber)
        .setTimestampNanoseconds(timestamp)
        .setDurationNanoseconds(duration)
        .setDepth(depth)
        .build()

    val PHASE_PROTO: TraceProcessor.AndroidFrameEventsResult.Phase =
      TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
        .setPhaseName("App")
        .addAllFrameEvent(listOf(
          makeFrame(0, 0, 1000, 0),
          makeFrame(1, 1000, 1000, 0),
          makeFrame(2, 2000, 1000, 0),
          makeFrame(3, 2000, 1000, 1)))
        .build()
  }
}