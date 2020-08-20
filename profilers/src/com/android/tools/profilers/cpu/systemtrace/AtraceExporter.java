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
package com.android.tools.profilers.cpu.systemtrace;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import org.jetbrains.annotations.NotNull;
import trebuchet.io.DataSlice;

/**
 * This class takes Atrace files recorded by the profilers and exports them in a format Systrace can load.
 * Systrace expects the file to be compressed. The first line should be "# tracer: nop".
 */
public final class AtraceExporter {
  /**
   * This method reads data from an {@link AtraceProducer} and writes it out compressed to a {@link File}.
   * @param file Input file to read data from.
   * @param output The file stream to write systrace compatible data to.
   * @throws IOException if the trace file failed to decompress or fails to write.
   */
  public static void export(@NotNull File file, @NotNull OutputStream output) throws IOException {
    TrebuchetBufferProducer buffer;
    if (AtraceProducer.verifyFileHasAtraceHeader(file)) {
      buffer = new AtraceProducer();
    }
    else {
      throw new IOException("Unable to verify file type for export: " + file.getAbsolutePath());
    }
    if (!buffer.parseFile(file)) {
      throw new IOException("Failed to parse file for export: " + file.getAbsolutePath());
    }
    try (DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(output)) {
      // The first line is added by the AtraceProducer, for the atrace-parser. Systrace will throw an error
      // if this line is detected in the file parsing so we throw away the line.
      DataSlice line = buffer.next();
      output.write(AtraceProducer.HEADER.toByteArray());
      while ((line = buffer.next()) != null) {
        deflaterOutputStream.write(line.getBuffer());
      }
      deflaterOutputStream.flush();
    } catch (IOException ex) {
      throw new IOException("Failed to export atrace file.", ex);
    }
  }
}
