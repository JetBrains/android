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
package com.android.cliserver

import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs
import org.jetbrains.annotations.TestOnly

private const val PID_DIR = "cli/studio"

data class ServerInstance(val pid: Long, val port: Int, val key: String)

interface ProcessService {
  val current: ProcessHandle

  fun of(pid: Long): ProcessHandle?
}

private object DefaultProcessService : ProcessService {
  override val current: ProcessHandle = ProcessHandle.current()

  override fun of(pid: Long): ProcessHandle? = ProcessHandle.of(pid).getOrNull()
}

class CliServerRegistry(androidHome: Path, @TestOnly private val processService: ProcessService = DefaultProcessService) {
  private val registryDir = androidHome.resolve(PID_DIR)

  fun register(port: Int): String {
    registryDir.createDirectories()
    clean()
    val process = processService.current
    val file = registryDir.resolve(process.pid().toString())
    val uuid = UUID.randomUUID().toString()
    val startInstant = process.info().startInstant().getOrNull()?.epochSecond ?: "start time unavailable"

    file.writeText("$port $uuid $startInstant")

    return uuid
  }

  fun unregister() {
    val pid = processService.current.pid()
    registryDir.resolve(pid.toString()).deleteIfExists()
  }

  private fun clean() {
    if (!registryDir.exists() || !registryDir.isDirectory()) {
      return
    }

    registryDir
      .listDirectoryEntries()
      .filter { it.name.matches(Regex("[0-9]+")) }
      .filter { it.isRegularFile() }
      .forEach { file ->
        var process: ProcessHandle?
        try {
          val pid = file.name.toLong()
          process = processService.of(pid)
        } catch (_: NumberFormatException) {
          // If the file name is malformed, we can clean it up
          process = null
        }

        val processStartInstant = process?.info()?.startInstant()?.getOrNull()?.epochSecond
        val fileStartInstant = file.readLines().getOrNull(2)?.toLongOrNull()
        val diff = if (processStartInstant != null && fileStartInstant != null) abs(processStartInstant - fileStartInstant) else null

        if (process?.isAlive != true || (diff != null && diff > 5) || ((fileStartInstant == null) xor (processStartInstant == null))) {
          try {
            file.deleteIfExists()
          } catch (_: Exception) {
            // Ignore deletion failures
          }
        }
      }
  }
}
