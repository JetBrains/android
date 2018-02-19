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

import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.protobuf3jarjar.ByteString
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilersTestData
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.ExecutionException

class CpuCaptureParserTest {

  val ANY_TRACE_ID = 3039

  @Test
  fun parsingAValidTraceShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("valid_trace.trace")

    val futureCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.ART)!!

    // Parsing should create a valid CpuCapture object
    checkValidCapture(futureCapture.get())

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun longTraceShouldProduceNullCpuCaptureIfNotParsed() {
    val largeTraceFile = ByteString.copyFrom(ByteArray(CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1))
    val fakeServices = FakeIdeProfilerServices()
    // Decide not to parse long trace files
    fakeServices.setShouldParseLongTraces(false)
    val parser = CpuCaptureParser(fakeServices)
    assertThat(parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, largeTraceFile, CpuProfiler.CpuProfilerType.ART)).isNull()
  }

  @Test
  fun longTraceShouldProduceNotNullCpuCaptureIfParsed() {
    val largeTraceFile = ByteString.copyFrom(ByteArray(CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1))
    val fakeServices = FakeIdeProfilerServices()
    // Decide to parse long trace files
    fakeServices.setShouldParseLongTraces(true)
    val parser = CpuCaptureParser(fakeServices)
    assertThat(parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, largeTraceFile, CpuProfiler.CpuProfilerType.ART)).isNotNull()
  }

  @Test
  fun corruptedTraceFileThrowsException() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val corruptedTrace = CpuProfilerTestUtils.traceFileToByteString("corrupted_trace.trace") // Malformed trace file.

    // Parsing will fail because the trace is corrupted. However, the future capture should still be created properly (not null).
    val futureCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, corruptedTrace, CpuProfiler.CpuProfilerType.ART)!!

    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // We expect getting the capture to throw an ExecutionException because the trace failed to be parsed.
      // CpuCapture fails with an IllegalStateException
      assertThat(e.cause).isInstanceOf(IllegalStateException::class.java)
    }

  }

  @Test
  fun parsingShouldHappenOnlyOnce() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("valid_trace.trace")
    val firstParsedCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.ART)!!
    val secondParsedCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.ART)!!

    // Second time we call parse(...) we just return the capture parsed the first time.
    assertThat(secondParsedCapture).isEqualTo(firstParsedCapture)
  }

  @Test
  fun parsingAValidSimpleperfTraceShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    // Create and parse a simpleperf trace
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val futureCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.SIMPLEPERF)!!

    // Parsing should create a valid CpuCapture object
    checkValidCapture(futureCapture.get())

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun parsingAValidTraceWithWrongProfilerTypeShouldThrowException() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    // Try to parse a simpleperf trace passing ART as profiler type
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val futureCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.ART)!!

    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // Do nothing. We expect the parsing to fail.
    }

  }

  @Test
  fun profilerTypeMustBeSpecified() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val futureCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER)!!

    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // IllegalStateException expected to be thrown because a valid profiler type was not set.
      val cause = e.cause!!
      assertThat(cause).isInstanceOf(IllegalStateException::class.java)
      assertThat(cause.message).contains("Trace file cannot be parsed")
    }
  }

  /**
   * Check some fields of a [CpuCapture] to see if it was properly built.
   */
  private fun checkValidCapture(capture: CpuCapture) {
    val captureRange = capture.range
    assertThat(captureRange.isEmpty).isFalse()
    assertThat(capture.duration).isEqualTo(captureRange.length.toLong())

    val main = capture.mainThreadId
    assertThat(capture.containsThread(main)).isTrue()
    val mainNode = capture.getCaptureNode(main)!!
    assertThat(mainNode.data).isNotNull()

    val threads = capture.threads
    assertThat(threads).isNotEmpty()
    for (thread in threads) {
      assertThat(capture.getCaptureNode(thread.id)).isNotNull()
      assertThat(capture.containsThread(thread.id)).isTrue()
    }

    val nonExistentThreadId = -1
    assertThat(capture.containsThread(nonExistentThreadId)).isFalse()
    assertThat(capture.getCaptureNode(nonExistentThreadId)).isNull()

    assertThat(capture.traceId).isEqualTo(ANY_TRACE_ID)
  }
}
