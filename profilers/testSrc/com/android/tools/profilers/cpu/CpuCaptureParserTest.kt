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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeTraceProcessorService
import com.android.tools.profilers.ProfilersTestData
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class CpuCaptureParserTest {

  private val ANY_TRACE_ID = 3039L

  @JvmField
  @Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun parsingAValidTraceShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val trace = CpuProfilerTestUtils.getTraceFile("valid_trace.trace")
    val futureCapture = parser.parseForTestWithArt(trace, idHint = ProfilersTestData.SESSION_DATA.pid)

    // Parsing should create a valid CpuCapture object
    checkValidCapture(futureCapture.get())

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun longTraceShouldProduceCompletedExceptionallyIfNotParsed() {
    val traceLength = CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1
    val largeTrace = ByteString.copyFrom(ByteArray(traceLength))
    val largeTraceFile = temporaryFolder.newFile()
    largeTrace.writeTo(largeTraceFile.outputStream())

    val fakeServices = FakeIdeProfilerServices()
    // Decide not to parse long trace files
    fakeServices.setShouldProceedYesNoDialog(false)
    val parser = CpuCaptureParser(fakeServices)

    val futureCapture = parser.parseForTest(largeTraceFile, idHint = ProfilersTestData.SESSION_DATA.pid)
    assertThat(futureCapture).isNotNull()
    assertThat(futureCapture.isCompletedExceptionally).isTrue()

    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // We expect getting the capture to throw an ExecutionException because the trace failed to be parsed.
      // CpuCapture fails with an IllegalStateException
      assertThat(e).hasCauseThat().isInstanceOf(CancellationException::class.java)
      assertThat(e).hasCauseThat().hasMessageThat().contains(
        String.format("Parsing of a long (%d bytes) trace file was aborted by the user.", traceLength)
      )
    }
  }

  @Test
  fun longTraceShouldCompletesExceptionally() {
    val largeTrace = ByteString.copyFrom(ByteArray(CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1))
    val largeTraceFile = temporaryFolder.newFile()
    largeTrace.writeTo(largeTraceFile.outputStream())

    val fakeServices = FakeIdeProfilerServices()
    // Decide to parse long trace files
    fakeServices.setShouldProceedYesNoDialog(true)
    val parser = CpuCaptureParser(fakeServices)
    val futureCapture = parser.parseForTest(largeTraceFile)
    assertThat(futureCapture).isNotNull()
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
  }

  @Test
  fun corruptedTraceFileCompletesExceptionally() {
    val services = FakeIdeProfilerServices()
    val parser = CpuCaptureParser(services)
    val corruptedTrace = CpuProfilerTestUtils.getTraceFile("corrupted_trace.trace") // Malformed trace file.

    // Parsing will fail because the trace is corrupted. However, the future capture should still be created properly (not null).
    val futureCapture = parser.parseForTestWithArt(corruptedTrace)
    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // We expect getting the capture to throw an ExecutionException because the trace failed to be parsed.
      // CpuCapture fails with an IllegalStateException
      assertThat(e.cause).isInstanceOf(CpuCaptureParser.ParsingFailureException::class.java)
    } finally {
      val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
      assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.status).isEqualTo(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_ERROR)
    }
  }

  @Test
  fun invalidTraceFilePathCompletesExceptionally() {
    val services = FakeIdeProfilerServices()
    val parser = CpuCaptureParser(services)
    val corruptedTrace = CpuProfilerTestUtils.getTraceFile("") // Trace directory.

    // Parsing will fail because the trace file is a directory. However, the future capture should still be created properly (not null).
    val futureCapture = parser.parseForTestWithArt(corruptedTrace)
    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // We expect getting the capture to throw an ExecutionException because the trace file is a directory.
      // CpuCapture fails with an ExecutionException
      assertThat(e.cause).isInstanceOf(CpuCaptureParser.InvalidPathParsingFailureException::class.java)
    } finally {
      val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
      assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.status).isEqualTo(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PATH_INVALID)
    }
  }

  @Test
  fun parsingShouldHappenOnlyOnce() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    val traceFile = CpuProfilerTestUtils.getTraceFile("valid_trace.trace")
    val firstParsedCapture = parser.parseForTestWithArt(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)
    val secondParsedCapture = parser.parseForTestWithArt(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)

    // Second time we call parse(...) we just return the capture parsed the first time.
    assertThat(secondParsedCapture).isEqualTo(firstParsedCapture)
  }

  @Test
  fun parsingAValidSimpleperfTraceShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    // Create and parse a simpleperf trace
    val traceFile = CpuProfilerTestUtils.getTraceFile("simpleperf.trace")
    val futureCapture = parser.parseForTestWithSimplePerf(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)

    // Parsing should create a valid CpuCapture object
    checkValidCapture(futureCapture.get())

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun parsingAValidTraceWithWrongtraceTypeShouldThrowException() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    // Try to parse a simpleperf trace passing ART as profiler type
    val traceFile = CpuProfilerTestUtils.getTraceFile("simpleperf.trace")
    val futureCapture = parser.parseForTestWithArt(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)

    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // Do nothing. We expect the parsing to fail.
    }
  }

  @Test
  fun traceTypeInferredFromMissingType_SimplePerf() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    val traceFile = CpuProfilerTestUtils.getTraceFile("simpleperf.trace")
    val futureCapture = parser.parseForTest(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)

    // Parsing should create a valid CpuCapture object
    val capture = futureCapture.get()
    checkValidCapture(capture)
    assertThat(capture.type).isEqualTo(Trace.TraceType.SIMPLEPERF)

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun traceTypeInferredFromMissingType_Atrace() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    val futureCapture = parser.parseForTest(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)

    // Parsing should create a valid CpuCapture object
    val capture = futureCapture.get()
    checkValidCapture(capture)
    assertThat(capture.type).isEqualTo(Trace.TraceType.ATRACE)

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun traceTypeInferredFromMissingType_Perfetto() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    val futureCapture = parser.parseForTest(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)

    // Parsing should create a valid CpuCapture object
    val capture = futureCapture.get()
    checkValidCapture(capture)
    assertThat(capture.type).isEqualTo(Trace.TraceType.PERFETTO)

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun parsingArtFilesShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val futureCapture = parser.parseForTest(CpuProfilerTestUtils.getTraceFile("valid_trace.trace"), 234)

    val capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.traceId).isEqualTo(234)
    // ART capture should be dual clock
    assertThat(capture.isDualClock).isTrue()
    assertThat(capture.type).isEqualTo(Trace.TraceType.ART)
  }

  @Test
  fun parsingSimpleperfFilesShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val futureCapture = parser.parseForTest(
      CpuProfilerTestUtils.getTraceFile("simpleperf.trace"), 123)

    val capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.traceId).isEqualTo(123)
    // Simpleperf capture should not be dual clock
    assertThat(capture.isDualClock).isFalse()
    assertThat(capture.type).isEqualTo(Trace.TraceType.SIMPLEPERF)
  }

  @Test
  fun parsingAtrace_userCancelDialog() {
    val services = FakeIdeProfilerServices()
    services.setListBoxOptionsIndex(-1) // Parse the capture, assume the user canceled the dialog.
    val parser = CpuCaptureParser(services)
    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace")

    val futureCapture = parser.parseForTest(traceFile)
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // We expect getting the capture to throw an ExecutionException because the trace failed to be parsed.
      // CpuCapture fails with an IllegalStateException
      assertThat(e).hasCauseThat().isInstanceOf(CancellationException::class.java)
      assertThat(e).hasCauseThat().hasMessageThat().contains("User aborted process choice dialog.")
    }
  }

  @Test
  fun parsingAtrace_userSelectFirst() {
    val services = FakeIdeProfilerServices()
    services.setListBoxOptionsIndex(0)
    val parser = CpuCaptureParser(services)
    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace")

    val futureCapture = parser.parseForTest(traceFile)
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    val capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.threads).hasSize(1)
  }

  @Test
  fun parsingPerfetto_userCancelDialog() {
    val services = FakeIdeProfilerServices()
    services.setListBoxOptionsIndex(-1) // Assume the user canceled the dialog.
    val parser = CpuCaptureParser(services)
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")

    val futureCapture = parser.parseForTest(traceFile)
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // We expect getting the capture to throw an ExecutionException because the trace failed to be parsed.
      // CpuCapture fails with an IllegalStateException
      assertThat(e).hasCauseThat().isInstanceOf(CancellationException::class.java)
      assertThat(e).hasCauseThat().hasMessageThat().contains("User aborted process choice dialog.")
    }
  }

  @Test
  fun parsingPerfetto_userSelectSpecificProcess() {
    val services = FakeIdeProfilerServices()
    services.setListBoxOptionsMatcher { option -> option.contains("system_server") }

    val parser = CpuCaptureParser(services)
    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")

    // Now set a process select callback to return a process
    val futureCapture = parser.parseForTest(traceFile)
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    val capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.threads).hasSize(17)
  }

  @Test
  fun parsingPerfettoWithProcessNameHintAutoSelectsProcess() {
    val services = FakeIdeProfilerServices()
    val parser = CpuCaptureParser(services)

    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    // Try to parse the file, assume the user canceled the dialog. If the dialog is shown.
    services.setListBoxOptionsIndex(-1) // This makes process selector throws if we didn't selected based on name hint first.
    val futureCapture = parser.parseForTest(traceFile, nameHint = "/system/bin/surfaceflinger")
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

    services.setListBoxOptionsIndex(0)
    val futureCapture = parser.parseForTest(traceFile, nameHint = "displayingbitmaps")
    assertThat(futureCapture.isCompletedExceptionally).isFalse()

    val capture = futureCapture.get()
    // capture.threads
    val mainNode = capture.getCaptureNode(capture.mainThreadId)!!
    assertThat(mainNode.data.name).isEqualTo("splayingbitmaps")
  }

  @Test
  fun parsingInvalidTraceProducesCompletedExceptionally() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())
    val futureCapture = parser.parseForTest(CpuProfilerTestUtils.getTraceFile("corrupted_trace.trace"))
    assertThat(futureCapture.isCompletedExceptionally).isTrue()

    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      // Do nothing. We expect the parsing to fail.
    }
  }

  @Test
  fun parsingDirectoriesCompletesExceptionally() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    val dir = resolveWorkspacePath("").toFile()
    assertThat(dir.exists()).isTrue()

    val futureCapture = parser.parseForTest(dir)
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
  }

  @Test
  fun parsingNonExistentFilesCompletesExceptionally() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    val nonExistentFile = File("")
    assertThat(nonExistentFile.exists()).isFalse()

    val futureCapture = parser.parseForTest(nonExistentFile)
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
  }

  @Test
  fun longFileShouldCompletesExceptionallyIfNotParsed() {
    val someFile = temporaryFolder.newFile("any_trace")
    someFile.writeBytes(ByteArray(CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1))

    val fakeServices = FakeIdeProfilerServices()
    // Decide not to parse long trace files
    fakeServices.setShouldProceedYesNoDialog(false)
    val parser = CpuCaptureParser(fakeServices)
    val future = parser.parseForTest(someFile)
    assertThat(future.isCompletedExceptionally).isTrue()
  }

  @Test
  fun longFileShouldProduceNotNullCompletableFutureIfNotParsed() {
    val someFile = temporaryFolder.newFile("any_trace")
    someFile.writeBytes(ByteArray(CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1))

    val fakeServices = FakeIdeProfilerServices()
    // Decide to parse long trace files
    fakeServices.setShouldProceedYesNoDialog(true)
    val parser = CpuCaptureParser(fakeServices)
    assertThat(parser.parseForTest(someFile)).isNotNull()
  }

  @Test
  fun validateImportMetricsReportedForImport() {
    val services = FakeIdeProfilerServices()
    val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
    val parser = CpuCaptureParser(services)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()

    parser.parseForTest(CpuProfilerTestUtils.getTraceFile("valid_trace.trace")).get()
    assertThat(fakeFeatureTracker.lastImportTraceStatus).isTrue()
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata).isNotNull()
  }

  @Test
  fun validateMetricsReportedOnceForCapture() {
    val services = FakeIdeProfilerServices()
    val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
    val parser = CpuCaptureParser(services)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata).isNull()

    parser.parseForTest(CpuProfilerTestUtils.getTraceFile("valid_trace.trace"), traceId = 100, idHint = 33).get()
    assertThat(fakeFeatureTracker.lastImportTraceStatus).isNull()
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata).isNotNull()

    // Validate 2nd time parsing does not trigger metrics.
    fakeFeatureTracker.resetLastCpuCaptureMetadata()
    parser.parseForTest(
      CpuProfilerTestUtils.getTraceFile("valid_trace.trace"), traceId = 100, idHint = 33).get()
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata).isNull()
  }

  @Test
  fun inputValidationExceptionIsPropagatedForExpectedTraceType() {
    val services = FakeIdeProfilerServices()
    val parser = CpuCaptureParser(services)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()

    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    val futureCapture = parser.parseForTest(traceFile, type = Trace.TraceType.PERFETTO)
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      assertThat(e).hasCauseThat().isInstanceOf(CpuCaptureParser.FileHeaderParsingFailureException::class.java)
      assertThat(e).hasCauseThat().hasMessageThat().contains(
        "Trace file '${traceFile.absolutePath}' expected to be of type PERFETTO but failed header verification.")

      assertThat(e).hasCauseThat().hasCauseThat().isInstanceOf(Throwable::class.java)
      assertThat(e).hasCauseThat().hasCauseThat().hasMessageThat().contains(
        "Encountered unknown tag (84) when attempting to parse perfetto capture.")
    }
    finally {
      val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
      assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.status).isEqualTo(
        CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_FILE_HEADER_ERROR)
    }
  }

  @Test
  fun internalExceptionOnTraceProcessorIsPropagated_forExpectedTraceType() {
    val services = FakeIdeProfilerServices()
    val fakeTraceProcessorService = services.traceProcessorService as FakeTraceProcessorService
    fakeTraceProcessorService.forceFailLoadTrace = true

    val parser = CpuCaptureParser(services)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()

    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    val futureCapture = parser.parseForTest(traceFile, type = Trace.TraceType.PERFETTO)
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      assertThat(e).hasCauseThat().isInstanceOf(CpuCaptureParser.ParsingFailureException::class.java)
      assertThat(e).hasCauseThat().hasMessageThat().contains("Trace file '${traceFile.absolutePath}' failed to be parsed as PERFETTO")

      assertThat(e).hasCauseThat().hasCauseThat().isInstanceOf(RuntimeException::class.java)
      assertThat(e).hasCauseThat().hasCauseThat().hasMessageThat().contains("Unable to load trace with TPD.")
    }
    finally {
      val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
      assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.status).isEqualTo(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_ERROR)
    }
  }

  @Test
  fun internalExceptionOnTraceProcessorIsPropagated_forUnknownTraceType() {
    val services = FakeIdeProfilerServices()
    val fakeTraceProcessorService = services.traceProcessorService as FakeTraceProcessorService
    fakeTraceProcessorService.forceFailLoadTrace = true

    val parser = CpuCaptureParser(services)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()

    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    val futureCapture = parser.parseForTest(traceFile)
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      assertThat(e).hasCauseThat().isInstanceOf(CpuCaptureParser.ParsingFailureException::class.java)
      assertThat(e).hasCauseThat().hasMessageThat().contains("Trace file '${traceFile.absolutePath}' failed to be parsed as PERFETTO")

      assertThat(e).hasCauseThat().hasCauseThat().isInstanceOf(RuntimeException::class.java)
      assertThat(e).hasCauseThat().hasCauseThat().hasMessageThat().contains("Unable to load trace with TPD.")
    }
    finally {
      val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
      assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.status).isEqualTo(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_ERROR)
    }
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

  private fun CpuCaptureParser.parseForTestWithArt(traceFile: File,
                                                   traceId: Long = ANY_TRACE_ID,
                                                   idHint: Int = 0,
                                                   nameHint: String = ""): CompletableFuture<CpuCapture> {
    return this.parseForTest(traceFile, traceId, Trace.TraceType.ART, idHint, nameHint)
  }

  private fun CpuCaptureParser.parseForTestWithSimplePerf(traceFile: File,
                                                          traceId: Long = ANY_TRACE_ID,
                                                          idHint: Int = 0,
                                                          nameHint: String = ""): CompletableFuture<CpuCapture> {
    return this.parseForTest(traceFile, traceId, Trace.TraceType.SIMPLEPERF, idHint, nameHint)
  }

  private fun CpuCaptureParser.parseForTest(traceFile: File,
                                            traceId: Long = ANY_TRACE_ID,
                                            type: Trace.TraceType = Trace.TraceType.UNSPECIFIED_TYPE,
                                            idHint: Int = 0,
                                            nameHint: String = ""): CompletableFuture<CpuCapture> {
    return this.parse(traceFile, traceId, type, idHint, nameHint)
  }
}
