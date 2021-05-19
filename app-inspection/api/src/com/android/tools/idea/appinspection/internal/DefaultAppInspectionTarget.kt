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
import com.android.tools.app.inspection.AppInspection.CreateInspectorCommand
import com.android.tools.idea.appinspection.api.AppInspectionJarCopier
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLaunchException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLibraryMissingException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionServiceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionIncompatibleException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.awaitForDisposal
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryVersionResponse
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.manager.StreamEventQuery
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Commands.Command.CommandType.ATTACH_AGENT
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.Event.Kind.AGENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_RESPONSE
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

  val streamEventQuery = transport.createStreamEventQuery(
    eventKind = AGENT,
    filter = { it.agentData.status == ATTACHED }
  )
  transport.executeCommand(attachCommand.toExecuteRequest(), streamEventQuery)
  return DefaultAppInspectionTarget(transport, jarCopier, parentScope)
}

@AnyThread
internal class DefaultAppInspectionTarget(
  val transport: AppInspectionTransport,
  private val jarCopier: AppInspectionJarCopier,
  parentScope: CoroutineScope
) : AppInspectionTarget() {
  private val scope = parentScope.createChildScope(true)

  private val messengers = ConcurrentHashMap<String, Deferred<AppInspectorMessenger>>()

  /**
   * Used exclusively in tests. Allows tests to wait until after inspector client is removed from internal cache.
   */
  @VisibleForTesting
  internal val inspectorDisposableJobs = ConcurrentHashMap<String, Job>()

  override suspend fun launchInspector(
    params: LaunchParameters
  ): AppInspectorMessenger {
    val messengerDeferred = messengers.computeIfAbsent(params.inspectorId) {
      scope.async {
        val fileDevicePath = jarCopier.copyFileToDevice(params.inspectorJar).first()
        val launchMetadata = AppInspection.LaunchMetadata.newBuilder()
          .setLaunchedByName(params.projectName)
          .setForce(params.force)
        if (params.libraryCoordinate != null) {
          launchMetadata.setVersionParams(
            AppInspection.VersionParams.newBuilder()
              .setVersionFileName(params.libraryCoordinate!!.toVersionFileName())
              .setMinVersion(params.libraryCoordinate!!.version)
              .build()
          ).build()
        }
        val createInspectorCommand = CreateInspectorCommand.newBuilder()
          .setDexPath(fileDevicePath)
          .setLaunchMetadata(launchMetadata)
          .build()
        val commandId = AppInspectionTransport.generateNextCommandId()
        val appInspectionCommand = AppInspectionCommand.newBuilder()
          .setInspectorId(params.inspectorId)
          .setCreateInspectorCommand(createInspectorCommand)
          .setCommandId(commandId)
          .build()
        val eventQuery = transport.createStreamEventQuery(
          eventKind = APP_INSPECTION_RESPONSE,
          filter = { it.appInspectionResponse.commandId == commandId }
        )
        val event = transport.executeCommand(appInspectionCommand, eventQuery)
        when (event.appInspectionResponse.createInspectorResponse.status) {
          AppInspection.CreateInspectorResponse.Status.SUCCESS -> {
            val connection = AppInspectorConnection(transport, params.inspectorId, event.timestamp, scope.createChildScope(false))
            inspectorDisposableJobs[params.inspectorId] = scope.launch {
              connection.awaitForDisposal()
              messengers.remove(params.inspectorId)
              inspectorDisposableJobs.remove(params.inspectorId)
            }
            connection
          }
          AppInspection.CreateInspectorResponse.Status.GENERIC_SERVICE_ERROR -> throw event.appInspectionResponse.getException(
            params.inspectorId)
          AppInspection.CreateInspectorResponse.Status.VERSION_INCOMPATIBLE -> throw event.appInspectionResponse.getException(
            params.inspectorId)
          AppInspection.CreateInspectorResponse.Status.LIBRARY_MISSING -> throw event.appInspectionResponse.getException(params.inspectorId)
          else -> throw event.appInspectionResponse.getException(params.inspectorId)
        }
      }
    }
    try {
      return messengerDeferred.await()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      messengers.remove(params.inspectorId)
      throw t
    }
  }

  /**
   * Disposes all inspectors that were launched on this target.
   */
  override suspend fun dispose() {
    scope.cancel()
    messengers.clear()
  }

  override suspend fun getLibraryVersions(libraryCoordinates: List<ArtifactCoordinate>): List<LibraryVersionResponse> {
    val libraryVersions = libraryCoordinates.map {
      AppInspection.VersionParams.newBuilder().setMinVersion(it.version).setVersionFileName(it.toVersionFileName()).build()
    }
    val getLibraryVersionsCommand = AppInspection.GetLibraryVersionsCommand.newBuilder().addAllTargetVersions(libraryVersions).build()
    val commandId = AppInspectionTransport.generateNextCommandId()
    val appInspectionCommand = AppInspectionCommand.newBuilder().setCommandId(commandId).setGetLibraryVersionsCommand(
      getLibraryVersionsCommand).build()
    val streamQuery = StreamEventQuery(
      eventKind = APP_INSPECTION_RESPONSE,
      filter = { it.appInspectionResponse.commandId == commandId }
    )
    val response = transport.executeCommand(appInspectionCommand, streamQuery)
    // The API call should always return a list of the same size.
    assert(libraryCoordinates.size == response.appInspectionResponse.libraryVersionsResponse.responsesCount)
    return response.appInspectionResponse.libraryVersionsResponse.responsesList.mapIndexed { i, result ->
      result.toLibraryVersionResponse(libraryCoordinates[i])
    }
  }
}

@VisibleForTesting
fun launchInspectorForTest(
  inspectorId: String,
  transport: AppInspectionTransport,
  connectionStartTimeNs: Long,
  scope: CoroutineScope
): AppInspectorMessenger {
  return AppInspectorConnection(transport, inspectorId, connectionStartTimeNs, scope)
}

/**
 * Maps the ServiceResponse.ErrorType to an [AppInspectionServiceException] and returns the correct exception.
 */
private fun AppInspection.AppInspectionResponse.getException(inspectorId: String): AppInspectionServiceException {
  val message = "Could not launch inspector ${inspectorId}: ${this.errorMessage}"
  return when (this.createInspectorResponse.status) {
    AppInspection.CreateInspectorResponse.Status.VERSION_INCOMPATIBLE -> AppInspectionVersionIncompatibleException(message)
    AppInspection.CreateInspectorResponse.Status.LIBRARY_MISSING -> AppInspectionLibraryMissingException(message)
    else -> AppInspectionLaunchException(message)
  }
}

@VisibleForTesting
internal fun AppInspection.LibraryVersionResponse.toLibraryVersionResponse(artifactCoordinate: ArtifactCoordinate): LibraryVersionResponse {
  val responseStatus = when (status) {
    AppInspection.LibraryVersionResponse.Status.COMPATIBLE -> LibraryVersionResponse.Status.COMPATIBLE
    AppInspection.LibraryVersionResponse.Status.INCOMPATIBLE -> LibraryVersionResponse.Status.INCOMPATIBLE
    AppInspection.LibraryVersionResponse.Status.LIBRARY_MISSING -> LibraryVersionResponse.Status.LIBRARY_MISSING
    else -> LibraryVersionResponse.Status.ERROR
  }
  return LibraryVersionResponse(artifactCoordinate, responseStatus, version, errorMessage)
}

/**
 * Constructs the name of the version file located in an app's APK's META-INF folder.
 */
private fun ArtifactCoordinate.toVersionFileName() = "${groupId}_${artifactId}.version"