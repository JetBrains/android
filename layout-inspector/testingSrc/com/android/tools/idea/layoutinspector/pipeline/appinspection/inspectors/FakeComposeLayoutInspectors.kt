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

import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Command
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Event
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParameterDetailsResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetRecompositionStateReadResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Response
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.UpdateSettingsResponse
import kotlin.test.fail

fun FakeInspector.Connection<Event>.sendEvent(init: Event.Builder.() -> Unit) {
  sendEvent(Event.newBuilder().apply(init).build())
}

class FakeComposeLayoutInspector(connection: Connection<Event>) :
  FakeInspector<Command, Response, Event>(connection) {

  override fun handleCommandImpl(command: Command): Response {
    return when (command.specializedCase) {
      Command.SpecializedCase.GET_COMPOSABLES_COMMAND ->
        Response.newBuilder()
          .setGetComposablesResponse(GetComposablesResponse.getDefaultInstance())
          .build()
      Command.SpecializedCase.GET_PARAMETERS_COMMAND ->
        Response.newBuilder()
          .setGetParametersResponse(GetParametersResponse.getDefaultInstance())
          .build()
      Command.SpecializedCase.GET_ALL_PARAMETERS_COMMAND ->
        Response.newBuilder()
          .setGetAllParametersResponse(GetAllParametersResponse.getDefaultInstance())
          .build()
      Command.SpecializedCase.UPDATE_SETTINGS_COMMAND ->
        Response.newBuilder()
          .setUpdateSettingsResponse(UpdateSettingsResponse.getDefaultInstance())
          .build()
      Command.SpecializedCase.GET_PARAMETER_DETAILS_COMMAND ->
        Response.newBuilder()
          .setGetParameterDetailsResponse(GetParameterDetailsResponse.getDefaultInstance())
          .build()
      Command.SpecializedCase.GET_RECOMPOSITION_STATE_READ_COMMAND ->
        Response.newBuilder()
          .setGetRecompositionStateReadResponse(
            GetRecompositionStateReadResponse.getDefaultInstance()
          )
          .build()
      else -> fail("Unhandled view inspector command: ${command.specializedCase}")
    }
  }
}
