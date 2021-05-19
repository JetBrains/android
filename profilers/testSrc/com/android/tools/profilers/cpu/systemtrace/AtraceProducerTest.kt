/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.FileInputStream
import java.io.InputStreamReader

class AtraceProducerTest {

  private lateinit var myProducer: AtraceProducer
  @Before
  fun setup() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    myProducer = AtraceProducer()
    assertThat(myProducer.parseFile(traceFile)).isTrue()
  }

  @Test
  fun testNoDataSliceLongerThan1023Characters() {
    val rawTraceData = CpuProfilerTestUtils.getTraceFile("../long_line_trace_truncated.txt")
    val contents = InputStreamReader(FileInputStream(rawTraceData))
    val traceFile = CpuProfilerTestUtils.getTraceFile("long_line.ctrace")
    myProducer = AtraceProducer()
    assertThat(myProducer.parseFile(traceFile)).isTrue()
    // "# Initial Data Required by Importer\n"
    var slice = myProducer.next()
    val lines = contents.readLines()
    var i = 0
    do {
      slice = myProducer.next()!!
      assertThat(slice.toString().trim()).isEqualTo(lines[i++].trim())
      // Verify that all string lengths are less than or equal to 1024 characters
      // 1023 characters are expected + 1 for the \n. This is needed to prevent a bug in trebuchet
      // see (b/77846431)
      assertThat(slice.endIndex - slice.startIndex).isLessThan(1023 + 1)
    }
    while (i != lines.size)
  }

  @Test
  fun testDecompressedLineHasNewLineChar() {
    val slice = myProducer.next()
    assertThat(slice.toString()).endsWith("\n")
  }

  @Test
  fun testCompressBounderyProperlySetsNewLine() {
    for (line in myProducer.lines) {
      val tracerIndex = line.indexOf("# tracer: nop")
      assertThat(tracerIndex == -1 || tracerIndex == 0).isTrue()
    }
  }

  @Test
  fun testDontLoseLastCompleteLineInDecompression() {
    var knownTimestampOccurences = 0
    for (line in myProducer.lines) {
      if (line.indexOf(KNOWN_TIMESTAMP) >= 0) {
        knownTimestampOccurences++
      }
    }
    assertThat(knownTimestampOccurences).isEqualTo(1)
  }

  @Test
  fun testEndOfFileReturnsNull() {
    do {
      // Read each line until we hit the end of stream.
      val line = myProducer.nextLine
    }
    while (line != null)
    // Validate that next returns null to indicate end of stream.
    assertThat(myProducer.next()).isNull()
  }

  @Test
  fun testCaptureLoadsWhenDataFitsExactBufferBounds() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("exact_size_atrace.ctrace")
    val producer = AtraceProducer()
    assertThat(producer.parseFile(traceFile)).isTrue()
    do {
      // Read each line until we hit the end of stream.
      val line = producer.nextLine
    }
    while (line != null)
  }

  // Adding a kotlin property fopr AtraceProducer to assist with iterating lines.
  val AtraceProducer.lines: Iterator<String>
    get() = object : Iterator<String> {
      var line = this@lines.nextLine
      override fun next(): String {
        if (line == null)
          throw NoSuchElementException()
        val result = line!!
        line = this@lines.nextLine
        return result
      }

      override fun hasNext() = line != null
    }

  companion object {
    // Setting const for atrace file in one location so if we update file we can update const in one location.

    // Timestamp is significant as it test that leftover lines properly end with \n and are parsed. When updating the atrace file
    // the timestamp that precedes a buffer ending with \n should be selected.
    private val KNOWN_TIMESTAMP = "87688.590600"
  }
}
