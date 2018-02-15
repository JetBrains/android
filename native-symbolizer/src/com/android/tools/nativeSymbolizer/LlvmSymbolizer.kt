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

import com.intellij.openapi.Disposable
import java.io.*

/**
 * Implementation of NativeSymbolizer that uses llvm-symbolizer.
 *
 * llvm-symbolizer speaks a simple text protocol
 *  stdin: <path to native library> 0x<hex offset>
 * stdout: <function name>
 * stdout: <path to source file>:<line number>:<column number>
 * stdout: _______<empty line>________
 *
 * For example:
 * /local/path/to/libnative-lib.so 0x909c
 * TestSimpleMethodCall(_JNIEnv*, _jobject*)
 * /usr/local/google/home/ezemtsov/projects/android-apps/sum/app/src/main/cpp/native-lib.cpp:36:7
 *
 * More info about llvm-symbolizer: https://llvm.org/docs/CommandGuide/llvm-symbolizer.html
 */
class LlvmSymbolizer(private val symbolizerExe: String, private val symLocator: SymbolFilesLocator) : NativeSymbolizer {

  private var procHolder : ProcessHolder? = null

  override fun symbolize(abiArch: String, module: String, offset: Long): Symbol? {
    val symFiles = symLocator.findSymbolFiles(abiArch, module)

    var holder = procHolder
    if (holder == null || !holder.process.isAlive) {
      start()
      holder = procHolder!! // procHolder must't be null after start()
    }

    for (symFile in symFiles) {
      holder.stdin.write(formatRequest(symFile, offset))
      holder.stdin.flush()

      val response: MutableList<String> = mutableListOf()
      var responseLine: String?
      while (true) {
        responseLine = holder.stdout.readLine()
        if (responseLine == null || responseLine.isEmpty()) {
          break
        }
        response.add(responseLine)
      }

      val result = parseResponse(response, module)
      if (result != null)
        return result
    }

    return null
  }

  override fun dispose() {
    stop()
  }

  private fun formatRequest(symFile: File, offset: Long): String {
    return java.lang.String.format("%s 0x%x\n", symFile.path, offset)
  }

  private fun parseResponse(response: List<String>, module: String): Symbol? {
    if (response.isEmpty())
      return null

    val name = response.first().trim()
    if (name.isEmpty() || name == "??") {
      return null
    }
    if (response.size < 2)
      return Symbol(name, module)

    val locationParts = response[1].trim().split(':')
    if (locationParts.isEmpty())
      return Symbol(name, module)

    val sourceFile = locationParts[0]
    val lineNumber = locationParts.getOrNull(1)?.toIntOrNull() ?: 0

    return Symbol(name, module, sourceFile, lineNumber)
  }

  private fun start() {
    if (procHolder != null)
      stop()

    val builder = ProcessBuilder(symbolizerExe, "-demangle")
    val process = builder.start()
    if (!process.isAlive) {
      throw IOException("Symbolizer process is not alive.")
    }

    val stdin = OutputStreamWriter(process.outputStream, Charsets.UTF_8)
    val stdout = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
    procHolder = ProcessHolder(process, stdout, stdin)
  }

  private fun stop() {
    procHolder?.dispose()
    procHolder = null
  }

  private class ProcessHolder(val process: Process,
                              val stdout: BufferedReader,
                              val stdin: OutputStreamWriter) : Disposable {
    override fun dispose() {
      process.destroy()
      stdout.close()
      stdin.close()
    }
  }
}