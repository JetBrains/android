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
package com.android.tools.nativeSymbolizer

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.downloads.AndroidProfilerDownloader
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.io.IOException
import java.nio.file.Paths

/**
 * @param name - The name of the symbol.
 * @param module - The file path to the module containing the symbol.
 * @param sourceFile - The file path to the source file where the symbol is defined.
 * @param lineNumber - The line in [sourceFile] where the symbol is defined.
 */
data class Symbol(val name: String, val module: String, val sourceFile: String = "", val lineNumber: Int = 0)

/**
 * Components that can fetch information about native symbols by a module and an offset.
 */
interface NativeSymbolizer {

  /**
   * Obtains information about a function (symbol) located at a given offset in a given module
   * @param abiArch - CPU architecture of a give module (e.g x86, arm, arm64 and so on)
   * @param module - path to a native module (on the device or host)
   * @param offset - offset in the native module that needs to be symbolized
   * @return symbols info if it can be found, or null otherwise
   */
  @Throws(IOException::class)
  fun symbolize(abiArch: String, module: File, offset: Long): Symbol?
  fun stop()
}

fun createNativeSymbolizer(locator:SymbolFilesLocator): NativeSymbolizer {
  val symbolizerPath = getLlvmSymbolizerPath()
  getLogger().info("Creating a native symbolizer. Executable path: $symbolizerPath")
  return LlvmSymbolizer(symbolizerPath, locator)
}

/**
 *  Get path to the llvm-symbolizer executable
 */
fun getLlvmSymbolizerPath(): String {
  val exe: String
  val os: String
  if (SystemInfo.isLinux) {
    os = "linux-x86_64"
    exe = "llvm-symbolizer"
  } else if (SystemInfo.isMac) {
    os = "darwin-x86_64"
    exe = "llvm-symbolizer"
  } else if (SystemInfo.isWindows) {
    os = "windows-x86_64"
    exe = "llvm-symbolizer.exe"
  } else {
    throw IllegalStateException("Unknown operating system")
  }

  val result = if (StudioPathManager.isRunningFromSources()) {
    StudioPathManager.resolvePathFromSourcesRoot("prebuilts/tools/$os/lldb/bin/$exe")
  } else {
    if (IdeInfo.getInstance().isAndroidStudio) {
      Paths.get(PathManager.getHomePath(), "plugins/android-ndk/resources/lldb/bin/$exe")
    } else {
      AndroidProfilerDownloader.getInstance().getHostDir("plugins/android/resources/llvm-symbolizer/$os/$exe")
    }
  }
  return result.toString()
}

internal fun getLogger(): Logger {
  return Logger.getInstance("NativeSymbolizer")
}
