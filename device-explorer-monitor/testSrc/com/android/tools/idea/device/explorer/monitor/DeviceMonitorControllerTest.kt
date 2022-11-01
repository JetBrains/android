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

import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutures
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorController.Companion.getProjectController
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDeviceService
import com.android.tools.idea.device.explorer.monitor.mocks.MockDeviceMonitorView
import com.android.tools.idea.device.explorer.monitor.processes.DeviceProcessService
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DeviceMonitorControllerTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.withSdk()

  private val project: Project
    get() = androidProjectRule.project

  @get:Rule
  val adb = FakeAdbRule()

  private lateinit var model: DeviceMonitorModel
  private lateinit var service: AdbDeviceService
  private lateinit var processService: DeviceProcessService
  private lateinit var mockView: MockDeviceMonitorView
  private lateinit var testDevice1: DeviceState

  @Before
  fun setup() {
    service = AdbDeviceService(project)
    processService = DeviceProcessService()
    model = DeviceMonitorModel(processService)
    mockView = MockDeviceMonitorView(model)
    mockView.setup()
    testDevice1 = adb.attachDevice("test_device_01", "Google", "Pix3l", "versionX", "29")
  }

  @After
  fun cleanup() {
    Disposer.dispose(service)
  }

  @Test
  fun testIfControllerIsSetAsProjectKey() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare Act
    val controller = createController()

    // Assert
    assertThat(controller).isEqualTo(getProjectController(project))
  }

  @Test
  fun testStartingController() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    waitForCondition { service.getIDeviceFromSerialNumber(testDevice1.deviceId) != null }
    controller.setActiveConnectedDevice(testDevice1.deviceId)

    // Assert
    checkMockViewInitialState()
  }

  @Test
  fun testConnectingSecondDevice() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForCondition { service.getIDeviceFromSerialNumber(testDevice1.deviceId) != null }
    controller.setActiveConnectedDevice(testDevice1.deviceId)
    checkMockViewInitialState()

    // Act
    val testDevice2 = adb.attachDevice("test_device_02", "Google", "Pix3l", "versionX", "29")
    controller.setActiveConnectedDevice(testDevice2.deviceId)

    // Assert
    checkMockViewActiveDevice(testDevice2)
  }

  @Test
  fun testRemovingDevice() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForCondition { service.getIDeviceFromSerialNumber(testDevice1.deviceId) != null }
    controller.setActiveConnectedDevice(testDevice1.deviceId)
    checkMockViewInitialState()

    // Act
    controller.setActiveConnectedDevice(null)

    // Assert
    val treeModel = pumpEventsAndWaitForFuture(mockView.mockModelListener.deviceTreeModelChangeTracker.consume())
    assertThat(treeModel).isNull()
  }

  @Test
  fun testKillProcesses() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForCondition { service.getIDeviceFromSerialNumber(testDevice1.deviceId) != null }
    controller.setActiveConnectedDevice(testDevice1.deviceId)
    checkMockViewInitialState()
    addClient(testDevice1, 5)
    val treeModel = pumpEventsAndWaitForFutures(mockView.mockModelListener.deviceTreeModelChangeTracker.consumeMany(3)).last()
    val rootEntry = checkNotNull(DeviceTreeNode.fromNode(treeModel?.root))
    assertThat(rootEntry.childCount).isEqualTo(1)

    // Act
    val processList = getListOfChildNodes(rootEntry)
    val clientStopped = processList[0] as ProcessInfoTreeNode
    val removeProcessList = listOf(clientStopped)
    testDevice1.setActivityManager { args, _ ->
      if ("force-stop" == args[0] && "package-${clientStopped.processInfo.pid}" == args[1]) {
        testDevice1.stopClient(clientStopped.processInfo.pid)
      }
    }
    mockView.killNodes(removeProcessList)

    // Assert
    val updatedTreeModel = pumpEventsAndWaitForFuture(mockView.mockModelListener.deviceTreeModelChangeTracker.consume())
    val updatedRootEntry = checkNotNull(DeviceTreeNode.fromNode(updatedTreeModel?.root))
    assertThat(updatedRootEntry.childCount).isEqualTo(0)
    assertPidIsNotInChildNodes(updatedRootEntry, clientStopped.processInfo.pid)
  }

  @Test
  fun testChangeInDeviceSelection() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    waitForCondition { service.getIDeviceFromSerialNumber(testDevice1.deviceId) != null }
    controller.setActiveConnectedDevice(testDevice1.deviceId)
    checkMockViewInitialState()

    // Act
    val testDevice2 = adb.attachDevice("test_device_02", "Google", "Pix3l", "versionX", "29")
    controller.setActiveConnectedDevice(testDevice2.deviceId)

    // Assert
    checkMockViewActiveDevice(testDevice2)
    controller.setActiveConnectedDevice(testDevice1.deviceId)
    checkMockViewActiveDevice(testDevice1)
  }

  private fun createController(): DeviceMonitorController {
    return DeviceMonitorController(project, model, mockView, service)
  }

  private fun checkMockViewInitialState() {
    checkMockViewActiveDevice(testDevice1)
  }

  private fun checkMockViewActiveDevice(activeDevice: DeviceState) {
    // Check the file system tree is displaying the file system of the first device
    val treeModel = pumpEventsAndWaitForFuture(mockView.mockModelListener.deviceTreeModelChangeTracker.consume())
    val rootEntry = checkNotNull(DeviceTreeNode.fromNode(treeModel?.root))
    assertThat(rootEntry.device.serialNumber).isEqualTo(activeDevice.deviceId)
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

  private fun getListOfChildNodes(rootNode: DeviceTreeNode): List<ProcessTreeNode> =
    rootNode.children().toList().map { it as ProcessTreeNode }

  private fun assertPidIsNotInChildNodes(rootNode: DeviceTreeNode, pid: Int) {
    val children = getListOfChildNodes(rootNode)
    for (child in children) {
      assertThat((child as ProcessInfoTreeNode).processInfo.pid).isNotEqualTo(pid)
    }
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