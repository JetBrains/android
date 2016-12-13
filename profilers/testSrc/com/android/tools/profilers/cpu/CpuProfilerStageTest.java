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
package com.android.tools.profilers.cpu;

import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.TestGrpcChannel;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static com.android.tools.profiler.proto.CpuProfiler.*;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.*;

public class CpuProfilerStageTest {

  @Rule
  public TestGrpcChannel<FakeCpuService> myGrpcChannel =
    new TestGrpcChannel<>("CpuProfilerStageTestChannel", new FakeCpuService(), new FakeProfilerService());

  private CpuProfilerStage myStage;

  /**
   * Countdown used to avoid making start/stop monitoring calls before the available processes are set.
   * The reason for that is these methods require a process to be selected.
   */
  private volatile CountDownLatch myGetProcessesLatch;

  @Before
  public void setUp() throws Exception {
    myStage = new CpuProfilerStage(myGrpcChannel.getProfilers());
    myGetProcessesLatch = new CountDownLatch(1);
    myGrpcChannel.getProfilers().addDependency().onChange(ProfilerAspect.PROCESSES, myGetProcessesLatch::countDown);
  }

  @Test
  public void testDefaultValues() throws IOException {
    assertNotNull(myStage.getCpuTraceDataSeries());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    assertNull(myStage.getCapture());
    assertFalse(myStage.isCapturing());
    assertNotNull(myStage.getAspect());
  }

  @Test
  public void testStartCapturing() throws InterruptedException {
    assertFalse(myStage.isCapturing());

    myGetProcessesLatch.await();
    // Start a successful capture
    myGrpcChannel.getService().setStartProfilingStatus(CpuProfilingAppStartResponse.Status.SUCCESS);
    myStage.startCapturing();
    assertTrue(myStage.isCapturing());

    // Start a failing capture
    myGrpcChannel.getService().setStartProfilingStatus(CpuProfilingAppStartResponse.Status.FAILURE);
    myStage.startCapturing();
    assertFalse(myStage.isCapturing());
  }

  @Test
  public void testStopCapturing() throws InterruptedException {
    assertFalse(myStage.isCapturing());

    myGetProcessesLatch.await();
    // Start a successful capture
    myGrpcChannel.getService().setStartProfilingStatus(CpuProfilingAppStartResponse.Status.SUCCESS);
    myStage.startCapturing();
    assertTrue(myStage.isCapturing());

    // Stop capturing
    myGrpcChannel.getService().setStopProfilingStatus(CpuProfilingAppStopResponse.Status.SUCCESS);
    myStage.stopCapturing();
    assertFalse(myStage.isCapturing());

    // Start a successful capture
    myStage.startCapturing();
    assertTrue(myStage.isCapturing());

    // Stop a capture unsuccessfully
    myGrpcChannel.getService().setStopProfilingStatus(CpuProfilingAppStopResponse.Status.FAILURE);
    myStage.stopCapturing();
    assertFalse(myStage.isCapturing());

    // TODO: test stop capturing with non-null captures
  }

  @Test
  public void testSelectedThread() {
    myStage.setSelectedThread(0);
    assertEquals(0, myStage.getSelectedThread());

    myStage.setSelectedThread(42);
    assertEquals(42, myStage.getSelectedThread());
  }

  private static class FakeCpuService extends CpuServiceGrpc.CpuServiceImplBase {

    private CpuProfilingAppStartResponse.Status myStartProfilingStatus = CpuProfilingAppStartResponse.Status.SUCCESS;
    private CpuProfilingAppStopResponse.Status myStopProfilingStatus = CpuProfilingAppStopResponse.Status.SUCCESS;

    @Override
    public void startProfilingApp(CpuProfilingAppStartRequest request, StreamObserver<CpuProfilingAppStartResponse> responseObserver) {
      CpuProfilingAppStartResponse.Builder response = CpuProfilingAppStartResponse.newBuilder();
      response.setStatus(myStartProfilingStatus);
      response.setErrorMessage("MockStartProfilingApp");

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void stopProfilingApp(CpuProfilingAppStopRequest request, StreamObserver<CpuProfilingAppStopResponse> responseObserver) {
      CpuProfilingAppStopResponse.Builder response = CpuProfilingAppStopResponse.newBuilder();
      response.setStatus(myStopProfilingStatus);
      response.setErrorMessage("MockStopProfilingApp");

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    private void setStartProfilingStatus(CpuProfilingAppStartResponse.Status status) {
      myStartProfilingStatus = status;
    }

    private void setStopProfilingStatus(CpuProfilingAppStopResponse.Status status) {
      myStopProfilingStatus = status;
    }
  }

  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {

    private Profiler.Device myDevice = Profiler.Device.newBuilder().setSerial("FakeDevice").build();

    private Profiler.Process myProcess = Profiler.Process.newBuilder().setPid(20).setName("FakeProcess").build();

    @Override
    public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
      Profiler.GetDevicesResponse.Builder response = Profiler.GetDevicesResponse.newBuilder();
      response.addDevice(myDevice);

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> responseObserver) {
      Profiler.GetProcessesResponse.Builder response = Profiler.GetProcessesResponse.newBuilder();
      response.addProcess(myProcess);

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTimes(Profiler.TimesRequest request, StreamObserver<Profiler.TimesResponse> responseObserver) {
      Profiler.TimesResponse.Builder response = Profiler.TimesResponse.newBuilder();

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }
  }
}
