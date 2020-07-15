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
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.api.AppInspectorLauncher
import com.android.tools.idea.appinspection.api.TestInspectorClient
import com.android.tools.idea.appinspection.api.TestInspectorCommandHandler
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.api.process.ProcessNotifier
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.AppInspectionProcessDiscovery
import com.android.tools.idea.appinspection.internal.AppInspectionTarget
import com.android.tools.idea.appinspection.internal.AppInspectionTargetManager
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.appinspection.internal.DefaultAppInspectionApiServices
import com.android.tools.idea.appinspection.internal.DefaultAppInspectorLauncher
import com.android.tools.idea.appinspection.internal.launchInspectorForTest
import com.android.tools.idea.testing.NamedExternalResource
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.runner.Description
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
  lateinit var executorService: ExecutorService
  lateinit var scope: CoroutineScope
  lateinit var streamManager: TransportStreamManager
  lateinit var streamChannel: TransportStreamChannel
  lateinit var transport: AppInspectionTransport
  lateinit var jarCopier: AppInspectionTestUtils.TestTransportJarCopier
  internal lateinit var targetManager: AppInspectionTargetManager
  lateinit var launcher: AppInspectorLauncher
  lateinit var processNotifier: ProcessNotifier
  lateinit var apiServices: AppInspectionApiServices

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
    streamManager = TransportStreamManager.createManager(client.transportStub, TimeUnit.MILLISECONDS.toNanos(100))
    streamChannel = TransportStreamChannel(stream, streamManager.poller)
    executorService = Executors.newSingleThreadExecutor()
    scope = CoroutineScope(executorService.asCoroutineDispatcher())
    transport = AppInspectionTransport(client, stream, process, executorService, streamChannel)
    jarCopier = AppInspectionTestUtils.TestTransportJarCopier
    targetManager = AppInspectionTargetManager(executorService, client, scope)
    processNotifier = AppInspectionProcessDiscovery(executorService, streamManager)
    launcher = DefaultAppInspectorLauncher(targetManager, processNotifier as AppInspectionProcessDiscovery) { jarCopier }
    apiServices = DefaultAppInspectionApiServices(targetManager, processNotifier, launcher, scope)
  }

  override fun after(description: Description) = runBlocking {
    TransportStreamManager.unregisterManager(streamManager)
    scope.coroutineContext[Job]!!.cancelAndJoin()
    executorService.shutdownNow()
    client.shutdown()
    timer.currentTimeNs += 1
  }

  /**
   * Launches a new [AppInspectionTarget] and sets the optional [commandHandler].
   */
  internal fun launchTarget(
    process: ProcessDescriptor,
    project: String = TEST_PROJECT,
    commandHandler: CommandHandler = TestInspectorCommandHandler(timer)
  ): ListenableFuture<AppInspectionTarget> {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, defaultAttachHandler)
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, commandHandler)
    val targetFuture = targetManager.attachToProcess(process, jarCopier, streamChannel, project)
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
    eventListener: AppInspectorClient.RawEventListener = TestInspectorRawEventListener()
  ): AppInspectorClient {
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, commandHandler)
    return launchInspectorForTest(inspectorId, transport, timer.currentTimeNs) {
      TestInspectorClient(it, eventListener)
    }.also { timer.currentTimeNs += 1 }
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

  fun addProcessListener(listener: ProcessListener) {
    processNotifier.addProcessListener(executorService, listener)
  }

  /**
   * Keeps track of all events so they can be gotten later and compared.
   */
  open class TestInspectorRawEventListener : AppInspectorClient.RawEventListener {
    private val events = mutableListOf<ByteArray>()

    val rawEvents
      get() = events.toList()

    override fun onRawEvent(eventData: ByteArray) {
      events.add(eventData)
    }
  }
}
