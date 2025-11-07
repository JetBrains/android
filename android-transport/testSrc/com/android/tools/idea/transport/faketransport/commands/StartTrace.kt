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
package com.android.tools.idea.transport.faketransport.commands

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Trace

class StartTrace(timer: FakeTimer) : CommandHandler(timer) {
  var startStatus: Trace.TraceStartStatus = Trace.TraceStartStatus.getDefaultInstance()

  /**
   * Generates the events required to start a trace recording based on the current [startStatus].
   *
   * The handler performs following actions:
   * 1. Info Creation: Builds a [Trace.TraceInfo] with a temporary `toTimestamp` of -1, marking the trace as in-progress.
   * 2. Report Trace Status: Emits a [Common.Event.Kind.TRACE_STATUS] event to report the immediate success or failure of the request.
   * 3. Signal Trace Start: If the status is successful, emits an additional [Common.Event.Kind.CPU_TRACE] event, carrying the
   * [Trace.TraceInfo] to signal the profiler stage that a recording has begun.
   *
   * @param command The incoming START_TRACE command.
   * @param events The list to which generated events are added.
   */
  override fun handleCommand(command: Command, events: MutableList<Common.Event>) {
    val traceId = timer.currentTimeNs

    val info = Trace.TraceInfo.newBuilder()
      .setTraceId(traceId)
      .setFromTimestamp(traceId)
      .setToTimestamp(-1)
      .setConfiguration(command.startTrace.configuration)
      .setStartStatus(startStatus)
      .build()

    events.add(Common.Event.newBuilder().apply {
      groupId = traceId
      pid = command.pid
      kind = Common.Event.Kind.TRACE_STATUS
      timestamp = timer.currentTimeNs
      commandId = command.commandId
      traceStatus = Trace.TraceStatusData.newBuilder().apply {
        traceStartStatus = startStatus
      }.build()
    }.build())

    if (startStatus.status == Trace.TraceStartStatus.Status.SUCCESS) {
      events.add(Common.Event.newBuilder().apply {
        groupId = traceId
        pid = command.pid
        kind = Common.Event.Kind.CPU_TRACE
        timestamp = timer.currentTimeNs
        traceData = Trace.TraceData.newBuilder().apply {
          traceStarted = Trace.TraceData.TraceStarted.newBuilder().apply {
            traceInfo = info
          }.build()
        }.build()
      }.build())
    }
  }
}
