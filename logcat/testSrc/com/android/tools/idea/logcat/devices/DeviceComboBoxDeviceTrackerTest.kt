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
import com.android.tools.idea.logcat.testing.TestDevice
import com.android.tools.idea.logcat.testing.setupCommandsForDevice
import com.android.tools.idea.logcat.testing.setupInitialDevices
import com.android.tools.idea.logcat.testing.setupTrackingData
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test


/**
 * Tests for [DeviceComboBoxDeviceTracker]
 */
class DeviceComboBoxDeviceTrackerTest {
  @get:Rule
  val projectRule = ProjectRule()

  private val adbSession = FakeAdbLibSession()

  private val device1 = TestDevice("device-1", ONLINE, "release1", "sdk1", "manufacturer1", "model1", avdName = "")
  private val device2 = TestDevice("device-2", ONLINE, "release2", "sdk2", "manufacturer2", "model2", avdName = "")
  private val emulator1 = TestDevice("emulator-1", ONLINE, "release1", "sdk1", manufacturer = "", model = "", "avd1")

  @Before
  fun setUp() {
    adbSession.deviceServices.setupCommandsForDevice(device1)
    adbSession.deviceServices.setupCommandsForDevice(device2)
    adbSession.deviceServices.setupCommandsForDevice(emulator1)
    adbSession.deviceServices.setupCommandsForDevice(emulator1.withSerialNumber("emulator-2"))
  }

  @Test
  fun initialDevices(): Unit = runBlocking {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)
    adbSession.setupInitialDevices(device1, emulator1)

    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(device1.device),
      Added(emulator1.device),
    ).inOrder()
  }

  @Test
  fun initialDevices_ignoresOffline(): Unit = runBlocking {
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)
    adbSession.setupInitialDevices(device1, emulator1.withState(AUTHORIZING))

    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(device1.device),
    ).inOrder()
  }

  @Test
  fun initialDevices_withPreexistingDevice(): Unit = runBlocking {
    val emulator1Offline = emulator1.withState(OFFLINE)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession, preexistingDevice = emulator1Offline.device)
    adbSession.setupInitialDevices(device1)

    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(device1.device),
      Added(emulator1Offline.device),
    ).inOrder()
  }

  @Test
  fun initialDevices_withInitialPreexistingDevice(): Unit = runBlocking {
    val preexistingEmulator = emulator1.withState(OFFLINE).withSerialNumber("")
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession, preexistingDevice = preexistingEmulator.device)
    adbSession.setupInitialDevices(emulator1, device1)

    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(emulator1.device),
      Added(device1.device),
    ).inOrder()
  }

  @Test
  fun deviceAdded(): Unit = runBlocking {
    adbSession.setupTrackingData(
      listOf(device1, device2),
      listOf(emulator1),
    )
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(device1.device),
      Added(device2.device),
      Added(emulator1.device),
    ).inOrder()
  }

  @Test
  fun deviceAdded_ignoreOffline(): Unit = runBlocking {
    adbSession.setupTrackingData(
      listOf(device1, device2.withState(OFFLINE)),
    )
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(device1.device),
    ).inOrder()
  }

  @Test
  fun deviceAdded_ignoreIfAlreadyAdded(): Unit = runBlocking {
    adbSession.setupTrackingData(
      listOf(device1),
      listOf(device1),
    )
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(device1.device),
    ).inOrder()
  }

  @Test
  fun changeState_goesOffline(): Unit = runBlocking {
    adbSession.setupTrackingData(
      listOf(device1),
      listOf(device1.withState(OFFLINE)),
    )
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(device1.device.copy(isOnline = true)),
      StateChanged(device1.device.copy(isOnline = false)),
    ).inOrder()
  }

  @Test
  fun changeState_ignoreSameState(): Unit = runBlocking {
    adbSession.setupTrackingData(
      listOf(device1.withState(ONLINE)),
      listOf(device1.withState(ONLINE)),
      listOf(device1.withState(OFFLINE)),
      listOf(device1.withState(OFFLINE)),
    )
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)


    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(device1.device),
      StateChanged(device1.device.copy(isOnline = false)),
    ).inOrder()
  }

  @Test
  fun changeState_goesOfflineComesOnline(): Unit = runBlocking {
    adbSession.setupTrackingData(
      listOf(device1),
      listOf(device1.withState(OFFLINE)),
      listOf(device1.withState(ONLINE)),
    )
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(device1.device.copy(isOnline = true)),
      StateChanged(device1.device.copy(isOnline = false)),
      StateChanged(device1.device.copy(isOnline = true)),
    ).inOrder()
  }

  @Test
  fun changeState_emulatorComesOnlineWithDifferentSerialNumber(): Unit = runBlocking {
    adbSession.setupTrackingData(
      listOf(emulator1.withState(ONLINE).withSerialNumber("emulator-1")),
      listOf(emulator1.withState(OFFLINE)),
      listOf(emulator1.withState(ONLINE).withSerialNumber("emulator-2")),
    )
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = adbSession)

    val events = deviceTracker.trackDevices().toList()

    assertThat(events).containsExactly(
      Added(emulator1.device.copy(serialNumber = "emulator-1")),
      StateChanged(emulator1.device.copy(isOnline = false)),
      StateChanged(emulator1.device.copy(isOnline = true, serialNumber = "emulator-2")),
    ).inOrder()
  }

  private fun deviceComboBoxDeviceTracker(
    project: Project = projectRule.project,
    preexistingDevice: Device? = null,
    adbSession: AdbLibSession = this.adbSession
  ) = DeviceComboBoxDeviceTracker(project, preexistingDevice, adbSession)
}

