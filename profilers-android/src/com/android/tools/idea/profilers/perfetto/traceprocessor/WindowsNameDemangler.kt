/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Class that calls out to the C++Filt tool to demangle itanium names on windows.
 *
 * llvm-c++filt speaks a simple text protocol
 *  stdin: <mangled c++ name>
 * stdout: <demangled name>
 * stdout: _______<empty line>________
 *
 * For example:
 * _ZN7android6Parcel13continueWriteEm
 * android::Parcel::continueWrite(unsigned long)
 *
 * More info about c++filt: https://llvm.org/docs/CommandGuide/llvm-cxxfilt.html
 */
class WindowsNameDemangler(private val timeoutMsc: Long = 5000) : NameDemangler {
  private fun getLlvmCppFiltPath(): String {
    val exe = "x86_64-linux-android-c++filt.exe"
    val result = if (StudioPathManager.isRunningFromSources()) {
      StudioPathManager.resolvePathFromSourcesRoot("prebuilts/tools/windows-x86_64/lldb/bin/$exe")
    }
    else {
      Paths.get(PathManager.getBinPath(), "lldb/bin/$exe")
    }
    return result.toString()
  }

  override fun demangleInplace(stackFrames: Collection<NameHolder>) {
    if (!SystemInfo.isWindows) {
      // Currently only windows needs an outside process to demangle names, mac/linux are done inside the daemon.
      return
    }
    val holder = start()
    holder ?: return

    val duplicatesMap = HashMap<String, String>()
    for (frame in stackFrames) {
      try {
        if (duplicatesMap.containsKey(frame.name)) {
          frame.name = duplicatesMap[frame.name]!!
          continue
        }
        // Only submit names that start with _Z. Other names are invalid.
        if (frame.name.startsWith("_Z")) {
          holder.stdin.write(frame.name + "\n")
          holder.stdin.flush()
          val response = holder.stdout.readLine() ?: frame.name
          duplicatesMap[frame.name] = response
          frame.name = response

        }
      }
      catch (ex: Exception) {
        getLogger().error(ex)
      }
    }
    holder.dispose()
  }

  private fun start() : ProcessHolder? {
    var procHolder: ProcessHolder?
    try {
      val llvmfiltPath = getLlvmCppFiltPath()
      val builder = ProcessBuilder(llvmfiltPath)
      val process = builder.start()
      if (!process.isAlive) {
        throw IOException("C++ filt process is not alive. Executable: $llvmfiltPath")
      }

      val stdin = OutputStreamWriter(process.outputStream, Charsets.UTF_8)
      val stdout = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
      procHolder = ProcessHolder(process, stdout, stdin, timeoutMsc)
    } catch (e: Exception) {
      Logger.getInstance("CppNameDemangler").error(e)
      procHolder = null
    }
    return procHolder
  }

  private fun getLogger(): Logger {
    return Logger.getInstance("CppNameDemangler")
  }

  private class ProcessHolder(val process: Process,
                              val stdout: BufferedReader,
                              val stdin: OutputStreamWriter,
                              val timeoutMsc: Long) : Disposable {
    override fun dispose() {
      process.destroy()
      process.waitFor(timeoutMsc, TimeUnit.MILLISECONDS)
    }
  }
}