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
package com.android.tools.idea.tests.gui.layoutinspector

import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.test.TestAppInspectorCommandHandler
import com.android.tools.idea.appinspection.test.createResponse
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.FakeInspector
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.FakeViewLayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.VIEW_LAYOUT_INSPECTOR_ID
import com.android.tools.idea.tests.util.ddmlib.AndroidDebugBridgeUtils
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.AgentData.Status.UNATTACHABLE
import com.intellij.testFramework.DisposableRule
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

val DEFAULT_PROCESS = Common.Process.newBuilder().apply {
  name = "myProcess"
  pid = 12345
  deviceId = 123456
  state = Common.Process.State.ALIVE
  exposureLevel = Common.Process.ExposureLevel.DEBUGGABLE
}.build()!!

val DEFAULT_DEVICE = Common.Device.newBuilder().apply {
  deviceId = 123456
  model = "My Model"
  manufacturer = "Google"
  serial = "123456"
  apiLevel = 29
  featureLevel = 29
  state = Common.Device.State.ONLINE
}.build()!!

private const val TEST_CHANNEL_NAME = "LayoutInspectorUiTest"

// TODO: Merge this with the features of LayoutInspectorTransportRule

/**
 * Rule providing mechanisms for testing the layout inspector. Notably, users of this rule should use [advanceTime] instead of using [timer]
 * or calling [com.android.tools.idea.transport.poller.TransportEventPoller.poll()] directly.
 */
class TransportRule(
  val timer: FakeTimer = FakeTimer(),
  private val adbRule: FakeAdbRule = FakeAdbRule().initAbdBridgeDuringSetup(false).closeServerDuringCleanUp(false),
  private val transportService: FakeTransportService = FakeTransportService(timer, false),
  private val grpcServer: FakeGrpcServer = FakeGrpcServer.createFakeGrpcServer(TEST_CHANNEL_NAME, transportService)
) : TestRule {
  /** If you set this to false before attaching a device, the attach will fail (return [UNATTACHABLE]) */
  var shouldConnectSuccessfully = true

  private val viewConnection = object : FakeInspector.Connection<LayoutInspectorViewProtocol.Event>() {
    override fun sendEvent(event: LayoutInspectorViewProtocol.Event) {
      val rawEvent = AppInspection.RawEvent.newBuilder().setContent(event.toByteString())
      val appEvent = AppInspection.AppInspectionEvent.newBuilder().setRawEvent(rawEvent).setInspectorId(VIEW_LAYOUT_INSPECTOR_ID)
      val commonEvent = Common.Event.newBuilder().apply {
        pid = DEFAULT_PROCESS.pid
        timestamp = timer.currentTimeNs
        isEnded = true
        kind = Common.Event.Kind.APP_INSPECTION_EVENT
        appInspectionEvent = appEvent.build()
      }.build()
      transportService.addEventToStream(DEFAULT_DEVICE.deviceId, commonEvent)
      timer.currentTimeNs += 1
    }
  }
  val viewInspector = FakeViewLayoutInspector(viewConnection)

  private val viewInspectorHandler = TestAppInspectorCommandHandler(
    timer,
    createInspectorResponse = { createCommand ->
      createCommand.createResponse(viewInspector.createResponseStatus)
    },
    rawInspectorResponse = { rawCommand ->
      val viewCommand = LayoutInspectorViewProtocol.Command.parseFrom(rawCommand.content)
      val viewResponse = viewInspector.handleCommand(viewCommand)
      val rawResponse = AppInspection.RawResponse.newBuilder().setContent(viewResponse.toByteString())
      AppInspection.AppInspectionResponse.newBuilder().setRawResponse(rawResponse)
    })

  private var attachHandler: CommandHandler = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      if (command.type == Commands.Command.CommandType.ATTACH_AGENT) {
        events.add(
          Common.Event.newBuilder().apply {
            pid = command.pid
            kind = Common.Event.Kind.AGENT
            agentData = Common.AgentData.newBuilder().setStatus(if (shouldConnectSuccessfully) ATTACHED else UNATTACHABLE).build()
          }.build()
        )
      }
    }
  }

  private val appInspectionHandler: CommandHandler = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      viewInspectorHandler.handleCommand(command, events)
    }
  }

  private val disposableRule = DisposableRule()

  fun withDeviceCommandHandler(handler: DeviceCommandHandler) = apply {
    adbRule.withDeviceCommandHandler(handler)
  }

  /**
   * Add the given process and stream to the transport service.
   */
  fun addProcess(device: Common.Device, process: Common.Process) {
    adbRule.attachDevice(device.deviceId.toString(), device.manufacturer, device.model, device.version, device.apiLevel.toString(),
                         DeviceState.HostConnectionType.USB)
    if (device.featureLevel >= 29) {
      transportService.addDevice(device)
      transportService.addProcess(device, process)
    }
  }

  /**
   * Remember a position in the event list for the specified stream.
   */
  fun saveEventPositionMark(streamId: Long) {
    transportService.saveEventPositionMark(streamId)
  }

  override fun apply(base: Statement, description: Description): Statement {
    return disposableRule.apply(grpcServer.apply(adbRule.apply(
      object : Statement() {
        override fun evaluate() {
          before()
          base.evaluate()
        }
      }, description
    ), description
    ), description)
  }

  private fun before() {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, attachHandler)
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, appInspectionHandler)

    // Start ADB with fake server and its port.
    AndroidDebugBridgeUtils.enableFakeAdbServerMode(adbRule.fakeAdbServerPort)
  }
}
