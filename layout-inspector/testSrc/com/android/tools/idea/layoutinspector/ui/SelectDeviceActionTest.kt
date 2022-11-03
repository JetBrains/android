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
package com.android.tools.idea.layoutinspector.ui

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SelectDeviceActionTest {

  private val FAKE_MANUFACTURER_NAME = "FAKEMANUFACTURERNAME"

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    projectRule.mockService(ActionManager::class.java)
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
    isEmulator: Boolean = true
  ): Common.Stream {
    val device = createFakeDevice(deviceName).toBuilder()
      .setSerial(serial)
      .setManufacturer(FAKE_MANUFACTURER_NAME)
      .setIsEmulator(isEmulator)
      .build()

    return Common.Stream.newBuilder()
      .setDevice(device)
      .build()
  }

  private fun Common.Stream.createFakeProcess(name: String? = null, pid: Int = 0): ProcessDescriptor {
    return TransportProcessDescriptor(this, FakeTransportService.FAKE_PROCESS.toBuilder()
      .setName(name ?: FakeTransportService.FAKE_PROCESS_NAME)
      .setPid(pid)
      .build())
  }

  private fun createFakeEvent(): AnActionEvent = AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT)

  @Test
  fun testNoDevices() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier)
    val deviceModel = DeviceModel(projectRule.testRootDisposable, model)
    val selectDeviceAction = SelectDeviceAction(deviceModel, {}, {})
    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectDeviceAction.getChildren(null)
    Truth.assertThat(children).hasLength(2)
    Truth.assertThat(children[0].templateText).isEqualTo("No devices detected")
    Truth.assertThat(children[1].templateText).isEqualTo("Stop Inspector")
  }

  @Test
  fun displayTextForDevicesSetAsExpected() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier)

    val physicalStream = createFakeStream(isEmulator = false)
    val emulatorStream = createFakeStream(isEmulator = true)

    val deviceModel = DeviceModel(
      projectRule.testRootDisposable,
      model,
      setOf(physicalStream.device.toDeviceDescriptor(),emulatorStream.device.toDeviceDescriptor()))
    val selectDeviceAction = SelectDeviceAction(deviceModel, {}, {})

    testNotifier.addDevice(physicalStream.device.toDeviceDescriptor())
    testNotifier.addDevice(emulatorStream.device.toDeviceDescriptor())

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectDeviceAction.getChildren(null)
    Truth.assertThat(children).hasLength(3)
    // Physical devices prepend the manufacturer
    Truth.assertThat(children[0].templateText).isEqualTo("$FAKE_MANUFACTURER_NAME ${FakeTransportService.FAKE_DEVICE_NAME}")
    // Virtual devices hide the manufacturer
    Truth.assertThat(children[1].templateText).isEqualTo(FakeTransportService.FAKE_DEVICE_NAME)
    // Stop button
    Truth.assertThat(children[2].templateText).isEqualTo("Stop Inspector")
  }

  @Test
  fun listsDevicesInSortedOrder() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier)

    val fakeStream1 = createFakeStream("device3", isEmulator = true)
    val fakeStream2 = createFakeStream("device2", isEmulator = false)
    val fakeStream3 = createFakeStream("device1", isEmulator = true)
    testNotifier.addDevice(fakeStream1.device.toDeviceDescriptor())
    testNotifier.addDevice(fakeStream2.device.toDeviceDescriptor())
    testNotifier.addDevice(fakeStream3.device.toDeviceDescriptor())

    val deviceModel = DeviceModel(
      projectRule.testRootDisposable,
      model,
      setOf(fakeStream1.device.toDeviceDescriptor(), fakeStream2.device.toDeviceDescriptor(), fakeStream3.device.toDeviceDescriptor())
    )
    val selectDeviceAction = SelectDeviceAction(deviceModel, {}, {})

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectDeviceAction.getChildren(null)
    children.forEach { it.update(createFakeEvent()) }
    Truth.assertThat(children).hasLength(4)

    // Preferred processes first, then non-preferred, but everything sorted
    Truth.assertThat(children[0].templateText).isEqualTo("$FAKE_MANUFACTURER_NAME device2")
    Truth.assertThat(children[1].templateText).isEqualTo("device1")
    Truth.assertThat(children[2].templateText).isEqualTo("device3")
  }

  @Test
  fun deadDeviceFilteredOut() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier)

    val fakeStream = createFakeStream()
    testNotifier.addDevice(fakeStream.device.toDeviceDescriptor())

    val deviceModel = DeviceModel(
      projectRule.testRootDisposable,
      model,
      setOf(fakeStream.device.toDeviceDescriptor())
    )
    val selectDeviceAction = SelectDeviceAction(deviceModel, {}, {})

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    Truth.assertThat(selectDeviceAction.childrenCount).isEqualTo(2)
    val children1 = selectDeviceAction.getChildren(null)
    Truth.assertThat(children1[0].templateText).isEqualTo(FakeTransportService.FAKE_DEVICE_NAME)
    Truth.assertThat(children1[1].templateText).isEqualTo("Stop Inspector")

    testNotifier.removeDevice(fakeStream.device.toDeviceDescriptor())

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    Truth.assertThat(selectDeviceAction.childrenCount).isEqualTo(2)
    val children2 = selectDeviceAction.getChildren(null)
    Truth.assertThat(children2[0].templateText).isEqualTo("No devices detected")
    Truth.assertThat(children2[1].templateText).isEqualTo("Stop Inspector")
  }

  @Test
  fun selectStopInspection_firesCallback() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier) { it.name == "B" }

    val fakeStream = createFakeStream()
    val processB = fakeStream.createFakeProcess("B", 101)
    testNotifier.addDevice(processB.device)
    testNotifier.fireConnected(processB)

    val deviceModel = DeviceModel(
      projectRule.testRootDisposable,
      model,
      setOf(processB.device)
    )
    deviceModel.selectedDevice = processB.device
    val callbackFiredLatch = CountDownLatch(1)
    val selectDeviceAction = SelectDeviceAction(deviceModel, {}, onDetachAction = {
      callbackFiredLatch.countDown()
    }, onProcessSelected = {})

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectDeviceAction.getChildren(null)
    Truth.assertThat(children).hasLength(2)
    val device = children[0]
    Truth.assertThat(device.templateText).isEqualTo("FakeDevice")

    val stop = children[1]
    Truth.assertThat(stop.templateText).isEqualTo("Stop Inspector")

    stop.actionPerformed(createFakeEvent())
    callbackFiredLatch.await()
  }

  @Test
  fun selectStopInspection_changesStateBasedOnSelectedDeviceAndSelectedProcess() {
    val testNotifier = TestProcessDiscovery()
    val processesModel = ProcessesModel(testNotifier) { it.name == "B" }

    val fakeStream = createFakeStream()
    val process = fakeStream.createFakeProcess("B", 101)
    testNotifier.addDevice(process.device)
    testNotifier.fireConnected(process)

    val deviceModel = DeviceModel(
      projectRule.testRootDisposable,
      processesModel,
      setOf(process.device)
    )
    val callbackFiredLatch = CountDownLatch(1)
    val selectDeviceAction = SelectDeviceAction(deviceModel, {}, onDetachAction = {
      deviceModel.selectedDevice = null
      processesModel.selectedProcess = null
      callbackFiredLatch.countDown()
    }, onProcessSelected = {})

    // has selected device, but no selected process
    deviceModel.selectedDevice = process.device

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectDeviceAction.getChildren(null)
    Truth.assertThat(children).hasLength(2)
    val device = children[0]
    Truth.assertThat(device.templateText).isEqualTo("FakeDevice")

    val stop = children[1]
    stop as SelectDeviceAction.DetachInspectorAction
    Truth.assertThat(stop.isEnabled()).isTrue()
    Truth.assertThat(stop.templateText).isEqualTo("Stop Inspector")

    // stop inspector
    stop.actionPerformed(createFakeEvent())
    callbackFiredLatch.await()

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children2 = selectDeviceAction.getChildren(null)
    val stop2 = children2[1]
    stop2 as SelectDeviceAction.DetachInspectorAction
    Truth.assertThat(stop2.templateText).isEqualTo("Stop Inspector")
    Truth.assertThat(stop2.isEnabled()).isFalse()

    // no selected device, but has selected process
    processesModel.selectedProcess = process

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children3 = selectDeviceAction.getChildren(null)
    val stop3 = children3[1]
    stop3 as SelectDeviceAction.DetachInspectorAction
    Truth.assertThat(stop3.templateText).isEqualTo("Stop Inspector")
    Truth.assertThat(stop3.isEnabled()).isTrue()

    // stop inspector
    stop.actionPerformed(createFakeEvent())
    callbackFiredLatch.await()

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children4 = selectDeviceAction.getChildren(null)
    val stop4 = children4[1]
    stop4 as SelectDeviceAction.DetachInspectorAction
    Truth.assertThat(stop4.templateText).isEqualTo("Stop Inspector")
    Truth.assertThat(stop4.isEnabled()).isFalse()
  }

  @Test
  fun testCustomAttribution() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier)

    val stream = createFakeStream()
    testNotifier.addDevice(stream.device.toDeviceDescriptor())

    val deviceModel = DeviceModel(
      projectRule.testRootDisposable,
      model,
      setOf(stream.device.toDeviceDescriptor())
    )

    val deviceAttribution: (DeviceDescriptor, AnActionEvent) -> Unit = MockitoKt.mock()

    val selectDeviceAction = SelectDeviceAction(deviceModel, {}, {}, customDeviceAttribution = deviceAttribution)

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectDeviceAction.getChildren(null)
    Truth.assertThat(children).hasLength(2)
    val deviceAction = children[0] as ToggleAction
    val event1 = update(deviceAction)
    Mockito.verify(deviceAttribution).invoke(MockitoKt.eq(stream.device.toDeviceDescriptor()), MockitoKt.eq(event1))
  }

  @Test
  fun selectDevice_firesCallback() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier)

    val fakeStream = createFakeStream()
    testNotifier.addDevice(fakeStream.device.toDeviceDescriptor())

    val deviceModel = DeviceModel(
      projectRule.testRootDisposable,
      model,
      setOf(fakeStream.device.toDeviceDescriptor())
    )
    val callbackFiredLatch = CountDownLatch(1)
    var actionPerformed = false
    val selectDeviceAction = SelectDeviceAction(
      deviceModel,
      onDeviceSelected = {
        actionPerformed = true
        callbackFiredLatch.countDown()
      },
      onProcessSelected = { })

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectDeviceAction.getChildren(null)
    Truth.assertThat(children).hasLength(2)
    val device = children[0]
    Truth.assertThat(device.templateText).isEqualTo("FakeDevice")

    device.actionPerformed(createFakeEvent())

    callbackFiredLatch.await(2, TimeUnit.SECONDS)
    Truth.assertThat(actionPerformed).isTrue()
  }

  @Test
  fun deviceThatDoesNotSupportForegroundProcessDetectionShowProcessPicker() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier)

    val physicalStream = createFakeStream(isEmulator = false)
    val emulatorStream = createFakeStream(isEmulator = true)
    val process = emulatorStream.createFakeProcess("A", 100)
    testNotifier.fireConnected(process)

    testNotifier.addDevice(physicalStream.device.toDeviceDescriptor())
    testNotifier.addDevice(emulatorStream.device.toDeviceDescriptor())

    val deviceModel = DeviceModel(
      projectRule.testRootDisposable,
      model,
      setOf(physicalStream.device.toDeviceDescriptor())
    )
    val selectDeviceAction = SelectDeviceAction(deviceModel, {}, {})

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectDeviceAction.getChildren(null)
    Truth.assertThat(children).hasLength(3)
    // Physical devices prepend the manufacturer
    Truth.assertThat(children[0].templateText).isEqualTo("$FAKE_MANUFACTURER_NAME ${FakeTransportService.FAKE_DEVICE_NAME}")
    // Virtual devices hide the manufacturer
    Truth.assertThat(children[1].templateText).isEqualTo(FakeTransportService.FAKE_DEVICE_NAME)
    // Stop button
    Truth.assertThat(children[2].templateText).isEqualTo("Stop Inspector")

    run {
      val processAction = (children[1] as ActionGroup).getChildren(null)[0]
      Truth.assertThat(processAction.templateText).isEqualTo("A")
    }

    testNotifier.fireDisconnected(process)
    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)

    run {
      val processAction = (selectDeviceAction.getChildren(null)[1] as ActionGroup).getChildren(null)[0]
      Truth.assertThat(processAction.templateText).isEqualTo("No debuggable processes detected")
    }
  }

  @Test
  fun selectProcess_firesCallback() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier)

    val physicalStream = createFakeStream(isEmulator = false)
    val emulatorStream = createFakeStream(isEmulator = true)
    val process = emulatorStream.createFakeProcess("A", 100)
    testNotifier.fireConnected(process)

    testNotifier.addDevice(physicalStream.device.toDeviceDescriptor())
    testNotifier.addDevice(emulatorStream.device.toDeviceDescriptor())

    val callbackFiredLatch = CountDownLatch(1)
    val deviceModel = DeviceModel(
      projectRule.testRootDisposable,
      model,
      setOf(physicalStream.device.toDeviceDescriptor())
    )
    var actionPerformed = false
    val selectDeviceAction = SelectDeviceAction(deviceModel, {}, {
      actionPerformed = true
      callbackFiredLatch.countDown()
    })

    selectDeviceAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectDeviceAction.getChildren(null)

    val processAction = (children[1] as ActionGroup).getChildren(null)[0]
    Truth.assertThat(processAction.templateText).isEqualTo("A")

    processAction.actionPerformed(createFakeEvent())

    callbackFiredLatch.await(2, TimeUnit.SECONDS)
    Truth.assertThat(actionPerformed).isTrue()
  }

  private fun update(action: AnAction): AnActionEvent {
    val presentation = action.templatePresentation.clone()
    val event: AnActionEvent = MockitoKt.mock()
    Mockito.`when`(event.presentation).thenReturn(presentation)
    action.update(event)
    return event
  }
}