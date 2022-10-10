/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.systemtrace.AtraceParser
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class CpuUsageTest {

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuUsageTest", transportService)

  private val services = FakeIdeProfilerServices()
  private val profilers by lazy { StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer) }

  @Test
  fun atraceCaptureCreatesMergedDataSeries() {
    val parser = AtraceParser(MainProcessSelector(idHint = 1))
    val capture = parser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"), 0)
    val usage = CpuUsage(profilers, capture.range, capture.range, capture)
    assertThat(usage.cpuSeries.series).isNotEmpty()
  }

  @Test
  fun perfettoCaptureCreatesMergedDataSeries() {
    val parser = AtraceParser(Trace.UserOptions.TraceType.PERFETTO, MainProcessSelector())
    val capture = parser.parse(CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage.trace"), 0)
    val usage = CpuUsage(profilers, capture.range, capture.range, capture)
    assertThat(usage.cpuSeries.series).isNotEmpty()
  }

  @Test
  fun nullCaptureIsHandled() {
    val viewRange = Range(0.0, 1.0)
    val dataRange = Range(0.0, 10.0)
    val usage = CpuUsage(profilers, viewRange, dataRange, null)
    assertThat(usage.cpuSeries.series).isEmpty()
  }
}