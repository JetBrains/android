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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors

import com.android.tools.idea.concurrency.executeOnPooledThread
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertiesEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.UpdateScreenshotTypeResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.WindowRootsEvent
import kotlin.test.fail

fun FakeInspector.Connection<Event>.sendEvent(init: Event.Builder.() -> Unit) {
  sendEvent(Event.newBuilder().apply(init).build())
}

class FakeViewLayoutInspector(connection: Connection<Event>)
  : FakeInspector<Command, Response, Event>(connection) {

  private fun sendProgress(progress: LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint) {
    connection.sendEvent {
      progressEvent = LayoutInspectorViewProtocol.ProgressEvent.newBuilder().apply {
        checkpoint = progress
      }.build()
    }
  }

  override fun handleCommandImpl(command: Command): Response {
    return when (command.specializedCase) {
      Command.SpecializedCase.START_FETCH_COMMAND -> {
        sendProgress(LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.START_RECEIVED)
        sendProgress(LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.STARTED)
        sendProgress(LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.ROOTS_EVENT_SENT)
        connection.sendEvent {
          rootsEvent = WindowRootsEvent.newBuilder().addIds(123).build()
        }
        sendProgress(LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.VIEW_INVALIDATION_CALLBACK)
        sendProgress(LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.SCREENSHOT_CAPTURED)
        sendProgress(LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.VIEW_HIERARCHY_CAPTURED)
        sendProgress(LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.RESPONSE_SENT)
        connection.sendEvent {
          layoutEvent = LayoutEvent.newBuilder().apply { rootViewBuilder.id = 123 }.build()
        }
        if (!command.startFetchCommand.continuous) {
          connection.sendEvent {
            propertiesEvent = PropertiesEvent.getDefaultInstance()
          }
        }
        Response.newBuilder().setStartFetchResponse(StartFetchResponse.getDefaultInstance()).build()
      }
      Command.SpecializedCase.UPDATE_SCREENSHOT_TYPE_COMMAND -> {
        Response.newBuilder().setUpdateScreenshotTypeResponse(UpdateScreenshotTypeResponse.getDefaultInstance()).build()
      }
      Command.SpecializedCase.STOP_FETCH_COMMAND -> {
        Response.newBuilder().setStopFetchResponse(StopFetchResponse.getDefaultInstance()).build()
      }
      Command.SpecializedCase.GET_PROPERTIES_COMMAND -> {
        Response.newBuilder().setGetPropertiesResponse(GetPropertiesResponse.getDefaultInstance()).build()
      }
      else -> fail("Unhandled view inspector command: ${command.specializedCase}")
    }
  }
}
