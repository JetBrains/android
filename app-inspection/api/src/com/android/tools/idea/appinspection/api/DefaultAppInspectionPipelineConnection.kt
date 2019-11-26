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
package com.android.tools.idea.appinspection.api

import com.android.tools.app.inspection.AppInspection.AppInspectionCommand
import com.android.tools.app.inspection.AppInspection.AppInspectionResponse.Status.SUCCESS
import com.android.tools.app.inspection.AppInspection.CreateInspectorCommand
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.transport.DeployableFile
import com.android.tools.idea.transport.TransportFileCopier
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Commands.Command.CommandType.ATTACH_AGENT
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.Event.Kind.AGENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_RESPONSE
import com.android.tools.profiler.proto.Common.Process
import com.android.tools.profiler.proto.Common.Stream
import com.android.tools.profiler.proto.Transport.ExecuteRequest
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import java.nio.file.Path

/**
 * Sends ATTACH command to transport daemon, that makes sure perfa is running and is ready to receive
 * app-inspection-specific commands.
 */
internal fun attachAppInspectionPipelineConnection(
  stream: Stream,
  process: Process,
  transport: AppInspectionTransport,
  fileCopier: TransportFileCopier
): ListenableFuture<AppInspectionPipelineConnection> {
  val connectionFuture = SettableFuture.create<AppInspectionPipelineConnection>()

  // The device daemon takes care of the case if and when the agent is previously attached already.
  val attachCommand = Command.newBuilder()
    .setStreamId(stream.streamId)
    .setPid(process.pid)
    .setType(ATTACH_AGENT)
    .setAttachAgent(
      Commands.AttachAgent.newBuilder()
        .setAgentLibFileName("libjvmtiagent_${process.abiCpuArch}.so")
        .setAgentConfigPath(TransportFileManager.getAgentConfigFile()))
    .build()

  transport.registerEventListener(
    transport.createEventListener(eventKind = AGENT, filter = { it.agentData.status == ATTACHED }
    ) {
      connectionFuture.set(DefaultAppInspectionPipelineConnection(transport, fileCopier))
      true
    })
  transport.client.transportStub.execute(ExecuteRequest.newBuilder().setCommand(attachCommand).build())
  return connectionFuture
}

private class DefaultAppInspectionPipelineConnection(val processTransport: AppInspectionTransport,
                                                     private val fileCopier: TransportFileCopier) : AppInspectionPipelineConnection {
  override fun <T : AppInspectorClient> launchInspector(
    inspectorId: String,
    inspectorJar: DeployableFile,
    creator: (AppInspectorClient.CommandMessenger) -> T
  ): ListenableFuture<T> {
    val launchResultFuture = MoreExecutors.listeningDecorator(processTransport.executorService)
      .submit<Path> { fileCopier.copyFileToDevice(inspectorJar).first() }
    return Futures.transformAsync(
      launchResultFuture,
      AsyncFunction<Path, AppInspectorConnection> { fileDevicePath ->
        val connectionFuture = SettableFuture.create<AppInspectorConnection>()
        val createInspectorCommand = CreateInspectorCommand.newBuilder()
          .setDexPath(fileDevicePath.toString())
          .build()
        val appInspectionCommand = AppInspectionCommand.newBuilder()
          .setInspectorId(inspectorId)
          .setCreateInspectorCommand(createInspectorCommand)
          .build()
        val commandId = processTransport.executeCommand(appInspectionCommand)
        processTransport.registerEventListener(
          processTransport.createEventListener(
            eventKind = APP_INSPECTION_RESPONSE,
            filter = { it.appInspectionResponse.commandId == commandId }
          ) { event ->
            if (event.appInspectionResponse.status == SUCCESS) {
              connectionFuture.set(AppInspectorConnection(processTransport, inspectorId, event.timestamp))
            }
            else {
              connectionFuture.setException(RuntimeException("Could not launch inspector $inspectorId"))
            }
            true
          }
        )
        connectionFuture
      },
      processTransport.executorService
    )
      .transform(processTransport.executorService) { setupEventListener(creator, it) }
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