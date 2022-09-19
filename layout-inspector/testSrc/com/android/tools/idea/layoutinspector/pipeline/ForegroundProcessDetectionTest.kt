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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.metrics.ForegroundProcessDetectionMetrics
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.appinspection.DebugViewAttributes
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Event
import com.android.tools.profiler.proto.Common.Stream
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAutoConnectInfo
import com.intellij.testFramework.DisposableRule
import com.intellij.util.concurrency.SameThreadExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import layout_inspector.LayoutInspector
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ForegroundProcessDetectionTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("ForegroundProcessDetectionTest", transportService)

  private val monitor = MockitoKt.mock<InspectorClientLaunchMonitor>()

  private val disposableRule = DisposableRule()
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val inspectionRule = AppInspectionInspectorRule(disposableRule.disposable, projectRule)
  private val inspectorRule = LayoutInspectorRule(listOf(inspectionRule.createInspectorClientProvider { monitor }), projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule)
    .around(inspectionRule)
    .around(inspectorRule)
    .around(grpcServerRule)
    .around(disposableRule)!!

  private lateinit var transportClient: TransportClient

  private val stream1 = createFakeStream(1, deviceName = "device1")
  private val stream2 = createFakeStream(2, deviceName = "device2")
  private val stream3 = createFakeStream(3, deviceName = "device3")
  private val stream4 = createFakeStream(4, deviceName = "device4")

  private lateinit var helper: ForegroundProcessDetectionHelper

  @Before
  fun createPoller() {
    transportClient = TransportClient(grpcServerRule.name)
    helper = ForegroundProcessDetectionHelper(
      timer,
      transportService,
      setOf(
        ForegroundProcessDetectionStream(stream1, LayoutInspector.TrackingForegroundProcessSupported.SupportType.SUPPORTED),
        ForegroundProcessDetectionStream(stream2, LayoutInspector.TrackingForegroundProcessSupported.SupportType.SUPPORTED),
        ForegroundProcessDetectionStream(stream3, LayoutInspector.TrackingForegroundProcessSupported.SupportType.NOT_SUPPORTED),
        ForegroundProcessDetectionStream(stream4, LayoutInspector.TrackingForegroundProcessSupported.SupportType.UNKNOWN),
      )
    )
  }

  @Test
  fun testReceiveEventsFromSingleDevice() {
    val expectedForegroundProcesses = listOf(ForegroundProcess(1, "process1"), ForegroundProcess(2, "process2"))
    val receivedForegroundProcesses = mutableListOf<ForegroundProcess>()

    val expectedDevices = listOf(stream1.device.toDeviceDescriptor(), stream1.device.toDeviceDescriptor())
    val receivedDevices = mutableListOf<DeviceDescriptor>()

    val foregroundProcessLatch = CountDownLatch(expectedForegroundProcesses.size)
    val mockMetrics = mock(ForegroundProcessDetectionMetrics::class.java)

    val deviceModel = createDeviceModel(stream1.device.toDeviceDescriptor())
    val foregroundProcessDetection = ForegroundProcessDetection(
      inspectorRule.project,
      deviceModel,
      transportClient,
      mockMetrics,
      CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()),
      SameThreadExecutor.INSTANCE.asCoroutineDispatcher()
    )
    foregroundProcessDetection.foregroundProcessListeners.add(object : ForegroundProcessListener {
      override fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess) {
        receivedForegroundProcesses.add(foregroundProcess)
        receivedDevices.add(device)

        foregroundProcessLatch.countDown()
      }
    })

    foregroundProcessDetection.startListeningForEvents()

    connectDevice(stream1.device)

    helper.sendForegroundProcessEvents(stream1, expectedForegroundProcesses)

    // wait for events to be dispatched
    foregroundProcessLatch.await(2, TimeUnit.SECONDS)

    disconnectDevice(stream1.device)

    foregroundProcessDetection.stopListeningForEvents()

    assertThat(receivedForegroundProcesses).isEqualTo(expectedForegroundProcesses)
    assertThat(receivedDevices).isEqualTo(expectedDevices)
    assertThat(helper.startCommandInvocationCount).isEqualTo(1)
    assertThat(helper.startHandshakeCommandInvocationCount).isEqualTo(1)
    assertThat(helper.stopCommandInvocationCount).isEqualTo(0)

    // metrics
    val event = LayoutInspector.TrackingForegroundProcessSupported.newBuilder()
      .setSupportType(LayoutInspector.TrackingForegroundProcessSupported.SupportType.SUPPORTED)
      .build()
    verify(mockMetrics).logHandshakeResult(event)
  }

  @Test
  fun testReceiveEventsFromSelectedDevice() {
    val foregroundProcess1 = ForegroundProcess(1, "process1")
    val foregroundProcess2 = ForegroundProcess(2, "process2")
    val foregroundProcess3 = ForegroundProcess(3, "process3")

    val receivedForegroundProcesses = mutableListOf<ForegroundProcess>()
    val receivedDevices = mutableListOf<DeviceDescriptor>()

    val deviceModel = createDeviceModel(stream1.device.toDeviceDescriptor(), stream2.device.toDeviceDescriptor())

    val latch1 = CountDownLatch(1)
    val latch2 = CountDownLatch(2)

    val foregroundProcessDetection = ForegroundProcessDetection(
      inspectorRule.project,
      deviceModel,
      transportClient,
      ForegroundProcessDetectionMetrics(LayoutInspectorMetrics(inspectorRule.project)),
      CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()),
      SameThreadExecutor.INSTANCE.asCoroutineDispatcher()
    )
    foregroundProcessDetection.foregroundProcessListeners.add(object : ForegroundProcessListener {
      override fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess) {
        receivedForegroundProcesses.add(foregroundProcess)
        receivedDevices.add(device)

        if (device == stream1.device.toDeviceDescriptor()) {
          latch1.countDown()
        }
        else if (device == stream2.device.toDeviceDescriptor()) {
          latch2.countDown()
        }
      }
    })

    foregroundProcessDetection.startListeningForEvents()

    connectDevice(stream1.device)
    connectDevice(stream2.device)

    helper.sendForegroundProcessEvent(stream1, foregroundProcess1)

    // wait for events to be dispatched
    latch1.await()

    assertThat(receivedForegroundProcesses).isEqualTo(listOf(foregroundProcess1))
    assertThat(receivedDevices).isEqualTo(listOf(stream1.device.toDeviceDescriptor()))

    foregroundProcessDetection.startPollingDevice(stream2.device.toDeviceDescriptor())

    helper.sendForegroundProcessEvent(stream2, foregroundProcess2)
    helper.sendForegroundProcessEvent(stream2, foregroundProcess3)

    // wait for events to be dispatched
    latch2.await()

    assertThat(receivedForegroundProcesses).isEqualTo(listOf(foregroundProcess1, foregroundProcess2, foregroundProcess3))
    assertThat(receivedDevices).isEqualTo(listOf(stream1.device.toDeviceDescriptor(), stream2.device.toDeviceDescriptor(), stream2.device.toDeviceDescriptor()))

    foregroundProcessDetection.stopListeningForEvents()

    assertThat(helper.startCommandInvocationCount).isEqualTo(2)
    assertThat(helper.startHandshakeCommandInvocationCount).isEqualTo(2)
    assertThat(helper.stopCommandInvocationCount).isEqualTo(1)
  }

  @Test
  fun testHandshakeDeviceIsNotSupported() {
    val expectedForegroundProcesses = emptyList<ForegroundProcess>()
    val receivedForegroundProcesses = mutableListOf<ForegroundProcess>()

    val foregroundProcessLatch = CountDownLatch(1)
    val mockMetrics = mock(ForegroundProcessDetectionMetrics::class.java)

    val deviceModel = createDeviceModel(stream1.device.toDeviceDescriptor())
    val foregroundProcessDetection = ForegroundProcessDetection(
      inspectorRule.project,
      deviceModel,
      transportClient,
      mockMetrics,
      CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()),
      SameThreadExecutor.INSTANCE.asCoroutineDispatcher()
    )
    foregroundProcessDetection.foregroundProcessListeners.add(object : ForegroundProcessListener {
      override fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess) {
        receivedForegroundProcesses.add(foregroundProcess)

        foregroundProcessLatch.countDown()
      }
    })

    foregroundProcessDetection.startListeningForEvents()

    // device connected
    // stream3 was added as NOT_SUPPORTED to ForegroundProcessDetectionHelper
    connectDevice(stream3.device)

    // wait for events to be dispatched
    // TODO this latch is used a timeout, write better implementation
    foregroundProcessLatch.await(2, TimeUnit.SECONDS)

    foregroundProcessDetection.stopListeningForEvents()

    // no foreground process event received
    assertThat(receivedForegroundProcesses).isEqualTo(expectedForegroundProcesses)
    assertThat(helper.startHandshakeCommandInvocationCount).isEqualTo(1)
    // start command was never sent
    assertThat(helper.startCommandInvocationCount).isEqualTo(0)
    assertThat(helper.stopCommandInvocationCount).isEqualTo(0)

    // metrics
    val event = LayoutInspector.TrackingForegroundProcessSupported.newBuilder()
      .setSupportType(LayoutInspector.TrackingForegroundProcessSupported.SupportType.NOT_SUPPORTED)
      .setReasonNotSupported(LayoutInspector.TrackingForegroundProcessSupported.ReasonNotSupported.DUMPSYS_NOT_FOUND)
      .build()
    verify(mockMetrics).logHandshakeResult(event)
  }

  @Test
  fun testHandshakeDeviceIsUnknownSupported() {
    val expectedForegroundProcesses = emptyList<ForegroundProcess>()
    val receivedForegroundProcesses = mutableListOf<ForegroundProcess>()

    val foregroundProcessLatch = CountDownLatch(1)
    val mockMetrics = mock(ForegroundProcessDetectionMetrics::class.java)

    val deviceModel = createDeviceModel(stream1.device.toDeviceDescriptor())
    val foregroundProcessDetection = ForegroundProcessDetection(
      inspectorRule.project,
      deviceModel,
      transportClient,
      mockMetrics,
      CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()),
      SameThreadExecutor.INSTANCE.asCoroutineDispatcher()
    )
    foregroundProcessDetection.foregroundProcessListeners.add(object : ForegroundProcessListener {
      override fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess) {
        receivedForegroundProcesses.add(foregroundProcess)

        foregroundProcessLatch.countDown()
      }
    })

    foregroundProcessDetection.startListeningForEvents()

    // device connected
    // stream4 was added as UNKNOWN to ForegroundProcessDetectionHelper
    connectDevice(stream4.device)

    // wait for events to be dispatched
    // TODO this latch is used a timeout, write better implementation
    foregroundProcessLatch.await(3, TimeUnit.SECONDS)

    foregroundProcessDetection.stopListeningForEvents()

    // no foreground process event received
    assertThat(receivedForegroundProcesses).isEqualTo(expectedForegroundProcesses)
    // handshake is repeated because result was "UNKNOWN"
    assertThat(helper.startHandshakeCommandInvocationCount).isEqualTo(2)
    // start command was never sent
    assertThat(helper.startCommandInvocationCount).isEqualTo(0)
    assertThat(helper.stopCommandInvocationCount).isEqualTo(0)

    // metrics
    val event = LayoutInspector.TrackingForegroundProcessSupported.newBuilder()
      .setSupportType(LayoutInspector.TrackingForegroundProcessSupported.SupportType.UNKNOWN)
      .build()
    verify(mockMetrics).logHandshakeResult(event)
  }

  @Test
  fun testUnknownToSupportedIsLoggedInMetrics() {
    val foregroundProcessLatch1 = CountDownLatch(1)
    val foregroundProcessLatch2 = CountDownLatch(1)
    val mockMetrics = mock(ForegroundProcessDetectionMetrics::class.java)

    val deviceModel = createDeviceModel(stream1.device.toDeviceDescriptor())
    val foregroundProcessDetection = ForegroundProcessDetection(
      inspectorRule.project,
      deviceModel,
      transportClient,
      mockMetrics,
      CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()),
      SameThreadExecutor.INSTANCE.asCoroutineDispatcher()
    )

    foregroundProcessDetection.startListeningForEvents()

    // device connected
    // stream4 was added as UNKNOWN to ForegroundProcessDetectionHelper
    connectDevice(stream4.device)

    // wait for events to be dispatched
    // TODO this latch is used a timeout, write better implementation
    foregroundProcessLatch1.await(2, TimeUnit.SECONDS)

    // change type to SUPPORTED
    helper.availableStreams
      .first { it.stream == stream4 }
      .supportType = LayoutInspector.TrackingForegroundProcessSupported.SupportType.SUPPORTED

    // TODO this latch is used a timeout, write better implementation
    foregroundProcessLatch2.await(2, TimeUnit.SECONDS)

    disconnectDevice(stream4.device)

    foregroundProcessDetection.stopListeningForEvents()

    // handshake is repeated because result was "UNKNOWN"
    assertThat(helper.startHandshakeCommandInvocationCount).isEqualTo(2)

    // metrics
    val eventUnknown = LayoutInspector.TrackingForegroundProcessSupported.newBuilder()
      .setSupportType(LayoutInspector.TrackingForegroundProcessSupported.SupportType.UNKNOWN)
      .build()

    val eventSupported = LayoutInspector.TrackingForegroundProcessSupported.newBuilder()
      .setSupportType(LayoutInspector.TrackingForegroundProcessSupported.SupportType.SUPPORTED)
      .build()

    verify(mockMetrics).logHandshakeResult(eventUnknown)
    verify(mockMetrics).logHandshakeResult(eventSupported)
    verify(mockMetrics).logConversion(DynamicLayoutInspectorAutoConnectInfo.HandshakeUnknownConversion.UNKNOWN_TO_SUPPORTED)
    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testUnknownToNotSupportedIsLoggedInMetrics() {
    val foregroundProcessLatch1 = CountDownLatch(1)
    val foregroundProcessLatch2 = CountDownLatch(1)
    val mockMetrics = mock(ForegroundProcessDetectionMetrics::class.java)

    val deviceModel = createDeviceModel(stream1.device.toDeviceDescriptor())
    val foregroundProcessDetection = ForegroundProcessDetection(
      inspectorRule.project,
      deviceModel,
      transportClient,
      mockMetrics,
      CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()),
      SameThreadExecutor.INSTANCE.asCoroutineDispatcher()
    )

    foregroundProcessDetection.startListeningForEvents()

    // device connected
    // stream4 was added as UNKNOWN to ForegroundProcessDetectionHelper
    connectDevice(stream4.device)

    // wait for events to be dispatched
    // TODO this latch is used a timeout, write better implementation
    foregroundProcessLatch1.await(2, TimeUnit.SECONDS)

    // change type to SUPPORTED
    helper.availableStreams.first { it.stream == stream4 }.supportType = LayoutInspector.TrackingForegroundProcessSupported.SupportType.NOT_SUPPORTED

    // TODO this latch is used a timeout, write better implementation
    foregroundProcessLatch2.await(2, TimeUnit.SECONDS)

    disconnectDevice(stream4.device)

    foregroundProcessDetection.stopListeningForEvents()

    // handshake is repeated because result was "UNKNOWN"
    assertThat(helper.startHandshakeCommandInvocationCount).isEqualTo(2)

    // metrics
    val eventUnknown = LayoutInspector.TrackingForegroundProcessSupported.newBuilder()
      .setSupportType(LayoutInspector.TrackingForegroundProcessSupported.SupportType.UNKNOWN)
      .build()

    val eventNotSupported = LayoutInspector.TrackingForegroundProcessSupported.newBuilder()
      .setSupportType(LayoutInspector.TrackingForegroundProcessSupported.SupportType.NOT_SUPPORTED)
      .setReasonNotSupported(LayoutInspector.TrackingForegroundProcessSupported.ReasonNotSupported.DUMPSYS_NOT_FOUND)
      .build()

    verify(mockMetrics).logHandshakeResult(eventUnknown)
    verify(mockMetrics).logHandshakeResult(eventNotSupported)
    verify(mockMetrics).logConversion(DynamicLayoutInspectorAutoConnectInfo.HandshakeUnknownConversion.UNKNOWN_TO_NOT_SUPPORTED)
    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testUnknownIsNotResolvedIsLoggedInMetrics() {
    val foregroundProcessLatch1 = CountDownLatch(1)
    val foregroundProcessLatch2 = CountDownLatch(1)
    val mockMetrics = mock(ForegroundProcessDetectionMetrics::class.java)

    val deviceModel = createDeviceModel(stream1.device.toDeviceDescriptor())
    val foregroundProcessDetection = ForegroundProcessDetection(
      inspectorRule.project,
      deviceModel,
      transportClient,
      mockMetrics,
      CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()),
      SameThreadExecutor.INSTANCE.asCoroutineDispatcher()
    )

    foregroundProcessDetection.startListeningForEvents()

    // device connected
    // stream4 was added as UNKNOWN to ForegroundProcessDetectionHelper
    connectDevice(stream4.device)

    // wait for events to be dispatched
    // TODO this latch is used a timeout, write better implementation
    foregroundProcessLatch1.await(2, TimeUnit.SECONDS)

    // disconnect device
    disconnectDevice(stream4.device)

    // TODO this latch is used a timeout, write better implementation
    foregroundProcessLatch2.await(2, TimeUnit.SECONDS)

    foregroundProcessDetection.stopListeningForEvents()

    // handshake is repeated because result was "UNKNOWN"
    assertThat(helper.startHandshakeCommandInvocationCount).isEqualTo(2)

    // metrics
    val eventUnknown = LayoutInspector.TrackingForegroundProcessSupported.newBuilder()
      .setSupportType(LayoutInspector.TrackingForegroundProcessSupported.SupportType.UNKNOWN)
      .build()

    verify(mockMetrics).logHandshakeResult(eventUnknown)
    verify(mockMetrics).logConversion(DynamicLayoutInspectorAutoConnectInfo.HandshakeUnknownConversion.UNKNOWN_NOT_RESOLVED)
  }

  @Test
  fun testDeviceViewAttributeResetAfterDeviceDisconnect() = runWithFlagState(true) {
    inspectorRule.attachDevice(stream1.device.toDeviceDescriptor())
    inspectorRule.adbProperties.debugViewAttributes = "null"

    val foregroundProcessLatch1 = CountDownLatch(1)
    val foregroundProcessLatch2 = CountDownLatch(1)
    val mockMetrics = mock(ForegroundProcessDetectionMetrics::class.java)

    val deviceModel = createDeviceModel(stream1.device.toDeviceDescriptor())
    val foregroundProcessDetection = ForegroundProcessDetection(
      inspectorRule.project,
      deviceModel,
      transportClient,
      mockMetrics,
      CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()),
      SameThreadExecutor.INSTANCE.asCoroutineDispatcher()
    )

    foregroundProcessDetection.startListeningForEvents()

    val changed = DebugViewAttributes.getInstance().set(
      inspectorRule.project, stream1.device.toDeviceDescriptor().createProcess("fakeprocess")
    )
    assertThat(changed).isTrue()
    connectDevice(stream1.device)

    // wait for events to be dispatched
    // TODO this latch is used as a timeout, write better implementation
    foregroundProcessLatch1.await(2, TimeUnit.SECONDS)

    // disconnect device
    disconnectDevice(stream1.device)

    // TODO this latch is used as a timeout, write better implementation
    foregroundProcessLatch2.await(4, TimeUnit.SECONDS)

    foregroundProcessDetection.stopListeningForEvents()

    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(2)
    assertThat(inspectorRule.adbProperties.debugViewAttributes).isNull()
  }

  @Test
  fun testStopPollingSelectedDevice() {
    val foregroundProcess1 = ForegroundProcess(1, "process1")
    val foregroundProcess2 = ForegroundProcess(2, "process2")
    val foregroundProcess3 = ForegroundProcess(3, "process3")

    val receivedForegroundProcesses = mutableListOf<ForegroundProcess>()
    val receivedDevices = mutableListOf<DeviceDescriptor>()

    val deviceModel = createDeviceModel(stream1.device.toDeviceDescriptor(), stream2.device.toDeviceDescriptor())

    val latch1 = CountDownLatch(1)
    val latch2 = CountDownLatch(2)

    val foregroundProcessDetection = ForegroundProcessDetection(
      inspectorRule.project,
      deviceModel,
      transportClient,
      ForegroundProcessDetectionMetrics(LayoutInspectorMetrics(inspectorRule.project)),
      CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()),
      SameThreadExecutor.INSTANCE.asCoroutineDispatcher()
    )
    foregroundProcessDetection.foregroundProcessListeners.add(object : ForegroundProcessListener {
      override fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess) {
        receivedForegroundProcesses.add(foregroundProcess)
        receivedDevices.add(device)

        if (device == stream1.device.toDeviceDescriptor()) {
          latch1.countDown()
        }
        else if (device == stream2.device.toDeviceDescriptor()) {
          latch2.countDown()
        }
      }
    })

    foregroundProcessDetection.startListeningForEvents()

    connectDevice(stream1.device)
    connectDevice(stream2.device)

    helper.sendForegroundProcessEvent(stream1, foregroundProcess1)

    // wait for events to be dispatched
    latch1.await()

    assertThat(receivedForegroundProcesses).isEqualTo(listOf(foregroundProcess1))
    assertThat(receivedDevices).isEqualTo(listOf(stream1.device.toDeviceDescriptor()))

    foregroundProcessDetection.startPollingDevice(stream2.device.toDeviceDescriptor())

    helper.sendForegroundProcessEvent(stream2, foregroundProcess2)
    helper.sendForegroundProcessEvent(stream2, foregroundProcess3)

    // wait for events to be dispatched
    latch2.await()

    assertThat(receivedForegroundProcesses).isEqualTo(listOf(foregroundProcess1, foregroundProcess2, foregroundProcess3))
    assertThat(receivedDevices).isEqualTo(
      listOf(stream1.device.toDeviceDescriptor(), stream2.device.toDeviceDescriptor(), stream2.device.toDeviceDescriptor())
    )

    foregroundProcessDetection.stopPollingSelectedDevice()

    assertThat(helper.startCommandInvocationCount).isEqualTo(2)
    assertThat(helper.startHandshakeCommandInvocationCount).isEqualTo(2)
    // todo: refactor helper to have the list of disconnected processes instead of just the count
    assertThat(helper.stopCommandInvocationCount).isEqualTo(2)
    assertThat(deviceModel.selectedDevice).isNull()
  }

  @Test
  fun testStopInspector() {
    val foregroundProcessDetection = mock(ForegroundProcessDetection::class.java)

    val testProcessDiscovery = TestProcessDiscovery()
    testProcessDiscovery.addDevice(stream1.device.toDeviceDescriptor())
    val processModel = ProcessesModel(testProcessDiscovery)
    val deviceModel = DeviceModel(processModel)

    // test has device, no process
    deviceModel.selectedDevice = stream1.device.toDeviceDescriptor()
    processModel.selectedProcess = null

    stopInspector(inspectorRule.project, deviceModel, processModel, foregroundProcessDetection)

    verify(foregroundProcessDetection).stopPollingSelectedDevice()
    assertThat(processModel.selectedProcess).isNull()

    // test no device, has process
    deviceModel.selectedDevice = null
    processModel.selectedProcess = stream1.device.toDeviceDescriptor().createProcess("fake_process")

    stopInspector(inspectorRule.project, deviceModel, processModel, foregroundProcessDetection)

    verifyNoMoreInteractions(foregroundProcessDetection)
    assertThat(processModel.selectedProcess).isNull()
  }

  @Test
  fun testStopInspectorResetsFlag() = runWithFlagState(true) {
    inspectorRule.attachDevice(stream1.device.toDeviceDescriptor())
    val foregroundProcessDetection = mock(ForegroundProcessDetection::class.java)

    val fakeProcess = stream1.device.toDeviceDescriptor().createProcess("fake_process")

    val testProcessDiscovery = TestProcessDiscovery()
    testProcessDiscovery.addDevice(stream1.device.toDeviceDescriptor())
    val processModel = ProcessesModel(testProcessDiscovery)
    val deviceModel = DeviceModel(processModel)

    val debugViewAttributes = DebugViewAttributes.getInstance()
    debugViewAttributes.set(inspectorRule.project, fakeProcess)

    // test has device, no process
    deviceModel.selectedDevice = stream1.device.toDeviceDescriptor()
    processModel.selectedProcess = null

    stopInspector(inspectorRule.project, deviceModel, processModel, foregroundProcessDetection)

    verify(foregroundProcessDetection).stopPollingSelectedDevice()
    assertThat(processModel.selectedProcess).isNull()

    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(2)
    assertThat(inspectorRule.adbProperties.debugViewAttributes).isNull()

    // test no device, has process
    deviceModel.selectedDevice = null
    processModel.selectedProcess = fakeProcess

    stopInspector(inspectorRule.project, deviceModel, processModel, foregroundProcessDetection)

    verifyNoMoreInteractions(foregroundProcessDetection)
    assertThat(processModel.selectedProcess).isNull()
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(2)
    assertThat(inspectorRule.adbProperties.debugViewAttributes).isNull()
  }

  private fun createDeviceModel(vararg devices: DeviceDescriptor): DeviceModel {
    val testProcessDiscovery = TestProcessDiscovery()
    devices.forEach { testProcessDiscovery.addDevice(it) }
    return DeviceModel(ProcessesModel(testProcessDiscovery))
  }

  private fun createFakeStream(streamId: Long, deviceName: String, serial: String = UUID.randomUUID().toString(), isEmulator: Boolean = true): Stream {
    val device = getFakeDevice(streamId, deviceName).toBuilder()
      .setSerial(serial)
      .setManufacturer("FakeManufacturer")
      .setIsEmulator(isEmulator)
      .build()

    return Stream.newBuilder()
      .setStreamId(streamId)
      .setDevice(device)
      .build()
  }

  private fun getFakeDevice(id: Long = FakeTransportService.FAKE_DEVICE_ID, name: String): Common.Device {
    return Common.Device.newBuilder()
      .setDeviceId(id)
      .setSerial(name)
      .setApiLevel(AndroidVersion.VersionCodes.O)
      .setFeatureLevel(AndroidVersion.VersionCodes.O)
      .setModel(name)
      .setCpuAbi("arm64-v8a")
      .setState(Common.Device.State.ONLINE)
      .build()
  }

  private fun connectDevice(device: Common.Device) {
    transportService.addDevice(device)
  }

  private fun disconnectDevice(device: Common.Device) {
    val offlineDevice = device.toBuilder()
      .setState(Common.Device.State.OFFLINE)
      .build()

    transportService.updateDevice(device, offlineDevice)
  }

  /**
   * Helper class used to send LAYOUT_INSPECTOR_FOREGROUND_PROCESS events.
   * Only sends events to a stream if it is connected. If it's not the events are held in a queue waiting for the stream to connect.
   */
  // TODO refactor, this class makes tests hard to read
  private class ForegroundProcessDetectionHelper(private val timer: FakeTimer, private val transportService: FakeTransportService, val availableStreams: Set<ForegroundProcessDetectionStream>) {
    private val connectedStreamIds = mutableListOf<Long>()
    private var eventsQueue = mutableMapOf<Stream, MutableList<Event>>()

    var startHandshakeCommandInvocationCount = 0
    var startCommandInvocationCount = 0
    var stopCommandInvocationCount = 0

    var timestamp = 0L

    init {
      // Handler for the handshake command.
      transportService.setCommandHandler(Commands.Command.CommandType.IS_TRACKING_FOREGROUND_PROCESS_SUPPORTED) { command ->
        startHandshakeCommandInvocationCount += 1

        val foregroundProcessDetectionStream = availableStreams.find {
          it.stream.streamId == command.streamId
        } ?: throw java.lang.RuntimeException("Received command from unknown streamId: ${command.streamId}")

        val foregroundProcessEventBuilder = Event.newBuilder().layoutInspectorTrackingForegroundProcessSupportedBuilder
          .setSupportType(foregroundProcessDetectionStream.supportType)

        if (foregroundProcessDetectionStream.supportType == LayoutInspector.TrackingForegroundProcessSupported.SupportType.NOT_SUPPORTED) {
          foregroundProcessEventBuilder.reasonNotSupported = LayoutInspector.TrackingForegroundProcessSupported.ReasonNotSupported.DUMPSYS_NOT_FOUND
        }

        val event = Event.newBuilder()
          .setKind(Event.Kind.LAYOUT_INSPECTOR_TRACKING_FOREGROUND_PROCESS_SUPPORTED)
          .setLayoutInspectorTrackingForegroundProcessSupported(foregroundProcessEventBuilder.build())
          .setTimestamp(timestamp)
          .build()

        timestamp += 1
        sendEventImmediately(foregroundProcessDetectionStream.stream, event)
      }

      // Handler for the start command.
      transportService.setCommandHandler(Commands.Command.CommandType.START_TRACKING_FOREGROUND_PROCESS) { command ->
        startCommandInvocationCount += 1
        onStreamConnected(command.streamId)
      }

      // Handler for the stop command.
      transportService.setCommandHandler(Commands.Command.CommandType.STOP_TRACKING_FOREGROUND_PROCESS) { command ->
        stopCommandInvocationCount += 1
        onStreamDisconnected(command.streamId)
      }
    }

    private fun onStreamConnected(streamId: Long) {
      val stream = availableStreams.find { it.stream.streamId == streamId }!!.stream

      connectedStreamIds.add(streamId)
      val eventsToSend = eventsQueue.remove(stream) ?: emptyList()
      eventsToSend.forEach { sendForegroundProcessEvent(stream, it) }
    }

    private fun onStreamDisconnected(streamId: Long) {
      connectedStreamIds.remove(streamId)
    }

    fun sendForegroundProcessEvent(stream: Stream, foregroundProcess: ForegroundProcess) {
      sendForegroundProcessEvent(stream, createForegroundProcessEvent(foregroundProcess, stream))
    }

    fun sendForegroundProcessEvents(stream: Stream, foregroundProcesses: List<ForegroundProcess>) {
      foregroundProcesses.forEachIndexed { _, foregroundProcess ->
        sendForegroundProcessEvent(stream, createForegroundProcessEvent(foregroundProcess, stream))
      }
    }

    private fun sendForegroundProcessEvent(stream: Stream, event: Event) {
      assertThat(event.kind).isEqualTo(Event.Kind.LAYOUT_INSPECTOR_FOREGROUND_PROCESS)

      if (connectedStreamIds.contains(stream.streamId)) {
        sendEventImmediately(stream, event)
      }
      else {
        if (eventsQueue.containsKey(stream)) {
          eventsQueue[stream]?.add(event)
        }
        else {
          eventsQueue[stream] = mutableListOf(event)
        }
      }
    }

    private fun sendEventImmediately(stream: Stream, event: Event) {
      transportService.addEventToStream(stream.streamId, event)
    }

    private fun createForegroundProcessEvent(foregroundProcess: ForegroundProcess, stream: Stream): Event {
      val eventBuilder = Event.newBuilder()
      timestamp += 1
      return eventBuilder
        .setKind(Event.Kind.LAYOUT_INSPECTOR_FOREGROUND_PROCESS)
        .setTimestamp(timestamp)
        .setGroupId(stream.streamId)
        .setStream(
          eventBuilder.streamBuilder.setStreamConnected(
            eventBuilder.streamBuilder.streamConnectedBuilder
              .setStream(stream)
          )
        ).setLayoutInspectorForegroundProcess(
          eventBuilder.layoutInspectorForegroundProcessBuilder
            .setPid(foregroundProcess.pid.toString())
            .setProcessName(foregroundProcess.processName)
            .build()
        ).build()
    }

    private fun FakeTransportService.setCommandHandler(command: Commands.Command.CommandType, block: (Commands.Command) -> Unit) {
      setCommandHandler(command, object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
          block.invoke(command)
        }
      })
    }
  }
}

private data class ForegroundProcessDetectionStream(val stream: Stream, var supportType: LayoutInspector.TrackingForegroundProcessSupported.SupportType)

private fun runWithFlagState(desiredFlagState: Boolean, task: () -> Unit) {
  val flag = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED
  val flagPreviousState = flag.get()
  flag.override(desiredFlagState)

  task()

  // restore flag state
  flag.override(flagPreviousState)
}