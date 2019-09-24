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

import com.android.testutils.TestUtils
import com.android.tools.profiler.proto.Cpu
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilersTestData
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.ExecutionException

class CpuCaptureParserTest {

  val ANY_TRACE_ID = 3039L

  @Test
  fun parsingAValidTraceShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("valid_trace.trace")

    val futureCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, Cpu.CpuTraceType.ART)!!

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
    assertThat(parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, largeTraceFile, Cpu.CpuTraceType.ART)).isNull()
  }

  @Test
  fun longTraceShouldProduceNotNullCpuCaptureIfParsed() {
    val largeTraceFile = ByteString.copyFrom(ByteArray(CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1))
    val fakeServices = FakeIdeProfilerServices()
    // Decide to parse long trace files
    fakeServices.setShouldParseLongTraces(true)
    val parser = CpuCaptureParser(fakeServices)
    assertThat(parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, largeTraceFile, Cpu.CpuTraceType.ART)).isNotNull()
  }

  @Test
  fun corruptedTraceFileThrowsException() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val corruptedTrace = CpuProfilerTestUtils.traceFileToByteString("corrupted_trace.trace") // Malformed trace file.

    // Parsing will fail because the trace is corrupted. However, the future capture should still be created properly (not null).
    val futureCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, corruptedTrace, Cpu.CpuTraceType.ART)!!

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
    val firstParsedCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, Cpu.CpuTraceType.ART)!!
    val secondParsedCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, Cpu.CpuTraceType.ART)!!

    // Second time we call parse(...) we just return the capture parsed the first time.
    assertThat(secondParsedCapture).isEqualTo(firstParsedCapture)
  }

  @Test
  fun parsingAValidSimpleperfTraceShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    // Create and parse a simpleperf trace
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val futureCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, Cpu.CpuTraceType.SIMPLEPERF)!!

    // Parsing should create a valid CpuCapture object
    checkValidCapture(futureCapture.get())

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun parsingAValidTraceWithWrongtraceTypeShouldThrowException() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    // Try to parse a simpleperf trace passing ART as profiler type
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val futureCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes, Cpu.CpuTraceType.ART)!!

    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // Do nothing. We expect the parsing to fail.
    }

  }

  @Test
  fun traceTypeMustBeSpecified() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val futureCapture = parser.parse(ProfilersTestData.SESSION_DATA, ANY_TRACE_ID, traceBytes,
                                     Cpu.CpuTraceType.UNSPECIFIED_TYPE)!!

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

  @Test
  fun parsingArtFilesShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val futureCapture = parser.parse(CpuProfilerTestUtils.getTraceFile("valid_trace.trace"))!!

    val capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.traceId).isEqualTo(CpuCaptureParser.IMPORTED_TRACE_ID)
    // ART capture should be dual clock
    assertThat(capture.isDualClock).isTrue()
  }

  @Test
  fun parsingSimpleperfFilesShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val traceFile = CpuProfilerTestUtils.getTraceFile("simpleperf.trace")

    val futureCapture = parser.parse(traceFile)!!
    val capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.traceId).isEqualTo(CpuCaptureParser.IMPORTED_TRACE_ID)
    // Simpleperf capture should not be dual clock
    assertThat(capture.isDualClock).isFalse()
  }

  @Test
  fun parsingAtraceFilesShouldCompleteIfFlagEnabled() {
    val services = FakeIdeProfilerServices()
    val parser = CpuCaptureParser(services)
    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace")

    // First, try to parse the capture with the flag disabled.
    services.enableAtrace(false)
    var futureCapture = parser.parse(traceFile)!!
    var capture = futureCapture.get()
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    assertThat(capture).isNull()

    // Now enable the flag and try to parse it again, assume the user canceled the dialog.
    services.setListBoxOptionsIndex(-1)
    services.enableAtrace(true)
    futureCapture = parser.parse(traceFile)!!
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    capture = futureCapture.get()
    assertThat(capture).isNull()

    // Now set a process select callback to return a process
    services.setListBoxOptionsIndex(0)
    services.enableAtrace(true)
    futureCapture = parser.parse(traceFile)!!
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.threads).hasSize(1)
  }

  @Test
  fun parsingPerfettoFilesShouldCompleteIfFlagEnabled() {
    val services = FakeIdeProfilerServices()
    val parser = CpuCaptureParser(services)
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")

    // First, try to parse the capture with the atrace flag disabled.
    services.enableAtrace(false)
    services.enablePerfetto(true)
    var futureCapture = parser.parse(traceFile)!!
    var capture = futureCapture.get()
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    assertThat(capture).isNull()

    // Now try to parse the capture with the atrace flag enabled but perfetto disabled.
    services.enableAtrace(true)
    services.enablePerfetto(false)
    futureCapture = parser.parse(traceFile)!!
    capture = futureCapture.get()
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    assertThat(capture).isNull()

    // Now enable the flag and try to parse it again, assume the user canceled the dialog.
    services.setListBoxOptionsIndex(-1)
    services.enableAtrace(true)
    services.enablePerfetto(true)
    futureCapture = parser.parse(traceFile)!!
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    capture = futureCapture.get()
    assertThat(capture).isNull()

    // Now set a process select callback to return a process
    services.setListBoxOptionsIndex(0)
    services.enableAtrace(true)
    services.enablePerfetto(true)
    futureCapture = parser.parse(traceFile)!!
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.threads).hasSize(17)
  }

  @Test
  fun parsingPerfettoWithProcessNameHintAutoSelectsProcess() {
    val services = FakeIdeProfilerServices()
    val parser = CpuCaptureParser(services)
    parser.setProcessNameHint("surfaceflinger", 0)
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    // Now enable the flag and try to parse it again, assume the user canceled the dialog. If the dialog is shown.
    services.setListBoxOptionsIndex(-1)
    services.enableAtrace(true)
    services.enablePerfetto(true)
    val futureCapture = parser.parse(traceFile)!!
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    val capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.threads).hasSize(7)
  }

  @Test
  fun parsingWithAPackageNameWillBringThatProcessToTop() {
    val services = FakeIdeProfilerServices()
    val parser = CpuCaptureParser(services)
    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")

    services.applicationId = "displayingbitmaps"
    services.setListBoxOptionsIndex(0)
    services.enableAtrace(true)
    val futureCapture = parser.parse(traceFile)!!
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    val capture = futureCapture.get()
    capture.threads
    val mainNode = capture.getCaptureNode(capture.mainThreadId)!!
    assertThat(mainNode.data.name).isEqualTo("splayingbitmaps")
  }

  @Test
  fun parsingInvalidTraceProducesNullCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val futureCapture = parser.parse(CpuProfilerTestUtils.getTraceFile("corrupted_trace.trace"))!!

    val capture = futureCapture.get()
    assertThat(capture).isNull()
  }

  @Test
  fun parsingDirectoriesOrNonExistentFilesReturnNullCompletableFuture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    val nonExistentFile = File("")
    assertThat(nonExistentFile.exists()).isFalse()

    var futureCapture = parser.parse(nonExistentFile)
    assertThat(futureCapture).isNull()

    val dir = TestUtils.getWorkspaceFile("")
    assertThat(dir.exists()).isTrue()

    futureCapture = parser.parse(dir)
    assertThat(futureCapture).isNull()
  }

  @Test
  fun longFileShouldProduceNullCompletableFutureIfNotParsed() {
    val someFile = File(TestUtils.createTempDirDeletedOnExit(), "any_trace")
    someFile.writeBytes(ByteArray(CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1))

    val fakeServices = FakeIdeProfilerServices()
    // Decide not to parse long trace files
    fakeServices.setShouldParseLongTraces(false)
    val parser = CpuCaptureParser(fakeServices)
    assertThat(parser.parse(someFile)).isNull()
  }

  @Test
  fun longFileShouldProduceNotNullCompletableFutureIfNotParsed() {
    val someFile = File(TestUtils.createTempDirDeletedOnExit(), "any_trace")
    someFile.writeBytes(ByteArray(CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1))

    val fakeServices = FakeIdeProfilerServices()
    // Decide to parse long trace files
    fakeServices.setShouldParseLongTraces(true)
    val parser = CpuCaptureParser(fakeServices)
    assertThat(parser.parse(someFile)).isNotNull()
  }

  /**
   * Check some fields of a [CpuCapture] to see if it was properly built.
   */
  private fun checkValidCapture(capture: CpuCapture) {
    val captureRange = capture.range
    assertThat(captureRange.isEmpty).isFalse()
    assertThat(capture.durationUs).isEqualTo(captureRange.length.toLong())

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
