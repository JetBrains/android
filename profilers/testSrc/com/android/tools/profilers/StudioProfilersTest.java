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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerTestUtils;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public final class StudioProfilersTest {
  private final FakeProfilerService myProfilerService = new FakeProfilerService(false);
  @Rule public FakeGrpcServer myGrpcServer = new FakeGrpcServer("StudioProfilerTestChannel", myProfilerService);

  @Before
  public void setup() {
    myProfilerService.reset();
  }

  @Test
  public void testVersion() throws Exception {
    Profiler.VersionResponse response =
      myGrpcServer.getClient().getProfilerClient().getVersion(Profiler.VersionRequest.getDefaultInstance());
    assertEquals(FakeProfilerService.VERSION, response.getVersion());
  }

  @Test
  public void testClearedOnMonitorStage() throws Exception {
    StudioProfilers profilers = getProfilersWithDeviceAndProcess();
    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());

    profilers.setStage(new CpuProfilerStage(profilers));
    profilers.getTimeline().getSelectionRange().set(10, 10);
    profilers.setMonitoringStage();

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());
  }

  @Test
  public void testProfilerModeChange() throws Exception {
    StudioProfilers profilers = getProfilersWithDeviceAndProcess();
    assertEquals(ProfilerMode.NORMAL, profilers.getMode());
    CpuProfilerStage stage = new CpuProfilerStage(profilers);
    profilers.setStage(stage);
    assertEquals(ProfilerMode.NORMAL, profilers.getMode());
    stage.setAndSelectCapture(CpuProfilerTestUtils.getValidCapture());
    assertEquals(ProfilerMode.EXPANDED, profilers.getMode());
    profilers.setMonitoringStage();
    assertEquals(ProfilerMode.NORMAL, profilers.getMode());
  }

  @Test
  public void testSleepBeforeAppLaunched() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    //Validate we start in the null stage.
    assertEquals(NullMonitorStage.class, profilers.getStageClass());

    Profiler.Device device = Profiler.Device.newBuilder()
      .setSerial("FakeDevice")
      .setState(Profiler.Device.State.ONLINE)
      .build();
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    // Validate that just because we add a device, we still have not left the  null monitor stage.
    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertEquals(NullMonitorStage.class, profilers.getStageClass());

    // Pick a time to set the device to. Note that this value is arbitrary but the bug this tests
    // is exposed if this value is larger than nanoTime.
    long timeOnDevice = System.nanoTime() + 1000;
    myProfilerService.setTimestampNs(timeOnDevice);

    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setName("FakeProcess")
      .setStartTimestampNs(timeOnDevice)
      .setState(Profiler.Process.State.ALIVE)
      .build();

    // Add a process and validate the stage goes to the monitor stage.
    myProfilerService.addProcess(session, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    // Test that the process was attached correctly
    assertTrue(profilers.getTimeline().isStreaming());
    // Test that the data range has not been inverted
    assertFalse(profilers.getTimeline().getDataRange().isEmpty());
  }

  @Test
  public void testProfilerStageChange() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    //Validate we start in the null stage.
    assertEquals(NullMonitorStage.class, profilers.getStageClass());

    Profiler.Device device = Profiler.Device.newBuilder()
      .setSerial("FakeDevice")
      .setState(Profiler.Device.State.ONLINE)
      .build();
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    // Validate that just because we add a device, we still have not left the  null monitor stage.
    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertEquals(NullMonitorStage.class, profilers.getStageClass());

    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setName("FakeProcess")
      .setState(Profiler.Process.State.ALIVE)
      .build();

    // Add a process and validate the stage goes to the monitor stage.
    myProfilerService.addProcess(session, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(StudioMonitorStage.class, profilers.getStageClass());

    // Add a second device with no processes, and select that device.
    device = Profiler.Device.newBuilder().setSerial("FakeDevice2").build();
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    assertEquals(NullMonitorStage.class, profilers.getStageClass());
  }

  @Test
  public void testLateConnectionOfPreferredProcess() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertNull(profilers.getDevice());
    assertNull(profilers.getProcess());

    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertNull(profilers.getProcess());
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();

    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setName("FakeProcess")
      .setState(Profiler.Process.State.ALIVE)
      .build();
    myProfilerService.addProcess(session, process);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertEquals("FakeProcess", profilers.getProcess().getName());

    profilers.setPreferredProcessName("Preferred");

    Profiler.Process preferred = Profiler.Process.newBuilder()
      .setPid(20)
      .setName("Preferred")
      .setState(Profiler.Process.State.ALIVE)
      .build();
    myProfilerService.addProcess(session, preferred);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertEquals("Preferred", profilers.getProcess().getName());

    assertEquals(2, profilers.getProcesses().size());
    assertTrue(profilers.getProcesses().containsAll(ImmutableList.of(process, preferred)));
  }

  @Test
  public void testConnectionError() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setName("FakeProcess")
      .setState(Profiler.Process.State.ALIVE)
      .build();
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();

    myProfilerService.addDevice(device);
    myProfilerService.addProcess(session, process);

    // This should fail and not find any devices
    myProfilerService.setThrowErrorOnGetDevices(true);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertNull(profilers.getDevice());
    assertNull(profilers.getProcess());

    // Server "is back up", try again
    myProfilerService.setThrowErrorOnGetDevices(false);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertEquals("FakeProcess", profilers.getProcess().getName());
  }

  @Test
  public void testAlreadyConnected() throws Exception {
    FakeTimer timer = new FakeTimer();
    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
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

    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertTrue(profilers.getStage() instanceof StudioMonitorStage);
  }

  @Test
  public void testTimeResetOnConnectedDevice() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    int nowInSeconds = 42;
    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds));

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .setStartTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds))
      .build();
    myProfilerService.addDevice(device);
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myProfilerService.addProcess(session, process);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    int dataNow = nowInSeconds - StudioProfilers.TIMELINE_BUFFER;
    assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMin(), 0.001);
    // Because we check for System.nanotime when we update devices, we need to set the delta for the test to account for the time
    // it takes for the execution path to go from setDevice, to setProcess where the timeline gets reset and we calculate the deice time.
    // same with below.
    assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMax(), TimeUnit.MILLISECONDS.toMicros(10));

    timer.tick(FakeTimer.ONE_SECOND_IN_NS * 5);

    assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMin(), 0.001);
    assertEquals(TimeUnit.SECONDS.toMicros(dataNow + 5), profilers.getTimeline().getDataRange().getMax(),
                 TimeUnit.MILLISECONDS.toMicros(10));
  }

  @Test
  public void testAgentAspectFiring() throws Exception {
    FakeTimer timer = new FakeTimer();
    AgentStatusAspectObserver observer = new AgentStatusAspectObserver();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    profilers.addDependency(observer).onChange(ProfilerAspect.AGENT, observer::AgentStatusChanged);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertFalse(profilers.isAgentAttached());
    assertEquals(0, observer.getAgentStatusChangedCount());

    // Test that status changes if no process is selected does nothing
    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertNull(profilers.getProcess());
    assertFalse(profilers.isAgentAttached());
    assertEquals(0, observer.getAgentStatusChangedCount());

    // Test that agent status change fires after a process is selected.
    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
    Profiler.Process process1 = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess1")
      .build();
    Profiler.Process process2 = Profiler.Process.newBuilder()
      .setPid(21)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess2")
      .build();
    myProfilerService.addDevice(device);
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myProfilerService.addProcess(session, process1);
    myProfilerService.addProcess(session, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(process1, profilers.getProcess());
    assertTrue(profilers.isAgentAttached());
    assertEquals(1, observer.getAgentStatusChangedCount());

    // Test that manually setting a process fires an agent status change
    profilers.setProcess(process2);
    assertEquals(process2, profilers.getProcess());
    assertTrue(profilers.isAgentAttached());
    assertEquals(2, observer.getAgentStatusChangedCount());

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.DETACHED);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertFalse(profilers.isAgentAttached());
    assertEquals(3, observer.getAgentStatusChangedCount());
  }

  @Test
  public void testAgentAspectNotFiredWhenSettingSameDeviceProcess() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
    Profiler.Process process1 = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess1")
      .build();
    myProfilerService.addDevice(device);
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myProfilerService.addProcess(session, process1);

    AgentStatusAspectObserver observer = new AgentStatusAspectObserver();
    profilers.addDependency(observer).onChange(ProfilerAspect.AGENT, observer::AgentStatusChanged);

    // Test that the status changed is fired when the process first gets selected.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(device, profilers.getDevice());
    assertEquals(process1, profilers.getProcess());
    assertFalse(profilers.isAgentAttached());
    assertEquals(1, observer.getAgentStatusChangedCount());

    // Test that resetting the same device/process would not trigger the status changed event.
    profilers.setDevice(device);
    profilers.setProcess(process1);
    assertEquals(device, profilers.getDevice());
    assertEquals(process1, profilers.getProcess());
    assertFalse(profilers.isAgentAttached());
    assertEquals(1, observer.getAgentStatusChangedCount());
  }

  @Test
  public void testProcessRestartedSetsAliveAsActive() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    int nowInSeconds = 42;
    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds));

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .setStartTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds))
      .build();
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();

    profilers.setPreferredProcessName(process.getName());
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(session, process);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(20, profilers.getProcess().getPid());
    assertEquals(profilers.getProcess().getState(), Profiler.Process.State.ALIVE);

    // Change the alive (active) process to DEAD, and create a new ALIVE process simulating a debugger restart.
    myProfilerService.removeProcess(session, process);
    process = process.toBuilder()
      .setState(Profiler.Process.State.DEAD)
      .build();
    myProfilerService.addProcess(session, process);

    //Verify the process is in the dead state.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(20, profilers.getProcess().getPid());
    assertEquals(profilers.getProcess().getState(), Profiler.Process.State.DEAD);

    process = process.toBuilder()
      .setPid(21)
      .setState(Profiler.Process.State.ALIVE)
      .build();
    myProfilerService.addProcess(session, process);

    // Expect new process to have proper PID, and state.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(21, profilers.getProcess().getPid());
    assertEquals(profilers.getProcess().getState(), Profiler.Process.State.ALIVE);
  }

  @Test
  public void testProcessStateChangesShouldNotTriggerStageChange() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(session, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(20, profilers.getProcess().getPid());
    assertEquals(profilers.getProcess().getState(), Profiler.Process.State.ALIVE);

    AspectObserver observer = new AspectObserver();
    profilers.addDependency(observer).onChange(ProfilerAspect.STAGE, () -> {
      assert false;
    });
    // Change the alive (active) process to DEAD
    myProfilerService.removeProcess(session, process);
    process = process.toBuilder()
      .setState(Profiler.Process.State.DEAD)
      .build();
    myProfilerService.addProcess(session, process);

    //Verify the process is in the dead state.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(20, profilers.getProcess().getPid());
    assertEquals(profilers.getProcess().getState(), Profiler.Process.State.DEAD);
  }

  @Test
  public void timelineShouldBeStreamingWhenProcessIsSelected() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(session, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertTrue(profilers.getTimeline().isStreaming());
  }

  @Test
  public void newOnlineDeviceShouldBeSelectedIfCurrentIsNotOnline() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(session, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertEquals(process, profilers.getProcess());

    // Update device state to disconnect
    Profiler.Device disconnectedDevice = Profiler.Device.newBuilder()
      .setSerial(device.getSerial())
      .setState(Profiler.Device.State.DISCONNECTED)
      .build();
    myProfilerService.updateDevice(session, device, disconnectedDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect a new device
    Profiler.Device device2 = Profiler.Device.newBuilder().setSerial("FakeDevice2").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process2 = Profiler.Process.newBuilder()
      .setPid(3039)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess2")
      .build();
    Common.Session session2 = Common.Session.newBuilder()
      .setBootId(device2.getBootId())
      .setDeviceSerial(device2.getSerial())
      .build();
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(session2, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertEquals("FakeDevice2", profilers.getDevice().getSerial());
    assertEquals(process2, profilers.getProcess());
  }

  @Test
  public void onlineDeviceShouldNotOverrideExplicitlySetOfflineDevice() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.DISCONNECTED).build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(session, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect a new device
    Profiler.Device device2 = Profiler.Device.newBuilder().setSerial("FakeDevice2").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process2 = Profiler.Process.newBuilder()
      .setPid(3039)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess2")
      .build();
    Common.Session session2 = Common.Session.newBuilder()
      .setBootId(device2.getBootId())
      .setDeviceSerial(device2.getSerial())
      .build();
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(session2, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connecting an online device should override previously selection if it was not online
    assertEquals("FakeDevice2", profilers.getDevice().getSerial());
    assertEquals(process2, profilers.getProcess());

    // Explicitly set device
    profilers.setDevice(device);
    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertEquals(process, profilers.getProcess());
  }

  @Test
  public void deviceWithAliveProcessesHasPreference() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(1234)
      .setState(Profiler.Process.State.DEAD)
      .setName("FakeProcess")
      .build();
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(session, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect a device with a process that can be profiled
    Profiler.Device device2 = Profiler.Device.newBuilder().setSerial("FakeDevice2").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process2 = Profiler.Process.newBuilder()
      .setPid(3039)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess2")
      .build();
    Common.Session session2 = Common.Session.newBuilder()
      .setBootId(device2.getBootId())
      .setDeviceSerial(device2.getSerial())
      .build();
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(session2, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect another device.
    Profiler.Device device3 = Profiler.Device.newBuilder().setSerial("FakeDevice3").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process3 = Profiler.Process.newBuilder()
      .setPid(3)
      .setState(Profiler.Process.State.DEAD)
      .setName("FakeProcess3")
      .build();
    Common.Session session3 = Common.Session.newBuilder()
      .setBootId(device3.getBootId())
      .setDeviceSerial(device3.getSerial())
      .build();
    myProfilerService.addDevice(device3);
    myProfilerService.addProcess(session3, process3);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Preferred device should be the one with process that can be profiled
    assertEquals("FakeDevice2", profilers.getDevice().getSerial());
    assertEquals(process2, profilers.getProcess());
  }

  @Test
  public void keepSelectedDeviceAfterDisconnectingAllDevices() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Profiler.Device device1 = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process1 = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    Common.Session session1 = Common.Session.newBuilder()
      .setBootId(device1.getBootId())
      .setDeviceSerial(device1.getSerial())
      .build();
    myProfilerService.addDevice(device1);
    myProfilerService.addProcess(session1, process1);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect a new device
    Profiler.Device device2 = Profiler.Device.newBuilder().setSerial("FakeDevice2").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process2 = Profiler.Process.newBuilder()
      .setPid(3039)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess2")
      .build();
    Common.Session session2 = Common.Session.newBuilder()
      .setBootId(device2.getBootId())
      .setDeviceSerial(device2.getSerial())
      .build();
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(session2, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Update device1 state to disconnect
    Profiler.Device disconnectedDevice = Profiler.Device.newBuilder()
      .setSerial(device1.getSerial())
      .setState(Profiler.Device.State.DISCONNECTED)
      .build();
    myProfilerService.updateDevice(session1, device1, disconnectedDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Update device2 state to disconnect
    Profiler.Device disconnectedDevice2 = Profiler.Device.newBuilder()
      .setSerial(device2.getSerial())
      .setState(Profiler.Device.State.DISCONNECTED)
      .build();
    myProfilerService.updateDevice(session2, device2, disconnectedDevice2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Selected device should be FakeDevice2, which was selected before disconnecting all devices
    assertEquals("FakeDevice2", profilers.getDevice().getSerial());
    // Make sure the device is disconnected
    assertEquals(Profiler.Device.State.DISCONNECTED, profilers.getDevice().getState());
  }

  @Test
  public void testProfileOneProcessAtATime() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Profiler.Device device1 = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process1 = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    Profiler.Process process2 = Profiler.Process.newBuilder()
      .setPid(21)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess2")
      .build();
    Common.Session session1 = Common.Session.newBuilder()
      .setBootId(device1.getBootId())
      .setDeviceSerial(device1.getSerial())
      .build();
    myProfilerService.addDevice(device1);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(0, myGrpcServer.getProfiledProcessCount());
    myProfilerService.addProcess(session1, process1);
    myProfilerService.addProcess(session1, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(process1, profilers.getProcess());
    assertEquals(1, myGrpcServer.getProfiledProcessCount());
    assertEquals(process1, profilers.getProcess());

    // Switch to another process.
    profilers.setProcess(process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(1, myGrpcServer.getProfiledProcessCount());
    assertEquals(process2, profilers.getProcess());

    // Connect a new device with a process.
    Profiler.Device device2 = Profiler.Device.newBuilder().setSerial("FakeDevice2").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process3 = Profiler.Process.newBuilder()
      .setPid(3039)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess3")
      .build();
    Common.Session session2 = Common.Session.newBuilder()
      .setBootId(device2.getBootId())
      .setDeviceSerial(device2.getSerial())
      .build();
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(session2, process3);

    // Switch to the new device.
    profilers.setDevice(device2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(1, myGrpcServer.getProfiledProcessCount());
    assertEquals(process3, profilers.getProcess());

    // Update device2 state to disconnect
    Profiler.Device disconnectedDevice2 = Profiler.Device.newBuilder()
      .setSerial(device2.getSerial())
      .setState(Profiler.Device.State.DISCONNECTED)
      .build();
    myProfilerService.updateDevice(session2, device2, disconnectedDevice2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(1, myGrpcServer.getProfiledProcessCount());
    // Restart profiling process1 on device1.
    assertEquals(process1, profilers.getProcess());

    // Update device1 state to disconnect
    Profiler.Device disconnectedDevice = Profiler.Device.newBuilder()
      .setSerial(device1.getSerial())
      .setState(Profiler.Device.State.DISCONNECTED)
      .build();
    myProfilerService.updateDevice(session1, device1, disconnectedDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(0, myGrpcServer.getProfiledProcessCount());
  }

  @Test
  public void testAttachAgentCalledWhenFeatureEnabled() throws Exception {
    FakeIdeProfilerServices fakeIdeService = new FakeIdeProfilerServices();
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeIdeService, timer);

    Profiler.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Profiler.Device.State.ONLINE);
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    Profiler.Process process1 = createProcess(1, "FakeProcess1", Profiler.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(session, process1);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(device, profilers.getDevice());
    assertEquals(process1, profilers.getProcess());
    assertFalse(myProfilerService.getAgentAttachCalled());

    fakeIdeService.enableJvmtiAgent(true);
    Profiler.Process process2 = createProcess(2, "FakeProcess2", Profiler.Process.State.ALIVE);
    myProfilerService.addProcess(session, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(process2);
    assertEquals(device, profilers.getDevice());
    assertEquals(process2, profilers.getProcess());
    assertTrue(myProfilerService.getAgentAttachCalled());
  }

  @Test
  public void testAttachAgentNotCalledPreO() throws Exception {
    FakeIdeProfilerServices fakeIdeService = new FakeIdeProfilerServices();
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeIdeService, timer);

    fakeIdeService.enableJvmtiAgent(true);
    Profiler.Device device = createDevice(AndroidVersion.VersionCodes.N, "FakeDevice", Profiler.Device.State.ONLINE);
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    Profiler.Process process1 = createProcess(1, "FakeProcess1", Profiler.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(session, process1);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(device, profilers.getDevice());
    assertEquals(process1, profilers.getProcess());
    assertFalse(myProfilerService.getAgentAttachCalled());
  }

  /**
   * We need to account for an scenario where perfd reinstantiates and needs to pass a new client socket to the app. Hence we make the
   * same attach agent call from Studio side and let perfd handles the rest.
   * @throws Exception
   */
  @Test
  public void testAttachAgentEvenIfAlreadyAttached() throws Exception {
    FakeIdeProfilerServices fakeIdeService = new FakeIdeProfilerServices();
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeIdeService, timer);

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    fakeIdeService.enableJvmtiAgent(true);
    Profiler.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Profiler.Device.State.ONLINE);
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    Profiler.Process process1 = createProcess(1, "FakeProcess1", Profiler.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(session, process1);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(device, profilers.getDevice());
    assertEquals(process1, profilers.getProcess());
    assertTrue(myProfilerService.getAgentAttachCalled());
  }

  @Test
  public void testProfilingStops() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Profiler.Device device1 = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process1 = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    Profiler.Process process2 = Profiler.Process.newBuilder()
      .setPid(21)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess2")
      .build();
    Common.Session session1 = Common.Session.newBuilder()
      .setBootId(device1.getBootId())
      .setDeviceSerial(device1.getSerial())
      .build();
    myProfilerService.addDevice(device1);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(0, myGrpcServer.getProfiledProcessCount());
    myProfilerService.addProcess(session1, process1);
    myProfilerService.addProcess(session1, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(process1, profilers.getProcess());
    assertEquals(1, myGrpcServer.getProfiledProcessCount());
    assertEquals(process1, profilers.getProcess());
    assertTrue(timer.isRunning());

    // Stop the profiler
    profilers.stop();

    assertEquals(0, myGrpcServer.getProfiledProcessCount());
    assertEquals(null, profilers.getProcess());
    assertEquals(null, profilers.getDevice());
    assertEquals(NullMonitorStage.class, profilers.getStageClass());
    assertFalse(timer.isRunning());
  }

  @Test
  public void testProfilingStopsWithLiveAllocationEnabled() throws Exception {
    FakeTimer timer = new FakeTimer();
    FakeIdeProfilerServices services = new FakeIdeProfilerServices();
    // Enable live allocation tracker
    services.enableLiveAllocationTracking(true);
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), services, timer);

    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process = Profiler.Process.newBuilder().setPid(1).setState(Profiler.Process.State.ALIVE).setName("process").build();
    Common.Session session1 = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();

    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilerService.addProcess(session1, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertTrue(timer.isRunning());

    assertEquals(device, profilers.getDevice());
    assertEquals(process, profilers.getProcess());

    // Stop the profiler
    profilers.stop();

    assertFalse(timer.isRunning());
    assertEquals(null, profilers.getProcess());
    assertEquals(null, profilers.getDevice());
  }

  @Test
  public void testStopppingTwice() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    // Should be modified when STAGE aspect is fired.
    boolean[] stageAspectTriggered = {false};
    profilers.addDependency(new AspectObserver())
      .onChange(ProfilerAspect.STAGE, () -> {
        stageAspectTriggered[0] = true;
      });

    // Check profiler is not stopped.
    assertFalse(profilers.isStopped());
    assertTrue(timer.isRunning());
    // Stop the profiler
    profilers.stop();
    // Profiler should have stopped and STAGE is supposed to have been fired.
    assertTrue(stageAspectTriggered[0]);

    // Check profiler is stopped.
    assertTrue(profilers.isStopped());
    assertFalse(timer.isRunning());
    stageAspectTriggered[0] = false;
    // Try to stop the profiler again.
    profilers.stop();
    // Profiler was already stopped and STAGE is not supposed to have been fired.
    assertFalse(stageAspectTriggered[0]);
  }

  private StudioProfilers getProfilersWithDeviceAndProcess() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Profiler.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Profiler.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertEquals(device, profilers.getDevice());
    assertNull(profilers.getProcess());
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();

    Profiler.Process process = createProcess(20, "FakeProcess", Profiler.Process.State.ALIVE);
    myProfilerService.addProcess(session, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    assertEquals(process, profilers.getProcess());
    return profilers;
  }

  // TODO refactor tests to use this helper
  private Profiler.Device createDevice(int featureLevel, @NotNull String serial, @NotNull Profiler.Device.State state) {
    return Profiler.Device.newBuilder().setFeatureLevel(featureLevel).setSerial(serial).setState(state).build();
  }

  // TODO refactor tests to use this helper
  private Profiler.Process createProcess(int pid, @NotNull String name, Profiler.Process.State state) {
    return Profiler.Process.newBuilder().setPid(pid).setName(name).setState(state).build();
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
}
