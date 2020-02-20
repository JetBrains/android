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
package com.android.tools.idea.appinspection.test

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.api.AppInspectionTarget
import com.android.tools.idea.appinspection.api.TestInspectorClient
import com.android.tools.idea.appinspection.api.TestInspectorCommandHandler
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.appinspection.internal.attachAppInspectionTarget
import com.android.tools.idea.appinspection.internal.launchInspectorForTest
import com.android.tools.idea.testing.NamedExternalResource
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.idea.transport.manager.TransportPoller
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.util.concurrent.ListenableFuture
import org.junit.runner.Description
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

val TEST_JAR = AppInspectorJar("test")

/**
 * Rule providing all of the underlying components of App Inspection including [executorService], [streamChannel], [transport] and [client].
 *
 * It also provides a number of useful utility functions for tests. Normally used in conjunction with [FakeGrpcServer] rule.
 */
class AppInspectionServiceRule(
  private val timer: FakeTimer,
  private val transportService: FakeTransportService,
  private val grpcServer: FakeGrpcServer
) : NamedExternalResource() {
  lateinit var client: TransportClient
  lateinit var executorService: ThreadPoolExecutor
  lateinit var streamChannel: TransportStreamChannel
  lateinit var transport: AppInspectionTransport
  lateinit var jarCopier: AppInspectionTestUtils.TestTransportJarCopier

  private val stream = Common.Stream.newBuilder()
    .setType(Common.Stream.Type.DEVICE)
    .setStreamId(FakeTransportService.FAKE_DEVICE_ID)
    .setDevice(FakeTransportService.FAKE_DEVICE)
    .build()
  private val process = FakeTransportService.FAKE_PROCESS!!

  private val defaultAttachHandler = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      events.add(
        Common.Event.newBuilder()
          .setCommandId(command.commandId)
          .setPid(command.pid)
          .setKind(Common.Event.Kind.AGENT)
          .setAgentData(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build())
          .build()
      )
    }
  }

  override fun before(description: Description) {
    client = TransportClient(grpcServer.name)
    streamChannel =
      TransportStreamChannel(stream, TransportPoller.createPoller(client.transportStub, TimeUnit.MILLISECONDS.toNanos(100)))
    executorService = ThreadPoolExecutor(0, 1, 1L, TimeUnit.SECONDS, LinkedBlockingQueue())
    transport = AppInspectionTransport(client, stream, process, executorService, streamChannel)
    jarCopier = AppInspectionTestUtils.TestTransportJarCopier
  }

  override fun after(description: Description) {
    TransportPoller.removePoller(streamChannel.poller)
    executorService.shutdownNow()
  }

  /**
   * Launches a new [AppInspectionTarget] and sets the optional [commandHandler].
   */
  fun launchTarget(
    commandHandler: CommandHandler = TestInspectorCommandHandler(timer)
  ): ListenableFuture<AppInspectionTarget> {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, defaultAttachHandler)
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, commandHandler)
    val targetFuture = attachAppInspectionTarget(transport, jarCopier)
    timer.currentTimeNs += 1
    return targetFuture
  }

  /**
   * Launches a new inspector connection.
   *
   * [commandHandler], and [eventListener] can be provided to customize behavior of how commands and events are received.
   */
  fun launchInspectorConnection(
    inspectorId: String = INSPECTOR_ID,
    commandHandler: CommandHandler = TestInspectorCommandHandler(timer),
    eventListener: AppInspectorClient.EventListener = TestInspectorEventListener()
  ): AppInspectorClient.CommandMessenger {
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, commandHandler)
    val messenger = launchInspectorForTest(inspectorId, transport, timer.currentTimeNs) {
      TestInspectorClient(it, eventListener)
    }.messenger
    timer.currentTimeNs += 1
    return messenger
  }

  fun addEvent(event: Common.Event) {
    val modifiedEvent = event.toBuilder().setPid(process.pid).setTimestamp(timer.currentTimeNs).build()
    transportService.addEventToStream(stream.streamId, modifiedEvent)
    timer.currentTimeNs += 1
  }

  /**
   * Generate a fake [Common.Event] using the provided [appInspectionResponse].
   */
  fun addAppInspectionResponse(appInspectionResponse: AppInspection.AppInspectionResponse) {
    transportService.addEventToStream(
      stream.streamId,
      Common.Event.newBuilder()
        .setKind(Common.Event.Kind.APP_INSPECTION_RESPONSE)
        .setPid(process.pid)
        .setTimestamp(timer.currentTimeNs)
        .setIsEnded(true)
        .setAppInspectionResponse(appInspectionResponse)
        .build()
    )
    timer.currentTimeNs += 1
  }

  /**
   * Generate a fake [Common.Event] using the provided [appInspectionEvent].
   */
  fun addAppInspectionEvent(appInspectionEvent: AppInspection.AppInspectionEvent) {
    transportService.addEventToStream(
      stream.streamId,
      Common.Event.newBuilder()
        .setPid(process.pid)
        .setKind(Common.Event.Kind.APP_INSPECTION_EVENT)
        .setTimestamp(timer.currentTimeNs)
        .setIsEnded(true)
        .setAppInspectionEvent(appInspectionEvent)
        .build()
    )
    timer.currentTimeNs += 1
  }

  /**
   * Keeps track of all events so they can be gotten later and compared.
   */
  open class TestInspectorEventListener : AppInspectorClient.EventListener {
    private val events = mutableListOf<ByteArray>()
    private val crashes = mutableListOf<String>()
    var isDisposed = false

    val rawEvents
      get() = events.toList()

    val crashEvents
      get() = crashes.toList()

    override fun onRawEvent(eventData: ByteArray) {
      events.add(eventData)
    }

    override fun onCrashEvent(message: String) {
      crashes.add(message)
    }

    override fun onDispose() {
      isDisposed = true
    }
  }
}
