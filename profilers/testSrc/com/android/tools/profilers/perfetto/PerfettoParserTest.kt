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
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.MainProcessSelector
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PerfettoParserTest {

  @Test
  fun `with useTraceProcessor disabled`() {
    val services = FakeIdeProfilerServices()
    services.enableUseTraceProcessor(false)

    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")

    val parser = PerfettoParser(MainProcessSelector(), services)
    val capture = parser.parse(traceFile, 1)

    assertThat(capture).isInstanceOf(SystemTraceCpuCapture::class.java)
    assertThat(capture.type).isEqualTo(Cpu.CpuTraceType.PERFETTO)
  }

  @Test
  fun `with useTraceProcessor enabled`() {
    val services = FakeIdeProfilerServices()
    services.enableUseTraceProcessor(true)

    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")

    val parser = PerfettoParser(MainProcessSelector(), services)
    val capture = parser.parse(traceFile, 1)

    assertThat(capture).isInstanceOf(SystemTraceCpuCapture::class.java)
    assertThat(capture.type).isEqualTo(Cpu.CpuTraceType.PERFETTO)
  }
}