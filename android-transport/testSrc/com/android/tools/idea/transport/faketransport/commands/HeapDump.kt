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
import com.android.tools.profiler.proto.Memory

/**
 * Handles the HEAP_DUMP command in tests. This is responsible for generating a status event, then if |dumpStatus| is
 * set to be |SUCCUESS|, also generates the start and end event pair for the heap dump. The start event's timestamp
 * is the current timer's timestamp and the end event's timestamp is +1 of that.
 */
class HeapDump(timer: FakeTimer, isTaskBasedUxEnabled: Boolean) : CommandHandler(timer, isTaskBasedUxEnabled) {
  var dumpStatus = Memory.HeapDumpStatus.Status.UNSPECIFIED

  override fun handleCommand(command: Command, events: MutableList<Common.Event>) {
    val dumpStartTime = timer.currentTimeNs

    events.add(Common.Event.newBuilder().apply {
      pid = command.pid
      kind = Common.Event.Kind.MEMORY_HEAP_DUMP_STATUS
      timestamp = dumpStartTime
      commandId = command.commandId
      memoryHeapdumpStatus = Memory.MemoryHeapDumpStatusData.newBuilder().apply {
        status = Memory.HeapDumpStatus.newBuilder().setStatus(dumpStatus).build()
      }.build()
    }.build())

    if (dumpStatus == Memory.HeapDumpStatus.Status.SUCCESS) {
      events.add(Common.Event.newBuilder().apply {
        groupId = dumpStartTime
        pid = command.pid
        kind = Common.Event.Kind.MEMORY_HEAP_DUMP
        timestamp = dumpStartTime
        memoryHeapdump = Memory.MemoryHeapDumpData.newBuilder().apply {
          info = Memory.HeapDumpInfo.newBuilder().apply {
            startTime = dumpStartTime
            endTime = Long.MAX_VALUE
          }.build()
        }.build()
      }.build())

      events.add(Common.Event.newBuilder().apply {
        groupId = dumpStartTime
        pid = command.pid
        kind = Common.Event.Kind.MEMORY_HEAP_DUMP
        timestamp = dumpStartTime + 1
        memoryHeapdump = Memory.MemoryHeapDumpData.newBuilder().apply {
          info = Memory.HeapDumpInfo.newBuilder().apply {
            startTime = dumpStartTime
            endTime = dumpStartTime + 1
            success = true
          }.build()
        }.build()
      }.build())

      addSessionEndedEvent(command, events)
    }
  }
}