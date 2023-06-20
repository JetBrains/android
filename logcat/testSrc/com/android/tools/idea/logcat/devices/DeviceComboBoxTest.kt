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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.android.tools.idea.testing.ProjectServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBList
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.spy

/**
 * Tests for [DeviceComboBox]
 */
@Suppress("OPT_IN_USAGE") // runTest is experimental
class DeviceComboBoxTest {
  private val projectRule = ProjectRule()
  private val deviceTracker = FakeDeviceComboBoxDeviceTracker()

  @get:Rule
  val rule = RuleChain(
    projectRule,
    ProjectServiceRule(projectRule, DeviceComboBoxDeviceTrackerFactory::class.java, DeviceComboBoxDeviceTrackerFactory { deviceTracker }),
  )

  private val selectionEvents = mutableListOf<Any?>()

  private val device1 = Device.createPhysical("device1", false, "11", 30, "Google", "Pixel 2")
  private val device2 = Device.createPhysical("device2", false, "11", 30, "Google", "Pixel 2")
  private val emulator = Device.createEmulator("emulator-5555", false, "11", 30, "AVD")

  @Test
  fun noDevice_noSelection(): Unit = runTest(dispatchTimeoutMs = 5_000) {
    val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)

    val selectedDevices = async { deviceComboBox.trackSelectedDevice().toList() }
    deviceTracker.close()

    assertThat(selectedDevices.await()).isEmpty()
    assertThat(selectionEvents).isEmpty()
  }

  @Test
  fun noInitialDevice_selectsFirstDevice(): Unit = runTest(dispatchTimeoutMs = 5_000) {
    val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)
    val selectedDevices = async { deviceComboBox.trackSelectedDevice().toList() }

    deviceTracker.use {
      it.sendEvents(
        Added(device1),
        Added(device2),
      )
      advanceUntilIdle()
    }

    assertThat(selectionEvents).containsExactly(device1)
    assertThat(selectedDevices.await()).isEqualTo(selectionEvents)
    assertThat(deviceComboBox.getItems()).containsExactly(
      device1,
      device2,
    )
  }

  @Test
  fun withInitialDevice_selectsInitialDevice(): Unit = runTest(dispatchTimeoutMs = 5_000) {
    val deviceComboBox = deviceComboBox(initialDevice = device2, selectionEvents = selectionEvents)
    val selectedDevices = async { deviceComboBox.trackSelectedDevice().toList() }

    deviceTracker.use {
      it.sendEvents(
        Added(device1),
        Added(device2),
      )
      advanceUntilIdle()
    }

    assertThat(selectionEvents).containsExactly(device2)
    assertThat(selectedDevices.await()).isEqualTo(selectionEvents)
    assertThat(deviceComboBox.getItems()).containsExactly(
      device1,
      device2,
    )
  }

  @Test
  fun selectedDeviceStateChanges_selectsDevice(): Unit = runTest(dispatchTimeoutMs = 5_000) {
    val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)
    val selectedDevices = async { deviceComboBox.trackSelectedDevice().toList() }

    deviceTracker.use {
      it.sendEvents(
        Added(device2.online()),
        StateChanged(device2.offline()),
      )
      advanceUntilIdle()
    }

    assertThat(selectionEvents).containsExactly(
      device2.online(),
      device2.offline(),
    )
    assertThat(selectedDevices.await()).isEqualTo(selectionEvents)
    assertThat(deviceComboBox.getItems()).containsExactly(
      device2.offline(),
    )
  }

  @Test
  fun unselectedDeviceStateChanges_doesNotSelect(): Unit = runTest(dispatchTimeoutMs = 5_000) {
    val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)
    val selectedDevices = async { deviceComboBox.trackSelectedDevice().toList() }

    deviceTracker.use {
      it.sendEvents(
        Added(device1),
        Added(device2.online()),
        StateChanged(device2.offline()),
      )
      advanceUntilIdle()
    }

    assertThat(selectionEvents).containsExactly(device1)
    assertThat(selectedDevices.await()).isEqualTo(selectionEvents)
    assertThat(deviceComboBox.getItems()).containsExactly(
      device1,
      device2.offline(),
    )
  }

  @Test
  fun userSelection_sendsToFlow(): Unit = runTest(dispatchTimeoutMs = 5_000) {
    val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)
    val selectedDevices = async { deviceComboBox.trackSelectedDevice().toList() }

    deviceTracker.use {
      it.sendEvents(
        Added(device1),
        Added(device2),
      )
      advanceUntilIdle()
      deviceComboBox.selectedItem = device2
      advanceUntilIdle()
    }

    assertThat(selectedDevices.await()).containsExactly(device1, device2)
  }

  @Test
  fun renderer_physicalDevice_offline() {
    val deviceComboBox = deviceComboBox()

    assertThat(deviceComboBox.getRenderedText(device1.offline()))
      .isEqualTo("Google Pixel 2 Android 11, API 30 [OFFLINE]")
  }

  @Test
  fun renderer_physicalDevice_online() {
    val deviceComboBox = deviceComboBox()

    assertThat(deviceComboBox.getRenderedText(device1.online()))
      .isEqualTo("Google Pixel 2 (device1) Android 11, API 30")
  }

  @Test
  fun renderer_emulator_offline() {
    val deviceComboBox = deviceComboBox()

    assertThat(deviceComboBox.getRenderedText(emulator.offline()))
      .isEqualTo("AVD Android 11, API 30 [OFFLINE]")
  }

  @Test
  fun renderer_emulator_online() {
    val deviceComboBox = deviceComboBox()

    assertThat(deviceComboBox.getRenderedText(emulator.online()))
      .isEqualTo("AVD (emulator-5555) Android 11, API 30")
  }

  private fun deviceComboBox(
    initialDevice: Device? = null,
    selectionEvents: MutableList<Any?> = mutableListOf(),
  ): DeviceComboBox {
    return DeviceComboBox(projectRule.project, initialDevice).also {
      // Replace the model with a spy that records all the calls to setSelectedItem()
      it.model = spy(it.model)
      whenever(it.model.setSelectedItem(any())).thenAnswer { invocation ->
        invocation.callRealMethod()
        selectionEvents.add(invocation.arguments[0])
      }
    }
  }
}

private fun Device.offline() = copy(isOnline = false)

private fun Device.online() = copy(isOnline = true)

private fun DeviceComboBox.getRenderedText(device: Device) =
  renderer.getListCellRendererComponent(JBList(), device, 0, false, false).toString()

private fun DeviceComboBox.getItems(): List<Device> =
  (model as CollectionComboBoxModel<Device>).items