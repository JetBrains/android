/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.screenrecording

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.DeviceState.HostConnectionType
import com.android.fakeadbserver.shellcommandhandlers.ScreenRecordCommandHandler
import com.android.sdklib.AndroidApiLevel
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.testing.disposable
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import org.junit.Rule
import org.junit.Test

/** Tests for [ShellCommandRecordingProvider]. */
class ShellCommandRecordingProviderTest {

  private val projectRule = ProjectRule()
  private val fakeAdbRule = FakeAdbServerProviderRule { installDefaultCommandHandlers() }
  private val temporaryDirectoryRule = TemporaryDirectory()

  @get:Rule
  val ruleChain = RuleChain(projectRule, fakeAdbRule, temporaryDirectoryRule)

  private val fakeAdb get() =
      fakeAdbRule.fakeAdb
  private val deviceServices get() =
      fakeAdbRule.adbSession.deviceServices

  @Test
  fun testRecording(): Unit = runBlockingWithTimeout {
    // Prepare.
    val device = createFakeDevice()
    val options = ScreenRecorderOptions(
        displayId = PRIMARY_DISPLAY_ID, width = 600, height = 400, bitrateMbps = 6, showTouches = false, timeLimitSec = 300)
    val provider = ShellCommandRecordingProvider(projectRule.disposable, device.deviceId, RECODING_FILE, options, fakeAdbRule.adbSession)

    // Act.
    val done = provider.startRecording()
    // Wait until the recording file is created on the device.
    yieldUntil { device.getFile(RECODING_FILE) != null || done.isCompleted }
    // Stop the screen recording operation and wait for it to finish.
    provider.stopRecording()
    done.await()
    val file = temporaryDirectoryRule.newPath("screen-recording.mp4")
    provider.pullRecording(file)

    // Assert.
    assertThat(file).hasContents(ScreenRecordCommandHandler.FINISHED_RECORDING_CONTENTS)
  }

  private suspend fun createFakeDevice(): DeviceState {
    val device = fakeAdb.connectDevice("1234", "Google", "Pixel 9", "Baklava", AndroidApiLevel(36), HostConnectionType.USB)
    device.deviceStatus = DeviceState.DeviceStatus.ONLINE
    deviceServices.session.waitForOnlineConnectedDevice(device.deviceId)
    return device
  }
}

private const val RECODING_FILE = "/sdcard/screen-recording.mp4"
