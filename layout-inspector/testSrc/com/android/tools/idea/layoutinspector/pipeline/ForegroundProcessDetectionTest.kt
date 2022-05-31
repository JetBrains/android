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
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Stream
import com.google.common.truth.Truth.assertThat
import com.intellij.util.concurrency.SameThreadExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ForegroundProcessDetectionTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("ForegroundProcessDetectionTest", transportService)

  private lateinit var transportClient: TransportClient

  private val stream1 = createFakeStream(1, deviceName = "device1")
  private val stream2 = createFakeStream(2, deviceName = "device2")

  private lateinit var helper: ForegroundProcessDetectionHelper

  @Before
  fun createPoller() {
    transportClient = TransportClient(grpcServerRule.name)
    helper = ForegroundProcessDetectionHelper(timer, transportService, listOf(stream1, stream2))
  }

  @Test
  fun testReceiveEventsFromSingleDevice() {
    val expectedForegroundProcesses = listOf(ForegroundProcess(1, "process1"), ForegroundProcess(2, "process2"))
    val receivedForegroundProcesses = mutableListOf<ForegroundProcess>()

    val expectedDevices = listOf(stream1.device.toDeviceDescriptor(), stream1.device.toDeviceDescriptor())
    val receivedDevices = mutableListOf<DeviceDescriptor>()

    val foregroundProcessLatch = CountDownLatch(expectedForegroundProcesses.size)

    val deviceModel = createDeviceModel(stream1.device.toDeviceDescriptor())
    val foregroundProcessDetection = ForegroundProcessDetection(deviceModel, transportClient, object : ForegroundProcessListener {
      override fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess) {
        receivedForegroundProcesses.add(foregroundProcess)
        receivedDevices.add(device)

        foregroundProcessLatch.countDown()
      }
    }, CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()), SameThreadExecutor.INSTANCE.asCoroutineDispatcher())

    foregroundProcessDetection.startListeningForEvents()

    connectStream(stream1)

    helper.sendEvents(stream1, expectedForegroundProcesses)

    // wait for events to be dispatched
    foregroundProcessLatch.await(2, TimeUnit.SECONDS)

    disconnectStream(stream1)

    foregroundProcessDetection.stopListeningForEvents()

    assertThat(receivedForegroundProcesses).isEqualTo(expectedForegroundProcesses)
    assertThat(receivedDevices).isEqualTo(expectedDevices)
    assertThat(helper.startCommandInvocationCount).isEqualTo(1)
    assertThat(helper.stopCommandInvocationCount).isEqualTo(0)
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

    val foregroundProcessDetection = ForegroundProcessDetection(deviceModel, transportClient, object : ForegroundProcessListener {
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
    }, CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()), SameThreadExecutor.INSTANCE.asCoroutineDispatcher())

    foregroundProcessDetection.startListeningForEvents()

    connectStream(stream1)
    connectStream(stream2)

    helper.sendEvent(stream1, foregroundProcess1)

    // wait for events to be dispatched
    latch1.await()

    assertThat(receivedForegroundProcesses).isEqualTo(listOf(foregroundProcess1))
    assertThat(receivedDevices).isEqualTo(listOf(stream1.device.toDeviceDescriptor()))

    foregroundProcessDetection.startPollingDevice(stream2.device.toDeviceDescriptor())

    helper.sendEvent(stream2, foregroundProcess2)
    helper.sendEvent(stream2, foregroundProcess3)

    // wait for events to be dispatched
    latch2.await()

    assertThat(receivedForegroundProcesses).isEqualTo(listOf(foregroundProcess1, foregroundProcess2, foregroundProcess3))
    assertThat(receivedDevices).isEqualTo(listOf(stream1.device.toDeviceDescriptor(), stream2.device.toDeviceDescriptor(), stream2.device.toDeviceDescriptor()))

    foregroundProcessDetection.stopListeningForEvents()

    assertThat(helper.startCommandInvocationCount).isEqualTo(2)
    assertThat(helper.stopCommandInvocationCount).isEqualTo(1)
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

  private fun connectStream(stream: Stream) {
    transportService.addDevice(stream.device)
    sendEvent(stream, createStreamConnectedEvent(stream))
  }

  private fun disconnectStream(stream: Stream) {
    sendEvent(stream, createStreamEndedEvent(stream))
  }

  private fun sendEvent(stream: Stream, event: Common.Event) {
    transportService.addEventToStream(stream.streamId, event)
  }

  private fun createStreamConnectedEvent(stream: Stream): Common.Event {
    val eventBuilder = Common.Event.newBuilder()
    return eventBuilder
      .setKind(Common.Event.Kind.STREAM)
      .setTimestamp(1)
      .setGroupId(stream.streamId)
      .setStream(
        eventBuilder.streamBuilder.setStreamConnected(
          eventBuilder.streamBuilder.streamConnectedBuilder
            .setStream(stream)
        )
      ).build()
  }

  private fun createStreamEndedEvent(stream: Stream): Common.Event {
    val eventBuilder = Common.Event.newBuilder()
    return eventBuilder
      .setKind(Common.Event.Kind.STREAM)
      .setTimestamp(1)
      .setIsEnded(true)
      .setGroupId(stream.streamId)
      .build()
  }

  /**
   * Helper class used to send LAYOUT_INSPECTOR_FOREGROUND_PROCESS events.
   * Only sends events to a stream if it is connected. If it's not the events are held in a queue waiting for the stream to connect.
   */
  class ForegroundProcessDetectionHelper(private val timer: FakeTimer, private val transportService: FakeTransportService, private val availableStreams: List<Stream>) {

    private val connectedStreamIds = mutableListOf<Long>()
    private var eventsQueue = mutableMapOf<Stream, MutableList<Common.Event>>()

    var startCommandInvocationCount = 0
    var stopCommandInvocationCount = 0

    init {
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
      val stream = availableStreams.find { it.streamId == streamId }!!

      connectedStreamIds.add(streamId)
      val eventsToSend = eventsQueue.remove(stream) ?: emptyList()
      eventsToSend.forEach { sendEvent(stream, it) }
    }

    private fun onStreamDisconnected(streamId: Long) {
      connectedStreamIds.remove(streamId)
    }

    fun sendEvent(stream: Stream, foregroundProcess: ForegroundProcess) {
      sendEvent(stream, createForegroundProcessEvent(foregroundProcess, 1, stream))
    }

    fun sendEvents(stream: Stream, foregroundProcesses: List<ForegroundProcess>) {
      foregroundProcesses.forEachIndexed { index, foregroundProcess ->
        sendEvent(stream, createForegroundProcessEvent(foregroundProcess, index, stream))
      }
    }

    private fun sendEvent(stream: Stream, foregroundProcessEvent: Common.Event) {
      assertThat(foregroundProcessEvent.kind).isEqualTo(Common.Event.Kind.LAYOUT_INSPECTOR_FOREGROUND_PROCESS)

      if (connectedStreamIds.contains(stream.streamId)) {
        transportService.addEventToStream(stream.streamId, foregroundProcessEvent)
      }
      else {
        if (eventsQueue.containsKey(stream)) {
          eventsQueue[stream]?.add(foregroundProcessEvent)
        }
        else {
          eventsQueue[stream] = mutableListOf(foregroundProcessEvent)
        }
      }
    }

    private fun createForegroundProcessEvent(foregroundProcess: ForegroundProcess, timestamp: Int, stream: Stream): Common.Event {
      val eventBuilder = Common.Event.newBuilder()
      return eventBuilder
        .setKind(Common.Event.Kind.LAYOUT_INSPECTOR_FOREGROUND_PROCESS)
        .setTimestamp(timestamp.toLong())
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
        override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
          block.invoke(command)
        }
      })
    }
  }
}