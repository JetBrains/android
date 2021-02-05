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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.ErrorEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertiesEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.WindowRootsEvent

const val VIEW_LAYOUT_INSPECTOR_ID = "layoutinspector.view.inspection"
private val JAR = AppInspectorJar("layoutinspector-view-inspection.jar",
                                  developmentDirectory = "bazel-bin/tools/base/dynamic-layout-inspector/agent/appinspection/",
                                  releaseDirectory = "plugins/android/resources/app-inspection/")

/**
 * The client responsible for interacting with the view layout inspector running on the target
 * device.
 *
 * @param scope A coroutine scope used for asynchronously responding to events coming in from
 *     the inspector and other miscellaneous coroutine tasks.
 *
 * @param messenger The messenger that lets us communicate with the view inspector.
 *
 * @param composeInspector An inspector which, if provided, lets us fetch additional data useful
 *     for compose related values contained within our view tree.
 */
class ViewLayoutInspectorClient(
  model: InspectorModel,
  private val scope: CoroutineScope,
  private val messenger: AppInspectorMessenger,
  private val composeInspector: ComposeLayoutInspectorClient?,
  private val fireError: (String) -> Unit = {},
  private val fireTreeEvent: (Data) -> Unit = {},
) {

  /**
   * Data packaged up and sent via [fireTreeEvent], needed for constructing the tree view in the
   * layout inspector.
   */
  class Data(
    val generation: Int,
    val rootIds: List<Long>,
    val viewEvent: LayoutEvent,
    val composeEvent: GetComposablesResponse?,
    val updateScreenshotType: (Screenshot.Type) -> Unit
  )

  companion object {
    /**
     * Helper function for launching the view layout inspector and creating a client to interact
     * with it.
     *
     * @param eventScope Scope which will be used for processing incoming inspector events. It's
     *     expected that this will be a scope associated with a background dispatcher.
     */
    suspend fun launch(apiServices: AppInspectionApiServices,
                       process: ProcessDescriptor,
                       model: InspectorModel,
                       eventScope: CoroutineScope,
                       composeLayoutInspectorClient: ComposeLayoutInspectorClient?,
                       fireError: (String) -> Unit,
                       fireTreeEvent: (Data) -> Unit): ViewLayoutInspectorClient {
      // Set force = true, to be more aggressive about connecting the layout inspector if an old version was
      // left running for some reason. This is a better experience than silently falling back to a legacy client.
      val params = LaunchParameters(process, VIEW_LAYOUT_INSPECTOR_ID, JAR, model.project.name, force = true)
      val messenger = apiServices.launchInspector(params)
      return ViewLayoutInspectorClient(model, eventScope, messenger, composeLayoutInspectorClient, fireError, fireTreeEvent)
    }
  }

  val propertiesCache = ViewPropertiesCache(this, model)

  /**
   * Whether this client is continuously receiving layout events or not.
   *
   * This will be true between calls to `startFetching(continuous = true)` and
   * `stopFetching`.
   */
  private var isFetchingContinuously: Boolean = false
    set(value) {
      field = value
      propertiesCache.allowFetching = value
      composeInspector?.parametersCache?.allowFetching = value
    }

  private var generation = 0 // Update the generation each time we get a new LayoutEvent
  private val currRoots = mutableListOf<Long>()

  init {
    scope.launch {
      messenger.eventFlow.collect { eventBytes ->
        val event = Event.parseFrom(eventBytes)
        when (event.specializedCase) {
          Event.SpecializedCase.ERROR_EVENT -> handleErrorEvent(event.errorEvent)
          Event.SpecializedCase.ROOTS_EVENT -> handleRootsEvent(event.rootsEvent)
          Event.SpecializedCase.LAYOUT_EVENT -> handleLayoutEvent(event.layoutEvent)
          Event.SpecializedCase.PROPERTIES_EVENT -> handlePropertiesEvent(event.propertiesEvent)
          else -> error { "Unhandled event case: ${event.specializedCase}" }
        }
      }
    }
  }

  suspend fun startFetching(continuous: Boolean) {
    isFetchingContinuously = continuous
    messenger.sendCommand {
      startFetchCommand = StartFetchCommand.newBuilder().apply {
        this.continuous = continuous
        skipSystemViews = TreeSettings.hideSystemNodes
      }.build()
    }
  }

  private suspend fun updateScreenshotType(type: Screenshot.Type, scale: Float = 1.0f) {
    messenger.sendCommand {
      updateScreenshotTypeCommandBuilder.apply {
        this.type = type
        this.scale = scale
      }
    }
  }

  suspend fun stopFetching() {
    isFetchingContinuously = false
    messenger.sendCommand {
      stopFetchCommand = StopFetchCommand.getDefaultInstance()
    }
  }

  suspend fun getProperties(rootViewId: Long, viewId: Long): GetPropertiesResponse {
    val response = messenger.sendCommand {
      getPropertiesCommand = GetPropertiesCommand.newBuilder().apply {
        this.rootViewId = rootViewId
        this.viewId = viewId
      }.build()
    }
    return response.getPropertiesResponse
  }

  fun disconnect() {
    messenger.scope.cancel()
  }

  private fun handleErrorEvent(errorEvent: ErrorEvent) {
    fireError(errorEvent.message)
  }

  private fun handleRootsEvent(rootsEvent: WindowRootsEvent) {
    currRoots.clear()
    currRoots.addAll(rootsEvent.idsList)

    propertiesCache.retain(currRoots)
    composeInspector?.parametersCache?.retain(currRoots)
  }

  private suspend fun handleLayoutEvent(layoutEvent: LayoutEvent) {
    generation++
    propertiesCache.clearFor(layoutEvent.rootView.id)
    composeInspector?.parametersCache?.clearFor(layoutEvent.rootView.id)

    val composablesResponse = composeInspector?.getComposeables(layoutEvent.rootView.id)
    fireTreeEvent(Data(
      generation,
      currRoots,
      layoutEvent,
      composablesResponse,
      updateScreenshotType = { type ->
        scope.launch { updateScreenshotType(type) }
      }))
  }

  private suspend fun handlePropertiesEvent(propertiesEvent: PropertiesEvent) {
    propertiesCache.setAllFrom(propertiesEvent)

    composeInspector?.let {
      it.parametersCache.setAllFrom(it.getAllParameters(propertiesEvent.rootId))
    }
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
