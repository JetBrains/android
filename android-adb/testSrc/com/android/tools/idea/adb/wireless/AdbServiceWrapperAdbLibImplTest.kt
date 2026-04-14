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
package com.android.tools.idea.adb.wireless

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.AndroidApiLevel
import com.android.tools.adblib.testutils.FakeAdbServerAdbLibRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class AdbServiceWrapperAdbLibImplTest {
  private val projectRule = ProjectRule()

  private val adbRule = FakeAdbServerAdbLibRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(adbRule)

  private val project
    get() = projectRule.project

  @Test
  fun waitForOnlineDeviceShouldWork() = runBlockingWithTimeout {
    // Prepare
    val adbService = AdbServiceWrapperAdbLibImpl(project)
    val deviceId = "test-device-id"
    val pairingResult = PairingResult(InetAddress.getByName("127.0.0.1"), 1234, deviceId)

    // Act: Start waiting in a background coroutine
    val deferred = async { adbService.waitForOnlineDevice(pairingResult) }

    // Give it a bit of time to start tracking
    delay(100.milliseconds)

    // Simulate device coming online
    adbRule.connectDevice(deviceId, "Google", "Pixel 10", "testRelease", AndroidApiLevel(36), DeviceState.HostConnectionType.USB)

    // Assert
    val onlineDevice = deferred.await()
    assertThat(onlineDevice.id).isEqualTo(deviceId)
  }
}
