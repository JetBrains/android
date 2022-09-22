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
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import java.util.concurrent.TimeUnit

class StopCpuTrace(timer: FakeTimer) : CommandHandler(timer) {
  var stopStatus: Trace.TraceStopStatus = Trace.TraceStopStatus.getDefaultInstance()
  var traceDurationNs: Long = TimeUnit.SECONDS.toNanos(1)
  var lastTraceInfo: Cpu.CpuTraceInfo = Cpu.CpuTraceInfo.getDefaultInstance()

  override fun handleCommand(command: Command, events: MutableList<Common.Event>) {
    val traceId = timer.currentTimeNs
    val endTimestamp = traceId + traceDurationNs
    lastTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setTraceId(traceId)
      .setFromTimestamp(traceId)
      .setToTimestamp(endTimestamp)
      .setConfiguration(command.stopCpuTrace.configuration)
      .setStopStatus(stopStatus)
      .build()

    events.add(Common.Event.newBuilder().apply {
      groupId = traceId
      pid = command.pid
      kind = Common.Event.Kind.CPU_TRACE_STATUS
      timestamp = timer.currentTimeNs
      commandId = command.commandId
      cpuTraceStatus = Trace.TraceStatusData.newBuilder().apply {
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
        cpuTrace = Cpu.CpuTraceData.newBuilder().apply {
          traceEnded = Cpu.CpuTraceData.TraceEnded.newBuilder().apply {
            traceInfo = lastTraceInfo
          }.build()
        }.build()
      }.build())
    }
  }
}
