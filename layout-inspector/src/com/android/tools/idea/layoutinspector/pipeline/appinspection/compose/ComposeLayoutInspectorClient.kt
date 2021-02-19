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
import com.android.tools.idea.appinspection.inspector.api.AppInspectionAppProguardedException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionIncompatibleException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.cancel
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Command
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersCommand
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesCommand
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParametersCommand
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Response

const val COMPOSE_LAYOUT_INSPECTOR_ID = "layoutinspector.compose.inspection"
private val JAR = AppInspectorJar("compose-ui-inspection.jar",
                                  developmentDirectory = "prebuilts/tools/common/app-inspection/androidx/compose/ui/")

private val MINIMUM_COMPOSE_COORDINATE = ArtifactCoordinate(
  "androidx.compose.ui", "ui", "1.0.0-alpha13", ArtifactCoordinate.Type.AAR
)

@VisibleForTesting
val INCOMPATIBLE_LIBRARY_MESSAGE =
  "Inspecting Compose layouts is available only when connecting to apps using $MINIMUM_COMPOSE_COORDINATE or higher."

@VisibleForTesting
const val PROGUARDED_LIBRARY_MESSAGE = "Inspecting Compose layouts might not work properly with code shrinking enabled."

private const val PROGUARD_LEARN_MORE = "https://d.android.com/r/studio-ui/layout-inspector/code-shrinking"

/**
 * The client responsible for interacting with the compose layout inspector running on the target
 * device.
 *
 * @param messenger The messenger that lets us communicate with the view inspector.
 */
class ComposeLayoutInspectorClient(model: InspectorModel, private val messenger: AppInspectorMessenger) {

  companion object {
    /**
     * Helper function for launching the compose layout inspector and creating a client to interact
     * with it.
     */
    suspend fun launch(apiServices: AppInspectionApiServices,
                       process: ProcessDescriptor,
                       model: InspectorModel): ComposeLayoutInspectorClient? {
      // Set force = true, to be more aggressive about connecting the layout inspector if an old version was
      // left running for some reason. This is a better experience than silently falling back to a legacy client.
      val params = LaunchParameters(process, COMPOSE_LAYOUT_INSPECTOR_ID, JAR, model.project.name, MINIMUM_COMPOSE_COORDINATE, force = true)
      return try {
        val messenger = apiServices.launchInspector(params)
        ComposeLayoutInspectorClient(model, messenger)
      }
      catch (ignored: AppInspectionVersionIncompatibleException) {
        InspectorBannerService.getInstance(model.project).setNotification(INCOMPATIBLE_LIBRARY_MESSAGE)
        null
      }
      catch (ignored: AppInspectionAppProguardedException) {
        val banner = InspectorBannerService.getInstance(model.project)
        banner.setNotification(
          PROGUARDED_LIBRARY_MESSAGE,
          listOf(InspectorBannerService.LearnMoreAction(PROGUARD_LEARN_MORE), banner.DISMISS_ACTION))
        null
      }
      catch (ignored: AppInspectionException) {
        null
      }
    }
  }

  val parametersCache = ComposeParametersCache(this, model)

  suspend fun getComposeables(rootViewId: Long): GetComposablesResponse {
    val response = messenger.sendCommand {
      getComposablesCommand = GetComposablesCommand.newBuilder().apply {
        this.rootViewId = rootViewId
        skipSystemComposables = TreeSettings.hideSystemNodes
      }.build()
    }
    return response.getComposablesResponse
  }

  suspend fun getParameters(rootViewId: Long, composableId: Long): GetParametersResponse {
    val response = messenger.sendCommand {
      getParametersCommand = GetParametersCommand.newBuilder().apply {
        this.rootViewId = rootViewId
        this.composableId = composableId
        skipSystemComposables = TreeSettings.hideSystemNodes
      }.build()
    }
    return response.getParametersResponse
  }

  suspend fun getAllParameters(rootViewId: Long): GetAllParametersResponse {
    val response = messenger.sendCommand {
      getAllParametersCommand = GetAllParametersCommand.newBuilder().apply {
        this.rootViewId = rootViewId
        skipSystemComposables = TreeSettings.hideSystemNodes
      }.build()
    }
    return response.getAllParametersResponse
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
  return Response.parseFrom(sendRawCommand(command.build().toByteArray()))
}
