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
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.app.inspection.AppInspection
import com.android.tools.app.inspection.AppInspection.AppInspectionCommand
import com.android.tools.app.inspection.AppInspection.CreateInspectorCommand
import com.android.tools.idea.appinspection.api.AppInspectionJarCopier
import com.android.tools.idea.appinspection.inspector.api.AppInspectionAppProguardedException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLaunchException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLibraryMissingException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionServiceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionIncompatibleException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionMissingException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibility
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.manager.StreamEventQuery
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Commands.Command.CommandType.ATTACH_AGENT
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.Event.Kind.AGENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_RESPONSE
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
import com.android.tools.profiler.proto.Transport
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * Attaches the transport agent if it is not already.
 *
 * An agent is attached if the latest PROCESS event has is_ended set to false, and the
 * latest AGENT event has status ATTACHED and has a timestamp greater than the PROCESS event.
 *
 * After the agent is attached, inspectors can start sending commands and receiving responses.
 */
internal suspend fun attachAppInspectionTarget(
  transport: AppInspectionTransport,
  jarCopier: AppInspectionJarCopier,
  parentScope: CoroutineScope
): AppInspectionTarget {
  var lastAgentAttachedEventTimestampNs = Long.MIN_VALUE
  val processEvents = transport.client.transportStub.getEventGroups(
    Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(transport.process.streamId)
      .setPid(transport.process.pid)
      .setKind(PROCESS)
      .build()
  )
  val lastProcessEvent = processEvents.groupsList.flatMap { it.eventsList }.lastOrNull()
  if (lastProcessEvent?.isEnded == false) {
    val agentEvents = transport.client.transportStub.getEventGroups(
      Transport.GetEventGroupsRequest.newBuilder()
        .setStreamId(transport.process.streamId)
        .setPid(transport.process.pid)
        .setKind(AGENT)
        .setFromTimestamp(lastProcessEvent.timestamp + 1)
        .build()
    )
    agentEvents.groupsList.flatMap { it.eventsList }.lastOrNull()?.let { event ->
      lastAgentAttachedEventTimestampNs = event.timestamp + 1
      if (event.timestamp >= lastProcessEvent.timestamp && event.agentData.status == ATTACHED) {
        // Agent is already attached and connected, so there is no need to attach.
        return DefaultAppInspectionTarget(transport, jarCopier, parentScope)
      }
    }
  }

  // Attach transport agent command.
  val attachCommand = Command.newBuilder()
    .setStreamId(transport.process.streamId)
    .setPid(transport.process.pid)
    .setType(ATTACH_AGENT)
    .setAttachAgent(
      Commands.AttachAgent.newBuilder()
        .setAgentLibFileName("libjvmtiagent_${transport.process.abiCpuArch}.so")
        .setAgentConfigPath(TransportFileManager.getAgentConfigFile())
        .setPackageName(transport.process.packageName)
    )
    .build()

  val streamEventQuery = transport.createStreamEventQuery(
    eventKind = AGENT,
    startTimeNs = { lastAgentAttachedEventTimestampNs },
    filter = { it.agentData.status == ATTACHED && it.timestamp >= lastAgentAttachedEventTimestampNs }
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

  @WorkerThread
  override suspend fun launchInspector(
    params: LaunchParameters
  ): AppInspectorMessenger {
    val fileDevicePath = jarCopier.copyFileToDevice(params.inspectorJar).first()
    val launchMetadata = AppInspection.LaunchMetadata.newBuilder()
      .setLaunchedByName(params.projectName)
      .setForce(params.force)
    params.library?.let { launchMetadata.setMinLibrary(it.toLibraryCompatibilityProto()) }
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
    if (event.appInspectionResponse.createInspectorResponse.status == AppInspection.CreateInspectorResponse.Status.SUCCESS) {
      return AppInspectorConnection(transport, params.inspectorId, event.timestamp, scope.createChildScope(false))
    }
    else {
      throw event.appInspectionResponse.getException(params.inspectorId)
    }
  }

  /**
   * Disposes all inspectors that were launched on this target.
   */
  @WorkerThread
  override suspend fun dispose() {
    scope.cancel()
  }

  @WorkerThread
  override suspend fun getLibraryVersions(libraryCoordinates: List<LibraryCompatibility>): List<LibraryCompatbilityInfo> {
    val libraryVersions = libraryCoordinates.map { it.toLibraryCompatibilityProto() }
    val getLibraryVersionsCommand = AppInspection.GetLibraryCompatibilityInfoCommand.newBuilder().addAllTargetLibraries(
      libraryVersions).build()
    val commandId = AppInspectionTransport.generateNextCommandId()
    val appInspectionCommand = AppInspectionCommand.newBuilder().setCommandId(commandId).setGetLibraryCompatibilityInfoCommand(
      getLibraryVersionsCommand).build()
    val streamQuery = StreamEventQuery(
      eventKind = APP_INSPECTION_RESPONSE,
      filter = { it.appInspectionResponse.commandId == commandId }
    )
    val response = transport.executeCommand(appInspectionCommand, streamQuery)
    // The API call should always return a list of the same size.
    assert(libraryCoordinates.size == response.appInspectionResponse.getLibraryCompatibilityResponse.responsesCount)
    return response.appInspectionResponse.getLibraryCompatibilityResponse.responsesList.mapIndexed { i, result ->
      result.toLibraryCompatibilityInfo(libraryCoordinates[i].coordinate)
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
    AppInspection.CreateInspectorResponse.Status.VERSION_MISSING -> AppInspectionVersionMissingException(message)
    AppInspection.CreateInspectorResponse.Status.LIBRARY_MISSING -> AppInspectionLibraryMissingException(message)
    AppInspection.CreateInspectorResponse.Status.APP_PROGUARDED -> AppInspectionAppProguardedException(message)
    else -> AppInspectionLaunchException(message)
  }
}

@VisibleForTesting
internal fun AppInspection.LibraryCompatibilityInfo.toLibraryCompatibilityInfo(
  artifactCoordinate: ArtifactCoordinate): LibraryCompatbilityInfo {
  val responseStatus = when (status) {
    AppInspection.LibraryCompatibilityInfo.Status.COMPATIBLE -> LibraryCompatbilityInfo.Status.COMPATIBLE
    AppInspection.LibraryCompatibilityInfo.Status.INCOMPATIBLE -> LibraryCompatbilityInfo.Status.INCOMPATIBLE
    AppInspection.LibraryCompatibilityInfo.Status.VERSION_MISSING -> LibraryCompatbilityInfo.Status.VERSION_MISSING
    AppInspection.LibraryCompatibilityInfo.Status.LIBRARY_MISSING -> LibraryCompatbilityInfo.Status.LIBRARY_MISSING
    AppInspection.LibraryCompatibilityInfo.Status.APP_PROGUARDED -> LibraryCompatbilityInfo.Status.APP_PROGUARDED
    else -> LibraryCompatbilityInfo.Status.ERROR
  }
  return LibraryCompatbilityInfo(artifactCoordinate, responseStatus, version, errorMessage)
}