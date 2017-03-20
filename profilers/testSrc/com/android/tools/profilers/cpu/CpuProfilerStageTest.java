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
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.stacktrace.CodeLocation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class CpuProfilerStageTest extends AspectObserver {
  private final FakeCpuService myCpuService = new FakeCpuService();
  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService, new FakeProfilerService());

  private CpuProfilerStage myStage;

  private FakeIdeProfilerServices myServices;

  private boolean myCaptureDetailsCalled;

  @Before
  public void setUp() throws Exception {
    FakeTimer timer = new FakeTimer();
    myServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), myServices, timer);
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStage = new CpuProfilerStage(profilers);
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

    // Start a successful capture
    startCapturingSuccess();

    // Start a failing capture
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.FAILURE);
    startCapturing();
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
  }

  @Test
  public void startCapturingInstrumented() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS);
    myServices.setPrePoolExecutor(() -> assertEquals(CpuProfilerStage.CaptureState.STARTING, myStage.getCaptureState()));
    // Start a capture using INSTRUMENTED mode
    myStage.setProfilingMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED);
    startCapturing();
    assertEquals(CpuProfilerStage.CaptureState.CAPTURING, myStage.getCaptureState());
  }

  @Test
  public void testStopCapturingInvalidTrace() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());

    // Start a successful capture
    startCapturingSuccess();

    // Stop capturing, but don't include a trace in the response.
    myServices.setOnExecute(() -> {
      // First, the main executor is going to be called to execute stopCapturingCallback,
      // which should set the capture state to PARSING
      assertEquals(CpuProfilerStage.CaptureState.PARSING, myStage.getCaptureState());
      // Then, the next time the main executor is called, it will try to parse the capture unsuccessfully
      // and set the capture state to IDLE
      myServices.setOnExecute(() -> {
        assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
        // Capture was stopped successfully, but capture should still be null as the response has no valid trace
        assertNull(myStage.getCapture());
      });
    });
    myCpuService.setStopProfilingStatus(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(false);
    stopCapturing();
  }

  @Test
  public void testStopCapturingInvalidTraceFailureStatus() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());

    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture unsuccessfully
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.FAILURE);
    myCpuService.setValidTrace(false);
    myServices.setOnExecute(() -> {
      assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
      assertNull(myStage.getCapture());
    });
    stopCapturing();
  }

  @Test
  public void testStopCapturingValidTraceFailureStatus() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());

    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture unsuccessfully, but with a valid trace
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.FAILURE);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    myServices.setOnExecute(() -> {
      assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
      // Despite the fact of having a valid trace, we first check for the response status.
      // As it wasn't SUCCESS, capture should not be set.
      assertNull(myStage.getCapture());
    });
    stopCapturing();
  }

  @Test
  public void testStopCapturingSuccessfully() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    captureSuccessfully();
  }

  @Test
  public void testSelectedThread() {
    myStage.setSelectedThread(0);
    assertEquals(0, myStage.getSelectedThread());

    myStage.setSelectedThread(42);
    assertEquals(42, myStage.getSelectedThread());
  }

  @Test
  public void testCaptureDetails() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());

    captureSuccessfully();

    myStage.setSelectedThread(myStage.getCapture().getMainThreadId());

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);

    // Top Down
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(CaptureModel.Details.Type.TOP_DOWN);
    assertTrue(myCaptureDetailsCalled = true);

    CaptureModel.Details details = myStage.getCaptureDetails();
    assertTrue(details instanceof CaptureModel.TopDown);
    assertNotNull(((CaptureModel.TopDown)details).getModel());

    // Bottom Up
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(CaptureModel.Details.Type.BOTTOM_UP);
    assertTrue(myCaptureDetailsCalled);

    details = myStage.getCaptureDetails();
    assertTrue(details instanceof CaptureModel.BottomUp);
    assertNotNull(((CaptureModel.BottomUp)details).getModel());

    // Chart
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(CaptureModel.Details.Type.CHART);
    assertTrue(myCaptureDetailsCalled);

    details = myStage.getCaptureDetails();
    assertTrue(details instanceof CaptureModel.TreeChart);
    assertNotNull(((CaptureModel.TreeChart)details).getNode());

    // null
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(null);
    assertTrue(myCaptureDetailsCalled);
    assertNull(myStage.getCaptureDetails());

    // HNode is null, as a result the model is null as well
    myStage.setSelectedThread(-1);
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(CaptureModel.Details.Type.BOTTOM_UP);
    assertTrue(myCaptureDetailsCalled);
    details = myStage.getCaptureDetails();
    assertTrue(details instanceof CaptureModel.BottomUp);
    assertNull(((CaptureModel.BottomUp)details).getModel());

    // Capture has changed, keeps the same type of details
    captureSuccessfully();
    CaptureModel.Details newDetails = myStage.getCaptureDetails();
    assertNotEquals(details, newDetails);
    assertTrue(newDetails instanceof CaptureModel.BottomUp);
    assertNotNull(((CaptureModel.BottomUp)newDetails).getModel());
  }

  @Test
  public void setCaptureShouldChangeDetails() throws Exception {
    // Capture a trace
    myCpuService.setTraceId(0);
    captureSuccessfully();

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);

    myCaptureDetailsCalled = false;
    // Capture another trace
    myCpuService.setTraceId(1);
    captureSuccessfully();

    assertNotNull(myStage.getCapture());
    assertEquals(myStage.getCapture(1), myStage.getCapture());
    assertTrue(myCaptureDetailsCalled);
  }

  @Test
  public void setSelectedThreadShouldChangeDetails() throws Exception {
    captureSuccessfully();

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);

    myCaptureDetailsCalled = false;
    myStage.setSelectedThread(42);

    assertEquals(42, myStage.getSelectedThread());
    assertTrue(myCaptureDetailsCalled);
  }

  @Test
  public void settingTheSameThreadDoesNothing() throws Exception {
    myCpuService.setTraceId(0);
    captureSuccessfully();

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);

    myCaptureDetailsCalled = false;
    myStage.setSelectedThread(42);
    assertTrue(myCaptureDetailsCalled);

    myCaptureDetailsCalled = false;
    // Thread id is the same as the current selected thread, so it should do nothing
    myStage.setSelectedThread(42);
    assertFalse(myCaptureDetailsCalled);
  }

  @Test
  public void settingTheSameDetailsTypeDoesNothing() throws Exception {
    myCpuService.setTraceId(0);
    captureSuccessfully();

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);
    assertEquals(CaptureModel.Details.Type.TOP_DOWN, myStage.getCaptureDetails().getType());

    myCaptureDetailsCalled = false;
    // The first time we set it to bottom up, CAPTURE_DETAILS should be fired
    myStage.setCaptureDetails(CaptureModel.Details.Type.BOTTOM_UP);
    assertTrue(myCaptureDetailsCalled);

    myCaptureDetailsCalled = false;
    // If we call it again for bottom up, we shouldn't fire CAPTURE_DETAILS
    myStage.setCaptureDetails(CaptureModel.Details.Type.BOTTOM_UP);
    assertFalse(myCaptureDetailsCalled);
  }

  @Test
  public void profilerReturnsToNormalModeAfterNavigatingToCode() throws IOException {
    // We need to be on the stage itself or else we won't be listening to code navigation events
    myStage.getStudioProfilers().setStage(myStage);

    // to EXPANDED mode
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myStage.setAndSelectCapture(new CpuCapture(CpuCaptureTest.readValidTrace()));
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    // After code navigation it should be Normal mode.
    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().navigate(CodeLocation.stub());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());

    myStage.setCapture(new CpuCapture(CpuCaptureTest.readValidTrace()));
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
  }

  @Test
  public void captureStateDependsOnAppBeingProfiling() {
    FakeTimer timer = new FakeTimer();
    myCpuService.setAppBeingProfiled(true);
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    CpuProfilerStage stage = new CpuProfilerStage(profilers);
    assertEquals(CpuProfilerStage.CaptureState.CAPTURING, stage.getCaptureState());

    timer = new FakeTimer();
    myCpuService.setAppBeingProfiled(false);
    profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    stage = new CpuProfilerStage(profilers);
    assertEquals(CpuProfilerStage.CaptureState.IDLE, stage.getCaptureState());
  }

  @Test
  public void setAndSelectCaptureDifferentClockType() throws IOException {
    CpuCapture capture = new CpuCapture(CpuCaptureTest.readValidTrace());
    CaptureNode captureNode = capture.getCaptureNode(capture.getMainThreadId());
    assertNotNull(captureNode);
    myStage.setSelectedThread(capture.getMainThreadId());

    assertEquals(ClockType.GLOBAL, captureNode.getClockType());
    myStage.setAndSelectCapture(capture);
    ProfilerTimeline timeline = myStage.getStudioProfilers().getTimeline();
    double eps = 0.00001;
    // In GLOBAL clock type, selection should be the main node range
    assertEquals(timeline.getSelectionRange().getMin(), captureNode.getStartGlobal(), eps);
    assertEquals(timeline.getSelectionRange().getMax(), captureNode.getEndGlobal(), eps);
    assertEquals(capture.getRange().getMax(), timeline.getSelectionRange().getMax(), eps);

    myStage.setClockType(ClockType.THREAD);
    assertEquals(ClockType.THREAD, captureNode.getClockType());
    myStage.setAndSelectCapture(capture);
    // In THREAD clock type, selection should scale the interval based on thread-clock/wall-clock ratio [node's startTime, node's endTime].
    double threadToGlobal = 1 / captureNode.threadGlobalRatio();
    double threadSelectionStart = captureNode.getStartGlobal() +
                                  threadToGlobal * (captureNode.getStartThread() - timeline.getSelectionRange().getMin());
    double threadSelectionEnd = threadSelectionStart +
                                threadToGlobal * captureNode.duration();
    assertEquals(timeline.getSelectionRange().getMin(), threadSelectionStart, eps);
    assertEquals(timeline.getSelectionRange().getMax(), threadSelectionEnd, eps);

    myStage.setClockType(ClockType.GLOBAL);
    assertEquals(ClockType.GLOBAL, captureNode.getClockType());
    // Just setting the clock type shouldn't change the selection range
    assertEquals(timeline.getSelectionRange().getMin(), threadSelectionStart, eps);
    assertEquals(timeline.getSelectionRange().getMax(), threadSelectionEnd, eps);
  }

  @Test
  public void testCaptureRangeConversion() throws Exception {
    captureSuccessfully();

    myStage.setSelectedThread(myStage.getCapture().getMainThreadId());
    myStage.setCaptureDetails(CaptureModel.Details.Type.BOTTOM_UP);

    Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    double eps = 1e-5;
    assertEquals(myStage.getCapture().getRange().getMin(), selection.getMin(), eps);
    assertEquals(myStage.getCapture().getRange().getMax(), selection.getMax(), eps);

    assertTrue(myStage.getCaptureDetails() instanceof CaptureModel.BottomUp);
    CaptureModel.BottomUp details = (CaptureModel.BottomUp)myStage.getCaptureDetails();

    Range detailsRange = details.getModel().getRange();

    // When ClockType.Global is used, the range of a capture details should the same as the selection range
    assertEquals(ClockType.GLOBAL, myStage.getClockType());
    assertEquals(detailsRange.getMin(), selection.getMin(), eps);
    assertEquals(detailsRange.getMax(), selection.getMax(), eps);

    detailsRange.set(0, 10);
    assertEquals(selection.getMin(), 0, eps);
    assertEquals(selection.getMax(), 10, eps);

    selection.set(1, 5);
    assertEquals(detailsRange.getMin(), 1, eps);
    assertEquals(detailsRange.getMax(), 5, eps);
  }

  private void captureSuccessfully() throws InterruptedException {
    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture successfully with a valid trace
    myServices.setOnExecute(() -> {
      // First, the main executor is going to be called to execute stopCapturingCallback,
      // which should set the capture state to PARSING
      assertEquals(CpuProfilerStage.CaptureState.PARSING, myStage.getCaptureState());
      // Then, the next time the main executor is called, it will parse the capture successfully
      // and set the capture state to IDLE
      myServices.setOnExecute(() -> {
        assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
        assertNotNull(myStage.getCapture());
      });
    });
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    stopCapturing();
  }

  private void startCapturingSuccess() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS);
    myServices.setPrePoolExecutor(() -> assertEquals(CpuProfilerStage.CaptureState.STARTING, myStage.getCaptureState()));
    startCapturing();
    assertEquals(CpuProfilerStage.CaptureState.CAPTURING, myStage.getCaptureState());
  }

  private void startCapturing() {
    myServices.setPrePoolExecutor(() -> assertEquals(CpuProfilerStage.CaptureState.STARTING, myStage.getCaptureState()));
    myStage.startCapturing();
  }

  private void stopCapturing() {
    // The pre executor will pass through STOPPING and then PARSING
    myServices.setPrePoolExecutor(() -> {
      assertEquals(CpuProfilerStage.CaptureState.STOPPING, myStage.getCaptureState());
      myServices.setPrePoolExecutor(() -> assertEquals(CpuProfilerStage.CaptureState.PARSING, myStage.getCaptureState()));
    });
    myStage.stopCapturing();
  }
}
