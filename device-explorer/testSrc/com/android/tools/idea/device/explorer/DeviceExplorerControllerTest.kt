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
package com.android.tools.idea.device.explorer

import com.android.adblib.serialNumber
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutures
import com.android.tools.idea.device.explorer.mocks.MockDeviceExplorerTabController
import com.android.tools.idea.device.explorer.mocks.MockDeviceExplorerView
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DeviceExplorerControllerTest {
  @get:Rule
  val androidProjectRule = AndroidProjectRule.withSdk()

  private val project: Project
    get() = androidProjectRule.project

  @JvmField
  @Rule
  val deviceProvisionerRule = DeviceProvisionerRule()

  private lateinit var model: DeviceExplorerModel
  private lateinit var view: MockDeviceExplorerView
  private lateinit var tabController: MockDeviceExplorerTabController

  @Before
  fun setUp() {
    model = DeviceExplorerModel(deviceProvisionerRule.deviceProvisioner)
    view = MockDeviceExplorerView(project, model)
    tabController = MockDeviceExplorerTabController()
  }

  @After
  fun tearDown() {
    for (device in deviceProvisionerRule.deviceProvisioner.devices.value) {
      device.state.connectedDevice?.serialNumber?.let {
        deviceProvisionerRule.fakeAdb.disconnectDevice(it)
      }
    }
  }

  @Test
  fun controllerIsSetAsProjectKey() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare Act
    val controller = createController()

    // Assert
    assertThat(controller).isEqualTo(DeviceExplorerController.getProjectController(project))
  }

  @Test
  fun startControllerWithNoDeviceConnected() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()

    // Assert
    pumpEventsAndWaitForFuture(tabController.activeDeviceTracker.consume())
    assertThat(view.viewComboBox().isVisible).isFalse()
    assertThat(view.viewTabPane().isVisible).isFalse()
  }

  @Test
  fun startControllerWithDeviceConnected() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    connectDevice("test_device_01")

    // Act
    controller.setup()

    // Assert
    pumpEventsAndWaitForFutures(tabController.activeDeviceTracker.consumeMany(2))
    assertThat(view.viewComboBox().isVisible).isTrue()
    assertThat(view.viewComboBox().itemCount).isEqualTo(1)
    assertThat(view.viewTabPane().isVisible).isTrue()
  }

  @Test
  fun deviceSelection() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    connectDevice("test_device_01")
    val newDeviceState = connectDevice("test_device_02")
    controller.setup()
    waitForCondition { view.viewComboBox().itemCount == 2 }

    // Act
    controller.selectActiveDevice(newDeviceState.deviceId)

    // Assert
    waitForCondition { (view.viewComboBox().selectedItem as? DeviceHandle)?.state?.connectedDevice?.serialNumber ==  newDeviceState.deviceId }
    assertThat(view.viewComboBox().isVisible).isTrue()
    assertThat(view.viewTabPane().isVisible).isTrue()
  }

  @Test
  fun simulatePackageFilterSelection() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()

    // Act
    controller.packageFilterToggled(true)

    // Assert
    pumpEventsAndWaitForFuture(tabController.activeDeviceTracker.consume())
    val isActive = pumpEventsAndWaitForFuture(tabController.packageFilterTracker.consume())
    assertThat(isActive).isTrue()
  }

  private fun createController(): DeviceExplorerController =
    DeviceExplorerController(project, model, view, listOf(tabController))

  private fun connectDevice(deviceId: String): DeviceState {
    val deviceState = deviceProvisionerRule.fakeAdb.connectDevice(
      deviceId = deviceId,
      manufacturer = "Google",
      deviceModel = "Pixel 10",
      release = "8.0",
      sdk = "31",
      hostConnectionType = DeviceState.HostConnectionType.USB
    )
    deviceState.deviceStatus = DeviceState.DeviceStatus.ONLINE
    return deviceState
  }

  private suspend fun waitForCondition(condition: () -> Boolean) {
    val nano = TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLISECONDS)
    val startNano = System.nanoTime()
    val endNano = startNano + nano

    while (System.nanoTime() <= endNano) {
      if (condition.invoke()) {
        return
      }

      delay(50L)
    }

    throw TimeoutException()
  }

  private val TIMEOUT_MILLISECONDS: Long = 30_000
}