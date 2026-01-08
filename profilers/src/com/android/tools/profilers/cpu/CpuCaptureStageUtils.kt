/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.intellij.openapi.util.io.FileUtil
import java.io.File

object CpuCaptureStageUtils {
  /**
   * Renames a temporary capture file to an identifiable trace file within the system's temporary directory.
   *
   * This is used when a system trace is captured and needs to be saved with a specific name so it can
   * be easily identified and opened by the editor.
   *
   * @param captureFile the source temporary file
   * @param targetFileName the desired name for the trace file
   * @return the renamed [File] if successful, or null otherwise
   */
  @JvmStatic
  fun renameTempToTraceFile(captureFile: File, targetFileName: String): File? {
    // Use the system temp directory as the destination folder for the permanent file.
    val outputDir = File(FileUtil.getTempDirectory())
    val traceFile = File(outputDir, targetFileName)

    // Try to rename the capture file to the target file.
    if (captureFile.renameTo(traceFile)) {
      return traceFile
    }
    return null
  }

  @JvmStatic
  fun getTraceFile(traceId: Long): File {
    return File(FileUtil.getTempDirectory(), "capture_$traceId.trace")
  }
}
