/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.testing.FakeAdbRule
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbDebugViewProperties
import com.android.tools.idea.layoutinspector.pipeline.adb.FakeShellCommandHandler
import com.android.tools.idea.layoutinspector.pipeline.appinspection.DebugViewAttributes
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessDetection
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class LayoutInspectorTest {

  private val device1 = Common.Device.newBuilder()
    .setDeviceId(1)
    .setManufacturer("man1")
    .setModel("mod1")
    .setSerial("serial1")
    .setIsEmulator(false)
    .setApiLevel(1)
    .setVersion("version1")
    .setCodename("codename1")
    .setState(Common.Device.State.ONLINE)
    .build()

  @get:Rule
  val disposableRule = DisposableRule()

  private val projectRule = ProjectRule()

  private val adbRule = FakeAdbRule()
  private val adbProperties: AdbDebugViewProperties = FakeShellCommandHandler().apply {
    adbRule.withDeviceCommandHandler(this)
  }
  private val adbService = AdbServiceRule(projectRule::project, adbRule)

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("ForegroundProcessDetectionTest", transportService)

  private val deviceToStreamMap = mapOf(
    device1 to createFakeStream(1, device1),
  )

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule)
    .around(adbRule)
    .around(adbService)

  private lateinit var layoutInspector: LayoutInspector
  private lateinit var deviceModel: DeviceModel
  private lateinit var processModel: ProcessesModel
  private lateinit var mockForegroundProcessDetection: ForegroundProcessDetection

  @Before
  fun setUp() {
    val scope = AndroidCoroutineScope(disposableRule.disposable)
    val (deviceModel, processModel) = createDeviceModel(device1)
    this.deviceModel = deviceModel
    this.processModel = processModel
    mockForegroundProcessDetection = mock<ForegroundProcessDetection>()
    val mockClientSettings = mock<InspectorClientSettings>()
    val mockLauncher = mock<InspectorClientLauncher>()
    val inspectorModel = InspectorModel(projectRule.project)
    val mockTreeSettings = mock<TreeSettings>()
    layoutInspector = LayoutInspector(
      scope,
      processModel,
      deviceModel,
      mockForegroundProcessDetection,
      mockClientSettings,
      mockLauncher,
      inspectorModel,
      mockTreeSettings
    )
  }

  @Test
  fun testDoNotShowErrorMessagesInDialog() {
    // Make sure that we don't accidentally have this on in a stable build
    assertThat(SHOW_ERROR_MESSAGES_IN_DIALOG).isFalse()
  }

  @Test
  fun testStopInspectorListenersAreCalled() {
    var called = false
    layoutInspector.stopInspectorListeners.add { called = true }

    layoutInspector.stopInspector()

    assertThat(called).isTrue()
  }

  @Test
  fun testStopInspector() {
    // test has device, no process
    deviceModel.setSelectedDevice(device1.toDeviceDescriptor())
    processModel.selectedProcess = null

    layoutInspector.stopInspector()

    verify(mockForegroundProcessDetection).stopPollingSelectedDevice()
    assertThat(processModel.selectedProcess).isNull()

    // test no device, has process
    deviceModel.setSelectedDevice(null)
    processModel.selectedProcess = device1.toDeviceDescriptor().createProcess("fake_process")

    layoutInspector.stopInspector()

    verifyNoMoreInteractions(mockForegroundProcessDetection)
    assertThat(processModel.selectedProcess).isNull()
  }

  @Test
  fun testStopInspectorResetsDebugViewAttributes() = runBlockingWithFlagState(true) {
    val scope = AndroidCoroutineScope(disposableRule.disposable)
    val (deviceModel, processModel) = createDeviceModel(device1)
    val mockForegroundProcessDetection = mock<ForegroundProcessDetection>()
    val mockClientSettings = mock<InspectorClientSettings>()
    val mockLauncher = mock<InspectorClientLauncher>()
    val inspectorModel = InspectorModel(projectRule.project)
    val mockTreeSettings = mock<TreeSettings>()
    val layoutInspector = LayoutInspector(
      scope,
      processModel,
      deviceModel,
      mockForegroundProcessDetection,
      mockClientSettings,
      mockLauncher,
      inspectorModel,
      mockTreeSettings
    )

    val fakeProcess = device1.toDeviceDescriptor().createProcess("fake_process")

    connectDevice(device1)

    val debugViewAttributes = DebugViewAttributes.getInstance()
    val changed = debugViewAttributes.set(projectRule.project, fakeProcess)
    assertThat(changed).isTrue()

    // test has device, no process
    deviceModel.setSelectedDevice(device1.toDeviceDescriptor())
    processModel.selectedProcess = null

    layoutInspector.stopInspector()

    verify(mockForegroundProcessDetection).stopPollingSelectedDevice()
    assertThat(processModel.selectedProcess).isNull()

    assertThat(adbProperties.debugViewAttributesChangesCount).isEqualTo(2)
    assertThat(adbProperties.debugViewAttributes).isNull()

    // test no device, has process
    deviceModel.setSelectedDevice(null)
    processModel.selectedProcess = fakeProcess

    layoutInspector.stopInspector()

    verifyNoMoreInteractions(mockForegroundProcessDetection)
    assertThat(processModel.selectedProcess).isNull()
    // the device is still connected, so the global flag should not be reset
    assertThat(adbProperties.debugViewAttributesChangesCount).isEqualTo(2)
    assertThat(adbProperties.debugViewAttributes).isNull()
  }

  /**
   * Connect a device to the transport and to adb.
   */
  private fun connectDevice(device: Common.Device, timestamp: Long? = null) {
    val transportDevice = deviceToStreamMap[device]!!.device

    if (timestamp != null) {
      transportService.addDevice(transportDevice, timestamp)
    }
    else {
      transportService.addDevice(transportDevice)
    }

    if (adbRule.bridge.devices.none { it.serialNumber == device.serial }) {
      adbRule.attachDevice(device.serial, device.manufacturer, device.model, device.version, device.apiLevel.toString())
    }
  }

  private fun createDeviceModel(vararg devices: Common.Device): Pair<DeviceModel, ProcessesModel> {
    val testProcessDiscovery = TestProcessDiscovery()
    devices.forEach { testProcessDiscovery.addDevice(it.toDeviceDescriptor()) }
    val processModel = ProcessesModel(testProcessDiscovery)
    return DeviceModel(disposableRule.disposable, processModel) to processModel
  }

  private fun createFakeStream(streamId: Long, device: Common.Device): Common.Stream {
    return Common.Stream.newBuilder()
      .setStreamId(streamId)
      .setDevice(device)
      .build()
  }

  @Suppress("SameParameterValue")
  private fun runBlockingWithFlagState(desiredFlagState: Boolean, task: suspend () -> Unit): Unit = runBlocking {
    val flag = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED
    val flagPreviousState = flag.get()
    flag.override(desiredFlagState)

    task()

    // restore flag state
    flag.override(flagPreviousState)
  }
}
