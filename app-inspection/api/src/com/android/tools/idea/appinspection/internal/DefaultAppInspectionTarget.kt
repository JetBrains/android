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

import com.android.annotations.concurrency.AnyThread
import com.android.tools.app.inspection.AppInspection
import com.android.tools.app.inspection.AppInspection.AppInspectionCommand
import com.android.tools.app.inspection.AppInspection.AppInspectionResponse.Status.SUCCESS
import com.android.tools.app.inspection.AppInspection.CreateInspectorCommand
import com.android.tools.idea.appinspection.api.AppInspectionJarCopier
import com.android.tools.idea.appinspection.api.AppInspectorLauncher
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLaunchException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Commands.Command.CommandType.ATTACH_AGENT
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.Event.Kind.AGENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_RESPONSE
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends ATTACH command to the transport daemon, that makes sure an agent is running and is ready
 * to receive app-inspection-specific commands.
 */
internal suspend fun attachAppInspectionTarget(
  transport: AppInspectionTransport,
  jarCopier: AppInspectionJarCopier,
  parentScope: CoroutineScope
): AppInspectionTarget {
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

  val eventListener = transport.createStreamEventListener(
    eventKind = AGENT,
    filter = { it.agentData.status == ATTACHED }
  ) {}
  transport.executeCommand(attachCommand.toExecuteRequest(), eventListener)
  return DefaultAppInspectionTarget(transport, jarCopier, parentScope)
}

@AnyThread
internal class DefaultAppInspectionTarget(
  val transport: AppInspectionTransport,
  private val jarCopier: AppInspectionJarCopier,
  parentScope: CoroutineScope
) : AppInspectionTarget {
  private val scope = parentScope.createChildScope(true)

  @VisibleForTesting
  internal val clients = ConcurrentHashMap<String, Deferred<AppInspectorClient>>()

  override suspend fun <C: AppInspectorClient> launchInspector(
    params: AppInspectorLauncher.LaunchParameters,
    creator: (AppInspectorClient.CommandMessenger) -> C
  ): C {
    val clientDeferred = clients.computeIfAbsent(params.inspectorId) {
      scope.async {
        val fileDevicePath = jarCopier.copyFileToDevice(params.inspectorJar).first()
        val createInspectorCommand = CreateInspectorCommand.newBuilder()
          .setDexPath(fileDevicePath)
          .setLaunchMetadata(AppInspection.LaunchMetadata.newBuilder().setLaunchedByName(params.projectName).setForce(params.force).build())
          .build()
        val commandId = AppInspectionTransport.generateNextCommandId()
        val appInspectionCommand = AppInspectionCommand.newBuilder()
          .setInspectorId(params.inspectorId)
          .setCreateInspectorCommand(createInspectorCommand)
          .setCommandId(commandId)
          .build()
        val eventListener = transport.createStreamEventListener(
          eventKind = APP_INSPECTION_RESPONSE,
          filter = { it.appInspectionResponse.commandId == commandId }
        ) {}
        val event = transport.executeCommand(appInspectionCommand, eventListener)
        if (event.appInspectionResponse.status == SUCCESS) {
          val connection = AppInspectorConnection(transport, params.inspectorId, event.timestamp, scope)
          setupEventListener(creator, connection).also { inspectorClient ->
            inspectorClient.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
              override fun onDispose() {
                clients.remove(params.inspectorId)
              }
            }, MoreExecutors.directExecutor())
          }
        }
        else {
          throw AppInspectionLaunchException(
            "Could not launch inspector ${params.inspectorId}: ${event.appInspectionResponse.errorMessage}")
        }
      }
    }
    try {
      // We always know the exact type of the client because it gets created via the passed-in creator
      @Suppress("UNCHECKED_CAST")
      return clientDeferred.await() as C
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      clients.remove(params.inspectorId)
      throw t
    }
  }

  /**
   * Disposes all clients that were launched on this target.
   */
  override suspend fun dispose() {
    scope.cancel()
    clients.clear()
  }
}

private fun <T : AppInspectorClient> setupEventListener(creator: (AppInspectorConnection) -> T,
                                                        connection: AppInspectorConnection): T {
  val client = creator(connection)
  connection.setupConnection(client.rawEventListener, client.serviceEventNotifier)
  return client
}

@VisibleForTesting
fun <T : AppInspectorClient> launchInspectorForTest(
  inspectorId: String,
  transport: AppInspectionTransport,
  connectionStartTimeNs: Long,
  scope: CoroutineScope,
  creator: (AppInspectorClient.CommandMessenger) -> T
): T {
  val connection = AppInspectorConnection(transport, inspectorId, connectionStartTimeNs, scope)
  return setupEventListener(creator, connection)
}