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

import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.ui.components.JBList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.spy

/**
 * Tests for [DeviceComboBox]
 */
class DeviceComboBoxTest {

  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(ApplicationRule(), disposableRule)

  private val selectionEvents = mutableListOf<Any?>()
  private val deviceTracker = FakeDeviceComboBoxDeviceTracker()

  private val device1 = Device.createPhysical("device1", false, "11", "30", "Google", "Pixel 2")
  private val device2 = Device.createPhysical("device2", false, "11", "30", "Google", "Pixel 2")
  private val emulator = Device.createEmulator("emulator-5555", false, "11", "30", "AVD")

  @Test
  fun noDevice_noSelection(): Unit = runBlocking {
    val deviceComboBox = deviceComboBox(deviceTracker = deviceTracker, selectionEvents = selectionEvents)

    assertThat(deviceComboBox.trackSelectedDevice().toList()).isEmpty()
    assertThat(selectionEvents).isEmpty()
  }

  @Test
  fun noInitialDevice_selectsFirstDevice(): Unit = runBlocking {
    deviceTracker.setEvents(
      Added(device1),
      Added(device2),
    )

    val deviceComboBox = deviceComboBox(deviceTracker = deviceTracker, selectionEvents = selectionEvents)
    val selectedDevices = deviceComboBox.trackSelectedDevice().toList()

    assertThat(selectedDevices).containsExactly(device1)
    assertThat(selectionEvents).isEqualTo(selectedDevices)
  }

  @Test
  fun withInitialDevice_selectsInitialDevice(): Unit = runBlocking {
    deviceTracker.setEvents(
      Added(device1),
      Added(device2),
    )

    val deviceComboBox = deviceComboBox(initialDevice = device2, deviceTracker = deviceTracker, selectionEvents = selectionEvents)
    val selectedDevices = deviceComboBox.trackSelectedDevice().toList()

    assertThat(selectedDevices).containsExactly(device2)
    assertThat(selectionEvents).isEqualTo(selectedDevices)
  }

  @Test
  fun selectedDeviceStateChanges_selectsDevice(): Unit = runBlocking {
    deviceTracker.setEvents(
      Added(device2.online()),
      StateChanged(device2.offline()),
    )

    val deviceComboBox = deviceComboBox(deviceTracker = deviceTracker, selectionEvents = selectionEvents)
    val selectedDevices = deviceComboBox.trackSelectedDevice().toList()

    assertThat(selectedDevices).containsExactly(
      device2.online(),
      device2.offline(),
    )
    assertThat(selectionEvents).isEqualTo(selectedDevices)
  }

  @Test
  fun unselectedDeviceStateChanges_doesNotSelect(): Unit = runBlocking {
    deviceTracker.setEvents(
      Added(device1),
      Added(device2.online()),
      StateChanged(device2.offline()),
    )

    val deviceComboBox = deviceComboBox(deviceTracker = deviceTracker, selectionEvents = selectionEvents)
    val selectedDevices = deviceComboBox.trackSelectedDevice().toList()

    assertThat(selectedDevices).containsExactly(device1)
    assertThat(selectionEvents).isEqualTo(selectedDevices)
  }

  @Test
  fun renderer_physicalDevice_offline() {
    val deviceComboBox = deviceComboBox(deviceTracker = deviceTracker)

    assertThat(deviceComboBox.getRenderedText(device1.offline()))
      .isEqualTo("Google Pixel 2 Android 11, API 30 [OFFLINE]")
  }

  @Test
  fun renderer_physicalDevice_online() {
    val deviceComboBox = deviceComboBox(deviceTracker = deviceTracker)

    assertThat(deviceComboBox.getRenderedText(device1.online()))
      .isEqualTo("Google Pixel 2 (device1) Android 11, API 30")
  }

  @Test
  fun renderer_emulator_offline() {
    val deviceComboBox = deviceComboBox(deviceTracker = deviceTracker)

    assertThat(deviceComboBox.getRenderedText(emulator.offline()))
      .isEqualTo("AVD Android 11, API 30 [OFFLINE]")
  }

  @Test
  fun renderer_emulator_online() {
    val deviceComboBox = deviceComboBox(deviceTracker = deviceTracker)

    assertThat(deviceComboBox.getRenderedText(emulator.online()))
      .isEqualTo("AVD (emulator-5555) Android 11, API 30")
  }

  private fun deviceComboBox(
    disposable: Disposable = disposableRule.disposable,
    initialDevice: Device? = null,
    deviceTracker: IDeviceComboBoxDeviceTracker = FakeDeviceComboBoxDeviceTracker(),
    selectionEvents: MutableList<Any?> = mutableListOf(),
    ): DeviceComboBox =
    DeviceComboBox(disposable, initialDevice, deviceTracker, autostart = false).also {
      // Replace the model with a spy that records all the calls to setSelectedItem()
      it.model = spy(it.model)
      `when`(it.model.setSelectedItem(any())).thenAnswer { invocation ->
        invocation.callRealMethod()
        selectionEvents.add(invocation.arguments[0])
      }
      // We must call the ctor for autostart = false so it doesn't start tracking before we replace the model
      it.startTrackingDevices()
    }
}

private class FakeDeviceComboBoxDeviceTracker : IDeviceComboBoxDeviceTracker {

  private var events = mutableListOf<DeviceEvent>()

  fun setEvents(vararg events: DeviceEvent) {
    this.events.clear()
    this.events.addAll(events)
  }

  override suspend fun trackDevices(): Flow<DeviceEvent> {
    return events.asFlow()
  }
}

private fun Device.offline() = copy(isOnline = false)
private fun Device.online() = copy(isOnline = true)

private fun DeviceComboBox.getRenderedText(device: Device) =
  renderer.getListCellRendererComponent(JBList(), device, 0, false, false).toString()