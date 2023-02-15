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
import com.android.tools.idea.appinspection.api.process.ProcessDiscovery
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.AppInspectionProcessDiscovery
import com.android.tools.idea.appinspection.internal.AppInspectionTarget
import com.android.tools.idea.appinspection.internal.AppInspectionTargetManager
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.appinspection.internal.DefaultAppInspectionApiServices
import com.android.tools.idea.appinspection.internal.launchInspectorForTest
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.testing.NamedExternalResource
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.runner.Description

val DEFAULT_TEST_INSPECTION_STREAM =
  Common.Stream.newBuilder()
    .apply {
      type = Common.Stream.Type.DEVICE
      streamId = FakeTransportService.FAKE_DEVICE_ID
      device = FakeTransportService.FAKE_DEVICE
    }
    .build()!!
val DEFAULT_TEST_INSPECTION_PROCESS =
  TransportProcessDescriptor(DEFAULT_TEST_INSPECTION_STREAM, FakeTransportService.FAKE_PROCESS)

/**
 * Rule providing all of the underlying components of App Inspection including [executorService],
 * [streamChannel], [transport] and [client].
 *
 * It also provides a number of useful utility functions for tests. Normally used in conjunction
 * with [FakeGrpcServer] rule.
 */
class AppInspectionServiceRule(
  private val timer: FakeTimer,
  private val transportService: FakeTransportService,
  private val grpcServer: FakeGrpcServer,
  private val stream: Common.Stream = DEFAULT_TEST_INSPECTION_STREAM,
  private val process: ProcessDescriptor = DEFAULT_TEST_INSPECTION_PROCESS
) : NamedExternalResource() {
  lateinit var client: TransportClient
  lateinit var executorService: ExecutorService
  lateinit var scope: CoroutineScope
  lateinit var streamManager: TransportStreamManager
  lateinit var streamChannel: TransportStreamChannel
  lateinit var transport: AppInspectionTransport
  lateinit var jarCopier: AppInspectionTestUtils.TestTransportJarCopier
  internal lateinit var targetManager: AppInspectionTargetManager
  lateinit var processDiscovery: ProcessDiscovery
  lateinit var apiServices: AppInspectionApiServices

  private val defaultAttachHandler =
    object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
        events.add(
          Common.Event.newBuilder()
            .setCommandId(command.commandId)
            .setPid(command.pid)
            .setKind(Common.Event.Kind.AGENT)
            .setAgentData(
              Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build()
            )
            .setTimestamp(timer.currentTimeNs)
            .build()
        )
      }
    }

  override fun before(description: Description) {
    client = TransportClient(grpcServer.name)
    executorService = Executors.newSingleThreadExecutor()
    streamManager =
      TransportStreamManager.createManager(
        client.transportStub,
        executorService.asCoroutineDispatcher()
      )
    streamChannel =
      TransportStreamChannel(stream, client.transportStub, executorService.asCoroutineDispatcher())
    scope = CoroutineScope(executorService.asCoroutineDispatcher() + SupervisorJob())
    transport = AppInspectionTransport(client, process, streamChannel)
    jarCopier = AppInspectionTestUtils.TestTransportJarCopier
    targetManager = AppInspectionTargetManager(client, scope)
    processDiscovery = AppInspectionProcessDiscovery(streamManager, scope)
    apiServices =
      DefaultAppInspectionApiServices(
        targetManager,
        { jarCopier },
        processDiscovery as AppInspectionProcessDiscovery
      )
    transportService.setCommandHandler(
      Commands.Command.CommandType.ATTACH_AGENT,
      defaultAttachHandler
    )
  }

  override fun after(description: Description) = runBlocking {
    TransportStreamManager.unregisterManager(streamManager)
    scope.coroutineContext[Job]!!.cancelAndJoin()
    executorService.shutdownNow()
    timer.currentTimeNs += 1
    client.shutdown()
  }

  /** Launches a new [AppInspectionTarget] and sets the optional [commandHandler]. */
  internal suspend fun launchTarget(
    process: ProcessDescriptor,
    project: String = TEST_PROJECT,
    commandHandler: CommandHandler = TestAppInspectorCommandHandler(timer)
  ): AppInspectionTarget {
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, commandHandler)
    return targetManager.attachToProcess(process, jarCopier, streamChannel, project).also {
      timer.currentTimeNs += 1
    }
  }

  /**
   * Launches a new inspector connection.
   *
   * [commandHandler] can be provided to customize behavior of how commands and events are received.
   */
  fun launchInspectorConnection(
    inspectorId: String = INSPECTOR_ID,
    commandHandler: CommandHandler = TestAppInspectorCommandHandler(timer),
    parentScope: CoroutineScope = scope
  ): AppInspectorMessenger {
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, commandHandler)
    return launchInspectorForTest(
        inspectorId,
        transport,
        timer.currentTimeNs,
        parentScope.createChildScope(false)
      )
      .also { timer.currentTimeNs += 1 }
  }

  fun addEvent(event: Common.Event) {
    val modifiedEvent =
      event.toBuilder().setPid(process.pid).setTimestamp(timer.currentTimeNs).build()
    transportService.addEventToStream(stream.streamId, modifiedEvent)
    timer.currentTimeNs += 1
  }

  /**
   * Generate fake [Common.Event]s using the provided [payloadEvents].
   *
   * Once this has been called, the complete payload (all passed in payload events concatenated)
   * should be cached on the other side of the connection, and it can be referenced via the
   * [payloadId] passed in here.
   *
   * @param payloadId You may pass in any unique ID you want here. Re-using an old ID will
   * potentially overwrite a previously sent payload, so be careful not to do that! In production,
   * this value will be generated automatically by the app inspection service, but for tests, you
   * can just specify a value, as long as you're consistent about using it later when fetching the
   * payload.
   *
   * See also: [AppInspectionTestUtils.createPayloadChunks]
   */
  fun addAppInspectionPayload(
    payloadId: Long,
    payloadEvents: List<AppInspection.AppInspectionPayload>
  ) {
    payloadEvents.forEachIndexed { i, payloadEvent ->
      addAppInspectionPayload(payloadId, payloadEvent, i == payloadEvents.lastIndex)
    }
  }

  private fun addAppInspectionPayload(
    payloadId: Long,
    payloadEvent: AppInspection.AppInspectionPayload,
    isEnded: Boolean
  ) {
    addTransportEvent { transportEvent ->
      transportEvent.kind = Common.Event.Kind.APP_INSPECTION_PAYLOAD
      transportEvent.groupId = payloadId
      transportEvent.isEnded = isEnded
      transportEvent.appInspectionPayload = payloadEvent
    }
  }

  /** Generate a fake [Common.Event] using the provided [appInspectionEvent]. */
  fun addAppInspectionEvent(appInspectionEvent: AppInspection.AppInspectionEvent) {
    addTransportEvent { transportEvent ->
      transportEvent.kind = Common.Event.Kind.APP_INSPECTION_EVENT
      transportEvent.appInspectionEvent = appInspectionEvent
    }
  }

  fun addProcessListener(listener: ProcessListener) {
    processDiscovery.addProcessListener(executorService, listener)
  }

  private fun addTransportEvent(initEvent: (Common.Event.Builder) -> Unit) {
    val transportEvent =
      Common.Event.newBuilder()
        .apply {
          pid = this@AppInspectionServiceRule.process.pid
          timestamp = timer.currentTimeNs
          isEnded = true

          initEvent(this)
        }
        .build()

    transportService.addEventToStream(stream.streamId, transportEvent)
    timer.currentTimeNs += 1
  }
}
