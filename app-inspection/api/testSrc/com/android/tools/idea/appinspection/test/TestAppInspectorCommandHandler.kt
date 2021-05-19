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
package com.android.tools.idea.appinspection.test

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common

/**
 * A command handler implementation that offers convenient substitutions for all of the App Inspection response types. It provides a default
 * response for each response type. See below for details.
 *
 * When they are not provided, the default response is:
 *   CreateInspectorCommand - CreateInspectorResponse with status SUCCESS
 *   DisposeInspectorCommand - DisposeInspectorResponse (it is an empty message)
 *   RawInspectorCommand - RawInspectorResponse with content set to the same content in the command
 *   GetLibraryVersionsCommand - GetLibraryVersionsResponse where each LibraryVersionResponse is of status COMPATIBLE with version set to
 *                               the minVersion provided in the command. This will signal all inspectors are compatible.
 */
class TestAppInspectorCommandHandler(
  timer: FakeTimer,
  private val getLibraryVersionsResponse: ((AppInspection.GetLibraryVersionsCommand) -> AppInspection.GetLibraryVersionsResponse)? = null,
  private val rawInspectorResponse: (() -> AppInspection.AppInspectionResponse)? = null,
  private val disposeInspectorResponse: () -> AppInspection.AppInspectionResponse = DEFAULT_DISPOSE_INSPECTOR_RESPONSE,
  private val createInspectorResponse: () -> AppInspection.AppInspectionResponse = DEFAULT_CREATE_INSPECTOR_RESPONSE
) : CommandHandler(timer) {
  private fun createResponse(command: Commands.Command, appInspectionResponse: AppInspection.AppInspectionResponse) =
    Common.Event.newBuilder()
      .setKind(Common.Event.Kind.APP_INSPECTION_RESPONSE)
      .setPid(command.pid)
      .setTimestamp(timer.currentTimeNs)
      .setCommandId(command.commandId)
      .setIsEnded(true)
      .setAppInspectionResponse(appInspectionResponse)
      .build()

  override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
    when {
      command.appInspectionCommand.hasCreateInspectorCommand() -> {
        events.add(
          createResponse(command, createInspectorResponse().toBuilder().setCommandId(command.appInspectionCommand.commandId).build()))
      }
      command.appInspectionCommand.hasDisposeInspectorCommand() -> {
        events.add(
          createResponse(command, disposeInspectorResponse().toBuilder().setCommandId(command.appInspectionCommand.commandId).build()))
      }
      command.appInspectionCommand.hasRawInspectorCommand() -> {
        events.add(
          createResponse(
            command,
            rawInspectorResponse?.let {
              it().toBuilder().setCommandId(command.appInspectionCommand.commandId).build()
            } ?: getDefaultRawResponse(command)
          )
        )
      }
      command.appInspectionCommand.hasGetLibraryVersionsCommand() -> {
        events.add(
          createResponse(command,
                         AppInspection.AppInspectionResponse.newBuilder()
                           .setCommandId(command.appInspectionCommand.commandId)
                           .setLibraryVersionsResponse(
                             getLibraryVersionsResponse?.let { it(command.appInspectionCommand.getLibraryVersionsCommand) }
                             ?: getDefaultLibraryVersionsResponse(command)
                           )
                           .build()
          )
        )
      }
    }
  }
}

private val DEFAULT_CREATE_INSPECTOR_RESPONSE = createCreateInspectorResponse(
  AppInspection.AppInspectionResponse.Status.SUCCESS,
  AppInspection.CreateInspectorResponse.Status.SUCCESS)

private val DEFAULT_DISPOSE_INSPECTOR_RESPONSE = {
  AppInspection.AppInspectionResponse.newBuilder().setStatus(AppInspection.AppInspectionResponse.Status.SUCCESS)
    .setDisposeInspectorResponse(AppInspection.DisposeInspectorResponse.getDefaultInstance()).build()
}

private fun getDefaultLibraryVersionsResponse(command: Commands.Command): AppInspection.GetLibraryVersionsResponse {
  val responses = command.appInspectionCommand.getLibraryVersionsCommand.targetVersionsList.map { target ->
    AppInspection.LibraryVersionResponse.newBuilder().setVersionFileName(target.versionFileName).setVersion(target.minVersion).setStatus(
      AppInspection.LibraryVersionResponse.Status.COMPATIBLE).build()
  }
  return AppInspection.GetLibraryVersionsResponse.newBuilder()
    .addAllResponses(responses).build()
}

private fun getDefaultRawResponse(command: Commands.Command): AppInspection.AppInspectionResponse {
  return AppInspection.AppInspectionResponse.newBuilder()
    .setStatus(AppInspection.AppInspectionResponse.Status.SUCCESS)
    .setCommandId(command.appInspectionCommand.commandId)
    .setRawResponse(
      AppInspection.RawResponse.newBuilder().setContent(command.appInspectionCommand.rawInspectorCommand.content).build()
    )
    .build()
}

fun createCreateInspectorResponse(status: AppInspection.AppInspectionResponse.Status,
                                  createStatus: AppInspection.CreateInspectorResponse.Status,
                                  error: String? = null): () -> AppInspection.AppInspectionResponse {
  return {
    val builder = AppInspection.AppInspectionResponse.newBuilder()
      .setCreateInspectorResponse(AppInspection.CreateInspectorResponse.newBuilder()
                                    .setStatus(createStatus)
                                    .build())
      .setStatus(status)
    if (error != null) {
      builder.errorMessage = error
    }
    builder.build()
  }
}

fun createRawResponse(status: AppInspection.AppInspectionResponse.Status,
                      content: String): () -> AppInspection.AppInspectionResponse {
  return {
    AppInspection.AppInspectionResponse.newBuilder()
      .setRawResponse(AppInspection.RawResponse.newBuilder().setContent(ByteString.copyFromUtf8(content)).build())
      .setStatus(status)
      .build()
  }
}