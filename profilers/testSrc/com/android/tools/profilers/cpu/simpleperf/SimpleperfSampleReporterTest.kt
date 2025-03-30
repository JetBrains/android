/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.simpleperf

import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.TracePreProcessor
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.intellij.openapi.util.io.FileUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.File
import java.io.FileOutputStream

class SimpleperfSampleReporterTest {

  @get:Rule
  val expected = ExpectedException.none()!!

  private lateinit var sampleReporter: SimpleperfSampleReporter

  @Before
  fun setUp() {
    sampleReporter = SimpleperfSampleReporter(DeviceInfo.getDefaultInstance())
  }

  @Test
  fun simpleperfBinaryShouldExist() {
    assertThat(File(sampleReporter.simpleperfBinaryPath).exists()).isTrue()
  }

  @Test
  fun preProcessingInvalidTraceReturnsFailure() {
    val processedTrace = sampleReporter.preProcessTrace(ByteString.copyFromUtf8("bad trace"), emptyList())
    assertThat(processedTrace).isEqualTo(TracePreProcessor.FAILURE)

    val trace = FileUtil.createTempFile("cpu_trace", ".trace", true)
    FileOutputStream(trace).use { out -> out.write(processedTrace.toByteArray()) }

    // Trace can't be parsed by SimpleperfTraceParser. Expect an exception
    expected.expect(Exception::class.java)
    SimpleperfTraceParser().parse(trace, 0)
  }

  @Test
  fun preProcessingRawTraceReturnsValidTrace() {
    val processedTrace = sampleReporter.preProcessTrace(
      CpuProfilerTestUtils.traceFileToByteString("simpleperf_trace_without_symbols.trace"),
      emptyList())
    assertThat(processedTrace).isNotEqualTo(TracePreProcessor.FAILURE)

    val trace = FileUtil.createTempFile("cpu_trace", ".trace", true)
    FileOutputStream(trace).use { out -> out.write(processedTrace.toByteArray()) }

    // Trace can be parsed by SimpleperfTraceParser
    val parsedTrace = SimpleperfTraceParser().parse(trace, 0)
    assertThat(parsedTrace).isNotNull()
  }

  private fun unknownRawSymbolsSymbolizedOnlyIfValidSymDirProvided(symDir: List<String>, expectedName: String) {
    val rawTrace = CpuProfilerTestUtils.traceFileToByteString("simpleperf_trace_without_symbols.trace")

    // When providing a path to SimpleperfSampleReporter, we should include the --symdir flag in the report-sample command.
    // That happens even if the path does not contain valid .so files.
    val command = sampleReporter.getReportSampleCommand(rawTrace, FileUtil.createTempFile("any", "file", true), symDir)
    if (symDir.isEmpty()) {
      assertThat(command).doesNotContain("--symdir")
    } else {
      assertThat(command).contains("--symdir")
    }

    val trace = FileUtil.createTempFile("cpu_trace", ".trace", true)
    FileOutputStream(trace).use { out -> out.write(sampleReporter.preProcessTrace(rawTrace, symDir).toByteArray()) }
    val parsedTrace = SimpleperfTraceParser().parse(trace, 0)

    // Find the target node in a given thread, at a particular location in the tree
    var target = parsedTrace.getCaptureNode(15496)!!
    for (i in intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0)) {
      target = target.children[i]
    }
    assertThat(target!!.data.name).isEqualTo(expectedName)
  }

  @Test
  fun unknownSymbolsSymbolizedWhenProvidingSymDir() {
    val symDir = listOf(resolveWorkspacePath("tools/adt/idea/profilers/testData").toString())
    // The unknown symbol should be properly symbolized
    unknownRawSymbolsSymbolizedOnlyIfValidSymDirProvided(symDir, "DoExpensiveFpuCalculation")
  }

  @Test
  fun unknownSymbolsNotSymbolizedWhenSymDirNotProvided() {
    // The unknown symbol should not be properly symbolized, if no paths to symbols are provided.
    unknownRawSymbolsSymbolizedOnlyIfValidSymDirProvided(emptyList(), "libnative_cpu.so+0xdb18")
  }

  @Test
  fun unknownSymbolsNotSymbolizedWhenProvidingInvalidSymDir() {
    val symDir = listOf(
      resolveWorkspacePath("tools/adt/idea/profilers/testData/cputraces").toString()) // Dir without valid .so files
    // The unknown symbol should NO be properly symbolized
    unknownRawSymbolsSymbolizedOnlyIfValidSymDirProvided(symDir, "libnative_cpu.so+0xdb18")
  }

  @Test
  fun providingMultipleSymDirsResultsInMultipleFlags() {
    val symDir1 = resolveWorkspacePath("tools/adt/idea/profilers/testData/cputraces").toString()
    val symDir2 = resolveWorkspacePath("tools/adt/idea/profilers/testData").toString()
    // Passing a linked set is important for this test so we have predictable iteration order and can make the index checks below
    val rawTrace = CpuProfilerTestUtils.traceFileToByteString("simpleperf_trace_without_symbols.trace")

    // When providing multiples path to SimpleperfSampleReporter, we should include a --symdir flag in the report-sample command
    // corresponding to each directory passed.
    val command = sampleReporter.getReportSampleCommand(rawTrace, FileUtil.createTempFile("any", "file", true),
                                                        listOf(symDir1, symDir2))
    assertThat(command.count { it == "--symdir" }).isEqualTo(2)

    val firstSymDirIndex = command.indexOfFirst { it == "--symdir" }
    assertThat(command[firstSymDirIndex + 1]).isEqualTo(symDir1)

    val secondSymDirIndex = command.indexOfLast { it == "--symdir" }
    assertThat(command[secondSymDirIndex + 1]).isEqualTo(symDir2)
  }
}