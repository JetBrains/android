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
package com.android.tools.profilers.perfetto

import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeTraceProcessorService
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.MainProcessSelector
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import perfetto.protos.PerfettoTrace
import java.util.concurrent.TimeUnit

class PerfettoParserTest {

  @Test
  fun `with useTraceProcessor enabled`() {
    val services = FakeIdeProfilerServices()
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")

    val parser = PerfettoParser(MainProcessSelector(), services)
    val capture = parser.parse(traceFile, 1)

    assertThat(capture).isInstanceOf(SystemTraceCpuCapture::class.java)
    assertThat(capture.type).isEqualTo(Cpu.CpuTraceType.PERFETTO)
  }

  @Test
  fun `with UiState appended to trace file`() {
    val services = FakeIdeProfilerServices()
    val fakeTraceProcessorService = services.traceProcessorService as FakeTraceProcessorService
    fakeTraceProcessorService.uiStateForTraceId[1] = java.util.Base64.getEncoder().encodeToString(PerfettoTrace.UiState.newBuilder()
                                                                     .setHighlightProcess(
                                                                       PerfettoTrace.UiState.HighlightProcess.newBuilder().setPid(1001))
                                                                     .setTimelineStartTs(TimeUnit.SECONDS.toNanos(1))
                                                                     .setTimelineEndTs(TimeUnit.SECONDS.toNanos(99))
                                                                     .build().toByteArray())
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")

    val parser = PerfettoParser(MainProcessSelector(), services)
    val capture = parser.parse(traceFile, 1)
    assertThat(capture.mainThreadId).isEqualTo(1001)
    assertThat(capture.timeline.viewRange.min).isEqualTo(TimeUnit.SECONDS.toMicros(1).toDouble())
    assertThat(capture.timeline.viewRange.max).isEqualTo(TimeUnit.SECONDS.toMicros(99).toDouble())
  }

  @Test
  fun `with UiState command line`() {
    val services = FakeIdeProfilerServices()
    val fakeTraceProcessorService = services.traceProcessorService as FakeTraceProcessorService
    fakeTraceProcessorService.uiStateForTraceId[1] = java.util.Base64.getEncoder().encodeToString(PerfettoTrace.UiState.newBuilder()
                                                                     .setHighlightProcess(PerfettoTrace.UiState.HighlightProcess
                                                                                            .newBuilder()
                                                                                            .setCmdline("com.android.phone"))
                                                                     .setTimelineStartTs(TimeUnit.SECONDS.toNanos(1))
                                                                     .setTimelineEndTs(TimeUnit.SECONDS.toNanos(99))
                                                                     .build().toByteArray())
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")

    val parser = PerfettoParser(MainProcessSelector(), services)
    val capture = parser.parse(traceFile, 1)
    assertThat(capture.mainThreadId).isEqualTo(2515)
    assertThat(capture.timeline.viewRange.min).isEqualTo(TimeUnit.SECONDS.toMicros(1).toDouble())
    assertThat(capture.timeline.viewRange.max).isEqualTo(TimeUnit.SECONDS.toMicros(99).toDouble())
  }

  @Test
  fun `with invalid UiState in metadataTable`() {
    val services = FakeIdeProfilerServices()
    val fakeTraceProcessorService = services.traceProcessorService as FakeTraceProcessorService
    fakeTraceProcessorService.uiStateForTraceId[1] = "Not a valid base64 encoded proto"
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")

    val parser = PerfettoParser(MainProcessSelector(), services)
    val capture = parser.parse(traceFile, 1)

    assertThat(capture).isInstanceOf(SystemTraceCpuCapture::class.java)
    assertThat(capture.type).isEqualTo(Cpu.CpuTraceType.PERFETTO)
  }

  @Test
  fun parseAndroidFrameLayers() {
    val services = FakeIdeProfilerServices()
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto_frame_lifecycle.trace")

    val parser = PerfettoParser(MainProcessSelector("android.com.java.profilertester"), services)
    val capture = parser.parse(traceFile, 1)

    assertThat(capture).isInstanceOf(SystemTraceCpuCapture::class.java)
    val systraceCapture = capture as SystemTraceCpuCapture
    val androidFrameLayers = systraceCapture.androidFrameLayers
    assertThat(androidFrameLayers).hasSize(1)
    assertThat(androidFrameLayers[0].layerName).startsWith("android.com.java.profilertester")
    assertThat(androidFrameLayers[0].phaseCount).isEqualTo(4)
  }
}