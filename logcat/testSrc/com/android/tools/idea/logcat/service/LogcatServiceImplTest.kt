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

import com.android.adblib.AdbDeviceServices
import com.android.adblib.DeviceState.ONLINE
import com.android.adblib.ddmlibcompatibility.testutils.createAdbSession
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.shellcommandhandlers.LogcatCommandHandler
import com.android.testutils.TestResources
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.adb.processnamemonitor.testing.FakeProcessNameMonitor
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.logcatMessage
import com.android.tools.idea.logcat.message.LogLevel.DEBUG
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.testing.TestDevice
import com.android.tools.idea.logcat.testing.attachDevice
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.flow.collect
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
  private val fakeAdb = FakeAdbRule()
  private val closeables = CloseablesRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(fakeAdb, closeables, disposableRule)

  private val device30 = TestDevice("device", ONLINE, release = "10", sdk = 30, manufacturer = "Google", model = "Pixel")
  private val device23 = TestDevice("device", ONLINE, release = "7", sdk = 23, manufacturer = "Google", model = "Pixel")

  private val fakeDeviceServices = FakeAdbSession().deviceServices
  private val fakeProcessNameMonitor = FakeProcessNameMonitor()

  @Before
  fun setUp() {
    fakeProcessNameMonitor.addProcessName("device", 1, "app-1.1", "process-1.1")
  }

  @Test
  fun readLogcat_launchesLogcat_sdk30(): Unit = runBlocking {
    val device = device30
    val service = logcatServiceImpl(deviceServicesFactory = { fakeAdb.createAdbSession(closeables).deviceServices })
    val logcatHandler = CheckFormatLogcatHandler()
    fakeAdb.addDeviceCommandHandler(logcatHandler)
    fakeAdb.attachDevice(device)

    val job = launch {
      service.readLogcat(device.device).collect {}
    }
    yieldUntil { logcatHandler.lastDeviceId == device.serialNumber }
    job.cancel()

    assertThat(logcatHandler.lastArgs).isEqualTo("-v long -v epoch")
  }

  @Test
  fun readLogcat_launchesLogcat_sdk23() = runBlocking {
    val device = device23
    val service = logcatServiceImpl(deviceServicesFactory = { fakeAdb.createAdbSession(closeables).deviceServices })
    val logcatHandler = CheckFormatLogcatHandler()
    fakeAdb.addDeviceCommandHandler(logcatHandler)
    fakeAdb.attachDevice(device)

    val job = launch {
      try {
        service.readLogcat(device.device).collect {}
      }
      catch (e: EOFException) {
        // We sometimes (~1%) get an EOFException when the ADB Server terminates
      }
    }
    yieldUntil { logcatHandler.lastDeviceId == device.serialNumber }
    job.cancel()

    assertThat(logcatHandler.lastArgs).isEqualTo("-v long")
  }

  /**
   * Test a large file with numbered Logcat messages so if there's a bug, the numbers can help debug it.
   */
  @Test
  fun readLogcat_50000SimpleLines() = runBlocking {
    val logcat = TestResources.getFile("/logcatFiles/logcat-50000.txt").readText()
    val service = logcatServiceImpl(
      deviceServicesFactory = { fakeAdb.createAdbSession(closeables).deviceServices },
      processNameMonitor = fakeProcessNameMonitor)
    val deviceState = fakeAdb.attachDevice(device30)
    // Break up the logcat into chunks to put more pressure of the code that collects them.
    logcat.chunked(10000).forEach {
      deviceState.addLogcatMessage(it)
    }
    deviceState.addLogcatMessage(LAST_MESSAGE)

    val messages = mutableListOf<LogcatMessage>()
    val job = launch {
      service.readLogcat(device30.device).collect {
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
   * Test a large file from an actual device. This is a more realistic test than readLogcat_50000SimpleLines, but it's harder to debug if
   * something goes wrong.
   */
  @Test
  fun readLogcat_actualLogcatFromDevice() = runBlocking {
    val logcat = TestResources.getFile("/logcatFiles/real-logcat-from-device.txt").readText()
    val service = logcatServiceImpl(
      deviceServicesFactory = { fakeAdb.createAdbSession(closeables).deviceServices },
      processNameMonitor = fakeProcessNameMonitor)
    val deviceState = fakeAdb.attachDevice(device30)
    // Break up the logcat into chunks to put more pressure of the code that collects them.
    logcat.chunked(10000).forEach {
      deviceState.addLogcatMessage(it)
    }
    deviceState.addLogcatMessage(LAST_MESSAGE)

    val messages = mutableListOf<LogcatMessage>()
    val job = launch {
      service.readLogcat(device30.device).collect {
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
    val service = LogcatServiceImpl(
      disposableRule.disposable,
      deviceServicesFactory = { fakeAdb.createAdbSession(closeables).deviceServices },
      processNameMonitor = fakeProcessNameMonitor,
      lastMessageDelayMs = SECONDS.toMillis(10),
    )
    val deviceState = fakeAdb.attachDevice(device30)
    deviceState.addLogcatMessage(logcat)

    val messages = mutableListOf<LogcatMessage>()
    val job = launch {
      service.readLogcat(device30.device).collect {
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
    val service = logcatServiceImpl(deviceServicesFactory = { fakeAdb.createAdbSession(closeables).deviceServices })
    val logcatHandler = CheckFormatLogcatHandler()
    fakeAdb.addDeviceCommandHandler(logcatHandler)
    fakeAdb.attachDevice(device)

    val job = launch {
      service.clearLogcat(device.device)
    }
    yieldUntil { logcatHandler.lastDeviceId == device.serialNumber }
    job.cancel()

    assertThat(logcatHandler.lastArgs).isEqualTo("-c")
  }

  private fun logcatServiceImpl(
    deviceServicesFactory: () -> AdbDeviceServices = { fakeDeviceServices },
    processNameMonitor: ProcessNameMonitor = fakeProcessNameMonitor,
  ): LogcatServiceImpl =
    LogcatServiceImpl(disposableRule.disposable, deviceServicesFactory, processNameMonitor)

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
