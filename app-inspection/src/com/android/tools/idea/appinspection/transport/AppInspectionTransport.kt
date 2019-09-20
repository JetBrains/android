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
package com.android.tools.idea.appinspection.transport

import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Small helper class to work with the one exact process and app-inspection events & commands.
 */
internal class AppInspectionTransport(
  private val client: TransportClient,
  private val stream: Common.Stream,
  private val process: Common.Process,
  val poller: TransportEventPoller = TransportEventPoller.createPoller(client.transportStub,
                                                                       TimeUnit.MILLISECONDS.toNanos(100))
) {

  fun createEventListener(
    executor: Executor,
    filter: (Common.Event) -> Boolean = { true },
    eventKind: Common.Event.Kind = Common.Event.Kind.APP_INSPECTION,
    callback: (Common.Event) -> Boolean
  ) = TransportEventListener(eventKind = eventKind,
                             executor = executor,
                             streamId = stream::getStreamId,
                             filter = filter,
                             processId = process::getPid,
                             callback = callback)

  fun registerEventListener(
    executor: Executor,
    filter: (Common.Event) -> Boolean = { true },
    eventKind: Common.Event.Kind = Common.Event.Kind.APP_INSPECTION,
    callback: (Common.Event) -> Boolean
  ): TransportEventListener {
    val listener = createEventListener(executor, filter, eventKind, callback)
    poller.registerListener(listener)
    return listener
  }

  fun executeCommand(appInspectionCommand: AppInspection.AppInspectionCommand): Transport.ExecuteResponse {
    val command = Commands.Command.newBuilder()
      .setType(Commands.Command.CommandType.APP_INSPECTION)
      .setStreamId(stream.streamId)
      .setPid(process.pid)
      .setAppInspectionCommand(appInspectionCommand)
    return client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build())
  }
}