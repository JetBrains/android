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

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.analytics.currentIdeBrand
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
import com.android.tools.idea.layoutinspector.pipeline.ErrorInfo
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLaunchMonitor
import com.android.tools.idea.layoutinspector.pipeline.appinspection.errorCode
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.protobuf.CodedInputStream
import com.android.tools.idea.transport.TransportException
import com.android.tools.idea.util.StudioPathManager
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
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
import java.nio.file.Paths
import java.util.EnumSet
import kotlin.io.path.name
import kotlin.io.path.pathString

const val COMPOSE_LAYOUT_INSPECTOR_ID = "layoutinspector.compose.inspection"

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

@VisibleForTesting
const val COMPOSE_MAY_CAUSE_APP_CRASH_KEY = "compose.inspection.may.cause.app.crash"

@VisibleForTesting
const val MAVEN_DOWNLOAD_PROBLEM = "maven.download.problem"

@VisibleForTesting
const val COMPOSE_JAR_FOUND_FOUND_KEY = "compose.jar.not.found"

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
      logErrorToMetrics: (AttachErrorCode) -> Unit,
      @VisibleForTesting isRunningFromSourcesInTests: Boolean? = null // Should only be set from tests
    ): ComposeLayoutInspectorClient? {
      val project = model.project
      var requiredCompatibility: LibraryCompatibility? = null
      val jar = if (StudioFlags.APP_INSPECTION_USE_DEV_JAR.get()) {
        // This dev jar is used for:
        // - most tests (developmentDirectory)
        // - development on studio using an androidx-main (developmentDirectory)
        // - development on androidx-main using released version of studio (releaseDirectory)
        // - released version of studio using local artifact of unreleased compose version (releaseDirectory)
        AppInspectorJar(
          "compose-ui-inspection.jar",
          developmentDirectory = resolveFolder(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_DEVELOPMENT_FOLDER.get()),
          releaseDirectory = resolveFolder(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_RELEASE_FOLDER.get())
        )
      }
      else if (currentIdeBrand() == AndroidStudioEvent.IdeBrand.ANDROID_STUDIO_WITH_BLAZE) {
        requiredCompatibility = COMPOSE_INSPECTION_COMPATIBILITY.copy(
          coordinate = COMPOSE_INSPECTION_COMPATIBILITY.coordinate.copy(version = "+"))
        try {
          InspectorArtifactService.instance.getOrResolveInspectorJar(project, requiredCompatibility.coordinate)
        }
        catch (exception: AppInspectionArtifactNotFoundException) {
          return handleError(project, logErrorToMetrics, isRunningFromSourcesInTests, exception.errorCode)
        }
      }
      else {
        requiredCompatibility = COMPOSE_INSPECTION_COMPATIBILITY
        val compatibility = apiServices.checkVersion(project.name, process, MINIMUM_COMPOSE_COORDINATE.groupId,
                                                     MINIMUM_COMPOSE_COORDINATE.artifactId, listOf(EXPECTED_CLASS_IN_COMPOSE_LIBRARY))
        val version = compatibility?.version?.takeIf { it.isNotBlank() }
                      ?: return handleError(project, logErrorToMetrics, isRunningFromSourcesInTests, compatibility?.status.errorCode)

        checkComposeVersion(project, version)

        try {
          InspectorArtifactService.instance.getOrResolveInspectorJar(project, MINIMUM_COMPOSE_COORDINATE.copy(version = version))
        }
        catch (exception: AppInspectionArtifactNotFoundException) {
          return handleError(project, logErrorToMetrics, isRunningFromSourcesInTests, exception.errorCode)
        }
      }

      // Set force = true, to be more aggressive about connecting the layout inspector if an old version was
      // left running for some reason. This is a better experience than silently falling back to a legacy client.
      val params = LaunchParameters(process, COMPOSE_LAYOUT_INSPECTOR_ID, jar, model.project.name, requiredCompatibility, force = true)
      return try {
        val messenger = apiServices.launchInspector(params)
        ComposeLayoutInspectorClient(model, treeSettings, messenger, capabilities, launchMonitor).apply { updateSettings() }
      }
      catch (unexpected: AppInspectionException) {
        handleError(project, logErrorToMetrics, isRunningFromSourcesInTests, unexpected.errorCode)
      }
      catch (unexpected: TransportException) {
        handleError(project, logErrorToMetrics, isRunningFromSourcesInTests, unexpected.errorCode)
      }
    }

    private fun resolveFolder(folder: String?): String? = resolveFolder(".", folder)

    /**
     * Resolve the [folder] to a possible initial parent reference.
     *
     * This functionality is added for developers on androidx-main where the depth of the current directory (where studio is started from)
     * depends on the platform. By adding the "#studio" the reference would work on all platforms.
     *
     * The name after an initial '#' is regarded as a parent reference. The parent reference is matched to a parent folder of
     * [currentFolder]. The returned path will make [folder] relative to the matched parent folder.
     *
     * Example: if the currentFolder is:
     *    "/Volumes/android/androidx-main/frameworks/support/studio/android-studio-2022.2.1.5-mac/Android Studio Preview.app/Contents"
     * Then a folder spec of "#studio/../../../out/some-folder" will be resolved to:
     *    "../../../../../../out/some-folder"
     * which later will be resolved to the absolute path:
     *    "/Volumes/android/androidx-main/out/some-folder"
     */
    @VisibleForTesting
    fun resolveFolder(currentFolder: String, folder: String?): String? {
      if (folder?.startsWith("#") != true) {
        return folder.nullize()
      }
      val currentDir = Paths.get(currentFolder).toAbsolutePath()
      val devPath = Paths.get(folder)
      val searchFor = devPath.getName(0).pathString.substring(1)
      var depth = 0
      for (i in 0 until currentDir.nameCount) {
        if (currentDir.getName(currentDir.nameCount - 1 - i).name == searchFor) {
          depth = i
          break
        }
      }
      val restPath = devPath.subpath(1, devPath.nameCount)
      if (depth == 0) {
        return restPath.pathString
      }
      var path = Paths.get("..")
      for (i in 1 until depth) {
        path = path.resolve("..")
      }
      for (part in restPath) {
        path = path.resolve(part)
      }
      return path.pathString
    }

    private fun handleError(
      project: Project,
      logErrorToMetrics: (AttachErrorCode) -> Unit,
      isRunningFromSourcesInTests: Boolean?,
      error: ErrorInfo
    ): ComposeLayoutInspectorClient? {
      val actions = mutableListOf<AnAction>()
      val message: String = when (error.code) {
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
        AttachErrorCode.APP_INSPECTION_FAILED_MAVEN_DOWNLOAD ->
          LayoutInspectorBundle.message(MAVEN_DOWNLOAD_PROBLEM, error.args["artifact"]!!)
        AttachErrorCode.TRANSPORT_PUSH_FAILED_FILE_NOT_FOUND ->
          LayoutInspectorBundle.message(COMPOSE_JAR_FOUND_FOUND_KEY, error.args["path"]!!, inspectorFolderFlag(isRunningFromSourcesInTests))
        else -> {
          logErrorToMetrics(error.code)
          return null
        }
      }
      val banner = InspectorBannerService.getInstance(project) ?: return null
      actions.add(banner.DISMISS_ACTION)
      banner.setNotification(message, actions)
      logErrorToMetrics(error.code)
      return null
    }

    /**
     * Check for problems with the specified compose version.
     * @return false if the compose inspector should not be started for this version.
     */
    private fun checkComposeVersion(project: Project, versionString: String) {
      val version = GradleVersion.tryParse(versionString) ?: return
      // b/237987764 App crash while fetching parameters with empty lambda was fixed in 1.3.0-alpha03 and in 1.2.1
      // b/235526153 App crash while fetching component tree with certain Borders was fixed in 1.3.0-alpha03 and in 1.2.1
      if (version.isAtLeast(1, 3, 0, "alpha", 3, false) || (version.minor == 2 && version.isAtLeast(1, 2, 1))) return
      val versionUpgrade = if (version.minor == 3) "1.3.0" else "1.2.1"
      val banner = InspectorBannerService.getInstance(project) ?: return
      banner.setNotification(LayoutInspectorBundle.message(COMPOSE_MAY_CAUSE_APP_CRASH_KEY, versionString, versionUpgrade))
      // Allow the user to connect and inspect compose elements because:
      // - b/235526153 is uncommon
      // - b/237987764 only happens if the kotlin compiler version is at least 1.6.20 (which we cannot reliably detect)
    }

    /**
     * Return the flag name that can be used to specify the folder of the compose inspector if running on dev jar
     * i.e. if [StudioFlags.APP_INSPECTION_USE_DEV_JAR] is turned on.
     */
    private fun inspectorFolderFlag(isRunningFromSourcesInTests: Boolean?): String =
      if (isRunningFromSourcesInTests ?: StudioPathManager.isRunningFromSources())
        StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_DEVELOPMENT_FOLDER.id
      else
        StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_RELEASE_FOLDER.id
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
