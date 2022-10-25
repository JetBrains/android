/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.api.checkVersion
import com.android.tools.idea.appinspection.ide.InspectorArtifactService
import com.android.tools.idea.appinspection.ide.getOrResolveInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibility
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLaunchMonitor
import com.android.tools.idea.layoutinspector.pipeline.errorCode
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.protobuf.CodedInputStream
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.util.text.nullize
import kotlinx.coroutines.cancel
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Command
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersCommand
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesCommand
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParameterDetailsCommand
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParameterDetailsResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParametersCommand
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Response
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.UpdateSettingsCommand
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.UpdateSettingsResponse
import java.util.EnumSet

const val COMPOSE_LAYOUT_INSPECTOR_ID = "layoutinspector.compose.inspection"

private val DEV_JAR = AppInspectorJar(
  "compose-ui-inspection.jar",
  developmentDirectory = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_DEVELOPMENT_FOLDER.get(),
  releaseDirectory = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_RELEASE_FOLDER.get().nullize()
)
@VisibleForTesting
val MINIMUM_COMPOSE_COORDINATE = ArtifactCoordinate(
  "androidx.compose.ui", "ui", "1.0.0-beta02", ArtifactCoordinate.Type.AAR
)
private const val EXPECTED_CLASS_IN_COMPOSE_LIBRARY = "androidx.compose.ui.Modifier"
private val COMPOSE_INSPECTION_COMPATIBILITY = LibraryCompatibility(
  MINIMUM_COMPOSE_COORDINATE, listOf(EXPECTED_CLASS_IN_COMPOSE_LIBRARY)
)

@VisibleForTesting
const val INCOMPATIBLE_LIBRARY_MESSAGE_KEY = "incompatible.library.message"

@VisibleForTesting
const val PROGUARDED_LIBRARY_MESSAGE_KEY = "proguarded.library.message"

@VisibleForTesting
const val VERSION_MISSING_MESSAGE_KEY = "version.missing.message"

@VisibleForTesting
const val INSPECTOR_NOT_FOUND_USE_SNAPSHOT_KEY = "inspector.not.found.use.snapshot"

@VisibleForTesting
const val COMPOSE_INSPECTION_NOT_AVAILABLE_KEY = "compose.inspection.not.available"

private const val PROGUARD_LEARN_MORE = "https://d.android.com/r/studio-ui/layout-inspector/code-shrinking"

/**
 * Result from [ComposeLayoutInspectorClient.getComposeables].
 */
class GetComposablesResult(
  /** The response received from the agent */
  val response: GetComposablesResponse,

  /** This is true, if a recomposition count reset command was sent after the GetComposables command was sent. */
  val pendingRecompositionCountReset: Boolean
)

/**
 * The client responsible for interacting with the compose layout inspector running on the target
 * device.
 *
 * @param messenger The messenger that lets us communicate with the view inspector.
 * @param capabilities Of the containing [InspectorClient]. Some capabilities may be added by this class.
 */
class ComposeLayoutInspectorClient(
  model: InspectorModel,
  private val treeSettings: TreeSettings,
  private val messenger: AppInspectorMessenger,
  private val capabilities: EnumSet<Capability>,
  private val launchMonitor: InspectorClientLaunchMonitor
) {

  companion object {
    /**
     * Helper function for launching the compose layout inspector and creating a client to interact
     * with it.
     */
    suspend fun launch(
      apiServices: AppInspectionApiServices,
      process: ProcessDescriptor,
      model: InspectorModel,
      treeSettings: TreeSettings,
      capabilities: EnumSet<Capability>,
      launchMonitor: InspectorClientLaunchMonitor,
      logErrorToMetrics: (AttachErrorCode) -> Unit
    ): ComposeLayoutInspectorClient? {
      val project = model.project
      val jar = if (StudioFlags.APP_INSPECTION_USE_DEV_JAR.get()) {
        DEV_JAR // This branch is used by tests
      }
      else {
        val compatibility = apiServices.checkVersion(project.name, process, MINIMUM_COMPOSE_COORDINATE.groupId,
                                                     MINIMUM_COMPOSE_COORDINATE.artifactId, listOf(EXPECTED_CLASS_IN_COMPOSE_LIBRARY))
        val version = compatibility?.version?.takeIf { it.isNotBlank() }
                      ?: return handleError(project, logErrorToMetrics, compatibility?.status.errorCode)
        try {
          InspectorArtifactService.instance.getOrResolveInspectorJar(project, MINIMUM_COMPOSE_COORDINATE.copy(version = version))
        }
        catch (exception: AppInspectionArtifactNotFoundException) {
          return handleError(project, logErrorToMetrics, versionNotFoundAsErrorCode(version))
        }
      }

      // Set force = true, to be more aggressive about connecting the layout inspector if an old version was
      // left running for some reason. This is a better experience than silently falling back to a legacy client.
      val params = LaunchParameters(process, COMPOSE_LAYOUT_INSPECTOR_ID, jar, model.project.name, COMPOSE_INSPECTION_COMPATIBILITY,
                                    force = true)
      return try {
        val messenger = apiServices.launchInspector(params)
        ComposeLayoutInspectorClient(model, treeSettings, messenger, capabilities, launchMonitor).apply { updateSettings() }
      }
      catch (unexpected: AppInspectionException) {
        handleError(project, logErrorToMetrics, unexpected.errorCode)
        null
      }
    }

    /**
     * We were unable to find the compose inspection jar. This can mean eiter:
     * - the app is using a SNAPSHOT for compose:ui:ui but have not specified the VM flag use.snapshot.jar
     * - the jar file wasn't found where it is supposed to be / could not be downloaded
     */
    private fun versionNotFoundAsErrorCode(version: String = "") =
        if (version.endsWith("-SNAPSHOT")) AttachErrorCode.APP_INSPECTION_SNAPSHOT_NOT_SPECIFIED
        else AttachErrorCode.APP_INSPECTION_COMPOSE_INSPECTOR_NOT_FOUND

    private fun handleError(
      project: Project,
      logErrorToMetrics: (AttachErrorCode) -> Unit,
      error: AttachErrorCode
    ): ComposeLayoutInspectorClient? {
      val actions = mutableListOf<AnAction>()
      val message: String = when (error) {
        AttachErrorCode.APP_INSPECTION_MISSING_LIBRARY -> {
          // This is not an error we want to report.
          // The compose.ui.ui was not present, which is normal in a View only application.
          return null
        }
        AttachErrorCode.APP_INSPECTION_VERSION_FILE_NOT_FOUND ->
          LayoutInspectorBundle.message(VERSION_MISSING_MESSAGE_KEY)
        AttachErrorCode.APP_INSPECTION_INCOMPATIBLE_VERSION ->
          LayoutInspectorBundle.message(INCOMPATIBLE_LIBRARY_MESSAGE_KEY, MINIMUM_COMPOSE_COORDINATE.toString())
        AttachErrorCode.APP_INSPECTION_PROGUARDED_APP -> {
          actions.add(InspectorBannerService.LearnMoreAction(PROGUARD_LEARN_MORE))
          LayoutInspectorBundle.message(PROGUARDED_LIBRARY_MESSAGE_KEY)
        }
        AttachErrorCode.APP_INSPECTION_SNAPSHOT_NOT_SPECIFIED ->
          LayoutInspectorBundle.message(INSPECTOR_NOT_FOUND_USE_SNAPSHOT_KEY)
        AttachErrorCode.APP_INSPECTION_COMPOSE_INSPECTOR_NOT_FOUND ->
          LayoutInspectorBundle.message(COMPOSE_INSPECTION_NOT_AVAILABLE_KEY)
        else -> {
          logErrorToMetrics(error)
          return null
        }
      }
      val banner = InspectorBannerService.getInstance(project) ?: return null
      actions.add(banner.DISMISS_ACTION)
      banner.setNotification(message, actions)
      logErrorToMetrics(error)
      return null
    }
  }

  val parametersCache = ComposeParametersCache(this, model)

  /**
   * The caller will supply a running (increasing) number, that can be used to coordinate the responses from
   * varies commands.
   */
  private var lastGeneration = 0

  /**
   * The value of [lastGeneration] when the last recomposition reset command was sent.
   */
  private var lastGenerationReset = 0

  suspend fun getComposeables(rootViewId: Long, newGeneration: Int, forSnapshot: Boolean): GetComposablesResult {
    lastGeneration = newGeneration
    launchMonitor.updateProgress(AttachErrorState.COMPOSE_REQUEST_SENT)
    val response = messenger.sendCommand {
      getComposablesCommand = GetComposablesCommand.newBuilder().apply {
        this.rootViewId = rootViewId
        generation = lastGeneration
        extractAllParameters = forSnapshot
      }.build()
    }
    launchMonitor.updateProgress(AttachErrorState.COMPOSE_RESPONSE_RECEIVED)
    return GetComposablesResult(response.getComposablesResponse, lastGenerationReset >= newGeneration)
  }

  suspend fun getParameters(rootViewId: Long, composableId: Long, anchorHash: Int): GetParametersResponse {
    val response = messenger.sendCommand {
      getParametersCommand = GetParametersCommand.newBuilder().apply {
        this.rootViewId = rootViewId
        this.composableId = composableId
        this.anchorHash = anchorHash
        generation = lastGeneration
      }.build()
    }
    return response.getParametersResponse
  }

  suspend fun getAllParameters(rootViewId: Long): GetAllParametersResponse {
    val response = messenger.sendCommand {
      getAllParametersCommand = GetAllParametersCommand.newBuilder().apply {
        this.rootViewId = rootViewId
        generation = lastGeneration
      }.build()
    }
    return response.getAllParametersResponse
  }

  suspend fun getParameterDetails(
    rootViewId: Long,
    reference: ParameterReference,
    startIndex: Int,
    maxElements: Int
  ): GetParameterDetailsResponse {
    val response = messenger.sendCommand {
      getParameterDetailsCommand = GetParameterDetailsCommand.newBuilder().apply {
        this.rootViewId = rootViewId
        generation = lastGeneration
        this.startIndex = startIndex
        this.maxElements = maxElements
        referenceBuilder.apply {
          composableId = reference.nodeId
          anchorHash = reference.anchorHash
          kind = reference.kind.convert()
          parameterIndex = reference.parameterIndex
          addAllCompositeIndex(reference.indices.asIterable())
        }
      }.build()
    }
    return response.getParameterDetailsResponse
  }

  suspend fun updateSettings(): UpdateSettingsResponse {
    lastGenerationReset = lastGeneration
    val response = messenger.sendCommand {
      updateSettingsCommand = UpdateSettingsCommand.newBuilder().apply {
        includeRecomposeCounts = treeSettings.showRecompositions
        delayParameterExtractions = true
      }.build()
    }
    if (response.hasUpdateSettingsResponse()) {
      capabilities.add(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)
    }
    return response.updateSettingsResponse
  }

  fun disconnect() {
    messenger.scope.cancel()
  }
}

/**
 * Convenience method for wrapping a specific view-inspector command inside a parent
 * app inspection command.
 */
private suspend fun AppInspectorMessenger.sendCommand(initCommand: Command.Builder.() -> Unit): Response {
  val command = Command.newBuilder()
  command.initCommand()
  val bytes = sendRawCommand(command.build().toByteArray())

  // The protobuf parser has a default recursion limit of 100.
  // Increase this limit for the compose inspector because we typically get a deep recursion response.
  val inputStream = CodedInputStream.newInstance(bytes, 0, bytes.size).apply { setRecursionLimit(Integer.MAX_VALUE) }
  return Response.parseFrom(inputStream)
}
