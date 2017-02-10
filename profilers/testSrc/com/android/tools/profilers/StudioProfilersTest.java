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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuCaptureTest;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

final public class StudioProfilersTest {
  private final FakeProfilerService myProfilerService = new FakeProfilerService(false);
  @Rule public FakeGrpcServer myGrpcServer = new FakeGrpcServer("StudioProfilerTestChannel", myProfilerService);

  @Test
  public void testVersion() throws Exception {
    Profiler.VersionResponse response =
      myGrpcServer.getClient().getProfilerClient().getVersion(Profiler.VersionRequest.getDefaultInstance());
    assertEquals(FakeProfilerService.VERSION, response.getVersion());
  }

  @Test
  public void testClearedOnMonitorStage() throws Exception {
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices());

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());

    profilers.setStage(new CpuProfilerStage(profilers));
    profilers.getTimeline().getSelectionRange().set(10, 10);
    profilers.setMonitoringStage();

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());
  }

  @Test
  public void testProfilerModeChange() throws Exception {
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices());
    assertEquals(ProfilerMode.NORMAL, profilers.getMode());
    CpuProfilerStage stage = new CpuProfilerStage(profilers);
    profilers.setStage(stage);
    assertEquals(ProfilerMode.NORMAL, profilers.getMode());
    stage.setCapture(new CpuCapture(CpuCaptureTest.readValidTrace()));
    assertEquals(ProfilerMode.EXPANDED, profilers.getMode());
    profilers.setMonitoringStage();
    assertEquals(ProfilerMode.NORMAL, profilers.getMode());
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

    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
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
    assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMax(), 0.001);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS * 5);

    assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMin(), 0.001);
    assertEquals(TimeUnit.SECONDS.toMicros(dataNow + 5), profilers.getTimeline().getDataRange().getMax(), 0.001);
  }

  @Test
  public void testAgentStatusChange() throws Exception {
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
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(process, profilers.getProcess());
    assertTrue(profilers.isAgentAttached());
    assertEquals(1, observer.getAgentStatusChangedCount());

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.DETACHED);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertFalse(profilers.isAgentAttached());
    assertEquals(2, observer.getAgentStatusChangedCount());
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
