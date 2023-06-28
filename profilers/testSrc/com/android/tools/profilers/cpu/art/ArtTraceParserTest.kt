/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.cpu.art

import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.intellij.openapi.util.io.FileUtil
import org.junit.Test
import java.io.FileOutputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtTraceParserTest {
  @Test
  fun verifyFileHasArtHeaderMismatchMagicNumberWithSimplepref() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = ArtTraceParser.verifyFileHasArtHeader(trace);
    assertFalse { result }
  }

  @Test
  fun verifyFileHasArtHeaderMismatchMagicNumberWithAtrace() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("atrace.ctrace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = ArtTraceParser.verifyFileHasArtHeader(trace);
    assertFalse { result }
  }

  @Test
  fun verifyFileHasArtHeaderMismatchMagicNumberWithPerfetto() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("perfetto.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = ArtTraceParser.verifyFileHasArtHeader(trace);
    assertFalse { result }
  }

  @Test
  fun verifyFileHasArtHeaderMatchNonStreaming() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("art_non_streaming.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = ArtTraceParser.verifyFileHasArtHeader(trace);
    assertTrue { result }
  }

  @Test
  fun verifyFileHasArtHeaderMatchStreaming() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("art_streaming.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = ArtTraceParser.verifyFileHasArtHeader(trace);
    assertTrue { result }
  }

  @Test
  fun verifyFileHasArtHeaderMagicNumberMismatch() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("empty_trace.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = ArtTraceParser.verifyFileHasArtHeader(trace);
    assertFalse { result }
  }
}