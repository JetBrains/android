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
package com.android.tools.idea.appinspection.api

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection
import com.android.tools.app.inspection.AppInspection.AppInspectionResponse.Status.ERROR
import com.android.tools.app.inspection.AppInspection.AppInspectionResponse.Status.SUCCESS
import com.android.tools.app.inspection.AppInspection.ServiceResponse
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common.Event
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_RESPONSE

/**
 * A convenient test class for [CommandHandler] for AppInspection service commands.
 */
class TestInspectorCommandHandler(timer: FakeTimer,
                                  private val success: Boolean = true,
                                  private val error: String = "") : CommandHandler(timer) {
  override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
    if (command.appInspectionCommand.hasCreateInspectorCommand() || command.appInspectionCommand.hasDisposeInspectorCommand()) {
      events.add(Event.newBuilder()
                   .setKind(APP_INSPECTION_RESPONSE)
                   .setPid(command.pid)
                   .setTimestamp(timer.currentTimeNs)
                   .setCommandId(command.commandId)
                   .setIsEnded(true)
                   .setAppInspectionResponse(AppInspection.AppInspectionResponse.newBuilder()
                                               .setCommandId(command.appInspectionCommand.commandId)
                                               .setStatus(
                                                 if (success) SUCCESS else ERROR)
                                               .setErrorMessage(error)
                                               .setServiceResponse(ServiceResponse.getDefaultInstance())
                                               .build())
                   .build())
    }
    else if (command.appInspectionCommand.hasRawInspectorCommand()) {
      // If successful, response is a raw event containing the same content as command. Otherwise, event's content is set to provided error.
      events.add(Event.newBuilder()
                   .setKind(APP_INSPECTION_RESPONSE)
                   .setPid(command.pid)
                   .setTimestamp(timer.currentTimeNs)
                   .setCommandId(command.commandId)
                   .setIsEnded(true)
                   .setAppInspectionResponse(AppInspection.AppInspectionResponse.newBuilder()
                                               .setCommandId(command.appInspectionCommand.commandId)
                                               .setRawResponse(AppInspection.RawResponse.newBuilder()
                                                                 .setContent(
                                                                   if (success) command.appInspectionCommand.rawInspectorCommand.content
                                                                   else ByteString.copyFromUtf8(error))
                                                                 .build())
                                               .build())
                   .build())
    }
  }
}

