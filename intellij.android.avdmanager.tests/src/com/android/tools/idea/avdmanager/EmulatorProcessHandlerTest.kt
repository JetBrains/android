/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus
import com.android.tools.idea.avdmanager.EmulatorLogListener.Severity
import com.android.tools.idea.testing.executeCapturingLoggedErrorsAndWarnings
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

/** Tests for [EmulatorProcessHandler]. */
class EmulatorProcessHandlerTest {

  @get:Rule val rule = ApplicationRule()

  @Test
  fun testLogProcessing() {
    val avdDir = Paths.get("/users/user/.android/avd")
    val avdId = "Pixel_9_API_34"
    val avd =
      AvdInfo(
        avdDir.resolve("$avdId.ini"),
        avdDir.resolve("$avdId.avd"),
        null,
        null,
        null,
        AvdStatus.OK,
      )
    val commandLine =
      "/users/user/Android/Sdk/emulator/emulator -avd $avdId -qt-hide-window -grpc-use-token"
    val logMessages =
      arrayOf(
        "INFO         | Informational message",
        "USER_INFO    | Informational message with user notification",
        "WARNING      | Warning message",
        "USER_WARNING | Warning message with user notification",
        "ERROR        | Error message",
        "USER_ERROR   | Error message with user notification",
        "FATAL        | Fatal error message",
      )

    val capturedLogEntries = mutableListOf<LogEntry>()
    val logCapturer =
      object : EmulatorLogListener {
        override fun messageLogged(
          avd: AvdInfo,
          severity: Severity,
          notifyUser: Boolean,
          message: String,
        ) {
          capturedLogEntries.add(LogEntry(avd, severity, notifyUser, message))
        }
      }
    ApplicationManager.getApplication()
      .messageBus
      .connect()
      .subscribe(EmulatorLogListener.TOPIC, logCapturer)
    val loggedMessages = executeCapturingLoggedErrorsAndWarnings {
      val process = FakeProcess(logMessages, 0)
      val processHandler = EmulatorProcessHandler(process, commandLine, avd)
      processHandler.startNotify()
      processHandler.waitFor()
    }

    assertThat(loggedMessages.warnings)
      .containsExactly(
        "Warning message",
        "Warning message with user notification",
        "Error message",
        "Error message with user notification",
        "Fatal error message",
      )
    assertThat(loggedMessages.errors).isEmpty()
    assertThat(capturedLogEntries)
      .containsExactly(
        LogEntry(avd, Severity.INFO, false, commandLine),
        LogEntry(avd, Severity.INFO, false, "Informational message"),
        LogEntry(avd, Severity.INFO, true, "Informational message with user notification"),
        LogEntry(avd, Severity.WARNING, false, "Warning message"),
        LogEntry(avd, Severity.WARNING, true, "Warning message with user notification"),
        LogEntry(avd, Severity.ERROR, false, "Error message"),
        LogEntry(avd, Severity.ERROR, true, "Error message with user notification"),
        LogEntry(avd, Severity.FATAL, false, "Fatal error message"),
        LogEntry(avd, Severity.INFO, false, "Process finished with exit code 0"),
      )
  }

  private class FakeProcess(output: Array<String>, private val exitCode: Int) : Process() {

    private val stdin = ByteArrayOutputStream()
    private val stdout = CountDownByteArrayInputStream(output.joinToString("\n").toByteArray())
    private val stderr = ByteArray(0).inputStream()
    private val handle = mock<ProcessHandle>()

    override fun destroy() {
      TODO("Not yet implemented")
    }

    override fun getOutputStream(): OutputStream = stdin

    override fun getInputStream(): InputStream = stdout

    override fun getErrorStream(): InputStream = stderr

    override fun toHandle(): ProcessHandle = handle

    override fun waitFor(): Int {
      stdout.waitUntilReadCompletely()
      return exitCode
    }

    override fun exitValue(): Int {
      check(stdout.isReadCompletely())
      return exitCode
    }
  }

  private class CountDownByteArrayInputStream(data: ByteArray) :
    FilterInputStream(data.inputStream()) {

    private val latch = CountDownLatch(data.size)

    override fun read(): Int {
      val n = super.read()
      if (n >= 0) {
        latch.countDown()
      }
      return n
    }

    override fun read(b: ByteArray): Int {
      val n = super.read(b)
      latch.countDown(n)
      return n
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      val n = super.read(b, off, len)
      latch.countDown(n)
      return n
    }

    override fun skip(len: Long): Long {
      val n = super.skip(len)
      latch.countDown(n.toInt())
      return n
    }

    fun isReadCompletely(): Boolean = latch.count == 0L

    fun waitUntilReadCompletely() {
      latch.await()
    }
  }

  private data class LogEntry(
    val avd: AvdInfo,
    val severity: Severity,
    val notifyUser: Boolean,
    val message: String,
  )
}

private fun CountDownLatch.countDown(n: Int) {
  for (i in 0 until n) {
    countDown()
  }
}
