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
import org.junit.Assert.assertNull
import org.junit.Test

import org.junit.Assert.assertTrue
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
    assertTrue(slice.toString().endsWith("\n"))
  }

  @Test
  fun testEndOfFileReturnsNull() {
    do {
      // Read each line until we hit the end of stream.
      var line = myDecompressor.nextLine
    }
    while (line != null)
    // Validate that next returns null to indicate end of stream.
    assertNull(myDecompressor.next())
  }
}
