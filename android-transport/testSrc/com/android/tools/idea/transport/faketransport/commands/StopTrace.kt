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
import java.util.concurrent.TimeUnit

class StopTrace(timer: FakeTimer) : CommandHandler(timer) {
  var stopStatus: Trace.TraceStopStatus = Trace.TraceStopStatus.getDefaultInstance()
  var traceDurationNs: Long = TimeUnit.SECONDS.toNanos(1)
  var lastTraceInfo: Trace.TraceInfo = Trace.TraceInfo.getDefaultInstance()

  override fun handleCommand(command: Command, events: MutableList<Common.Event>) {
    val traceId = timer.currentTimeNs
    val endTimestamp = traceId + traceDurationNs
    // Fake StopTrace command for memory profiler assumes successful stop trace status event
    if (command.stopTrace.profilerType == Trace.ProfilerType.MEMORY) {
      this.stopStatus = Trace.TraceStopStatus.newBuilder().apply {
        status = Trace.TraceStopStatus.Status.SUCCESS
      }.build()
    }

    lastTraceInfo = Trace.TraceInfo.newBuilder()
      .setTraceId(traceId)
      .setFromTimestamp(traceId)
      .setToTimestamp(endTimestamp)
      .setConfiguration(command.stopTrace.configuration)
      .setStopStatus(stopStatus)
      .build()

    events.add(Common.Event.newBuilder().apply {
      groupId = traceId
      pid = command.pid
      kind = Common.Event.Kind.TRACE_STATUS
      timestamp = timer.currentTimeNs
      commandId = command.commandId
      traceStatus = Trace.TraceStatusData.newBuilder().apply {
        traceStopStatus = stopStatus
      }.build()
    }.build())

    // Only inserts a stop event if there is a matching start event with the same trace id
    events.find { it.groupId == traceId }?.let {
      events.add(Common.Event.newBuilder().apply {
        groupId = traceId
        pid = command.pid
        kind = Common.Event.Kind.CPU_TRACE
        timestamp = endTimestamp
        isEnded = true
        traceData = Trace.TraceData.newBuilder().apply {
          traceEnded = Trace.TraceData.TraceEnded.newBuilder().apply {
            traceInfo = lastTraceInfo
          }.build()
        }.build()
      }.build())
    }
  }
}
