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

import com.android.adblib.DeviceSelector
import com.android.adblib.ddmlibcompatibility.debugging.associatedIDevice
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType.SHELL
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.fakeadbserver.services.StatusWriter
import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.adblib.testutils.FakeAdbServerAdbLibRule
import com.android.tools.idea.device.explorer.common.DeviceExplorerSettings
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorControllerImpl.Companion.getProjectController
import com.android.tools.idea.device.explorer.monitor.mocks.MockDeviceMonitorView
import com.android.tools.idea.device.explorer.monitor.mocks.MockProjectApplicationIdsProvider
import com.android.tools.idea.device.explorer.monitor.processes.DeviceProcessService
import com.android.tools.idea.device.explorer.monitor.processes.isPidOnly
import com.android.tools.idea.device.explorer.monitor.processes.safeProcessName
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerOrReplaceServiceInstance
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val TIMEOUT_SECONDS: Long = 30

class DeviceMonitorControllerImplTest {
  private val androidProjectRule = AndroidProjectRule.inMemory()

  private val project: Project
    get() = androidProjectRule.project

  private val commandHandler = TestCommandHandler()

  private val fakeAdbServerAdbLibRule = FakeAdbServerAdbLibRule { addDeviceHandler(commandHandler) }

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(androidProjectRule).around(fakeAdbServerAdbLibRule)

  private lateinit var model: DeviceMonitorModel
  private lateinit var processService: DeviceProcessService
  private lateinit var mockView: MockDeviceMonitorView
  private lateinit var testDevice1: DeviceState
  private lateinit var packageNameProvider: MockProjectApplicationIdsProvider
  private var debugTriggeredForPids = mutableListOf<Int>()

  @Before
  fun setup() {
    processService = DeviceProcessService { _, client, _ -> debugTriggeredForPids.add(client.clientData.pid) }
    ApplicationManager.getApplication()
      .registerOrReplaceServiceInstance(DeviceExplorerSettings::class.java, DeviceExplorerSettings(), androidProjectRule.testRootDisposable)
    packageNameProvider = MockProjectApplicationIdsProvider(project)
    model = DeviceMonitorModel(processService, packageNameProvider)
    mockView = MockDeviceMonitorView(project, model)
    mockView.setup()
    testDevice1 = connectDevice("test_device_01")
    addClient(testDevice1, 5)
  }

  @Test
  fun ifControllerIsSetAsProjectKey() =
    runBlocking(Dispatchers.EDT) {
      // Prepare Act
      val controller = createController()

      // Assert
      assertThat(controller).isEqualTo(getProjectController(project))
    }

  @Test
  fun startingController() =
    runBlocking(Dispatchers.EDT) {
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
  fun connectingSecondDevice() =
    runBlocking(Dispatchers.EDT) {
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
  fun removingDevice() =
    runBlocking(Dispatchers.EDT) {
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
  fun killProcesses() =
    runBlocking(Dispatchers.EDT) {
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
  fun attachDebuggerToProcesses() =
    runBlocking(Dispatchers.EDT) {
      // Prepare
      val controller = createController()
      controller.setup()
      controller.setActiveConnectedDevice(getDeviceHandle(testDevice1.deviceId))
      checkMockViewInitialState()
      waitForCondition("Client ${model.tableModel.getValueForRow(0).safeProcessName} has an unknown name") {
        !model.tableModel.getValueForRow(0).isPidOnly
      }
      val config =
        RunManager.getInstance(project)
          .createConfiguration("debugAllInDeviceMonitorTest", AndroidTestRunConfigurationType.getInstance().factory)
      RunManager.getInstance(project).addConfiguration(config)
      RunManager.getInstance(project).selectedConfiguration = config

      // Act
      val connectedDevice = getDeviceHandle(testDevice1.deviceId)?.state?.connectedDevice!!
      yieldUntil { connectedDevice.associatedIDevice() != null }
      mockView.debugNodes()

      // Assert
      waitForCondition("Debugging wasn't started") { debugTriggeredForPids.contains(5) }
    }

  @Test
  fun changeInDeviceSelection() =
    runBlocking(Dispatchers.EDT) {
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
  fun filterOneProcessOut() =
    runBlocking(Dispatchers.EDT) {
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
  fun filterAllProcesses() =
    runBlocking(Dispatchers.EDT) {
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
  fun filterProcessesAfterProjectSync() =
    runBlocking(Dispatchers.EDT) {
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

  @Test
  fun clearAppData(): Unit =
    runBlocking(Dispatchers.EDT) {
      // Prepare
      val controller = createController()
      controller.setup()
      val deviceHandle = getDeviceHandle(testDevice1.deviceId)
      controller.setActiveConnectedDevice(deviceHandle)
      checkMockViewInitialState()

      // Act
      mockView.clearAppDataNodes()

      waitForCondition("Expected 'pm clear' to be called", 5) { commandHandler.commands.contains("shell pm clear package_5") }
    }

  @Test
  fun uninstallApp(): Unit =
    runBlocking(Dispatchers.EDT) {
      // Prepare
      val controller = createController()
      controller.setup()
      val deviceHandle = getDeviceHandle(testDevice1.deviceId)
      controller.setActiveConnectedDevice(deviceHandle)
      checkMockViewInitialState()

      // Act
      mockView.uninstallAppNodes()

      waitForCondition("Expected 'pm uninstall' to be called", 5) {
        println(commandHandler.commands)
        commandHandler.commands.contains("shell pm uninstall package_5")
      }
    }

  private fun createController(): DeviceMonitorControllerImpl {
    return DeviceMonitorControllerImpl(project, model, mockView)
  }

  private suspend fun checkMockViewInitialState() {
    checkMockViewActiveDevice(1)
    // Since we add a client process to start with wait for its name to be set since
    // it's required to perform actions like killing a process or clearing AppData
    waitForCondition("Client ${model.tableModel.getValueForRow(0).safeProcessName} has an unknown name") {
      !model.tableModel.getValueForRow(0).isPidOnly
    }
  }

  private suspend fun checkMockViewActiveDevice(numOfClientsExpected: Int) {
    waitForCondition("Table model has ${model.tableModel.rowCount} but expected $numOfClientsExpected") {
      model.tableModel.rowCount == numOfClientsExpected
    }
  }

  private suspend fun getDeviceHandle(serialNumber: String): DeviceHandle? {
    val deviceProvisioner = project.getService(DeviceProvisionerService::class.java).deviceProvisioner
    return deviceProvisioner.findConnectedDeviceHandle(DeviceSelector.fromSerialNumber(serialNumber))
  }

  private fun addClient(fakeDevice: DeviceState, pid: Int): ClientState {
    return fakeDevice.startClient(pid, pid * 2, "process_$pid", "package_$pid", true)
  }

  private fun connectDevice(deviceId: String): DeviceState =
    runBlocking(Dispatchers.Default) {
      val deviceState =
        fakeAdbServerAdbLibRule.connectDevice(
          deviceId = deviceId,
          manufacturer = "Google",
          deviceModel = "Pixel 10",
          release = "8.0",
          sdk = AndroidApiLevel(30),
          hostConnectionType = DeviceState.HostConnectionType.USB,
        )
      deviceState
    }

  /** Waits until process names show up for all the rows */
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

  private suspend fun waitForCondition(failureMessage: String, timeoutSec: Long = TIMEOUT_SECONDS, condition: () -> Boolean) {
    val nano = TimeUnit.SECONDS.toNanos(timeoutSec)
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

  private class TestCommandHandler : DeviceCommandHandler("") {
    val commands = mutableListOf<String>()

    override fun accept(
      server: FakeAdbServer,
      socketScope: CoroutineScope,
      socket: Socket,
      device: DeviceState,
      command: String,
      args: String,
      statusWriter: StatusWriter,
      shellCommandOutputProvider: (() -> ShellCommandOutput)?,
    ): Boolean {
      if (command.startsWith("shell") && (args.startsWith("pm clear ") || args.startsWith("pm uninstall "))) {
        val output =
          shellCommandOutputProvider?.invoke()
            ?: (if (command == "shell,v2") com.android.fakeadbserver.ShellProtocolType.SHELL_V2 else SHELL).createServiceOutput(
              socket,
              device,
            )
        statusWriter.writeOk()
        output.writeStdout("Success")
        output.writeExitCode(0)
        // remove any excess spaces from the args because IDevice.uninstallPackage() actually
        // executes `pm uninstall  <package>` (2 spaced before the package name
        commands.add("shell ${args.split(" +".toRegex()).joinToString(" ") { it }}")
        return true
      }
      return false
    }
  }
}
