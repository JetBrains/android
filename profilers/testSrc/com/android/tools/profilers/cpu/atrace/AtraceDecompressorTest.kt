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
package com.android.tools.profilers.cpu.atrace

import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test

import org.junit.Before

class AtraceDecompressorTest {

  private lateinit var myDecompressor: AtraceDecompressor
  @Before
  fun setup() {
    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    myDecompressor = AtraceDecompressor(traceFile)
  }

  @Test
  fun testDecompressedLineHasNewLineChar() {
    var slice = myDecompressor.next()
    assertThat(slice.toString()).endsWith("\n");
  }

  @Test
  fun testCompressBounderyProperlySetsNewLine() {
    for (line in myDecompressor.lines) {
      val tracerIndex = line.indexOf("# tracer: nop")
      assertThat(tracerIndex == -1 || tracerIndex == 0).isTrue()
    }
  }

  @Test
  fun testDontLoseLastCompleteLineInDecompression() {
    var knownTimestampOccurences = 0
    for (line in myDecompressor.lines) {
      if (line.indexOf(KNOWN_TIMESTAMP) >= 0) {
        knownTimestampOccurences++;
      }
    }
    assertThat(knownTimestampOccurences).isEqualTo(1)
  }

  @Test
  fun testEndOfFileReturnsNull() {
    do {
      // Read each line until we hit the end of stream.
      var line = myDecompressor.nextLine
    }
    while (line != null)
    // Validate that next returns null to indicate end of stream.
    assertThat(myDecompressor.next()).isNull()
  }

  // Adding a kotlin property fopr AtraceDecompressor to assist with iterating lines.
  val AtraceDecompressor.lines: Iterator<String>
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
