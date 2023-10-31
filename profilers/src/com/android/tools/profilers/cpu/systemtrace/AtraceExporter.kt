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
package com.android.tools.profilers.cpu.systemtrace

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.DeflaterOutputStream

/**
 * This class takes Atrace files recorded by the profilers and exports them in a format Systrace can load. Systrace expects the file to be
 * compressed. The first line should be "# tracer: nop".
 */
object AtraceExporter {
  /**
   * This method reads data from an [AtraceProducer] and writes it out compressed to a [File].
   * @param file Input file to read data from.
   * @param output The file stream to write systrace compatible data to.
   * @throws IOException if the trace file failed to decompress or fails to write.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun export(file: File, output: OutputStream) {
    if (!AtraceProducer.verifyFileHasAtraceHeader(file)) {
      throw IOException("Unable to verify file type for export: ${file.absolutePath}")
    }

    val buffer = AtraceProducer()

    if (!buffer.parseFile(file)) {
      throw IOException("Failed to parse file for export: ${file.absolutePath}")
    }

    try {
      // The header is not compressed, so we don't need to pass it through the deflater.
      output.write(AtraceProducer.HEADER.toByteArray())

      // We use DeflaterOutputStream because the data in the trace is compressed, we need it decompressed.
      DeflaterOutputStream(output).use {deflaterStream ->
        // The first line is added by the AtraceProducer, for the atrace-parser. Systrace will throw an error  if this line is detected in
        // the file parsing, so we throw away the line.
        buffer.next()

        while (true) {
          if (buffer.next()?.also { deflaterStream.write(it.buffer) } == null) {
            break
          }
        }

        deflaterStream.flush()
      }
    } catch (ex: IOException) {
      throw IOException("Failed to export atrace file.", ex)
    }
  }
}
