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

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_ID;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME;
import static com.android.tools.profilers.cpu.CpuProfilerTestUtils.ATRACE_DATA_FILE;
import static com.android.tools.profilers.cpu.CpuProfilerTestUtils.ATRACE_MISSING_DATA_FILE;
import static com.google.common.truth.Truth.assertThat;

import com.android.sdklib.AndroidVersion;
import com.android.testutils.TestUtils;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.filter.FilterModel;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.inspectors.common.api.stacktrace.CodeLocation;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.FakeFeatureTracker;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioMonitorStage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.analytics.FilterMetadata;
import com.android.tools.profilers.cpu.CpuProfilerStage.CaptureState;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.cpu.capturedetails.CaptureModel;
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration;
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration;
import com.android.tools.profilers.cpu.systemtrace.AtraceParser;
import com.android.tools.profilers.cpu.systemtrace.CpuKernelTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuThreadSliceInfo;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.network.FakeNetworkService;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public final class CpuProfilerStageTest extends AspectObserver {
  private static final int FAKE_PID = 20;
  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer);
  private final FakeCpuService myCpuService = new FakeCpuService();
  private final FakeMemoryService myMemoryService = new FakeMemoryService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService, myTransportService, new FakeProfilerService(myTimer),
                        myMemoryService, new FakeEventService(), FakeNetworkService.newBuilder().build());

  private CpuProfilerStage myStage;

  private FakeIdeProfilerServices myServices;

  private boolean myCaptureDetailsCalled;

  public CpuProfilerStageTest() {
    myServices = new FakeIdeProfilerServices();
    // This test file assumes always using Transport Pipeline.
    myServices.enableEventsPipeline(true);
  }

  @Before
  public void setUp() {
    myCpuService.clearTraceInfo();

    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myServices, myTimer);
    // One second must be enough for new devices (and processes) to be picked up
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStage = new CpuProfilerStage(profilers);
    myStage.getStudioProfilers().setStage(myStage);
    if (myServices.getFeatureConfig().isUnifiedPipelineEnabled()) {
      ProfilersTestData.populateThreadData(myTransportService, ProfilersTestData.SESSION_DATA.getStreamId());
    }
  }

  @Test
  public void testDefaultValues() {
    assertThat(myStage.getCpuTraceDataSeries()).isNotNull();
    assertThat(myStage.getThreadStates()).isNotNull();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    assertThat(myStage.getCapture()).isNull();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(myStage.getAspect()).isNotNull();
  }

  @Test
  public void testStartCapturing() throws InterruptedException, IOException {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Start a successful capture
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true, CpuProfilerTestUtils.readValidTrace());

    // Start a failing capture
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, false);
  }

  @Test
  public void startCapturingInstrumented() throws InterruptedException {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    // Start a capture using INSTRUMENTED mode
    ProfilingConfiguration instrumented = new ArtInstrumentedConfiguration("My Instrumented Config");
    myStage.getProfilerConfigModel().setProfilingConfiguration(instrumented);
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
  }

  @Test
  public void recordingPanelHasDefaultConfigurations() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    // Start a capture using INSTRUMENTED mode
    List<ProfilingConfiguration> defaultConfigurations = myStage.getProfilerConfigModel().getDefaultProfilingConfigurations();
    assertThat(myStage.getRecordingModel().getBuiltInOptions()).hasSize(defaultConfigurations.size());
    for(int i = 0; i < defaultConfigurations.size(); i++) {
      ProfilingTechnology tech = ProfilingTechnology.fromConfig(defaultConfigurations.get(i));
      assertThat(myStage.getRecordingModel().getBuiltInOptions().get(i).getTitle()).isEqualTo(tech.getName());
    }
  }

  @Test
  public void testStopCapturingFailure() throws InterruptedException {
    // Start a successful capture
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);

    // We expect two state changes, first is stopping and the other is IDLE after the stop request returns failure
    AspectObserver observer = new AspectObserver();
    CountDownLatch latch = CpuProfilerTestUtils.waitForProfilingStateChangeSequence(myStage,
                                                                                    observer,
                                                                                    CaptureState.STOPPING,
                                                                                    CaptureState.IDLE);
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, false, null);
    latch.await();
  }

  @Test
  public void testStopCapturingInvalidTrace() throws InterruptedException {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Start a successful capture
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);

    // Complete a capture successfully, but with an empty trace
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true, ByteString.EMPTY);
    assertThat(myStage.getCapture()).isNull();
  }

  @Test
  public void testStopCapturingSuccessfully() throws InterruptedException, IOException {
    CpuProfilerTestUtils
      .captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
  }

  @Test
  public void testJumpToLiveIfOngoingRecording() throws InterruptedException {
    StreamingTimeline timeline = myStage.getTimeline();
    timeline.setStreaming(false);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(timeline.isStreaming()).isFalse();

    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
    assertThat(timeline.isStreaming()).isTrue();
  }

  @Test
  public void testSelectedThread() {
    myStage.setSelectedThread(0);
    assertThat(myStage.getSelectedThread()).isEqualTo(0);

    myStage.setSelectedThread(42);
    assertThat(myStage.getSelectedThread()).isEqualTo(42);
  }

  @Test
  public void testCaptureDetails() throws IOException, ExecutionException, InterruptedException {
    CpuProfilerTestUtils
      .captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    myStage.setSelectedThread(myStage.getCapture().getMainThreadId());

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);

    // Top Down
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(CaptureDetails.Type.TOP_DOWN);
    assertThat(myCaptureDetailsCalled).isTrue();

    CaptureDetails details = myStage.getCaptureDetails();
    assertThat(details).isInstanceOf(CaptureDetails.TopDown.class);
    assertThat(((CaptureDetails.TopDown)details).getModel()).isNotNull();

    // Bottom Up
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(myCaptureDetailsCalled).isTrue();

    details = myStage.getCaptureDetails();
    assertThat(details).isInstanceOf(CaptureDetails.BottomUp.class);
    assertThat(((CaptureDetails.BottomUp)details).getModel()).isNotNull();

    // Chart
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(CaptureDetails.Type.CALL_CHART);
    assertThat(myCaptureDetailsCalled).isTrue();

    details = myStage.getCaptureDetails();
    assertThat(details).isInstanceOf(CaptureDetails.CallChart.class);
    assertThat(((CaptureDetails.CallChart)details).getNode()).isNotNull();

    // Default value
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(null);
    assertThat(myCaptureDetailsCalled).isTrue();
    details = myStage.getCaptureDetails();
    assertThat(details).isInstanceOf(CaptureDetails.CallChart.class);
    assertThat(((CaptureDetails.CallChart)details).getNode()).isNotNull();

    // CaptureNode is null, as a result the model is null as well
    myStage.setSelectedThread(-1);
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(myCaptureDetailsCalled).isTrue();
    details = myStage.getCaptureDetails();
    assertThat(details).isInstanceOf(CaptureDetails.BottomUp.class);
    assertThat(((CaptureDetails.BottomUp)details).getModel()).isNull();

    // Capture has changed, keeps the same type of details
    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    myStage.setAndSelectCapture(capture);
    CaptureDetails newDetails = myStage.getCaptureDetails();
    assertThat(newDetails).isNotEqualTo(details);
    assertThat(newDetails).isInstanceOf(CaptureDetails.BottomUp.class);
    assertThat(((CaptureDetails.BottomUp)newDetails).getModel()).isNotNull();

    // No capture sets details to null
    myStage.setCapture(null);
    assertThat(myStage.getCaptureDetails()).isNull();
  }

  @Test
  public void setCaptureShouldChangeDetails() throws IOException, ExecutionException, InterruptedException {
    // Capture a trace
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);
    myCaptureDetailsCalled = false;

    // Capture a trace.
    myTimer.setCurrentTimeNs(1); // Update the timer to give the second trace a different trace id.
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    assertThat(myStage.getCapture()).isNotNull();
    assertThat(myStage.getCapture()).isEqualTo(myStage.getCaptureFuture(1).get());
    assertThat(myCaptureDetailsCalled).isTrue();
  }

  @Test
  public void rangeIntersectionReturnsASingleTraceId() {
    int traceId1 = 1;
    int traceId2 = 2;

    addTraceInfoHelper(traceId1, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), TimeUnit.MICROSECONDS.toNanos(10),
                       TimeUnit.MICROSECONDS.toNanos(20), Cpu.CpuTraceConfiguration.getDefaultInstance());
    addTraceInfoHelper(traceId2, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), TimeUnit.MICROSECONDS.toNanos(30),
                       TimeUnit.MICROSECONDS.toNanos(40), Cpu.CpuTraceConfiguration.getDefaultInstance());

    // No intersection.
    CpuTraceInfo traceInfo = myStage.getIntersectingTraceInfo(new Range(0, 5));
    assertThat(traceInfo).isNull();

    // Intersecting only with trace 1.
    traceInfo = myStage.getIntersectingTraceInfo(new Range(5, 15));
    assertThat(traceInfo).isNotNull();
    assertThat(traceInfo.getTraceId()).isEqualTo(traceId1);

    // Intersecting only with trace 2.
    traceInfo = myStage.getIntersectingTraceInfo(new Range(25, 35));
    assertThat(traceInfo).isNotNull();
    assertThat(traceInfo.getTraceId()).isEqualTo(traceId2);

    // Intersecting with both traces. First trace is returned.
    traceInfo = myStage.getIntersectingTraceInfo(new Range(0, 50));
    assertThat(traceInfo).isNotNull();
    assertThat(traceInfo.getTraceId()).isEqualTo(traceId1);
  }

  @Test
  public void setSelectedThreadShouldChangeDetails() throws IOException, InterruptedException {
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);

    myCaptureDetailsCalled = false;
    myStage.setSelectedThread(42);

    assertThat(myStage.getSelectedThread()).isEqualTo(42);
    assertThat(myCaptureDetailsCalled).isTrue();
  }

  @Test
  public void unselectingThreadSetDetailsNodeToNull() throws InterruptedException, IOException {
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    myStage.setCaptureDetails(CaptureDetails.Type.CALL_CHART);
    myStage.setSelectedThread(myStage.getCapture().getMainThreadId());
    assertThat(myStage.getCaptureDetails()).isInstanceOf(CaptureDetails.CallChart.class);
    assertThat(((CaptureDetails.CallChart)myStage.getCaptureDetails()).getNode()).isNotNull();

    myStage.setSelectedThread(CaptureModel.NO_THREAD);
    assertThat(((CaptureDetails.CallChart)myStage.getCaptureDetails()).getNode()).isNull();
  }

  @Test
  public void settingTheSameThreadDoesNothing() throws InterruptedException, IOException {
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);

    myCaptureDetailsCalled = false;
    myStage.setSelectedThread(42);
    assertThat(myCaptureDetailsCalled).isTrue();

    myCaptureDetailsCalled = false;
    // Thread id is the same as the current selected thread, so it should do nothing
    myStage.setSelectedThread(42);
    assertThat(myCaptureDetailsCalled).isFalse();
  }

  @Test
  public void settingTheSameDetailsTypeDoesNothing() throws InterruptedException, IOException {
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);
    assertThat(myStage.getCaptureDetails().getType()).isEqualTo(CaptureDetails.Type.CALL_CHART);

    myCaptureDetailsCalled = false;
    // The first time we set it to bottom up, CAPTURE_DETAILS should be fired
    myStage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(myCaptureDetailsCalled).isTrue();

    myCaptureDetailsCalled = false;
    // If we call it again for bottom up, we shouldn't fire CAPTURE_DETAILS
    myStage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(myCaptureDetailsCalled).isFalse();
  }

  @Test
  public void callChartShouldBeSetAfterACapture() throws IOException, ExecutionException, InterruptedException {
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    assertThat(myStage.getCaptureDetails().getType()).isEqualTo(CaptureDetails.Type.CALL_CHART);

    // Change details type and verify it was actually changed.
    myStage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(myStage.getCaptureDetails().getType()).isEqualTo(CaptureDetails.Type.BOTTOM_UP);

    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    myStage.setAndSelectCapture(capture);
    // Just selecting a different capture shouldn't change the capture details
    assertThat(myStage.getCaptureDetails().getType()).isEqualTo(CaptureDetails.Type.BOTTOM_UP);

    myTimer.setCurrentTimeNs(1); // Update the timer to give the second trace a different trace id.
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    // Capturing again should set the details to call chart
    assertThat(myStage.getCaptureDetails().getType()).isEqualTo(CaptureDetails.Type.CALL_CHART);
  }

  @Test
  public void profilerReturnsToNormalModeAfterNavigatingToCode() throws IOException, ExecutionException, InterruptedException {
    // We need to be on the stage itself or else we won't be listening to code navigation events
    myStage.getStudioProfilers().setStage(myStage);

    // to EXPANDED mode
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myStage.setAndSelectCapture(CpuProfilerTestUtils.getValidCapture());
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    // After code navigation it should be Normal mode.
    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().navigate(CodeLocation.stub());
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);

    myStage.setCapture(CpuProfilerTestUtils.getValidCapture());
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
  }

  @Test
  public void setAndSelectCaptureDifferentClockType() throws InterruptedException, IOException {
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    CpuCapture capture = myStage.getCapture();
    CaptureNode captureNode = capture.getCaptureNode(capture.getMainThreadId());
    assertThat(captureNode).isNotNull();
    myStage.setSelectedThread(capture.getMainThreadId());

    assertThat(captureNode.getClockType()).isEqualTo(ClockType.GLOBAL);
    myStage.setAndSelectCapture(capture);
    StreamingTimeline timeline = myStage.getTimeline();
    double eps = 0.00001;
    // In GLOBAL clock type, selection should be the main node range
    assertThat(capture.getRange().getMin()).isWithin(eps).of(timeline.getSelectionRange().getMin());
    assertThat(capture.getRange().getMax()).isWithin(eps).of(timeline.getSelectionRange().getMax());

    timeline.getSelectionRange().set(captureNode.getStartGlobal(), captureNode.getEndGlobal());
    myStage.setClockType(ClockType.THREAD);
    assertThat(captureNode.getClockType()).isEqualTo(ClockType.THREAD);
    myStage.setCapture(capture);
    // In THREAD clock type, selection should scale the interval based on thread-clock/wall-clock ratio [node's startTime, node's endTime].
    double threadToGlobal = 1 / captureNode.threadGlobalRatio();
    double threadSelectionStart = captureNode.getStartGlobal() +
                                  threadToGlobal * (captureNode.getStartThread() - timeline.getSelectionRange().getMin());
    double threadSelectionEnd = threadSelectionStart +
                                threadToGlobal * captureNode.getDuration();
    assertThat(threadSelectionStart).isWithin(eps).of(timeline.getSelectionRange().getMin());
    assertThat(threadSelectionEnd).isWithin(eps).of(timeline.getSelectionRange().getMax());

    myStage.setClockType(ClockType.GLOBAL);
    assertThat(captureNode.getClockType()).isEqualTo(ClockType.GLOBAL);
    // Just setting the clock type shouldn't change the selection range
    assertThat(threadSelectionStart).isWithin(eps).of(timeline.getSelectionRange().getMin());
    assertThat(threadSelectionEnd).isWithin(eps).of(timeline.getSelectionRange().getMax());
  }

  @Test
  public void testCaptureRangeConversion() throws InterruptedException, IOException {
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    myStage.setSelectedThread(myStage.getCapture().getMainThreadId());
    myStage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP);

    Range selection = myStage.getTimeline().getSelectionRange();
    double eps = 1e-5;
    assertThat(selection.getMin()).isWithin(eps).of(myStage.getCapture().getRange().getMin());
    assertThat(selection.getMax()).isWithin(eps).of(myStage.getCapture().getRange().getMax());

    assertThat(myStage.getCaptureDetails()).isInstanceOf(CaptureDetails.BottomUp.class);
    CaptureDetails.BottomUp details = (CaptureDetails.BottomUp)myStage.getCaptureDetails();

    Range detailsRange = details.getModel().getRange();

    // When ClockType.Global is used, the range of a capture details should the same as the selection range
    assertThat(myStage.getClockType()).isEqualTo(ClockType.GLOBAL);
    assertThat(selection.getMin()).isWithin(eps).of(detailsRange.getMin());
    assertThat(selection.getMax()).isWithin(eps).of(detailsRange.getMax());

    detailsRange.set(0, 10);
    assertThat(selection.getMin()).isWithin(eps).of(0);
    assertThat(selection.getMax()).isWithin(eps).of(10);

    selection.set(1, 5);
    assertThat(detailsRange.getMin()).isWithin(eps).of(1);
    assertThat(detailsRange.getMax()).isWithin(eps).of(5);
  }

  @Test
  public void settingACaptureAfterNullShouldSelectMainThread() throws IOException, ExecutionException, InterruptedException {
    assertThat(myStage.getSelectedThread()).isEqualTo(CaptureModel.NO_THREAD);
    assertThat(myStage.getCapture()).isNull();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);

    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    assertThat(capture).isNotNull();
    myStage.setAndSelectCapture(capture);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    // Capture main thread should be selected
    assertThat(myStage.getSelectedThread()).isEqualTo(capture.getMainThreadId());

    myStage.setCapture(null);
    // Profiler mode remains expanded so there's no jumping back and forth
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    // Thread selection is reset
    assertThat(myStage.getSelectedThread()).isEqualTo(CaptureModel.NO_THREAD);
  }

  @Test
  public void traceMissingDataShowsDialog() throws IOException, InterruptedException {
    // Select valid capture no dialog should be presented.
    myStage.getProfilerConfigModel().setProfilingConfiguration(FakeIdeProfilerServices.ATRACE_CONFIG);
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService,
                                             CpuProfilerTestUtils.traceFileToByteString(
                                               TestUtils.resolveWorkspacePath(ATRACE_DATA_FILE).toFile()));
    assertThat(myServices.getNotification()).isNull();

    // Select invalid capture we should see dialog.
    myTimer.setCurrentTimeNs(1); // Update the timer to give the second trace a different trace id.
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService,
                                             CpuProfilerTestUtils
                                               .traceFileToByteString(TestUtils.resolveWorkspacePath(ATRACE_MISSING_DATA_FILE).toFile()));
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.ATRACE_BUFFER_OVERFLOW);
  }

  @Test
  public void changingCaptureShouldKeepThreadSelection() throws IOException, ExecutionException, InterruptedException {
    CpuCapture capture1 = CpuProfilerTestUtils.getValidCapture();
    CpuCapture capture2 = CpuProfilerTestUtils.getValidCapture();
    assertThat(capture1).isNotEqualTo(capture2);

    myStage.setAndSelectCapture(capture1);
    // Capture main thread should be selected
    int mainThread = capture1.getMainThreadId();
    assertThat(myStage.getSelectedThread()).isEqualTo(mainThread);

    int otherThread = mainThread;
    // Select a thread other than main
    for (CpuThreadInfo thread : capture1.getThreads()) {
      if (thread.getId() != mainThread) {
        otherThread = thread.getId();
        break;
      }
    }

    assertThat(otherThread).isNotEqualTo(mainThread);
    myStage.setSelectedThread(otherThread);
    assertThat(myStage.getSelectedThread()).isEqualTo(otherThread);

    myStage.setAndSelectCapture(capture2);
    assertThat(myStage.getCapture()).isEqualTo(capture2);
    // Thread selection should be kept instead of selecting capture2 main thread.
    assertThat(myStage.getSelectedThread()).isEqualTo(otherThread);
  }

  @Test
  public void selectingAndDeselectingCaptureShouldNotMakeUiJump() throws IOException, ExecutionException, InterruptedException {
    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    assertThat(capture).isNotNull();

    myStage.setAndSelectCapture(capture);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);

    myStage.setCapture(null);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);

    myStage.setAndSelectCapture(capture);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
  }

  @Test
  public void testUsageTooltip() {
    myStage.enter();
    myStage.setTooltip(new CpuProfilerStageCpuUsageTooltip(myStage));
    assertThat(myStage.getTooltip()).isInstanceOf(CpuProfilerStageCpuUsageTooltip.class);
    CpuProfilerStageCpuUsageTooltip tooltip = (CpuProfilerStageCpuUsageTooltip)myStage.getTooltip();

    long tooltipTimeMs = 123456;
    long sampleIntervalMs = 100;
    int pid = myStage.getStudioProfilers().getSession().getPid();

    Cpu.CpuUsageData usageData1 = Cpu.CpuUsageData.newBuilder()
      .setEndTimestamp(TimeUnit.MILLISECONDS.toNanos(tooltipTimeMs - sampleIntervalMs))
      .setAppCpuTimeInMillisec(0)
      .setSystemCpuTimeInMillisec(0)
      .setElapsedTimeInMillisec(0)
      .build();
    Cpu.CpuUsageData usageData2 = Cpu.CpuUsageData.newBuilder()
      .setEndTimestamp(TimeUnit.MILLISECONDS.toNanos(tooltipTimeMs))
      .setAppCpuTimeInMillisec(10)
      .setSystemCpuTimeInMillisec(50)
      .setElapsedTimeInMillisec(sampleIntervalMs)
      .build();
    Cpu.CpuThreadData threadData = Cpu.CpuThreadData.newBuilder()
      .setTid(pid)
      .setName("FakeThread")
      .setState(Cpu.CpuThreadData.State.RUNNING)
      .build();
    long sessionStreamId = myStage.getStudioProfilers().getSession().getStreamId();
    myTransportService.addEventToStream(sessionStreamId, Common.Event.newBuilder()
      .setPid(pid)
      .setKind(Common.Event.Kind.CPU_USAGE)
      .setGroupId(pid)
      .setTimestamp(usageData1.getEndTimestamp())
      .setCpuUsage(usageData1)
      .build());
    myTransportService.addEventToStream(sessionStreamId, Common.Event.newBuilder()
      .setPid(pid)
      .setKind(Common.Event.Kind.CPU_USAGE)
      .setGroupId(pid)
      .setTimestamp(usageData2.getEndTimestamp())
      .setCpuUsage(usageData2)
      .build());
    myTransportService.addEventToStream(sessionStreamId, Common.Event.newBuilder()
      .setPid(pid)
      .setKind(Common.Event.Kind.CPU_THREAD)
      .setGroupId(pid)
      .setTimestamp(usageData1.getEndTimestamp())
      .setCpuThread(threadData)
      .build());

    long tooltipTimeUs = TimeUnit.MILLISECONDS.toMicros(tooltipTimeMs);
    myStage.getTimeline().getTooltipRange().set(tooltipTimeUs, tooltipTimeUs);
    CpuProfilerStage.CpuStageLegends legends = tooltip.getLegends();
    assertThat(legends.getCpuLegend().getName()).isEqualTo("App");
    assertThat(legends.getOthersLegend().getName()).isEqualTo("Others");
    assertThat(legends.getThreadsLegend().getName()).isEqualTo("Threads");
    assertThat(legends.getCpuLegend().getValue()).isEqualTo("10 %");
    assertThat(legends.getOthersLegend().getValue()).isEqualTo("40 %");
    assertThat(legends.getThreadsLegend().getValue()).isEqualTo("1");
  }

  @Test
  public void testThreadsTooltip() {
    Range viewRange = myStage.getTimeline().getViewRange();
    Range tooltipRange = myStage.getTimeline().getTooltipRange();

    viewRange.set(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(11));

    myStage.enter();
    myStage.setTooltip(new CpuThreadsTooltip(myStage.getTimeline()));
    assertThat(myStage.getTooltip()).isInstanceOf(CpuThreadsTooltip.class);
    CpuThreadsTooltip tooltip = (CpuThreadsTooltip)myStage.getTooltip();

    // Null thread series
    tooltip.setThread(null, null);
    assertThat(tooltip.getThreadName()).isNull();
    assertThat(tooltip.getThreadState()).isNull();

    // Thread series: [<1, running>, <8, dead>]
    CpuThreadStateDataSeries series =
      new CpuThreadStateDataSeries(myStage.getStudioProfilers().getClient().getTransportClient(),
                                   ProfilersTestData.SESSION_DATA.getStreamId(),
                                   ProfilersTestData.SESSION_DATA.getPid(),
                                   1,
                                   null);
    tooltip.setThread("Thread 1", series);

    assertThat(tooltip.getThreadName()).isEqualTo("Thread 1");

    // Tooltip before all data.
    long tooltipTimeUs = TimeUnit.SECONDS.toMicros(0);
    tooltipRange.set(tooltipTimeUs, tooltipTimeUs);
    assertThat(tooltip.getThreadState()).isNull();

    // Tooltip on first thread.
    tooltipTimeUs = TimeUnit.SECONDS.toMicros(5);
    tooltipRange.set(tooltipTimeUs, tooltipTimeUs);
    assertThat(tooltip.getThreadState()).isEqualTo(ThreadState.RUNNING);

    // Tooltip right on second thread.
    tooltipTimeUs = TimeUnit.SECONDS.toMicros(8);
    tooltipRange.set(tooltipTimeUs, tooltipTimeUs);
    assertThat(tooltip.getThreadState()).isEqualTo(ThreadState.DEAD);

    // Tooltip after all data. Because data don't contain end time so the last thread state lasts "forever".
    tooltipTimeUs = TimeUnit.SECONDS.toMicros(12);
    tooltipRange.set(tooltipTimeUs, tooltipTimeUs);
    assertThat(tooltip.getThreadState()).isEqualTo(ThreadState.DEAD);
  }

  @Test
  public void testCpuKernelTooltip() throws IOException {
    Range viewRange = myStage.getTimeline().getViewRange();
    Range tooltipRange = myStage.getTimeline().getTooltipRange();

    viewRange.set(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(11));
    CpuCapture cpuCapture = new AtraceParser(new MainProcessSelector("", 1, null))
      .parse(CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace"), 0);
    myStage.setCapture(cpuCapture);
    myStage.enter();
    myStage.setTooltip(new CpuKernelTooltip(myStage.getTimeline(), FAKE_PID));
    assertThat(myStage.getTooltip()).isInstanceOf(CpuKernelTooltip.class);
    CpuKernelTooltip tooltip = (CpuKernelTooltip)myStage.getTooltip();

    // Null series
    tooltip.setCpuSeries(0, null);
    assertThat(tooltip.getCpuThreadSliceInfo()).isNull();

    // Test Series
    tooltipRange.set(11, 11);
    List<SeriesData<CpuThreadSliceInfo>> cpuSeriesData = new ArrayList<>();
    cpuSeriesData.add(new SeriesData<>(5, CpuThreadSliceInfo.NULL_THREAD));
    cpuSeriesData.add(new SeriesData<>(10, new CpuThreadSliceInfo(0, "Test", 0, "Test")));
    LazyDataSeries<CpuThreadSliceInfo> series = new LazyDataSeries<>(() -> cpuSeriesData);
    tooltip.setCpuSeries(1, series);
    assertThat(tooltip.getCpuThreadSliceInfo().getProcessName()).isEqualTo("Test");

    // Tooltip before all data.
    long tooltipTimeUs = TimeUnit.SECONDS.toMicros(0);
    tooltipRange.set(tooltipTimeUs, tooltipTimeUs);
    assertThat(tooltip.getCpuThreadSliceInfo()).isNull();

    // Tooltip null process is null.
    tooltipRange.set(0, 9);
    assertThat(tooltip.getCpuThreadSliceInfo()).isNull();
  }

  @Test
  public void testElapsedTime() throws InterruptedException, IOException {
    // Before we capture, elapsed time should be unset (default set to 0)
    assertThat(myStage.currentTimeNs() - myStage.getCaptureStartTimeNs()).isEqualTo(0);
    Range dataRange = myStage.getTimeline().getDataRange();

    // Start capturing
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    // Increment 3 seconds on data range
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS * 3);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
    // Check that we're capturing for three seconds

    assertThat(myStage.currentTimeNs() - myStage.getCaptureStartTimeNs()).isEqualTo(TimeUnit.SECONDS.toNanos(3));

    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true, CpuProfilerTestUtils.readValidTrace());
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS * 2);
    long traceStartTime = (long)dataRange.getMax();
    myTimer.setCurrentTimeNs(TimeUnit.MICROSECONDS.toNanos(traceStartTime));
    // Start capturing again, this time for 10 seconds
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS * 10);

    // Check that we're capturing for 10 seconds.
    assertThat(myStage.currentTimeNs() - myStage.getCaptureStartTimeNs())
      .isEqualTo(TimeUnit.MICROSECONDS.toNanos((long)dataRange.getMax() - traceStartTime));
  }

  @Test
  public void exitingAndReEnteringStageAgainShouldPreserveProfilingTime() throws InterruptedException {
    // Set a non-zero start time to test non-default values
    Range dataRange = myStage.getTimeline().getDataRange();
    double currentMax = dataRange.getMax() + TimeUnit.SECONDS.toMicros(10);
    dataRange.setMax(currentMax);
    myTimer.setCurrentTimeNs(TimeUnit.MICROSECONDS.toNanos((long)currentMax));

    // Start capturing
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);

    // Increment 3 seconds
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS * 3);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);

    // Go back to monitor stage and go back to a new Cpu profiler stage
    myStage.getStudioProfilers().setStage(new StudioMonitorStage(myStage.getStudioProfilers()));
    CpuProfilerStage stage = new CpuProfilerStage(myStage.getStudioProfilers());
    myStage.getStudioProfilers().setStage(stage);
    // Trigger an update to kick off the InProgressTraceHandler which syncs the capture state.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Make sure we're capturing
    assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);

    // Check that we're capturing for four seconds total
    assertThat(stage.currentTimeNs() - stage.getCaptureStartTimeNs()).isEqualTo(TimeUnit.SECONDS.toNanos(4));
  }

  @Test
  public void stopProfilerIsConsistentToStartProfiler() throws InterruptedException, IOException {
    myStage.getProfilerConfigModel().setProfilingConfiguration(FakeIdeProfilerServices.SIMPLEPERF_CONFIG);
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService,
                                             CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));

    assertThat(myStage.getCapture().getType()).isEqualTo(Cpu.CpuTraceType.SIMPLEPERF);

    myStage.getProfilerConfigModel().setProfilingConfiguration(FakeIdeProfilerServices.ART_SAMPLED_CONFIG);
    // Start capturing with ART
    myTimer.setCurrentTimeNs(1); // Update the timer to give the second trace a different trace id.
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    // Change the profiling configurations in the middle of the capture and stop capturing
    myStage.getProfilerConfigModel().setProfilingConfiguration(FakeIdeProfilerServices.SIMPLEPERF_CONFIG);
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true, CpuProfilerTestUtils.readValidTrace());
    // Stop profiler should be the same as the one passed in the start request
    assertThat(myStage.getCapture().getType()).isEqualTo(Cpu.CpuTraceType.ART);
  }

  @Test
  public void suggestedProfilingConfigurationDependsOnNativePreference() {
    // Make sure simpleperf is supported by setting an O device.
    addAndSetDevice(AndroidVersion.VersionCodes.O, "Any Serial");

    myServices.setNativeProfilingConfigurationPreferred(false);
    myStage = new CpuProfilerStage(myStage.getStudioProfilers());
    myStage.enter();
    // ART Sampled should be the default configuration when there is no preference for a native config.
    assertThat(
      myStage.getProfilerConfigModel().getProfilingConfiguration().getName()).isEqualTo(FakeIdeProfilerServices.FAKE_ART_SAMPLED_NAME);

    myServices.setNativeProfilingConfigurationPreferred(true);
    myStage = new CpuProfilerStage(myStage.getStudioProfilers());
    myStage.enter();
    // Simpleperf should be the default configuration when a native config is preferred.
    assertThat(
      myStage.getProfilerConfigModel().getProfilingConfiguration().getName()).isEqualTo(FakeIdeProfilerServices.FAKE_SIMPLEPERF_NAME);
  }

  @Test
  public void exitingStageAndEnteringAgainShouldPreserveCaptureState() throws InterruptedException, IOException {
    myStage.getProfilerConfigModel().setProfilingConfiguration(FakeIdeProfilerServices.SIMPLEPERF_CONFIG);
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);

    // Go back to monitor stage and go back to a new Cpu profiler stage
    myStage.getStudioProfilers().setStage(new StudioMonitorStage(myStage.getStudioProfilers()));
    CpuProfilerStage stage = new CpuProfilerStage(myStage.getStudioProfilers());
    myStage.getStudioProfilers().setStage(stage);
    // Trigger an update to kick off the InProgressTraceHandler which syncs the capture state.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Make sure we're capturing
    assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
    CpuProfilerTestUtils.stopCapturing(stage, myCpuService, myTransportService, true,
                                       CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(stage.getCapture().getType()).isEqualTo(Cpu.CpuTraceType.SIMPLEPERF);

    // Make sure we tracked the correct configuration
    ProfilingConfiguration trackedConfig =
      ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata().getProfilingConfiguration();
    assertThat(trackedConfig.getTraceType()).isEqualTo(Cpu.CpuTraceType.SIMPLEPERF);
  }

  @Test
  public void transitsToIdleWhenApiInitiatedTracingEnds() {
    // API-initiated tracing starts.
    Cpu.CpuTraceConfiguration apiTracingConfig = Cpu.CpuTraceConfiguration.newBuilder()
      .setInitiationType(Cpu.TraceInitiationType.INITIATED_BY_API)
      .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder().setTraceType(Cpu.CpuTraceType.ART))
      .build();
    addTraceInfoHelper(1, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), 100, -1, apiTracingConfig);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);

    myCpuService.clearTraceInfo();
    addTraceInfoHelper(1, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), 100, 101, apiTracingConfig);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
  }

  @Test
  public void apiInitiatedCaptureUsageTracking() {
    // Trace 1: not API-initiated. Shouldn't have API-tracing usage.
    addTraceInfoHelper(1, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), 10, 11,
                       Cpu.CpuTraceConfiguration.newBuilder().setInitiationType(Cpu.TraceInitiationType.INITIATED_BY_UI).build());

    final FakeFeatureTracker featureTracker = (FakeFeatureTracker)myServices.getFeatureTracker();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(featureTracker.getApiTracingUsageCount()).isEqualTo(0);

    // Trace 2: API-initiated
    myCpuService.clearTraceInfo();
    addTraceInfoHelper(2, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), 10, 11,
                       Cpu.CpuTraceConfiguration.newBuilder().setInitiationType(Cpu.TraceInitiationType.INITIATED_BY_API).build());

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(featureTracker.getApiTracingUsageCount()).isGreaterThan(0);
    assertThat(featureTracker.getLastCpuAPiTracingPathProvided()).isTrue();
  }

  @Test
  public void setAndSelectCaptureShouldStopStreamingMode() throws IOException, ExecutionException, InterruptedException {
    // Capture has changed, keeps the same type of details
    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    myStage.getTimeline().setIsPaused(false);
    myStage.getTimeline().setStreaming(true);
    myStage.setAndSelectCapture(capture);
    assertThat(myStage.getTimeline().isStreaming()).isFalse();
  }

  @Test
  public void captureStageTransitionTest() throws Exception {
    myServices.enableCpuCaptureStage(false);
    myServices.enableEventsPipeline(true);
    // Needs to be set true else null is inserted into the capture parser.
    myServices.setShouldProceedYesNoDialog(true);
    // Try to parse a simpleperf trace with ART config.
    ProfilingConfiguration config = new ArtSampledConfiguration("My Config");
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    assertThat(myStage.getStudioProfilers().getStage()).isEqualTo(myStage);
    myServices.enableCpuCaptureStage(true);
    myTimer.setCurrentTimeNs(1);  // Update the timer to generate a different trace id for the second trace.
    // Don't pass a capture to the test utils for now as we don't want to block on parsing.
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    // Force a flush of the UI event queue.
    assertThat(myStage.getStudioProfilers().getStage().getClass()).isAssignableTo(CpuCaptureStage.class);
  }

  @Test
  public void configurationShouldBeTheOnGoingProfilingAfterExitAndEnter() throws InterruptedException {
    ProfilingConfiguration testConfig = new SimpleperfConfiguration(FakeIdeProfilerServices.FAKE_SIMPLEPERF_NAME);
    myStage.getProfilerConfigModel().setProfilingConfiguration(testConfig);
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    assertThat(myStage.getProfilerConfigModel().getProfilingConfiguration()).isEqualTo(testConfig);
    myStage.exit();

    // Enter CpuProfilerStage again.
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myServices, myTimer);
    CpuProfilerStage newStage = new CpuProfilerStage(profilers);
    newStage.getStudioProfilers().setStage(newStage);
    // Trigger an update to kick off the InProgressTraceHandler which syncs the capture state and configuration.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(newStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
    assertThat(newStage.getProfilerConfigModel().getProfilingConfiguration()).isEqualTo(testConfig);
  }

  @Test
  public void configurationShouldBeTheLastSelectedOneAfterExitAndEnter() {
    ProfilingConfiguration testConfig = new SimpleperfConfiguration(FakeIdeProfilerServices.FAKE_SIMPLEPERF_NAME);
    myStage.getProfilerConfigModel().setProfilingConfiguration(testConfig);
    assertThat(myStage.getProfilerConfigModel().getProfilingConfiguration()).isEqualTo(testConfig);
    myStage.exit();

    // Enter CpuProfilerStage again.
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myServices, myTimer);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    CpuProfilerStage newStage = new CpuProfilerStage(profilers);
    newStage.getStudioProfilers().setStage(newStage);

    assertThat(newStage.getProfilerConfigModel().getProfilingConfiguration()).isEqualTo(testConfig);
  }

  @Test
  public void selectARangeWithNoCapturesShouldKeepCurrentCaptureSelected() throws InterruptedException, IOException {
    assertThat(myStage.getCapture()).isNull();
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    assertThat(myStage.getCapture()).isNotNull();
    CpuCapture capture = myStage.getCapture();

    Range selectionRange = myStage.getTimeline().getSelectionRange();
    // Select an area before the capture.
    selectionRange.set(capture.getRange().getMin() - 20, capture.getRange().getMin() - 10);
    // Last selected capture should remain selected.
    assertThat(myStage.getCapture()).isEqualTo(capture);
  }

  /**
   * Simulate the scenario of calling {@link CpuProfilerStage#setAndSelectCapture(long)} before calling
   * {@link CpuProfilerStage#stopCapturing()}.
   */
  @Test
  public void captureShouldBeParsedOnlyOnce() throws IOException, InterruptedException {
    assertThat(myStage.getCapture()).isNull();
    long traceId =
      CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    CpuCapture capture = myStage.getCapture();
    assertThat(capture).isNotNull();
    myStage.setCaptureState(CpuProfilerStage.CaptureState.IDLE);
    myStage.setCapture(null);

    // Capture should be the same as the one obtained by myStage.getCapture(...),
    // because we should not parse the trace into another CpuCapture object.
    myStage.setAndSelectCapture(traceId);
    assertThat(myStage.getCapture()).isEqualTo(capture);
  }

  /**
   * Simulate the scenario of calling {@link CpuProfilerStage#stopCapturing()} before calling
   * {@link CpuProfilerStage#getCaptureFuture(long)}.
   */
  @Test
  public void captureShouldBeParsedOnlyOnceStopCapturingBefore() throws IOException, ExecutionException, InterruptedException {
    assertThat(myStage.getCapture()).isNull();
    // stopCapturing() should create a capture with id == 1
    long traceId =
      CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    CpuCapture capture = myStage.getCapture();
    assertThat(capture).isNotNull();

    // Capture should be the same as the one created by stopCapturing(),
    // because we should not parse the trace into another CpuCapture object.
    assertThat(myStage.getCaptureFuture(traceId).get()).isEqualTo(capture);
  }

  @Test
  public void getCaptureFutureShouldTellParserToStartParsing() throws InterruptedException, IOException {
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    assertThat(myStage.getCaptureParser().isParsing()).isFalse();

    // Complete a capture once so we have valid data in the service.
    long traceId =
      CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    // Start a new stage so the capture has to be manually selected and parse.
    myStage = new CpuProfilerStage(myStage.getStudioProfilers());
    myStage.getStudioProfilers().setStage(myStage);
    AspectObserver observer = new AspectObserver();
    CountDownLatch latch = CpuProfilerTestUtils.waitForParsingStartFinish(myStage, observer);
    myStage.getCaptureFuture(traceId);
    latch.countDown();

    // Parsing should set the profiler to EXPANDED
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
  }

  @Test
  public void setCaptureWhileCapturingShouldParseAndContinueInCapturingState() throws InterruptedException, IOException {
    // First generate a finished capture that we can select
    long traceId =
      CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    myStage = new CpuProfilerStage(myStage.getStudioProfilers());
    // Invoke enter to update the profiling configuration model
    myStage.getStudioProfilers().setStage(myStage);
    myTimer.setCurrentTimeNs(2);  // Update the timer to generate a different trace id for the second trace.
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);

    // Select the previous capture
    AspectObserver observer = new AspectObserver();
    CountDownLatch parseLatch = CpuProfilerTestUtils.waitForParsingStartFinish(myStage, observer);
    myStage.setAndSelectCapture(traceId);
    parseLatch.await();
    assertThat(myStage.getCaptureState()).isEqualTo(CaptureState.CAPTURING);
    assertThat(myStage.getCaptureParser().isParsing()).isFalse();
  }

  @Test
  public void setCaptureWhileIdleShouldParseAndStayInIdleState() throws InterruptedException, IOException {
    // First generate a finished capture that we can select
    long traceId =
      CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    myStage = new CpuProfilerStage(myStage.getStudioProfilers());
    myStage.getStudioProfilers().setStage(myStage);
    assertThat(myStage.getCaptureState()).isEqualTo(CaptureState.IDLE);

    // Select the previous capture
    AspectObserver observer = new AspectObserver();
    CountDownLatch parseLatch = CpuProfilerTestUtils.waitForParsingStartFinish(myStage, observer);
    myStage.setAndSelectCapture(traceId);
    parseLatch.await();
    assertThat(myStage.getCaptureState()).isEqualTo(CaptureState.IDLE);
    assertThat(myStage.getCaptureParser().isParsing()).isFalse();
  }

  @Test
  public void setCaptureShouldUseTraceType() throws IOException, InterruptedException {
    myServices.enableCpuCaptureStage(true);
    // Capture a new trace.
    long traceId =
      CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    // Now change the recording type configuration.
    myStage.getProfilerConfigModel().setProfilingConfiguration(new SimpleperfConfiguration("new-simpleperf"));
    // Load the previously recorded trace. It should be parsed as the trace's own type, not the selected configuration type.
    myStage.setAndSelectCapture(traceId);
    assertThat(myStage.getStudioProfilers().getStage()).isInstanceOf(CpuCaptureStage.class);
  }

  @Test
  public void cpuMetadataSuccessfulCapture() throws InterruptedException, IOException {
    CpuCaptureParser.clearPreviouslyLoadedCaptures();
    ArtSampledConfiguration config = new ArtSampledConfiguration("My Config");
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.SUCCESS);
    ArtSampledConfiguration metadataConfig = (ArtSampledConfiguration)metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getTraceType()).isEqualTo(Cpu.CpuTraceType.ART);
    assertThat(metadata.getParsingTimeMs()).isGreaterThan(0L);
    assertThat(metadata.getStoppingTimeMs()).isEqualTo(FakeCpuService.FAKE_STOPPING_TIME_MS);
    assertThat(metadata.getRecordDurationMs()).isGreaterThan(0L);
    assertThat(metadata.getCaptureDurationMs()).isGreaterThan(0L);
    assertThat(metadata.getTraceFileSizeBytes()).isGreaterThan(0);
  }

  @Test
  public void cpuMetadataFailureStopCapture() throws InterruptedException {
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ArtSampledConfiguration config = new ArtSampledConfiguration("My Config");
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    long captureStartTime = (long)myStage.getTimeline().getDataRange().getMax();
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);

    // Increment 3 seconds on data range to simulate time has passed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, false, null);
    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.STOP_FAILED_STOP_COMMAND_FAILED);
    // Profiling Configurations should remain the same
    ArtSampledConfiguration metadataConfig = (ArtSampledConfiguration)metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getTraceType()).isEqualTo(Cpu.CpuTraceType.ART);
    //assertThat(metadataConfig.getMode()).isEqualTo(Cpu.CpuTraceMode.SAMPLED);
    // Capture duration is calculated from the elapsed time since recording has started.
    // Note the legacy pipeline would generate two instances of metadata. First, it gets the error status from
    // the StopProfilingApp rpc right away. Second, just like unified pipeline, InProgressTraceHandler will detect
    // the latest trace info and generate a second piece of metadata. Compared to the first metadata, the second one
    // happens after wait for the extra tick.
    long captureDuration = (long)myStage.getTimeline().getDataRange().getMax() - captureStartTime;
    assertThat(metadata.getCaptureDurationMs()).isEqualTo(TimeUnit.MICROSECONDS.toMillis(captureDuration));
    // Trace was not generated, so trace size, parsing time and recording duration should be 0 (unset)
    assertThat(metadata.getParsingTimeMs()).isEqualTo(0);
    assertThat(metadata.getRecordDurationMs()).isEqualTo(0);
    assertThat(metadata.getTraceFileSizeBytes()).isEqualTo(0);
  }

  @Test
  public void cpuMetadataFailureParsing() throws InterruptedException, IOException {
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ArtSampledConfiguration config = new ArtSampledConfiguration("My Config");
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    // Simulate a 3 second capture.
    CpuProfilerTestUtils
      .stopCapturing(myStage, myCpuService, myTransportService, true, CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"),
                     TimeUnit.SECONDS.toNanos(3));

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_ERROR);
    ArtSampledConfiguration metadataConfig = (ArtSampledConfiguration)metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getTraceType()).isEqualTo(Cpu.CpuTraceType.ART);
    // Trace was generated, so trace size should be greater than 0
    assertThat(metadata.getTraceFileSizeBytes()).isGreaterThan(0);
    // Capture duration is calculated from the elapsed time since recording has started.
    assertThat(metadata.getCaptureDurationMs()).isEqualTo(TimeUnit.SECONDS.toMillis(3));
    // Trace was not parsed correctly, so parsing time and recording duration should be 0 (unset)
    assertThat(metadata.getParsingTimeMs()).isEqualTo(0);
    assertThat(metadata.getRecordDurationMs()).isEqualTo(0);
  }

  @Test
  public void cpuMetadataFailureUserAbort() throws InterruptedException {
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ArtSampledConfiguration config = new ArtSampledConfiguration("My Config");
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    ByteString largeTraceFile = ByteString.copyFrom(new byte[CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1]);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    myServices.setShouldProceedYesNoDialog(false);

    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    // Simulate a 3 second capture.
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true, largeTraceFile, TimeUnit.SECONDS.toNanos(3));

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING);
    // Profiling Configurations should remain the same.
    ArtSampledConfiguration metadataConfig = (ArtSampledConfiguration)metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getTraceType()).isEqualTo(Cpu.CpuTraceType.ART);
    // Trace was generated, so trace size should be greater than 0
    assertThat(metadata.getTraceFileSizeBytes()).isGreaterThan(0);
    // Capture duration is calculated from the elapsed time since recording has started.
    assertThat(metadata.getCaptureDurationMs()).isEqualTo(TimeUnit.SECONDS.toMillis(3));
    // Trace was not parsed at all, so parsing time and recording duration should be 0 (unset)
    assertThat(metadata.getParsingTimeMs()).isEqualTo(0);
    assertThat(metadata.getRecordDurationMs()).isEqualTo(0);
  }

  @Test
  @Ignore("TODO (b/140296690) Need to discuss how we handle preprocessing failures now it is a preprocessor.")
  public void cpuMetadataFailurePreProcess() throws InterruptedException, IOException {
    // Make sure the TracePreProcessor fails to pre-process the trace
    ((FakeTracePreProcessor)myServices.getTracePreProcessor()).setFailedToPreProcess(true);
    // Select a simpleperf configuration
    ProfilingConfiguration config = new SimpleperfConfiguration("My Config");
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    // Use a trace that is not a raw simpleperf trace. That should cause pre-process to return a failure.
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true,
                                       CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.PREPROCESS_FAILURE);
    // We should still log the trace size if we fail to pre-process. As we're using "simpleperf.trace", the size should be greater than 0.
    assertThat(metadata.getTraceFileSizeBytes()).isGreaterThan(0);
  }

  @Test
  public void parsingFailureIsNotifiedToUi() throws InterruptedException, IOException {
    // Start an ART capturing successfully
    ArtSampledConfiguration config = new ArtSampledConfiguration("My Config");
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    // Force the return of a simpleperf. As we started an ART capture, the capture parsing should fail.
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true,
                                       CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    // As parsing has failed, capture should be null.
    assertThat(myStage.getCapture()).isNull();
    // Sanity check to see if we reached the final capture state
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    // Sanity check to verify we're not parsing anymore
    assertThat(myStage.getCaptureParser().isParsing()).isFalse();
  }

  @Test
  public void startCapturingJumpsToLiveData() throws InterruptedException, IOException {
    StreamingTimeline timeline = myStage.getTimeline();
    timeline.setStreaming(false);
    assertThat(timeline.isStreaming()).isFalse();

    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    assertThat(timeline.isStreaming()).isTrue();
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true, CpuProfilerTestUtils.readValidTrace());
    assertThat(timeline.isStreaming()).isFalse();

    // Sanity test to check that start recording sets streaming again to true
    myTimer.setCurrentTimeNs(1);  // update the timer to generate a different trace id for the second trace.
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    assertThat(timeline.isStreaming()).isTrue();
  }

  @Test
  public void captureNavigationChangesCaptureSelection() throws InterruptedException, IOException {
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    CpuCapture capture1 = myStage.getCapture();

    myTimer.setCurrentTimeNs(1);  // update the timer to generate a different trace id for the second trace.
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    CpuCapture capture2 = myStage.getCapture();

    // Sanity check to show we have different captures.
    assertThat(capture1).isNotEqualTo(capture2);

    myStage.setCapture(null);

    // We have 2 captures where we can navigate to. When nothing is selected, we should be able to navigate to the first one.
    assertThat(myStage.getTraceIdsIterator().hasNext()).isTrue();
    myStage.navigateNext();
    assertThat(myStage.getCapture()).isEqualTo(capture1);
    // Now we can still navigate to the second capture.
    assertThat(myStage.getTraceIdsIterator().hasNext()).isTrue();
    myStage.navigateNext();
    assertThat(myStage.getCapture()).isEqualTo(capture2);
    // We're already selecting the last capture and can't navigate next
    assertThat(myStage.getTraceIdsIterator().hasNext()).isFalse();

    myStage.setCapture(null);

    // We have 2 captures where we can navigate to. When nothing is selected, we should be able to navigate to the last one.
    assertThat(myStage.getTraceIdsIterator().hasPrevious()).isTrue();
    myStage.navigatePrevious();
    assertThat(myStage.getCapture()).isEqualTo(capture2);
    // Now we can still navigate to the first capture.
    assertThat(myStage.getTraceIdsIterator().hasPrevious()).isTrue();
    myStage.navigatePrevious();
    assertThat(myStage.getCapture()).isEqualTo(capture1);
    // We're already selecting the first capture and can't navigate previous
    assertThat(myStage.getTraceIdsIterator().hasPrevious()).isFalse();
  }

  @Test
  public void captureNavigationEnabledInSessionsWithTraces() throws IOException, InterruptedException {
    // There are no traces/captures in the current session. We can't navigate anywhere.
    assertThat(myStage.getTraceIdsIterator().hasNext()).isFalse();
    assertThat(myStage.getTraceIdsIterator().hasPrevious()).isFalse();

    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    myStage = new CpuProfilerStage(myStage.getStudioProfilers());

    // Verify we can now navigate. Note we didn't have to parse any captures. The model should fetch all the trace info when it's created.
    assertThat(myStage.getTraceIdsIterator().hasNext()).isTrue();
    assertThat(myStage.getTraceIdsIterator().hasPrevious()).isTrue();
  }

  @Test
  public void testHasUserUsedCapture() throws InterruptedException {
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(0);
    assertThat(myStage.hasUserUsedCpuCapture()).isFalse();
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(1);
    assertThat(myStage.hasUserUsedCpuCapture()).isTrue();
  }

  @Test
  public void startCapturingFailureShowsErrorBalloon() throws IOException, ExecutionException, InterruptedException {
    myStage.setCapture(CpuProfilerTestUtils.getValidCapture());
    assertThat(myStage.getCapture()).isNotNull();
    // Start a failing capture
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, false);
    // Sanity check to see if we reached the final capture state
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.CAPTURE_START_FAILURE);
    assertThat(myStage.getCapture()).isNull();
  }

  @Test
  public void stopCapturingFailureShowsErrorBalloon() throws IOException, ExecutionException, InterruptedException {
    myStage.setCapture(CpuProfilerTestUtils.getValidCapture());
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ProfilingConfiguration config = new ArtSampledConfiguration("My Config");
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    assertThat(myStage.getCapture()).isNotNull();
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, false, null);
    // Sanity check to see if we reached the final capture state
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.CAPTURE_STOP_FAILURE);
    assertThat(myStage.getCapture()).isNull();
  }

  @Test
  public void captureParsingFailureShowsErrorBalloon() throws InterruptedException, IOException {
    ProfilingConfiguration config = new ArtSampledConfiguration("My Config");
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);

    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true,
                                       CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    // Sanity check to see if we reached the final capture state
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.PARSING_FAILURE);
  }

  @Test
  @Ignore("TODO (b/140296690) Need to discuss how we handle preprocessing failures now it is a preprocessor.")
  public void tracePreProcessingFailureShowsErrorBalloon() throws InterruptedException, IOException {
    // Make sure the TracePreProcessor fails to pre-process the trace
    ((FakeTracePreProcessor)myServices.getTracePreProcessor()).setFailedToPreProcess(true);
    // Select a simpleperf configuration
    ProfilingConfiguration config = new ArtSampledConfiguration("My Config");
    // Use a trace that is not a raw simpleperf trace. That should cause pre-process to return a failure.
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true,
                                       CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));

    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.PREPROCESS_FAILURE);

    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    // Intuitively, capture successfully would set a valid capture. However, failing to pre-process sets the capture to null
    assertThat(myStage.getCapture()).isNull();
  }

  @Test
  public void abortParsingRecordedTraceFileShowsABalloon() throws InterruptedException {
    myServices.setShouldProceedYesNoDialog(false);
    ByteString largeTraceFile = ByteString.copyFrom(new byte[CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1]);
    CpuProfilerTestUtils.startCapturing(myStage, myCpuService, myTransportService, true);
    CpuProfilerTestUtils.stopCapturing(myStage, myCpuService, myTransportService, true, largeTraceFile);

    // We should show a balloon saying the parsing was aborted, because FakeParserCancelParsing emulates a cancelled parsing task
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.PARSING_ABORTED);
  }

  @Test
  public void exitStageShouldCallParserAbort() {
    StudioProfilers profilers = myStage.getStudioProfilers();

    FakeParserCancelParsing parser = new FakeParserCancelParsing(myServices);
    CpuProfilerStage stage = new CpuProfilerStage(profilers, parser);
    stage.enter();
    assertThat(parser.isAbortParsingCalled()).isFalse();
    stage.exit();
    assertThat(parser.isAbortParsingCalled()).isTrue();
  }

  @Test
  public void testCaptureFilterFeatureTrack() throws InterruptedException, IOException {
    final FakeFeatureTracker tracker = (FakeFeatureTracker)myServices.getFeatureTracker();

    // Capture a trace to apply filter on.
    CpuProfilerTestUtils
      .captureSuccessfully(myStage, myCpuService, myTransportService, CpuProfilerTestUtils.readValidTrace());

    FilterModel filterModel = new FilterModel();
    Filter filter = Filter.EMPTY_FILTER;
    filterModel.setFilter(filter);

    myStage.applyCaptureFilter(filter);

    FilterMetadata filterMetadata = tracker.getLastFilterMetadata();
    assertThat(filterMetadata).isNotNull();
    assertThat(filterMetadata.getFilterTextLength()).isEqualTo(0);
    assertThat(filterMetadata.getFeaturesUsed()).isEqualTo(0);

    // Test with some filter features and non empty text

    filter = new Filter("some", true, true);
    filterModel.setFilter(filter);
    myStage.applyCaptureFilter(filter);
    filterMetadata = tracker.getLastFilterMetadata();
    assertThat(filterMetadata).isNotNull();
    assertThat(filterMetadata.getFilterTextLength()).isEqualTo(4);
    assertThat(filterMetadata.getFeaturesUsed()).isEqualTo(FilterMetadata.MATCH_CASE | FilterMetadata.IS_REGEX);
  }
  @Test
  public void changingCaptureStateUpdatesOptionsModel() throws IOException, InterruptedException {
    assertThat(myStage.getCapture()).isNull();
    assertThat(myStage.getRecordingModel().isRecording()).isFalse();
    myStage.setCaptureState(CaptureState.CAPTURING);
    assertThat(myStage.getRecordingModel().isRecording()).isTrue();

  }

  private void addAndSetDevice(int featureLevel, String serial) {
    int deviceId = serial.hashCode();
    Common.Device device = Common.Device.newBuilder()
      .setDeviceId(deviceId)
      .setFeatureLevel(featureLevel)
      .setSerial(serial)
      .setState(Common.Device.State.ONLINE).build();
    Common.Process process = Common.Process.newBuilder()
      .setPid(FAKE_PID)
      .setDeviceId(deviceId)
      .setState(Common.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    myTransportService.addDevice(device);
    // Adds at least one ALIVE process as well. Otherwise, StudioProfilers would prefer selecting a device that has live processes.
    myTransportService.addProcess(device, process);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new device to be picked up
    myStage.getStudioProfilers().setProcess(device, process);
    // Setting the device will change the stage. We need to go back to CpuProfilerStage
    myStage.getStudioProfilers().setStage(myStage);
  }

  private void addTraceInfoHelper(long traceId,
                                  long streamId,
                                  int pid,
                                  long startTimestampNs,
                                  long endTimestampNs,
                                  Cpu.CpuTraceConfiguration configuration) {
    Cpu.CpuTraceInfo info = Cpu.CpuTraceInfo.newBuilder()
      .setTraceId(traceId)
      .setFromTimestamp(startTimestampNs)
      .setToTimestamp(endTimestampNs)
      .setConfiguration(configuration)
      .build();
    if (myServices.getFeatureConfig().isUnifiedPipelineEnabled()) {
      Common.Event.Builder traceEventBuilder = Common.Event.newBuilder()
        .setGroupId(traceId)
        .setPid(pid)
        .setKind(Common.Event.Kind.CPU_TRACE);
      myTransportService.addEventToStream(streamId, traceEventBuilder
        .setTimestamp(startTimestampNs)
        .setCpuTrace(Cpu.CpuTraceData.newBuilder()
                       .setTraceStarted(Cpu.CpuTraceData.TraceStarted.newBuilder()
                                          .setTraceInfo(info)
                                          .build())
                       .build())
        .build());
      if (endTimestampNs != -1) {
        myTransportService.addEventToStream(streamId, traceEventBuilder
          .setTimestamp(endTimestampNs)
          .setCpuTrace(Cpu.CpuTraceData.newBuilder()
                         .setTraceEnded(Cpu.CpuTraceData.TraceEnded.newBuilder().setTraceInfo(info).build())
                         .build())
          .build());
      }
    }
    else {
      myCpuService.addTraceInfo(info);
    }
  }

  /**
   * An instance of {@link CpuCaptureParser} that will always cancel the {@link CompletableFuture<CpuCapture>} task responsible for parsing
   * a trace into a {@link CpuCapture}. This way, we can test the behavior of {@link CpuProfilerStage} when such scenario happens.
   */
  private static class FakeParserCancelParsing extends CpuCaptureParser {

    private boolean myAbortParsingCalled = false;

    FakeParserCancelParsing(@NotNull IdeProfilerServices services) {
      super(services);
    }

    @Override
    public void abortParsing() {
      myAbortParsingCalled = true;
      super.abortParsing();
    }

    @NotNull
    @Override
    public CompletableFuture<CpuCapture> parse(
      @NotNull File traceFile, long traceId, @Nullable Cpu.CpuTraceType preferredProfilerType, int idHint, @Nullable String nameHint) {
      CompletableFuture<CpuCapture> capture = new CompletableFuture<>();
      capture.cancel(true);
      return capture;
    }

    public boolean isAbortParsingCalled() {
      return myAbortParsingCalled;
    }
  }
}
