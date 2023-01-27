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

import com.android.adblib.AdbSession
import com.android.adblib.DeviceState.OFFLINE
import com.android.adblib.DeviceState.ONLINE
import com.android.adblib.SOCKET_CONNECT_TIMEOUT_MS
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.testing.FakeAdbDeviceProvisionerPlugin
import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.android.tools.idea.logcat.testing.TestDevice
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import java.time.Duration


/**
 * Tests for [DeviceComboBoxDeviceTracker]
 */
class DeviceComboBoxDeviceTrackerTest {
  private val closeables = CloseablesRule()
  private val disposableRule = DisposableRule()


  @get:Rule
  val rule = RuleChain(closeables, disposableRule)

  private val fakeAdb = closeables.register(FakeAdbServerProvider().buildDefault().start())
  private val host = closeables.register(TestingAdbSessionHost())
  private val adbSession =
    closeables.register(AdbSession.create(
      host,
      fakeAdb.createChannelProvider(host),
      Duration.ofMillis(SOCKET_CONNECT_TIMEOUT_MS)
    ))
  private val plugin = FakeAdbDeviceProvisionerPlugin(adbSession.scope, fakeAdb)
  private val deviceProvisioner = DeviceProvisioner.create(adbSession, listOf(plugin))

  private val device1 = TestDevice("device-1", ONLINE, "11", 30, "manufacturer1", "model1")
  private val device2 = TestDevice("device-2", ONLINE, "12", 31, "manufacturer2", "model2")
  private val emulator1 = TestDevice("emulator-1", ONLINE, "11", 30, avdName = "avd1")

  @Test
  fun initialDevices(): Unit = runBlockingWithTimeout {
    val deviceHandles = addDevices(device1, emulator1)

    val deviceTracker = deviceComboBoxDeviceTracker()

    val events = deviceTracker.trackDevices().take(deviceHandles.size).toList()

    assertThat(events).containsExactly(
      Added(device1.device),
      Added(emulator1.device),
    )
  }

  @Test
  fun initialDevices_ignoresOffline(): Unit = runBlockingWithTimeout {
    addDevices(device1, device2.withState(OFFLINE))

    val deviceTracker = deviceComboBoxDeviceTracker()

    val events = deviceTracker.trackDevices().take(1).toList()

    assertThat(events).containsExactly(
      Added(device1.device),
    )
  }

  @Test
  fun initialDevices_withInitialPreexistingDevice(): Unit = runBlockingWithTimeout {
    val preexistingEmulator = emulator1.withState(OFFLINE).withSerialNumber("")
    addDevices(device1)
    val deviceTracker = deviceComboBoxDeviceTracker(preexistingEmulator.device)

    val events = deviceTracker.trackDevices().take(2).toList()

    assertThat(events).containsExactly(
      Added(device1.device),
      Added(preexistingEmulator.device),
    )
  }

  @Test
  fun initialDevices_withInitialPreexistingDeviceMatchingOnlineDevice(): Unit = runBlockingWithTimeout {
    val preexistingEmulator = emulator1.withState(OFFLINE).withSerialNumber("")
    addDevices(emulator1, device1)
    val deviceTracker = deviceComboBoxDeviceTracker(preexistingEmulator.device)

    val events = deviceTracker.trackDevices().take(2).toList()

    assertThat(events).containsExactly(
      Added(emulator1.device),
      Added(device1.device),
    )
  }

  @Test
  fun deviceAdded(): Unit = runBlockingWithTimeout {
    val preexistingDevice = device1.withState(OFFLINE).device
    val deviceTracker = deviceComboBoxDeviceTracker(preexistingDevice)

    val events = async { deviceTracker.trackDevices().take(3).toList() }.also {
      addDevices(device2, emulator1)
    }.await()

    assertThat(events).containsExactly(
      Added(preexistingDevice),
      Added(device2.device),
      Added(emulator1.device),
    )
  }

  @Test
  fun changeState_goesOffline(): Unit = runBlockingWithTimeout {
    val deviceHandle = device1.addDevice(plugin)
    val deviceTracker = deviceComboBoxDeviceTracker()
    val events = mutableListOf<DeviceEvent>()

    launch { deviceTracker.trackDevices().take(2).toList(events) }.also {
      yieldUntil { events.size == 1 }
      deviceHandle.disconnect()
    }.join()

    assertThat(events).containsExactly(
      Added(device1.device),
      StateChanged(device1.withState(OFFLINE).device),
    ).inOrder()
  }

  @Test
  fun changeState_deviceRemoved(): Unit = runBlockingWithTimeout {
    val deviceHandle = device1.addDevice(plugin)
    val deviceTracker = deviceComboBoxDeviceTracker()
    val events = mutableListOf<DeviceEvent>()

    launch { deviceTracker.trackDevices().take(2).toList(events) }.also {
      yieldUntil { events.size == 1 }
      plugin.removeDevice(deviceHandle)
    }.join()

    assertThat(events).containsExactly(
      Added(device1.device),
      StateChanged(device1.withState(OFFLINE).device),
    ).inOrder()
  }

  @Test
  fun changeState_goesOfflineComesOnline(): Unit = runBlockingWithTimeout {
    val deviceHandle = device1.addDevice(plugin)
    val deviceTracker = deviceComboBoxDeviceTracker()
    val events = mutableListOf<DeviceEvent>()

    launch { deviceTracker.trackDevices().take(3).toList(events) }.also {
      yieldUntil { events.size == 1 }
      deviceHandle.disconnect()
      yieldUntil { events.size == 2 }
      deviceHandle.connect()
    }.join()

    assertThat(events).containsExactly(
      Added(device1.device),
      StateChanged(device1.withState(OFFLINE).device),
      StateChanged(device1.device),
    ).inOrder()
  }


  @Test
  fun changeState_emulatorComesOnlineWithDifferentSerialNumber(): Unit = runBlockingWithTimeout {
    val emulator = emulator1.withSerialNumber("emulator-1")
    val deviceHandle = emulator.addDevice(plugin)
    val deviceTracker = deviceComboBoxDeviceTracker()
    val events = mutableListOf<DeviceEvent>()

    launch { deviceTracker.trackDevices().take(3).toList(events) }.also {
      yieldUntil { events.size == 1 }
      deviceHandle.disconnect()
      yieldUntil { events.size == 2 }
      val emulatorReconnectedOnDifferentPort = emulator1.withSerialNumber("emulator-2")
      emulatorReconnectedOnDifferentPort.addDevice(plugin)
    }.join()

    assertThat(events).containsExactly(
      Added(emulator.device),
      StateChanged(emulator1.withState(OFFLINE).device),
      StateChanged(emulator1.withSerialNumber("emulator-2").device),
    ).inOrder()
  }

  private fun deviceComboBoxDeviceTracker(preexistingDevice: Device? = null): DeviceComboBoxDeviceTracker {
    return DeviceComboBoxDeviceTracker(deviceProvisioner, preexistingDevice)
  }

  private suspend fun addDevices(vararg devices: TestDevice): List<DeviceHandle> = devices.map { it.addDevice(plugin) }
}

private suspend fun DeviceHandle.connect() {
  activationAction?.activate()
  yieldUntil { state.connectedDevice != null }
}

private suspend fun DeviceHandle.disconnect() {
  deactivationAction?.deactivate()
  yieldUntil { state.connectedDevice == null }
}
