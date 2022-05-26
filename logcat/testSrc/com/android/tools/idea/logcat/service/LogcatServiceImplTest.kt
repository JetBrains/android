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
import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbDeviceServices.ShellRequest
import com.android.adblib.testing.FakeAdbLibSession
import com.android.ddmlib.Log.LogLevel.DEBUG
import com.android.ddmlib.Log.LogLevel.INFO
import com.android.ddmlib.logcat.LogCatMessage
import com.android.testutils.TestResources
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.adb.processnamemonitor.testing.FakeProcessNameMonitor
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.logCatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * Tests for [LogcatServiceImpl]
 */
@Suppress("OPT_IN_USAGE") // runBlockingTest is experimental
class LogcatServiceImplTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule)

  private val device30 = Device.createPhysical("device", isOnline = true, release = 10, sdk = 30, manufacturer = "Google", model = "Pixel")
  private val device23 = Device.createPhysical("device", isOnline = true, release = 7, sdk = 23, manufacturer = "Google", model = "Pixel")

  private val fakeDeviceServices = FakeAdbLibSession().deviceServices
  private val fakeProcessNameMonitor = FakeProcessNameMonitor()

  @Before
  fun setUp() {
    fakeProcessNameMonitor.addProcessName("device", 1, "app-1.1", "process-1.1")
  }

  @Test
  fun readLogcat_launchesLogcat() = runBlockingTest {
    val service = logcatServiceImpl(deviceServicesFactory = { fakeDeviceServices })

    try {
      service.readLogcat(device30).collect { }
    }
    catch (e: IllegalStateException) {
      // Ignore if not configured
    }

    assertThat(fakeDeviceServices.shellRequests).containsExactly(
      ShellRequest("serial-device", "logcat -v long -v epoch")
    )
  }

  @Test
  fun readLogcat_oldDevice_launchesLogcat() = runBlockingTest {
    val service = logcatServiceImpl(deviceServicesFactory = { fakeDeviceServices })

    try {
      service.readLogcat(device23).collect { }
    }
    catch (e: IllegalStateException) {
      // Ignore if not configured
    }

    assertThat(fakeDeviceServices.shellRequests).containsExactly(
      ShellRequest("serial-device", "logcat -v long")
    )
  }

  @Test
  fun readLogcat() = runBlockingTest {
    val logcat = TestResources.getFile("/logcatFiles/real-logcat-from-device.txt").readText()
    fakeDeviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(device30.serialNumber), "logcat -v long -v epoch", logcat)
    val service = logcatServiceImpl(deviceServicesFactory = { fakeDeviceServices }, processNameMonitor = fakeProcessNameMonitor)

    val actualLines = service.readLogcat(device30).toList().flatten().joinToString("\n") { it.toString() }.split('\n')
    val expectedLines = TestResources.getFile("/logcatFiles/real-logcat-from-device-expected.txt").readLines()
    assertThat(actualLines).hasSize(expectedLines.size)
    actualLines.zip(expectedLines).forEachIndexed { index, (actual, expected) ->
      assertThat(actual).named("Line $index").isEqualTo(expected)
    }
  }

  @Test
  fun readLogcat_containsError() = runBlockingTest {
    val logcat = """
      [          1650711610.619  1: 1000 D/Tag  ]
      A message
      [          1650711610.700  1: 1000 I/Tag  ]
      Last message

      Error message

      More error information
    """.trimIndent()
    fakeDeviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(device30.serialNumber), "logcat -v long -v epoch", logcat)
    val service = logcatServiceImpl(deviceServicesFactory = { fakeDeviceServices }, processNameMonitor = fakeProcessNameMonitor)

    val messages = service.readLogcat(device30).toList().flatten()

    assertThat(messages).containsExactly(
      logCatMessage(DEBUG, 1, 1000, "app-1.1", "Tag", Instant.ofEpochSecond(1650711610, MILLISECONDS.toNanos(619)), "A message"),
      logCatMessage(INFO, 1, 1000, "app-1.1", "Tag", Instant.ofEpochSecond(1650711610, MILLISECONDS.toNanos(700)), "Last message"),
      LogCatMessage(SYSTEM_HEADER, "Error message\n\nMore error information"),
    )
  }

  @Test
  fun clearLogcat_launchesLogcat() = runBlockingTest {
    val service = logcatServiceImpl(deviceServicesFactory = { fakeDeviceServices })

    try {
      service.clearLogcat(device30)
    }
    catch (e: IllegalStateException) {
      // Ignore if not configured
    }

    assertThat(fakeDeviceServices.shellRequests).containsExactly(
      ShellRequest("serial-device", "logcat -c", Duration.ofSeconds(2))
    )
  }

  private fun logcatServiceImpl(
    deviceServicesFactory: () -> AdbDeviceServices = { fakeDeviceServices },
    processNameMonitor: ProcessNameMonitor = fakeProcessNameMonitor,
  ): LogcatServiceImpl =
    LogcatServiceImpl(projectRule.project, deviceServicesFactory, processNameMonitor)
}
