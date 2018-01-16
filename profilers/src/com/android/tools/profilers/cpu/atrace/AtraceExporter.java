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
package com.android.tools.profilers.cpu.atrace;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;

/**
 * This class takes Atrace files recorded by the profilers and exports them in a format Systrace can load.
 * Systrace expects the file to be compressed. The first line should be "# tracer: nop".
 */
public final class AtraceExporter {
  /**
   * This method reads data from an {@link AtraceDecompressor} and writes it out compressed to a {@link File}.
   * @param data Profiler captured atrace data.
   * @param exportFile The file to write systrace compatible data to.
   * @throws IOException if the trace file failed to decompress or fails to write.
   */
  public static void export(@NotNull AtraceDecompressor data, @NotNull File exportFile) throws IOException {
    try (DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(new FileOutputStream(exportFile))) {
      // The first line is added by the AtraceDecompressor, for the atrace-parser. Systrace will throw an error
      // if this line is detected in the file parsing so we throw away the line.
      String line = data.getNextLine();
      while (!StringUtil.isEmpty(line = data.getNextLine())) {
          deflaterOutputStream.write(String.format("%s\n", line).getBytes());
      }
      deflaterOutputStream.flush();
      deflaterOutputStream.close();
    } catch (IOException | DataFormatException ex) {
      throw new IOException("Failed to export atrace file.", ex);
    }
  }
}
