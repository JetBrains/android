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
package com.android.tools.idea.transport.faketransport.commands

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Trace

/**
 * Test helper to handle native memory commands. This helper assumes both start and stop are success and return
 * events similar to perfd.
 */
class MemoryNativeSampling(timer: FakeTimer) : CommandHandler(timer) {
  private var startCommandTimestamp = 0L
  override fun handleCommand(command: Command, events: MutableList<Common.Event>) {
    if (command.hasStartNativeSample()) {
      startCommandTimestamp = timer.currentTimeNs
      events.add(Common.Event.newBuilder().apply {
        pid = command.pid
        commandId = command.commandId
        kind = Common.Event.Kind.TRACE_STATUS
        timestamp = timer.currentTimeNs
        groupId = startCommandTimestamp
        traceStatus = Trace.TraceStatusData.newBuilder().apply {
          traceStartStatus = Trace.TraceStartStatus.newBuilder().apply {
            startTimeNs = timer.currentTimeNs
            status = Trace.TraceStartStatus.Status.SUCCESS
          }.build()
        }.build()
      }.build())
      events.add(Common.Event.newBuilder().apply {
        pid = command.pid
        commandId = command.commandId
        kind = Common.Event.Kind.MEM_TRACE
        timestamp = timer.currentTimeNs
        groupId = startCommandTimestamp
        traceData = Trace.TraceData.newBuilder().apply {
          traceStarted = Trace.TraceData.TraceStarted.newBuilder().apply {
            traceInfo = Trace.TraceInfo.newBuilder().apply {
              fromTimestamp = timer.currentTimeNs
              toTimestamp = Long.MAX_VALUE
            }.build()
          }.build()
        }.build()
      }.build())
    }
    else {
      events.add(Common.Event.newBuilder().apply {
        pid = command.pid
        commandId = command.commandId
        kind = Common.Event.Kind.TRACE_STATUS
        timestamp = timer.currentTimeNs
        groupId = startCommandTimestamp
        traceStatus = Trace.TraceStatusData.newBuilder().apply {
          traceStopStatus = Trace.TraceStopStatus.newBuilder().apply {
            status = Trace.TraceStopStatus.Status.SUCCESS
          }.build()
        }.build()
      }.build())
      events.add(Common.Event.newBuilder().apply {
        pid = command.pid
        commandId = command.commandId
        kind = Common.Event.Kind.MEM_TRACE
        timestamp = timer.currentTimeNs
        groupId = startCommandTimestamp
        traceData = Trace.TraceData.newBuilder().apply {
          traceEnded = Trace.TraceData.TraceEnded.newBuilder().apply {
            traceInfo = Trace.TraceInfo.newBuilder().apply {
              fromTimestamp = startCommandTimestamp
              toTimestamp = timer.currentTimeNs
            }.build()
          }.build()
        }.build()
      }.build())
    }
  }
}
