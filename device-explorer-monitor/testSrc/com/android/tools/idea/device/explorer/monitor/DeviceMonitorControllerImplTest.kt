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

import com.android.ddmlib.ClientData
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.explorer.common.DeviceExplorerSettings
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorControllerImpl.Companion.getProjectController
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDeviceService
import com.android.tools.idea.device.explorer.monitor.mocks.MockDeviceHandle
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
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DeviceMonitorControllerImplTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.withSdk()

  private val project: Project
    get() = androidProjectRule.project

  @get:Rule
  val fakeAdbRule = FakeAdbRule()

  private lateinit var model: DeviceMonitorModel
  private lateinit var service: AdbDeviceService
  private lateinit var processService: DeviceProcessService
  private lateinit var mockView: MockDeviceMonitorView
  private lateinit var testDevice1: DeviceState
  private lateinit var testDeviceHandle1: MockDeviceHandle
  private lateinit var packageNameProvider: MockProjectApplicationIdsProvider

  @Before
  fun setup() {
    service = AdbDeviceService(project)
    processService = DeviceProcessService { _, client, _ ->
      client.clientData.debuggerConnectionStatus = ClientData.DebuggerStatus.ATTACHED
      // Add new client to trigger device update
      addClient(testDevice1, 60)
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
    testDevice1 = fakeAdbRule.attachDevice("test_device_01", "Google", "Pix3l", "versionX", "29")
    testDeviceHandle1 = MockDeviceHandle(mock(CoroutineScope::class.java), testDevice1.deviceId)
    addClient(testDevice1, 5)
  }

  @After
  fun tearDown() {
    Disposer.dispose(service)
    fakeAdbRule.disconnectDevice(testDevice1.deviceId)
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
    waitForServiceToRetrieveInitialDevice()
    controller.setActiveConnectedDevice(testDeviceHandle1)

    // Assert
    checkMockViewInitialState()
  }

  @Test
  fun connectingSecondDevice() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForServiceToRetrieveInitialDevice()
    controller.setActiveConnectedDevice(testDeviceHandle1)
    checkMockViewInitialState()

    // Act
    val testDevice2 = fakeAdbRule.attachDevice("test_device_02", "Google", "Pix3l", "versionX", "29")
    val testDeviceHandle2 = MockDeviceHandle(mock(CoroutineScope::class.java), testDevice2.deviceId)
    controller.setActiveConnectedDevice(testDeviceHandle2)
    addClient(testDevice2, 10)
    addClient(testDevice2, 20)

    // Assert
    checkMockViewActiveDevice(2)

    fakeAdbRule.disconnectDevice(testDevice2.deviceId)
  }

  @Test
  fun removingDevice() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForServiceToRetrieveInitialDevice()
    controller.setActiveConnectedDevice(testDeviceHandle1)
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
    waitForServiceToRetrieveInitialDevice()
    controller.setActiveConnectedDevice(testDeviceHandle1)
    checkMockViewInitialState()

    // Act
    val processToKill = model.tableModel.getValueForRow(0)
    testDevice1.setActivityManager { args, _ ->
      if ("force-stop" == args[0] && "package-${processToKill.pid}" == args[1]) {
        testDevice1.stopClient(processToKill.pid)
      }
    }
    mockView.killNodes()

    // Assert
    checkMockViewActiveDevice(0)
  }

  @Test
  fun attachDebuggerToProcesses() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForServiceToRetrieveInitialDevice()
    controller.setActiveConnectedDevice(testDeviceHandle1)
    checkMockViewInitialState()
    waitForCondition("Client ${model.tableModel.getValueForRow(0).safeProcessName} has an unknown name") { !model.tableModel.getValueForRow(0).isPidOnly }
    val config = RunManager.getInstance(project).createConfiguration("debugAllInDeviceMonitorTest", AndroidTestRunConfigurationType.getInstance().factory)
    RunManager.getInstance(project).addConfiguration(config)
    RunManager.getInstance(project).selectedConfiguration = config

    // Act
    mockView.debugNodes()

    // Assert
    checkMockViewActiveDevice(2)
    waitForCondition(
      "No client has debugger status as ${ClientData.DebuggerStatus.ATTACHED}"
    ) {
      for (index in 0 until model.tableModel.rowCount) {
        val processInfo = model.tableModel.getValueForRow(index)
        if (processInfo.pid == 5 && processInfo.debuggerStatus == ClientData.DebuggerStatus.ATTACHED) {
          return@waitForCondition true
        }
      }
      return@waitForCondition false
    }
  }

  @Test
  fun changeInDeviceSelection() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForServiceToRetrieveInitialDevice()
    controller.setActiveConnectedDevice(testDeviceHandle1)
    checkMockViewInitialState()

    // Act
    val testDevice2 = fakeAdbRule.attachDevice("test_device_02", "Google", "Pix3l", "versionX", "29")
    val testDeviceHandle2 = MockDeviceHandle(mock(CoroutineScope::class.java), testDevice2.deviceId)
    controller.setActiveConnectedDevice(testDeviceHandle2)

    // Assert
    checkMockViewActiveDevice(0)
    controller.setActiveConnectedDevice(testDeviceHandle1)
    checkMockViewActiveDevice(1)

    fakeAdbRule.disconnectDevice(testDevice2.deviceId)
  }

  @Test
  fun filterOneProcessOut() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForServiceToRetrieveInitialDevice()
    controller.setActiveConnectedDevice(testDeviceHandle1)
    checkMockViewInitialState()

    addClient(testDevice1, 10)
    checkMockViewActiveDevice(2)

    // Act
    packageNameProvider.setApplicationIds("package-10")
    model.setPackageFilter(true)

    // Assert
    checkMockViewActiveDevice(1)
  }

  @Test
  fun filterAllProcesses() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForServiceToRetrieveInitialDevice()
    controller.setActiveConnectedDevice(testDeviceHandle1)
    checkMockViewInitialState()

    addClient(testDevice1, 10)
    checkMockViewActiveDevice(2)

    // Act
    packageNameProvider.setApplicationIds("no-process-package")
    model.setPackageFilter(true)

    // Assert
    checkMockViewActiveDevice(0)
  }

  @Test
  fun filterProcessesAfterProjectSync() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForServiceToRetrieveInitialDevice()
    controller.setActiveConnectedDevice(testDeviceHandle1)
    checkMockViewInitialState()

    model.setPackageFilter(true)
    addClient(testDevice1, 10)
    checkMockViewActiveDevice(2)

    // Act
    packageNameProvider.setApplicationIds("package-10")

    // Assert
    checkMockViewActiveDevice(1)
  }

  private fun createController(): DeviceMonitorControllerImpl {
    return DeviceMonitorControllerImpl(project, model, mockView, service)
  }

  private suspend fun checkMockViewInitialState() {
    checkMockViewActiveDevice(1)
  }

  private suspend fun checkMockViewActiveDevice(numOfClientsExpected: Int) {
    waitForCondition(
      "Table model has ${model.tableModel.rowCount} but expected $numOfClientsExpected"
    ) { model.tableModel.rowCount == numOfClientsExpected }
  }

  private suspend fun waitForServiceToRetrieveInitialDevice() {
    waitForCondition(
      "Service failed to retrieve initial device"
    ) { service.getIDeviceFromSerialNumber(testDevice1.deviceId) != null }
  }

  private fun addClient(fakeDevice: DeviceState, pid: Int): ClientState {
    return fakeDevice.startClient(
      pid,
      pid * 2,
      "package-$pid",
      "app-$pid",
      true
    )
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