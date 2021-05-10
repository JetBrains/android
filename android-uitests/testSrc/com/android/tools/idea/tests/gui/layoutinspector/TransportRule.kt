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
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.tests.util.ddmlib.AndroidDebugBridgeUtils
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.AgentData.Status.UNATTACHABLE
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

val DEFAULT_PROCESS = Common.Process.newBuilder().apply {
  name = "myProcess"
  pid = 12345
  deviceId = 123456
  state = Common.Process.State.ALIVE
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

private const val TEST_CHANNEL_NAME = "LayoutInspectorTestChannel"

// TODO: Merge this with the features of LayoutInspectorTransportRule

/**
 * Rule providing mechanisms for testing the layout inspector. Notably, users of this rule should use [advanceTime] instead of using [timer]
 * or calling [com.android.tools.idea.transport.poller.TransportEventPoller.poll()] directly.
 */
class TransportRule(
  private val timer: FakeTimer = FakeTimer(),
  private val adbRule: FakeAdbRule = FakeAdbRule().initAbdBridgeDuringSetup(false).closeServerDuringCleanUp(false),
  private val transportService: FakeTransportService = FakeTransportService(timer),
  private val grpcServer: FakeGrpcServer = FakeGrpcServer.createFakeGrpcServer(TEST_CHANNEL_NAME, transportService)
) : TestRule {
  override fun apply(base: Statement, description: Description) = base

    /* Disabled pending rewrite to app inspection: b/187734852
      /** If you set this to false before attaching a device, the attach will fail (return [UNATTACHABLE]) */
      var shouldConnectSuccessfully = true

      private val commandHandlers = mutableMapOf<
        LayoutInspectorProto.LayoutInspectorCommand.Type,
        (Commands.Command, MutableList<Common.Event>) -> Unit>()

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

      private var inspectorHandler: CommandHandler = object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
          val handler = commandHandlers[command.layoutInspector.type]
          handler?.invoke(command, events)
        }
      }

      /**
       * Add a specific [LayoutInspectorProto.LayoutInspectorCommand] handler.
       */
      fun withCommandHandler(type: LayoutInspectorProto.LayoutInspectorCommand.Type,
                             handler: (Commands.Command, MutableList<Common.Event>) -> Unit) =
        apply { commandHandlers[type] = handler }

      fun withFile(id: Int, bytes: ByteArray) = apply {
        transportService.addFile(id.toString(), ByteString.copyFrom(bytes))
      }

      fun addEventToStream(device: Common.Device, event: Common.Event) {
        transportService.addEventToStream(device.deviceId, event)
      }

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

      /**
       * Remove all events added added after the previously saved mark in the events for the specified stream.
       *
       * This is useful if we want the process event to remain in a Bleak test.
       */
      fun revertToEventPositionMark(streamId: Long) {
        transportService.revertToEventPositionMark(streamId)
      }

      override fun apply(base: Statement, description: Description): Statement {
        return grpcServer.apply(adbRule.apply(
          object: Statement() {
            override fun evaluate() {
              before()
              base.evaluate()
            }
          }, description
        ), description)
      }

      private fun before() {
        transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, attachHandler)
        transportService.setCommandHandler(Commands.Command.CommandType.LAYOUT_INSPECTOR, inspectorHandler)

        // Start ADB with fake server and its port.
        AndroidDebugBridgeUtils.enableFakeAdbServerMode(adbRule.fakeAdbServerPort)
      }*/
}