/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.memory

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.TransportServiceUtils
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_ID
import com.android.tools.idea.transport.faketransport.commands.MemoryAllocTracking
import com.android.tools.perflib.heap.SnapshotBuilder
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.LiveStage
import com.android.tools.profilers.ProfilerAspect
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE
import com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_UNATTACHABLE_RESPONSE
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.LiveTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.JavaKotlinAllocationsTaskHandler
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class MemoryProfilerTest {

  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule val myGrpcChannel = FakeGrpcChannel("MemoryProfilerTest", myTransportService)

  private lateinit var myStudioProfiler: StudioProfilers
  private lateinit var myIdeProfilerServices: FakeIdeProfilerServices

  @Before
  fun setUp() {
    myIdeProfilerServices = FakeIdeProfilerServices()
    myStudioProfiler = StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeProfilerServices, myTimer)
  }

  @Test
  fun testStopMonitoringCallsStopTracking() {
    val allocTrackingHandler =
      myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING) as MemoryAllocTracking
    val memoryProfiler = MemoryProfiler(myStudioProfiler)

    // We stop any ongoing allocation tracking session before stopping the memory profiler.
    assertThat(allocTrackingHandler.lastInfo).isEqualTo(Memory.AllocationsInfo.getDefaultInstance())
    memoryProfiler.stopProfiling(TEST_SESSION)
    assertThat(allocTrackingHandler.lastInfo).isNotEqualTo(Memory.AllocationsInfo.getDefaultInstance())
  }

  @Test
  fun taskBasedUxNotLiveAllocationTracking() {
    myIdeProfilerServices.enableTaskBasedUx(true)
    setupODeviceAndProcessForTaskBasedUx(Common.ProfilerTaskType.LIVE_VIEW, true)
    myStudioProfiler.addTaskHandler(ProfilerTaskType.LIVE_VIEW, LiveTaskHandler(myStudioProfiler.sessionsManager))

    val allocTrackingHandler =
      myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING) as MemoryAllocTracking
    // Wait for the session starting with agent
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(myStudioProfiler.isAgentAttached).isTrue()

    var lastCommand = allocTrackingHandler.lastCommand
    // Last command is Stop Alloc Tracking
    assertThat(lastCommand.type).isEqualTo(Commands.Command.CommandType.STOP_ALLOC_TRACKING)

    val liveStage = LiveStage(myStudioProfiler)
    // Set stage as liveStage
    myStudioProfiler.stage = liveStage
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    lastCommand = allocTrackingHandler.lastCommand
    // Last command is still Stop Alloc tracking
    assertThat(lastCommand.type).isEqualTo(Commands.Command.CommandType.STOP_ALLOC_TRACKING)
  }

  @Test
  fun nonTaskBasedUxLiveAllocationTracking() {
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking).trackStatus =
      Memory.TrackStatus.newBuilder().setStatus(Memory.TrackStatus.Status.SUCCESS).build()
    myIdeProfilerServices.enableTaskBasedUx(false)
    setupODeviceAndProcess()

    val allocTrackingHandler =
      myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking
    // Wait for the session starting with agent
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(myStudioProfiler.isAgentAttached).isTrue()

    var lastCommand = allocTrackingHandler.lastCommand
    // Last command is Stop Alloc Tracking
    assertThat(lastCommand.type).isEqualTo(Commands.Command.CommandType.STOP_ALLOC_TRACKING)

    val allocationStage = spy(AllocationStage.makeLiveStage(myStudioProfiler, FakeCaptureObjectLoader()))
    allocationStage.liveAllocationSamplingMode = BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED
    doReturn(false).whenever(allocationStage).isAgentAttached // make delayed allocation tracking
    // Set stage as AllocationStage
    myStudioProfiler.stage = allocationStage
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS) // wait for the start allocation tracking
    lastCommand = allocTrackingHandler.lastCommand
    // Check if last ran command is start allocation tracking, there is no delay since it's not task based ux
    assertThat(lastCommand.type).isEqualTo(Commands.Command.CommandType.START_ALLOC_TRACKING)
  }

  /** After the agent is already attached by prior tasks, the user starts a J/K Allocation task */
  @Test
  fun taskBasedUxLiveAllocationTrackingNoDelayedStart() {
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking).trackStatus =
      Memory.TrackStatus.newBuilder().setStatus(Memory.TrackStatus.Status.SUCCESS).build()
    myIdeProfilerServices.enableTaskBasedUx(true)
    myStudioProfiler.addTaskHandler(
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
      JavaKotlinAllocationsTaskHandler(myStudioProfiler.sessionsManager),
    )

    val allocTrackingHandler =
      myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking
    setupODeviceAndProcessForTaskBasedUx(Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, true)
    // After Set Process, agent attached will be false.
    // That shouldn't have triggered any allocation tracking command
    var lastCommand = allocTrackingHandler.lastCommand
    // No Stop Allocation tracking or start allocation tracking yet.
    assertThat(lastCommand.type).isEqualTo(Commands.Command.CommandType.UNSPECIFIED)
    // Set Agent as not attached yet
    assertThat(myStudioProfiler.isAgentAttached).isFalse()

    val allocationStage = spy(AllocationStage.makeLiveStage(myStudioProfiler, FakeCaptureObjectLoader()))
    allocationStage.liveAllocationSamplingMode = BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED
    // Agent already attached
    doReturn(true).whenever(allocationStage).isAgentAttached
    // Set stage as AllocationStage
    myStudioProfiler.stage = allocationStage
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    lastCommand = allocTrackingHandler.lastCommand
    // Check if the last ran command is still start allocation tracking
    assertThat(lastCommand.type).isEqualTo(Commands.Command.CommandType.START_ALLOC_TRACKING)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Allocation tracking started
    assertThat(allocationStage.hasStartedTracking).isTrue()
    assertThat(allocationStage.hasEndedTracking).isFalse()
  }

  /** When J/K Allocation task is the first task (Agent not being attached) */
  @Test
  fun taskBasedUxLiveAllocationTrackingDelayedStart() {
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking).trackStatus =
      Memory.TrackStatus.newBuilder().setStatus(Memory.TrackStatus.Status.SUCCESS).build()
    myIdeProfilerServices.enableTaskBasedUx(true)
    myStudioProfiler.addTaskHandler(
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
      JavaKotlinAllocationsTaskHandler(myStudioProfiler.sessionsManager),
    )

    val allocTrackingHandler =
      myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking
    setupODeviceAndProcessForTaskBasedUx(Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, true)
    // After Set Process, agent attached will be false.
    // That shouldn't have triggered any allocation tracking command
    var lastCommand = allocTrackingHandler.lastCommand
    // No Stop Allocation tracking or start allocation tracking yet.
    assertThat(lastCommand.type).isEqualTo(Commands.Command.CommandType.UNSPECIFIED)
    // Set Agent as not attached yet
    assertThat(myStudioProfiler.isAgentAttached).isFalse()

    val allocationStage = spy(AllocationStage.makeLiveStage(myStudioProfiler, FakeCaptureObjectLoader()))
    allocationStage.liveAllocationSamplingMode = BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED
    // Delay allocation tracking
    doReturn(false).whenever(allocationStage).isAgentAttached
    // Set stage as AllocationStage
    myStudioProfiler.stage = allocationStage
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    lastCommand = allocTrackingHandler.lastCommand
    // Check if the last ran command is still no allocation tracking
    assertThat(lastCommand.type).isEqualTo(Commands.Command.CommandType.UNSPECIFIED)

    doReturn(true).whenever(allocationStage).isAgentAttached
    // If the agent changed again, it should start the tracking
    myStudioProfiler.changed(ProfilerAspect.AGENT)
    // Wait for the agent status change
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    lastCommand = allocTrackingHandler.lastCommand
    // Last command is now start Alloc Tracking
    assertThat(lastCommand.type).isEqualTo(Commands.Command.CommandType.START_ALLOC_TRACKING)
    // Allocation tracking started
    assertThat(allocationStage.hasStartedTracking).isTrue()
    assertThat(allocationStage.hasEndedTracking).isFalse()
  }

  @Test
  fun testAllocationTrackingWhenAgentUnAttached() {
    myIdeProfilerServices.enableTaskBasedUx(false)

    val session =
      Common.Session.newBuilder().setSessionId(2).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS).setEndTimestamp(Long.MAX_VALUE).build()
    // Setting to Long.Max_Value so the session is still active
    val sessionOMetadata =
      Common.SessionMetaData.newBuilder()
        .setSessionId(2)
        .setType(Common.SessionMetaData.SessionType.FULL)
        .setJvmtiEnabled(true)
        .setStartTimestampEpochMs(1)
        .build()
    myTransportService.addSession(session, sessionOMetadata)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Agent status is unspecified
    assertThat(myStudioProfiler.agentData.status).isEqualTo(Common.AgentData.Status.UNSPECIFIED)

    myTransportService.addEventToStream(
      session.streamId,
      Common.Event.newBuilder()
        .setKind(Common.Event.Kind.AGENT)
        .setPid(session.pid)
        .setAgentData(DEFAULT_AGENT_UNATTACHABLE_RESPONSE)
        .build(),
    )
    val allocationStage = spy(AllocationStage.makeLiveStage(myStudioProfiler, FakeCaptureObjectLoader()))
    myStudioProfiler.sessionsManager.setSession(session)
    myStudioProfiler.stage = allocationStage
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(myStudioProfiler.agentData.status).isEqualTo(Common.AgentData.Status.UNATTACHABLE)
    // If Stage is AllocationStage and agent status is un-attachable, it will mark as AgentError
    assertThat(allocationStage.hasAgentError).isTrue()
    assertThat(allocationStage.hasEndedTracking).isTrue()
  }

  @Test
  fun testLiveAllocationTrackingStoppedAndNotStartedOnAgentAttach() {
    myIdeProfilerServices.enableTaskBasedUx(false)

    setupODeviceAndProcess()
    // Verify start and stop allocation tracking commands are handled by the same handler.
    val allocTrackingHandler =
      myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking
    assertThat(allocTrackingHandler).isEqualTo(myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING))
    assertThat(myStudioProfiler.isAgentAttached).isFalse()
    assertThat(allocTrackingHandler.lastInfo).isEqualTo(Memory.AllocationsInfo.getDefaultInstance())

    // Advance the timer to select the device + process
    myTransportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(myStudioProfiler.isAgentAttached).isTrue()
    // We call stop tracking after agent is attached when the session starts, not starting tracking without explicit operations.
    val firstStopCommand = allocTrackingHandler.lastCommand
    assertThat(firstStopCommand.type).isEqualTo(Commands.Command.CommandType.STOP_ALLOC_TRACKING)
    assertThat(isUsingLiveAllocation()).isFalse()
    val lastInfo = allocTrackingHandler.lastInfo
    assertThat(lastInfo.endTime).isEqualTo(1)
    assertThat(lastInfo.success).isTrue()
  }

  @Test
  fun liveAllocationTrackingDidNotStartIfAgentIsNotAttached() {
    myIdeProfilerServices.enableTaskBasedUx(false)

    setupODeviceAndProcess()

    myTransportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(myStudioProfiler.isAgentAttached).isTrue()

    // Verify start and stop allocation tracking commands are handled by the same handler.
    val allocTrackingHandler =
      myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking
    assertThat(allocTrackingHandler).isEqualTo(myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING))
    // We call stop tracking after agent is attached when the session starts.
    val lastInfo = allocTrackingHandler.lastInfo
    assertThat(lastInfo.endTime).isEqualTo(1)
    assertThat(lastInfo.success).isTrue()
    // Reset for testing when agent is not attached below.
    allocTrackingHandler.lastInfo = Memory.AllocationsInfo.getDefaultInstance()

    myTransportService.addEventToStream(
      myStudioProfiler.session.streamId,
      Common.Event.newBuilder()
        .setPid(myStudioProfiler.session.pid)
        .setKind(Common.Event.Kind.AGENT)
        .setAgentData(DEFAULT_AGENT_UNATTACHABLE_RESPONSE)
        .build(),
    )

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myStudioProfiler.changed(ProfilerAspect.AGENT)
    assertThat(myStudioProfiler.isAgentAttached).isFalse()
    assertThat(allocTrackingHandler.lastInfo).isEqualTo(Memory.AllocationsInfo.getDefaultInstance())
  }

  @Test
  fun testGetNativeHeapSamplesForSession() {
    val nativeHeapTimestamp = 30L
    val nativeHeapInfo =
      Trace.TraceData.newBuilder()
        .setTraceEnded(
          Trace.TraceData.TraceEnded.newBuilder()
            .setTraceInfo(Trace.TraceInfo.newBuilder().setFromTimestamp(nativeHeapTimestamp).setToTimestamp(nativeHeapTimestamp + 1))
        )
        .build()
    val nativeHeapData =
      ProfilersTestData.generateMemoryTraceData(nativeHeapTimestamp, nativeHeapTimestamp + 1, nativeHeapInfo)
        .setPid(ProfilersTestData.SESSION_DATA.pid)
        .build()
    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId, nativeHeapData)
    val samples =
      MemoryProfiler.getNativeHeapSamplesForSession(
        myStudioProfiler.client,
        ProfilersTestData.SESSION_DATA,
        Range(Long.MIN_VALUE.toDouble(), Long.MAX_VALUE.toDouble()),
      )
    assertThat(samples).containsExactly(nativeHeapInfo.traceEnded.traceInfo)
  }

  @Test
  fun testSaveHeapDumpToFile() {
    val startTimeNs = 3L
    val endTimeNs = 8L
    val dumpInfo = HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build()
    // Load in a simple Snapshot and verify the MemoryObject hierarchy:
    // - 1 holds reference to 2
    // - single root object in default heap
    val snapshotBuilder = SnapshotBuilder(2, 0, 0).addReferences(1, 2).addRoot(1)
    val buffer = snapshotBuilder.byteBuffer
    val file = TransportServiceUtils.createTempFile("temp_heap", ".hprof", ByteString.copyFrom(buffer))
    myTransportService.addFile(startTimeNs.toString(), file.absolutePath)
    val baos = ByteArrayOutputStream()
    MemoryProfiler.saveHeapDumpToFile(
      myStudioProfiler.client,
      ProfilersTestData.SESSION_DATA,
      dumpInfo,
      baos,
      myStudioProfiler.ideServices.featureTracker,
    )
    assertArrayEquals(buffer, baos.toByteArray())
  }

  @Test
  fun testSaveHeapProfdSampleToFile() {
    val startTimeNs = 3L
    val data = Trace.TraceInfo.newBuilder().setFromTimestamp(startTimeNs).build()
    val buffer = data.toByteArray()
    val file = TransportServiceUtils.createTempFile("temp_heap_prof", ".trace", ByteString.copyFrom(buffer))
    myTransportService.addFile(startTimeNs.toString(), file.absolutePath)
    val baos = ByteArrayOutputStream()
    MemoryProfiler.saveHeapProfdSampleToFile(myStudioProfiler.client, ProfilersTestData.SESSION_DATA, data, baos)
    assertArrayEquals(buffer, baos.toByteArray())
  }

  @Test
  fun testGetAllocationInfosForSession() {
    val session = myStudioProfiler.session

    // Insert a completed info.
    val info1 = Memory.AllocationsInfo.newBuilder().setStartTime(1).setEndTime(2).setSuccess(true).setLegacy(true).build()
    myTransportService.addEventToStream(
      session.streamId,
      ProfilersTestData.generateMemoryAllocationInfoData(1, session.pid, info1).build(),
    )

    var infos =
      MemoryProfiler.getAllocationInfosForSession(
        myStudioProfiler.client,
        session,
        Range(0.0, 10.0),
      )
    assertThat(infos).containsExactly(info1)

    // Insert a not yet completed info followed up by a generic end event.
    val info2 = Memory.AllocationsInfo.newBuilder().setStartTime(5).setEndTime(Long.MAX_VALUE).setLegacy(true).build()
    myTransportService.addEventToStream(
      session.streamId,
      Common.Event.newBuilder()
        .setTimestamp(5)
        .setGroupId(5)
        .setKind(Common.Event.Kind.MEMORY_ALLOC_TRACKING)
        .setPid(session.pid)
        .setMemoryAllocTracking(Memory.MemoryAllocTrackingData.newBuilder().setInfo(info2))
        .build(),
    )
    myTransportService.addEventToStream(
      session.streamId,
      Common.Event.newBuilder()
        .setTimestamp(10)
        .setGroupId(5)
        .setKind(Common.Event.Kind.MEMORY_ALLOC_TRACKING)
        .setPid(session.pid)
        .setIsEnded(true)
        .build(),
    )
    infos =
      MemoryProfiler.getAllocationInfosForSession(
        myStudioProfiler.client,
        session,
        Range(0.0, 10.0),
      )
    assertThat(infos).containsExactly(info1, info2.toBuilder().setEndTime(session.endTimestamp).setSuccess(false).build())
  }

  private fun setupODeviceAndProcess() {
    val device =
      Common.Device.newBuilder()
        .setDeviceId(FAKE_DEVICE_ID)
        .setSerial("FakeDevice")
        .setState(Common.Device.State.ONLINE)
        .setFeatureLevel(AndroidVersion.VersionCodes.O)
        .build()
    val process =
      Common.Process.newBuilder()
        .setPid(20)
        .setDeviceId(FAKE_DEVICE_ID)
        .setState(Common.Process.State.ALIVE)
        .setName("FakeProcess")
        .setStartTimestampNs(DEVICE_STARTTIME_NS)
        .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
        .build()
    myTransportService.addDevice(device)
    myTransportService.addProcess(device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myStudioProfiler.setProcess(device, process)
  }

  private fun setupODeviceAndProcessForTaskBasedUx(taskType: Common.ProfilerTaskType, isStartupTask: Boolean) {
    val device =
      Common.Device.newBuilder()
        .setDeviceId(FAKE_DEVICE_ID)
        .setSerial("FakeDevice")
        .setState(Common.Device.State.ONLINE)
        .setFeatureLevel(AndroidVersion.VersionCodes.O)
        .build()
    val process =
      Common.Process.newBuilder()
        .setPid(20)
        .setDeviceId(FAKE_DEVICE_ID)
        .setState(Common.Process.State.ALIVE)
        .setName("FakeProcess")
        .setStartTimestampNs(DEVICE_STARTTIME_NS)
        .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
        .build()
    myTransportService.addDevice(device)
    myTransportService.addProcess(device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myStudioProfiler.setProcess(device, process, taskType, isStartupTask)
  }

  private fun isUsingLiveAllocation(): Boolean {
    val session = myStudioProfiler.sessionsManager.selectedSession
    myTransportService.addEventToStream(
      myStudioProfiler.session.streamId,
      Common.Event.newBuilder()
        .setPid(myStudioProfiler.session.pid)
        .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
        .setMemoryAllocSampling(DEFAULT_MEMORY_ALLOCATION_SAMPLING_DATA)
        .build(),
    )
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    return MemoryProfiler.isUsingLiveAllocation(myStudioProfiler, session)
  }

  companion object {
    @JvmField
    val DEFAULT_MEMORY_ALLOCATION_SAMPLING_DATA: Memory.MemoryAllocSamplingData =
      Memory.MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(10).build()

    private const val FAKE_PID = 111
    private val TEST_SESSION = Common.Session.newBuilder().setSessionId(1).setPid(FAKE_PID).build()
    private const val DEVICE_STARTTIME_NS = 0L
  }
}
