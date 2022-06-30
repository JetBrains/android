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
package com.android.tools.idea.logcat.devices

import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceState.AUTHORIZING
import com.android.adblib.DeviceState.OFFLINE
import com.android.adblib.DeviceState.ONLINE
import com.android.adblib.testing.FakeAdbLibSession
import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.android.tools.idea.logcat.devices.DeviceEvent.TrackingReset
import com.android.tools.idea.logcat.testing.TestDevice
import com.android.tools.idea.logcat.testing.sendDevices
import com.android.tools.idea.logcat.testing.setDevices
import com.android.tools.idea.logcat.testing.setupCommandsForDevice
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext


/**
 * Tests for [DeviceComboBoxDeviceTracker]
 */
@Suppress("OPT_IN_USAGE") // runBlockingTest is experimental
class DeviceComboBoxDeviceTrackerTest {
  @get:Rule
  val projectRule = ProjectRule()

  private val adbSession = FakeAdbLibSession()
  private val hostServices = adbSession.hostServices
  private val deviceServices = adbSession.deviceServices

  private val device1 = TestDevice("device-1", ONLINE, 11, 30, "manufacturer1", "model1", avdName = "")
  private val device2 = TestDevice("device-2", ONLINE, 12, 31, "manufacturer2", "model2", avdName = "")
  private val emulator1 = TestDevice("emulator-1", ONLINE, 11, 30, manufacturer = "", model = "", avdName = "avd1")

  @Before
  fun setUp() {
    deviceServices.setupCommandsForDevice(device1)
    deviceServices.setupCommandsForDevice(device2)
    deviceServices.setupCommandsForDevice(emulator1)
    deviceServices.setupCommandsForDevice(emulator1.withSerialNumber("emulator-2"))
  }

  @Test
  fun initialDevices(): Unit = runBlockingTest {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)
    hostServices.setDevices(device1, emulator1)

    val events = async { deviceTracker.trackDevices().toList() }
    hostServices.closeTrackDevicesFlow()

    assertThat(events.await()).containsExactly(
      Added(device1.device),
      Added(emulator1.device),
    ).inOrder()
  }

  @Test
  fun emulatorWithLegacyAvdName(): Unit = runBlockingTest {
    val emulator =
      TestDevice("emulator-3", ONLINE, 10, 29, manufacturer = "", model = "", avdName = "", avdNamePre31 = "avd3")
    adbSession.deviceServices.setupCommandsForDevice(emulator)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = async { deviceTracker.trackDevices().toList() }

    hostServices.use {
      it.sendDevices(emulator)
      it.sendDevices(emulator.withState(OFFLINE))
      it.sendDevices()
    }

    assertThat(events.await()).containsExactly(
      Added(Device.createEmulator(emulator.serialNumber, true, emulator.release, emulator.sdk, emulator.avdNamePre31)),
      StateChanged(Device.createEmulator(emulator.serialNumber, false, emulator.release, emulator.sdk, emulator.avdNamePre31)),
    ).inOrder()
  }

  @Test
  fun emulatorWithoutAvdProperty(): Unit = runBlockingTest {
    val emulator =
      TestDevice("emulator-3", ONLINE, 10, 29, manufacturer = "", model = "", avdName = "", avdNamePre31 = "")
    adbSession.deviceServices.setupCommandsForDevice(emulator)

    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = async { deviceTracker.trackDevices().toList() }

    hostServices.use {
      it.sendDevices(emulator)
      it.sendDevices(emulator.withState(OFFLINE))
      it.sendDevices()
    }

    assertThat(events.await()).containsExactly(
      Added(Device.createEmulator(emulator.serialNumber, true, emulator.release, emulator.sdk, emulator.serialNumber)),
      StateChanged(Device.createEmulator(emulator.serialNumber, false, emulator.release, emulator.sdk, emulator.serialNumber)),
    ).inOrder()
  }

  @Test
  fun initialDevices_ignoresOffline(): Unit = runBlockingTest {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)
    hostServices.setDevices(device1, emulator1.withState(AUTHORIZING))

    val events = async { deviceTracker.trackDevices().toList() }
    hostServices.closeTrackDevicesFlow()

    assertThat(events.await()).containsExactly(
      Added(device1.device),
    ).inOrder()
  }

  @Test
  fun initialDevices_withPreexistingDevice(): Unit = runBlockingTest {
    val emulator1Offline = emulator1.withState(OFFLINE)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession, preexistingDevice = emulator1Offline.device)
    hostServices.setDevices(device1)

    val events = async { deviceTracker.trackDevices().toList() }
    hostServices.closeTrackDevicesFlow()

    assertThat(events.await()).containsExactly(
      Added(device1.device),
      Added(emulator1Offline.device),
    ).inOrder()
  }

  @Test
  fun initialDevices_withInitialPreexistingDevice(): Unit = runBlockingTest {
    val preexistingEmulator = emulator1.withState(OFFLINE).withSerialNumber("")
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession, preexistingDevice = preexistingEmulator.device)
    hostServices.setDevices(emulator1, device1)

    val events = async { deviceTracker.trackDevices().toList() }
    hostServices.closeTrackDevicesFlow()

    assertThat(events.await()).containsExactly(
      Added(emulator1.device),
      Added(device1.device),
    ).inOrder()
  }

  @Test
  fun deviceAdded(): Unit = runBlockingTest {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = async { deviceTracker.trackDevices().toList() }

    hostServices.use {
      it.sendDevices(device1, device2)
      it.sendDevices(device1, device2, emulator1)
    }

    assertThat(events.await()).containsExactly(
      Added(device1.device),
      Added(device2.device),
      Added(emulator1.device),
    ).inOrder()
  }

  @Test
  fun deviceAdded_ignoreOffline(): Unit = runBlockingTest {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = async { deviceTracker.trackDevices().toList() }

    hostServices.use {
      it.sendDevices(device1, device2.withState(OFFLINE))
    }

    assertThat(events.await()).containsExactly(
      Added(device1.device),
    ).inOrder()
  }

  @Test
  fun deviceAdded_ignoreIfAlreadyAdded(): Unit = runBlockingTest {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = async { deviceTracker.trackDevices().toList() }

    hostServices.use {
      it.sendDevices(device1)
      it.sendDevices(device1)
    }

    assertThat(events.await()).containsExactly(
      Added(device1.device),
    ).inOrder()
  }

  @Test
  fun changeState_goesOffline(): Unit = runBlockingTest {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = async { deviceTracker.trackDevices().toList() }

    hostServices.use {
      it.sendDevices(device1)
      it.sendDevices(device1.withState(OFFLINE))
    }

    assertThat(events.await()).containsExactly(
      Added(device1.device.copy(isOnline = true)),
      StateChanged(device1.device.copy(isOnline = false)),
    ).inOrder()
  }

  @Test
  fun changeState_ignoreSameState(): Unit = runBlockingTest {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = async { deviceTracker.trackDevices().toList() }

    hostServices.use {
      it.sendDevices(device1.withState(ONLINE))
      it.sendDevices(device1.withState(ONLINE))
      it.sendDevices(device1.withState(OFFLINE))
      it.sendDevices(device1.withState(OFFLINE))
    }

    assertThat(events.await()).containsExactly(
      Added(device1.device),
      StateChanged(device1.device.copy(isOnline = false)),
    ).inOrder()
  }

  @Test
  fun changeState_goesOfflineComesOnline(): Unit = runBlockingTest {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = async { deviceTracker.trackDevices().toList() }

    hostServices.use {
      it.sendDevices(device1.withState(ONLINE))
      it.sendDevices(device1.withState(OFFLINE))
      it.sendDevices()
      it.sendDevices(device1.withState(ONLINE))
    }

    assertThat(events.await()).containsExactly(
      Added(device1.device.copy(isOnline = true)),
      StateChanged(device1.device.copy(isOnline = false)),
      StateChanged(device1.device.copy(isOnline = true)),
    ).inOrder()
  }

  @Test
  fun changeState_emulatorComesOnlineWithDifferentSerialNumber(): Unit = runBlockingTest {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = async { deviceTracker.trackDevices().toList() }

    hostServices.use {
      it.sendDevices(emulator1.withState(ONLINE).withSerialNumber("emulator-1"))
      it.sendDevices(emulator1.withState(OFFLINE))
      it.sendDevices()
      it.sendDevices(emulator1.withState(ONLINE).withSerialNumber("emulator-2"))
    }

    assertThat(events.await()).containsExactly(
      Added(emulator1.device.copy(serialNumber = "emulator-1")),
      StateChanged(emulator1.device.copy(isOnline = false)),
      StateChanged(emulator1.device.copy(isOnline = true, serialNumber = "emulator-2")),
    ).inOrder()
  }

  @Test
  fun trackDevicesThrows(): Unit = runBlockingTest {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = async { deviceTracker.trackDevices().toList() }

    hostServices.sendDevices(device1)
    val ioException = IOException("error while tracking")
    hostServices.closeTrackDevicesFlow(-1, ioException)
    hostServices.sendDevices(device1)

    hostServices.close()
    assertThat(events.await()).containsExactly(
      Added(device1.device),
      TrackingReset(ioException),
      Added(device1.device),
    ).inOrder()
  }

  private fun deviceComboBoxDeviceTracker(
    preexistingDevice: Device? = null,
    adbSession: AdbLibSession = this@DeviceComboBoxDeviceTrackerTest.adbSession,
  ) = DeviceComboBoxDeviceTracker(projectRule.project, preexistingDevice, adbSession, EmptyCoroutineContext)
}

