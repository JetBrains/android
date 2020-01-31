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
package com.android.tools.profilers;

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS;
import static com.android.tools.profilers.StudioProfilers.AGENT_STATUS_MAX_RETRY_COUNT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.idea.transport.faketransport.FakeGrpcServer;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.AgentData;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.customevent.CustomEventProfilerStage;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class StudioProfilersTest {
  @Parameterized.Parameters
  public static Collection<Boolean> useNewEventPipelineParameter() {
    return Arrays.asList(false, true);
  }

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer, false);
  private final FakeProfilerService myProfilerService = new FakeProfilerService(myTimer);
  @Rule public FakeGrpcServer myGrpcServer =
    FakeGrpcServer.createFakeGrpcServer("StudioProfilerTestChannel", myTransportService, myProfilerService);
  private final ProfilerClient myProfilerClient = new ProfilerClient(myGrpcServer.getName());
  private final FakeGrpcServer.CpuService myCpuService = myGrpcServer.getCpuService();
  private final FakeIdeProfilerServices myIdeProfilerServices;

  private final boolean myNewEventPipeline;

  public StudioProfilersTest(boolean useNewEventPipeline) {
    myIdeProfilerServices = new FakeIdeProfilerServices();
    myIdeProfilerServices.enableEventsPipeline(useNewEventPipeline);
    myNewEventPipeline = useNewEventPipeline;
  }

  @Test
  public void testSleepBeforeAppLaunched() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    //Validate we start in the null stage.
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setProcess(device, null);
    // Validate that just because we add a device, we still have not left the  null monitor stage.
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    // Pick a time to set the device to. Note that this value is arbitrary but the bug this tests
    // is exposed if this value is larger than nanoTime.
    long timeOnDevice = System.nanoTime() + 1000;
    myTimer.setCurrentTimeNs(timeOnDevice);

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    // Add a process and validate the stage goes to the monitor stage.
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Test that the process was attached correctly
    assertThat(profilers.getTimeline().isStreaming()).isTrue();
    // Test that the data range has not been inverted
    assertThat(profilers.getTimeline().getDataRange().isEmpty()).isFalse();
  }

  @Test
  public void testProfilerStageChange() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    //Validate we start in the null stage.
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setProcess(device, null);
    // Validate that just because we add a device, we still have not left the  null monitor stage.
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    // Add a process and validate the stage goes to the monitor stage.
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getStageClass()).isSameAs(StudioMonitorStage.class);
  }

  @Test
  public void testLateConnectionOfPreferredProcess() {
    final String PREFERRED_PROCESS = "Preferred";
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    profilers.setPreferredProcess(null, PREFERRED_PROCESS, null);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setProcess(device, null);
    // We are waiting for the preferred process so the process should not be selected.
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getProcess()).isNull();

    Common.Process preferred = createProcess(device.getDeviceId(), 21, PREFERRED_PROCESS, Common.Process.State.ALIVE);
    myTransportService.addProcess(device, preferred);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(preferred);
    assertThat(profilers.getProcesses()).hasSize(2);
    assertThat(profilers.getProcesses()).containsAllIn(ImmutableList.of(process, preferred));
  }

  @Test
  public void testSetPreferredProcessDoesNotProfileEarlierProcess() {
    final String PREFERRED_PROCESS = "Preferred";
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    profilers.setPreferredProcess(null, PREFERRED_PROCESS, p -> p.getStartTimestampNs() > 5);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process earlierProcess = Common.Process.newBuilder()
      .setDeviceId(device.getDeviceId())
      .setPid(20)
      .setName(PREFERRED_PROCESS)
      .setState(Common.Process.State.ALIVE)
      .setStartTimestampNs(5)
      .build();
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, earlierProcess);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, null);
    // The process start time is before the time we start looking for preferred process, so profiler should not have started.
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isNull();

    Common.Process afterProcess = Common.Process.newBuilder()
      .setDeviceId(device.getDeviceId())
      .setPid(21)
      .setName(PREFERRED_PROCESS)
      .setState(Common.Process.State.ALIVE)
      .setStartTimestampNs(10)
      .build();
    myTransportService.addProcess(device, afterProcess);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(afterProcess);
    assertThat(profilers.getProcesses()).hasSize(2);
    assertThat(profilers.getProcesses()).containsAllIn(ImmutableList.of(earlierProcess, afterProcess));
  }

  @Test
  public void testSetNullPreferredProcessDoesNotStartAutoProfiling() {
    final String PREFERRED_PROCESS = "Preferred";
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    profilers.setPreferredProcess(null, null, p -> p.getStartTimestampNs() > 5);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = Common.Process.newBuilder()
      .setDeviceId(device.getDeviceId())
      .setPid(21)
      .setName(PREFERRED_PROCESS)
      .setState(Common.Process.State.ALIVE)
      .setStartTimestampNs(10)
      .build();
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, null);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());
  }

  @Test
  public void testConnectionError() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE)
      .toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    profilers.setPreferredProcess("FakeDevice", "FakeProcess", null);
    // This should fail and not find any devices
    myTransportService.setThrowErrorOnGetDevices(true);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    // Server "is back up", try again
    myTransportService.setThrowErrorOnGetDevices(false);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getProcess().getName()).isEqualTo("FakeProcess");
  }

  @Test
  public void testAlreadyConnected() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getStage()).isInstanceOf(StudioMonitorStage.class);
  }

  @Test
  public void testTimeResetOnConnectedDevice() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    int nowInSeconds = 42;
    myTimer.setCurrentTimeNs(TimeUnit.SECONDS.toNanos(nowInSeconds));
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getTimeline().getDataRange().getMin()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds));
    assertThat(profilers.getTimeline().getDataRange().getMax()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds));

    // The timeline has reset in the previous tick, so we need to advance the current time to make sure the next tick advances data range.
    myTimer.setCurrentTimeNs(myTimer.getCurrentTimeNs() + FakeTimer.ONE_SECOND_IN_NS * 5);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS * 5);

    assertThat(profilers.getTimeline().getDataRange().getMin()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds));
    assertThat(profilers.getTimeline().getDataRange().getMax()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds + 5));
  }

  @Test
  public void testAgentUnattachableAfterMaxRetries() {
    Assume.assumeFalse(myNewEventPipeline);
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    AgentData attachedResponse = AgentData.newBuilder().setStatus(AgentData.Status.UNSPECIFIED).build();
    myTransportService.setAgentStatus(attachedResponse);
    myTransportService.addDevice(FAKE_DEVICE);
    myTransportService.addProcess(FAKE_DEVICE, FAKE_PROCESS);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(FAKE_DEVICE, FAKE_PROCESS);

    for (int i = 0; i < AGENT_STATUS_MAX_RETRY_COUNT; i++) {
      assertThat(profilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNSPECIFIED);
      myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    }
    assertThat(profilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNATTACHABLE);

    // Ensures that if the agent becomes attached at a later point, the status will be correct.
    attachedResponse = AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build();
    myTransportService.setAgentStatus(attachedResponse);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.ATTACHED);
  }

  @Test
  public void testAgentStatusRetryCachedForSession() {
    Assume.assumeFalse(myNewEventPipeline);
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    AgentData attachedResponse = AgentData.newBuilder().setStatus(AgentData.Status.UNSPECIFIED).build();
    myTransportService.setAgentStatus(attachedResponse);
    myTransportService.addDevice(FAKE_DEVICE);
    myTransportService.addProcess(FAKE_DEVICE, FAKE_PROCESS);
    Common.Process process2 = FAKE_PROCESS.toBuilder().setPid(2).build();
    myTransportService.addProcess(FAKE_DEVICE, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(FAKE_DEVICE, FAKE_PROCESS);
    profilers.getSessionsManager().endCurrentSession();
    Common.Session session1 = profilers.getSession();

    // Switch to a different process.
    profilers.setProcess(FAKE_DEVICE, process2);
    Common.Session session2 = profilers.getSession();
    int session2RetryCount;
    for (session2RetryCount = 0; session2RetryCount < AGENT_STATUS_MAX_RETRY_COUNT / 2; session2RetryCount++) {
      assertThat(profilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNSPECIFIED);
      myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    }

    // Switch back to the first session, agent status should remain unspecified because it is ended.
    profilers.getSessionsManager().setSession(session1);
    for (int j = 0; j < AGENT_STATUS_MAX_RETRY_COUNT * 2; j++) {
      myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    }
    assertThat(profilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNSPECIFIED);

    // Switch back to the second session, we should only need another 5 ticks to reach UNATTACHABLE.
    profilers.getSessionsManager().setSession(session2);
    for (; session2RetryCount < AGENT_STATUS_MAX_RETRY_COUNT; session2RetryCount++) {
      assertThat(profilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNSPECIFIED);
      myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    }
    assertThat(profilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNATTACHABLE);

    // Switch to the first session and back should give UNATTACHABLE immediately.
    profilers.getSessionsManager().setSession(session1);
    profilers.getSessionsManager().setSession(session2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNATTACHABLE);
  }

  @Test
  public void testAgentAspectFiring() {
    AgentStatusAspectObserver observer = new AgentStatusAspectObserver();
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    profilers.addDependency(observer).onChange(ProfilerAspect.AGENT, observer::AgentStatusChanged);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getAgentData()).isEqualTo(AgentData.getDefaultInstance());
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(0);

    // Test that status changes if no process is selected does nothing
    AgentData attachedResponse = AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build();
    myTransportService.setAgentStatus(attachedResponse);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.getAgentData()).isEqualTo(AgentData.getDefaultInstance());
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(0);

    // Test that agent status change fires after a process is selected.
    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Process process2 = createProcess(device.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process1);
    myTransportService.addProcess(device, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(profilers.getAgentData()).isEqualTo(attachedResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);

    // Test that manually setting a process fires an agent status change
    profilers.setProcess(device, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess()).isSameAs(process2);
    assertThat(profilers.getAgentData()).isEqualTo(attachedResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(2);

    // Setting the same agent status should not trigger an aspect change.
    attachedResponse = AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build();
    myTransportService.setAgentStatus(attachedResponse);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getAgentData()).isEqualTo(attachedResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(2);
  }

  @Test
  public void testAgentAspectNotFiredWhenSettingSameDeviceProcess() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    AgentStatusAspectObserver observer = new AgentStatusAspectObserver();
    profilers.addDependency(observer).onChange(ProfilerAspect.AGENT, observer::AgentStatusChanged);

    // Test that the status changed is fired when the process first gets selected.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice()).isSameAs(device);
    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(profilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);

    // Test that resetting the same device/process would not trigger the status changed event.
    profilers.setProcess(device, process);
    assertThat(profilers.getDevice()).isSameAs(device);
    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(profilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);
  }

  @Test
  public void testRestartedPreferredProcessNotSelected() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    //int nowInSeconds = 42;
    //myTransportService.setTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds));
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device =
      createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE).toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    profilers.setPreferredProcess("FakeDevice", process.getName(), null);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);

    // Change the alive (active) process to DEAD, and create a new ALIVE process simulating a debugger restart.
    myTransportService.removeProcess(device, process);
    process = process.toBuilder()
      .setState(Common.Process.State.DEAD)
      .build();
    myTransportService.addProcess(device, process);

    // Verify the process is in the dead state.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.DEAD);

    process = process.toBuilder()
      .setPid(21)
      .setState(Common.Process.State.ALIVE)
      .build();
    myTransportService.addProcess(device, process);


    // The profiler should not automatically selects the alive, preferred process again.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.DEAD);
    assertThat(profilers.getAutoProfilingEnabled()).isFalse();

    // Re-enable auto-profiling should pick up the new process.
    profilers.setAutoProfilingEnabled(true);
    assertThat(profilers.getProcess().getPid()).isEqualTo(21);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
  }

  @Test
  public void shouldNotSelectPreferredAfterUserSelectsOtherProcess() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE)
      .toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    profilers.setPreferredProcess("FakeDevice", process.getName(), null);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);

    Common.Process otherProcess = createProcess(device.getDeviceId(), 21, "OtherProcess", Common.Process.State.ALIVE);
    myTransportService.addProcess(device, otherProcess);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, otherProcess);
    // The user selected the other process explicitly
    assertThat(profilers.getProcess().getPid()).isEqualTo(21);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);

    // Should select the other process again
    profilers.setProcess(device, null);
    assertThat(profilers.getProcess().getPid()).isEqualTo(21);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
  }

  @Test
  public void shouldOpenCpuProfileStageIfStartupProfilingStarted() {
    myIdeProfilerServices.enableStartupCpuProfiling(true);

    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    Cpu.CpuTraceInfo traceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setInitiationType(Cpu.TraceInitiationType.INITIATED_BY_STARTUP))
      .build();
    if (myNewEventPipeline) {
      myTransportService.addEventToStream(device.getDeviceId(), Common.Event.newBuilder()
        .setGroupId(myTimer.getCurrentTimeNs())
        .setPid(process.getPid())
        .setKind(Common.Event.Kind.CPU_TRACE)
        .setCpuTrace(Cpu.CpuTraceData.newBuilder()
                       .setTraceEnded(Cpu.CpuTraceData.TraceEnded.newBuilder().setTraceInfo(traceInfo).build()))
        .build());
    }
    else {
      myCpuService.addTraceInfo(traceInfo);
    }

    // To make sure that StudioProfilers#update is called, which in a consequence polls devices and processes,
    // and starts a new session with the preferred process name.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
    assertThat(profilers.getStage()).isInstanceOf(CpuProfilerStage.class);
  }

  @Test
  public void testProcessStateChangesShouldNotTriggerStageChange() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(Common.Process.State.ALIVE).isEqualTo(profilers.getProcess().getState());

    AspectObserver observer = new AspectObserver();
    profilers.addDependency(observer).onChange(ProfilerAspect.STAGE, () -> {
      assert false;
    });
    // Change the alive (active) process to DEAD
    myTransportService.removeProcess(device, process);
    process = process.toBuilder()
      .setState(Common.Process.State.DEAD)
      .build();
    myTransportService.addProcess(device, process);

    //Verify the process is in the dead state.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(Common.Process.State.DEAD).isEqualTo(profilers.getProcess().getState());
  }

  @Test
  public void timelineShouldBeStreamingWhenProcessIsSelected() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getTimeline().isStreaming()).isTrue();
  }

  @Test
  public void timelineShouldStopStreamingWhenRangeIsSelected() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    ProfilerTimeline timeline = profilers.getTimeline();
    assertTrue(timeline.isStreaming());
    timeline.getDataRange().set(0, FakeTimer.ONE_SECOND_IN_NS);
    timeline.getSelectionRange().set(0, 0);
    assertFalse(timeline.isStreaming());

    timeline.setStreaming(true);
    assertTrue(timeline.isStreaming());
    timeline.getSelectionRange().set(0, FakeTimer.ONE_SECOND_IN_NS);
    assertFalse(timeline.isStreaming());
  }

  @Test
  public void onlineDeviceShouldNotOverrideSelectedOfflineDevice() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process);

    // Connect a new device, and marks the old one as disconnected
    Common.Device dead_device = device.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process2 = createProcess(device2.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myTransportService.updateDevice(device, dead_device);
    myTransportService.addDevice(device2);
    myTransportService.addProcess(device2, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connecting an online device should not override previously selection automatically
    assertThat(profilers.getDevice()).isEqualTo(dead_device);
    assertThat(profilers.getProcess()).isEqualTo(process);
  }

  @Test
  public void preferredDeviceShouldNotOverrideSelectedDevice() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    profilers.setPreferredProcess("Manufacturer Model", "ProcessName", null);

    // A device with a process that can be profiled
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    // The preferred device
    Common.Device preferredDevice = createDevice(AndroidVersion.VersionCodes.BASE, "PreferredDevice", Common.Device.State.ONLINE);
    preferredDevice = preferredDevice.toBuilder().setManufacturer("Manufacturer").setModel("Model").build();
    Common.Process preferredProcess =
      createProcess(preferredDevice.getDeviceId(), 21, "PreferredProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(preferredDevice);
    myTransportService.addProcess(preferredDevice, preferredProcess);

    // Preferred should be selected.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(preferredDevice);

    // Selecting device manually should keep it selected
    profilers.setProcess(device, null);
    assertThat(profilers.getDevice()).isEqualTo(device);

    // Changing states on the preferred device should have no effects.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(device);

    Common.Device offlinePreferredDevice = preferredDevice.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    myTransportService.updateDevice(preferredDevice, offlinePreferredDevice);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(device);

    myTransportService.updateDevice(offlinePreferredDevice, preferredDevice);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(device);
  }

  @Test
  public void preferredDeviceHasPriority() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    profilers.setPreferredProcess("Manufacturer Model", "PreferredProcess", null);

    // A device with a process that can be profiled
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    // Create the preferred device but have it offline to start with.
    Common.Device offlinePreferredDevice = createDevice(AndroidVersion.VersionCodes.BASE, "PreferredDevice", Common.Device.State.OFFLINE);
    offlinePreferredDevice = offlinePreferredDevice.toBuilder().setManufacturer("Manufacturer").setModel("Model").build();
    Common.Process preferredProcess =
      createProcess(offlinePreferredDevice.getDeviceId(), 21, "PreferredProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(offlinePreferredDevice);
    myTransportService.addProcess(offlinePreferredDevice, preferredProcess);
    // No device should be selected given no online preferred device is found.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    // Turn the preferred device online and it should be selected.
    Common.Device preferredDevice = offlinePreferredDevice.toBuilder().setState(Common.Device.State.ONLINE).build();
    myTransportService.updateDevice(offlinePreferredDevice, preferredDevice);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(preferredDevice);
    assertThat(profilers.getProcess()).isEqualTo(preferredProcess);

    Common.Device preferredDevice2 = createDevice(AndroidVersion.VersionCodes.BASE, "PreferredDevice2", Common.Device.State.ONLINE);
    preferredDevice2 = preferredDevice2.toBuilder().setManufacturer("Manufacturer2").setModel("Model2").build();
    Common.Process preferredProcess2 = createProcess(preferredDevice2.getDeviceId(), 22, "PreferredProcess2", Common.Process.State.ALIVE);
    myTransportService.addDevice(preferredDevice2);
    myTransportService.addProcess(preferredDevice2, preferredProcess2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(preferredDevice);
    assertThat(profilers.getProcess()).isEqualTo(preferredProcess);

    // Updating the preferred device should immediately switch over.
    profilers.setPreferredProcess("Manufacturer2 Model2", "PreferredProcess", null);
    assertThat(profilers.getDevice()).isEqualTo(preferredDevice2);
    assertThat(profilers.getProcess()).isNull();
  }

  @Test
  public void keepSelectedDeviceAfterDisconnectingAllDevices() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device1);
    myTransportService.addProcess(device1, process1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect a new device
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process2 = createProcess(device2.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myTransportService.addDevice(device2);
    myTransportService.addProcess(device2, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    profilers.setProcess(device2, null);
    assertThat(profilers.getDevice()).isEqualTo(device2);
    // Update device1 state to disconnect
    Common.Device disconnectedDevice = Common.Device.newBuilder()
      .setDeviceId(device1.getDeviceId())
      .setSerial(device1.getSerial())
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    myTransportService.updateDevice(device1, disconnectedDevice);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Update device2 state to disconnect
    Common.Device disconnectedDevice2 = Common.Device.newBuilder()
      .setDeviceId(device2.getDeviceId())
      .setSerial(device2.getSerial())
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    myTransportService.updateDevice(device2, disconnectedDevice2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Selected device should be FakeDevice2, which was selected before disconnecting all devices
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice2");
    // Make sure the device is disconnected
    assertThat(profilers.getDevice().getState()).isEqualTo(Common.Device.State.DISCONNECTED);
  }

  @Test
  public void testProfileOneProcessAtATime() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Process process2 = createProcess(device1.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myTransportService.addDevice(device1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    myTransportService.addProcess(device1, process1);
    myTransportService.addProcess(device1, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device1, process1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);

    // Switch to another process.
    profilers.setProcess(device1, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(profilers.getProcess()).isEqualTo(process2);

    // Connect a new device with a process.
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process3 = createProcess(device2.getDeviceId(), 22, "FakeProcess3", Common.Process.State.ALIVE);
    myTransportService.addDevice(device2);
    myTransportService.addProcess(device2, process3);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Switch to the new device + process
    profilers.setProcess(device2, process3);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(profilers.getProcess()).isEqualTo(process3);

    // Update device2 state to disconnect
    Common.Device disconnectedDevice2 = device2.toBuilder()
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    myTransportService.updateDevice(device2, disconnectedDevice2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);

    // Switch back to the first device.
    profilers.setProcess(device1, process1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);

    // Update device1 state to disconnect
    Common.Device disconnectedDevice = device1.toBuilder()
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    myTransportService.updateDevice(device1, disconnectedDevice);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
  }

  @Test
  public void testAttachAgentCalledPostO() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process1);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myNewEventPipeline ? myTransportService.getAgentAttachCalled() : myProfilerService.getAgentAttachCalled()).isTrue();
  }

  @Test
  public void testAttachAgentNotCalledPreO() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.N, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process1);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process1);

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myNewEventPipeline ? myTransportService.getAgentAttachCalled() : myProfilerService.getAgentAttachCalled()).isFalse();
  }

  /**
   * We need to account for an scenario where perfd reinstantiates and needs to pass a new client socket to the app. Hence we make the
   * same attach agent call from Studio side and let perfd handles the rest.
   */
  @Test
  public void testAttachAgentEvenIfAlreadyAttached() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    AgentData attachedResponse = AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build();
    myTransportService.setAgentStatus(attachedResponse);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process1);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myNewEventPipeline ? myTransportService.getAgentAttachCalled() : myProfilerService.getAgentAttachCalled()).isTrue();
  }

  @Test
  public void testProfilingStops() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE)
      .toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    myTransportService.addProcess(device, process);

    profilers.setPreferredProcess("FakeDevice", "FakeProcess", null);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(myTimer.isRunning()).isTrue();

    // Stop the profiler
    profilers.stop();

    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.getDevice()).isNull();
    assertThat(myTimer.isRunning()).isFalse();
  }

  @Test
  public void testNullDeviceKeepsPreviousSession() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice1", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    myTransportService.addDevice(device1);
    myTransportService.addProcess(device1, process);
    myTransportService.addDevice(device2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device1, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Common.Session session = profilers.getSession();
    assertThat(profilers.getDevice()).isEqualTo(device1);
    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(session).isNotEqualTo(Common.Session.getDefaultInstance());
    assertThat(profilers.getStageClass()).isEqualTo(StudioMonitorStage.class);

    // Setting device and process to null should maintain the current session.
    profilers.setProcess(null, null);
    assertThat(profilers.getDevice()).isEqualTo(null);
    assertThat(profilers.getProcess()).isEqualTo(null);
    assertThat(profilers.getSession().getSessionId()).isEqualTo(session.getSessionId());
    assertThat(profilers.getStageClass()).isEqualTo(StudioMonitorStage.class);
  }

  @Test
  public void testUnsupportedDeviceShowsNullStage() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    String deviceName = "UnsupportedDevice";
    String unsupportedReason = "Unsupported device";
    Common.Device device = Common.Device.newBuilder()
      .setDeviceId(deviceName.hashCode())
      .setFeatureLevel(AndroidVersion.VersionCodes.KITKAT)
      .setModel(deviceName)
      .setState(Common.Device.State.ONLINE)
      .setUnsupportedReason(unsupportedReason)
      .build();
    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setPreferredProcess(deviceName, "FakeProcess", null);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.getStageClass()).isEqualTo(NullMonitorStage.class);
    assertThat(((NullMonitorStage)profilers.getStage()).getUnsupportedReason()).isEqualTo(unsupportedReason);
  }

  @Test
  public void testProfilingStopsWithLiveAllocationEnabled() {
    // Enable live allocation tracker
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);

    assertThat(myTimer.isRunning()).isTrue();

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process);

    // Stop the profiler
    profilers.stop();

    assertThat(myTimer.isRunning()).isFalse();
    assertThat(profilers.getProcess()).isEqualTo(null);
    assertThat(profilers.getDevice()).isEqualTo(null);
  }

  @Test
  public void testStoppingTwice() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    // Should be modified when STAGE aspect is fired.
    boolean[] stageAspectTriggered = {false};
    profilers.addDependency(new AspectObserver())
      .onChange(ProfilerAspect.STAGE, () -> stageAspectTriggered[0] = true);

    // Check profiler is not stopped.
    assertThat(profilers.isStopped()).isFalse();
    assertThat(myTimer.isRunning()).isTrue();
    // Stop the profiler
    profilers.stop();
    // Profiler should have stopped and STAGE is supposed to have been fired.
    assertThat(stageAspectTriggered[0]).isTrue();

    // Check profiler is stopped.
    assertThat(profilers.isStopped()).isTrue();
    assertThat(myTimer.isRunning()).isFalse();
    stageAspectTriggered[0] = false;
    // Try to stop the profiler again.
    profilers.stop();
    // Profiler was already stopped and STAGE is not supposed to have been fired.
    assertThat(stageAspectTriggered[0]).isFalse();
  }

  @Test
  public void testBeginAndEndSessionOnProcessChange() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a device without processes. Session should be null.
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a process, which should trigger the session to start.
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);

    // Mark the process as dead, which should ends the session.
    myTransportService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testBeginAndEndSessionOnDeviceChange() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a device with process. Session should start immediately
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);

    // Killing the device should stop the session
    myTransportService.removeProcess(device, process);
    Common.Device deadDevice = device.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    Common.Process deadProcess = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTransportService.addDevice(deadDevice);
    myTransportService.addProcess(deadDevice, deadProcess);
    myTimer.setCurrentTimeNs(FakeTimer.ONE_SECOND_IN_NS);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testSessionDoesNotAutoStartOnSameProcess() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // End the session.
    myTransportService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Common.Session session = profilers.getSession();
    assertThat(session).isNotNull();
    assertThat(session.getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(session.getPid()).isEqualTo(process.getPid());

    // The same process coming alive should not start a new session automatically.
    myTransportService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.ALIVE).build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isEqualTo(session);
  }

  @Test
  public void testNewSessionResetsStage() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);
    assertThat(profilers.getStage()).isInstanceOf(StudioMonitorStage.class);

    // Goes into a different stage
    profilers.setStage(new FakeStage(profilers));
    assertThat(profilers.getStage()).isInstanceOf(FakeStage.class);

    // Ending a session should not leave the stage automatically.
    myTransportService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
    assertThat(profilers.getStage()).isInstanceOf(FakeStage.class);

    // Restarting a session on the same process should re-enter the StudioMonitorStage
    myTransportService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.ALIVE).build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);
    assertThat(profilers.getStage()).isInstanceOf(StudioMonitorStage.class);
  }

  @Test
  public void testGetDirectStagesReturnsOnlyExpectedStages() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    // When energy flag is enabled and device is pre-O, GetDirectStages does not return Energy stage.
    myIdeProfilerServices.enableEnergyProfiler(true);
    Common.Device deviceNougat = createDevice(AndroidVersion.VersionCodes.N_MR1, "FakeDeviceN", Common.Device.State.ONLINE);
    myTransportService.addDevice(deviceNougat);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(deviceNougat, null);
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDeviceN");

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class).inOrder();

    // When energy flag is enabled and device is O, GetDirectStages returns Energy stage.
    Common.Device deviceOreo = createDevice(AndroidVersion.VersionCodes.O, "FakeDeviceO", Common.Device.State.ONLINE);
    myTransportService.addDevice(deviceOreo);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(deviceOreo, null);
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDeviceO");

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class,
      EnergyProfilerStage.class).inOrder();

    // When energy flag is disabled and device is O, GetDirectStages does not return Energy stage.
    myIdeProfilerServices.enableEnergyProfiler(false);
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDeviceO");

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class).inOrder();

    // When custom event flag is enabled and device is O, GetDirectStages returns Custom Event stage.
    myIdeProfilerServices.enableCustomEventVisualization(true);
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDeviceO");

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class,
      CustomEventProfilerStage.class).inOrder();

    // When custom event flag is disabled and device is O, GetDirectStages does not return Custom Event stage.
    myIdeProfilerServices.enableCustomEventVisualization(false);
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDeviceO");

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class).inOrder();
  }

  @Test
  public void testGetDirectStageReturnsEnergyOnlyForPostOSession() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);

    // When energy flag is enabled and the session is pre-O, GetDirectStages does not return Energy stage.
    myIdeProfilerServices.enableEnergyProfiler(true);
    Common.Session sessionPreO = Common.Session.newBuilder()
      .setSessionId(1).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS).setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build();
    Common.SessionMetaData sessionPreOMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(1).setType(Common.SessionMetaData.SessionType.FULL).setJvmtiEnabled(false).setStartTimestampEpochMs(1).build();

    if (myNewEventPipeline) {
      myTransportService.addSession(sessionPreO, sessionPreOMetadata);
    }
    else {
      myProfilerService.addSession(sessionPreO, sessionPreOMetadata);
    }
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.getSessionsManager().setSession(profilers.getSessionsManager().getSessionArtifacts().get(0).getSession());
    assertThat(profilers.getSessionsManager().getSelectedSessionMetaData().getJvmtiEnabled()).isFalse();

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class).inOrder();

    // When energy flag is enabled and the session is O, GetDirectStages returns Energy stage.
    Common.Session sessionO = Common.Session.newBuilder()
      .setSessionId(2).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS).setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build();
    Common.SessionMetaData sessionOMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(2).setType(Common.SessionMetaData.SessionType.FULL).setJvmtiEnabled(true).setStartTimestampEpochMs(1).build();
    if (myNewEventPipeline) {
      myTransportService.addSession(sessionO, sessionOMetadata);
    }
    else {
      myProfilerService.addSession(sessionO, sessionOMetadata);
    }
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.getSessionsManager().setSession(profilers.getSessionsManager().getSessionArtifacts().get(0).getSession());
    assertThat(profilers.getSessionsManager().getSelectedSessionMetaData().getJvmtiEnabled()).isTrue();

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class,
      EnergyProfilerStage.class).inOrder();

    // When energy flag is disabled and the session is pre-O, GetDirectStages does not return Energy stage.
    myIdeProfilerServices.enableEnergyProfiler(false);
    assertThat(profilers.getSessionsManager().getSelectedSessionMetaData().getJvmtiEnabled()).isTrue();
    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class).inOrder();
  }

  @Test
  public void testBuildSessionName() {
    Common.Device device1 = Common.Device.newBuilder()
      .setManufacturer("Manufacturer")
      .setModel("Model")
      .setSerial("Serial")
      .build();
    Common.Device device2 = Common.Device.newBuilder()
      .setModel("Model-Serial")
      .setSerial("Serial")
      .build();
    Common.Process process1 = Common.Process.newBuilder()
      .setPid(10)
      .setAbiCpuArch("x86")
      .setName("Process1")
      .build();
    Common.Process process2 = Common.Process.newBuilder()
      .setPid(20)
      .setAbiCpuArch("arm")
      .setName("Process2")
      .build();

    assertThat(StudioProfilers.buildSessionName(device1, process1)).isEqualTo("Process1 (Manufacturer Model)");
    assertThat(StudioProfilers.buildSessionName(device2, process2)).isEqualTo("Process2 (Model)");
  }

  @Test
  public void testBuildDeviceName() {
    Common.Device device = Common.Device.newBuilder()
      .setManufacturer("Manufacturer")
      .setModel("Model")
      .setSerial("Serial")
      .build();
    assertThat(StudioProfilers.buildDeviceName(device)).isEqualTo("Manufacturer Model");

    Common.Device deviceWithEmptyManufacturer = Common.Device.newBuilder()
      .setModel("Model")
      .setSerial("Serial")
      .build();
    assertThat(StudioProfilers.buildDeviceName(deviceWithEmptyManufacturer)).isEqualTo("Model");

    Common.Device deviceWithSerialInModel = Common.Device.newBuilder()
      .setManufacturer("Manufacturer")
      .setModel("Model-Serial")
      .setSerial("Serial")
      .build();
    assertThat(StudioProfilers.buildDeviceName(deviceWithSerialInModel)).isEqualTo("Manufacturer Model");
  }

  @Test
  public void testSelectedAppNameFromSession() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess (phone)", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isNotEqualTo(Common.Session.getDefaultInstance());
    assertThat(profilers.getSelectedAppName()).isEqualTo("FakeProcess");
  }

  @Test
  public void testSelectedAppNameFromProcessWhenNoSession() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess (phone)", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(device, process);

    profilers.getSessionsManager().setSession(Common.Session.getDefaultInstance());
    assertThat(profilers.getSelectedAppName()).isEqualTo("FakeProcess");
  }

  @Test
  public void testSelectedAppNameWhenNoProcessAndNoSession() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.getSelectedAppName()).isEmpty();
  }

  @Test
  public void testSessionViewRangeCaches() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Session finishedSession = Common.Session.newBuilder()
      .setSessionId(1).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS)
      .setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build();
    Common.SessionMetaData finishedSessionMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(1)
      .setType(Common.SessionMetaData.SessionType.FULL)
      .setStartTimestampEpochMs(1).build();
    Common.Session ongoingSession = Common.Session.newBuilder()
      .setSessionId(2).setStartTimestamp(0).setEndTimestamp(Long.MAX_VALUE).build();
    Common.SessionMetaData ongoingSessionMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(2).setType(Common.SessionMetaData.SessionType.FULL)
      .setStartTimestampEpochMs(2).build();
    if (myNewEventPipeline) {
      myTransportService.addSession(finishedSession, finishedSessionMetadata);
    }
    else {
      myProfilerService.addSession(finishedSession, finishedSessionMetadata);
    }
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    if (myNewEventPipeline) {
      myTransportService.addSession(ongoingSession, ongoingSessionMetadata);
    }
    else {
      myProfilerService.addSession(ongoingSession, ongoingSessionMetadata);
    }
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Arbitrary view range min/max to be set for each session
    long viewRangeMin = TimeUnit.MILLISECONDS.toMicros(1200);
    long viewRangeMax = TimeUnit.MILLISECONDS.toMicros(1600);

    // selecting an ongoing session should use the default zoom with streaming enabled
    profilers.getSessionsManager().setSession(profilers.getSessionsManager().getSessionArtifacts().get(0).getSession());
    assertThat(profilers.getTimeline().getViewRange().getMin()).isWithin(0).of(-ProfilerTimeline.DEFAULT_VIEW_LENGTH_US);
    assertThat(profilers.getTimeline().getViewRange().getMax()).isWithin(0).of(0);
    assertThat(profilers.getTimeline().isStreaming()).isTrue();
    assertThat(profilers.getTimeline().isPaused()).isFalse();
    profilers.getTimeline().getViewRange().set(viewRangeMin, viewRangeMax);

    // selecting a finished session without a view range cache should use the entire data range
    profilers.getSessionsManager().setSession(profilers.getSessionsManager().getSessionArtifacts().get(1).getSession());
    assertThat(profilers.getTimeline().getViewRange().getMin()).isWithin(0).of(TimeUnit.SECONDS.toMicros(1));
    assertThat(profilers.getTimeline().getViewRange().getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(2));
    assertThat(profilers.getTimeline().isStreaming()).isFalse();
    assertThat(profilers.getTimeline().isPaused()).isTrue();
    profilers.getTimeline().getViewRange().set(viewRangeMin, viewRangeMax);

    // Navigate back to the ongoing session should still use the default zoom
    profilers.getSessionsManager().setSession(profilers.getSessionsManager().getSessionArtifacts().get(0).getSession());
    assertThat(profilers.getTimeline().getViewRange().getMin()).isWithin(0).of(-ProfilerTimeline.DEFAULT_VIEW_LENGTH_US);
    assertThat(profilers.getTimeline().getViewRange().getMax()).isWithin(0).of(0);
    assertThat(profilers.getTimeline().isStreaming()).isTrue();
    assertThat(profilers.getTimeline().isPaused()).isFalse();

    // Navigate again to the finished session should use the last view range
    profilers.getSessionsManager().setSession(profilers.getSessionsManager().getSessionArtifacts().get(1).getSession());
    assertThat(profilers.getTimeline().getViewRange().getMin()).isWithin(0).of(viewRangeMin);
    assertThat(profilers.getTimeline().getViewRange().getMax()).isWithin(0).of(viewRangeMax);
    assertThat(profilers.getTimeline().isStreaming()).isFalse();
    assertThat(profilers.getTimeline().isPaused()).isTrue();

    // Arbitrarily setting the view range to something beyond the data range should force the timeline to clamp to data range.
    profilers.getTimeline().getViewRange().set(TimeUnit.SECONDS.toMicros(-10), TimeUnit.SECONDS.toMicros(10));
    profilers.getSessionsManager().setSession(Common.Session.getDefaultInstance());
    profilers.getSessionsManager().setSession(profilers.getSessionsManager().getSessionArtifacts().get(1).getSession());
    assertThat(profilers.getTimeline().getViewRange().getMin()).isWithin(0).of(TimeUnit.SECONDS.toMicros(1));
    assertThat(profilers.getTimeline().getViewRange().getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(2));
    assertThat(profilers.getTimeline().isStreaming()).isFalse();
    assertThat(profilers.getTimeline().isPaused()).isTrue();
  }

  @Test
  public void testMultipleUpdateTicksShouldNotChangeSession() {
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
    Common.Session finishedSession = Common.Session.newBuilder()
      .setSessionId(1).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS)
      .setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build();
    Common.SessionMetaData finishedSessionMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(1)
      .setType(Common.SessionMetaData.SessionType.FULL)
      .setStartTimestampEpochMs(1).build();
    Common.Session ongoingSession = Common.Session.newBuilder()
      .setSessionId(2).setStartTimestamp(0).setEndTimestamp(Long.MAX_VALUE).build();
    Common.SessionMetaData ongoingSessionMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(2).setType(Common.SessionMetaData.SessionType.FULL)
      .setStartTimestampEpochMs(2).build();
    if (myNewEventPipeline) {
      myTransportService.addSession(finishedSession, finishedSessionMetadata);
    }
    else {
      myProfilerService.addSession(finishedSession, finishedSessionMetadata);
    }
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    if (myNewEventPipeline) {
      myTransportService.addSession(ongoingSession, ongoingSessionMetadata);
    }
    else {
      myProfilerService.addSession(ongoingSession, ongoingSessionMetadata);
    }
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    profilers.getSessionsManager().setSession(profilers.getSessionsManager().getSessionArtifacts().get(0).getSession());
    assertThat(profilers.getSessionsManager().getSelectedSession().getSessionId()).isEqualTo(ongoingSession.getSessionId());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSessionsManager().getSelectedSession().getSessionId()).isEqualTo(ongoingSession.getSessionId());

    profilers.getSessionsManager().setSession(profilers.getSessionsManager().getSessionArtifacts().get(1).getSession());
    assertThat(profilers.getSessionsManager().getSelectedSession().getSessionId()).isEqualTo(finishedSession.getSessionId());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSessionsManager().getSelectedSession().getSessionId()).isEqualTo(finishedSession.getSessionId());
  }

  private static Common.Device createDevice(int featureLevel, @NotNull String serial, @NotNull Common.Device.State state) {
    return Common.Device.newBuilder()
      .setDeviceId(serial.hashCode())
      .setFeatureLevel(featureLevel)
      .setSerial(serial)
      .setState(state)
      .build();
  }

  private Common.Process createProcess(long deviceId, int pid, @NotNull String name, Common.Process.State state) {
    return Common.Process.newBuilder()
      .setDeviceId(deviceId)
      .setPid(pid)
      .setName(name)
      .setState(state)
      .build();
  }

  private static class AgentStatusAspectObserver extends AspectObserver {
    private int myAgentStatusChangedCount;

    void AgentStatusChanged() {
      myAgentStatusChangedCount++;
    }

    int getAgentStatusChangedCount() {
      return myAgentStatusChangedCount;
    }
  }

  private static class FakeStage extends Stage {
    private FakeStage(@NotNull StudioProfilers profilers) {
      super(profilers);
    }

    @Override
    public void enter() {
    }

    @Override
    public void exit() {
    }
  }
}
