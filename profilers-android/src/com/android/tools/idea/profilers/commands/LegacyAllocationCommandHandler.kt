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
package com.android.tools.idea.profilers.commands

import com.android.ddmlib.IDevice
import com.android.tools.idea.profilers.LegacyAllocationTracker
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Transport
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.util.concurrent.BlockingDeque
import java.util.concurrent.Executor
import java.util.function.BiFunction
import java.util.function.Consumer

class LegacyAllocationCommandHandler(val device: IDevice,
                                     val eventQueue: BlockingDeque<Common.Event>,
                                     val byteCache: MutableMap<String, ByteString>,
                                     val fetchExecutor: Executor,
                                     val legacyTrackerSupplier: BiFunction<IDevice, Int, LegacyAllocationTracker>)
  : TransportProxy.ProxyCommandHandler {

  // Per-process cache of LegacyAllocationTracker and AllocationsInfo (keyed by pid)
  private val myLegacyTrackers = Int2ObjectOpenHashMap<LegacyAllocationTracker>()
  private val myInProgressTrackingInfo = Int2ObjectOpenHashMap<Memory.AllocationsInfo>()

  override fun execute(command: Commands.Command): Transport.ExecuteResponse {
    when (command.type) {
      Commands.Command.CommandType.START_ALLOC_TRACKING -> enableAllocations(command)
      Commands.Command.CommandType.STOP_ALLOC_TRACKING -> disableAllocations(command)
      else -> {}
    }

    return Transport.ExecuteResponse.getDefaultInstance()
  }

  private fun enableAllocations(command: Commands.Command) {
    val requestTime = command.startAllocTracking.requestTime
    var statusBuilder = Memory.TrackStatus.newBuilder()
    if (myInProgressTrackingInfo.containsKey(command.pid)) {
      statusBuilder.setStatus(Memory.TrackStatus.Status.IN_PROGRESS)
    }
    else {
      if (!myLegacyTrackers.containsKey(command.pid)) {
        myLegacyTrackers.put(command.pid, legacyTrackerSupplier.apply(device, command.pid))
      }
      val tracker = myLegacyTrackers.get(command.pid)
      val success = tracker.trackAllocations(true, null, null)
      if (success) {
        val newInfo = Memory.AllocationsInfo.newBuilder()
          .setStartTime(requestTime)
          .setEndTime(java.lang.Long.MAX_VALUE)
          .setLegacy(true)
          .build()
        myInProgressTrackingInfo.put(command.pid, newInfo)
        // Sends the MEMORY_ALLOC_TRACKING event
        val allocEvent = Common.Event.newBuilder().apply {
          pid = command.pid
          kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING
          groupId = requestTime
          timestamp = requestTime // Not exactly the current time, but it's close enough and saves a query to get the actual device time.
          memoryAllocTracking = Memory.MemoryAllocTrackingData.newBuilder().apply {
            info = newInfo
          }.build()
        }.build()
        eventQueue.offer(allocEvent)

        statusBuilder.setStartTime(requestTime)
        statusBuilder.setStatus(Memory.TrackStatus.Status.SUCCESS)
      }
      else {
        statusBuilder.setStatus(Memory.TrackStatus.Status.FAILURE_UNKNOWN)
      }
    }

    // Sends the MEMORY_ALLOC_TRACKING_STATUS event
    val statusEvent = Common.Event.newBuilder().apply {
      pid = command.pid
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING_STATUS
      commandId = command.commandId
      timestamp = requestTime // Not exactly the current time, but it's close enough and saves a query to get the actual device time.
      memoryAllocTrackingStatus = Memory.MemoryAllocTrackingStatusData.newBuilder().apply {
        status = statusBuilder.build()
      }.build()
    }.build()
    eventQueue.offer(statusEvent)
  }

  private fun disableAllocations(command: Commands.Command) {
    val requestTime = command.stopAllocTracking.requestTime
    var statusBuilder = Memory.TrackStatus.newBuilder()
    if (!myInProgressTrackingInfo.containsKey(command.pid)) {
      statusBuilder.setStatus(Memory.TrackStatus.Status.NOT_ENABLED)
    }
    else {
      assert(myLegacyTrackers.containsKey(command.pid))
      val tracker = myLegacyTrackers.get(command.pid)
      val lastInfo = myInProgressTrackingInfo.get(command.pid)
      val success = tracker.trackAllocations(false, fetchExecutor,
                                             Consumer { bytes ->
                                               byteCache.put(lastInfo.startTime.toString(),
                                                             if (bytes == null) ByteString.EMPTY
                                                             else ByteString.copyFrom(bytes))
                                             })
      val lastInfoBuilder = lastInfo.toBuilder()
      lastInfoBuilder.endTime = requestTime
      lastInfoBuilder.success = success
      // Sends the MEMORY_ALLOC_TRACKING event
      val allocEvent = Common.Event.newBuilder().apply {
        pid = command.pid
        kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING
        groupId = lastInfoBuilder.startTime
        isEnded = true
        timestamp = requestTime // Not exactly the current time, but it's close enough and saves a query to get the actual device time.
        memoryAllocTracking = Memory.MemoryAllocTrackingData.newBuilder().apply {
          info = lastInfoBuilder.build()
        }.build()
      }.build()
      eventQueue.offer(allocEvent)

      addSessionEndedEvent(eventQueue, requestTime, command.pid, command.sessionId)

      if (success) {
        statusBuilder.setStatus(Memory.TrackStatus.Status.SUCCESS).setStartTime(lastInfo.startTime)
      }
      else {
        statusBuilder.setStatus(Memory.TrackStatus.Status.FAILURE_UNKNOWN)
      }

      myInProgressTrackingInfo.remove(command.pid)
    }

    // Sends the MEMORY_ALLOC_TRACKING_STATUS event
    val statusEvent = Common.Event.newBuilder().apply {
      pid = command.pid
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING_STATUS
      commandId = command.commandId
      timestamp = requestTime // Not exactly the current time, but it's close enough and saves a query to get the actual device time.
      memoryAllocTrackingStatus = Memory.MemoryAllocTrackingStatusData.newBuilder().apply {
        status = statusBuilder.build()
      }.build()
    }.build()
    eventQueue.offer(statusEvent)
  }
}