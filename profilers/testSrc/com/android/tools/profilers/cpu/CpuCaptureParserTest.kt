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
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

class CpuCaptureParserTest {

  val ANY_TRACE_ID = 3039

  @Test
  fun parsingAValidTraceShouldProduceCpuCapture() {
    val singleThreadExecutor = Executor { it.run() }
    val parser = CpuCaptureParser(singleThreadExecutor)
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("valid_trace.trace")

    val futureCapture = parser.parse(ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.ART)

    // Parsing should create a valid CpuCapture object
    checkValidCapture(futureCapture.get())

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun futureIsReturnedIfCaptureIsParsedAsynchronously() {
    // Used to stop execution before parsing capture
    val preParsingLatch = CountDownLatch(1)
    // Used to stop execution when waiting for parsing to be done
    val waitForParsingLatch = CountDownLatch(1)
    // Create a parser with an asynchronous executor
    val parser = CpuCaptureParser { runnable ->
      Thread {
        preParsingLatch.await()
        runnable.run()
        waitForParsingLatch.countDown()
      }.start()
    }

    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("valid_trace.trace")
    val futureCapture = parser.parse(ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.ART)!!
    // Even if parsing is not finished, a future is returned by the parser.
    assertThat(futureCapture.isDone).isFalse()
    preParsingLatch.countDown()
    waitForParsingLatch.await()
    // Make sure capture is done after parsing
    assertThat(futureCapture.isDone).isTrue()
  }

  @Test
  fun corruptedTraceFileThrowsException() {
    val singleThreadExecutor = Executor { it.run() }
    val parser = CpuCaptureParser(singleThreadExecutor)
    val corruptedTrace = CpuProfilerTestUtils.traceFileToByteString("corrupted_trace.trace") // Malformed trace file.

    // Parsing will fail because the trace is corrupted. However, the future capture should still be created properly (not null).
    val futureCapture = parser.parse(ANY_TRACE_ID, corruptedTrace, CpuProfiler.CpuProfilerType.ART)!!

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
    val singleThreadExecutor = Executor { it.run() }
    val parser = CpuCaptureParser(singleThreadExecutor)
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("valid_trace.trace")
    val firstParsedCapture = parser.parse(ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.ART)
    val secondParsedCapture = parser.parse(ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.ART)

    // Second time we call parse(...) we just return the capture parsed the first time.
    assertThat(secondParsedCapture).isEqualTo(firstParsedCapture)
  }

  @Test
  fun parsingAValidSimpleperfTraceShouldProduceCpuCapture() {
    val singleThreadExecutor = Executor { it.run() }
    val parser = CpuCaptureParser(singleThreadExecutor)

    // Create and parse a simpleperf trace
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val futureCapture = parser.parse(ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.SIMPLE_PERF)

    // Parsing should create a valid CpuCapture object
    checkValidCapture(futureCapture.get())

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun parsingAValidTraceWithWrongProfilerTypeShouldThrowException() {
    val singleThreadExecutor = Executor { it.run() }
    val parser = CpuCaptureParser(singleThreadExecutor)

    // Try to parse a simpleperf trace passing ART as profiler type
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val futureCapture = parser.parse(ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.ART)

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
    val singleThreadExecutor = Executor { it.run() }
    val parser = CpuCaptureParser(singleThreadExecutor)
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val futureCapture = parser.parse(ANY_TRACE_ID, traceBytes, CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER)

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
  }
}
