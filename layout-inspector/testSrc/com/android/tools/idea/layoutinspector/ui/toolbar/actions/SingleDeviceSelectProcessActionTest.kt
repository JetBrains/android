/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui.toolbar.actions

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.ui.NO_PROCESS_ACTION
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.runningdevices.withAutoConnect
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SingleDeviceSelectProcessActionTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    projectRule.mockService(ActionManager::class.java)
  }

  @Test
  fun testProcessPickerNotVisibleIfDeviceSupportsForegroundProcessDetection() {
    val latch = CountDownLatch(1)
    val processName1 = "process1"
    val testProcessDiscovery = TestProcessDiscovery()
    val processModel =
      ProcessesModel(testProcessDiscovery) {
        if (it.name == processName1) {
          latch.countDown()
          true
        } else {
          false
        }
      }

    val fakeStream = createFakeStream(serial = "serial")
    val process1 = fakeStream.createFakeProcess(processName1, 1)
    testProcessDiscovery.addDevice(process1.device)
    testProcessDiscovery.fireConnected(process1)

    val deviceModel =
      DeviceModel(projectRule.testRootDisposable, processModel, setOf(process1.device))
    val processPicker =
      SingleDeviceSelectProcessAction(
        deviceModel,
        targetDeviceSerialNumber = "serial",
        onProcessSelected = {},
      )

    latch.await()

    val fakeEvent = createFakeEvent()
    processPicker.update(fakeEvent)
    assertThat(fakeEvent.presentation.isVisible).isFalse()
  }

  @Test
  fun testDeviceSerialNumberDoesNotMatchAnyDevice() {
    val processName1 = "process1"
    val testProcessDiscovery = TestProcessDiscovery()
    val processModel = ProcessesModel(testProcessDiscovery) { it.name == processName1 }

    val fakeStream = createFakeStream()
    val process1 = fakeStream.createFakeProcess(processName1, 1)
    testProcessDiscovery.addDevice(process1.device)
    testProcessDiscovery.fireConnected(process1)

    val deviceModel =
      DeviceModel(projectRule.testRootDisposable, processModel, setOf(process1.device))
    val processPicker =
      SingleDeviceSelectProcessAction(
        deviceModel,
        targetDeviceSerialNumber = "wrong serial number",
        onProcessSelected = {},
      )

    processPicker.updateActions(DataContext.EMPTY_CONTEXT)

    val selectProcessActions = processPicker.getChildren(null)
    assertThat(selectProcessActions).hasLength(1)
    assertThat(selectProcessActions[0]).isEqualTo(NO_PROCESS_ACTION)
  }

  @Test
  fun testProcessesAreAlphabeticallySorted() {
    val processName1 = "process1"
    val processName2 = "process2"
    val processName3 = "process3"
    val testProcessDiscovery = TestProcessDiscovery()
    val processModel = ProcessesModel(testProcessDiscovery) { it.name == processName1 }

    val fakeStream = createFakeStream()
    val process1 = fakeStream.createFakeProcess(processName1, 1)
    val process2 = fakeStream.createFakeProcess(processName2, 2)
    val process3 = fakeStream.createFakeProcess(processName3, 3)
    testProcessDiscovery.addDevice(process1.device)
    testProcessDiscovery.fireConnected(process3)
    testProcessDiscovery.fireConnected(process2)
    testProcessDiscovery.fireConnected(process1)

    val deviceModel =
      DeviceModel(projectRule.testRootDisposable, processModel, setOf(process1.device))
    val processPicker =
      SingleDeviceSelectProcessAction(
        deviceModel,
        targetDeviceSerialNumber = fakeStream.device.serial,
        onProcessSelected = {},
      )

    processPicker.updateActions(DataContext.EMPTY_CONTEXT)

    val selectProcessActions = processPicker.getChildren(null)
    assertThat(selectProcessActions).hasLength(3)
    assertThat(selectProcessActions[0].templateText).isEqualTo("process1")
    assertThat(selectProcessActions[1].templateText).isEqualTo("process2")
    assertThat(selectProcessActions[2].templateText).isEqualTo("process3")
  }

  @Test
  fun testProcessSelection() {
    val preferredProcessName = "myprocess"
    val testProcessDiscovery = TestProcessDiscovery()
    val processModel = ProcessesModel(testProcessDiscovery) { it.name == preferredProcessName }

    val fakeStream = createFakeStream()
    val process = fakeStream.createFakeProcess(preferredProcessName, 1)
    testProcessDiscovery.addDevice(process.device)
    testProcessDiscovery.fireConnected(process)

    val deviceModel =
      DeviceModel(projectRule.testRootDisposable, processModel, setOf(process.device))
    var processSelected = false
    val latch = CountDownLatch(1)
    val processPicker =
      SingleDeviceSelectProcessAction(
        deviceModel,
        targetDeviceSerialNumber = fakeStream.device.serial,
        onProcessSelected = {
          processSelected = true
          latch.countDown()
        },
      )

    processPicker.updateActions(DataContext.EMPTY_CONTEXT)

    val selectProcessActions = processPicker.getChildren(null)
    assertThat(selectProcessActions).hasLength(1)
    assertThat(selectProcessActions[0].templateText).isEqualTo("myprocess")

    selectProcessActions[0].actionPerformed(createFakeEvent())

    latch.await(2, TimeUnit.SECONDS)

    assertThat(processSelected).isTrue()
  }

  @Test
  fun testActionIsSelectedIfProcessIsSelectedInModel() {
    val latch = CountDownLatch(1)
    val preferredProcessName = "myprocess"
    val testProcessDiscovery = TestProcessDiscovery()
    val processModel =
      ProcessesModel(testProcessDiscovery) {
        if (it.name == preferredProcessName) {
          latch.countDown()
          true
        } else {
          false
        }
      }

    val fakeStream = createFakeStream()
    val process = fakeStream.createFakeProcess(preferredProcessName, 1)
    testProcessDiscovery.addDevice(process.device)
    testProcessDiscovery.fireConnected(process)

    val deviceModel =
      DeviceModel(projectRule.testRootDisposable, processModel, setOf(process.device))
    val processPicker =
      SingleDeviceSelectProcessAction(
        deviceModel,
        targetDeviceSerialNumber = fakeStream.device.serial,
        onProcessSelected = {},
      )

    processPicker.updateActions(DataContext.EMPTY_CONTEXT)

    val selectProcessActions = processPicker.getChildren(null)
    assertThat(selectProcessActions).hasLength(1)
    val isSelected = (selectProcessActions[0] as ToggleAction).isSelected(createFakeEvent())
    assertThat(isSelected).isTrue()
  }

  @Test
  fun testActionIsNotVisibleByDefault() = withAutoConnect {
    val processPicker =
      SingleDeviceSelectProcessAction(
        mock(),
        targetDeviceSerialNumber = "serial",
        onProcessSelected = {},
      )

    val fakeEvent = createFakeEvent()
    processPicker.update(fakeEvent)
    assertThat(fakeEvent.presentation.isVisible).isFalse()

    enableAutoConnect = false

    processPicker.update(fakeEvent)
    assertThat(fakeEvent.presentation.isVisible).isTrue()
  }
}

private fun createFakeDevice(name: String = FakeTransportService.FAKE_DEVICE_NAME): Common.Device {
  return Common.Device.newBuilder()
    .setDeviceId(FakeTransportService.FAKE_DEVICE_ID)
    .setSerial(name)
    .setApiLevel(AndroidVersion.VersionCodes.O)
    .setFeatureLevel(AndroidVersion.VersionCodes.O)
    .setModel(name)
    .setCpuAbi("arm64-v8a")
    .setState(Common.Device.State.ONLINE)
    .build()
}

private fun createFakeStream(
  deviceName: String = FakeTransportService.FAKE_DEVICE_NAME,
  serial: String = UUID.randomUUID().toString(),
  isEmulator: Boolean = true,
): Common.Stream {
  val device =
    createFakeDevice(deviceName).toBuilder().setSerial(serial).setIsEmulator(isEmulator).build()

  return Common.Stream.newBuilder().setDevice(device).build()
}

private fun Common.Stream.createFakeProcess(name: String? = null, pid: Int = 0): ProcessDescriptor {
  return TransportProcessDescriptor(
    this,
    FakeTransportService.FAKE_PROCESS.toBuilder()
      .setName(name ?: FakeTransportService.FAKE_PROCESS_NAME)
      .setPid(pid)
      .build(),
  )
}

private fun createFakeEvent(): AnActionEvent =
  AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT)
