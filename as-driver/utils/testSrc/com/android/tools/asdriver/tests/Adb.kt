/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests

import com.android.SdkConstants
import com.android.testutils.TestUtils
import com.android.utils.time.TimeSource
import com.android.utils.time.toDurationUnit
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.regex.Matcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toDuration

class Adb private constructor(
  private val sdk: AndroidSdk,
  private val home: Path,
  private val process: Process? = null,
  private val stdout: Path? = null,
  private val stderr: Path? = null,
): AutoCloseable {

  @Throws(IOException::class)
  override fun close() {
    when(process) {
      null -> runCommand("kill-server")
      else -> if (process.isAlive) process.destroy()
    }
  }

  @Throws(IOException::class, InterruptedException::class)
  fun waitForLog(expectedRegex: String, timeout: Long, unit: TimeUnit): Matcher =
    LogFile(stdout).waitForMatchingLine(expectedRegex, timeout, unit)

  @JvmSynthetic
  fun waitForLog(expectedRegex: String, timeout: Duration): Matcher = waitForLog(expectedRegex, timeout.inWholeMicroseconds, MICROSECONDS)

  /** Waits for the expected regular expressions to show up in the logs for this [Adb], in order. The timeout is for all lines. */
  @Throws(IOException::class, InterruptedException::class)
  fun waitForLogs(expectedRegexes: Iterable<String>, timeout: Long, unit: TimeUnit): List<Matcher> =
    waitForLogs(expectedRegexes, timeout.toDuration(unit.toDurationUnit()))

  /** Waits for the expected regular expressions to show up in the logs for this [Adb], in order. The timeout is for all lines. */
  @JvmSynthetic
  fun waitForLogs(expectedRegexes: Iterable<String>, timeout: Duration): List<Matcher> {
    val start = TimeSource.Monotonic.markNow()
    val logFile = LogFile(stdout)
    return expectedRegexes.map {
      val remainingDuration = timeout - start.elapsedNow()
      logFile.waitForMatchingLine(it, remainingDuration.inWholeMicroseconds, MICROSECONDS)
    }
  }

  @Throws(IOException::class, InterruptedException::class)
  fun waitForDevice(emulator: Emulator) {
    waitForDevice(emulator, 24.hours)
  }

  @JvmSynthetic
  fun waitForDevice(emulator: Emulator, duration: Duration) {
    runCommand("track-devices") {
      // https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/SERVICES.TXT;l=23
      waitForLog("([0-9a-f]{4})?${emulator.serialNumber}\tdevice", duration)
    }
  }

  @Throws(IOException::class)
  fun runCommand(vararg command: String): Adb = exec(sdk, home, *command)

  @Throws(IOException::class)
  fun runCommand(vararg command: String, emulator: Emulator): Adb = exec(sdk, home, *command, emulator=emulator)

  @JvmSynthetic
  @Throws(IOException::class)
  fun runCommand(vararg command: String, emulator: Emulator? = null, block: (Adb.() -> Unit)) {
    exec(sdk, home, *command, emulator=emulator).use { with(it, block) }
  }

  companion object {
    /** Default start for most use cases. */
    @JvmStatic
    @Throws(IOException::class)
    fun start(sdk: AndroidSdk, home: Path): Adb = start(sdk, home, true, "nodaemon")

    @JvmStatic
    @Throws(IOException::class)
    fun start(sdk: AndroidSdk, home: Path, startServer: Boolean, vararg params: String): Adb {
      if (!startServer) return Adb(sdk, home)
      val command = arrayOf("server") + params.filter(String::isNotBlank).toTypedArray()
      return exec(sdk, home, *command)
    }

    @Throws(IOException::class)
    private fun exec(sdk: AndroidSdk, home: Path, vararg params: String, emulator: Emulator? = null): Adb {
      val logsDir = Files.createTempDirectory(TestUtils.getTestOutputDir(), "adb_logs")
      val stdout = logsDir.resolve("stdout.txt").also { Files.createFile(it) }
      val stderr = logsDir.resolve("stderr.txt").also { Files.createFile(it) }
      val header = "=== $stdout ${params.joinToString("-")} ${System.currentTimeMillis()} ===\n"
      FileWriter(stdout.toString(), /* append = */ true).use { it.write(header) }
      FileWriter(stderr.toString()).use { it.write(header) }
      val command = listOf(sdk.sourceDir.resolve(SdkConstants.FD_PLATFORM_TOOLS).resolve(SdkConstants.FN_ADB).toString()) + params
      System.out.printf("Adb invocation '${command.joinToString(" ")}' has stdout log at: $stdout%n")
      val pb = ProcessBuilder(command).apply {
        redirectOutput(stdout.toFile())
        redirectError(stderr.toFile())
        environment()["HOME"] = home.toString()
        emulator?.let { environment()["ANDROID_SERIAL"] = emulator.serialNumber }
      }
      return Adb(sdk, home, pb.start(), stdout, stderr)
    }
  }
}
