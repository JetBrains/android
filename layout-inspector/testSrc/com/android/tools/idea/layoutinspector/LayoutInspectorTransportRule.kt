/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.FpsTimer
import com.android.tools.idea.layoutinspector.legacydevice.LegacyClient
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.transport.DefaultInspectorClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.NamedExternalResource
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.AgentData.Status.UNATTACHABLE
import org.junit.runner.Description
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Rule providing mechanisms for testing the layout inspector. Notably, users of this rule should use [advanceTime] instead of using [timer]
 * or calling [com.android.tools.idea.transport.poller.TransportEventPoller.poll()] directly.
 */
class LayoutInspectorTransportRule(
  private val timer: FakeTimer,
  private val adbRule: FakeAdbRule,
  private val transportService: FakeTransportService,
  private val grpcServer: FakeGrpcServer,
  private val projectRule: AndroidProjectRule
) : NamedExternalResource() {

  lateinit var inspectorClient: InspectorClient
  var inspectorModel: () -> InspectorModel = { model(projectRule.project) { view(0L) } }

  /** If you set this to false before attaching a device, the attach will fail (return [UNATTACHABLE]) */
  var shouldConnectSuccessfully = true

  // "2" since it's called for debug_view_attributes and debug_view_attributes_application_package
  private val unsetSettingsLatch = CountDownLatch(2)
  private val scheduler = VirtualTimeScheduler()
  private var inspectorClientFactory: () -> InspectorClient = { DefaultInspectorClient(inspectorModel(), grpcServer.name, scheduler) }

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

  private val startedLatch = CountDownLatch(1)
  private var inspectorHandler: CommandHandler = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      if (command.layoutInspector.type == LayoutInspectorProto.LayoutInspectorCommand.Type.START) {
        startedLatch.countDown()
      }
    }
  }

  init {
    adbRule.withDeviceCommandHandler(object : DeviceCommandHandler("shell") {
      override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
        if (args == "settings put global debug_view_attributes 1") {
          com.android.fakeadbserver.CommandHandler.writeOkay(socket.getOutputStream())
          return true
        }
        else if (args == "settings delete global debug_view_attributes" ||
                 args == "settings delete global debug_view_attributes_application_package") {
          com.android.fakeadbserver.CommandHandler.writeOkay(socket.getOutputStream())
          unsetSettingsLatch.countDown()
          return true
        }
        return false
      }
    })

    scheduler.scheduleAtFixedRate({ timer.step() }, 0, FpsTimer.ONE_FRAME_IN_NS, TimeUnit.NANOSECONDS)
  }

  /**
   * Create a [LegacyClient] rather than a [DefaultInspectorClient]
   */
  fun withLegacyClient() = apply { inspectorClientFactory = { LegacyClient(projectRule.project) } }

  /**
   * The default attach handler just attaches (or fails if [shouldConnectSuccessfully] is false). Use this if you want to do something else.
   */
  fun withAttachHandler(handler: CommandHandler) = apply { attachHandler = handler }

  /**
   * By default we get a model with one empty view. Use this if you want anything else.
   */
  fun withModel(modelFactory: () -> InspectorModel) {
    inspectorModel = modelFactory
  }

  /**
   * Advance the virtual time of the test. This will cause the [transportService] poller to fire, and will also advance [timer].
   */
  fun advanceTime(interval: Long, unit: TimeUnit) {
    scheduler.advanceBy(interval, unit)
  }

  /**
   * Wait until the device receives [LayoutInspectorProto.LayoutInspectorCommand.Type.START] (necessary if the request is made on a worker
   * thread).
   */
  fun waitForStart() {
    if (!shouldConnectSuccessfully) {
      return
    }
    startedLatch.await()
  }

  /**
   * Add the given process and stream to the transport service.
   */
  fun addProcess(stream: Common.Stream, process: Common.Process) {
    val device = stream.device
    adbRule.attachDevice(device.deviceId.toString(), device.manufacturer, device.model, device.version, device.apiLevel.toString(),
                         DeviceState.HostConnectionType.USB)
    transportService.addDevice(device)
    transportService.addProcess(device, process)
  }

  override fun before(description: Description) {
    inspectorClientFactory.let { inspectorClient = it() }
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, attachHandler)
    transportService.setCommandHandler(Commands.Command.CommandType.LAYOUT_INSPECTOR, inspectorHandler)
  }

  override fun after(description: Description) {
    if (inspectorClient.isConnected) {
      val processDone = CountDownLatch(1)
      inspectorClient.registerProcessChanged { processDone.countDown() }
      inspectorClient.disconnect()
      processDone.await()
      waitForUnsetSettings()
    }
  }

  private fun waitForUnsetSettings() {
    unsetSettingsLatch.await()
  }
}