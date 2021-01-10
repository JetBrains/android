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
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.ErrorEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
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
 * @param eventScope A coroutine scope used for asynchronously responding to events coming in from
 *     the inspector.
 *
 * @param messenger The messenger that lets us communicate with the view inspector.
 */
class ViewLayoutInspectorClient(
  eventScope: CoroutineScope,
  private val messenger: AppInspectorMessenger,
  private val fireError: (String) -> Unit = {},
  private val fireTreeEvent: (Data) -> Unit = {},
) {

  /**
   * Data packaged up and sent via [fireTreeEvent], needed for constructing the tree view in the
   * layout inspector.
   */
  class Data(
    val rootIds: List<Long>,
    val layoutEvent: LayoutEvent
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
                       project: Project,
                       process: ProcessDescriptor,
                       eventScope: CoroutineScope,
                       fireError: (String) -> Unit,
                       fireTreeEvent: (Data) -> Unit): ViewLayoutInspectorClient {
      val params = LaunchParameters(process, VIEW_LAYOUT_INSPECTOR_ID, JAR, project.name)
      val messenger = apiServices.launchInspector(params)
      return ViewLayoutInspectorClient(eventScope, messenger, fireError, fireTreeEvent)
    }
  }

  /**
   * Whether this client is continuously receiving layout events or not.
   *
   * This will be true between calls to `startFetching(continuous = true)` and
   * `stopFetching`.
   */
  var isFetchingContinuously: Boolean = false
    private set

  private val currRoots = mutableListOf<Long>()

  init {
    eventScope.launch {
      messenger.eventFlow.collect { eventBytes ->
        val event = Event.parseFrom(eventBytes)
        when (event.specializedCase) {
          Event.SpecializedCase.ERROR_EVENT -> handleErrorEvent(event.errorEvent)
          Event.SpecializedCase.ROOTS_EVENT -> handleRootsEvent(event.rootsEvent)
          Event.SpecializedCase.LAYOUT_EVENT -> handleLayoutEvent(event.layoutEvent)
          else -> error { "Unhandled event case: ${event.specializedCase}" }
        }
      }
    }
  }

  suspend fun startFetching(continuous: Boolean) {
    messenger.sendCommand {
      startFetchCommand = StartFetchCommand.newBuilder()
        .setContinuous(continuous)
        .build()
    }
    isFetchingContinuously = continuous
  }

  suspend fun stopFetching() {
    messenger.sendCommand {
      stopFetchCommand = StopFetchCommand.getDefaultInstance()
    }
    isFetchingContinuously = false
  }

  suspend fun fetchProperties(viewId: Long): LayoutInspectorViewProtocol.GetPropertiesResponse {
    val response = messenger.sendCommand {
      getPropertiesCommand = GetPropertiesCommand.newBuilder()
        .setViewId(viewId)
        .build()
    }
    return response.getPropertiesResponse
  }

  private fun handleErrorEvent(errorEvent: ErrorEvent) {
    fireError(errorEvent.message)
  }

  private fun handleRootsEvent(rootsEvent: WindowRootsEvent) {
    currRoots.clear()
    currRoots.addAll(rootsEvent.idsList)
  }

  private fun handleLayoutEvent(layoutEvent: LayoutEvent) {
    fireTreeEvent(Data(currRoots, layoutEvent))
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
