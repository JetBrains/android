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
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory

/**
 * Handles both the START_ALLOC_TRACKING and STOP_ALLOC_TRACKING commands in tests. This is responsible for generating a status event.
 * For the start tracking command,  if |trackStatus| is set to be |SUCCESS|, this generates a start event with timestamp matching what is
 * specified in |trackStatus|. For the end tracking command, an event (start timestamp + 1) is only added if a start event already
 * exists in the input event list.
 */
class MemoryAllocTracking(timer: FakeTimer, isTaskBasedUxEnabled: Boolean) : CommandHandler(timer, isTaskBasedUxEnabled) {
  var legacyTracking = false
  var trackStatus = Memory.TrackStatus.getDefaultInstance()
  var lastInfo = Memory.AllocationsInfo.getDefaultInstance()
  var lastCommand = Commands.Command.getDefaultInstance()

  override fun handleCommand(command: Command, events: MutableList<Common.Event>) {
    val isStartTracking = command.type == Command.CommandType.START_ALLOC_TRACKING
    val infoId = timer.currentTimeNs
    lastCommand = command
    lastInfo = Memory.AllocationsInfo.newBuilder()
      .setStartTime(trackStatus.startTime)
      .setEndTime(if (isStartTracking) Long.MAX_VALUE else trackStatus.startTime + 1)
      .setLegacy(legacyTracking)
      .setSuccess(!isStartTracking)
      .build()

    events.add(Common.Event.newBuilder().apply {
      groupId = infoId
      pid = command.pid
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING_STATUS
      timestamp = timer.currentTimeNs
      commandId = command.commandId
      memoryAllocTrackingStatus = Memory.MemoryAllocTrackingStatusData.newBuilder().apply {
        status = trackStatus
      }.build()
    }.build())

    if (isStartTracking) {
      if (trackStatus.status == Memory.TrackStatus.Status.SUCCESS) {
        events.add(Common.Event.newBuilder().apply {
          groupId = infoId
          pid = command.pid
          kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING
          timestamp = timer.currentTimeNs
          memoryAllocTracking = Memory.MemoryAllocTrackingData.newBuilder().apply {
            info = lastInfo
          }.build()
        }.build())
      }
    }
    else {
      // Only inserts a stop event if there is a matching start tracking event.
      events.find { it.groupId == infoId && it.kind == Common.Event.Kind.MEMORY_ALLOC_TRACKING}?.let {
        events.add(Common.Event.newBuilder().apply {
          groupId = infoId
          pid = command.pid
          kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING
          timestamp = timer.currentTimeNs
          isEnded = true
          memoryAllocTracking = Memory.MemoryAllocTrackingData.newBuilder().apply {
            info = lastInfo
          }.build()
        }.build())

        addSessionEndedEvent(command, events)
      }
    }
  }
}
