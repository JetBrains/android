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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.profilers.FakeLegacyAllocationTracker
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingDeque
import java.util.function.BiFunction

class LegacyAllocationCommandHandlerTest {
  @Test
  fun testLegacyAllocationTrackingWorkflow() {
    val time1 = 5L
    val time2 = 10L

    val allocTracker = FakeLegacyAllocationTracker()
    val device = mock(IDevice::class.java)
    val eventQueue = LinkedBlockingDeque<Common.Event>()
    val byteCache = HashMap<String, ByteString>()
    val commandHandler = LegacyAllocationCommandHandler(device,
                                                        eventQueue,
                                                        byteCache,
                                                        Executor { it.run() },
                                                        BiFunction { _, _ -> allocTracker })

    val startTrackCommand = Commands.Command.newBuilder().apply {
      type = Commands.Command.CommandType.START_ALLOC_TRACKING
      commandId = 1
      startAllocTracking = Memory.StartAllocTracking.newBuilder().setRequestTime(time1).build()
    }.build()
    commandHandler.execute(startTrackCommand)

    assertThat(eventQueue).hasSize(2)
    val startStatusEvent = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING_STATUS
      commandId = 1
      timestamp = time1
      memoryAllocTrackingStatus = Memory.MemoryAllocTrackingStatusData.newBuilder().apply {
        status = Memory.TrackStatus.newBuilder().setStatus(Memory.TrackStatus.Status.SUCCESS).setStartTime(time1).build()
      }.build()
    }.build()
    val startTrackingEvent = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING
      groupId = time1
      timestamp = time1
      memoryAllocTracking = Memory.MemoryAllocTrackingData.newBuilder().apply {
        info = Memory.AllocationsInfo.newBuilder().setStartTime(time1).setEndTime(Long.MAX_VALUE).setLegacy(true).build()
      }.build()
    }.build()
    assertThat(eventQueue).containsExactly(startTrackingEvent, startStatusEvent)

    // Verify that query for bytes on at ongoing session does not return any byte contents
    assertThat(byteCache).isEmpty()

    eventQueue.clear()
    val stopTrackComand = Commands.Command.newBuilder().apply {
      type = Commands.Command.CommandType.STOP_ALLOC_TRACKING
      commandId = 2
      stopAllocTracking = Memory.StopAllocTracking.newBuilder().setRequestTime(time2).build()
    }.build()
    commandHandler.execute(stopTrackComand)

    assertThat(eventQueue).hasSize(2)
    val stopStatusEvent = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING_STATUS
      commandId = 2
      timestamp = time2
      memoryAllocTrackingStatus = Memory.MemoryAllocTrackingStatusData.newBuilder().apply {
        status = Memory.TrackStatus.newBuilder().setStatus(Memory.TrackStatus.Status.SUCCESS).setStartTime(time1).build()
      }.build()
    }.build()
    val stopTrackingEvent = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING
      groupId = time1
      timestamp = time2
      isEnded = true
      memoryAllocTracking = Memory.MemoryAllocTrackingData.newBuilder().apply {
        info = Memory.AllocationsInfo.newBuilder().setStartTime(time1).setEndTime(time2).setLegacy(true).setSuccess(true).build()
      }.build()
    }.build()
    assertThat(eventQueue).containsExactly(stopTrackingEvent, stopStatusEvent)

    // Verify that query for bytes on at ongoing session does not return any byte contents until the fake tracker returns
    assertThat(byteCache).isEmpty()

    // Mock completion of the parsing process and the resulting data.
    allocTracker.parsingWaitLatch.countDown()
    allocTracker.parsingDoneLatch.await()
    assertThat(byteCache.get(time1.toString())).isEqualTo(ByteString.copyFrom(FakeLegacyAllocationTracker.RAW_DATA))
  }

  @Test
  fun testLegacyAllocationTrackingWorkflowWithTaskBasedUxEnabled() {
    StudioFlags.PROFILER_TASK_BASED_UX.override(true)

    val time1 = 5L
    val time2 = 10L

    val allocTracker = FakeLegacyAllocationTracker()
    val device = mock(IDevice::class.java)
    val eventQueue = LinkedBlockingDeque<Common.Event>()
    val byteCache = HashMap<String, ByteString>()
    val commandHandler = LegacyAllocationCommandHandler(device,
                                                        eventQueue,
                                                        byteCache,
                                                        Executor { it.run() },
                                                        BiFunction { _, _ -> allocTracker })

    val startTrackCommand = Commands.Command.newBuilder().apply {
      type = Commands.Command.CommandType.START_ALLOC_TRACKING
      commandId = 1
      startAllocTracking = Memory.StartAllocTracking.newBuilder().setRequestTime(time1).build()
    }.build()
    commandHandler.execute(startTrackCommand)

    assertThat(eventQueue).hasSize(2)
    val expectedStartStatusEvent = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING_STATUS
      commandId = 1
      timestamp = time1
      memoryAllocTrackingStatus = Memory.MemoryAllocTrackingStatusData.newBuilder().apply {
        status = Memory.TrackStatus.newBuilder().setStatus(Memory.TrackStatus.Status.SUCCESS).setStartTime(time1).build()
      }.build()
    }.build()
    val expectedStartTrackingEvent = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING
      groupId = time1
      timestamp = time1
      memoryAllocTracking = Memory.MemoryAllocTrackingData.newBuilder().apply {
        info = Memory.AllocationsInfo.newBuilder().setStartTime(time1).setEndTime(Long.MAX_VALUE).setLegacy(true).build()
      }.build()
    }.build()
    assertThat(eventQueue).containsExactly(expectedStartStatusEvent, expectedStartTrackingEvent)

    // Verify that query for bytes on at ongoing session does not return any byte contents
    assertThat(byteCache).isEmpty()

    eventQueue.clear()
    val stopTrackComand = Commands.Command.newBuilder().apply {
      type = Commands.Command.CommandType.STOP_ALLOC_TRACKING
      commandId = 2
      stopAllocTracking = Memory.StopAllocTracking.newBuilder().setRequestTime(time2).build()
    }.build()
    commandHandler.execute(stopTrackComand)

    assertThat(eventQueue).hasSize(3)
    val stopStatusEvent = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING_STATUS
      commandId = 2
      timestamp = time2
      memoryAllocTrackingStatus = Memory.MemoryAllocTrackingStatusData.newBuilder().apply {
        status = Memory.TrackStatus.newBuilder().setStatus(Memory.TrackStatus.Status.SUCCESS).setStartTime(time1).build()
      }.build()
    }.build()
    val stopTrackingEvent = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.MEMORY_ALLOC_TRACKING
      groupId = time1
      timestamp = time2
      isEnded = true
      memoryAllocTracking = Memory.MemoryAllocTrackingData.newBuilder().apply {
        info = Memory.AllocationsInfo.newBuilder().setStartTime(time1).setEndTime(time2).setLegacy(true).setSuccess(true).build()
      }.build()
    }.build()
    val endSessionEvent = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.SESSION
      groupId = stopTrackComand.sessionId
      timestamp = 10
      pid = stopTrackComand.pid
      isEnded = true
    }.build()

    assertThat(eventQueue).containsExactly(stopTrackingEvent, stopStatusEvent, endSessionEvent)

    // Verify that query for bytes on at ongoing session does not return any byte contents until the fake tracker returns
    assertThat(byteCache).isEmpty()

    // Mock completion of the parsing process and the resulting data.
    allocTracker.parsingWaitLatch.countDown()
    allocTracker.parsingDoneLatch.await()
    assertThat(byteCache.get(time1.toString())).isEqualTo(ByteString.copyFrom(FakeLegacyAllocationTracker.RAW_DATA))
  }

  @Test
  fun testLegacyAllocationTrackingReturningNullData() {

    val time1 = 5L
    val time2 = 10L

    val allocTracker = FakeLegacyAllocationTracker()
    val device = mock(IDevice::class.java)
    val eventQueue = LinkedBlockingDeque<Common.Event>()
    val byteCache = HashMap<String, ByteString>()
    val commandHandler = LegacyAllocationCommandHandler(device,
                                                        eventQueue,
                                                        byteCache,
                                                        Executor { it.run() },
                                                        BiFunction { _, _ -> allocTracker })

    val startTrackCommand = Commands.Command.newBuilder().apply {
      type = Commands.Command.CommandType.START_ALLOC_TRACKING
      commandId = 1
      startAllocTracking = Memory.StartAllocTracking.newBuilder().setRequestTime(time1).build()
    }.build()
    commandHandler.execute(startTrackCommand)

    val stopTrackComand = Commands.Command.newBuilder().apply {
      type = Commands.Command.CommandType.STOP_ALLOC_TRACKING
      commandId = 2
      stopAllocTracking = Memory.StopAllocTracking.newBuilder().setRequestTime(time2).build()
    }.build()
    commandHandler.execute(stopTrackComand)

    // Mock completion of the parsing process and the resulting data.
    allocTracker.returnNullTrackingData = true
    allocTracker.parsingWaitLatch.countDown()
    allocTracker.parsingDoneLatch.await()
    assertThat(byteCache.get(time1.toString())).isEqualTo(ByteString.EMPTY)
  }

  @After
  fun cleanup() {
    StudioFlags.PROFILER_TASK_BASED_UX.clearOverride()
  }
}