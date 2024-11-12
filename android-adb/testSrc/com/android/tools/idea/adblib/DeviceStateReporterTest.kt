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

import com.android.adblib.AdbUsageTracker
import com.android.adblib.AdbUsageTracker.DeviceState
import com.android.adblib.DeviceAddress
import com.android.adblib.DevicePropertyNames
import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.tools.idea.adblib.testing.TestAdbLibService
import com.android.tools.idea.testing.ProjectServiceRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeviceStateReporterTest {
  private val projectRule = ProjectRule()

  private val adbSession = FakeAdbSession()

  private val adbLibServiceRule =
    ProjectServiceRule(projectRule, AdbLibService::class.java, TestAdbLibService(adbSession))

  @get:Rule val rule = RuleChain(projectRule, adbLibServiceRule)

  private val deviceSerial = "device1"
  private lateinit var deviceStateReporter: DeviceStateReporter

  @Before
  fun setup() = runBlockingWithTimeout {
    // "getprop" is used to retrieve a device model
    adbSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(deviceSerial),
      mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Pix3l"),
    )

    deviceStateReporter = DeviceStateReporter()
    deviceStateReporter.execute(projectRule.project)

    adbSession.hostServices.connect(DeviceAddress(deviceSerial))
  }

  @Test
  fun reportsStatus() = runBlockingWithTimeout {
    // Prepare
    adbSession.host.timeProvider.pause()

    // Act
    yieldUntil { getLoggedAdbUsageEvents().isNotEmpty() }
    val adbEvent = getLoggedAdbUsageEvents()[0]

    // Assert: device goes online
    assertEquals(DeviceState.ONLINE, adbEvent.adbDeviceStateChange!!.deviceState)
    assertNull(adbEvent.adbDeviceStateChange!!.previousDeviceState)
    assertNull(adbEvent.adbDeviceStateChange!!.lastOnlineMs)

    // Act: let the device stay online for 50ms and disconnect
    adbSession.host.timeProvider.advance(50L, TimeUnit.MILLISECONDS)
    adbSession.hostServices.disconnect(DeviceAddress(deviceSerial))
    yieldUntil { getLoggedAdbUsageEvents().size == 2 }
    val adbEvent2 = getLoggedAdbUsageEvents()[1]

    // Assert: device gets disconnected (ONLINE->DISCONNECTED)
    assertEquals(DeviceState.DISCONNECTED, adbEvent2.adbDeviceStateChange!!.deviceState)
    assertEquals(DeviceState.ONLINE, adbEvent2.adbDeviceStateChange!!.previousDeviceState)
    assertEquals(0L, adbEvent2.adbDeviceStateChange!!.lastOnlineMs)

    // Act: Re-attach after the device stayed offline for another 125ms
    // Note that `adbRule.attachDevice` goes through an OFFLINE state very quickly, and it mostly,
    // though not always, isn't registered by a `ConnectedDevice.deviceInfoFlow` which is a
    // `StateFlow`.
    adbSession.host.timeProvider.advance(125L, TimeUnit.MILLISECONDS)
    adbSession.hostServices.connect(DeviceAddress(deviceSerial))
    yieldUntil { getLoggedAdbUsageEvents().size >= 3 }
    val adbEvent3 = getLoggedAdbUsageEvents()[2]

    // Assert (DISCONNECTED->ONLINE or DISCONNECTED->OFFLINE->ONLINE)
    assertTrue(
      adbEvent3.adbDeviceStateChange!!.deviceState == DeviceState.ONLINE ||
        adbEvent3.adbDeviceStateChange!!.deviceState == DeviceState.OFFLINE
    )
    assertEquals(DeviceState.DISCONNECTED, adbEvent3.adbDeviceStateChange!!.previousDeviceState)
    assertEquals(125L, adbEvent3.adbDeviceStateChange!!.lastOnlineMs)
  }

  @Test
  fun containsDeviceInfoEvenForDisconnectedDevice() = runBlockingWithTimeout {
    // Act
    yieldUntil { getLoggedAdbUsageEvents().isNotEmpty() }
    val adbEvent = getLoggedAdbUsageEvents()[0]

    // Assert
    assertEquals(DeviceState.ONLINE, adbEvent.adbDeviceStateChange!!.deviceState)
    assertEquals("Pix3l", adbEvent.deviceInfo!!.model)

    // Act
    adbSession.hostServices.disconnect(DeviceAddress(deviceSerial))
    yieldUntil { getLoggedAdbUsageEvents().size == 2 }
    val adbEvent2 = getLoggedAdbUsageEvents()[1]

    // Assert
    assertEquals(DeviceState.DISCONNECTED, adbEvent2.adbDeviceStateChange!!.deviceState)
    assertEquals("Pix3l", adbEvent2.deviceInfo!!.model)
  }

  private fun getLoggedAdbUsageEvents(): List<AdbUsageTracker.Event> {
    return adbSession.host.usageTracker.getLoggedEvents()
  }
}
