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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class CpuCaptureMinimapModelTest {
  private val timer = FakeTimer()

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuCaptureMinimapModelTest", FakeCpuService(), FakeProfilerService(timer), FakeTransportService(timer))

  private lateinit var profilers: StudioProfilers
  @Before
  fun setUp() {
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), FakeIdeProfilerServices(), timer)
  }

  @Test
  fun selectionRangeIsBoundByCaptureRange() {
    val mockCapture = Mockito.mock(SystemTraceCpuCapture::class.java)
    whenever(mockCapture.range).thenReturn(Range(1.0, 10.0))
    whenever(mockCapture.type).thenReturn(Trace.UserOptions.TraceType.ATRACE)
    whenever(mockCapture.timeline).thenReturn(DefaultTimeline())

    val minimapModel = CpuCaptureMinimapModel(profilers, mockCapture, Range())
    minimapModel.rangeSelectionModel.set(0.0, 15.0)
    // Selection range should be confined to the capture range.
    assertThat(minimapModel.rangeSelectionModel.selectionRange.isSameAs(Range(1.0, 10.0))).isTrue()
  }

  @Test
  fun defaultViewIsCaptureView() {
    val timeline = DefaultTimeline()
    timeline.viewRange.set(1.0,5.0)
    val mockCapture = Mockito.mock(SystemTraceCpuCapture::class.java)
    whenever(mockCapture.range).thenReturn(Range(1.0, 10.0))
    whenever(mockCapture.type).thenReturn(Trace.UserOptions.TraceType.PERFETTO)
    whenever(mockCapture.timeline).thenReturn(timeline)

    val minimapModel = CpuCaptureMinimapModel(profilers, mockCapture, Range())
    assertThat(minimapModel.rangeSelectionModel.selectionRange.min).isEqualTo(timeline.viewRange.min)
    assertThat(minimapModel.rangeSelectionModel.selectionRange.max).isEqualTo(timeline.viewRange.max)
  }
}