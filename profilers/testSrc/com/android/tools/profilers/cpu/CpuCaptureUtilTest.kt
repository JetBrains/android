/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.profilers.cpu.CpuCaptureParser.FileHeaderParsingFailureException
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertNull

class CpuCaptureUtilTest {
  @Test
  fun artStreamingKnownTypeTest() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("art_streaming.trace")
    // Known trace type is provided as the currently known trace type
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.ART)
    assertThat(result).isEqualTo(TraceType.ART)
  }

  @Test
  fun artNonStreamingKnownTypeTest() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("art_non_streaming.trace")
    // Known trace type is provided as the currently known trace type
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.ART)
    assertThat(result).isEqualTo(TraceType.ART)
  }

  @Test(expected = FileHeaderParsingFailureException::class)
  fun artKnownTypeVerificationError() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("simpleperf.trace")
    // Known trace type is provided as the currently known trace type and file fail trace verification
    CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.ART)
  }

  @Test
  fun simpleperfKnownTypeTest() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("simpleperf.trace");
    // Known trace type is provided as the currently known trace type
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.SIMPLEPERF)
    assertThat(result).isEqualTo(TraceType.SIMPLEPERF)
  }

  @Test(expected = FileHeaderParsingFailureException::class)
  fun simpleperfKnownTypeVerificationError() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("art_streaming.trace")
    // Known trace type is provided as the currently known trace type and file fail trace verification
    CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.SIMPLEPERF)
  }

  @Test
  fun atraceKnownTypeTest() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace.ctrace");
    // Known trace type is provided as the currently known trace type
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.ATRACE)
    assertThat(result).isEqualTo(TraceType.ATRACE)
  }

  @Test(expected = FileHeaderParsingFailureException::class)
  fun atraceKnownTypeVerificationError() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("simpleperf.trace");
    // Known trace type is provided as the currently known trace type and file fail trace verification
    CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.ATRACE)
  }

  @Test
  fun perfettoKnownTypeTest() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace");
    // Known trace type is provided as the currently known trace type
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.PERFETTO)
    assertThat(result).isEqualTo(TraceType.PERFETTO)
  }

  @Test(expected = FileHeaderParsingFailureException::class)
  fun perfettoKnownTypeVerificationError() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("art_streaming.trace");
    // Known trace type is provided as the currently known trace type and file fail trace verification
    CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.PERFETTO)
  }

  @Test
  fun artStreamingUnknownType() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("art_streaming.trace")
    // Unknown trace type is provided as the currently known trace type
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.UNSPECIFIED)
    assertThat(result).isEqualTo(TraceType.ART)
  }

  @Test
  fun artNonStreamingUnknownType() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("art_non_streaming.trace")
    // Unknown trace type is provided as the currently known trace type
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.UNSPECIFIED)
    assertThat(result).isEqualTo(TraceType.ART)
  }

  @Test
  fun simpleperfUnknownType() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("simpleperf.trace")
    // Unknown trace type is provided as the currently known trace type
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.UNSPECIFIED)
    assertThat(result).isEqualTo(TraceType.SIMPLEPERF)
  }

  @Test
  fun atraceUnknownType() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    // Unknown trace type is provided as the currently known trace type
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.UNSPECIFIED)
    assertThat(result).isEqualTo(TraceType.ATRACE)
  }

  @Test
  fun perfettoUnknownType() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    // Unknown trace type is provided as the currently known trace type
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.UNSPECIFIED)
    assertThat(result).isEqualTo(TraceType.PERFETTO)
  }

  @Test
  fun unknownTypeNoneTypeMatches() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("simpleperf_trace_without_symbols.trace")
    // Unknown trace type is provided as the currently known trace type and none trace type matches
    val result = CpuCaptureParserUtil.getFileTraceType(traceFile, TraceType.UNSPECIFIED)
    assertNull(result)
  }
}
