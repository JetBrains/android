/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchCommand

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
class ViewLayoutInspectorClient(eventScope: CoroutineScope, private val messenger: AppInspectorMessenger) {

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
                       eventScope: CoroutineScope): ViewLayoutInspectorClient {
      val params = LaunchParameters(process, VIEW_LAYOUT_INSPECTOR_ID, JAR, project.name)
      val messenger = apiServices.launchInspector(params)
      return ViewLayoutInspectorClient(eventScope, messenger)
    }
  }

  init {
    eventScope.launch {
      messenger.eventFlow.collect { eventBytes ->
        val event = Event.parseFrom(eventBytes)
        when (event.specializedCase) {
          Event.SpecializedCase.LAYOUT_EVENT -> handleLayoutEvent(event.layoutEvent)
          else -> error { "Unhandled event case: ${event.specializedCase}" }
        }
      }
    }
  }

  suspend fun startFetching() {
    messenger.sendCommand {
      startFetchCommand = StartFetchCommand.getDefaultInstance()
    }
  }

  suspend fun stopFetching() {
    messenger.sendCommand {
      stopFetchCommand = StopFetchCommand.getDefaultInstance()
    }
  }

  private fun handleLayoutEvent(layoutEvent: LayoutEvent) {
    // TODO: Update model with fetched data
  }
}

/**
 * Convenience method for wrapping a specific view-inspector command inside a parent
 * app inspection command.
 */
private suspend fun AppInspectorMessenger.sendCommand(initCommand: Command.Builder.() -> Unit) {
  val command = Command.newBuilder()
  command.initCommand()
  sendRawCommand(command.build().toByteArray())
}
