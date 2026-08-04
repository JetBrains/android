/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto

import com.android.tools.idea.transport.DeployableFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import java.io.File

object PerfettoPrebuiltLocator {
  private val LOGGER = Logger.getInstance(PerfettoPrebuiltLocator::class.java)

  /**
   * Resolves the absolute path to a native Perfetto prebuilt executable.
   *
   * @param binaryName The base name of the binary (e.g. "traceconv" or "trace_processor_daemon")
   * @param devDirName The directory name under `prebuilts/tools/common/` (e.g. "traceconv" or "trace-processor-daemon")
   * @param releaseDirName The directory name under `plugins/android/resources/` (e.g. "traceconv" or "trace_processor_daemon")
   */
  @JvmStatic
  fun getExecutablePath(binaryName: String, devDirName: String = binaryName, releaseDirName: String = binaryName): String {
    val devPath =
      when {
        SystemInfo.isWindows -> "prebuilts/tools/common/$devDirName/windows"
        SystemInfo.isMac -> "prebuilts/tools/common/$devDirName/${if (CpuArch.isArm64()) "darwin-arm64" else "darwin-x86_64"}"
        SystemInfo.isLinux -> "prebuilts/tools/common/$devDirName/linux"
        else -> {
          LOGGER.warn("Unsupported platform for $binaryName. Using linux binary.")
          "prebuilts/tools/common/$devDirName/linux"
        }
      }

    val releasePath = "plugins/android/resources/$releaseDirName"
    val executableName = if (SystemInfo.isWindows) "$binaryName.exe" else binaryName

    val deployable = DeployableFile.Builder(executableName).setReleaseDir(releasePath).setDevDir(devPath).setExecutable(true).build()

    return File(deployable.dir, deployable.fileName).absolutePath
  }
}
