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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class CpuProfilerStageTest extends AspectObserver {

  private final FakeCpuService myCpuService = new FakeCpuService();
  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService, new FakeProfilerService());

  private CpuProfilerStage myStage;

  /**
   * Countdown used to avoid making start/stop monitoring calls before the available processes are set.
   * The reason for that is these methods require a process to be selected.
   */
  private volatile CountDownLatch myGetProcessesLatch;

  private FakeIdeProfilerServices myServices;

  @Before
  public void setUp() throws Exception {
    myServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), myServices);
    myStage = new CpuProfilerStage(profilers);
    myGetProcessesLatch = new CountDownLatch(1);
    profilers.addDependency(this).onChange(ProfilerAspect.PROCESSES, myGetProcessesLatch::countDown);
  }

  @Test
  public void testDefaultValues() throws IOException {
    assertNotNull(myStage.getCpuTraceDataSeries());
    assertNotNull(myStage.getThreadStates());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    assertNull(myStage.getCapture());
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    assertNotNull(myStage.getAspect());
  }

  @Test
  public void testStartCapturing() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    myGetProcessesLatch.await();

    // Start a successful capture
    startCapturingSuccess();

    // Start a failing capture
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.FAILURE);
    CountDownLatch startCapturingFailureCompleted = new CountDownLatch(1);
    myServices.setOnExecute(startCapturingFailureCompleted::countDown);
    myStage.startCapturing();
    assertEquals(CpuProfilerStage.CaptureState.STARTING, myStage.getCaptureState());
    startCapturingFailureCompleted.await();
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
  }

  @Test
  public void testStopCapturingInvalidTrace() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    myGetProcessesLatch.await();

    // Start a successful capture
    startCapturingSuccess();

    // Stop capturing, but don't include a trace in the response.
    CountDownLatch captureTreeLatch = new CountDownLatch(1);
    CountDownLatch stopProfilingCallbackLatch = new CountDownLatch(1);
    myServices.setOnExecute(() -> {
      stopProfilingCallbackLatch.countDown();
      myServices.setOnExecute(captureTreeLatch::countDown);
    });
    myCpuService.setStopProfilingStatus(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(false);
    myStage.stopCapturing();
    assertEquals(CpuProfilerStage.CaptureState.STOPPING, myStage.getCaptureState());
    stopProfilingCallbackLatch.await();
    assertEquals(CpuProfilerStage.CaptureState.PARSING, myStage.getCaptureState());
    captureTreeLatch.await();
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    // Capture was stopped successfully, but capture should still be null as the response has no valid trace
    assertNull(myStage.getCapture());
  }

  @Test
  public void testStopCapturingInvalidTraceFailureStatus() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    myGetProcessesLatch.await();

    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture unsuccessfully
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.FAILURE);
    myCpuService.setValidTrace(false);
    CountDownLatch stopCapturingFailureCompleted = new CountDownLatch(1);
    myServices.setOnExecute(stopCapturingFailureCompleted::countDown);
    myStage.stopCapturing();
    assertEquals(CpuProfilerStage.CaptureState.STOPPING, myStage.getCaptureState());
    stopCapturingFailureCompleted.await();
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    assertNull(myStage.getCapture());
  }

  @Test
  public void testStopCapturingValidTraceFailureStatus() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    myGetProcessesLatch.await();

    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture unsuccessfully, but with a valid trace
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.FAILURE);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    CountDownLatch stopProfilingCallbackLatch = new CountDownLatch(1);
    myServices.setOnExecute(stopProfilingCallbackLatch::countDown);
    myStage.stopCapturing();
    assertEquals(CpuProfilerStage.CaptureState.STOPPING, myStage.getCaptureState());
    stopProfilingCallbackLatch.await();
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    // Despite the fact of having a valid trace, we first check for the response status.
    // As it wasn't SUCCESS, capture should not be set.
    assertNull(myStage.getCapture());
  }

  @Test
  public void testStopCapturingSuccessfully() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    myGetProcessesLatch.await();

    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture successfully with a valid trace
    CountDownLatch captureTreeLatch = new CountDownLatch(1);
    CountDownLatch stopProfilingCallbackLatch = new CountDownLatch(1);
    myServices.setOnExecute(() -> {
      stopProfilingCallbackLatch.countDown();
      myServices.setOnExecute(captureTreeLatch::countDown);
    });
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    myStage.stopCapturing();
    assertEquals(CpuProfilerStage.CaptureState.STOPPING, myStage.getCaptureState());
    stopProfilingCallbackLatch.await();
    assertEquals(CpuProfilerStage.CaptureState.PARSING, myStage.getCaptureState());
    captureTreeLatch.await();
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    assertNotNull(myStage.getCapture());
  }

  @Test
  public void testSelectedThread() {
    myStage.setSelectedThread(0);
    assertEquals(0, myStage.getSelectedThread());

    myStage.setSelectedThread(42);
    assertEquals(42, myStage.getSelectedThread());
  }

  private void startCapturingSuccess() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS);
    CountDownLatch startCapturingSuccessCompleted = new CountDownLatch(1);
    myServices.setOnExecute(startCapturingSuccessCompleted::countDown);
    myStage.startCapturing();
    assertEquals(CpuProfilerStage.CaptureState.STARTING, myStage.getCaptureState());
    startCapturingSuccessCompleted.await();
    assertEquals(CpuProfilerStage.CaptureState.CAPTURING, myStage.getCaptureState());
  }
}
