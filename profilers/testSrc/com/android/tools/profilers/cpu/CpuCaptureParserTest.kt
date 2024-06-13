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

import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeTraceProcessorService
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuCaptureParser.FileHeaderParsingFailureException
import com.android.tools.profilers.cpu.CpuCaptureParser.ProcessTraceAction
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.CpuImportTraceMetadata.ImportStatus
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.function.Predicate
import java.util.function.Supplier

class CpuCaptureParserTest {

  private val ANY_TRACE_ID = 3039L
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private lateinit var myProfilers: StudioProfilers

  @JvmField
  @Rule
  val temporaryFolder = TemporaryFolder()

  @get:Rule
  val grpcChannel = FakeGrpcChannel("CpuCaptureParserTest", transportService)


  class SampleParserWithoutException(private val resultCapture: CpuCapture) : TraceParser {
    @Throws(IOException::class)
    override fun parse(trace: File, traceId: Long): CpuCapture {
      return resultCapture;
    }
  }

  class SampleParserWithException : TraceParser {
    @Throws(IOException::class)
    override fun parse(trace: File, traceId: Long): CpuCapture {
      throw java.lang.RuntimeException("Unit Test Parse failure $traceId");
    }
  }

  private fun spyInitializeProcessTraceAction(filename: String, traceType: TraceType): ProcessTraceAction {
    val traceFile = CpuProfilerTestUtils.getTraceFile(filename)
    val traceId = 1000
    val processId = 100
    val processNameHint = "processNameHint"
    val mockIdeProfilerServices = MockitoKt.mock<IdeProfilerServices>()
    return Mockito.spy(
      ProcessTraceAction(traceFile, traceId.toLong(), traceType, processId, processNameHint, mockIdeProfilerServices))
  }

  @Before
  fun setUp() {
    val ideServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideServices, timer)
  }

  @Test
  fun artStreamingKnownTypeParseToCaptureTest() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("art_streaming.trace", TraceType.ART)
    val resultCapture = MockitoKt.mock<CpuCapture>()
    val supplierParseWithoutError = Supplier<TraceParser> { SampleParserWithoutException(resultCapture) }

    doReturn(supplierParseWithoutError).whenever(processTraceAction).getParserSupplier(TraceType.ART)
    val result = processTraceAction.apply(MockitoKt.mock<Void>())
    // File successfully parsed
    assertThat(result).isEqualTo(resultCapture)
  }

  @Test(expected = FileHeaderParsingFailureException::class)
  fun artKnownTypeVerificationError() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("simpleperf.trace", TraceType.ART)
    // Trace verification fails
    processTraceAction.apply(MockitoKt.mock<Void>())
  }

  @Test
  fun artNonStreamingKnownTypeParseToCaptureTest() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("art_non_streaming.trace", TraceType.ART)
    val resultCapture = MockitoKt.mock<CpuCapture>()
    val supplierParseWithoutError = Supplier<TraceParser> { SampleParserWithoutException(resultCapture) }

    doReturn(supplierParseWithoutError).whenever(processTraceAction).getParserSupplier(TraceType.ART)
    val result = processTraceAction.apply(MockitoKt.mock<Void>())
    // File successfully parsed
    assertThat(result).isEqualTo(resultCapture)
  }

  @Test(expected = CpuCaptureParser.ParsingFailureException::class)
  fun artKnownTypeParseError() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("art_streaming.trace", TraceType.ART)
    val supplierParseWithError = Supplier<TraceParser> { SampleParserWithException() }

    // Error while parsing the trace
    doReturn(supplierParseWithError).whenever(processTraceAction).getParserSupplier(TraceType.ART)
    processTraceAction.apply(MockitoKt.mock<Void>())
  }

  @Test
  fun simpleperfKnownTypeParseToCaptureTest() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("simpleperf.trace", TraceType.SIMPLEPERF)
    val resultCapture = MockitoKt.mock<CpuCapture>()
    val supplierParseWithoutError = Supplier<TraceParser> { SampleParserWithoutException(resultCapture) }

    doReturn(supplierParseWithoutError).whenever(processTraceAction).getParserSupplier(TraceType.SIMPLEPERF)
    val result = processTraceAction.apply(MockitoKt.mock<Void>())
    // File successfully parsed
    assertThat(result).isEqualTo(resultCapture)
  }

  @Test(expected = FileHeaderParsingFailureException::class)
  fun simpleprefKnownTypeVerificationError() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("art_streaming.trace", TraceType.SIMPLEPERF)
    // Fail trace verification
    processTraceAction.apply(MockitoKt.mock<Void>())
  }

  @Test(expected = CpuCaptureParser.ParsingFailureException::class)
  fun simpleprefKnownTypeParseError() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("simpleperf.trace", TraceType.SIMPLEPERF)
    val supplierParseWithError = Supplier<TraceParser> { SampleParserWithException() }

    // Error while parsing the trace
    doReturn(supplierParseWithError).whenever(processTraceAction).getParserSupplier(TraceType.SIMPLEPERF)
    processTraceAction.apply(MockitoKt.mock<Void>())
  }

  @Test
  fun atraceKnownTypeParseToCaptureTest() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("atrace.ctrace",TraceType.ATRACE)
    val resultCapture = MockitoKt.mock<CpuCapture>()
    val supplierParseWithoutError = Supplier<TraceParser> { SampleParserWithoutException(resultCapture) }

    doReturn(supplierParseWithoutError).whenever(processTraceAction).getParserSupplier(TraceType.ATRACE)
    val result = processTraceAction.apply(MockitoKt.mock<Void>())
    // File successfully parsed
    assertThat(result).isEqualTo(resultCapture)
  }

  @Test(expected = FileHeaderParsingFailureException::class)
  fun atraceKnownTypeVerificationError() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("simpleperf.trace", TraceType.ATRACE)
    // Fail trace verification
    processTraceAction.apply(MockitoKt.mock<Void>())
  }

  @Test(expected = CpuCaptureParser.ParsingFailureException::class)
  fun atraceKnownTypeParseError() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("atrace.ctrace", TraceType.ATRACE)
    val supplierParseWithError = Supplier<TraceParser> { SampleParserWithException() }

    // Error while parsing the trace
    doReturn(supplierParseWithError).whenever(processTraceAction).getParserSupplier(TraceType.ATRACE)
    processTraceAction.apply(MockitoKt.mock<Void>())
  }

  @Test
  fun perfettoKnownTypeParseToCaptureTest() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("perfetto.trace", TraceType.PERFETTO)
    val resultCapture = MockitoKt.mock<CpuCapture>()
    val supplierParseWithoutError = Supplier<TraceParser> { SampleParserWithoutException(resultCapture) }
    doReturn(supplierParseWithoutError).whenever(processTraceAction).getParserSupplier(TraceType.PERFETTO)
    val result = processTraceAction.apply(MockitoKt.mock<Void>())
    // File successfully parsed
    assertThat(result).isEqualTo(resultCapture)
  }

  @Test(expected = FileHeaderParsingFailureException::class)
  fun perfettoKnownTypeVerificationError() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("atrace.ctrace", TraceType.PERFETTO)
    // Fail trace verification
    processTraceAction.apply(MockitoKt.mock<Void>())
  }

  @Test(expected = CpuCaptureParser.ParsingFailureException::class)
  fun perfettoKnownTypeParseError() {
    // Known trace type
    val processTraceAction = spyInitializeProcessTraceAction("perfetto.trace", TraceType.PERFETTO)
    val supplierParseWithError = Supplier<TraceParser> { SampleParserWithException() }
    // Error while parsing the trace
    doReturn(supplierParseWithError).whenever(processTraceAction).getParserSupplier(TraceType.PERFETTO)
    processTraceAction.apply(MockitoKt.mock<Void>())
  }

  @Test
  fun artUnknownTypeParseToCapture() {
    // Unknown trace type
    val processTraceAction = spyInitializeProcessTraceAction("art_streaming.trace", TraceType.UNSPECIFIED)
    val resultCapture = MockitoKt.mock<CpuCapture>()
    val supplierParseWithoutError = Supplier<TraceParser> { SampleParserWithoutException(resultCapture) }

    doReturn(supplierParseWithoutError).whenever(processTraceAction).getParserSupplier(TraceType.ART)
    val result = processTraceAction.apply(MockitoKt.mock<Void>())
    // File successfully parsed
    assertThat(result).isEqualTo(resultCapture)
  }

  @Test
  fun simpleperfUnknownTypeParseToCapture() {
    // Unknown trace type
    val processTraceAction = spyInitializeProcessTraceAction("simpleperf.trace", TraceType.UNSPECIFIED)
    val resultCapture = MockitoKt.mock<CpuCapture>()
    val supplierParseWithoutError = Supplier<TraceParser> { SampleParserWithoutException(resultCapture) }

    doReturn(supplierParseWithoutError).whenever(processTraceAction).getParserSupplier(TraceType.SIMPLEPERF)
    val result = processTraceAction.apply(MockitoKt.mock<Void>())
    // File successfully parsed
    assertThat(result).isEqualTo(resultCapture)
  }

  @Test
  fun atraceUnknownTypeParseToCapture() {
    // Unknown trace type
    val processTraceAction = spyInitializeProcessTraceAction("atrace.ctrace",TraceType.UNSPECIFIED)
    val resultCapture = MockitoKt.mock<CpuCapture>();
    val supplierParseWithoutError = Supplier<TraceParser> { SampleParserWithoutException(resultCapture) }

    doReturn(supplierParseWithoutError).whenever(processTraceAction).getParserSupplier(TraceType.ATRACE)
    val result = processTraceAction.apply(MockitoKt.mock<Void>())
    // File successfully parsed
    assertThat(result).isEqualTo(resultCapture)
  }

  @Test
  fun perfettoUnknownTypeParseToCapture() {
    // Unknown trace type
    val processTraceAction = spyInitializeProcessTraceAction("perfetto.trace", TraceType.UNSPECIFIED)
    val resultCapture = MockitoKt.mock<CpuCapture>()
    val supplierParseWithoutError = Supplier<TraceParser> { SampleParserWithoutException(resultCapture) }

    doReturn(supplierParseWithoutError).whenever(processTraceAction).getParserSupplier(TraceType.PERFETTO)
    val result = processTraceAction.apply(MockitoKt.mock<Void>())
    // File successfully parsed
    assertThat(result).isEqualTo(resultCapture)
  }

  @Test(expected = CpuCaptureParser.UnknownParserParsingFailureException::class)
  fun unknownTypeParseToCaptureNoneTypeMatches() {
    // Unknown trace type
    val processTraceAction = spyInitializeProcessTraceAction("simpleperf_trace_without_symbols.trace", TraceType.UNSPECIFIED)
    // Trace verification fails
    processTraceAction.apply(MockitoKt.mock<Void>())
  }

  @Test
  fun parsingAValidTraceShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(myProfilers)
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

    val fakeServices = myProfilers.ideServices as FakeIdeProfilerServices
    // Decide not to parse long trace files
    fakeServices.setShouldProceedYesNoDialog(false)
    val parser = CpuCaptureParser(myProfilers)

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

    val fakeServices = myProfilers.ideServices as FakeIdeProfilerServices
    // Decide to parse long trace files
    fakeServices.setShouldProceedYesNoDialog(true)
    val parser = CpuCaptureParser(myProfilers)
    val futureCapture = parser.parseForTest(largeTraceFile)
    assertThat(futureCapture).isNotNull()
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
  }

  @Test
  fun corruptedTraceFileCompletesExceptionally() {
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val parser = CpuCaptureParser(myProfilers)
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
    val parser = CpuCaptureParser(myProfilers)
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
      val fakeFeatureTracker = myProfilers.ideServices.featureTracker as FakeFeatureTracker
      assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.status).isEqualTo(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PATH_INVALID)
    }
  }

  @Test
  fun parsingShouldHappenOnlyOnce() {
    val parser = CpuCaptureParser(myProfilers)

    val traceFile = CpuProfilerTestUtils.getTraceFile("valid_trace.trace")
    val firstParsedCapture = parser.parseForTestWithArt(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)
    val secondParsedCapture = parser.parseForTestWithArt(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)

    // Second time we call parse(...) we just return the capture parsed the first time.
    assertThat(secondParsedCapture).isEqualTo(firstParsedCapture)
  }

  @Test
  fun parsingAValidSimpleperfTraceShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(myProfilers)

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
    val parser = CpuCaptureParser(myProfilers)

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
    val parser = CpuCaptureParser(myProfilers)

    val traceFile = CpuProfilerTestUtils.getTraceFile("simpleperf.trace")
    val futureCapture = parser.parseForTest(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)

    // Parsing should create a valid CpuCapture object
    val capture = futureCapture.get()
    checkValidCapture(capture)
    assertThat(capture.type).isEqualTo(TraceType.SIMPLEPERF)

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun traceTypeInferredFromMissingType_Atrace() {
    val parser = CpuCaptureParser(myProfilers)

    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    val futureCapture = parser.parseForTest(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)

    // Parsing should create a valid CpuCapture object
    val capture = futureCapture.get()
    checkValidCapture(capture)
    assertThat(capture.type).isEqualTo(TraceType.ATRACE)

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun traceTypeInferredFromMissingType_Perfetto() {
    val parser = CpuCaptureParser(myProfilers)

    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    val futureCapture = parser.parseForTest(traceFile, idHint = ProfilersTestData.SESSION_DATA.pid)

    // Parsing should create a valid CpuCapture object
    val capture = futureCapture.get()
    checkValidCapture(capture)
    assertThat(capture.type).isEqualTo(TraceType.PERFETTO)

    // getCapture(traceId) should return the same object created by calling parse.
    assertThat(parser.getCapture(ANY_TRACE_ID)).isEqualTo(futureCapture)
  }

  @Test
  fun parsingArtFilesShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(myProfilers)
    val futureCapture = parser.parseForTest(CpuProfilerTestUtils.getTraceFile("valid_trace.trace"), 234)

    val capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.traceId).isEqualTo(234)
    // ART capture should be dual clock
    assertThat(capture.isDualClock).isTrue()
    assertThat(capture.type).isEqualTo(TraceType.ART)
  }

  @Test
  fun parsingSimpleperfFilesShouldProduceCpuCapture() {
    val parser = CpuCaptureParser(myProfilers)
    val futureCapture = parser.parseForTest(
      CpuProfilerTestUtils.getTraceFile("simpleperf.trace"), 123)

    val capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.traceId).isEqualTo(123)
    // Simpleperf capture should not be dual clock
    assertThat(capture.isDualClock).isFalse()
    assertThat(capture.type).isEqualTo(TraceType.SIMPLEPERF)
  }

  @Test
  fun parsingAtrace_userCancelDialog() {
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    services.setListBoxOptionsIndex(-1) // Parse the capture, assume the user canceled the dialog.
    val parser = CpuCaptureParser(myProfilers)
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
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    services.setListBoxOptionsIndex(0)
    val parser = CpuCaptureParser(myProfilers)
    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace")

    val futureCapture = parser.parseForTest(traceFile)
    assertThat(futureCapture.isCompletedExceptionally).isFalse()
    val capture = futureCapture.get()
    assertThat(capture).isNotNull()
    assertThat(capture.threads).hasSize(1)
  }

  @Test
  fun parsingPerfetto_userCancelDialog() {
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    services.setListBoxOptionsIndex(-1) // Assume the user canceled the dialog.
    val parser = CpuCaptureParser(myProfilers)
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
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    services.setListBoxOptionsMatcher { option -> option.contains("system_server") }

    val parser = CpuCaptureParser(myProfilers)
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
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val parser = CpuCaptureParser(myProfilers)

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
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val parser = CpuCaptureParser(myProfilers)
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
    val parser = CpuCaptureParser(myProfilers)
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
    val parser = CpuCaptureParser(myProfilers)

    val dir = resolveWorkspacePath("").toFile()
    assertThat(dir.exists()).isTrue()

    val futureCapture = parser.parseForTest(dir)
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
  }

  @Test
  fun parsingNonExistentFilesCompletesExceptionally() {
    val parser = CpuCaptureParser(myProfilers)

    val nonExistentFile = File("")
    assertThat(nonExistentFile.exists()).isFalse()

    val futureCapture = parser.parseForTest(nonExistentFile)
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
  }

  @Test
  fun longFileShouldCompletesExceptionallyIfNotParsed() {
    val someFile = temporaryFolder.newFile("any_trace")
    someFile.writeBytes(ByteArray(CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1))

    val fakeServices = myProfilers.ideServices as FakeIdeProfilerServices
    // Decide not to parse long trace files
    fakeServices.setShouldProceedYesNoDialog(false)
    val parser = CpuCaptureParser(myProfilers)
    val future = parser.parseForTest(someFile)
    assertThat(future.isCompletedExceptionally).isTrue()
  }

  @Test
  fun longFileShouldProduceNotNullCompletableFutureIfNotParsed() {
    val someFile = temporaryFolder.newFile("any_trace")
    someFile.writeBytes(ByteArray(CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1))

    val fakeServices = myProfilers.ideServices as FakeIdeProfilerServices
    // Decide to parse long trace files
    fakeServices.setShouldProceedYesNoDialog(true)
    val parser = CpuCaptureParser(myProfilers)
    assertThat(parser.parseForTest(someFile)).isNotNull()
  }

  @Test
  fun validateImportMetricsReportedForImport() {
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
    val parser = CpuCaptureParser(myProfilers)
    services.enableTaskBasedUx(false)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()

    parser.parseForTest(CpuProfilerTestUtils.getTraceFile("valid_trace.trace")).get()
    assertThat(fakeFeatureTracker.lastCpuImportTraceMetadata.importStatus).isEqualTo(ImportStatus.IMPORT_TRACE_SUCCESS)
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata).isNotNull()
  }

  @Test
  fun validateMetricsReportedForComposeTracingImport() {
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
    val parser = CpuCaptureParser(myProfilers)
    services.enableTaskBasedUx(false)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()

    parser.parseForTest(
      CpuProfilerTestUtils.getTraceFile("perfetto_cpu_compose.trace"),
      idHint = 0, // note: idHint == 0 signifies importing an existing trace from a file (i.e. not a fresh trace capture)
      nameHint = "com.google.samples.apps.nowinandroid.demo.debug",
      type = TraceType.PERFETTO
    ).get()
    assertThat(fakeFeatureTracker.lastCpuImportTraceMetadata.importStatus).isEqualTo(ImportStatus.IMPORT_TRACE_SUCCESS)
    assertThat(fakeFeatureTracker.lastCpuImportTraceMetadata.hasComposeTracingNodes).isTrue()
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.hasComposeTracingNodes).isTrue()
  }

  @Suppress("UsePropertyAccessSyntax")
  @Test
  fun validateMetricsReportedForNonComposeTracingImport() {
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
    val parser = CpuCaptureParser(myProfilers)
    services.enableTaskBasedUx(false)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()

    parser.parseForTest(
      CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage.trace"),
      idHint = 0, // note: idHint == 0 signifies importing an existing trace from a file (i.e. not a fresh trace capture)
      type = TraceType.PERFETTO
    ).get()
    assertThat(fakeFeatureTracker.lastCpuImportTraceMetadata.importStatus).isEqualTo(ImportStatus.IMPORT_TRACE_SUCCESS)
    assertThat(fakeFeatureTracker.lastCpuImportTraceMetadata.hasHasComposeTracingNodes()).isTrue() // check if Proto field is populated
    assertThat(fakeFeatureTracker.lastCpuImportTraceMetadata.getHasComposeTracingNodes()).isFalse() // check the value of the field
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.hasComposeTracingNodes).isFalse() // POJO class, so no need to check if populated
  }

  @Test
  fun validateMetricsReportedForComposeTracingCapture() {
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
    val parser = CpuCaptureParser(myProfilers)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()

    parser.parseForTest(
      CpuProfilerTestUtils.getTraceFile("perfetto_cpu_compose.trace"),
      idHint = 20728, // note: idHint != 0 signifies capturing a fresh trace (i.e. not importing an existing one from file)
      nameHint = "com.google.samples.apps.nowinandroid.demo.debug",
      type = TraceType.PERFETTO
    ).get()
    assertThat(fakeFeatureTracker.lastCpuImportTraceMetadata).isEqualTo(null)
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.hasComposeTracingNodes).isTrue()
  }

  @Test
  fun validateMetricsReportedOnceForCapture() {
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
    val parser = CpuCaptureParser(myProfilers)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata).isNull()

    parser.parseForTest(CpuProfilerTestUtils.getTraceFile("valid_trace.trace"), traceId = 100, idHint = 33).get()
    assertThat(fakeFeatureTracker.lastCpuImportTraceMetadata).isEqualTo(null)
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata).isNotNull()

    // Validate 2nd time parsing does not trigger metrics.
    fakeFeatureTracker.resetLastCpuCaptureMetadata()
    parser.parseForTest(
      CpuProfilerTestUtils.getTraceFile("valid_trace.trace"), traceId = 100, idHint = 33).get()
    assertThat(fakeFeatureTracker.lastCpuCaptureMetadata).isNull()
  }

  @Test
  fun inputValidationExceptionIsPropagatedForExpectedTraceType() {
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val parser = CpuCaptureParser(myProfilers)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()

    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    val futureCapture = parser.parseForTest(traceFile, type = TraceType.PERFETTO)
    assertThat(futureCapture.isCompletedExceptionally).isTrue()
    try {
      futureCapture.get()
      fail()
    }
    catch (e: ExecutionException) {
      assertThat(e).hasCauseThat().isInstanceOf(FileHeaderParsingFailureException::class.java)
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
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val fakeTraceProcessorService = services.traceProcessorService as FakeTraceProcessorService
    fakeTraceProcessorService.forceFailLoadTrace = true

    val parser = CpuCaptureParser(myProfilers)
    CpuCaptureParser.clearPreviouslyLoadedCaptures()

    val traceFile = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    val futureCapture = parser.parseForTest(traceFile, type = TraceType.PERFETTO)
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
    val services = myProfilers.ideServices as FakeIdeProfilerServices
    val fakeTraceProcessorService = services.traceProcessorService as FakeTraceProcessorService
    fakeTraceProcessorService.forceFailLoadTrace = true

    val parser = CpuCaptureParser(myProfilers)
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
    return this.parseForTest(traceFile, traceId, TraceType.ART, idHint, nameHint)
  }

  private fun CpuCaptureParser.parseForTestWithSimplePerf(traceFile: File,
                                                          traceId: Long = ANY_TRACE_ID,
                                                          idHint: Int = 0,
                                                          nameHint: String = ""): CompletableFuture<CpuCapture> {
    return this.parseForTest(traceFile, traceId, TraceType.SIMPLEPERF, idHint, nameHint)
  }

  private fun CpuCaptureParser.parseForTest(traceFile: File,
                                            traceId: Long = ANY_TRACE_ID,
                                            type: TraceType = TraceType.UNSPECIFIED,
                                            idHint: Int = 0,
                                            nameHint: String = ""): CompletableFuture<CpuCapture> {
    return this.parse(traceFile, traceId, type, idHint, nameHint) {}
  }
}
