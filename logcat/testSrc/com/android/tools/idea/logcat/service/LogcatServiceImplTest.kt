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
package com.android.tools.idea.logcat.service

import com.android.adblib.AdbSession
import com.android.adblib.ddmlibcompatibility.testutils.createAdbSession
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.shellcommandhandlers.LogcatCommandHandler
import com.android.testutils.TestResources
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.adb.processnamemonitor.testing.FakeProcessNameMonitor
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.testing.TestAdbLibService
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.logcatMessage
import com.android.tools.idea.logcat.message.LogLevel.DEBUG
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogcatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.EOFException
import java.net.Socket
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * A Logcat message that's sent as the last message to a device so that we can wait for it before terminating FakeAdbRule
 */
private const val LAST_MESSAGE_TAG = "LastMessage"
private val LAST_MESSAGE = """
      [          1650918000.052  1940: 1940 D/$LAST_MESSAGE_TAG ]
      Last message


    """.trimIndent()

/**
 * Tests for [LogcatServiceImpl]
 */
class LogcatServiceImplTest {
  private val projectRule = ProjectRule()
  private val fakeAdb = FakeAdbRule()
  private val closeables = CloseablesRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(projectRule, fakeAdb, closeables, disposableRule)

  private val project get() = projectRule.project
  private val disposable get() = disposableRule.disposable

  private val device30 = Device.createPhysical("device", true, "10", 30, "Google", "Pixel")
  private val device23 = Device.createPhysical("device", true, "7", 23, "Google", "Pixel")

  private val fakeProcessNameMonitor = FakeProcessNameMonitor()

  @Before
  fun setUp() {
    fakeProcessNameMonitor.addProcessName("device", 1, "app-1.1", "process-1.1")
    project.registerOrReplaceServiceInstance(ProcessNameMonitor::class.java, fakeProcessNameMonitor, disposable)
  }

  @Test
  fun readLogcat_launchesLogcat_sdk30(): Unit = runBlocking {
    val device = device30
    val service = logcatServiceImpl()
    val logcatHandler = CheckFormatLogcatHandler()
    fakeAdb.addDeviceCommandHandler(logcatHandler)
    fakeAdb.attachDevice(device.serialNumber,  manufacturer = "", model = "", release = "", sdk = "")

    val job = launch {
      service.readLogcat(device).collect {}
    }
    yieldUntil { logcatHandler.lastDeviceId == device.serialNumber }
    job.cancel()

    assertThat(logcatHandler.lastArgs).isEqualTo("-v long -v epoch")
  }

  @Test
  fun readLogcat_launchesLogcat_sdk23() = runBlocking {
    val device = device23
    val service = logcatServiceImpl()
    val logcatHandler = CheckFormatLogcatHandler()
    fakeAdb.addDeviceCommandHandler(logcatHandler)
    fakeAdb.attachDevice(device.serialNumber,  manufacturer = "", model = "", release = "", sdk = "")

    val job = launch {
      try {
        service.readLogcat(device).collect {}
      }
      catch (e: EOFException) {
        // We sometimes (~1%) get an EOFException when the ADB Server terminates
      }
    }
    yieldUntil { logcatHandler.lastDeviceId == device.serialNumber }
    job.cancel()

    assertThat(logcatHandler.lastArgs).isEqualTo("-v long")
  }

  @Test
  fun readLogcat_newMessagesOnly_launchesLogcat_sdk21(): Unit = runBlocking {
    val service = logcatServiceImpl()
    val logcatHandler = CheckFormatLogcatHandler()
    fakeAdb.addDeviceCommandHandler(logcatHandler)
    fakeAdb.attachDevice("device",  manufacturer = "", model = "", release = "", sdk = "21")

    val job = launch {
      service.readLogcat("device", 21, newMessagesOnly = true).collect {}
    }
    yieldUntil { logcatHandler.lastDeviceId == "device" }
    job.cancel()

    assertThat(logcatHandler.lastArgs).isEqualTo("-v long -T 0")
  }

  /**
   * Test a large file with numbered Logcat messages so if there's a bug, the numbers can help debug it.
   */
  @Test
  fun readLogcat_50000SimpleLines() = runBlocking {
    val logcat = TestResources.getFile("/logcatFiles/logcat-50000.txt").readText()
    val service = logcatServiceImpl()
    val deviceState = fakeAdb.attachDevice(device30.serialNumber, manufacturer = "", model = "", release = "", sdk = "")
    // Break up the logcat into chunks to put more pressure of the code that collects them.
    logcat.chunked(10000).forEach {
      deviceState.addLogcatMessage(it)
    }
    deviceState.addLogcatMessage(LAST_MESSAGE)

    val messages = mutableListOf<LogcatMessage>()
    val job = launch {
      service.readLogcat(device30).collect {
        messages.addAll(it)
      }
    }
    yieldUntil(Duration.ofSeconds(10)) { messages.lastOrNull()?.header?.tag == LAST_MESSAGE_TAG }
    job.cancel()

    val actualLines = messages.dropLast(1).joinToString("\n") { it.toString() }.split('\n')
    val expectedLines = TestResources.getFile("/logcatFiles/logcat-50000-expected.txt").readLines()
    assertThat(actualLines).hasSize(expectedLines.size)
    actualLines.zip(expectedLines).forEachIndexed { index, (actual, expected) ->
      assertThat(actual).named("Line $index").isEqualTo(expected)
    }
  }

  /**
   * Test that calling with `duration` only blocks for the specified duration
   */
  @Suppress("OPT_IN_IS_NOT_ENABLED")
  @OptIn(ExperimentalTime::class)
  @Test
  fun readLogcat_withTimeout() = runBlocking {
    val service = logcatServiceImpl()
    val deviceState = fakeAdb.attachDevice(device30.serialNumber, manufacturer = "", model = "", release = "", sdk = "")
    deviceState.addLogcatMessage(rawLogcatMessage(Instant.EPOCH, "Message1"))

    val (messages, duration) = measureTimedValue { service.readLogcat(device30, duration = Duration.ofSeconds(1)).toList() }

    assertThat(messages).containsExactly(listOf(logcatMessage (DEBUG, 1, 1, "app-1.1", "process-1.1", "Tag", Instant.EPOCH, "Message1")))
    assertThat(duration.inWholeSeconds).isEqualTo(1)
  }

  /**
   * Test a large file from an actual device. This is a more realistic test than readLogcat_50000SimpleLines, but it's harder to debug if
   * something goes wrong.
   */
  @Test
  fun readLogcat_actualLogcatFromDevice() = runBlocking {
    val logcat = TestResources.getFile("/logcatFiles/real-logcat-from-device.txt").readText()
    val service = logcatServiceImpl()
    val deviceState = fakeAdb.attachDevice(device30.serialNumber, manufacturer = "", model = "", release = "", sdk = "")
    // Break up the logcat into chunks to put more pressure of the code that collects them.
    logcat.chunked(10000).forEach {
      deviceState.addLogcatMessage(it)
    }
    deviceState.addLogcatMessage(LAST_MESSAGE)

    val messages = mutableListOf<LogcatMessage>()
    val job = launch {
      service.readLogcat(device30).collect {
        messages.addAll(it)
      }
    }
    yieldUntil(Duration.ofSeconds(10)) { messages.lastOrNull()?.header?.tag == LAST_MESSAGE_TAG }
    job.cancel()

    val actualLines = messages.dropLast(1).joinToString("\n") { it.toString() }.split('\n')
    val expectedLines = TestResources.getFile("/logcatFiles/real-logcat-from-device-expected.txt").readLines()
    assertThat(actualLines).hasSize(expectedLines.size)
    actualLines.zip(expectedLines).forEachIndexed { index, (actual, expected) ->
      assertThat(actual).named("Line $index").isEqualTo(expected)
    }
  }

  /**
   * Tests an edge case where the Logcat process terminates with an error.
   *
   * This can happen under some extreme situations for example, if a device is spewing logs faster than the reader can handle.
   *
   * When this happens, the logcat process emits an error message not formatted as a Logcat message. [LogcatServiceImpl.readLogcat] detects
   * this case and emits the error as a special `System Message` marked by [SYSTEM_HEADER].
   */
  @Test
  fun readLogcat_containsError(): Unit = runBlocking {
    val logcat = """
      [          1650711610.619  1: 1000 D/Tag  ]
      A message
      [          1650711610.700  1: 1000 I/Tag  ]
      Last message

      Error message

      More error information
    """.trimIndent()
    // This test is flaky because the underlying code has a 100ms delay before consuming the last log entry from the server. If the server
    // takes a bit too long to terminate, the delay expires and the error message is consumed as a normal message rather than an error
    // message. We pass a longer delay to LogcatServiceImpl to prevent LogcatMessageAssembler from consuming the last message before the
    // server terminates.
    val service = logcatServiceImpl(
      lastMessageDelayMs = SECONDS.toMillis(10),
      fakeAdb.createAdbSession(closeables),
    )
    val deviceState = fakeAdb.attachDevice(device30.serialNumber, manufacturer = "", model = "", release = "", sdk = "")
    deviceState.addLogcatMessage(logcat)

    val messages = mutableListOf<LogcatMessage>()
    val job = launch {
      service.readLogcat(device30).collect {
        messages.addAll(it)
      }
    }
    yieldUntil(Duration.ofSeconds(10)) { messages.isNotEmpty() }

    // job.cancel() doesn't work here. We have to let the Fake server terminate so all the messages come through
    // There still seems to be a very small flakiness (<1%).
    fakeAdb.stop()
    job.join()

    assertThat(messages).containsExactly(
      logcatMessage(
        DEBUG, 1, 1000, "app-1.1", "process-1.1", "Tag", Instant.ofEpochSecond(1650711610, MILLISECONDS.toNanos(619)), "A message"),
      logcatMessage(
        INFO, 1, 1000, "app-1.1", "process-1.1", "Tag", Instant.ofEpochSecond(1650711610, MILLISECONDS.toNanos(700)), "Last message"),
      LogcatMessage(SYSTEM_HEADER, "Error message\n\nMore error information"),
    )
  }

  @Test
  fun clearLogcat_launchesLogcat() = runBlocking {
    val device = device30
    val service = logcatServiceImpl()
    val logcatHandler = CheckFormatLogcatHandler()
    fakeAdb.addDeviceCommandHandler(logcatHandler)
    fakeAdb.attachDevice(device.serialNumber, manufacturer = "", model = "", release = "", sdk = "")

    val job = launch {
      service.clearLogcat(device)
    }
    yieldUntil { logcatHandler.lastDeviceId == device.serialNumber }
    job.cancel()

    assertThat(logcatHandler.lastArgs).isEqualTo("-c")
  }

  private fun logcatServiceImpl(
    lastMessageDelayMs: Long = 100L,
    adbSession: AdbSession = fakeAdb.createAdbSession(closeables),
  ): LogcatServiceImpl {
    project.registerOrReplaceServiceInstance(AdbLibService::class.java, TestAdbLibService(adbSession), disposable)
    return LogcatServiceImpl(project, lastMessageDelayMs)
  }

  private class CheckFormatLogcatHandler : LogcatCommandHandler() {
    var lastDeviceId: String? = null
    var lastArgs: String? = null
    override fun execute(fakeAdbServer: FakeAdbServer, responseSocket: Socket, device: DeviceState, args: String?) {
      lastDeviceId = device.deviceId
      lastArgs = args
      super.execute(fakeAdbServer, responseSocket, device, args)
    }
  }
}

@Suppress("SameParameterValue")
private fun rawLogcatMessage(timestamp: Instant, message: String): String {
  return """
    [          ${timestamp.epochSecond}.000 1:1 D/Tag     ]
    $message

  """.trimIndent()
}