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
package com.android.tools.idea.appinspection.transport

import com.android.tools.app.inspection.AppInspection.AppInspectionCommand
import com.android.tools.app.inspection.AppInspection.CreateInspectorCommand
import com.android.tools.app.inspection.AppInspection.ServiceResponse.Status.SUCCESS
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Commands.Command.CommandType.ATTACH_AGENT
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.Event.Kind.AGENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION
import com.android.tools.profiler.proto.Common.Process
import com.android.tools.profiler.proto.Common.Stream
import com.android.tools.profiler.proto.Transport.ExecuteRequest
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Function
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
  client: TransportClient,
  stream: Stream,
  process: Process
): ListenableFuture<AppInspectionPipelineConnection> {
  val transport = AppInspectionTransport(client, stream, process)
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
    eventKind = AGENT,
    executor = MoreExecutors.directExecutor(),
    filter = { it.agentData.status == ATTACHED }
  ) {
    connectionFuture.set(DefaultAppInspectionPipelineConnection(transport))
    true
  }
  client.transportStub.execute(ExecuteRequest.newBuilder().setCommand(attachCommand).build())
  return connectionFuture
}

private class DefaultAppInspectionPipelineConnection(val processTransport: AppInspectionTransport) : AppInspectionPipelineConnection {

  override fun <T : AppInspectorClient> launchInspector(
    inspectorId: String,
    inspectorJar: Path,
    creator: (AppInspectorClient.CommandMessenger) -> T
  ): ListenableFuture<T> {
    // TODO(b/142526985): push dex file to the device with adb.
    val connectionFuture = SettableFuture.create<AppInspectorConnection>()
    val createInspectorCommand = CreateInspectorCommand.newBuilder()
      .setInspectorId(inspectorId)
      .setDexPath(inspectorJar.toString())
      .build()
    val appInspectionCommand = AppInspectionCommand.newBuilder().setCreateInspectorCommand(createInspectorCommand).build()
    processTransport.registerEventListener(
      eventKind = APP_INSPECTION,
      executor = MoreExecutors.directExecutor()
    ) {
      if (it.appInspectionEvent.response.status == SUCCESS) {
        connectionFuture.set(AppInspectorConnection(processTransport, inspectorId))
      }
      else {
        connectionFuture.setException(RuntimeException("Could not launch inspector $inspectorId"))
      }
      true
    }

    processTransport.executeCommand(appInspectionCommand)
    return Futures.transform(connectionFuture, Function { setupEventListener(creator, it!!) }, MoreExecutors.directExecutor())
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
  client: TransportClient,
  stream: Stream,
  process: Process,
  creator: (AppInspectorClient.CommandMessenger) -> T
): T {
  val transport = AppInspectionTransport(client, stream, process)
  val connection = AppInspectorConnection(transport, inspectorId)
  return setupEventListener(creator, connection)
}