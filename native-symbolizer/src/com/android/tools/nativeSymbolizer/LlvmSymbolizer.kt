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
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
class LlvmSymbolizer(private val symbolizerExe: String,
                     private val symLocator: SymbolFilesLocator,
                     private val timeoutMsc: Long = 5000) : NativeSymbolizer {

  private var procHolder : ProcessHolder? = null
  private val executor : ExecutorService = Executors.newSingleThreadExecutor()

  /**
   * @param abiArch - The cpu architecture of the symbol.
   * @param module - The file path to the module.
   * @param offset - The starting byte address in the module of the symbol.
   */
  override fun symbolize(abiArch: String, module: File, offset: Long): Symbol? {
    val symFiles = symLocator.getFiles(abiArch)

    for (symFile in symFiles.filter { it.nameWithoutExtension == module.nameWithoutExtension }) {
      val request = formatRequest(symFile, offset)

      val holder = getProcHolder()
      val future = executor.submit( Callable<List<String>> {
        holder.stdin.write(request)
        holder.stdin.flush()

        val response: MutableList<String> = mutableListOf()
        var responseLine: String?
        while (true) {
          responseLine = holder.stdout.readLine()
          if (responseLine.isNullOrEmpty()) {
            break
          }
          response.add(responseLine)
        }
        response
      })
      val response : List<String>
      try {
        response = future.get(timeoutMsc, TimeUnit.MILLISECONDS)
      } catch (e: TimeoutException) {
        getLogger().warn("llvm-symbolizer timed out", e)
        stop()
        continue
      } catch (e: ExecutionException) {
        getLogger().warn("llvm-symbolizer communication failed", e)
        stop()
        continue
      }

      val result = parseResponse(response, module)
      if (result != null)
        return result
    }

    return null
  }

  private fun getProcHolder() : ProcessHolder {
    var holder = procHolder
    if (holder == null || !holder.process.isAlive) {
      start()
      holder = procHolder!! // procHolder must't be null after start()
    }
    return holder
  }

  private fun formatRequest(symFile: File, offset: Long): String {
    val escapedPath = symFile.path.replace("\\", "\\\\").replace("\"", "\\\"")
    return java.lang.String.format("\"%s\" 0x%x\n", escapedPath, offset)
  }

  private fun parseResponse(response: List<String>, module: File): Symbol? {
    if (response.isEmpty())
      return null

    val name = response.first().trim()
    if (name.isEmpty() || name == "??") {
      return null
    }
    if (response.size < 2)
      return Symbol(name, module.absolutePath)

    // Location line looks like this: <path to source file>:<line number>:<column number>
    val locationLine = response[1].trim()
    val indexBeforeColumn = locationLine.lastIndexOf(':')
    if (indexBeforeColumn < 2)
      return Symbol(name, module.absolutePath)

    val indexBeforeLine = locationLine.lastIndexOf(':', indexBeforeColumn - 1)
    if (indexBeforeColumn < 1)
      return Symbol(name, module.absolutePath)

    val sourceFile = locationLine.substring(0, indexBeforeLine)
    val lineNumber = locationLine.substring(indexBeforeLine + 1, indexBeforeColumn).toIntOrNull() ?: 0

    return Symbol(name, module.absolutePath, sourceFile, lineNumber)
  }

  private fun start() {
    if (procHolder != null)
      stop()

    val builder = ProcessBuilder(symbolizerExe)
    val process = builder.start()
    if (!process.isAlive) {
      throw IOException("Symbolizer process is not alive. Executable: $symbolizerExe")
    }

    val stdin = OutputStreamWriter(process.outputStream, Charsets.UTF_8)
    val stdout = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
    procHolder = ProcessHolder(process, stdout, stdin)
  }

  override fun stop() {
    procHolder?.dispose()
    procHolder = null
  }

  private class ProcessHolder(val process: Process,
                              val stdout: BufferedReader,
                              val stdin: OutputStreamWriter) : Disposable {
    override fun dispose() {
      process.destroy()
    }
  }
}