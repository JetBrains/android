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

package com.android.tools.idea.adblib

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.ddmlib.testing.FakeAdbRule
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.adb.FakeAdbServiceRule
import com.google.wireless.android.sdk.stats.AdbUsageEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeviceStateReporterTest {
  @get:Rule val projectRule = ProjectRule()

  @get:Rule val adbRule = FakeAdbRule()

  @get:Rule val adbServiceRule = FakeAdbServiceRule(projectRule::project, adbRule)

  @get:Rule val usageTracker = UsageTrackerRule()

  private val deviceSerial = "device1"
  private lateinit var deviceStateReporter: DeviceStateReporter

  @Before
  fun setup() = runBlockingWithTimeout {
    adbRule.attachDevice(deviceSerial, "Google", "Pix3l", "versionX", "32")
    // Delay a little for the device to settle on an ONLINE state
    delay(50)

    deviceStateReporter = DeviceStateReporter()
    deviceStateReporter.execute(projectRule.project)
  }

  @Test
  fun reportsStatus() = runBlockingWithTimeout {
    // Act
    yieldUntil { getLoggedAdbUsageEvents().isNotEmpty() }
    val adbEvent = getLoggedAdbUsageEvents()[0].adbUsageEvent

    // Assert: device goes online
    assertEquals(
      AdbUsageEvent.AdbDeviceStateChangeEvent.DeviceState.ONLINE,
      adbEvent.deviceStateChangeEvent.deviceState,
    )
    assertFalse(adbEvent.deviceStateChangeEvent.hasPreviousDeviceState())
    assertFalse(adbEvent.deviceStateChangeEvent.hasLastOnlineMs())

    // Act
    adbRule.disconnectDevice(deviceSerial)
    yieldUntil { getLoggedAdbUsageEvents().size == 2 }
    val adbEvent2 = getLoggedAdbUsageEvents()[1].adbUsageEvent

    // Assert: device gets disconnected
    assertEquals(
      AdbUsageEvent.AdbDeviceStateChangeEvent.DeviceState.DISCONNECTED,
      adbEvent2.deviceStateChangeEvent.deviceState,
    )
    assertEquals(
      AdbUsageEvent.AdbDeviceStateChangeEvent.DeviceState.ONLINE,
      adbEvent2.deviceStateChangeEvent.previousDeviceState,
    )
    assertEquals(0, adbEvent2.deviceStateChangeEvent.lastOnlineMs)

    // Act: Re-attach
    // Note that `adbRule.attachDevice` goes through an OFFLINE state very quickly, and it mostly,
    // though not always, isn't registered by a `ConnectedDevice.deviceInfoFlow` which is a
    // `StateFlow`.
    // delay a little to make sure we register a time difference when going from online to online
    delay(100)
    adbRule.attachDevice(deviceSerial, "Google", "Pix3l", "versionX", "32")
    yieldUntil { getLoggedAdbUsageEvents().size >= 3 }
    val adbEvent3 = getLoggedAdbUsageEvents()[2].adbUsageEvent

    // Assert
    assertTrue(
      adbEvent3.deviceStateChangeEvent.deviceState ==
        AdbUsageEvent.AdbDeviceStateChangeEvent.DeviceState.ONLINE ||
        adbEvent3.deviceStateChangeEvent.deviceState ==
          AdbUsageEvent.AdbDeviceStateChangeEvent.DeviceState.OFFLINE
    )
    assertEquals(
      AdbUsageEvent.AdbDeviceStateChangeEvent.DeviceState.DISCONNECTED,
      adbEvent3.deviceStateChangeEvent.previousDeviceState,
    )
    assertTrue(adbEvent3.deviceStateChangeEvent.lastOnlineMs > 0)
  }

  @Test
  fun containsDeviceInfoEvenForDisconnectedDevice() = runBlockingWithTimeout {
    // Act
    yieldUntil { getLoggedAdbUsageEvents().isNotEmpty() }
    val studioEvent = getLoggedAdbUsageEvents()[0]

    // Assert
    assertEquals(
      AdbUsageEvent.AdbDeviceStateChangeEvent.DeviceState.ONLINE,
      studioEvent.adbUsageEvent.deviceStateChangeEvent.deviceState,
    )
    assertEquals("Pix3l", studioEvent.deviceInfo.model)

    // Act
    adbRule.disconnectDevice(deviceSerial)
    yieldUntil { getLoggedAdbUsageEvents().size == 2 }
    val studioEvent2 = getLoggedAdbUsageEvents()[1]

    // Assert
    assertEquals(
      AdbUsageEvent.AdbDeviceStateChangeEvent.DeviceState.DISCONNECTED,
      studioEvent2.adbUsageEvent.deviceStateChangeEvent.deviceState,
    )
    assertEquals("Pix3l", studioEvent2.deviceInfo.model)
  }

  private fun getLoggedAdbUsageEvents(): List<AndroidStudioEvent> {
    return usageTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.ADB_USAGE_EVENT }
      .map { it.studioEvent }
      .toList()
  }
}
