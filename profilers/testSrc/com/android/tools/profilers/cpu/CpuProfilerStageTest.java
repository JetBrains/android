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

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME;
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
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.filter.FilterModel;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.FakeFeatureTracker;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioMonitorStage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import com.android.tools.profilers.analytics.FilterMetadata;
import com.android.tools.profilers.cpu.atrace.AtraceParser;
import com.android.tools.profilers.cpu.atrace.CpuKernelTooltip;
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.cpu.capturedetails.CaptureModel;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.network.FakeNetworkService;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.google.common.collect.Iterators;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CpuProfilerStageTest extends AspectObserver {
  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myFakeTransportService = new FakeTransportService(myTimer);
  private final FakeCpuService myCpuService = new FakeCpuService();
  private final FakeMemoryService myMemoryService = new FakeMemoryService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService, myFakeTransportService, new FakeProfilerService(myTimer),
                        myMemoryService, new FakeEventService(), FakeNetworkService.newBuilder().build());
  private ProfilerClient myProfilerClient = new ProfilerClient(myGrpcChannel.getName());

  private CpuProfilerStage myStage;

  private FakeIdeProfilerServices myServices;

  private boolean myCaptureDetailsCalled;

  @Before
  public void setUp() {
    myServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myServices, myTimer);
    // One second must be enough for new devices (and processes) to be picked up
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStage = new CpuProfilerStage(profilers);
    myStage.getStudioProfilers().setStage(myStage);
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
  public void testCpuUsageDataSource() {
    // When unified pipeline is disabled, we use custom CpuUsageDataSeries.
    myServices.enableEventsPipeline(false);
    myStage = new CpuProfilerStage(new StudioProfilers(myProfilerClient, myServices, myTimer));
    assertThat(myStage.getCpuUsage().getCpuSeries().getDataSeries()).isInstanceOf(CpuUsageDataSeries.class);
    assertThat(myStage.getCpuUsage().getThreadsCountSeries().getDataSeries()).isInstanceOf(LegacyCpuThreadCountDataSeries.class);

    // When unified pipeline is enabled, we use UnifiedEventDataSeries.
    myServices.enableEventsPipeline(true);
    myStage = new CpuProfilerStage(new StudioProfilers(myProfilerClient, myServices, myTimer));
    assertThat(myStage.getCpuUsage().getCpuSeries().getDataSeries()).isInstanceOf(UnifiedEventDataSeries.class);
    assertThat(myStage.getCpuUsage().getThreadsCountSeries().getDataSeries()).isInstanceOf(CpuThreadCountDataSeries.class);
  }

  @Test
  public void testStartCapturing() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Start a successful capture
    startCapturingSuccess();

    // Start a failing capture
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.FAILURE);
    startCapturing();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
  }

  @Test
  public void startCapturingInstrumented() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS);
    myServices.setPrePoolExecutor(() -> assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.STARTING));
    // Start a capture using INSTRUMENTED mode
    ProfilingConfiguration instrumented = new ProfilingConfiguration("My Instrumented Config",
                                                                     CpuProfiler.CpuProfilerType.ART,
                                                                     CpuProfiler.CpuProfilerMode.INSTRUMENTED);
    myStage.getProfilerConfigModel().setProfilingConfiguration(instrumented);
    startCapturing();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
  }

  @Test
  public void testStopCapturingInvalidTrace() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Start a successful capture
    startCapturingSuccess();

    // Stop capturing, but don't include a trace in the response.
    myServices.setOnExecute(() -> {
      // First, the main executor is going to be called to execute stopCapturingCallback which should tell CpuCaptureParser to start parsing
      assertThat(myStage.getCaptureParser().isParsing()).isTrue();
      // Then, the next time the main executor is called, it will try to parse the capture unsuccessfully and set the capture state to IDLE
      myServices.setOnExecute(() -> {
        assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
        // Capture was stopped successfully, but capture should still be null as the response has no valid trace
        assertThat(myStage.getCapture()).isNull();
      });
    });
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(false);
    stopCapturing();
  }

  @Test
  public void testStopCapturingInvalidTraceFailureStatus() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture unsuccessfully
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.CANNOT_READ_FILE);
    myCpuService.setValidTrace(false);
    myServices.setOnExecute(() -> {
      assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
      assertThat(myStage.getCapture()).isNull();
    });
    stopCapturing();
  }

  @Test
  public void testStopCapturingValidTraceFailureStatus() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture unsuccessfully, but with a valid trace
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.STILL_PROFILING_AFTER_STOP);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    myServices.setOnExecute(() -> {
      assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
      // Despite the fact of having a valid trace, we first check for the response status.
      // As it wasn't SUCCESS, capture should not be set.
      assertThat(myStage.getCapture()).isNull();
    });
    stopCapturing();
  }

  @Test
  public void testStopCapturingSuccessfully() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    captureSuccessfully();
  }

  @Test
  public void testJumpToLiveIfOngoingRecording() {
    ProfilerTimeline timeline = myStage.getStudioProfilers().getTimeline();
    timeline.setStreaming(false);
    CpuProfiler.CpuProfilerConfiguration config =
      CpuProfiler.CpuProfilerConfiguration.newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.SIMPLEPERF).build();
    myCpuService.setOngoingCaptureConfiguration(config, 100L, CpuProfiler.TraceInitiationType.INITIATED_BY_UI);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(timeline.isStreaming()).isFalse();
    myStage.updateProfilingState(false);
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
  public void testCaptureDetails() throws InterruptedException, IOException, ExecutionException {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    captureSuccessfully();

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

    // null
    myCaptureDetailsCalled = false;
    myStage.setCaptureDetails(null);
    assertThat(myCaptureDetailsCalled).isTrue();
    assertThat(myStage.getCaptureDetails()).isNull();

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

    assertThat(myStage.getCapture()).isNotNull();
    assertThat(myStage.getCapture()).isEqualTo(myStage.getCaptureFuture(1).get());
    assertThat(myCaptureDetailsCalled).isTrue();
  }

  @Test
  public void rangeIntersectionReturnsASingleTraceId() {
    int traceId1 = 1;
    String fileName1 = "This random file name";

    int traceId2 = 2;
    String fileName2 = "This other random file name";

    CpuProfiler.TraceInfo traceInfo1 = CpuProfiler.TraceInfo.newBuilder()
      .setTraceId(traceId1)
      .setTraceFilePath(fileName1)
      .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos(10))
      .setToTimestamp(TimeUnit.MICROSECONDS.toNanos(20))
      .build();

    CpuProfiler.TraceInfo traceInfo2 = CpuProfiler.TraceInfo.newBuilder()
      .setTraceId(traceId2)
      .setTraceFilePath(fileName2)
      .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos(30))
      .setToTimestamp(TimeUnit.MICROSECONDS.toNanos(40))
      .build();

    myCpuService.addTraceInfo(traceInfo1);
    myCpuService.addTraceInfo(traceInfo2);

    // No intersection.
    CpuTraceInfo traceInfo = myStage.getIntersectingTraceInfo(new Range(0, 5));
    assertThat(traceInfo).isNull();

    // Intersecting only with trace 1.
    traceInfo = myStage.getIntersectingTraceInfo(new Range(5, 15));
    assertThat(traceInfo).isNotNull();
    assertThat(traceInfo.getTraceId()).isEqualTo(traceId1);
    assertThat(traceInfo.getTraceFilePath()).isEqualTo(fileName1);

    // Intersecting only with trace 2.
    traceInfo = myStage.getIntersectingTraceInfo(new Range(25, 35));
    assertThat(traceInfo).isNotNull();
    assertThat(traceInfo.getTraceId()).isEqualTo(traceId2);
    assertThat(traceInfo.getTraceFilePath()).isEqualTo(fileName2);

    // Intersecting with both traces. First trace is returned.
    traceInfo = myStage.getIntersectingTraceInfo(new Range(0, 50));
    assertThat(traceInfo).isNotNull();
    assertThat(traceInfo.getTraceId()).isEqualTo(traceId1);
    assertThat(traceInfo.getTraceFilePath()).isEqualTo(fileName1);
  }

  @Test
  public void traceFilesGeneratedPerTrace() {
    int trace1Id = 30;
    String trace1ExpectedName = String.format("cpu_trace_%d.trace", trace1Id);
    File trace1 = new File(FileUtil.getTempDirectory(), trace1ExpectedName);

    int trace2Id = 39;
    String trace2ExpectedName = String.format("cpu_trace_%d.trace", trace2Id);
    File trace2 = new File(FileUtil.getTempDirectory(), trace2ExpectedName);

    // Make sure that both traces do not exist before effectively starting the test. Trace files are marked as temporary and will be deleted
    // when the program finishes executing. In production, this means we're fine, because we don't repeat trace IDs on the same Studio
    // instance, so the files won't conflict. For the tests, however, the files might get deleted only after all the tests run, so tests
    // running in parallel, or multiple executions of this test would mean the files would still be there and we might end up with
    // conflicting file names, which would cause this test to be flaky.
    trace1.delete();
    trace2.delete();

    assertThat(trace1.exists()).isFalse();
    myCpuService.setTraceId(trace1Id);
    captureSuccessfully();
    trace1 = new File(FileUtil.getTempDirectory(), trace1ExpectedName);
    assertThat(trace1.exists()).isTrue();

    assertThat(trace2.exists()).isFalse();
    myCpuService.setTraceId(trace2Id);
    captureSuccessfully();
    trace2 = new File(FileUtil.getTempDirectory(), trace1ExpectedName);
    assertThat(trace2.exists()).isTrue();

    List<String> paths = myCpuService.getTraceFilePaths();
    assertThat(paths).hasSize(2);
    assertThat(paths.get(0)).endsWith(trace1ExpectedName);
    assertThat(paths.get(1)).endsWith(trace2ExpectedName);
  }

  @Test
  public void setSelectedThreadShouldChangeDetails() {
    captureSuccessfully();

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);

    myCaptureDetailsCalled = false;
    myStage.setSelectedThread(42);

    assertThat(myStage.getSelectedThread()).isEqualTo(42);
    assertThat(myCaptureDetailsCalled).isTrue();
  }

  @Test
  public void unselectingThreadSetDetailsNodeToNull() {
    captureSuccessfully();
    myStage.setCaptureDetails(CaptureDetails.Type.CALL_CHART);
    myStage.setSelectedThread(myStage.getCapture().getMainThreadId());
    assertThat(myStage.getCaptureDetails()).isInstanceOf(CaptureDetails.CallChart.class);
    assertThat(((CaptureDetails.CallChart)myStage.getCaptureDetails()).getNode()).isNotNull();

    myStage.setSelectedThread(CaptureModel.NO_THREAD);
    assertThat(((CaptureDetails.CallChart)myStage.getCaptureDetails()).getNode()).isNull();
  }

  @Test
  public void settingTheSameThreadDoesNothing() {
    myCpuService.setTraceId(0);
    captureSuccessfully();

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
  public void settingTheSameDetailsTypeDoesNothing() {
    myCpuService.setTraceId(0);
    captureSuccessfully();

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
  public void callChartShouldBeSetAfterACapture() throws Exception {
    captureSuccessfully();
    assertThat(myStage.getCaptureDetails().getType()).isEqualTo(CaptureDetails.Type.CALL_CHART);

    // Change details type and verify it was actually changed.
    myStage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(myStage.getCaptureDetails().getType()).isEqualTo(CaptureDetails.Type.BOTTOM_UP);

    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    myStage.setAndSelectCapture(capture);
    // Just selecting a different capture shouldn't change the capture details
    assertThat(myStage.getCaptureDetails().getType()).isEqualTo(CaptureDetails.Type.BOTTOM_UP);

    captureSuccessfully();
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
  public void captureStateDependsOnAppBeingProfiling() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS);
    startCapturing();
    myCpuService.setValidTrace(true);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    stopCapturing();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
  }

  @Test
  public void setAndSelectCaptureDifferentClockType() {
    captureSuccessfully();
    CpuCapture capture = myStage.getCapture();
    CaptureNode captureNode = capture.getCaptureNode(capture.getMainThreadId());
    assertThat(captureNode).isNotNull();
    myStage.setSelectedThread(capture.getMainThreadId());

    assertThat(captureNode.getClockType()).isEqualTo(ClockType.GLOBAL);
    myStage.setAndSelectCapture(capture);
    ProfilerTimeline timeline = myStage.getStudioProfilers().getTimeline();
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
  public void testCaptureRangeConversion() {
    captureSuccessfully();

    myStage.setSelectedThread(myStage.getCapture().getMainThreadId());
    myStage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP);

    Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
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
  public void settingACaptureAfterNullShouldSelectMainThread() throws Exception {
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
  public void traceMissingDataShowsDialog() throws IOException {
    // Set a capture of type atrace.
    myCpuService.setProfilerType(CpuProfiler.CpuProfilerType.ATRACE);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(ATRACE_DATA_FILE)));
    // Select valid capture no dialog should be presented.
    myStage.setAndSelectCapture(0);

    assertThat(myServices.getNotification()).isNull();
    // Select invalid capture we should see dialog.
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(ATRACE_MISSING_DATA_FILE)));
    myStage.setAndSelectCapture(1);
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.ATRACE_BUFFER_OVERFLOW);
  }

  @Test
  public void changingCaptureShouldKeepThreadSelection() throws Exception {
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
  public void selectingAndDeselectingCaptureShouldNotMakeUiJump() throws Exception {
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
    myStage.setTooltip(new CpuUsageTooltip(myStage));
    assertThat(myStage.getTooltip()).isInstanceOf(CpuUsageTooltip.class);
    CpuUsageTooltip tooltip = (CpuUsageTooltip)myStage.getTooltip();

    CpuProfilerStage.CpuStageLegends legends = tooltip.getLegends();
    double tooltipTime = TimeUnit.SECONDS.toMicros(0);
    myCpuService.setAppTimeMs(10);
    myCpuService.setSystemTimeMs(50);
    myStage.getStudioProfilers().getTimeline().getTooltipRange().set(tooltipTime, tooltipTime);
    assertThat(legends.getCpuLegend().getName()).isEqualTo("App");
    assertThat(legends.getOthersLegend().getName()).isEqualTo("Others");
    assertThat(legends.getThreadsLegend().getName()).isEqualTo("Threads");
    assertThat(legends.getCpuLegend().getValue()).isEqualTo("10 %");
    assertThat(legends.getOthersLegend().getValue()).isEqualTo("40 %");
    assertThat(legends.getThreadsLegend().getValue()).isEqualTo("1");
  }

  @Test
  public void testThreadsTooltip() {
    Range viewRange = myStage.getStudioProfilers().getTimeline().getViewRange();
    Range tooltipRange = myStage.getStudioProfilers().getTimeline().getTooltipRange();

    viewRange.set(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(11));

    myStage.enter();
    myStage.setTooltip(new CpuThreadsTooltip(myStage));
    assertThat(myStage.getTooltip()).isInstanceOf(CpuThreadsTooltip.class);
    CpuThreadsTooltip tooltip = (CpuThreadsTooltip)myStage.getTooltip();

    // Null thread series
    tooltip.setThread(null, null);
    assertThat(tooltip.getThreadName()).isNull();
    assertThat(tooltip.getThreadState()).isNull();

    // Thread series: 1 - running - 8 - dead - 11
    LegacyCpuThreadStateDataSeries series =
      new LegacyCpuThreadStateDataSeries(myStage.getStudioProfilers().getClient().getCpuClient(), ProfilersTestData.SESSION_DATA, 1);
    tooltip.setThread("myThread", series);

    assertThat(tooltip.getThreadName()).isEqualTo("myThread");

    // Tooltip before all data.
    long tooltipTimeUs = TimeUnit.SECONDS.toMicros(0);
    tooltipRange.set(tooltipTimeUs, tooltipTimeUs);
    assertThat(tooltip.getThreadState()).isNull();

    // Tooltip on first thread.
    tooltipTimeUs = TimeUnit.SECONDS.toMicros(5);
    tooltipRange.set(tooltipTimeUs, tooltipTimeUs);
    assertThat(tooltip.getThreadState()).isEqualTo(CpuProfilerStage.ThreadState.RUNNING);

    // Tooltip right on second thread.
    tooltipTimeUs = TimeUnit.SECONDS.toMicros(8);
    tooltipRange.set(tooltipTimeUs, tooltipTimeUs);
    assertThat(tooltip.getThreadState()).isEqualTo(CpuProfilerStage.ThreadState.DEAD);

    // Tooltip after all data. Because data don't contain end time so the last thread state lasts "forever".
    tooltipTimeUs = TimeUnit.SECONDS.toMicros(12);
    tooltipRange.set(tooltipTimeUs, tooltipTimeUs);
    assertThat(tooltip.getThreadState()).isEqualTo(CpuProfilerStage.ThreadState.DEAD);
  }

  @Test
  public void testCpuKernelTooltip() throws Exception {
    Range viewRange = myStage.getStudioProfilers().getTimeline().getViewRange();
    Range tooltipRange = myStage.getStudioProfilers().getTimeline().getTooltipRange();

    viewRange.set(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(11));

    myStage.setCapture(new AtraceParser(1).parse(CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace"), 0));
    myStage.enter();
    myStage.setTooltip(new CpuKernelTooltip(myStage));
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
    AtraceDataSeries<CpuThreadSliceInfo> series = new AtraceDataSeries<>(myStage, (capture) -> cpuSeriesData);
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
  public void testElapsedTime() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    // Before we capture, elapsed time should be unset (default set to 0)
    assertThat(myStage.getCaptureElapsedTimeUs()).isEqualTo(0);

    // Start capturing
    startCapturingSuccess();
    // Increment 3 seconds on data range
    Range dataRange = myStage.getStudioProfilers().getTimeline().getDataRange();
    double currentMax = dataRange.getMax() + TimeUnit.SECONDS.toMicros(3);
    dataRange.setMax(currentMax);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
    // Check that we're capturing for three seconds
    assertThat(myStage.getCaptureElapsedTimeUs()).isEqualTo(TimeUnit.SECONDS.toMicros(3));

    myCpuService.setValidTrace(true);
    stopCapturing();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    currentMax += TimeUnit.SECONDS.toMicros(2);
    dataRange.setMax(currentMax);
    // Start capturing again, this time for 10 seconds
    startCapturingSuccess();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
    currentMax += TimeUnit.SECONDS.toMicros(10);
    dataRange.setMax(currentMax);

    // Check that we're capturing for 10 seconds.
    assertThat(myStage.getCaptureElapsedTimeUs()).isEqualTo(TimeUnit.SECONDS.toMicros(10));
  }

  @Test
  public void exitingAndReEnteringStageAgainShouldPreserveProfilingTime() {
    // Start capturing
    startCapturingSuccess();

    // Increment 3 seconds on data range
    Range dataRange = myStage.getStudioProfilers().getTimeline().getDataRange();
    dataRange.setMax(dataRange.getMax() + TimeUnit.SECONDS.toMicros(3));
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);


    // Go back to monitor stage and go back to a new Cpu profiler stage
    myStage.getStudioProfilers().setStage(new StudioMonitorStage(myStage.getStudioProfilers()));
    CpuProfilerStage stage = new CpuProfilerStage(myStage.getStudioProfilers());
    myStage.getStudioProfilers().setStage(stage);

    // Make sure we're capturing
    assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);

    // Check that we're capturing for three seconds
    assertThat(stage.getCaptureElapsedTimeUs()).isEqualTo(TimeUnit.SECONDS.toMicros(3));
  }

  @Test
  public void stopProfilerIsConsistentToStartProfiler() throws IOException {
    assertThat(myCpuService.getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);
    ProfilingConfiguration config1 = new ProfilingConfiguration("My Config",
                                                                CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                                                CpuProfiler.CpuProfilerMode.SAMPLED);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config1);
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    captureSuccessfully();
    assertThat(myCpuService.getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.SIMPLEPERF);

    ProfilingConfiguration config2 = new ProfilingConfiguration("My Config 2",
                                                                CpuProfiler.CpuProfilerType.ART,
                                                                CpuProfiler.CpuProfilerMode.SAMPLED);

    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("valid_trace.trace"));
    myStage.getProfilerConfigModel().setProfilingConfiguration(config2);
    // Start capturing with ART
    startCapturingSuccess();
    // Change the profiling configurations in the middle of the capture and stop capturing
    myStage.getProfilerConfigModel().setProfilingConfiguration(config1);
    stopCapturing();
    // Stop profiler should be the same as the one passed in the start request
    assertThat(myCpuService.getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);
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
  public void exitingStageAndEnteringAgainShouldPreserveCaptureState() throws IOException {
    assertThat(myCpuService.getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);
    ProfilingConfiguration config1 = new ProfilingConfiguration("My Config",
                                                                CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                                                CpuProfiler.CpuProfilerMode.SAMPLED);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config1);
    startCapturingSuccess();

    // Go back to monitor stage and go back to a new Cpu profiler stage
    myStage.getStudioProfilers().setStage(new StudioMonitorStage(myStage.getStudioProfilers()));
    CpuProfilerStage stage = new CpuProfilerStage(myStage.getStudioProfilers());
    myStage.getStudioProfilers().setStage(stage);

    // Make sure we're capturing
    assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);

    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    stopCapturing(stage);
    assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Stop profiler should be the same as the one passed in the start request
    assertThat(myCpuService.getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.SIMPLEPERF);

    // Make sure we tracked the correct configuration
    ProfilingConfiguration trackedConfig =
      ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata().getProfilingConfiguration();
    assertThat(trackedConfig.getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.SIMPLEPERF);
    assertThat(trackedConfig.getMode()).isEqualTo(CpuProfiler.CpuProfilerMode.SAMPLED);
  }

  @Test
  public void apiInitiatedCaptureShouldPreserveNonIdleNonCapturingState() {
    myStage.setCaptureState(CpuProfilerStage.CaptureState.STOPPING);

    // API-initiated tracing starts.
    CpuProfiler.CpuProfilerConfiguration apiTracingconfig =
      CpuProfiler.CpuProfilerConfiguration.newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.ART).build();
    long startTimestamp = 100;
    myCpuService.setOngoingCaptureConfiguration(apiTracingconfig, startTimestamp, CpuProfiler.TraceInitiationType.INITIATED_BY_API);

    // Verify the STOPPING state isn't changed due to API tracing.
    myStage.updateProfilingState(true);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.STOPPING);

    // Simulate the parsing of prior capture (the one being parsed when entering the test) is done.
    myStage.setCaptureState(CpuProfilerStage.CaptureState.IDLE);

    // Verify API-initiated tracing is shown as capturing.
    myStage.updateProfilingState(true);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
  }

  @Test
  public void transitsToIdleWhenApiInitiatedTracingEnds() {
    myServices.enableCpuApiTracing(true);

    // API-initiated tracing starts.
    CpuProfiler.CpuProfilerConfiguration artConfig =
      CpuProfiler.CpuProfilerConfiguration.newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.ART).build();

    long startTimestamp = 100;
    myCpuService.setOngoingCaptureConfiguration(artConfig, startTimestamp, CpuProfiler.TraceInitiationType.INITIATED_BY_API);

    myStage.setCaptureState(CpuProfilerStage.CaptureState.IDLE);
    myCpuService.setAppBeingProfiled(true);
    myStage.updateProfilingState(true);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);

    myCpuService.setAppBeingProfiled(false);
    myStage.updateProfilingState(true);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
  }

  @Test
  public void updateProfilingStatePreservesCapturingWhenNonApiInitiatedTracingEnds() {
    myServices.enableCpuApiTracing(true);

    // UI-initiated tracing starts.
    CpuProfiler.CpuProfilerConfiguration artConfig =
      CpuProfiler.CpuProfilerConfiguration.newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.ART).build();

    long startTimestamp = 100;
    myCpuService.setOngoingCaptureConfiguration(artConfig, startTimestamp, CpuProfiler.TraceInitiationType.INITIATED_BY_UI);

    myStage.setCaptureState(CpuProfilerStage.CaptureState.IDLE);
    myCpuService.setAppBeingProfiled(true);
    myStage.updateProfilingState(false);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);

    myCpuService.setAppBeingProfiled(false);
    myStage.updateProfilingState(false);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
  }

  @Test
  public void updateProfilingStateDontGoBackToRecordingForUiInitiatedTraces() {
    myServices.enableCpuApiTracing(true);
    myTimer.setHandler(myStage.getStudioProfilers().getUpdater());
    myStage.enter();

    // UI-initiated tracing starts.
    CpuProfiler.CpuProfilerConfiguration artConfig =
      CpuProfiler.CpuProfilerConfiguration.newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.ART).build();
    myCpuService.setOngoingCaptureConfiguration(artConfig, 100, CpuProfiler.TraceInitiationType.INITIATED_BY_UI);

    myStage.setCaptureState(CpuProfilerStage.CaptureState.IDLE);
    // Make the server return that the app is being profiled to simulate the race condition we might have between data poller and UI threads
    myCpuService.setAppBeingProfiled(true);
    // Simulate UPDATE_COUNT_TO_CALL_CALLBACK ticks in the updater. That should trigger a call to updateProfilingState from the
    // CpuCaptureStateUpdatable. We do this instead of calling updataeProfilingState(true) directly because if the updatable callback is
    // changed later for some reason this test will fail.
    for (int i = 0; i <= CpuProfilerStage.CpuCaptureStateUpdatable.UPDATE_COUNT_TO_CALL_CALLBACK; i++) {
      myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    }

    // We shouldn't go to CAPTURING despite the fact our service returns that the app is being profiled, beacause the trace was not
    // initiated by API.
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
  }

  @Test
  public void apiInitiatedCaptureRespectCpuApiTracingFlag() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // API-initiated tracing starts.
    CpuProfiler.CpuProfilerConfiguration apiTracingconfig =
      CpuProfiler.CpuProfilerConfiguration.newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.ART).build();
    long startTimestamp = 100;
    myCpuService.setOngoingCaptureConfiguration(apiTracingconfig, startTimestamp, CpuProfiler.TraceInitiationType.INITIATED_BY_API);

    // Verify that when cpu.api.tracing is off, an API-initiated tracing doesn't update stage's capture state.
    myServices.enableCpuApiTracing(false);
    myStage.getStudioProfilers().getUpdater().onTick(1);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Verify that when cpu.api.tracing is on, an API-initiated tracing does update stage's capture state.
    myServices.enableCpuApiTracing(true);
    CpuProfilerStage stage = new CpuProfilerStage(myStage.getStudioProfilers());
    stage.enter();
    stage.getStudioProfilers().getUpdater().onTick(1);
    assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
  }

  @Test
  public void apiInitiatedCaptureUsageTracking() {
    int traceId1 = 1;
    String fileName1 = "file1.trace";
    int traceId2 = 2;
    String fileName2 = "file2.trace";
    int traceId3 = 3;
    String fileName3 = "";

    // Trace 1: not API-initiated. Shouldn't have API-tracing usage.
    CpuProfiler.TraceInfo traceInfo1 = CpuProfiler.TraceInfo.newBuilder()
      .setTraceId(traceId1)
      .setTraceFilePath(fileName1)
      .setInitiationType(CpuProfiler.TraceInitiationType.INITIATED_BY_UI)
      .build();

    // Trace 2: API-initiated with a valid given trace path.
    CpuProfiler.TraceInfo traceInfo2 = CpuProfiler.TraceInfo.newBuilder()
      .setTraceId(traceId2)
      .setTraceFilePath(fileName2)
      .setInitiationType(CpuProfiler.TraceInitiationType.INITIATED_BY_API)
      .build();

    // Trace 3: API-initiated without a valid given trace path.
    CpuProfiler.TraceInfo traceInfo3 = CpuProfiler.TraceInfo.newBuilder()
      .setTraceId(traceId3)
      .setTraceFilePath(fileName3)
      .setInitiationType(CpuProfiler.TraceInitiationType.INITIATED_BY_API)
      .build();

    final FakeFeatureTracker featureTracker = (FakeFeatureTracker)myServices.getFeatureTracker();
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);

    myCpuService.addTraceInfo(traceInfo1);
    myStage.getStudioProfilers().getUpdater().onTick(1);
    assertThat(featureTracker.getApiTracingUsageCount()).isEqualTo(0);

    myCpuService.addTraceInfo(traceInfo2);
    myStage.getStudioProfilers().getUpdater().onTick(1);
    assertThat(featureTracker.getApiTracingUsageCount()).isGreaterThan(0);
    assertThat(featureTracker.getLastCpuAPiTracingPathProvided()).isTrue();

    myCpuService.addTraceInfo(traceInfo3);
    myStage.getStudioProfilers().getUpdater().onTick(1);
    assertThat(featureTracker.getLastCpuAPiTracingPathProvided()).isFalse();
  }

  @Test
  public void testStartupProfilingUsageTracking() {
    final FakeFeatureTracker featureTracker = (FakeFeatureTracker)myServices.getFeatureTracker();

    ProfilingConfiguration config = new ProfilingConfiguration("MyConfig", CpuProfiler.CpuProfilerType.ART,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    myStage.exit();

    myCpuService.setOngoingCaptureConfiguration(config.toProto(), 0, CpuProfiler.TraceInitiationType.INITIATED_BY_STARTUP);
    CpuProfilerStage stage = new CpuProfilerStage(myStage.getStudioProfilers());
    assertThat(featureTracker.getLastCpuStartupProfilingConfig()).isNull();
    stage.enter();
    assertThat(featureTracker.getLastCpuStartupProfilingConfig()).isEqualTo(config);
  }

  @Test
  public void setAndSelectCaptureShouldStopStreamingMode() throws Exception {
    // Capture has changed, keeps the same type of details
    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    myStage.getStudioProfilers().getTimeline().setIsPaused(false);
    myStage.getStudioProfilers().getTimeline().setStreaming(true);
    myStage.setAndSelectCapture(capture);
    assertThat(myStage.getStudioProfilers().getTimeline().isStreaming()).isFalse();
  }

  @Test
  public void testInProgressDuration() {
    assertThat(myStage.getInProgressTraceDuration().getSeries().getSeries()).hasSize(0);
    startCapturingSuccess();
    // Starting capturing should display in progress duration, it will be displayed when
    // myStage.getInProgressTraceDuration() contains exactly one element corresponding to unfinished duration.
    assertThat(myStage.getInProgressTraceDuration().getSeries().getSeries()).hasSize(1);
    assertThat(myStage.getInProgressTraceDuration().getSeries().getSeries().get(0).value.getDurationUs()).isEqualTo(Long.MAX_VALUE);
    myCpuService.setValidTrace(true);

    Iterator<CpuProfilerStage.CaptureState> comingStates = Iterators.forArray(CpuProfilerStage.CaptureState.STOPPING,
                                                                              CpuProfilerStage.CaptureState.IDLE);
    AtomicInteger transitionsCount = new AtomicInteger();

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_STATE, () -> {
      assertThat(myStage.getCaptureState()).isEqualTo(comingStates.next());
      transitionsCount.getAndIncrement();
      switch (myStage.getCaptureState()) {
        case IDLE:
          assertThat(myStage.getInProgressTraceDuration().getSeries().getSeries()).hasSize(0);
          break;
        case STOPPING:
          break;
        default:
          throw new RuntimeException("Unreachable code");
      }
    });

    AtomicBoolean parsingCalled = new AtomicBoolean(false);
    myStage.getCaptureParser().getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_PARSING, () -> {
      assertThat(myStage.getCaptureParser().isParsing()).isTrue();
      assertThat(myStage.getInProgressTraceDuration().getSeries().getSeries()).hasSize(1);
      assertThat(myStage.getInProgressTraceDuration().getSeries().getSeries().get(0).value.getDurationUs()).isLessThan(Long.MAX_VALUE);
      parsingCalled.set(true);
    });

    stopCapturing();
    assertThat(myStage.getInProgressTraceDuration().getSeries().getSeries()).hasSize(0);
    assertThat(transitionsCount.get()).isEqualTo(2);
    assertThat(parsingCalled.get()).isTrue();
  }

  @Test
  public void testInProgressDurationAfterExitAndEnter() {
    assertThat(myStage.getInProgressTraceDuration().getSeries().getSeries()).hasSize(0);
    startCapturingSuccess();
    assertThat(myStage.getInProgressTraceDuration().getSeries().getSeries()).hasSize(1);
    myStage.exit();

    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myServices, myTimer);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    CpuProfilerStage newStage = new CpuProfilerStage(profilers);
    newStage.getStudioProfilers().setStage(newStage);

    assertThat(newStage.getInProgressTraceDuration().getSeries().getSeries()).hasSize(1);
    myCpuService.setValidTrace(true);
    stopCapturing(newStage);
    assertThat(newStage.getInProgressTraceDuration().getSeries().getSeries()).hasSize(0);
  }

  @Test
  public void configurationShouldBeTheOnGoingProfilingAfterExitAndEnter() {
    ProfilingConfiguration testConfig = new ProfilingConfiguration(FakeIdeProfilerServices.FAKE_SIMPLEPERF_NAME,
                                                                   CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                                                   CpuProfiler.CpuProfilerMode.SAMPLED);
    myStage.getProfilerConfigModel().setProfilingConfiguration(testConfig);
    startCapturingSuccess();
    assertThat(myStage.getProfilerConfigModel().getProfilingConfiguration()).isEqualTo(testConfig);
    myStage.exit();

    // Enter CpuProfilerStage again.
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myServices, myTimer);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    CpuProfilerStage newStage = new CpuProfilerStage(profilers);
    newStage.getStudioProfilers().setStage(newStage);

    assertThat(newStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
    assertThat(newStage.getProfilerConfigModel().getProfilingConfiguration()).isEqualTo(testConfig);
  }

  @Test
  public void configurationShouldBeTheLastSelectedOneAfterExitAndEnter() {
    ProfilingConfiguration testConfig = new ProfilingConfiguration(FakeIdeProfilerServices.FAKE_SIMPLEPERF_NAME,
                                                                   CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                                                   CpuProfiler.CpuProfilerMode.SAMPLED);
    myStage.getProfilerConfigModel().setProfilingConfiguration(testConfig);
    assertThat(myStage.getProfilerConfigModel().getProfilingConfiguration()).isEqualTo(testConfig);
    myStage.exit();

    // Enter CpuProfilerStage again.
    StudioProfilers profilers = new StudioProfilers(myProfilerClient, myServices, myTimer);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    CpuProfilerStage newStage = new CpuProfilerStage(profilers);
    newStage.getStudioProfilers().setStage(newStage);

    assertThat(newStage.getProfilerConfigModel().getProfilingConfiguration()).isEqualTo(testConfig);
  }

  @Test
  public void selectARangeWithNoCapturesShouldKeepCurrentCaptureSelected() {
    assertThat(myStage.getCapture()).isNull();
    captureSuccessfully();
    assertThat(myStage.getCapture()).isNotNull();
    CpuCapture capture = myStage.getCapture();

    Range selectionRange = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    // Select an area before the capture.
    selectionRange.set(capture.getRange().getMin() - 20, capture.getRange().getMin() - 10);
    // Last selected capture should remain selected.
    assertThat(myStage.getCapture()).isEqualTo(capture);
  }

  /**
   * Simulate the scenario of calling {@link CpuProfilerStage#getCapture(int)} before calling {@link CpuProfilerStage#stopCapturing()}.
   */
  @Test
  public void captureShouldBeParsedOnlyOnceSyncGetCaptureBefore() throws InterruptedException, IOException, ExecutionException {
    assertThat(myStage.getCapture()).isNull();
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("valid_trace.trace"));
    // Capture with FAKE_TRACE_ID doesn't exist yet. myStage.getCapture(...) will parse it.
    CpuCapture capture = myStage.getCaptureFuture(FakeCpuService.FAKE_TRACE_ID).get();
    assertThat(capture).isNotNull();
    myStage.setCaptureState(CpuProfilerStage.CaptureState.IDLE);

    captureSuccessfully();
    // Capture should be the same as the one obtained by myStage.getCapture(...),
    // because we should not parse the trace into another CpuCapture object.
    assertThat(myStage.getCapture()).isEqualTo(capture);
  }

  /**
   * Simulate the scenario of calling {@link CpuProfilerStage#stopCapturing()} before calling {@link CpuProfilerStage#getCapture(int)}.
   */
  @Test
  public void captureShouldBeParsedOnlyOnceStopCapturingBefore() throws InterruptedException, ExecutionException {
    assertThat(myStage.getCapture()).isNull();
    // stopCapturing() should create a capture with FAKE_TRACE_ID
    captureSuccessfully();
    CpuCapture capture = myStage.getCapture();
    assertThat(capture).isNotNull();

    // Capture should be the same as the one created by stopCapturing(),
    // because we should not parse the trace into another CpuCapture object.
    assertThat(myStage.getCaptureFuture(FakeCpuService.FAKE_TRACE_ID).get()).isEqualTo(capture);
  }

  @Test
  public void getCaptureFutureShouldTellParserToStartParsing() {
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    assertThat(myStage.getCaptureParser().isParsing()).isFalse();

    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);

    // Make sure the capture will be parsed.
    AspectObserver observer = new AspectObserver();
    AtomicBoolean transitionHappened = new AtomicBoolean(false);
    myStage.getCaptureParser().getAspect().addDependency(observer).onChange(
      CpuProfilerAspect.CAPTURE_PARSING, () -> {
        assertThat(myStage.getCaptureParser().isParsing()).isTrue();
        transitionHappened.set(true);
      });
    myStage.getCaptureFuture(FakeCpuService.FAKE_TRACE_ID);

    // Parsing should set the profiler to EXPANDED
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    assertThat(transitionHappened.get()).isTrue();
  }

  @Test
  public void setCaptureWhileCapturingShouldParseAndContinueInCapturingState() {
    // Start capturing
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    int traceId1 = 1;
    myCpuService.setTraceId(traceId1);
    myStage.startCapturing();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);

    // We should parse the selected capture and continue in capturing state, recording another capture.
    AspectObserver observer = new AspectObserver();
    AtomicBoolean transitionHappened = new AtomicBoolean(false);
    myStage.getCaptureParser().getAspect().addDependency(observer).onChange(
      CpuProfilerAspect.CAPTURE_PARSING, () -> {
        assertThat(myStage.getCaptureParser().isParsing()).isTrue();
        transitionHappened.set(true);
      });

    // Select another capture
    int traceId2 = 2;
    myCpuService.setTraceId(traceId2);
    myStage.setAndSelectCapture(traceId2);
    assertThat(transitionHappened.get()).isTrue();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
  }

  @Test
  public void setCaptureWhileIdleShouldParseAndStayInIdleState() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Listen to CAPTURE_PARSING and check we go through parsing.
    AtomicBoolean parsingCalled = new AtomicBoolean(false);
    AspectObserver observer = new AspectObserver();
    myStage.getCaptureParser().getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_PARSING, () -> {
      assertThat(myStage.getCaptureParser().isParsing()).isTrue();
      parsingCalled.set(true);
    });

    // Select a capture
    int traceId1 = 1;
    myCpuService.setTraceId(traceId1);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    myStage.setAndSelectCapture(traceId1);
    assertThat(parsingCalled.get()).isTrue();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    // Sanity check to verify we''e not parsing anymore.
    assertThat(myStage.getCaptureParser().isParsing()).isFalse();
  }

  @Test
  public void cpuMetadataSuccessfulCapture() {
    ProfilingConfiguration config = new ProfilingConfiguration("My Config",
                                                               CpuProfiler.CpuProfilerType.ART,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    captureSuccessfully();
    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.SUCCESS);
    ProfilingConfiguration metadataConfig = metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);
    assertThat(metadataConfig.getMode()).isEqualTo(CpuProfiler.CpuProfilerMode.SAMPLED);
    assertThat(metadata.getParsingTimeMs()).isGreaterThan(0L);
    assertThat(metadata.getRecordDurationMs()).isGreaterThan(0L);
    assertThat(metadata.getCaptureDurationMs()).isGreaterThan(0L);
    assertThat(metadata.getTraceFileSizeBytes()).isGreaterThan(0);
  }

  @Test
  public void cpuMetadataFailureStopCapture() {
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ProfilingConfiguration config = new ProfilingConfiguration("My Config",
                                                               CpuProfiler.CpuProfilerType.ART,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    startCapturingSuccess();

    // Increment 3 seconds on data range to simulate this time has passed
    Range dataRange = myStage.getStudioProfilers().getTimeline().getDataRange();
    long elapsedTimeUs = TimeUnit.SECONDS.toMicros(3);
    dataRange.setMax(dataRange.getMax() + elapsedTimeUs);

    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.STOP_COMMAND_FAILED);
    stopCapturing();
    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.STOP_CAPTURING_FAILURE);
    // Profiling Configurations should remain the same
    ProfilingConfiguration metadataConfig = metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);
    assertThat(metadataConfig.getMode()).isEqualTo(CpuProfiler.CpuProfilerMode.SAMPLED);
    // Capture duration is calculated from the elapsed time since recording has started.
    assertThat(metadata.getCaptureDurationMs()).isEqualTo(TimeUnit.MICROSECONDS.toMillis(elapsedTimeUs));
    // Trace was not generated, so trace size, parsing time and recording duration should be 0 (unset)
    assertThat(metadata.getParsingTimeMs()).isEqualTo(0);
    assertThat(metadata.getRecordDurationMs()).isEqualTo(0);
    assertThat(metadata.getTraceFileSizeBytes()).isEqualTo(0);
  }

  @Test
  public void cpuMetadataFailureParsing() throws IOException {
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ProfilingConfiguration config = new ProfilingConfiguration("My Config",
                                                               CpuProfiler.CpuProfilerType.ART,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    myCpuService.setValidTrace(true);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    startCapturingSuccess();
    // Increment 3 seconds on data range to simulate this time has passed
    Range dataRange = myStage.getStudioProfilers().getTimeline().getDataRange();
    long elapsedTimeUs = TimeUnit.SECONDS.toMicros(3);
    dataRange.setMax(dataRange.getMax() + elapsedTimeUs);
    stopCapturing();

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.PARSING_FAILURE);
    ProfilingConfiguration metadataConfig = metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);
    assertThat(metadataConfig.getMode()).isEqualTo(CpuProfiler.CpuProfilerMode.SAMPLED);
    // Trace was generated, so trace size should be greater than 0
    assertThat(metadata.getTraceFileSizeBytes()).isGreaterThan(0);
    // Capture duration is calculated from the elapsed time since recording has started.
    assertThat(metadata.getCaptureDurationMs()).isEqualTo(TimeUnit.MICROSECONDS.toMillis(elapsedTimeUs));
    // Trace was not parsed correctly, so parsing time and recording duration should be 0 (unset)
    assertThat(metadata.getParsingTimeMs()).isEqualTo(0);
    assertThat(metadata.getRecordDurationMs()).isEqualTo(0);
  }

  @Test
  public void cpuMetadataFailureUserAbort() {
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ProfilingConfiguration config = new ProfilingConfiguration("My Config",
                                                               CpuProfiler.CpuProfilerType.ART,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    ByteString largeTraceFile = ByteString.copyFrom(new byte[CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1]);
    myCpuService.setTrace(largeTraceFile);
    myCpuService.setValidTrace(true);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    myServices.setShouldParseLongTraces(false);

    startCapturingSuccess();
    // Increment 3 seconds on data range to simulate this time has passed
    Range dataRange = myStage.getStudioProfilers().getTimeline().getDataRange();
    long elapsedTimeUs = TimeUnit.SECONDS.toMicros(3);
    dataRange.setMax(dataRange.getMax() + elapsedTimeUs);
    stopCapturing();

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING);
    // Profiling Configurations should remain the same.
    ProfilingConfiguration metadataConfig = metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);
    assertThat(metadataConfig.getMode()).isEqualTo(CpuProfiler.CpuProfilerMode.SAMPLED);
    // Trace was generated, so trace size should be greater than 0
    assertThat(metadata.getTraceFileSizeBytes()).isGreaterThan(0);
    // Capture duration is calculated from the elapsed time since recording has started.
    assertThat(metadata.getCaptureDurationMs()).isEqualTo(TimeUnit.MICROSECONDS.toMillis(elapsedTimeUs));
    // Trace was not parsed at all, so parsing time and recording duration should be 0 (unset)
    assertThat(metadata.getParsingTimeMs()).isEqualTo(0);
    assertThat(metadata.getRecordDurationMs()).isEqualTo(0);
  }

  @Test
  public void cpuMetadataFailurePreProcess() throws IOException {
    // Enable SIMPLEPERF_HOST flag to make sure we'll preprocess the trace
    myServices.enableSimpleperfHost(true);
    // Make sure the TracePreProcessor fails to pre-process the trace
    ((FakeTracePreProcessor)myServices.getSimpleperfTracePreProcessor()).setFailedToPreProcess(true);
    // Select a simpleperf configuration
    ProfilingConfiguration config = new ProfilingConfiguration("My Config",
                                                               CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    // Use a trace that is not a raw simpleperf trace. That should cause pre-process to return a failure.
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    captureSuccessfully();

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.PREPROCESS_FAILURE);
    // We should still log the trace size if we fail to pre-process. As we're using "simpleperf.trace", the size should be greater than 0.
    assertThat(metadata.getTraceFileSizeBytes()).isGreaterThan(0);
  }

  @Test
  public void parsingFailureIsNotifiedToUi() throws IOException {
    // Start an ART capturing successfully
    ProfilingConfiguration config = new ProfilingConfiguration("My Config",
                                                               CpuProfiler.CpuProfilerType.ART,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    startCapturingSuccess();

    // Sequence of states that should happen after stopping a capture that failures to parse the trace
    Iterator<CpuProfilerStage.CaptureState> captureStates = Iterators.forArray(CpuProfilerStage.CaptureState.STOPPING,
                                                                               CpuProfilerStage.CaptureState.IDLE);
    AtomicInteger transitionsCount = new AtomicInteger();
    // Listen to CAPTURE_STATE changes and check if the new state is equal to what we expect.
    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(
      CpuProfilerAspect.CAPTURE_STATE, () -> {
        transitionsCount.getAndIncrement();
        assertThat(myStage.getCaptureState()).isEqualTo(captureStates.next());
      });

    // Listen to CAPTURE_PARSING and check if we goes through parsing state before parsing fails.
    AtomicBoolean aspectFired = new AtomicBoolean(false);
    myStage.getCaptureParser().getAspect().addDependency(observer).onChange(
      CpuProfilerAspect.CAPTURE_PARSING, () -> {
        aspectFired.set(true);
        assertThat(myStage.getCaptureParser().isParsing()).isTrue();
      });

    // Force the return of a simpleperf. As we started an ART capture, the capture parsing should fail.
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    myCpuService.setValidTrace(true);
    stopCapturing();
    assertThat(transitionsCount.get()).isEqualTo(2);
    assertThat(aspectFired.get()).isTrue();

    // As parsing has failed, capture should be null.
    assertThat(myStage.getCapture()).isNull();
    // Sanity check to see if we reached the final capture state
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    // Sanity check to verify we're not parsing anymore
    assertThat(myStage.getCaptureParser().isParsing()).isFalse();
  }

  @Test
  public void startCapturingJumpsToLiveData() {
    ProfilerTimeline timeline = myStage.getStudioProfilers().getTimeline();
    timeline.setStreaming(false);
    assertThat(timeline.isStreaming()).isFalse();

    startCapturingSuccess();
    assertThat(timeline.isStreaming()).isTrue();
    stopCapturing();

    // Sanity test to check that start recording doesn't flip the status of isStreaming, but actually sets it to true
    assertThat(timeline.isStreaming()).isTrue();
    startCapturingSuccess();
    assertThat(timeline.isStreaming()).isTrue();
  }

  @Test
  public void captureNavigationChangesCaptureSelection() {
    int traceId1 = 1;
    int traceId2 = 2;

    myCpuService.setTraceId(traceId1);
    captureSuccessfully();
    CpuCapture capture1 = myStage.getCapture();

    myCpuService.setTraceId(traceId2);
    captureSuccessfully();
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
  public void captureNavigationEnabledInSessionsWithTraces() {
    // There are no traces/captures in the current session. We can't navigate anywhere.
    assertThat(myStage.getTraceIdsIterator().hasNext()).isFalse();
    assertThat(myStage.getTraceIdsIterator().hasPrevious()).isFalse();

    // Add a trace to the session
    myCpuService.addTraceInfo(CpuProfiler.TraceInfo.getDefaultInstance());
    myStage = new CpuProfilerStage(myStage.getStudioProfilers());

    // Verify we can now navigate. Note we didn't have to parse any captures. The model should fetch all the trace info when it's created.
    assertThat(myStage.getTraceIdsIterator().hasNext()).isTrue();
    assertThat(myStage.getTraceIdsIterator().hasPrevious()).isTrue();
  }

  @Test
  public void testHasUserUsedCapture() {
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(0);
    assertThat(myStage.hasUserUsedCpuCapture()).isFalse();
    startCapturing();
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(1);
    assertThat(myStage.hasUserUsedCpuCapture()).isTrue();
  }

  @Test
  public void startCapturingFailureShowsErrorBalloon() throws InterruptedException, ExecutionException, IOException {
    myStage.setCapture(CpuProfilerTestUtils.getValidCapture());
    // Start a failing capture
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.FAILURE);
    // Sequence of states that should happen after starting a capture and failing to do so
    Iterator<CpuProfilerStage.CaptureState> captureStates = Iterators.forArray(CpuProfilerStage.CaptureState.STARTING,
                                                                               CpuProfilerStage.CaptureState.IDLE);
    // Listen to CAPTURE_STATE changes and check if the new state is equal to what we expect.
    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(
      CpuProfilerAspect.CAPTURE_STATE, () -> assertThat(myStage.getCaptureState()).isEqualTo(captureStates.next()));
    assertThat(myStage.getCapture()).isNotNull();
    startCapturing();
    // Sanity check to see if we reached the final capture state
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.CAPTURE_START_FAILURE);

    assertThat(myStage.getCapture()).isNull();
  }

  @Test
  public void stopCapturingFailureShowsErrorBalloon() throws InterruptedException, ExecutionException, IOException {
    myStage.setCapture(CpuProfilerTestUtils.getValidCapture());
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ProfilingConfiguration config = new ProfilingConfiguration("My Config",
                                                               CpuProfiler.CpuProfilerType.ART,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    startCapturingSuccess();
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.WAIT_TIMEOUT);

    // Sequence of states that should happen after stopping a capture and failing to do so
    Iterator<CpuProfilerStage.CaptureState> captureStates = Iterators.forArray(CpuProfilerStage.CaptureState.STOPPING,
                                                                               CpuProfilerStage.CaptureState.IDLE);
    // Listen to CAPTURE_STATE changes and check if the new state is equal to what we expect.
    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(
      CpuProfilerAspect.CAPTURE_STATE, () -> assertThat(myStage.getCaptureState()).isEqualTo(captureStates.next()));
    assertThat(myStage.getCapture()).isNotNull();
    stopCapturing();
    // Sanity check to see if we reached the final capture state
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.CAPTURE_STOP_FAILURE);
    assertThat(myStage.getCapture()).isNull();
  }

  @Test
  public void captureParsingFailureShowsErrorBalloon() throws IOException {
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ProfilingConfiguration config = new ProfilingConfiguration("My Config",
                                                               CpuProfiler.CpuProfilerType.ART,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    myCpuService.setValidTrace(true);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    startCapturingSuccess();

    // Sequence of states that should happen after stopping a capture that fails to be parsed
    Iterator<CpuProfilerStage.CaptureState> captureStates = Iterators.forArray(CpuProfilerStage.CaptureState.STOPPING,
                                                                               CpuProfilerStage.CaptureState.IDLE);
    AtomicInteger transitionsCount = new AtomicInteger();
    // Listen to CAPTURE_STATE changes and check if the new state is equal to what we expect.
    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(
      CpuProfilerAspect.CAPTURE_STATE, () -> {
        transitionsCount.getAndIncrement();
        assertThat(myStage.getCaptureState()).isEqualTo(captureStates.next());
      });

    // Listen to CAPTURE_PARSING and check if we goes through parsing state before parsing fails.
    AtomicBoolean aspectFired = new AtomicBoolean(false);
    myStage.getCaptureParser().getAspect().addDependency(observer).onChange(
      CpuProfilerAspect.CAPTURE_PARSING, () -> {
        aspectFired.set(true);
        assertThat(myStage.getCaptureParser().isParsing()).isTrue();
      });
    stopCapturing();
    // Sanity check to see if we reached the final capture state
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.PARSING_FAILURE);

    assertThat(transitionsCount.get()).isEqualTo(2);
    assertThat(aspectFired.get()).isTrue();
  }

  @Test
  public void tracePreProcessingFailureShowsErrorBalloon() throws IOException {
    // Enable SIMPLEPERF_HOST flag to make sure we'll preprocess the trace
    myServices.enableSimpleperfHost(true);
    // Make sure the TracePreProcessor fails to pre-process the trace
    ((FakeTracePreProcessor)myServices.getSimpleperfTracePreProcessor()).setFailedToPreProcess(true);
    // Select a simpleperf configuration
    ProfilingConfiguration config = new ProfilingConfiguration("My Config",
                                                               CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    // Use a trace that is not a raw simpleperf trace. That should cause pre-process to return a failure.
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    captureSuccessfully();

    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.PREPROCESS_FAILURE);

    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    // Intuitively, capture successfully would set a valid capture. However, failing to pre-process sets the capture to null
    assertThat(myStage.getCapture()).isNull();
  }

  @Test
  public void importTraceModeOnlyEnabledWhenImportSessionFlagIsSet() {
    StudioProfilers profilers = myStage.getStudioProfilers();
    myServices.enableImportTrace(false);

    File traceFile = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");
    CpuProfilerStage stage = new CpuProfilerStage(profilers, traceFile);
    // Import trace flag is not set. Inspect trace mode should be disabled.
    assertThat(stage.isImportTraceMode()).isFalse();

    myServices.enableImportTrace(true);
    stage = new CpuProfilerStage(profilers, traceFile);
    // Flag is enabled, passing a non-null file to the constructor will set the stage to inspect trace mode.
    assertThat(stage.isImportTraceMode()).isTrue();

    stage = new CpuProfilerStage(profilers, null);
    // Similarly, passing null to the constructor will set the stage to normal mode.
    assertThat(stage.isImportTraceMode()).isFalse();

    stage = new CpuProfilerStage(profilers);
    // Not specifying whether the stage is initiated in inspect trace mode is the same as initializing it in normal mode.
    assertThat(stage.isImportTraceMode()).isFalse();
  }

  @Test
  public void corruptedTraceInImportTraceModeShowsABalloon() {
    StudioProfilers profilers = myStage.getStudioProfilers();
    myServices.enableImportTrace(true);

    FakeFeatureTracker tracker = (FakeFeatureTracker)myServices.getFeatureTracker();
    // Sanity check to verify the last import trace status was not set yet
    assertThat(tracker.getLastImportTraceStatus()).isNull();

    File traceFile = CpuProfilerTestUtils.getTraceFile("corrupted_trace.trace");
    CpuProfilerStage stage = new CpuProfilerStage(profilers, traceFile);
    stage.enter();
    // Import trace mode is enabled successfully
    assertThat(stage.isImportTraceMode()).isTrue();

    // We should show a balloon saying the import has failed
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.IMPORT_TRACE_PARSING_FAILURE);

    // We should track failed imports
    assertThat(tracker.getLastCpuProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER);
    assertThat(tracker.getLastImportTraceStatus()).isFalse();
  }

  @Test
  public void abortParsingImportTraceFileShowsABalloon() {
    StudioProfilers profilers = myStage.getStudioProfilers();
    myServices.enableImportTrace(true);
    File traceFile = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");

    FakeParserCancelParsing parser = new FakeParserCancelParsing(myServices);
    CpuProfilerStage stage = new CpuProfilerStage(profilers, traceFile, parser);
    stage.enter();
    // Import trace mode is enabled successfully
    assertThat(stage.isImportTraceMode()).isTrue();

    // We should show a balloon saying the parsing was aborted, because FakeParserCancelParsing emulates a cancelled parsing task
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.IMPORT_TRACE_PARSING_ABORTED);
  }

  @Test
  public void abortParsingRecordedTraceFileShowsABalloon() {
    StudioProfilers profilers = myStage.getStudioProfilers();
    FakeParserCancelParsing parser = new FakeParserCancelParsing(myServices);
    CpuProfilerStage stage = new CpuProfilerStage(profilers, null, parser);
    stage.enter();
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS);
    stage.startCapturing();
    myCpuService.setValidTrace(true);
    stopCapturing(stage);

    // We should show a balloon saying the parsing was aborted, because FakeParserCancelParsing emulates a cancelled parsing task
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.PARSING_ABORTED);
  }

  @Test
  public void exitStageShouldCallParserAbort() {
    StudioProfilers profilers = myStage.getStudioProfilers();

    FakeParserCancelParsing parser = new FakeParserCancelParsing(myServices);
    CpuProfilerStage stage = new CpuProfilerStage(profilers, null, parser);
    stage.enter();
    assertThat(parser.isAbortParsingCalled()).isFalse();
    stage.exit();
    assertThat(parser.isAbortParsingCalled()).isTrue();
  }

  @Test
  public void captureIsSetWhenOpeningStageInImportTraceMode() {
    StudioProfilers profilers = myStage.getStudioProfilers();
    myServices.enableImportTrace(true);

    FakeFeatureTracker tracker = (FakeFeatureTracker)myServices.getFeatureTracker();
    // Sanity check to verify the last import trace status was not set yet
    assertThat(tracker.getLastImportTraceStatus()).isNull();

    File traceFile = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");
    CpuProfilerStage stage = new CpuProfilerStage(profilers, traceFile);
    stage.enter();
    // Import trace mode is enabled successfully
    assertThat(stage.isImportTraceMode()).isTrue();
    ProfilerTimeline timeline = stage.getStudioProfilers().getTimeline();
    Range captureRange = stage.getCapture().getRange();
    double expansionAmount = ((long)(captureRange.getLength() * CpuProfilerStage.IMPORTED_TRACE_VIEW_EXPAND_PERCENTAGE));
    assertThat(timeline.isPaused()).isTrue();
    assertThat((long)timeline.getDataRange().getMin()).isEqualTo((long)captureRange.getMin());
    assertThat((long)(timeline.getDataRange().getMax() - expansionAmount)).isEqualTo((long)(captureRange.getMax()));
    // Need 1 because of floating point precision rounding error on large numbers.
    assertThat(timeline.getViewRange().getMin() + expansionAmount).isWithin(1).of(timeline.getDataRange().getMin());
    assertThat(stage.getCapture()).isNotNull();

    // We should track successful imports
    assertThat(tracker.getLastCpuProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);
    assertThat(tracker.getLastImportTraceStatus()).isTrue();
  }

  @Test
  public void threadsDataComesFromCaptureInImportTraceMode() {
    StudioProfilers profilers = myStage.getStudioProfilers();
    myServices.enableImportTrace(true);
    File traceFile = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");
    CpuProfilerStage stage = new CpuProfilerStage(profilers, traceFile);
    stage.enter();
    // Import trace mode is enabled successfully
    assertThat(stage.isImportTraceMode()).isTrue();

    CpuCapture capture = stage.getCapture();
    int captureThreadsCount = capture.getThreads().size();
    // Check that stage's Threads model has the same size of capture threads list (which is not empty)
    assertThat(stage.getThreadStates().getSize()).isEqualTo(captureThreadsCount);
    assertThat(captureThreadsCount).isGreaterThan(0);

    // Now check that capture contains all the threads from the stage's model.
    for (int i = 0; i < captureThreadsCount; i++) {
      int tid = stage.getThreadStates().get(i).getThreadId();
      assertThat(capture.containsThread(tid)).isTrue();
    }
  }

  @Test
  public void captureAlwaysSelectedInImportTraceMode() {
    StudioProfilers profilers = myStage.getStudioProfilers();
    myServices.enableImportTrace(true);
    File traceFile = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");
    CpuProfilerStage stage = new CpuProfilerStage(profilers, traceFile);
    stage.enter();
    // Import trace mode is enabled successfully
    assertThat(stage.isImportTraceMode()).isTrue();

    CpuCapture capture = stage.getCapture();
    assertThat(myStage.getStudioProfilers().getTimeline().getSelectionRange().getMin()).isEqualTo(capture.getRange().getMin());
    assertThat(myStage.getStudioProfilers().getTimeline().getSelectionRange().getMax()).isEqualTo(capture.getRange().getMax());
    // Pretend to clear the selection from UI.
    myStage.getSelectionModel().clear();
    assertThat(myStage.getStudioProfilers().getTimeline().getSelectionRange().getMin()).isEqualTo(capture.getRange().getMin());
    assertThat(myStage.getStudioProfilers().getTimeline().getSelectionRange().getMax()).isEqualTo(capture.getRange().getMax());
  }

  @Test
  public void testCaptureFilterFeatureTrack() {
    final FakeFeatureTracker tracker = (FakeFeatureTracker)myServices.getFeatureTracker();

    // Capture a trace to apply filter on.
    captureSuccessfully();

    FilterModel filterModel = new FilterModel();
    Filter filter = Filter.EMPTY_FILTER;
    filterModel.setFilter(filter);

    myStage.setCaptureFilter(filter);

    FilterMetadata filterMetadata = tracker.getLastFilterMetadata();
    assertThat(filterMetadata).isNotNull();
    assertThat(filterMetadata.getFilterTextLength()).isEqualTo(0);
    assertThat(filterMetadata.getFeaturesUsed()).isEqualTo(0);

    // Test with some filter features and non empty text

    filter = new Filter("some", true, true);
    filterModel.setFilter(filter);
    myStage.setCaptureFilter(filter);
    filterMetadata = tracker.getLastFilterMetadata();
    assertThat(filterMetadata).isNotNull();
    assertThat(filterMetadata.getFilterTextLength()).isEqualTo(4);
    assertThat(filterMetadata.getFeaturesUsed()).isEqualTo(FilterMetadata.MATCH_CASE | FilterMetadata.IS_REGEX);
  }

  @Test
  public void sessionChangeShouldntAffectStageSession() {
    assertThat(myCpuService.getStartStopCapturingSession()).isNull();
    // get profilers session
    Common.Session stageSession = myStage.getStudioProfilers().getSession();
    startCapturingSuccess();
    // startCapturing should set startStopSession in FakeCpuService to the one used in the startProfilingAppRequest
    assertThat(myCpuService.getStartStopCapturingSession()).isEqualTo(stageSession);

    Common.Session otherSession = Common.Session.getDefaultInstance();
    // Make sure the sessions are different
    assertThat(otherSession).isNotEqualTo(stageSession);

    myStage.getStudioProfilers().getSessionsManager().setSession(otherSession);
    // Sanity check to verify the session was indeed changed.
    assertThat(myStage.getStudioProfilers().getSession()).isEqualTo(otherSession);

    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    stopCapturing();

    // stopCapturing should set startStopSession in FakeCpuService to the one used in the stopProfilingAppRequest. This session should be
    // the one set when creating the profiler stage and not the one that was selected by the time the request was made.
    assertThat(myCpuService.getStartStopCapturingSession()).isEqualTo(stageSession);
  }

  @Test
  public void testMemoryLiveAllocationIsDisabledIfApplicable() {
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);

    // Initialize all conditions to false.
    myServices.getPersistentProfilerPreferences().setInt(MemoryProfilerStage.LIVE_ALLOCATION_SAMPLING_PREF, 1);
    myServices.enableLiveAllocationsSampling(false);
    addAndSetDevice(AndroidVersion.VersionCodes.N_MR1, "FOO");
    ProfilingConfiguration config = new ProfilingConfiguration("My Instrumented Config",
                                                               CpuProfiler.CpuProfilerType.ART,
                                                               CpuProfiler.CpuProfilerMode.INSTRUMENTED);
    config.setDisableLiveAllocation(false);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    myFakeTransportService.setAgentStatus(Common.AgentData.getDefaultInstance());

    // Live allocation sampling rate should remain the same.
    startCapturingSuccess();
    assertThat(myMemoryService.getSamplingRate()).isEqualTo(1);
    stopCapturing();
    assertThat(myMemoryService.getSamplingRate()).isEqualTo(1);

    // Enable feature flag.
    // Live allocation sampling rate should still remain the same.
    myServices.enableLiveAllocationsSampling(true);
    startCapturingSuccess();
    assertThat(myMemoryService.getSamplingRate()).isEqualTo(1);
    stopCapturing();
    assertThat(myMemoryService.getSamplingRate()).isEqualTo(1);

    // Set agent status to ATTACHED.
    // Live allocation sampling rate should still remain the same.
    myFakeTransportService.setAgentStatus(Common.AgentData.newBuilder()
                                            .setStatus(Common.AgentData.Status.ATTACHED)
                                            .build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    startCapturingSuccess();
    assertThat(myMemoryService.getSamplingRate()).isEqualTo(1);
    stopCapturing();
    assertThat(myMemoryService.getSamplingRate()).isEqualTo(1);

    // Set an O+ device.
    // Live allocation sampling rate should still remain the same.
    addAndSetDevice(AndroidVersion.VersionCodes.O, "FOO");
    startCapturingSuccess();
    assertThat(myMemoryService.getSamplingRate()).isEqualTo(1);
    stopCapturing();
    assertThat(myMemoryService.getSamplingRate()).isEqualTo(1);

    // Set profiling config to true.
    // Now all conditions are met, live allocation should be disabled during capture and re-enabled after capture is stopped.
    config.setDisableLiveAllocation(true);
    startCapturingSuccess();
    assertThat(myMemoryService.getSamplingRate()).isEqualTo(MemoryProfilerStage.LiveAllocationSamplingMode.NONE.getValue());
    stopCapturing();
    assertThat(myMemoryService.getSamplingRate()).isEqualTo(1);
  }

  @Test
  public void traceNotPreProcessedWhenFlagDisabled() throws IOException {
    myServices.enableSimpleperfHost(false);
    FakeTracePreProcessor preProcessor = (FakeTracePreProcessor)myServices.getSimpleperfTracePreProcessor();

    ProfilingConfiguration config1 = new ProfilingConfiguration("My simpleperf config",
                                                                CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                                                CpuProfiler.CpuProfilerMode.SAMPLED);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config1);
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    captureSuccessfully();

    assertThat(preProcessor.isTracePreProcessed()).isFalse();
  }

  @Test
  public void traceIsPreProcessedWhenFlagEnabled() throws IOException {
    myServices.enableSimpleperfHost(true);
    FakeTracePreProcessor preProcessor = (FakeTracePreProcessor)myServices.getSimpleperfTracePreProcessor();

    ProfilingConfiguration config1 = new ProfilingConfiguration("My simpleperf config",
                                                                CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                                                CpuProfiler.CpuProfilerMode.SAMPLED);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config1);
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    captureSuccessfully();

    assertThat(preProcessor.isTracePreProcessed()).isTrue();
  }

  @Test
  public void traceNotPreProcessedIfNotSimpleperf() throws IOException {
    myServices.enableSimpleperfHost(true);
    FakeTracePreProcessor preProcessor = (FakeTracePreProcessor)myServices.getSimpleperfTracePreProcessor();

    ProfilingConfiguration config1 = new ProfilingConfiguration("My simpleperf config",
                                                                CpuProfiler.CpuProfilerType.ART,
                                                                CpuProfiler.CpuProfilerMode.SAMPLED);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config1);
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString("basic.trace"));
    captureSuccessfully();

    assertThat(preProcessor.isTracePreProcessed()).isFalse();
  }

  private void addAndSetDevice(int featureLevel, String serial) {
    int deviceId = serial.hashCode();
    Common.Device device = Common.Device.newBuilder()
      .setDeviceId(deviceId)
      .setFeatureLevel(featureLevel)
      .setSerial(serial)
      .setState(Common.Device.State.ONLINE).build();
    Common.Process process = Common.Process.newBuilder()
      .setPid(20)
      .setDeviceId(deviceId)
      .setState(Common.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    myFakeTransportService.addDevice(device);
    // Adds at least one ALIVE process as well. Otherwise, StudioProfilers would prefer selecting a device that has live processes.
    myFakeTransportService.addProcess(device, process);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new device to be picked up
    myStage.getStudioProfilers().setProcess(device, null);
    // Setting the device will change the stage. We need to go back to CpuProfilerStage
    myStage.getStudioProfilers().setStage(myStage);
  }

  private void captureSuccessfully() {
    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture successfully with a valid trace
    myServices.setOnExecute(() -> {
      // First, the main executor is going to be called to execute stopCapturingCallback, which should tell the capture parser to start
      // parsing.
      assertThat(myStage.getCaptureParser().isParsing()).isTrue();
      // Whenever the capture is being parsed, profiler mode should be set to EXPANDED
      assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
      // Then, the next time the main executor is called, it will parse the capture successfully
      // and set the capture state to IDLE
      myServices.setOnExecute(() -> {
        assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
        assertThat(myStage.getCapture()).isNotNull();
      });
    });
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    stopCapturing();
  }

  /**
   * This is a convenience method to start a capture successfully.
   * It sets all the necessary states in the service and call {@link CpuProfilerStage#startCapturing}.
   */
  private void startCapturingSuccess() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS);
    startCapturing();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
  }

  /**
   * This is a convenience method to start a capture.
   * It makes sure to check the intermediate state (STARTING) between pressing the "Start" button and effectively start capturing.
   */
  private void startCapturing() {
    myServices.setPrePoolExecutor(() -> assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.STARTING));
    myStage.startCapturing();
  }

  /**
   * This is a convenience method to stop a capture.
   * It makes sure to check for the intermediate state (STOPPING) between pressing the "Stop" button and effectively stop capturing. Also,
   * it verifies the {@link CpuCaptureParser} is parsing the capture after we stop capturing.
   */
  private void stopCapturing(CpuProfilerStage stage) {
    // The pre executor will make sure we pass through STOPPING state before parsing.
    Runnable stoppingToParsing = () -> {
      assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.STOPPING);
      // Then it will make sure the CpuCaptureParser is parsing the capture.
      myServices.setPrePoolExecutor(() -> assertThat(stage.getCaptureParser().isParsing()).isTrue());
    };
    // There is an extra call to the pool executor in simpleperf captures , which happens during STOPPING, to convert the raw trace data
    // into a parsable trace file.
    myServices.setPrePoolExecutor(() -> {
      assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.STOPPING);
      myServices.setPrePoolExecutor(stoppingToParsing);
    });
    stage.stopCapturing();
  }

  private void stopCapturing() {
    stopCapturing(myStage);
  }

  /**
   * An instance of {@link CpuCaptureParser} that will always cancel the {@link CompletableFuture<CpuCapture>} task responsible for parsing
   * a trace into a {@link CpuCapture}. This way, we can test the behavior of {@link CpuProfilerStage} when such scenario happens.
   */
  private static class FakeParserCancelParsing extends CpuCaptureParser {

    private boolean myAbortParsingCalled = false;

    public FakeParserCancelParsing(@NotNull IdeProfilerServices services) {
      super(services);
    }

    @Override
    public void abortParsing() {
      myAbortParsingCalled = true;
      super.abortParsing();
    }

    @Nullable
    @Override
    public CompletableFuture<CpuCapture> parse(@NotNull Common.Session session,
                                               long traceId,
                                               @NotNull ByteString traceData,
                                               CpuProfiler.CpuProfilerType profilerType) {
      CompletableFuture<CpuCapture> capture = new CompletableFuture<>();
      capture.cancel(true);
      return capture;
    }

    @Nullable
    @Override
    public CompletableFuture<CpuCapture> parse(@NotNull File traceFile) {
      CompletableFuture<CpuCapture> capture = new CompletableFuture<>();
      capture.cancel(true);
      return capture;
    }

    public boolean isAbortParsingCalled() {
      return myAbortParsingCalled;
    }
  }
}
