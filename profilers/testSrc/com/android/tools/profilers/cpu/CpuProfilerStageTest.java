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

import static com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartResponse;
import static com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopResponse;
import static junit.framework.TestCase.assertNull;
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
    assertFalse(myStage.isCapturing());
    assertNotNull(myStage.getAspect());
  }

  @Test
  public void testStartCapturing() throws InterruptedException {
    assertFalse(myStage.isCapturing());
    myGetProcessesLatch.await();

    // Start a successful capture
    myCpuService.setStartProfilingStatus(CpuProfilingAppStartResponse.Status.SUCCESS);
    myStage.startCapturing();
    assertTrue(myStage.isCapturing());

    // Start a failing capture
    myCpuService.setStartProfilingStatus(CpuProfilingAppStartResponse.Status.FAILURE);
    myStage.startCapturing();
    assertFalse(myStage.isCapturing());
  }

  @Test
  public void testStopCapturingInvalidTrace() throws InterruptedException {
    assertFalse(myStage.isCapturing());
    myGetProcessesLatch.await();

    // Start a successful capture
    myCpuService.setStartProfilingStatus(CpuProfilingAppStartResponse.Status.SUCCESS);
    myStage.startCapturing();
    assertTrue(myStage.isCapturing());

    // Stop capturing, but don't include a trace in the response.
    CountDownLatch captureTreeLatch = new CountDownLatch(1);
    myServices.setOnExecute(captureTreeLatch::countDown);
    myCpuService.setStopProfilingStatus(CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(false);
    myStage.stopCapturing();
    assertFalse(myStage.isCapturing());
    assertTrue(myStage.isParsingCapture());
    captureTreeLatch.await();
    assertFalse(myStage.isParsingCapture());
    // Capture was stopped successfully, but capture should still be null as the response has no valid trace
    assertNull(myStage.getCapture());
  }

  @Test
  public void testStopCapturingInvalidTraceFailureStatus() throws InterruptedException {
    assertFalse(myStage.isCapturing());
    myGetProcessesLatch.await();

    // Start a successful capture
    myStage.startCapturing();
    assertTrue(myStage.isCapturing());

    // Stop a capture unsuccessfully
    myCpuService.setStopProfilingStatus(CpuProfilingAppStopResponse.Status.FAILURE);
    myCpuService.setValidTrace(false);
    myStage.stopCapturing();
    assertFalse(myStage.isCapturing());
    assertNull(myStage.getCapture());
  }

  @Test
  public void testStopCapturingValidTraceFailureStatus() throws InterruptedException {
    assertFalse(myStage.isCapturing());
    myGetProcessesLatch.await();

    // Start a successful capture
    myStage.startCapturing();
    assertTrue(myStage.isCapturing());

    // Stop a capture unsuccessfully, but with a valid trace
    myCpuService.setStopProfilingStatus(CpuProfilingAppStopResponse.Status.FAILURE);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    myStage.stopCapturing();
    assertFalse(myStage.isCapturing());
    // Despite the fact of having a valid trace, we first check for the response status.
    // As it wasn't SUCCESS, capture should not be set.
    assertNull(myStage.getCapture());
  }

  @Test
  public void testStopCapturingSuccessfully() throws InterruptedException {
    assertFalse(myStage.isCapturing());
    myGetProcessesLatch.await();

    // Start a successful capture
    myStage.startCapturing();
    assertTrue(myStage.isCapturing());

    // Stop a capture successfully with a valid trace
    CountDownLatch captureTreeLatch = new CountDownLatch(1);
    myServices.setOnExecute(captureTreeLatch::countDown);
    myCpuService.setStopProfilingStatus(CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    myStage.stopCapturing();
    assertFalse(myStage.isCapturing());
    assertTrue(myStage.isParsingCapture());
    captureTreeLatch.await();
    assertFalse(myStage.isParsingCapture());
    assertNotNull(myStage.getCapture());
  }

  @Test
  public void testSelectedThread() {
    myStage.setSelectedThread(0);
    assertEquals(0, myStage.getSelectedThread());

    myStage.setSelectedThread(42);
    assertEquals(42, myStage.getSelectedThread());
  }
}
