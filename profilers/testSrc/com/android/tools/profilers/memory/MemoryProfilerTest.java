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
package com.android.tools.profilers.memory;

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_ID;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_DETACHED_RESPONSE;
import static org.junit.Assert.assertArrayEquals;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.idea.transport.faketransport.commands.MemoryAllocTracking;
import com.android.tools.perflib.heap.SnapshotBuilder;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.google.common.truth.Truth;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MemoryProfilerTest {
  public static final Memory.MemoryAllocSamplingData DEFAULT_MEMORY_ALLOCATION_SAMPLING_DATA =
    Memory.MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(10).build();

  private static final int FAKE_PID = 111;
  private static final Common.Session TEST_SESSION = Common.Session.newBuilder().setSessionId(1).setPid(FAKE_PID).build();
  private static final long DEVICE_STARTTIME_NS = 0;

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer, false);
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryProfilerTest", myTransportService);
  private StudioProfilers myStudioProfiler;

  @Before
  public void setUp() {
    FakeIdeProfilerServices ideProfilerServices = new FakeIdeProfilerServices();
    ideProfilerServices.enableEventsPipeline(true);
    myStudioProfiler = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), ideProfilerServices, myTimer);
  }

  @Test
  public void testStopMonitoringCallsStopTracking() {
    MemoryAllocTracking allocTrackingHandler =
      (MemoryAllocTracking)myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING);
    MemoryProfiler memoryProfiler = new MemoryProfiler(myStudioProfiler);

    // We stop any ongoing allocation tracking session before stopping the memory profiler.
    Truth.assertThat(allocTrackingHandler.getLastInfo()).isEqualTo(Memory.AllocationsInfo.getDefaultInstance());
    memoryProfiler.stopProfiling(TEST_SESSION);
    Truth.assertThat(allocTrackingHandler.getLastInfo()).isNotEqualTo(Memory.AllocationsInfo.getDefaultInstance());
  }

  @Test
  public void testLiveAllocationTrackingStoppedAndNotStartedOnAgentAttach() {
    setupODeviceAndProcess();
    // Verify start and stop allocation tracking commands are handled by the same handler.
    MemoryAllocTracking allocTrackingHandler =
      (MemoryAllocTracking)myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING);
    Truth.assertThat(allocTrackingHandler).isEqualTo(
      myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING));
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isFalse();
    Truth.assertThat(allocTrackingHandler.getLastInfo()).isEqualTo(Memory.AllocationsInfo.getDefaultInstance());

    // Advance the timer to select the device + process
    myTransportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    // We call stop tracking after agent is attached when the session starts, not starting tracking without explicit operations.
    Commands.Command firstStopCommand = allocTrackingHandler.getLastCommand();
    Truth.assertThat(firstStopCommand.getType()).isEqualTo(Commands.Command.CommandType.STOP_ALLOC_TRACKING);
    Memory.AllocationsInfo lastInfo;
    Truth.assertThat(getIsUsingLiveAllocation()).isFalse();
    lastInfo = allocTrackingHandler.getLastInfo();
    Truth.assertThat(lastInfo.getEndTime()).isEqualTo(1);
    Truth.assertThat(lastInfo.getSuccess()).isTrue();
  }

  @Test
  public void liveAllocationTrackingDidNotStartIfAgentIsNotAttached() {
    setupODeviceAndProcess();

    myTransportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();

    // Verify start and stop allocation tracking commands are handled by the same handler.
    MemoryAllocTracking allocTrackingHandler =
      (MemoryAllocTracking)myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING);
    Truth.assertThat(allocTrackingHandler).isEqualTo(
      myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING));
    // We call stop tracking after agent is attached when the session starts.
    Memory.AllocationsInfo lastInfo = allocTrackingHandler.getLastInfo();
    Truth.assertThat(lastInfo.getEndTime()).isEqualTo(1);
    Truth.assertThat(lastInfo.getSuccess()).isTrue();
    // Reset for testing when agent is not attached below.
    allocTrackingHandler.setLastInfo(Memory.AllocationsInfo.getDefaultInstance());

    myTransportService.addEventToStream(myStudioProfiler.getSession().getStreamId(), Common.Event.newBuilder()
      .setPid(myStudioProfiler.getSession().getPid())
      .setKind(Common.Event.Kind.AGENT)
      .setAgentData(DEFAULT_AGENT_DETACHED_RESPONSE)
      .build());

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStudioProfiler.changed(ProfilerAspect.AGENT);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isFalse();
    Truth.assertThat(allocTrackingHandler.getLastInfo()).isEqualTo(Memory.AllocationsInfo.getDefaultInstance());
  }

  @Test
  public void testGetNativeHeapSamplesForSession() {
    long nativeHeapTimestamp = 30L;
    Trace.TraceData nativeHeapInfo = Trace.TraceData.newBuilder().setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(
      Trace.TraceInfo.newBuilder().setFromTimestamp(nativeHeapTimestamp).setToTimestamp(
        nativeHeapTimestamp + 1))).build();
    Common.Event nativeHeapData =
      ProfilersTestData.generateMemoryTraceData(nativeHeapTimestamp, nativeHeapTimestamp + 1, nativeHeapInfo)
        .setPid(ProfilersTestData.SESSION_DATA.getPid()).build();
    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.getStreamId(), nativeHeapData);
    List<Trace.TraceInfo> samples = MemoryProfiler
      .getNativeHeapSamplesForSession(myStudioProfiler.getClient(), ProfilersTestData.SESSION_DATA,
                                      new Range(Long.MIN_VALUE, Long.MAX_VALUE));
    Truth.assertThat(samples).containsExactly(nativeHeapInfo.getTraceEnded().getTraceInfo());
  }

  @Test
  public void testSaveHeapDumpToFile() {
    long startTimeNs = 3;
    long endTimeNs = 8;
    HeapDumpInfo dumpInfo = HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    // Load in a simple Snapshot and verify the MemoryObject hierarchy:
    // - 1 holds reference to 2
    // - single root object in default heap
    SnapshotBuilder snapshotBuilder = new SnapshotBuilder(2, 0, 0)
      .addReferences(1, 2)
      .addRoot(1);
    byte[] buffer = snapshotBuilder.getByteBuffer();
    myTransportService.addFile(Long.toString(startTimeNs), ByteString.copyFrom(buffer));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    MemoryProfiler.saveHeapDumpToFile(myStudioProfiler.getClient(), ProfilersTestData.SESSION_DATA, dumpInfo, baos,
                                      myStudioProfiler.getIdeServices().getFeatureTracker());
    assertArrayEquals(buffer, baos.toByteArray());
  }

  @Test
  public void testSaveHeapProfdSampleToFile() {
    long startTimeNs = 3;
    Trace.TraceInfo data = Trace.TraceInfo.newBuilder().setFromTimestamp(startTimeNs).build();
    byte[] buffer = data.toByteArray();
    myTransportService.addFile(Long.toString(startTimeNs), ByteString.copyFrom(buffer));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    MemoryProfiler.saveHeapProfdSampleToFile(myStudioProfiler.getClient(), ProfilersTestData.SESSION_DATA, data, baos);
    assertArrayEquals(buffer, baos.toByteArray());
  }

  @Test
  public void testgetAllocationInfosForSession() {
    Common.Session session = myStudioProfiler.getSession();

    // Insert a completed info.
    Memory.AllocationsInfo info1 = Memory.AllocationsInfo.newBuilder()
      .setStartTime(1).setEndTime(2).setSuccess(true).setLegacy(true).build();
    myTransportService.addEventToStream(
      session.getStreamId(),
      ProfilersTestData.generateMemoryAllocationInfoData(1, session.getPid(), info1).build());

    List<Memory.AllocationsInfo> infos = MemoryProfiler.getAllocationInfosForSession(myStudioProfiler.getClient(),
                                                                                     session,
                                                                                     new Range(0, 10)
    );
    Truth.assertThat(infos).containsExactly(info1);

    // Insert a not yet completed info followed up by a generic end event.
    Memory.AllocationsInfo info2 = Memory.AllocationsInfo.newBuilder().setStartTime(5).setEndTime(Long.MAX_VALUE).setLegacy(true).build();
    myTransportService.addEventToStream(
      session.getStreamId(),
      Common.Event.newBuilder().setTimestamp(5).setGroupId(5).setKind(Common.Event.Kind.MEMORY_ALLOC_TRACKING)
        .setPid(session.getPid())
        .setMemoryAllocTracking(Memory.MemoryAllocTrackingData.newBuilder().setInfo(info2))
        .build());
    myTransportService.addEventToStream(
      session.getStreamId(),
      Common.Event.newBuilder().setTimestamp(10).setGroupId(5).setKind(Common.Event.Kind.MEMORY_ALLOC_TRACKING)
        .setPid(session.getPid()).setIsEnded(true).build());
    infos = MemoryProfiler.getAllocationInfosForSession(myStudioProfiler.getClient(),
                                                        session,
                                                        new Range(0, 10)
    );
    Truth.assertThat(infos).containsExactly(info1, info2.toBuilder().setEndTime(session.getEndTimestamp()).setSuccess(false).build());
  }

  private void setupODeviceAndProcess() {
    Common.Device device = Common.Device.newBuilder()
      .setDeviceId(FAKE_DEVICE_ID)
      .setSerial("FakeDevice")
      .setState(Common.Device.State.ONLINE)
      .setFeatureLevel(AndroidVersion.VersionCodes.O)
      .build();
    Common.Process process = Common.Process.newBuilder()
      .setPid(20)
      .setDeviceId(FAKE_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setName("FakeProcess")
      .setStartTimestampNs(DEVICE_STARTTIME_NS)
      .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
      .build();
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStudioProfiler.setProcess(device, process);
  }

  private boolean getIsUsingLiveAllocation() {
    Common.Session session = myStudioProfiler.getSessionsManager().getSelectedSession();
    myTransportService.addEventToStream(myStudioProfiler.getSession().getStreamId(), Common.Event.newBuilder()
      .setPid(myStudioProfiler.getSession().getPid())
      .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
      .setMemoryAllocSampling(DEFAULT_MEMORY_ALLOCATION_SAMPLING_DATA)
      .build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    return MemoryProfiler.isUsingLiveAllocation(myStudioProfiler, session);
  }
}