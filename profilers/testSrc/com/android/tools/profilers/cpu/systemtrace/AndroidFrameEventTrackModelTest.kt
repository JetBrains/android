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
  fun trackOrderAndDisplayName() {
    val phases = listOf(
      TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder().setPhaseName("Display").build(),
      TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder().setPhaseName("App").build(),
      TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder().setPhaseName("GPU").build(),
      TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder().setPhaseName("Composition").build(),
    )
    val sortedPhaseNames = phases.sortedWith(AndroidFrameEventTrackModel.trackComparator).map {
      AndroidFrameEventTrackModel.getDisplayName(it.phaseName)
    }
    assertThat(sortedPhaseNames).containsExactly("Application", "Wait for GPU", "Composition", "Frames on display").inOrder()
  }

  @Test
  fun eventsAreGroupedByDepth() {
    val trackModel = AndroidFrameEventTrackModel(FRAME_EVENTS, Range(0.0, 10.0))
    assertThat(trackModel.series.size).isEqualTo(2)
    assertThat(trackModel.series[0].series).containsExactly(
      SeriesData(0L, AndroidFrameEvent.Data(0)),
      SeriesData(1L, AndroidFrameEvent.Data(1)),
      SeriesData(2L, AndroidFrameEvent.Data(2))
    ).inOrder()
    assertThat(trackModel.series[1].series).containsExactly(
      SeriesData(0L, AndroidFrameEvent.Padding),
      SeriesData(2L, AndroidFrameEvent.Data(3))
    )
  }

  private companion object {
    fun makeFrame(frameNumber: Int, timestamp: Long, duration: Long, depth: Int): TraceProcessor.AndroidFrameEventsResult.FrameEvent =
      TraceProcessor.AndroidFrameEventsResult.FrameEvent.newBuilder()
        .setFrameNumber(frameNumber)
        .setTimestampNanoseconds(timestamp)
        .setDurationNanoseconds(duration)
        .setDepth(depth)
        .build()

    val FRAME_EVENTS = listOf(
      makeFrame(0, 0, 1000, 0),
      makeFrame(1, 1000, 1000, 0),
      makeFrame(2, 2000, 1000, 0),
      makeFrame(3, 2000, 1000, 1),
    )
  }
}