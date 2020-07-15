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
import com.android.tools.idea.concurrency.getDoneOrNull
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Commands.Command.CommandType.ATTACH_AGENT
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.Event.Kind.AGENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_RESPONSE
import com.android.tools.profiler.proto.Transport.ExecuteRequest
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AsyncCallable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends ATTACH command to the transport daemon, that makes sure an agent is running and is ready
 * to receive app-inspection-specific commands.
 */
internal fun attachAppInspectionTarget(
  transport: AppInspectionTransport,
  jarCopier: AppInspectionJarCopier,
  scope: CoroutineScope
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
        transport.createStreamEventListener(
          eventKind = AGENT,
          filter = { it.agentData.status == ATTACHED },
          isTransient = true
        ) {
          connectionFuture.set(DefaultAppInspectionTarget(transport, jarCopier, scope))
        })
      transport.client.transportStub.execute(ExecuteRequest.newBuilder().setCommand(attachCommand).build())
      connectionFuture
    }, transport.executorService
  )
}


@AnyThread
internal class DefaultAppInspectionTarget(
  val transport: AppInspectionTransport,
  private val jarCopier: AppInspectionJarCopier,
  scope: CoroutineScope
) : AppInspectionTarget {
  private val isDisposed = AtomicBoolean(false)
  private val targetScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))

  @VisibleForTesting
  internal val clients = ConcurrentHashMap<AppInspectorLauncher.LaunchParameters, ListenableFuture<AppInspectorClient>>()

  override fun launchInspector(
    params: AppInspectorLauncher.LaunchParameters,
    creator: (AppInspectorClient.CommandMessenger) -> AppInspectorClient
  ): ListenableFuture<AppInspectorClient> {
    if (isDisposed.get()) {
      return Futures.immediateFailedFuture(ProcessNoLongerExistsException("Target process does not exist because it has ended."))
    }
    val clientFuture = clients.computeIfAbsent(params) {
      MoreExecutors.listeningDecorator(transport.executorService)
        .submit<String> { jarCopier.copyFileToDevice(params.inspectorJar).first() }
        .transformAsync(transport.executorService) { fileDevicePath ->
          val connectionFuture = SettableFuture.create<AppInspectorConnection>()
          val createInspectorCommand = CreateInspectorCommand.newBuilder()
            .setDexPath(fileDevicePath)
            .setLaunchMetadata(
              AppInspection.LaunchMetadata.newBuilder().setLaunchedByName(params.projectName).setForce(params.force).build())
            .build()
          val appInspectionCommand = AppInspectionCommand.newBuilder()
            .setInspectorId(params.inspectorId)
            .setCreateInspectorCommand(createInspectorCommand)
            .build()
          val commandId = transport.executeCommand(appInspectionCommand)
          transport.registerEventListener(
            transport.createStreamEventListener(
              eventKind = APP_INSPECTION_RESPONSE,
              filter = { it.appInspectionResponse.commandId == commandId },
              isTransient = true
            ) { event ->
              if (event.appInspectionResponse.status == SUCCESS) {
                connectionFuture.set(AppInspectorConnection(transport, params.inspectorId, event.timestamp))
              }
              else {
                connectionFuture.setException(
                  AppInspectionLaunchException(
                    "Could not launch inspector ${params.inspectorId}: ${event.appInspectionResponse.errorMessage}")
                )
              }
            }
          )
          connectionFuture
        }.transform(transport.executorService) { inspectorConnection ->
          setupEventListener(creator, inspectorConnection).also { inspectorClient ->
            inspectorClient.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
              override fun onDispose() {
                clients.remove(params)
              }
            }, MoreExecutors.directExecutor())
          }
        }
    }
    if (isDisposed.get()) {
      clients.remove(params)?.cancel(false)
      return Futures.immediateFailedFuture(ProcessNoLongerExistsException("Target process does not exist because it has ended."))
    }
    else {
      return clientFuture
    }
  }

  /**
   * Disposes all clients that were launched on this target.
   */
  override fun dispose() {
    if (isDisposed.compareAndSet(false, true)) {
      targetScope.launch {
        clients.values.forEach {
          it.getDoneOrNull()?.messenger?.disposeInspector() ?: it.cancel(false)
        }
        clients.clear()
      }
    }
  }
}

private fun <T : AppInspectorClient> setupEventListener(creator: (AppInspectorConnection) -> T, connection: AppInspectorConnection): T {
  val client = creator(connection)
  connection.setEventListeners(client.rawEventListener, client.serviceEventNotifier)
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