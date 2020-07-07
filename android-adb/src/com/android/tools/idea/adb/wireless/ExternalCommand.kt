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
package com.android.tools.idea.adb.wireless

import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import java.util.concurrent.TimeUnit

/**
 * Executes a command with ability to pass `stdin` and capture `stdout` and `stderr`.
 */
class ExternalCommand(val executable: String)  {
  private val LOG = logger<ExternalCommand>()

  @Throws(IOException::class)
  fun execute(args: List<String>, stdin: InputStream, stdout: OutputStream, stderr: OutputStream, timeout: Long, unit: TimeUnit): Int {
    val command: MutableList<String> = ArrayList()
    val exe = File(executable)
    command.add(exe.absolutePath)
    command.addAll(args)
    LOG.info("Executing command: ${command}")
    val pb = ProcessBuilder(command)
    val process = pb.start()
    val inToProcess = PipeConnector(stdin, process.outputStream)
    val processToOut = PipeConnector(process.inputStream, stdout)
    val processToErr = PipeConnector(process.errorStream, stderr)
    inToProcess.start()
    processToOut.start()
    processToErr.start()
    var code = 255
    try {
      val finished = process.waitFor(timeout, unit)
      if (!finished) {
        LOG.warn("Command did not terminate within specified timeout")
        process.destroyForcibly()
      } else {
        code = process.exitValue()
        LOG.info("Command finished with exit value ${code}")
      }
      processToOut.join()
      stdin.close()
      inToProcess.join()
    }
    catch (e: InterruptedException) {
      LOG.warn("Command was interrupted", e)
    }
    return code
  }

  private class PipeConnector(private val input: InputStream, private val output: OutputStream) : Thread() {
    override fun run() {
      try {
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
          output.write(buffer, 0, read)
          output.flush()
        }
      }
      catch (e: IOException) {
        // Ignore and exit the thread
      }
    }
  }
}