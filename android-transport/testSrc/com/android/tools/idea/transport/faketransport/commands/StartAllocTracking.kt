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

class StartAllocTracking(timer: FakeTimer) : CommandHandler(timer) {
  var startStatus = Memory.TrackStatus.getDefaultInstance()
  var lastInfo = Memory.AllocationsInfo.getDefaultInstance()

  override fun handleCommand(command: Command, events: MutableList<Common.Event>) {
    val infoId = timer.currentTimeNs
    lastInfo = Memory.AllocationsInfo.newBuilder()
      .setStartTime(infoId)
      .setEndTime(Long.MAX_VALUE)
      .build()

    events.add(Common.Event.newBuilder().apply {
      groupId = infoId
      pid = command.pid
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING_STATUS
      timestamp = timer.currentTimeNs
      commandId = command.commandId
      memoryAllocTrackingStatus = Memory.MemoryAllocTrackingStatusData.newBuilder().apply {
        status = startStatus
      }.build()
    }.build())

    if (startStatus.status == Memory.TrackStatus.Status.SUCCESS) {
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
}
