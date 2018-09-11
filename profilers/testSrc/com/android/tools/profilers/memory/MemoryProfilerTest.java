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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.perflib.heap.SnapshotBuilder;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRate;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRateEvent;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profilers.*;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.network.FakeNetworkService;
import com.google.common.truth.Truth;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.FakeProfilerService.FAKE_DEVICE_ID;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_DETACHED_RESPONSE;
import static org.junit.Assert.assertArrayEquals;

public class MemoryProfilerTest {
  private static final int FAKE_PID = 111;
  private static final Common.Session TEST_SESSION = Common.Session.newBuilder().setSessionId(1).setPid(FAKE_PID).build();
  private static final long DEVICE_STARTTIME_NS = 0;

  private final FakeProfilerService myProfilerService = new FakeProfilerService(false);
  private final FakeMemoryService myMemoryService = new FakeMemoryService();
  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MemoryProfilerTest", myMemoryService, myProfilerService, new FakeEventService(), new FakeCpuService(),
                        FakeNetworkService.newBuilder().build());

  private StudioProfilers myStudioProfiler;
  private FakeIdeProfilerServices myIdeProfilerServices;
  private FakeTimer myTimer;

  @Before
  public void setUp() {
    myTimer = new FakeTimer();
    myIdeProfilerServices = new FakeIdeProfilerServices();
    myStudioProfiler = new StudioProfilers(myGrpcChannel.getClient(), myIdeProfilerServices, myTimer);
  }

  @Test
  public void testStartMonitoring() {
    MemoryProfiler memoryProfiler = new MemoryProfiler(myStudioProfiler);
    memoryProfiler.startProfiling(TEST_SESSION);
    Truth.assertThat(myMemoryService.getProcessId()).isEqualTo(FAKE_PID);
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);
  }

  @Test
  public void testStopMonitoring() {
    MemoryProfiler memoryProfiler = new MemoryProfiler(myStudioProfiler);
    memoryProfiler.stopProfiling(TEST_SESSION);
    Truth.assertThat(myMemoryService.getProcessId()).isEqualTo(FAKE_PID);
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);
  }

  @Test
  public void testLiveAllocationTrackingOnAgentAttach() {
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    setupODeviceAndProcess();

    Truth.assertThat(myStudioProfiler.isAgentAttached()).isFalse();
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);

    // Advance the timer to select the device + process
    myProfilerService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(2);
  }

  @Test
  public void liveAllocationTrackingDidNotStartIfAgentIsNotAttached() {
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    setupODeviceAndProcess();

    myProfilerService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(2);

    myMemoryService.resetTrackAllocationCount();
    myProfilerService.setAgentStatus(DEFAULT_AGENT_DETACHED_RESPONSE);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStudioProfiler.changed(ProfilerAspect.AGENT);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isFalse();
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);
  }

  @Test
  public void testStopTrackingOnProfilerStop() {
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    myProfilerService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE);
    setupODeviceAndProcess();

    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(2);

    MemoryData memoryData = MemoryData.newBuilder()
      .setEndTimestamp(1)
      .addAllocSamplingRateEvents(
        AllocationSamplingRateEvent.newBuilder()
          .setTimestamp(TimeUnit.MICROSECONDS.toNanos(0))
          .setSamplingRate(AllocationSamplingRate.newBuilder().setSamplingNumInterval(1)))
      .build();
    myMemoryService.setMemoryData(memoryData);
    myStudioProfiler.stop();
    // Agent is still determined to be attached for the stopped session.
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    // Allocation tracking should have been called to stop.
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(3);
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
    myMemoryService.setExplicitSnapshotBuffer(buffer);
    myMemoryService.setExplicitDumpDataStatus(com.android.tools.profiler.proto.MemoryProfiler.DumpDataResponse.Status.SUCCESS);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    MemoryProfiler.saveHeapDumpToFile(myStudioProfiler.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, dumpInfo, baos,
                                      myStudioProfiler.getIdeServices().getFeatureTracker());
    assertArrayEquals(buffer, baos.toByteArray());
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
      .build();
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStudioProfiler.setDevice(device);
    myStudioProfiler.setProcess(process);
  }
}