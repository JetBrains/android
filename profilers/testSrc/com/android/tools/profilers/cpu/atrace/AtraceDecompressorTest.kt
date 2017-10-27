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
import com.intellij.openapi.util.io.FileUtil
import org.junit.Test

import java.io.BufferedWriter
import java.io.FileWriter

import org.junit.Assert.assertTrue

class AtraceDecompressorTest {

  @Test
  fun testDecompressFileHasParentTimestamp() {
    // TODO: When we have a model for an atrace file, update this to use the model. For now
    // this test only looks for the parent_ts from the atrace file. At the same time, it
    // parses the whole file to validate that decompressing the entire file works.
    val traceFile = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    val tmpFile = FileUtil.createTempFile("trace", "txt")
    val bufferedWriter = BufferedWriter(FileWriter(tmpFile))
    val decompressor = AtraceDecompressor(traceFile)
    var line = decompressor.nextLine
    var hasParentTimestamp = false
    while (line != null) {
      bufferedWriter.write(line + "\n")
      if (line.contains("parent_ts=")) {
        hasParentTimestamp = true
      }
      line = decompressor.nextLine
    }
    bufferedWriter.close()
    assertTrue(hasParentTimestamp)
  }
}
