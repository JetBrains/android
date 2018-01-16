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
package com.android.tools.profilers.cpu.atrace

import com.android.testutils.TestUtils
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.Test
import java.io.*
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

class AtraceExporterTest {

  @Test
  fun testFirstLineIsTracerNop() {
    val parser = AtraceDecompressor(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"))
    val exportedFile = FileUtil.createTempFile("atrace", ".trace")
    AtraceExporter.export(parser, exportedFile)
    val readCompressedFile = InflaterInputStream(FileInputStream(exportedFile))
    val buffer = ByteArray(1024)
    readCompressedFile.read(buffer, 0, 1024)
    val lines = String(buffer).split("\n")
    assertThat(lines[0]).matches("# tracer: nop")
  }

  @Test(expected = IOException::class)
  fun testLoadingInvalidFileThrowsException() {
    //Create temp invalid file
    val tempFile = createTempTraceFile()
    val parser = AtraceDecompressor(tempFile)
    val exportedFile = FileUtil.createTempFile("atrace", ".trace")
    exportedFile.deleteOnExit()
    AtraceExporter.export(parser, exportedFile)
  }

  fun createTempTraceFile() : File {
    val tempFile = File("temp.trace")
    val streamWriter = OutputStreamWriter(FileOutputStream(tempFile))
    // Copy the trace header output, and add a fake line to fail reading.
    streamWriter.write("TRACE:\n")
    streamWriter.write("Some Fake Line\n")
    streamWriter.flush()
    streamWriter.close()
    return tempFile
  }

}
