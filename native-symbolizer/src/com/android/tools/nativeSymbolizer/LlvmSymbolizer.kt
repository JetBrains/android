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
import java.time.Duration
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
                     private val timeout : Duration = Duration.ofSeconds(5)) : NativeSymbolizer {

  private var activeProcess : ProcessWrapper? = null
  private val executor : ExecutorService = Executors.newSingleThreadExecutor()

  /**
   * @param abiArch - The cpu architecture of the symbol.
   * @param module - The file path to the module.
   * @param offset - The starting byte address in the module of the symbol.
   */
  override fun symbolize(abiArch: String, module: File, offset: Long): Symbol? {
    // Sort the symbol files to ensure consistent ordering between runs.
    val symbolFiles = symLocator.getFiles(abiArch).filter { it.nameWithoutExtension == module.nameWithoutExtension }.sorted()
    val requests = symbolFiles.map { formatRequest(it, offset) }

    for (request in requests) {
      val future = executor.submit<Symbol?> {
        try {
          // Get the process within the future to ensure that if it dies executing this call, it can recover for the next.
          val process = getProcess()

          process.stdin.write(request)
          process.stdin.flush()

          parseResponse(readToEnd(process.stdout), module)
        } catch (e : Exception){
          // Restart the process since it could be in a broken state right now. We do it here so that we don't run into race conditions
          // between runs freeing and starting processes.
          stop()

          throw e
        }
      }

      try {
        try {
          future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)?.let { return it }
        } catch (e: TimeoutException) {
          throw e // Pass the timeout up level so it can be handle with the other expected exceptions.
        } catch (e: ExecutionException) {
          // If the future throws an exception, it will be wrapped as an ExecutionException. Rethrow the cause so we can handle it
          // correctly.
          e.cause?.let { throw it }
        }
      } catch (e: NoSymbolizerException) {
        getLogger().warn(e.message, e)
        throw e // If we could not find the symbolizer, there is no point trying other requests.}
      } catch (e: TimeoutException) {
        getLogger().warn("llvm-symbolizer timed out", e)
      } catch (e: ExecutionException) {
        getLogger().warn("llvm-symbolizer communication failed", e)
      } catch (e: Exception) {
        getLogger().warn("Unexpected exception while running llvm-symbolizer", e)
      }
    }

    return null
  }

  // We can't use the built-in "read to end" function on the buffered reader class because it will close the stream.
  private fun readToEnd(reader: BufferedReader) : List<String> {
    val response = mutableListOf<String>()

    while (true) {
      val line = reader.readLine()

      if (line.isNullOrEmpty()) {
        return response
      }

      response.add(line)
    }
  }

  /**
   * Gets the current process or start a new process if the process is not running.
   */
  private fun getProcess() : ProcessWrapper {
    if (activeProcess != null && activeProcess!!.isAlive) {
      return activeProcess!!
    }

    // If the process fails to start, start() will throw an IOException.
    activeProcess?.dispose()

    try {
      activeProcess = ProcessWrapper(ProcessBuilder(symbolizerExe).start())
    } catch (e: IOException) {
      throw NoSymbolizerException(e)  // Wrap the exception so we can catch it elsewhere.
    }

    return activeProcess!!
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

  override fun stop() {
    activeProcess?.dispose()
    activeProcess = null
  }

  private class ProcessWrapper(private val process: Process) : Disposable {
    val stdin = process.outputWriter(Charsets.UTF_8)!!
    val stdout =process.inputReader(Charsets.UTF_8)!!

    val isAlive: Boolean
      get() = process.isAlive

    override fun dispose() {
      process.destroy()
    }
  }

  private class NoSymbolizerException(e: IOException) : IOException(e.message, e) { }
}
