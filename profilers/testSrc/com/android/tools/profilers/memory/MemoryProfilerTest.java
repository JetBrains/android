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
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.network.FakeNetworkService;
import com.google.common.truth.Truth;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MemoryProfilerTest {
  private static final int FAKE_PID = 111;

  private final FakeProfilerService myProfilerService = new FakeProfilerService(false);
  private final FakeMemoryService myMemoryService = new FakeMemoryService();
  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MemoryProfilerTest", myMemoryService, myProfilerService, new FakeEventService(), new FakeCpuService(),
                        FakeNetworkService.newBuilder().build());

  private Profiler.Process FAKE_PROCESS = Profiler.Process.newBuilder().setPid(FAKE_PID).setName("FakeProcess").build();
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
    memoryProfiler.startProfiling(ProfilersTestData.SESSION_DATA, FAKE_PROCESS);
    Truth.assertThat(myMemoryService.getProcessId()).isEqualTo(FAKE_PID);
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);
    Truth.assertThat(myMemoryService.getSuspendAllocationCount()).isEqualTo(0);
    Truth.assertThat(myMemoryService.getResumeAllocationCount()).isEqualTo(0);
  }

  @Test
  public void testStopMonitoring() {
    MemoryProfiler memoryProfiler = new MemoryProfiler(myStudioProfiler);
    memoryProfiler.stopProfiling(ProfilersTestData.SESSION_DATA, FAKE_PROCESS);
    Truth.assertThat(myMemoryService.getProcessId()).isEqualTo(FAKE_PID);
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);
    Truth.assertThat(myMemoryService.getSuspendAllocationCount()).isEqualTo(0);
    Truth.assertThat(myMemoryService.getResumeAllocationCount()).isEqualTo(0);
  }

  @Test
  public void testLiveAllocationTrackingOnAgentAttach() {
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    setupODeviceAndProcess();

    // Advance the timer to select the device + process
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isFalse();
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    // Expecting a stop/start trackAllocations request pair.
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(2);
  }

  @Test
  public void testAllocationTrackingNotStartedIfInfoExists() {
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    setupODeviceAndProcess();

    // AllocationsInfo should exist for tracking to not restart.
    myMemoryService.setMemoryData(com.android.tools.profiler.proto.MemoryProfiler.MemoryData.newBuilder().addAllocationsInfo(
      com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo.newBuilder()
        .setStatus(com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo.Status.IN_PROGRESS)
        .setStartTime(Long.MIN_VALUE)
        .setEndTime(Long.MAX_VALUE)
        .setLegacy(false).build()
    ).build());

    // Advance the timer to select the device + process
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Truth.assertThat(myStudioProfiler.isAgentAttached()).isFalse();
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);
  }

  @Test
  public void testSuspendAndResumeLiveAllocationTracking() {
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    setupODeviceAndProcess();

    // Advance the timer to select the device + process
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    Truth.assertThat(myMemoryService.getSuspendAllocationCount()).isEqualTo(0);
    Truth.assertThat(myMemoryService.getResumeAllocationCount()).isEqualTo(1);

    // Switch to a different process. We should expect a suspend + resume pair.
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(21)
      .setState(Profiler.Process.State.ALIVE)
      .setName("PreferredFakeProcess")
      .build();
    myStudioProfiler.setPreferredProcessName("PreferredFakeProcess");
    myProfilerService.addProcess(myStudioProfiler.getSession(), process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    Truth.assertThat(myMemoryService.getSuspendAllocationCount()).isEqualTo(1);
    Truth.assertThat(myMemoryService.getResumeAllocationCount()).isEqualTo(2);
  }

  @Test
  public void testSuspendOnProfilerStop() {
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    setupODeviceAndProcess();

    // Advance the timer to select the device + process
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    Truth.assertThat(myMemoryService.getSuspendAllocationCount()).isEqualTo(0);
    Truth.assertThat(myMemoryService.getResumeAllocationCount()).isEqualTo(1);

    myStudioProfiler.stop();
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isFalse();
    Truth.assertThat(myMemoryService.getSuspendAllocationCount()).isEqualTo(1);
    Truth.assertThat(myMemoryService.getResumeAllocationCount()).isEqualTo(1);
  }

  private void setupODeviceAndProcess() {
    Profiler.Device device = Profiler.Device.newBuilder()
      .setSerial("FakeDevice")
      .setState(Profiler.Device.State.ONLINE)
      .setFeatureLevel(AndroidVersion.VersionCodes.O)
      .build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    myProfilerService.addDevice(device);
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myProfilerService.addProcess(session, process);
  }
}