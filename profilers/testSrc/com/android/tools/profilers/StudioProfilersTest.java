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
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.idea.transport.faketransport.FakeGrpcServer;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.idea.transport.faketransport.commands.BeginSession;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.AgentData;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.customevent.CustomEventProfilerStage;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.memory.MainMemoryProfilerStage;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.junit.Before;
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
  private final FakeGrpcServer.CpuService myCpuService = myGrpcServer.getCpuService();
  private final FakeIdeProfilerServices myIdeProfilerServices;
  private final boolean myNewEventPipeline;

  private ProfilerClient myProfilerClient;
  private StudioProfilers myProfilers;

  public StudioProfilersTest(boolean useNewEventPipeline) {
    myIdeProfilerServices = new FakeIdeProfilerServices();
    myIdeProfilerServices.enableEventsPipeline(useNewEventPipeline);
    myNewEventPipeline = useNewEventPipeline;
  }

  @Before
  public void setUp() {
    myProfilerClient = new ProfilerClient(myGrpcServer.getChannel());
    myProfilers = new StudioProfilers(myProfilerClient, myIdeProfilerServices, myTimer);
  }

  @Test
  public void testSleepBeforeAppLaunched() {
    //Validate we start in the null stage.
    assertThat(myProfilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    myProfilers.setProcess(device, null);
    // Validate that just because we add a device, we still have not left the  null monitor stage.
    assertThat(myProfilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(myProfilers.getStageClass()).isSameAs(NullMonitorStage.class);

    // Pick a time to set the device to. Note that this value is arbitrary but the bug this tests
    // is exposed if this value is larger than nanoTime.
    long timeOnDevice = System.nanoTime() + 1000;
    myTimer.setCurrentTimeNs(timeOnDevice);

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    // Add a process and validate the stage goes to the monitor stage.
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Test that the process was attached correctly
    assertThat(myProfilers.getTimeline().isStreaming()).isTrue();
    // Test that the data range has not been inverted
    assertThat(myProfilers.getTimeline().getDataRange().isEmpty()).isFalse();
  }

  @Test
  public void testProfilerStageChange() {
    //Validate we start in the null stage.
    assertThat(myProfilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    myProfilers.setProcess(device, null);
    // Validate that just because we add a device, we still have not left the  null monitor stage.
    assertThat(myProfilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(myProfilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    // Add a process and validate the stage goes to the monitor stage.
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getStageClass()).isSameAs(StudioMonitorStage.class);
  }

  @Test
  public void testLateConnectionOfPreferredProcess() {
    final String PREFERRED_PROCESS = "Preferred";
    myProfilers.setPreferredProcess(null, PREFERRED_PROCESS, null);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getDevice()).isNull();
    assertThat(myProfilers.getProcess()).isNull();

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    myProfilers.setProcess(device, null);
    // We are waiting for the preferred process so the process should not be selected.
    assertThat(myProfilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(myProfilers.getProcess()).isNull();

    Common.Process preferred = createProcess(device.getDeviceId(), 21, PREFERRED_PROCESS, Common.Process.State.ALIVE);
    myTransportService.addProcess(device, preferred);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertThat(myProfilers.getDevice()).isEqualTo(device);
    assertThat(myProfilers.getProcess()).isEqualTo(preferred);
    assertThat(myProfilers.getProcesses()).hasSize(2);
    assertThat(myProfilers.getProcesses()).containsAllIn(ImmutableList.of(process, preferred));
  }

  @Test
  public void testSetPreferredProcessDoesNotProfileEarlierProcess() {
    final String PREFERRED_PROCESS = "Preferred";
    myProfilers.setPreferredProcess(null, PREFERRED_PROCESS, p -> p.getStartTimestampNs() > 5);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getDevice()).isNull();
    assertThat(myProfilers.getProcess()).isNull();

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process earlierProcess = Common.Process.newBuilder()
      .setDeviceId(device.getDeviceId())
      .setPid(20)
      .setName(PREFERRED_PROCESS)
      .setState(Common.Process.State.ALIVE)
      .setStartTimestampNs(5)
      .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
      .build();
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, earlierProcess);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, null);
    // The process start time is before the time we start looking for preferred process, so profiler should not have started.
    assertThat(myProfilers.getDevice()).isEqualTo(device);
    assertThat(myProfilers.getProcess()).isNull();

    Common.Process afterProcess = Common.Process.newBuilder()
      .setDeviceId(device.getDeviceId())
      .setPid(21)
      .setName(PREFERRED_PROCESS)
      .setState(Common.Process.State.ALIVE)
      .setStartTimestampNs(10)
      .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
      .build();
    myTransportService.addProcess(device, afterProcess);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getDevice()).isEqualTo(device);
    assertThat(myProfilers.getProcess()).isEqualTo(afterProcess);
    assertThat(myProfilers.getProcesses()).hasSize(2);
    assertThat(myProfilers.getProcesses()).containsAllIn(ImmutableList.of(earlierProcess, afterProcess));
  }

  @Test
  public void testDebuggableProcessNotReportedAsProfileable() {
    Assume.assumeTrue(myNewEventPipeline);

    Common.Device device = FAKE_DEVICE;
    myTransportService.addDevice(device);

    Common.Process debuggableEvent = FAKE_PROCESS.toBuilder()
      .setStartTimestampNs(5)
      .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
      .build();
    myTransportService.addProcess(device, debuggableEvent);

    Common.Process profileableEvent = debuggableEvent.toBuilder()
      .setStartTimestampNs(10)
      .setExposureLevel(Common.Process.ExposureLevel.PROFILEABLE)
      .build();
    myTransportService.addProcess(device, profileableEvent);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getDeviceProcessMap()).hasSize(1);
    assertThat(myProfilers.getDeviceProcessMap().get(device)).hasSize(1);
    assertThat(myProfilers.getDeviceProcessMap().get(device).get(0).getExposureLevel()).
      isEqualTo(Common.Process.ExposureLevel.DEBUGGABLE);
  }

  @Test
  public void testSetNullPreferredProcessDoesNotStartAutoProfiling() {
    final String PREFERRED_PROCESS = "Preferred";
    myProfilers.setPreferredProcess(null, null, p -> p.getStartTimestampNs() > 5);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getDevice()).isNull();
    assertThat(myProfilers.getProcess()).isNull();

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
    myProfilers.setProcess(device, null);
    assertThat(myProfilers.getDevice()).isEqualTo(device);
    assertThat(myProfilers.getProcess()).isNull();
    assertThat(myProfilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());
  }

  @Test
  public void testConnectionError() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE)
      .toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    myProfilers.setPreferredProcess("FakeDevice", "FakeProcess", null);
    // This should fail and not find any devices
    myTransportService.setThrowErrorOnGetDevices(true);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getDevice()).isNull();
    assertThat(myProfilers.getProcess()).isNull();

    // Server "is back up", try again
    myTransportService.setThrowErrorOnGetDevices(false);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(myProfilers.getProcess().getName()).isEqualTo("FakeProcess");
  }

  @Test
  public void testAlreadyConnected() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);
  }

  @Test
  public void testTimeResetOnConnectedDevice() {
    int nowInSeconds = 42;
    myTimer.setCurrentTimeNs(TimeUnit.SECONDS.toNanos(nowInSeconds));
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getTimeline().getDataRange().getMin()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds));
    assertThat(myProfilers.getTimeline().getDataRange().getMax()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds));

    // The timeline has reset in the previous tick, so we need to advance the current time to make sure the next tick advances data range.
    myTimer.setCurrentTimeNs(myTimer.getCurrentTimeNs() + FakeTimer.ONE_SECOND_IN_NS * 5);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS * 5);

    assertThat(myProfilers.getTimeline().getDataRange().getMin()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds));
    assertThat(myProfilers.getTimeline().getDataRange().getMax()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds + 5));
  }

  @Test
  public void TestDiscoverProfileableCommand() {
    Assume.assumeTrue(myNewEventPipeline);
    // Devices that have executed the DISCOVER_PROFILEABLE command.
    List<Long> discoveringStreamIds = myTransportService.getDiscoveringProfileableStreamIds();
    assertThat(discoveringStreamIds).isEmpty();

    // DISCOVER_PROFILEABLE command should NOT be called on S devices.
    Common.Device deviceS = createDevice(AndroidVersion.VersionCodes.S, "FakeDeviceS", Common.Device.State.ONLINE);
    myTransportService.addDevice(deviceS);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(discoveringStreamIds).isEmpty();

    // DISCOVER_PROFILEABLE command should be called on R devices.
    Common.Device deviceR = createDevice(AndroidVersion.VersionCodes.R, "FakeDeviceR", Common.Device.State.ONLINE);
    myTransportService.addDevice(deviceR);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(discoveringStreamIds).containsExactly(deviceR.getDeviceId());

    // DISCOVER_PROFILEABLE command should be called on Q devices.
    // It's OK if multiple devices are connected at the same time.
    Common.Device deviceQ1 = createDevice(AndroidVersion.VersionCodes.Q, "FakeDeviceQ1", Common.Device.State.ONLINE);
    myTransportService.addDevice(deviceQ1);
    Common.Device deviceQ2 = createDevice(AndroidVersion.VersionCodes.Q, "FakeDeviceQ2", Common.Device.State.ONLINE);
    myTransportService.addDevice(deviceQ2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(discoveringStreamIds.subList(1, 3)).containsExactly(deviceQ1.getDeviceId(), deviceQ2.getDeviceId());

    // DISCOVER_PROFILEABLE command should NOT be called on P devices.
    Common.Device deviceP = createDevice(AndroidVersion.VersionCodes.P, "FakeDeviceP", Common.Device.State.ONLINE);
    myTransportService.addDevice(deviceP);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(discoveringStreamIds).hasSize(3);

    // DISCOVER_PROFILEABLE command should be called if a supported device is disconnected and then connected again.
    deviceQ1 = deviceQ1.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    myTransportService.addDevice(deviceQ1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    deviceQ1 = deviceQ1.toBuilder().setState(Common.Device.State.ONLINE).build();
    myTransportService.addDevice(deviceQ1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(discoveringStreamIds).hasSize(4);
    assertThat(discoveringStreamIds.get(3)).isEqualTo(deviceQ1.getDeviceId());
  }


  @Test
  public void testAgentUnattachableAfterMaxRetries() {
    Assume.assumeTrue(myNewEventPipeline);
    ((BeginSession)myTransportService.getRegisteredCommand(Commands.Command.CommandType.BEGIN_SESSION))
      .setAgentStatus(AgentData.Status.UNSPECIFIED);
    myProfilers.getSessionsManager().endCurrentSession();
    myProfilers.getSessionsManager().beginSession(FAKE_DEVICE.getDeviceId(), FAKE_DEVICE, FAKE_PROCESS);

    for (int i = 0; i < AGENT_STATUS_MAX_RETRY_COUNT; i++) {
      assertThat(myProfilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNSPECIFIED);
      myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    }
    assertThat(myProfilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNATTACHABLE);

    // Ensures that if the agent becomes attached at a later point, the status will be correct.
    Common.AgentData agentData = Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build();
    long sessionStreamId = myProfilers.getSession().getStreamId();
    myTransportService.addEventToStream(sessionStreamId, Common.Event.newBuilder()
      .setPid(FAKE_PROCESS.getPid())
      .setKind(Common.Event.Kind.AGENT)
      .setAgentData(agentData)
      .build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.ATTACHED);
  }

  @Test
  public void testAgentStatusRetryCachedForSession() {
    Assume.assumeTrue(myNewEventPipeline);
    ((BeginSession)myTransportService.getRegisteredCommand(Commands.Command.CommandType.BEGIN_SESSION))
      .setAgentStatus(AgentData.Status.UNSPECIFIED);
    myProfilers.getSessionsManager().endCurrentSession();
    myProfilers.getSessionsManager().beginSession(FAKE_DEVICE.getDeviceId(), FAKE_DEVICE, FAKE_PROCESS);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Common.Session session1 = myProfilers.getSession();

    // Switch to a different process.
    Common.Process process2 = FAKE_PROCESS.toBuilder().setPid(FAKE_PROCESS.getPid() + 1).build();
    myProfilers.getSessionsManager().endCurrentSession();
    myProfilers.getSessionsManager().beginSession(FAKE_DEVICE.getDeviceId(), FAKE_DEVICE, process2);
    // Note the following tick will update the selected session and mySessionIdToAgentStatusRetryMap for session 2.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Common.Session session2 = myProfilers.getSession();
    int session2RetryCount = 1;  // the latest tick (two lines above) has updated the retry count.
    for (; session2RetryCount < AGENT_STATUS_MAX_RETRY_COUNT / 2; session2RetryCount++) {
      assertThat(myProfilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNSPECIFIED);
      myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    }

    // Switch back to the first session, agent status should remain unspecified because it is ended.
    // Note we cannot call 'setSession(session1)' because the session object in the session manager has been updated
    // when it ended, and session manager doesn't want to set to an out-of-date session.
    myProfilers.getSessionsManager().setSessionById(session1.getSessionId());
    for (int j = 0; j < AGENT_STATUS_MAX_RETRY_COUNT * 2; j++) {
      myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    }
    assertThat(myProfilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNSPECIFIED);

    // Switch back to the second session, we should only need another
    // (AGENT_STATUS_MAX_RETRY_COUNT - session2RetryCount) ticks to reach UNATTACHABLE.
    myProfilers.getSessionsManager().setSession(session2);
    for (; session2RetryCount < AGENT_STATUS_MAX_RETRY_COUNT; session2RetryCount++) {
      assertThat(myProfilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNSPECIFIED);
      myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    }
    assertThat(myProfilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNATTACHABLE);

    // Switch to the first session and back should give UNATTACHABLE immediately.
    myProfilers.getSessionsManager().setSessionById(session1.getSessionId());
    myProfilers.getSessionsManager().setSessionById(session2.getSessionId());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getAgentData().getStatus()).isEqualTo(AgentData.Status.UNATTACHABLE);
  }

  @Test
  public void testAgentAspectFiring() {
    AgentStatusAspectObserver observer = new AgentStatusAspectObserver();
    myProfilers.addDependency(observer).onChange(ProfilerAspect.AGENT, observer::AgentStatusChanged);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getAgentData()).isEqualTo(AgentData.getDefaultInstance());
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(0);

    // Test that status changes if no process is selected does nothing
    AgentData attachedResponse = AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build();
    myTransportService.setAgentStatus(attachedResponse);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getProcess()).isNull();
    assertThat(myProfilers.getAgentData()).isEqualTo(AgentData.getDefaultInstance());
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(0);

    // Test that agent status change fires after a process is selected.
    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Process process2 = createProcess(device.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process1);
    myTransportService.addProcess(device, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getProcess()).isEqualTo(process1);
    assertThat(myProfilers.getAgentData()).isEqualTo(attachedResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);

    // Test that manually setting a process fires an agent status change
    myProfilers.setProcess(device, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getProcess()).isSameAs(process2);
    assertThat(myProfilers.getAgentData()).isEqualTo(attachedResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(2);

    // Setting the same agent status should not trigger an aspect change.
    attachedResponse = AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build();
    myTransportService.setAgentStatus(attachedResponse);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getAgentData()).isEqualTo(attachedResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(2);
  }

  @Test
  public void testAgentAspectNotFiredWhenSettingSameDeviceProcess() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    AgentStatusAspectObserver observer = new AgentStatusAspectObserver();
    myProfilers.addDependency(observer).onChange(ProfilerAspect.AGENT, observer::AgentStatusChanged);

    // Test that the status changed is fired when the process first gets selected.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getDevice()).isSameAs(device);
    assertThat(myProfilers.getProcess()).isEqualTo(process);
    assertThat(myProfilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);

    // Test that resetting the same device/process would not trigger the status changed event.
    myProfilers.setProcess(device, process);
    assertThat(myProfilers.getDevice()).isSameAs(device);
    assertThat(myProfilers.getProcess()).isEqualTo(process);
    assertThat(myProfilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);
  }

  @Test
  public void testRestartedPreferredProcessNotSelected() {
    Assume.assumeTrue(myNewEventPipeline);
    //int nowInSeconds = 42;
    //myTransportService.setTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds));
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device =
      createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE).toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilers.setPreferredProcess("FakeDevice", process.getName(), null);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getProcess().getPid()).isEqualTo(20);
    assertThat(myProfilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);

    // Change the alive (active) process to DEAD, and create a new ALIVE process simulating a debugger restart.
    myTransportService.removeProcess(device, process);
    process = process.toBuilder()
      .setState(Common.Process.State.DEAD)
      .build();
    myTransportService.addProcess(device, process);

    // Verify the process is in the dead state.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getProcess().getPid()).isEqualTo(20);
    assertThat(myProfilers.getProcess().getState()).isEqualTo(Common.Process.State.DEAD);

    process = process.toBuilder()
      .setPid(21)
      .setState(Common.Process.State.ALIVE)
      .build();
    myTransportService.addProcess(device, process);


    // The profiler should not automatically selects the alive, preferred process again.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getProcess().getPid()).isEqualTo(20);
    assertThat(myProfilers.getProcess().getState()).isEqualTo(Common.Process.State.DEAD);
    assertThat(myProfilers.getAutoProfilingEnabled()).isFalse();

    // Re-enable auto-profiling should pick up the new process.
    myProfilers.setAutoProfilingEnabled(true);
    // We need a change in processes to trigger the pickup. In production, setAutoProfilingEnabled(true)
    // is called only by StudioProfilers.setPreferredProcess() which is called when (1) the app is deployed,
    // (2) profiler tool window is initialized, or (3) profiler window is reopened and the app was deployed
    // but not profiled before. We simulate (1) here.
    myTransportService.removeProcess(device, process); // for legacy pipeline
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTransportService.addProcess(device, process); // for unified pipeline
    process = process.toBuilder()
      .setPid(22)
      .setState(Common.Process.State.ALIVE)
      .build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getProcess().getPid()).isEqualTo(22);
    assertThat(myProfilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
  }

  @Test
  public void shouldNotSelectPreferredAfterUserSelectsOtherProcess() {
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE)
      .toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilers.setPreferredProcess("FakeDevice", process.getName(), null);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getProcess().getPid()).isEqualTo(20);
    assertThat(myProfilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);

    Common.Process otherProcess = createProcess(device.getDeviceId(), 21, "OtherProcess", Common.Process.State.ALIVE);
    myTransportService.addProcess(device, otherProcess);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, otherProcess);
    // The user selected the other process explicitly
    assertThat(myProfilers.getProcess().getPid()).isEqualTo(21);
    assertThat(myProfilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);

    // Should select the other process again
    myProfilers.setProcess(device, null);
    assertThat(myProfilers.getProcess().getPid()).isEqualTo(21);
    assertThat(myProfilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
  }

  @Test
  public void shouldOpenMemoryProfileStageIfStartupProfilingStarted() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);

    myTransportService.addEventToStream(device.getDeviceId(), Common.Event.newBuilder()
      .setPid(process.getPid())
      .setCommandId(1)
      .setKind(Common.Event.Kind.MEMORY_NATIVE_SAMPLE_STATUS)
      .setTimestamp(myTimer.getCurrentTimeNs())
      .setGroupId(myTimer.getCurrentTimeNs())
      .setMemoryNativeTrackingStatus(Memory.MemoryNativeTrackingData.newBuilder()
                                       .setStartTime(myTimer.getCurrentTimeNs())
                                       .setStatus(Memory.MemoryNativeTrackingData.Status.SUCCESS)
                                       .build())
      .build());
    // To make sure that StudioProfilers#update is called, which in a consequence polls devices and processes,
    // and starts a new session with the preferred process name.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getProcess().getPid()).isEqualTo(20);
    assertThat(myProfilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
    assertThat(myProfilers.getStage()).isInstanceOf(MainMemoryProfilerStage.class);
  }

  @Test
  public void shouldOpenCpuProfileStageIfStartupProfilingStarted() {
    Assume.assumeTrue(myNewEventPipeline);
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
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getProcess().getPid()).isEqualTo(20);
    assertThat(myProfilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
    assertThat(myProfilers.getStage()).isInstanceOf(CpuProfilerStage.class);
  }

  @Test
  public void testProcessStateChangesShouldNotTriggerStageChange() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getProcess().getPid()).isEqualTo(20);
    assertThat(Common.Process.State.ALIVE).isEqualTo(myProfilers.getProcess().getState());

    AspectObserver observer = new AspectObserver();
    myProfilers.addDependency(observer).onChange(ProfilerAspect.STAGE, () -> {
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
    assertThat(myProfilers.getProcess().getPid()).isEqualTo(20);
    assertThat(Common.Process.State.DEAD).isEqualTo(myProfilers.getProcess().getState());
  }

  @Test
  public void timelineShouldBeStreamingWhenProcessIsSelected() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getTimeline().isStreaming()).isTrue();
  }

  @Test
  public void timelineShouldStopStreamingWhenRangeIsSelected() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    StreamingTimeline timeline = myProfilers.getTimeline();
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
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    assertThat(myProfilers.getDevice()).isEqualTo(device);
    assertThat(myProfilers.getProcess()).isEqualTo(process);

    // Connect a new device, and marks the old one as disconnected
    Common.Device dead_device = device.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process2 = createProcess(device2.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myTransportService.updateDevice(device, dead_device);
    myTransportService.addDevice(device2);
    myTransportService.addProcess(device2, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connecting an online device should not override previously selection automatically
    assertThat(myProfilers.getDevice()).isEqualTo(dead_device);
    assertThat(myProfilers.getProcess()).isEqualTo(process);
  }

  @Test
  public void preferredDeviceShouldNotOverrideSelectedDevice() {
    myProfilers.setPreferredProcess("Manufacturer Model", "ProcessName", null);

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
    assertThat(myProfilers.getDevice()).isEqualTo(preferredDevice);

    // Selecting device manually should keep it selected
    myProfilers.setProcess(device, null);
    assertThat(myProfilers.getDevice()).isEqualTo(device);

    // Changing states on the preferred device should have no effects.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getDevice()).isEqualTo(device);

    Common.Device offlinePreferredDevice = preferredDevice.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    myTransportService.updateDevice(preferredDevice, offlinePreferredDevice);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getDevice()).isEqualTo(device);

    myTransportService.updateDevice(offlinePreferredDevice, preferredDevice);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getDevice()).isEqualTo(device);
  }

  @Test
  public void preferredDeviceHasPriority() {
    myProfilers.setPreferredProcess("Manufacturer Model", "PreferredProcess", null);

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
    assertThat(myProfilers.getDevice()).isNull();
    assertThat(myProfilers.getProcess()).isNull();

    // Turn the preferred device online and it should be selected.
    Common.Device preferredDevice = offlinePreferredDevice.toBuilder().setState(Common.Device.State.ONLINE).build();
    myTransportService.updateDevice(offlinePreferredDevice, preferredDevice);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getDevice()).isEqualTo(preferredDevice);
    assertThat(myProfilers.getProcess()).isEqualTo(preferredProcess);

    Common.Device preferredDevice2 = createDevice(AndroidVersion.VersionCodes.BASE, "PreferredDevice2", Common.Device.State.ONLINE);
    preferredDevice2 = preferredDevice2.toBuilder().setManufacturer("Manufacturer2").setModel("Model2").build();
    Common.Process preferredProcess2 = createProcess(preferredDevice2.getDeviceId(), 22, "PreferredProcess2", Common.Process.State.ALIVE);
    myTransportService.addDevice(preferredDevice2);
    myTransportService.addProcess(preferredDevice2, preferredProcess2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getDevice()).isEqualTo(preferredDevice);
    assertThat(myProfilers.getProcess()).isEqualTo(preferredProcess);

    // Updating the preferred device should switch over.
    myProfilers.setPreferredProcess("Manufacturer2 Model2", "PreferredProcess", null);
    // We need a change in processes to trigger the pickup. In production, setAutoProfilingEnabled(true)
    // is called only by StudioProfilers.setPreferredProcess() which is called when (1) the app is deployed,
    // (2) profiler tool window is initialized, or (3) profiler window is reopened and the app was deployed
    // but not profiled before. We simulate (1) here.
    process = preferredProcess2.toBuilder()
      .setState(Common.Process.State.DEAD).build();
    myTransportService.addProcess(preferredDevice2, process);
    myTransportService.removeProcess(preferredDevice2, preferredProcess2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myTransportService.addProcess(preferredDevice2, preferredProcess2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getDevice()).isEqualTo(preferredDevice2);
    assertThat(myProfilers.getProcess()).isNull();
  }

  @Test
  public void keepSelectedDeviceAfterDisconnectingAllDevices() {
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

    myProfilers.setProcess(device2, null);
    assertThat(myProfilers.getDevice()).isEqualTo(device2);
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
    assertThat(myProfilers.getDevice().getSerial()).isEqualTo("FakeDevice2");
    // Make sure the device is disconnected
    assertThat(myProfilers.getDevice().getState()).isEqualTo(Common.Device.State.DISCONNECTED);
  }

  @Test
  public void testProfileOneProcessAtATime() {
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Process process2 = createProcess(device1.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myTransportService.addDevice(device1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    myTransportService.addProcess(device1, process1);
    myTransportService.addProcess(device1, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device1, process1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getProcess()).isEqualTo(process1);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);

    // Switch to another process.
    myProfilers.setProcess(device1, process2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(myProfilers.getProcess()).isEqualTo(process2);

    // Connect a new device with a process.
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process3 = createProcess(device2.getDeviceId(), 22, "FakeProcess3", Common.Process.State.ALIVE);
    myTransportService.addDevice(device2);
    myTransportService.addProcess(device2, process3);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Switch to the new device + process
    myProfilers.setProcess(device2, process3);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(myProfilers.getProcess()).isEqualTo(process3);

    // Update device2 state to disconnect
    Common.Device disconnectedDevice2 = device2.toBuilder()
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    myTransportService.updateDevice(device2, disconnectedDevice2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);

    // Switch back to the first device.
    myProfilers.setProcess(device1, process1);
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
    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process1);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getDevice()).isEqualTo(device);
    assertThat(myProfilers.getProcess()).isEqualTo(process1);
    assertThat(myNewEventPipeline ? myTransportService.getAgentAttachCalled() : myProfilerService.getAgentAttachCalled()).isTrue();
  }

  @Test
  public void testAttachAgentNotCalledPreO() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.N, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process1);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process1);

    assertThat(myProfilers.getDevice()).isEqualTo(device);
    assertThat(myProfilers.getProcess()).isEqualTo(process1);
    assertThat(myNewEventPipeline ? myTransportService.getAgentAttachCalled() : myProfilerService.getAgentAttachCalled()).isFalse();
  }

  /**
   * We need to account for an scenario where perfd reinstantiates and needs to pass a new client socket to the app. Hence we make the
   * same attach agent call from Studio side and let perfd handles the rest.
   */
  @Test
  public void testAttachAgentEvenIfAlreadyAttached() {
    AgentData attachedResponse = AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build();
    myTransportService.setAgentStatus(attachedResponse);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process1);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getDevice()).isEqualTo(device);
    assertThat(myProfilers.getProcess()).isEqualTo(process1);
    assertThat(myNewEventPipeline ? myTransportService.getAgentAttachCalled() : myProfilerService.getAgentAttachCalled()).isTrue();
  }

  @Test
  public void testProfilingStops() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE)
      .toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    myTransportService.addProcess(device, process);

    myProfilers.setPreferredProcess("FakeDevice", "FakeProcess", null);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getProcess()).isEqualTo(process);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(myProfilers.getProcess()).isEqualTo(process);
    assertThat(myTimer.isRunning()).isTrue();

    // Stop the profiler
    myProfilers.stop();

    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    assertThat(myProfilers.getProcess()).isNull();
    assertThat(myProfilers.getDevice()).isNull();
    assertThat(myTimer.isRunning()).isFalse();
  }

  @Test
  public void testNullDeviceKeepsPreviousSession() {
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice1", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    myTransportService.addDevice(device1);
    myTransportService.addProcess(device1, process);
    myTransportService.addDevice(device2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device1, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Common.Session session = myProfilers.getSession();
    assertThat(myProfilers.getDevice()).isEqualTo(device1);
    assertThat(myProfilers.getProcess()).isEqualTo(process);
    assertThat(session).isNotEqualTo(Common.Session.getDefaultInstance());
    assertThat(myProfilers.getStageClass()).isEqualTo(StudioMonitorStage.class);

    // Setting device and process to null should maintain the current session.
    myProfilers.setProcess(null, null);
    assertThat(myProfilers.getDevice()).isEqualTo(null);
    assertThat(myProfilers.getProcess()).isEqualTo(null);
    assertThat(myProfilers.getSession().getSessionId()).isEqualTo(session.getSessionId());
    assertThat(myProfilers.getStageClass()).isEqualTo(StudioMonitorStage.class);
  }

  @Test
  public void testUnsupportedDeviceShowsNullStage() {
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
    myProfilers.setPreferredProcess(deviceName, "FakeProcess", null);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getDevice()).isEqualTo(device);
    assertThat(myProfilers.getProcess()).isNull();
    assertThat(myProfilers.getStageClass()).isEqualTo(NullMonitorStage.class);
    assertThat(((NullMonitorStage)myProfilers.getStage()).getUnsupportedReason()).isEqualTo(unsupportedReason);
  }

  @Test
  public void testProfilingStopsWithLiveAllocationEnabled() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);

    assertThat(myTimer.isRunning()).isTrue();

    assertThat(myProfilers.getDevice()).isEqualTo(device);
    assertThat(myProfilers.getProcess()).isEqualTo(process);

    // Stop the profiler
    myProfilers.stop();

    assertThat(myTimer.isRunning()).isFalse();
    assertThat(myProfilers.getProcess()).isEqualTo(null);
    assertThat(myProfilers.getDevice()).isEqualTo(null);
  }

  @Test
  public void testStoppingTwice() {
    // Should be modified when STAGE aspect is fired.
    boolean[] stageAspectTriggered = {false};
    myProfilers.addDependency(new AspectObserver())
      .onChange(ProfilerAspect.STAGE, () -> stageAspectTriggered[0] = true);

    // Check profiler is not stopped.
    assertThat(myProfilers.isStopped()).isFalse();
    assertThat(myTimer.isRunning()).isTrue();
    // Stop the profiler
    myProfilers.stop();
    // Profiler should have stopped and STAGE is supposed to have been fired.
    assertThat(stageAspectTriggered[0]).isTrue();

    // Check profiler is stopped.
    assertThat(myProfilers.isStopped()).isTrue();
    assertThat(myTimer.isRunning()).isFalse();
    stageAspectTriggered[0] = false;
    // Try to stop the profiler again.
    myProfilers.stop();
    // Profiler was already stopped and STAGE is not supposed to have been fired.
    assertThat(stageAspectTriggered[0]).isFalse();
  }

  @Test
  public void testBeginAndEndSessionOnProcessChange() {
    assertThat(myProfilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a device without processes. Session should be null.
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    assertThat(myProfilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a process, which should trigger the session to start.
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(myProfilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(myProfilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);

    // Mark the process as dead, which should ends the session.
    myTransportService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(myProfilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(myProfilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testBeginAndEndSessionOnDeviceChange() {
    assertThat(myProfilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a device with process. Session should start immediately
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(myProfilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(myProfilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);

    // Killing the device should stop the session
    myTransportService.removeProcess(device, process);
    Common.Device deadDevice = device.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    Common.Process deadProcess = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTransportService.addDevice(deadDevice);
    myTransportService.addProcess(deadDevice, deadProcess);
    myTimer.setCurrentTimeNs(FakeTimer.ONE_SECOND_IN_NS);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(myProfilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(myProfilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testSessionDoesNotAutoStartOnSameProcess() {
    assertThat(myProfilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // End the session.
    myTransportService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Common.Session session = myProfilers.getSession();
    assertThat(session).isNotNull();
    assertThat(session.getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(session.getPid()).isEqualTo(process.getPid());

    // The same process coming alive should not start a new session automatically.
    myTransportService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.ALIVE).build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getSession()).isEqualTo(session);
  }

  @Test
  public void testNewSessionResetsStage() {
    assertThat(myProfilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getSession()).isNotNull();
    assertThat(myProfilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(myProfilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(myProfilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);
    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);

    // Goes into a different stage
    myProfilers.setStage(new FakeStage(myProfilers));
    assertThat(myProfilers.getStage()).isInstanceOf(FakeStage.class);

    // Ending a session should not leave the stage automatically.
    myTransportService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getSession()).isNotNull();
    assertThat(myProfilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(myProfilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(myProfilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
    assertThat(myProfilers.getStage()).isInstanceOf(FakeStage.class);

    // Restarting a session on the same process should re-enter the StudioMonitorStage
    myTransportService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.ALIVE).build();
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(myProfilers.getSession()).isNotNull();
    assertThat(myProfilers.getSession().getStreamId()).isEqualTo(device.getDeviceId());
    assertThat(myProfilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(myProfilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);
    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);
  }

  @Test
  public void testGetDirectStagesReturnsOnlyExpectedStages() {
    // When energy flag is enabled and device is pre-O, GetDirectStages does not return Energy stage.
    myIdeProfilerServices.enableEnergyProfiler(true);
    Common.Device deviceNougat = createDevice(AndroidVersion.VersionCodes.N_MR1, "FakeDeviceN", Common.Device.State.ONLINE);
    myTransportService.addDevice(deviceNougat);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(deviceNougat, null);
    assertThat(myProfilers.getDevice().getSerial()).isEqualTo("FakeDeviceN");

    assertThat(myProfilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MainMemoryProfilerStage.class).inOrder();

    // When energy flag is enabled and device is O, GetDirectStages returns Energy stage.
    Common.Device deviceOreo = createDevice(AndroidVersion.VersionCodes.O, "FakeDeviceO", Common.Device.State.ONLINE);
    myTransportService.addDevice(deviceOreo);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(deviceOreo, null);
    assertThat(myProfilers.getDevice().getSerial()).isEqualTo("FakeDeviceO");

    assertThat(myProfilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MainMemoryProfilerStage.class,
      EnergyProfilerStage.class).inOrder();

    // When energy flag is disabled and device is O, GetDirectStages does not return Energy stage.
    myIdeProfilerServices.enableEnergyProfiler(false);
    assertThat(myProfilers.getDevice().getSerial()).isEqualTo("FakeDeviceO");

    assertThat(myProfilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MainMemoryProfilerStage.class).inOrder();

    // When custom event flag is enabled and device is O, GetDirectStages returns Custom Event stage.
    myIdeProfilerServices.enableCustomEventVisualization(true);
    assertThat(myProfilers.getDevice().getSerial()).isEqualTo("FakeDeviceO");

    assertThat(myProfilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MainMemoryProfilerStage.class,
      CustomEventProfilerStage.class).inOrder();

    // When custom event flag is disabled and device is O, GetDirectStages does not return Custom Event stage.
    myIdeProfilerServices.enableCustomEventVisualization(false);
    assertThat(myProfilers.getDevice().getSerial()).isEqualTo("FakeDeviceO");

    assertThat(myProfilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MainMemoryProfilerStage.class).inOrder();
  }

  @Test
  public void testGetDirectStageReturnsEnergyOnlyForPostOSession() {
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
    myProfilers.getSessionsManager().setSession(myProfilers.getSessionsManager().getSessionArtifacts().get(0).getSession());
    assertThat(myProfilers.getSessionsManager().getSelectedSessionMetaData().getJvmtiEnabled()).isFalse();

    assertThat(myProfilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MainMemoryProfilerStage.class).inOrder();

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
    myProfilers.getSessionsManager().setSession(myProfilers.getSessionsManager().getSessionArtifacts().get(0).getSession());
    assertThat(myProfilers.getSessionsManager().getSelectedSessionMetaData().getJvmtiEnabled()).isTrue();

    assertThat(myProfilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MainMemoryProfilerStage.class,
      EnergyProfilerStage.class).inOrder();

    // When energy flag is disabled and the session is pre-O, GetDirectStages does not return Energy stage.
    myIdeProfilerServices.enableEnergyProfiler(false);
    assertThat(myProfilers.getSessionsManager().getSelectedSessionMetaData().getJvmtiEnabled()).isTrue();
    assertThat(myProfilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MainMemoryProfilerStage.class).inOrder();
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
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess (phone)", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getSession()).isNotEqualTo(Common.Session.getDefaultInstance());
    assertThat(myProfilers.getSelectedAppName()).isEqualTo("FakeProcess");
  }

  @Test
  public void testSelectedAppNameFromProcessWhenNoSession() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess (phone)", Common.Process.State.ALIVE);
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);

    myProfilers.getSessionsManager().setSession(Common.Session.getDefaultInstance());
    assertThat(myProfilers.getSelectedAppName()).isEqualTo("FakeProcess");
  }

  @Test
  public void testSelectedAppNameWhenNoProcessAndNoSession() {
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myTransportService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());
    assertThat(myProfilers.getProcess()).isNull();
    assertThat(myProfilers.getSelectedAppName()).isEmpty();
  }

  @Test
  public void testSessionViewRangeCaches() {
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
    myProfilers.getSessionsManager().setSession(myProfilers.getSessionsManager().getSessionArtifacts().get(0).getSession());
    assertThat(myProfilers.getTimeline().getViewRange().getMin()).isWithin(0).of(-StreamingTimeline.DEFAULT_VIEW_LENGTH_US);
    assertThat(myProfilers.getTimeline().getViewRange().getMax()).isWithin(0).of(0);
    assertThat(myProfilers.getTimeline().isStreaming()).isTrue();
    assertThat(myProfilers.getTimeline().isPaused()).isFalse();
    myProfilers.getTimeline().getViewRange().set(viewRangeMin, viewRangeMax);

    // selecting a finished session without a view range cache should use the entire data range
    myProfilers.getSessionsManager().setSession(myProfilers.getSessionsManager().getSessionArtifacts().get(1).getSession());
    assertThat(myProfilers.getTimeline().getViewRange().getMin()).isWithin(0).of(TimeUnit.SECONDS.toMicros(1));
    assertThat(myProfilers.getTimeline().getViewRange().getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(2));
    assertThat(myProfilers.getTimeline().isStreaming()).isFalse();
    assertThat(myProfilers.getTimeline().isPaused()).isTrue();
    myProfilers.getTimeline().getViewRange().set(viewRangeMin, viewRangeMax);

    // Navigate back to the ongoing session should still use the default zoom
    myProfilers.getSessionsManager().setSession(myProfilers.getSessionsManager().getSessionArtifacts().get(0).getSession());
    assertThat(myProfilers.getTimeline().getViewRange().getMin()).isWithin(0).of(-StreamingTimeline.DEFAULT_VIEW_LENGTH_US);
    assertThat(myProfilers.getTimeline().getViewRange().getMax()).isWithin(0).of(0);
    assertThat(myProfilers.getTimeline().isStreaming()).isTrue();
    assertThat(myProfilers.getTimeline().isPaused()).isFalse();

    // Navigate again to the finished session should use the last view range
    myProfilers.getSessionsManager().setSession(myProfilers.getSessionsManager().getSessionArtifacts().get(1).getSession());
    assertThat(myProfilers.getTimeline().getViewRange().getMin()).isWithin(0).of(viewRangeMin);
    assertThat(myProfilers.getTimeline().getViewRange().getMax()).isWithin(0).of(viewRangeMax);
    assertThat(myProfilers.getTimeline().isStreaming()).isFalse();
    assertThat(myProfilers.getTimeline().isPaused()).isTrue();

    // Arbitrarily setting the view range to something beyond the data range should force the timeline to clamp to data range.
    myProfilers.getTimeline().getViewRange().set(TimeUnit.SECONDS.toMicros(-10), TimeUnit.SECONDS.toMicros(10));
    myProfilers.getSessionsManager().setSession(Common.Session.getDefaultInstance());
    myProfilers.getSessionsManager().setSession(myProfilers.getSessionsManager().getSessionArtifacts().get(1).getSession());
    assertThat(myProfilers.getTimeline().getViewRange().getMin()).isWithin(0).of(TimeUnit.SECONDS.toMicros(1));
    assertThat(myProfilers.getTimeline().getViewRange().getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(2));
    assertThat(myProfilers.getTimeline().isStreaming()).isFalse();
    assertThat(myProfilers.getTimeline().isPaused()).isTrue();
  }

  @Test
  public void testMultipleUpdateTicksShouldNotChangeSession() {
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

    myProfilers.getSessionsManager().setSession(myProfilers.getSessionsManager().getSessionArtifacts().get(0).getSession());
    assertThat(myProfilers.getSessionsManager().getSelectedSession().getSessionId()).isEqualTo(ongoingSession.getSessionId());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getSessionsManager().getSelectedSession().getSessionId()).isEqualTo(ongoingSession.getSessionId());

    myProfilers.getSessionsManager().setSession(myProfilers.getSessionsManager().getSessionArtifacts().get(1).getSession());
    assertThat(myProfilers.getSessionsManager().getSelectedSession().getSessionId()).isEqualTo(finishedSession.getSessionId());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getSessionsManager().getSelectedSession().getSessionId()).isEqualTo(finishedSession.getSessionId());
  }

  @Test
  public void runAsyncResumesWithIntermediateValue() throws InterruptedException {
    int[] box = {0};
    CountDownLatch latch = new CountDownLatch(1);
    myIdeProfilerServices.runAsync(() -> 1 + 2,
                                   n -> {
                                     box[0] = n;
                                     latch.countDown();
                                   });
    assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(box[0]).isEqualTo(3);
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
      .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
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

  private static class FakeStage extends StreamingStage {
    private FakeStage(@NotNull StudioProfilers myProfilers) {
      super(myProfilers);
    }

    @Override
    public void enter() {
    }

    @Override
    public void exit() {
    }

    @Override
    public AndroidProfilerEvent.Stage getStageType() {
      return AndroidProfilerEvent.Stage.UNKNOWN_STAGE;
    }
  }
}
