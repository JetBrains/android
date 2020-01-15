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
package com.android.tools.idea.appinspection.internal

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.app.inspection.AppInspection.AppInspectionCommand
import com.android.tools.app.inspection.AppInspection.AppInspectionResponse.Status.SUCCESS
import com.android.tools.app.inspection.AppInspection.CreateInspectorCommand
import com.android.tools.idea.appinspection.api.AppInspectionJarCopier
import com.android.tools.idea.appinspection.api.AppInspectionTarget
import com.android.tools.idea.appinspection.api.AppInspectorClient
import com.android.tools.idea.appinspection.api.AppInspectorJar
import com.android.tools.idea.appinspection.api.ProcessDescriptor
import com.android.tools.idea.appinspection.api.TargetTerminatedListener
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Commands.Command.CommandType.ATTACH_AGENT
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.Event.Kind.AGENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_RESPONSE
import com.android.tools.profiler.proto.Transport.ExecuteRequest
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AsyncCallable
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executor

/**
 * Sends ATTACH command to the transport daemon, that makes sure an agent is running and is ready
 * to receive app-inspection-specific commands.
 */
internal fun attachAppInspectionTarget(
  transport: AppInspectionTransport,
  jarCopier: AppInspectionJarCopier
): ListenableFuture<AppInspectionTarget> {
  return Futures.submitAsync(
    AsyncCallable {
      val connectionFuture = SettableFuture.create<AppInspectionTarget>()
      // The device daemon takes care of the case if and when the agent is previously attached already.
      val attachCommand = Command.newBuilder()
        .setStreamId(transport.stream.streamId)
        .setPid(transport.process.pid)
        .setType(ATTACH_AGENT)
        .setAttachAgent(
          Commands.AttachAgent.newBuilder()
            .setAgentLibFileName("libjvmtiagent_${transport.process.abiCpuArch}.so")
            .setAgentConfigPath(TransportFileManager.getAgentConfigFile())
        )
        .build()

      transport.registerEventListener(
        transport.createEventListener(eventKind = AGENT, filter = { it.agentData.status == ATTACHED }
        ) {
          connectionFuture.set(DefaultAppInspectionTarget(transport, jarCopier))
          true
        })
      transport.client.transportStub.execute(ExecuteRequest.newBuilder().setCommand(attachCommand).build())
      connectionFuture
    }, transport.executorService
  )
}

private class DefaultAppInspectionTarget(
  val transport: AppInspectionTransport,
  private val jarCopier: AppInspectionJarCopier
) : AppInspectionTarget {

  @GuardedBy("this")
  private var isTerminated = false

  @GuardedBy("this")
  private val listeners = mutableMapOf<TargetTerminatedListener, Executor>()

  init {
    transport.registerEventListener(
      TransportEventListener(
        eventKind = Common.Event.Kind.PROCESS,
        streamId = transport.stream::getStreamId,
        processId = transport.process::getPid,
        groupId = { transport.process.pid.toLong() },
        filter = { it.isEnded },
        executor = transport.executorService
      ) {
        synchronized(this) {
          if (!isTerminated) {
            isTerminated = true
            listeners.forEach { it.value.execute { it.key() } }
          }
        }
        true
      }
    )
  }

  override fun <T : AppInspectorClient> launchInspector(
    inspectorId: String,
    inspectorJar: AppInspectorJar,
    creator: (AppInspectorClient.CommandMessenger) -> T
  ): ListenableFuture<T> {
    val launchResultFuture = MoreExecutors.listeningDecorator(transport.executorService)
      .submit<String> { jarCopier.copyFileToDevice(inspectorJar).first() }
    return Futures.transformAsync(
      launchResultFuture,
      AsyncFunction<String, AppInspectorConnection> { fileDevicePath ->
        val connectionFuture = SettableFuture.create<AppInspectorConnection>()
        val createInspectorCommand = CreateInspectorCommand.newBuilder()
          .setDexPath(fileDevicePath)
          .build()
        val appInspectionCommand = AppInspectionCommand.newBuilder()
          .setInspectorId(inspectorId)
          .setCreateInspectorCommand(createInspectorCommand)
          .build()
        val commandId = transport.executeCommand(appInspectionCommand)
        transport.registerEventListener(
          transport.createEventListener(
            eventKind = APP_INSPECTION_RESPONSE,
            filter = { it.appInspectionResponse.commandId == commandId }
          ) { event ->
            if (event.appInspectionResponse.status == SUCCESS) {
              connectionFuture.set(AppInspectorConnection(transport, inspectorId, event.timestamp))
            } else {
              connectionFuture.setException(RuntimeException("Could not launch inspector $inspectorId"))
            }
            true
          }
        )
        connectionFuture
      },
      transport.executorService
    )
      .transform(transport.executorService) { setupEventListener(creator, it) }
  }

  override fun addTargetTerminatedListener(
    executor: Executor,
    listener: TargetTerminatedListener
  ): TargetTerminatedListener {
    return synchronized(this) {
      if (isTerminated) {
        executor.execute(listener)
      } else {
        listeners[listener] = executor
      }
      listener
    }
  }

  override val processDescriptor: ProcessDescriptor
    get() {
      return ProcessDescriptor(transport.stream, transport.process)
    }
}

private fun <T : AppInspectorClient> setupEventListener(creator: (AppInspectorConnection) -> T, connection: AppInspectorConnection): T {
  val client = creator(connection)
  connection.clientEventListener = client.eventListener
  return client
}

@VisibleForTesting
fun <T : AppInspectorClient> launchInspectorForTest(
  inspectorId: String,
  transport: AppInspectionTransport,
  connectionStartTimeNs: Long,
  creator: (AppInspectorClient.CommandMessenger) -> T
): T {
  val connection = AppInspectorConnection(transport, inspectorId, connectionStartTimeNs)
  return setupEventListener(creator, connection)
}