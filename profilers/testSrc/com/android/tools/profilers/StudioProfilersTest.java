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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.*;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profiler.proto.EventProfiler.EventStartRequest;
import com.android.tools.profiler.proto.EventProfiler.EventStartResponse;
import com.android.tools.profiler.proto.EventProfiler.EventStopRequest;
import com.android.tools.profiler.proto.EventProfiler.EventStopResponse;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.NetworkProfiler.*;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuCaptureTest;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.google.common.collect.ImmutableList;
import io.grpc.stub.StreamObserver;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

final public class StudioProfilersTest {
  private final FakeProfilerService myProfilerService = new FakeProfilerService(false);

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel(
    "StudioProfilerTestChannel",
    myProfilerService,
    new EventService(),
    new MemoryService(),
    new NetworkService(),
    new CpuService());

  @Test
  public void testVersion() throws Exception {
    Profiler.VersionResponse response =
      myGrpcChannel.getClient().getProfilerClient().getVersion(Profiler.VersionRequest.getDefaultInstance());
    assertEquals(FakeProfilerService.VERSION, response.getVersion());
  }

  @Test
  public void testClearedOnMonitorStage() throws Exception {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub());

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());

    profilers.setStage(new CpuProfilerStage(profilers));
    profilers.getTimeline().getSelectionRange().set(10, 10);
    profilers.setMonitoringStage();

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());
  }

  @Test
  public void testProfilerModeChange() throws Exception {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub());
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
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertNull(profilers.getDevice());
    assertNull(profilers.getProcess());

    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertNull(profilers.getProcess());

    Profiler.Process process = Profiler.Process.newBuilder().setPid(20).setName("FakeProcess").build();
    myProfilerService.addProcess(device.getSerial(), process);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertEquals("FakeProcess", profilers.getProcess().getName());

    profilers.setPreferredProcessName("Preferred");

    Profiler.Process preferred = Profiler.Process.newBuilder().setPid(20).setName("Preferred").build();
    myProfilerService.addProcess(device.getSerial(), preferred);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertEquals("FakeDevice", profilers.getDevice().getSerial());
    assertEquals("Preferred", profilers.getProcess().getName());

    assertEquals(2, profilers.getProcesses().size());
    assertTrue(profilers.getProcesses().containsAll(ImmutableList.of(process, preferred)));
  }

  @Test
  public void testConnectionError() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub(), timer);

    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
    Profiler.Process process = Profiler.Process.newBuilder().setPid(20).setName("FakeProcess").build();

    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device.getSerial(), process);

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
    Profiler.Process process = Profiler.Process.newBuilder().setPid(20).setName("FakeProcess").build();
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device.getSerial(), process);

    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertTrue(profilers.getStage() instanceof StudioMonitorStage);
  }

  @Test
  public void testTimeResetOnConnectedDevice() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub(), timer);
    int nowInSeconds = 42;
    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds));

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
    myProfilerService.addDevice(device);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    int dataNow = nowInSeconds - StudioProfilers.TIMELINE_BUFFER;
    assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMin(), 0.001);
    assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMax(), 0.001);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS * 5);

    assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMin(), 0.001);
    assertEquals(TimeUnit.SECONDS.toMicros(dataNow + 5), profilers.getTimeline().getDataRange().getMax(), 0.001);
  }

  private static class EventService extends EventServiceGrpc.EventServiceImplBase {
    @Override
    public void startMonitoringApp(EventStartRequest request, StreamObserver<EventStartResponse> response) {
      response.onNext(EventStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(EventStopRequest request, StreamObserver<EventStopResponse> response) {
      response.onNext(EventStopResponse.getDefaultInstance());
      response.onCompleted();
    }
  }

  private static class MemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {
    @Override
    public void startMonitoringApp(MemoryStartRequest request, StreamObserver<MemoryStartResponse> response) {
      response.onNext(MemoryStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(MemoryStopRequest request, StreamObserver<MemoryStopResponse> response) {
      response.onNext(MemoryStopResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getData(MemoryRequest request, StreamObserver<MemoryData> response) {
      response.onNext(MemoryData.getDefaultInstance());
      response.onCompleted();
    }
  }

  private static class NetworkService extends NetworkServiceGrpc.NetworkServiceImplBase {
    @Override
    public void startMonitoringApp(NetworkStartRequest request, StreamObserver<NetworkStartResponse> response) {
      response.onNext(NetworkStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(NetworkStopRequest request, StreamObserver<NetworkStopResponse> response) {
      response.onNext(NetworkStopResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getData(NetworkDataRequest request, StreamObserver<NetworkDataResponse> response) {
      response.onNext(NetworkDataResponse.getDefaultInstance());
      response.onCompleted();
    }
  }

  private static class CpuService extends CpuServiceGrpc.CpuServiceImplBase {
    @Override
    public void startMonitoringApp(CpuStartRequest request, StreamObserver<CpuStartResponse> response) {
      response.onNext(CpuStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(CpuStopRequest request, StreamObserver<CpuStopResponse> response) {
      response.onNext(CpuStopResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getData(CpuDataRequest request, StreamObserver<CpuDataResponse> response) {
      response.onNext(CpuDataResponse.getDefaultInstance());
      response.onCompleted();
    }
  }
}
