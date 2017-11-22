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
import com.android.tools.profiler.proto.Profiler.AgentStatusResponse;
import com.android.tools.profiler.proto.Profiler.VersionRequest;
import com.android.tools.profiler.proto.Profiler.VersionResponse;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerTestUtils;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudioProfilersTest {
  private final FakeProfilerService myProfilerService = new FakeProfilerService(false);
  @Rule public FakeGrpcServer myGrpcServer = new FakeGrpcServer("StudioProfilerTestChannel", myProfilerService);

  @Before
  public void setup() {
    myProfilerService.reset();
  }

  @Test
  public void testVersion() throws Exception {
    VersionResponse response =
      myGrpcServer.getClient().getProfilerClient().getVersion(VersionRequest.getDefaultInstance());
    assertThat(response.getVersion()).isEqualTo(FakeProfilerService.VERSION);
  }

  @Test
  public void testClearedOnMonitorStage() throws Exception {
    StudioProfilers profilers = getProfilersWithDeviceAndProcess();
    assertThat(profilers.getTimeline().getSelectionRange().isEmpty()).isTrue();

    profilers.setStage(new CpuProfilerStage(profilers));
    profilers.getTimeline().getSelectionRange().set(10, 10);
    profilers.setMonitoringStage();

    assertThat(profilers.getTimeline().getSelectionRange().isEmpty()).isTrue();
  }

  @Test
  public void testProfilerModeChange() throws Exception {
    StudioProfilers profilers = getProfilersWithDeviceAndProcess();
    assertThat(profilers.getMode()).isEqualTo(ProfilerMode.NORMAL);
    CpuProfilerStage stage = new CpuProfilerStage(profilers);
    profilers.setStage(stage);
    assertThat(profilers.getMode()).isEqualTo(ProfilerMode.NORMAL);
    stage.setAndSelectCapture(CpuProfilerTestUtils.getValidCapture());
    assertThat(profilers.getMode()).isEqualTo(ProfilerMode.EXPANDED);
    profilers.setMonitoringStage();
    assertThat(profilers.getMode()).isEqualTo(ProfilerMode.NORMAL);
  }

  @Test
  public void testSleepBeforeAppLaunched() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    //Validate we start in the null stage.
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    // Validate that just because we add a device, we still have not left the  null monitor stage.
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    // Pick a time to set the device to. Note that this value is arbitrary but the bug this tests
    // is exposed if this value is larger than nanoTime.
    long timeOnDevice = System.nanoTime() + 1000;
    myProfilerService.setTimestampNs(timeOnDevice);

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    // Add a process and validate the stage goes to the monitor stage.
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    // Test that the process was attached correctly
    assertThat(profilers.getTimeline().isStreaming()).isTrue();
    // Test that the data range has not been inverted
    assertThat(profilers.getTimeline().getDataRange().isEmpty()).isFalse();
  }

  @Test
  public void testProfilerStageChange() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    //Validate we start in the null stage.
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    // Validate that just because we add a device, we still have not left the  null monitor stage.
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    // Add a process and validate the stage goes to the monitor stage.
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getStageClass()).isSameAs(StudioMonitorStage.class);
    // Add a second device with no processes, and select that device.
    device = Common.Device.newBuilder().setSerial("FakeDevice2").build();
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);
  }

  @Test
  public void testLateConnectionOfPreferredProcess() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getProcess()).isNull();

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addProcess(device, process);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getProcess().getName()).isEqualTo("FakeProcess");

    profilers.setPreferredProcessName("Preferred");

    Common.Process preferred = createProcess(device.getDeviceId(), 20, "Preferred", Common.Process.State.ALIVE);
    myProfilerService.addProcess(device, preferred);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getProcess().getName()).isEqualTo("Preferred");

    assertThat(profilers.getProcesses()).hasSize(2);
    assertThat(profilers.getProcesses()).containsAllIn(ImmutableList.of(process, preferred));
  }

  @Test
  public void testConnectionError() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    // This should fail and not find any devices
    myProfilerService.setThrowErrorOnGetDevices(true);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    // Server "is back up", try again
    myProfilerService.setThrowErrorOnGetDevices(false);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getProcess().getName()).isEqualTo("FakeProcess");
  }

  @Test
  public void testAlreadyConnected() throws Exception {
    FakeTimer timer = new FakeTimer();
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getStage()).isInstanceOf(StudioMonitorStage.class);
  }

  @Test
  public void testTimeResetOnConnectedDevice() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    int nowInSeconds = 42;
    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds));

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    int dataNow = nowInSeconds - StudioProfilers.TIMELINE_BUFFER;
    assertThat(profilers.getTimeline().getDataRange().getMin()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(dataNow));
    assertThat(profilers.getTimeline().getDataRange().getMax()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(dataNow));

    timer.tick(FakeTimer.ONE_SECOND_IN_NS * 5);

    assertThat(profilers.getTimeline().getDataRange().getMin()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(dataNow));
    assertThat(profilers.getTimeline().getDataRange().getMax()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(dataNow + 5));
  }

  @Test
  public void testAgentAspectFiring() throws Exception {
    FakeTimer timer = new FakeTimer();
    AgentStatusAspectObserver observer = new AgentStatusAspectObserver();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    profilers.addDependency(observer).onChange(ProfilerAspect.AGENT, observer::AgentStatusChanged);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(0);

    // Test that status changes if no process is selected does nothing
    myProfilerService.setAgentStatus(AgentStatusResponse.Status.ATTACHED);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(0);

    // Test that agent status change fires after a process is selected.
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Process process2 = createProcess(device.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process1);
    myProfilerService.addProcess(device, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(profilers.isAgentAttached()).isTrue();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);

    // Test that manually setting a process fires an agent status change
    profilers.setProcess(process2);
    assertThat(profilers.getProcess()).isSameAs(process2);
    assertThat(profilers.isAgentAttached()).isTrue();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(2);

    myProfilerService.setAgentStatus(AgentStatusResponse.Status.DETACHED);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(3);
  }

  @Test
  public void testAgentAspectNotFiredWhenSettingSameDeviceProcess() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    AgentStatusAspectObserver observer = new AgentStatusAspectObserver();
    profilers.addDependency(observer).onChange(ProfilerAspect.AGENT, observer::AgentStatusChanged);

    // Test that the status changed is fired when the process first gets selected.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isSameAs(device);
    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(profilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);

    // Test that resetting the same device/process would not trigger the status changed event.
    profilers.setDevice(device);
    profilers.setProcess(process);
    assertThat(profilers.getDevice()).isSameAs(device);
    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(profilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);
  }

  @Test
  public void testRestartedPreferredProcessNotSelected() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    int nowInSeconds = 42;
    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds));

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    profilers.setPreferredProcessName(process.getName());
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);

    // Change the alive (active) process to DEAD, and create a new ALIVE process simulating a debugger restart.
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder()
      .setState(Common.Process.State.DEAD)
      .build();
    myProfilerService.addProcess(device, process);

    // Verify the process is in the dead state.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.DEAD);

    process = process.toBuilder()
      .setPid(21)
      .setState(Common.Process.State.ALIVE)
      .build();
    myProfilerService.addProcess(device, process);

    // The profiler should not automatically selects the alive, preferred process again.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.DEAD);

    // Resets the preferred process and profiler should pick up the new process.
    profilers.setPreferredProcessName(process.getName());
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(21);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
  }

  @Test
  public void testProcessStateChangesShouldNotTriggerStageChange() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(Common.Process.State.ALIVE).isEqualTo(profilers.getProcess().getState());

    AspectObserver observer = new AspectObserver();
    profilers.addDependency(observer).onChange(ProfilerAspect.STAGE, () -> {
      assert false;
    });
    // Change the alive (active) process to DEAD
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder()
      .setState(Common.Process.State.DEAD)
      .build();
    myProfilerService.addProcess(device, process);

    //Verify the process is in the dead state.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(Common.Process.State.DEAD).isEqualTo(profilers.getProcess().getState());
  }

  @Test
  public void timelineShouldBeStreamingWhenProcessIsSelected() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getTimeline().isStreaming()).isTrue();
  }

  @Test
  public void timelineShouldStopStreamingWhenRangeIsSelected() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

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
  public void newOnlineDeviceShouldBeSelectedIfCurrentIsNotOnline() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getProcess()).isEqualTo(process);

    // Update device state to disconnect
    Common.Device disconnectedDevice = Common.Device.newBuilder()
      .setSerial(device.getSerial())
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    myProfilerService.updateDevice(device, disconnectedDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect a new device
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process2 = createProcess(device2.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(device2, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice2");
    assertThat(profilers.getProcess()).isEqualTo(process2);
  }

  @Test
  public void onlineDeviceShouldNotOverrideExplicitlySetOfflineDevice() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process);

    // Connect a new device, and marks the old one as disconnected
    Common.Device dead_device = device.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process2 = createProcess(device2.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.updateDevice(device, dead_device);
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(device2, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connecting an online device should override previously selection if it was not online
    assertThat(profilers.getDevice()).isEqualTo(device2);
    assertThat(profilers.getProcess()).isEqualTo(process2);

    // Explicitly set device
    profilers.setDevice(dead_device);
    assertThat(profilers.getDevice()).isEqualTo(dead_device);
    assertThat(profilers.getProcess()).isEqualTo(process);
  }

  @Test
  public void deviceWithAliveProcessesHasPreference() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.DEAD);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect a device with a process that can be profiled
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process2 = createProcess(device2.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(device2, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect another device.
    Common.Device device3 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice3", Common.Device.State.ONLINE);
    Common.Process process3 = createProcess(device3.getDeviceId(), 22, "FakeProcess3", Common.Process.State.DEAD);
    myProfilerService.addDevice(device3);
    myProfilerService.addProcess(device3, process3);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Preferred device should be the one with process that can be profiled
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice2");
    assertThat(profilers.getProcess()).isEqualTo(process2);
  }

  @Test
  public void keepSelectedDeviceAfterDisconnectingAllDevices() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device1);
    myProfilerService.addProcess(device1, process1);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect a new device
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process2 = createProcess(device2.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(device2, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Update device1 state to disconnect
    Common.Device disconnectedDevice = Common.Device.newBuilder()
      .setSerial(device1.getSerial())
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    myProfilerService.updateDevice(device1, disconnectedDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Update device2 state to disconnect
    Common.Device disconnectedDevice2 = Common.Device.newBuilder()
      .setSerial(device2.getSerial())
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    myProfilerService.updateDevice(device2, disconnectedDevice2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Selected device should be FakeDevice2, which was selected before disconnecting all devices
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice2");
    // Make sure the device is disconnected
    assertThat(profilers.getDevice().getState()).isEqualTo(Common.Device.State.DISCONNECTED);
  }

  @Test
  public void testProfileOneProcessAtATime() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Process process2 = createProcess(device1.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device1);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    myProfilerService.addProcess(device1, process1);
    myProfilerService.addProcess(device1, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(profilers.getProcess()).isEqualTo(process1);

    // Switch to another process.
    profilers.setProcess(process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(profilers.getProcess()).isEqualTo(process2);

    // Connect a new device with a process.
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process3 = createProcess(device2.getDeviceId(), 22, "FakeProcess3", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(device2, process3);

    // Switch to the new device.
    profilers.setDevice(device2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(profilers.getProcess()).isEqualTo(process3);

    // Update device2 state to disconnect
    Common.Device disconnectedDevice2 = device2.toBuilder()
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    myProfilerService.updateDevice(device2, disconnectedDevice2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    // Restart profiling process1 on device1.
    assertThat(profilers.getProcess()).isEqualTo(process1);

    // Update device1 state to disconnect
    Common.Device disconnectedDevice = device1.toBuilder()
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    myProfilerService.updateDevice(device1, disconnectedDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
  }

  @Test
  public void testAttachAgentCalledWhenFeatureEnabled() throws Exception {
    FakeIdeProfilerServices fakeIdeService = new FakeIdeProfilerServices();
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeIdeService, timer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process1);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myProfilerService.getAgentAttachCalled()).isFalse();

    fakeIdeService.enableJvmtiAgent(true);
    Common.Process process2 = createProcess(device.getDeviceId(), 2, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addProcess(device, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(process2);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process2);
    assertThat(myProfilerService.getAgentAttachCalled()).isTrue();
  }

  @Test
  public void testAttachAgentNotCalledPreO() throws Exception {
    FakeIdeProfilerServices fakeIdeService = new FakeIdeProfilerServices();
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeIdeService, timer);

    fakeIdeService.enableJvmtiAgent(true);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.N, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process1);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myProfilerService.getAgentAttachCalled()).isFalse();
  }

  /**
   * We need to account for an scenario where perfd reinstantiates and needs to pass a new client socket to the app. Hence we make the
   * same attach agent call from Studio side and let perfd handles the rest.
   */
  @Test
  public void testAttachAgentEvenIfAlreadyAttached() throws Exception {
    FakeIdeProfilerServices fakeIdeService = new FakeIdeProfilerServices();
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeIdeService, timer);

    myProfilerService.setAgentStatus(AgentStatusResponse.Status.ATTACHED);
    fakeIdeService.enableJvmtiAgent(true);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process1);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myProfilerService.getAgentAttachCalled()).isTrue();
  }

  @Test
  public void testProfilingStops() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Process process2 = createProcess(device1.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device1);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    myProfilerService.addProcess(device1, process1);
    myProfilerService.addProcess(device1, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(timer.isRunning()).isTrue();

    // Stop the profiler
    profilers.stop();

    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);
    assertThat(timer.isRunning()).isFalse();
  }

  @Test
  public void testProfilingStopsWithLiveAllocationEnabled() throws Exception {
    FakeTimer timer = new FakeTimer();
    FakeIdeProfilerServices services = new FakeIdeProfilerServices();
    // Enable live allocation tracker
    services.enableLiveAllocationTracking(true);
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), services, timer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(timer.isRunning()).isTrue();

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process);

    // Stop the profiler
    profilers.stop();

    assertThat(timer.isRunning()).isFalse();
    assertThat(profilers.getProcess()).isEqualTo(null);
    assertThat(profilers.getDevice()).isEqualTo(null);
  }

  @Test
  public void testStoppingTwice() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    // Should be modified when STAGE aspect is fired.
    boolean[] stageAspectTriggered = {false};
    profilers.addDependency(new AspectObserver())
      .onChange(ProfilerAspect.STAGE, () -> {
        stageAspectTriggered[0] = true;
      });

    // Check profiler is not stopped.
    assertThat(profilers.isStopped()).isFalse();
    assertThat(timer.isRunning()).isTrue();
    // Stop the profiler
    profilers.stop();
    // Profiler should have stopped and STAGE is supposed to have been fired.
    assertThat(stageAspectTriggered[0]).isTrue();

    // Check profiler is stopped.
    assertThat(profilers.isStopped()).isTrue();
    assertThat(timer.isRunning()).isFalse();
    stageAspectTriggered[0] = false;
    // Try to stop the profiler again.
    profilers.stop();
    // Profiler was already stopped and STAGE is not supposed to have been fired.
    assertThat(stageAspectTriggered[0]).isFalse();
  }

  @Test
  public void testBeginAndEndSessionOnProcessChange() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a device without processes. Session should be null.
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    assertThat(profilers.getSession()).isNull();

    // Adds a process, which should trigger the session to start.
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);

    // Mark the process as dead, which should ends the session.
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testBeginAndEndSessionOnDeviceChange() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a device with process. Session should start immediately
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);

    // Killing the device should stop the session
    myProfilerService.removeProcess(device, process);
    Common.Device deadDevice = device.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    Common.Process deadProcess = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myProfilerService.addDevice(deadDevice);
    myProfilerService.addProcess(deadDevice, deadProcess);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testNewSessionResetsStage() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);
    assertThat(profilers.getStage()).isInstanceOf(StudioMonitorStage.class);

    // Goes into a different stage
    profilers.setStage(new FakeStage(profilers));
    assertThat(profilers.getStage()).isInstanceOf(FakeStage.class);

    // Ending a session should not leave the stage automatically.
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
    assertThat(profilers.getStage()).isInstanceOf(FakeStage.class);

    // Restarting a session on the same process should re-enter the StudioMonitorStage
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.ALIVE).build();
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);
    assertThat(profilers.getStage()).isInstanceOf(StudioMonitorStage.class);
  }

  private StudioProfilers getProfilersWithDeviceAndProcess() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isNull();

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    assertThat(profilers.getProcess()).isEqualTo(process);
    return profilers;
  }

  private Common.Device createDevice(int featureLevel, @NotNull String serial, @NotNull Common.Device.State state) {
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
