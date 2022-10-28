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
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDeviceListService
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDeviceNameRendererFactory
import com.android.tools.idea.device.explorer.monitor.mocks.MockDeviceMonitorView
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
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
  private lateinit var service: AdbDeviceListService
  private lateinit var mockView: MockDeviceMonitorView
  private lateinit var testDevice1: DeviceState

  @Before
  fun setup() {
    service = AdbDeviceListService.getInstance(project)
    model = DeviceMonitorModel()
    mockView = MockDeviceMonitorView(project, AdbDeviceNameRendererFactory(service), model)
    mockView.setup()
    testDevice1 = adb.attachDevice("test_device_01", "Google", "Pix3l", "versionX", "29")
  }

  @Test
  fun testIfControllerIsSetAsProjectKey() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare Act
    val controller = createController()

    // Assert
    Assert.assertEquals(controller, getProjectController(project))
  }

  @Test
  fun testStartingController() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(mockView.startRefreshTracker.consume())
    addClient(testDevice1, 200)

    // Assert
    checkMockViewInitialState(controller)
  }

  @Test
  fun testConnectingSecondDevice() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(mockView.startRefreshTracker.consume())
    addClient(testDevice1, 200)
    checkMockViewInitialState(controller)

    // Act
    val testDevice2 = adb.attachDevice("test_device_02", "Google", "Pix3l", "versionX", "29")
    addClient(testDevice2, 300)
    controller.selectActiveDevice(testDevice2.deviceId)

    // Assert
    val devices = pumpEventsAndWaitForFutures(mockView.modelListener.deviceAddedTracker.consumeMany(2))
    Truth.assertThat(devices.map { it.serialNumber }).containsExactlyElementsIn(listOf(testDevice1.deviceId, testDevice2.deviceId))
    Assert.assertEquals(2, mockView.deviceCombo.itemCount)
    Assert.assertEquals(1, mockView.deviceCombo.selectedIndex)
    checkMockViewActiveDevice(testDevice2)
  }

  @Test
  fun testRemovingDevice() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(mockView.startRefreshTracker.consume())
    addClient(testDevice1, 200)
    checkMockViewInitialState(controller)

    // Act
    adb.disconnectDevice(testDevice1.deviceId)

    // Assert
    pumpEventsAndWaitForFutures(mockView.modelListener.deviceRemovedTracker.consumeMany(1))
    Assert.assertEquals(0, mockView.deviceCombo.itemCount)
    pumpEventsAndWaitForFuture(mockView.viewListener.deviceNotSelectedTracker.consume())
    Assert.assertFalse(controller.hasActiveDevice())
  }

  @Test
  fun testDeviceFailing() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(mockView.startRefreshTracker.consume())
    addClient(testDevice1, 200)
    checkMockViewInitialState(controller)

    // Act
    testDevice1.deviceStatus = DeviceState.DeviceStatus.UNAUTHORIZED

    // Assert
    pumpEventsAndWaitForFuture(mockView.reportErrorRelatedToDeviceTracker.consume())
    Assert.assertEquals(1, mockView.deviceCombo.itemCount)
    Assert.assertTrue(controller.hasActiveDevice())
  }

  @Test
  fun testKillProcesses() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(mockView.startRefreshTracker.consume())
    addClient(testDevice1, 5)
    addClient(testDevice1, 20)
    addClient(testDevice1, 200)
    checkMockViewInitialState(controller)
    val rootEntry = DeviceTreeNode.fromNode(mockView.tree.model.root)
    checkNotNull(rootEntry)
    waitForCondition { rootEntry.childCount == 3 }

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
    waitForCondition { rootEntry.childCount == 2 }
    Assert.assertEquals(2, rootEntry.childCount)
    assertPidIsNotInChildNodes(rootEntry, clientStopped.processInfo.pid)
  }

  @Test
  fun testExpandingNodesWhenSelectingDevice() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(mockView.startRefreshTracker.consume())
    addClient(testDevice1, 200)
    checkMockViewInitialState(controller)

    // Act
    val testDevice2 = adb.attachDevice("test_device_02", "Google", "Pix3l", "versionX", "29")
    addClient(testDevice2, 300)
    controller.selectActiveDevice(testDevice2.deviceId)

    // Assert
    val devices = pumpEventsAndWaitForFutures(mockView.modelListener.deviceAddedTracker.consumeMany(2))
    Truth.assertThat(devices.map { it.serialNumber }).containsExactlyElementsIn(listOf(testDevice1.deviceId, testDevice2.deviceId))
    Assert.assertEquals(2, mockView.deviceCombo.itemCount)
    Assert.assertEquals(1, mockView.deviceCombo.selectedIndex)
    checkMockViewActiveDevice(testDevice2)

    mockView.viewListener.treeNodeExpandingTracker.clear()
    controller.selectActiveDevice(testDevice1.deviceId)
    val oldNode = pumpEventsAndWaitForFuture(mockView.viewListener.treeNodeExpandingTracker.consume())
    val oldDeviceNode = checkNotNull(DeviceTreeNode.fromNode(oldNode))
    Assert.assertEquals(testDevice1.deviceId, oldDeviceNode.device.serialNumber)
  }

  private fun createController(): DeviceMonitorController {
    return DeviceMonitorController(project, model, mockView, service)
  }

  private suspend fun checkMockViewInitialState(controller: DeviceMonitorController) {
    checkMockViewComboBox(controller)
    checkMockViewActiveDevice(testDevice1)
  }

  private fun checkMockViewComboBox(controller: DeviceMonitorController) {
    // Check we have 2 devices available
    val devices = pumpEventsAndWaitForFutures(mockView.modelListener.deviceAddedTracker.consumeMany(1))
    Truth.assertThat(devices.map { it.serialNumber }).containsExactlyElementsIn(listOf(testDevice1.deviceId))

    // The device combo box should contain both devices, and the first one should be selected
    Assert.assertEquals(1, mockView.deviceCombo.itemCount)

    // The first device should be selected automatically
    pumpEventsAndWaitForFuture(mockView.viewListener.deviceSelectedTracker.consume())
    Assert.assertEquals(0, mockView.deviceCombo.selectedIndex)
    Assert.assertTrue(controller.hasActiveDevice())
  }

  private suspend fun checkMockViewActiveDevice(activeDevice: DeviceState) {
    // Check the file system tree is displaying the file system of the first device
    waitForCondition { DeviceTreeNode.fromNode(mockView.tree.model.root) != null }
    val rootEntry = checkNotNull(DeviceTreeNode.fromNode(mockView.tree.model.root))
    Assert.assertEquals(rootEntry.device.serialNumber, activeDevice.deviceId)
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
      Assert.assertNotEquals((child as ProcessInfoTreeNode).processInfo.pid, pid)
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