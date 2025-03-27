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
package com.android.tools.idea.device.explorer.monitor

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.ddmlibcompatibility.AdbLibIDeviceManagerFactory
import com.android.adblib.isOnline
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.ddmlib.AdbInitOptions
import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.explorer.common.DeviceExplorerSettings
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorControllerImpl.Companion.getProjectController
import com.android.tools.idea.device.explorer.monitor.mocks.MockDeviceMonitorView
import com.android.tools.idea.device.explorer.monitor.mocks.MockProjectApplicationIdsProvider
import com.android.tools.idea.device.explorer.monitor.processes.DeviceProcessService
import com.android.tools.idea.device.explorer.monitor.processes.isPidOnly
import com.android.tools.idea.device.explorer.monitor.processes.safeProcessName
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DeviceMonitorControllerImplTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.inMemory()

  private val project: Project
    get() = androidProjectRule.project

  @get:Rule
  val deviceProvisionerRule = DeviceProvisionerRule()

  private lateinit var model: DeviceMonitorModel
  private lateinit var processService: DeviceProcessService
  private lateinit var mockView: MockDeviceMonitorView
  private lateinit var testDevice1: DeviceState
  private lateinit var packageNameProvider: MockProjectApplicationIdsProvider
  private var debugTriggeredForPids = mutableListOf<Int>()

  @Before
  fun setup() {
    processService = DeviceProcessService { _, client, _ ->
      // TODO: Figure out if we can instead change `JdwpProcessProperties` for this process
      //  and set the `jdwpProxySocketServerStatus.isExternalDebuggerAttached` to `true`
      debugTriggeredForPids.add(client.clientData.pid)
    }
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(
      DeviceExplorerSettings::class.java,
      DeviceExplorerSettings(),
      androidProjectRule.testRootDisposable
    )
    packageNameProvider = MockProjectApplicationIdsProvider(project)
    model = DeviceMonitorModel(processService, packageNameProvider)
    mockView = MockDeviceMonitorView(project, model)
    mockView.setup()
    testDevice1 = connectDevice("test_device_01")
    addClient(testDevice1, 5)
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
  fun ifControllerIsSetAsProjectKey() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare Act
    val controller = createController()

    // Assert
    assertThat(controller).isEqualTo(getProjectController(project))
  }

  @Test
  fun startingController() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    val deviceHandle = getDeviceHandle(testDevice1.deviceId)
    controller.setActiveConnectedDevice(deviceHandle)

    // Assert
    checkMockViewInitialState()
  }

  @Test
  fun connectingSecondDevice() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    controller.setActiveConnectedDevice(getDeviceHandle(testDevice1.deviceId))
    checkMockViewInitialState()

    // Act
    val testDevice2 = connectDevice("test_device_02")
    controller.setActiveConnectedDevice(getDeviceHandle(testDevice2.deviceId))
    addClient(testDevice2, 10)
    addClient(testDevice2, 20)

    // Assert
    checkMockViewActiveDevice(2)
  }

  @Test
  fun removingDevice() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    controller.setActiveConnectedDevice(getDeviceHandle(testDevice1.deviceId))
    checkMockViewInitialState()

    // Act
    controller.setActiveConnectedDevice(null)

    // Assert
    checkMockViewActiveDevice(0)
  }

  @Test
  fun killProcesses() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    controller.setActiveConnectedDevice(getDeviceHandle(testDevice1.deviceId))
    checkMockViewInitialState()
    waitForProcessNames()

    // Act
    mockView.killNodes()

    // Assert
    checkMockViewActiveDevice(0)
  }

  @Test
  fun attachDebuggerToProcesses() = runBlocking(AndroidDispatchers.uiThread) {
    try {
      // Prepare
      AndroidDebugBridge.enableFakeAdbServerMode(deviceProvisionerRule.fakeAdb.fakeAdbServer.port)
      val adbInitOptions =
        AdbInitOptions.builder().setClientSupportEnabled(true)
          .setIDeviceManagerFactory(AdbLibIDeviceManagerFactory(deviceProvisionerRule.adbSession))
      AndroidDebugBridge.init(adbInitOptions.build())
      AndroidDebugBridge.createBridge(10, TimeUnit.SECONDS)

      val controller = createController()
      controller.setup()
      controller.setActiveConnectedDevice(getDeviceHandle(testDevice1.deviceId))
      checkMockViewInitialState()
      waitForCondition("Client ${model.tableModel.getValueForRow(0).safeProcessName} has an unknown name") {
        !model.tableModel.getValueForRow(0).isPidOnly
      }
      val config = RunManager.getInstance(project).createConfiguration("debugAllInDeviceMonitorTest",
                                                                       AndroidTestRunConfigurationType.getInstance().factory)
      RunManager.getInstance(project).addConfiguration(config)
      RunManager.getInstance(project).selectedConfiguration = config

      // Act
      mockView.debugNodes()

      // Assert
      waitForCondition(
        "Debugging wasn't started"
      ) {
        debugTriggeredForPids.contains(5)
      }
    } finally {
      // Cleanup
      AndroidDebugBridge.terminate()
      AndroidDebugBridge.disableFakeAdbServerMode()
    }
  }

  @Test
  fun changeInDeviceSelection() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    controller.setActiveConnectedDevice(getDeviceHandle(testDevice1.deviceId))
    checkMockViewInitialState()

    // Act
    val testDevice2 = connectDevice("test_device_02")
    controller.setActiveConnectedDevice(getDeviceHandle(testDevice2.deviceId))

    // Assert
    checkMockViewActiveDevice(0)
    controller.setActiveConnectedDevice(getDeviceHandle(testDevice1.deviceId))
    checkMockViewActiveDevice(1)
  }

  @Test
  fun filterOneProcessOut() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    controller.setActiveConnectedDevice(getDeviceHandle(testDevice1.deviceId))
    checkMockViewInitialState()
    addClient(testDevice1, 10)
    checkMockViewActiveDevice(2)
    waitForProcessNames()

    // Act
    packageNameProvider.setApplicationIds("package_10")
    model.setPackageFilter(true)

    // Assert
    checkMockViewActiveDevice(1)
  }

  @Test
  fun filterAllProcesses() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    controller.setActiveConnectedDevice(getDeviceHandle(testDevice1.deviceId))
    checkMockViewInitialState()

    addClient(testDevice1, 10)
    checkMockViewActiveDevice(2)

    // Act
    packageNameProvider.setApplicationIds("no_process_package")
    model.setPackageFilter(true)

    // Assert
    checkMockViewActiveDevice(0)
  }

  @Test
  fun filterProcessesAfterProjectSync() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    controller.setActiveConnectedDevice(getDeviceHandle(testDevice1.deviceId))
    checkMockViewInitialState()

    model.setPackageFilter(true)
    addClient(testDevice1, 10)
    checkMockViewActiveDevice(2)

    // Act
    packageNameProvider.setApplicationIds("package_10")

    // Assert
    checkMockViewActiveDevice(1)
  }

  private fun createController(): DeviceMonitorControllerImpl {
    return DeviceMonitorControllerImpl(project, model, mockView)
  }

  private suspend fun checkMockViewInitialState() {
    checkMockViewActiveDevice(1)
  }

  private suspend fun checkMockViewActiveDevice(numOfClientsExpected: Int) {
    waitForCondition(
      "Table model has ${model.tableModel.rowCount} but expected $numOfClientsExpected"
    ) {
      model.tableModel.rowCount == numOfClientsExpected
    }
  }

  private suspend fun getDeviceHandle(serialNumber: String): DeviceHandle? =
    deviceProvisionerRule.deviceProvisioner.findConnectedDeviceHandle(DeviceSelector.fromSerialNumber(serialNumber))

  private fun addClient(fakeDevice: DeviceState, pid: Int): ClientState {
    return fakeDevice.startClient(
      pid,
      pid * 2,
      "process_$pid",
      "package_$pid",
      true
    )
  }

  private fun connectDevice(deviceId: String): DeviceState = runBlocking(AndroidDispatchers.workerThread) {
    val deviceState = deviceProvisionerRule.fakeAdb.connectDevice(
      deviceId = deviceId,
      manufacturer = "Google",
      deviceModel = "Pixel 10",
      release = "8.0",
      sdk = "30",
      hostConnectionType = DeviceState.HostConnectionType.USB
    )
    deviceState.deviceStatus = DeviceState.DeviceStatus.ONLINE
    waitForOnlineConnectedDevice(deviceProvisionerRule.adbSession, deviceId)
    deviceState
  }

  private suspend fun waitForOnlineConnectedDevice(
    session: AdbSession,
    serialNumber: String
  ) {
    yieldUntil {
      session.connectedDevicesTracker.connectedDevices.value.any { it.isOnline && it.serialNumber == serialNumber }
    }
  }

  /** Waits until process names show up for  all the rows */
  private suspend fun waitForProcessNames() {
    yieldUntil {
      for (i in 0..<model.tableModel.rowCount) {
        if (model.tableModel.getValueForRow(i).processName == null) {
          return@yieldUntil false
        }
      }
      true
    }
  }

  private suspend fun waitForCondition(failureMessage: String, condition: () -> Boolean) {
    val nano = TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLISECONDS)
    val startNano = System.nanoTime()
    val endNano = startNano + nano

    while (System.nanoTime() <= endNano) {
      if (condition.invoke()) {
        return
      }

      delay(50L)
    }

    throw TimeoutException(failureMessage)
  }

  private val TIMEOUT_MILLISECONDS: Long = 30_000
}