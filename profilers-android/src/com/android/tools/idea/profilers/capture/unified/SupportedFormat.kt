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
package com.android.tools.idea.profilers.capture.unified

import com.android.tools.idea.profilers.capture.PerfettoCaptureFileType
import com.android.tools.profilers.cpu.CpuCaptureParserUtil
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

interface SupportedFormat {
  fun isSupported(file: VirtualFile): Boolean
}

object PerfettoTraceFormat : SupportedFormat {
  override fun isSupported(file: VirtualFile): Boolean {
    if (PerfettoCaptureFileType.EXTENSIONS.contains(file.extension)) {
      return true
    }
    val ioFile = try {
      file.toNioPath().toFile()
    } catch (e: Exception) {
      // VirtualFile might not be on local disk (e.g. inside a JAR),
      // in which case we can't use the legacy parser.
      return false
    }
    // Content-based trace type
    val detectedType = CpuCaptureParserUtil.getFileTraceType(
      ioFile,
      TraceType.UNSPECIFIED
    )
    return detectedType == TraceType.PERFETTO || detectedType == TraceType.ATRACE
  }
}
