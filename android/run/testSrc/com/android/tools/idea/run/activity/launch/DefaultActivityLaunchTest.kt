/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.activity.launch

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceSelector
import com.android.adblib.serialNumber
import com.android.adblib.testing.FakeAdbSession
import com.android.ddmlib.IDevice
import com.android.tools.deployer.model.App
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.configuration.execution.createApp
import com.android.tools.idea.run.configuration.execution.createConnectedDevice
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.AndroidTestCase
import org.mockito.kotlin.mock

class DefaultActivityLaunchTest : AndroidTestCase() {
  lateinit var apk: String
  lateinit var state: DefaultActivityLaunch.State
  lateinit var device: IDevice
  lateinit var app: App
  lateinit var stats: RunStats
  lateinit var fakeSession: FakeAdbSession
  lateinit var connectedDevice: ConnectedDevice

  override fun setUp() {
    super.setUp()
    StudioFlags.DEPLOYER_USE_CONNECTED_DEVICE.overrideForTest(true, testRootDisposable)
    fakeSession = FakeAdbSession()
    connectedDevice = fakeSession.createConnectedDevice("1234", 31)
    // apkWithDefaultActivity.apk contains simple project with basic activity `com.example.myapplication.MainActivity`.
    apk = "${myFixture.testDataPath}/configurations/activity/apkWithDefaultActivity.apk"
    state = DefaultActivityLaunch.State()
    device = mock<IDevice>()
    app = createApp("com.example.myapplication", emptyList(), ArrayList(setOf("com.example.myapplication.MainActivity")))
    stats = RunStats(myFixture.project)
  }

  override fun tearDown() {
    try {
      runBlocking { fakeSession.closeAndJoin() }
    } finally {
      super.tearDown()
    }
  }

  fun testLaunch() {
    // Prepare
    val command =
      "am start -n com.example.myapplication/com.example.myapplication.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
    fakeSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(connectedDevice.serialNumber), command, "")

    // Act
    state.launch(device, connectedDevice, app, TestApksProvider(apk, "com.example.myapplication"), false, "", EmptyTestConsoleView(), stats)

    // Assert
    assertContentEquals(listOf(command), fakeSession.deviceServices.shellRequests.map { it.command })
  }

  fun testLaunchWithMultipleApks() {
    // Prepare
    val command =
      "am start -n com.example.myapplication/com.example.myapplication.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
    fakeSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(connectedDevice.serialNumber), command, "")

    val multiApkProvider =
      TestApksProvider(
        listOf(
          ApkInfo(File("non-existent-apk-irrelevant-for-test"), "com.example.other.application"),
          ApkInfo(File(apk), "com.example.myapplication"),
        )
      )

    // Act
    state.launch(device, connectedDevice, app, multiApkProvider, false, "", EmptyTestConsoleView(), stats)

    // Assert
    assertContentEquals(listOf(command), fakeSession.deviceServices.shellRequests.map { it.command })
  }

  fun testLaunchWithNoMatchingApks() {
    // Prepare
    val multiApkProvider =
      TestApksProvider(
        listOf(
          ApkInfo(File("non-existent-apk-1-irrelevant-for-test"), "com.example.other.application1"),
          ApkInfo(File("non-existent-apk-2-irrelevant-for-test"), "com.example.other.application2"),
        )
      )

    // Act
    val exception =
      assertFailsWith<IllegalStateException> {
        state.launch(device, connectedDevice, app, multiApkProvider, false, "", EmptyTestConsoleView(), stats)
      }

    // Assert
    assertThat(exception.message).isEqualTo("No matching APK for application: com.example.myapplication\n")
  }

  fun testLaunchWithMultipleMatchingApks() {
    // Prepare
    val multiApkProvider =
      TestApksProvider(
        listOf(
          ApkInfo(File("non-existent-apk-1-irrelevant-for-test"), "com.example.myapplication"),
          ApkInfo(File("non-existent-apk-2-irrelevant-for-test"), "com.example.myapplication"),
        )
      )

    // Act
    val exception =
      assertFailsWith<IllegalStateException> {
        state.launch(device, connectedDevice, app, multiApkProvider, false, "", EmptyTestConsoleView(), stats)
      }

    // Assert
    assertThat(exception.message)
      .isEqualTo(
        """Multiple APKs present for application: com.example.myapplication
Projects:
  com.example.myapplication containing :
    non-existent-apk-1-irrelevant-for-test
  com.example.myapplication containing :
    non-existent-apk-2-irrelevant-for-test
"""
      )
  }

  fun testLaunchWithNoApks() {
    // Prepare
    val emptyApkProvider = TestApksProvider(emptyList())

    // Act
    val exception =
      assertFailsWith<AndroidExecutionException> {
        state.launch(device, connectedDevice, app, emptyApkProvider, false, "", EmptyTestConsoleView(), stats)
      }

    // Assert
    assertThat(exception.message).isEqualTo("No APKs provided. Unable to extract default activity")
  }

  private class TestApksProvider(private val apks: Collection<ApkInfo>) : ApkProvider {
    constructor(apkFile: String, appId: String) : this(listOf(ApkInfo(File(apkFile), appId)))

    override fun getApks(device: IDevice) = apks
  }
}
