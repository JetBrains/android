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

import com.android.tools.profilers.IdeProfilerServices
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException

object CpuCaptureStageUtils {
  /**
   * If the unified preview is enabled, we need to ensure that we create a permanent trace
   * file and put in directory specific to the project
   * This method checks if the given capture file is already in the temp directory with the correct
   * name. If so, it returns the file.
   * Otherwise, it creates a copy in the temp directory with the correct name and returns the copy.
   * TODO(b/472667234) Remove file copy logic since original file will be used
   * If copying fails, null is returned.
   */
  @JvmStatic
  fun getPermanentCaptureFile(services: IdeProfilerServices, captureFile: File, targetFileName: String): File? {
    val projectId = if (services.projectHomeHash.isNotEmpty()) {
      services.projectHomeHash
    } else {
      Integer.toHexString(System.identityHashCode(services))
    }
    val rootDir = File(FileUtil.getTempDirectory(), "AndroidStudioProfiler")
    val outputDir = File(rootDir, projectId)

    if (!outputDir.exists()) {
      outputDir.mkdirs()
    }
    // If a copy of the file is already present in temp directory. Return that
    // instead of creating a new one
    if (FileUtil.filesEqual(captureFile.parentFile, outputDir) &&
        captureFile.name == targetFileName) {
      return captureFile
    }
    try {
      val permanentFile = File(outputDir, targetFileName)
      if (permanentFile.exists()) {
        return permanentFile
      }
      FileUtil.copy(captureFile, permanentFile)
      return permanentFile
    }
    catch (e: IOException) {
      Logger.getInstance(CpuCaptureStage::class.java).warn("Failed to create permanent capture file", e)
    }
    return null
  }
}
