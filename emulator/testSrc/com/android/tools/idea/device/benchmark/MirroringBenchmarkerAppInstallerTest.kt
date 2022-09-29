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
package com.android.tools.idea.device.benchmark

import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbDeviceServices
import com.android.adblib.testing.FakeAdbSession
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val SERIAL_NUMBER = "abc123"

/** Tests the [MirroringBenchmarkerAppInstaller] class. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class MirroringBenchmarkerAppInstallerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()
  private val adb: FakeAdbDeviceServices = FakeAdbSession().deviceServices
  private val deviceSelector = DeviceSelector.fromSerialNumber(SERIAL_NUMBER)

  lateinit var installer: MirroringBenchmarkerAppInstaller
  @Before
  fun setUp() {
    installer = MirroringBenchmarkerAppInstaller(projectRule.project, SERIAL_NUMBER, adb)
  }

  @Test
  fun installationFails() = runBlockingTest {
    // We don't have a way to configure the directory for the project, so we can't test the case
    // where we currently have the benchmarking app open in Studio :(
    assertThat(installer.installBenchmarkingApp()).isFalse()
  }

  @Test
  fun launchBenchmarkingApp_success() = runBlockingTest {
    val command = "am start -n com.android.tools.screensharing.benchmark/.InputEventRenderingActivity -f 65536"
    adb.configureShellCommand(
      deviceSelector,
      command = command,
      stdout = "",
      exitCode = 0)
    assertThat(installer.launchBenchmarkingApp()).isTrue()
    assertThat(adb.shellV2Requests).hasSize(1)
    assertThat(adb.shellV2Requests.first.deviceSelector).isEqualTo(deviceSelector.toString())
    assertThat(adb.shellV2Requests.first.command).isEqualTo(command)
  }

  @Test
  fun launchBenchmarkingApp_failure() = runBlockingTest {
    val command = "am start -n com.android.tools.screensharing.benchmark/.InputEventRenderingActivity -f 65536"
    adb.configureShellCommand(
      deviceSelector,
      command = command,
      stdout = "",
      exitCode = 42)
    assertThat(installer.launchBenchmarkingApp()).isFalse()
    assertThat(adb.shellV2Requests).hasSize(1)
    assertThat(adb.shellV2Requests.first.deviceSelector).isEqualTo(deviceSelector.toString())
    assertThat(adb.shellV2Requests.first.command).isEqualTo(command)
  }

  @Test
  fun uninstallBenchmarkingApp() = runBlockingTest {
    val options = "" // Necessary because this is what AdbDeviceServices.uninstall does - there's an extra space.
    val command =  "pm uninstall $options com.android.tools.screensharing.benchmark"
    adb.configureShellCommand(deviceSelector, command = command, stdout = "")
    installer.uninstallBenchmarkingApp()
    assertThat(adb.shellRequests).hasSize(1)
    assertThat(adb.shellRequests.first.deviceSelector).isEqualTo(deviceSelector.toString())
    assertThat(adb.shellRequests.first.command).isEqualTo(command)
  }
}
