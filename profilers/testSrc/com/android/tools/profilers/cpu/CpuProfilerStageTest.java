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
import static com.google.common.truth.Truth.assertThat;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.FakeFeatureTracker;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioMonitorStage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.CpuProfilerStage.CaptureState;
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration;
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration;
import com.android.tools.profilers.event.FakeEventService;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuProfilerStageTestChannel", myTransportService, new FakeEventService());

  private CpuProfilerStage myStage;

  private final FakeIdeProfilerServices myServices;

  public CpuProfilerStageTest() {
    myServices = new FakeIdeProfilerServices();
    // This test file assumes always using Transport Pipeline.
    myServices.enableEventsPipeline(true);
  }

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myServices, myTimer);
    // One second must be enough for new devices (and processes) to be picked up
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStage = new CpuProfilerStage(profilers);
    myStage.getStudioProfilers().setStage(myStage);
    ProfilersTestData.populateThreadData(myTransportService, ProfilersTestData.SESSION_DATA.getStreamId());
  }

  @Test
  public void testDefaultValues() {
    assertThat(myStage.getCpuTraceDataSeries()).isNotNull();
    assertThat(myStage.getThreadStates()).isNotNull();
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(myStage.getAspect()).isNotNull();
  }

  @Test
  public void testStartCapturing() throws InterruptedException, IOException {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Start a successful capture
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
    CpuProfilerTestUtils.stopCapturing(myStage, myTransportService, true, CpuProfilerTestUtils.readValidTrace());
  }

  @Test
  public void startCapturingInstrumented() throws InterruptedException {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    // Start a capture using INSTRUMENTED mode
    ProfilingConfiguration instrumented = new ArtInstrumentedConfiguration("My Instrumented Config");
    myStage.getProfilerConfigModel().setProfilingConfiguration(instrumented);
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
  }

  @Test
  public void recordingPanelHasDefaultConfigurations() {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    // Start a capture using INSTRUMENTED mode
    List<ProfilingConfiguration> defaultConfigurations = myStage.getProfilerConfigModel().getDefaultProfilingConfigurations();
    assertThat(myStage.getRecordingModel().getBuiltInOptions()).hasSize(defaultConfigurations.size());
    for (int i = 0; i < defaultConfigurations.size(); i++) {
      assertThat(myStage.getRecordingModel().getBuiltInOptions().get(i).getTitle()).isEqualTo(defaultConfigurations.get(i).getName());
    }
  }

  @Test
  public void testStopCapturingFailure() throws InterruptedException {
    // Start a successful capture
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);

    // We expect two state changes, first is stopping and the other is IDLE after the stop request returns failure
    AspectObserver observer = new AspectObserver();
    CountDownLatch latch = CpuProfilerTestUtils.waitForProfilingStateChangeSequence(myStage,
                                                                                    observer,
                                                                                    CaptureState.STOPPING,
                                                                                    CaptureState.IDLE);
    CpuProfilerTestUtils.stopCapturing(myStage, myTransportService, false, null);
    latch.await();
  }

  @Test
  public void testStopCapturingInvalidTrace() throws InterruptedException {
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);

    // Start a successful capture
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);

    // Complete a capture successfully, but with an empty trace
    CpuProfilerTestUtils.stopCapturing(myStage, myTransportService, true, ByteString.EMPTY);
    assertThat(myStage.getCaptureState()).isEqualTo(CaptureState.IDLE);
  }

  @Test
  public void testStopCapturingSuccessfully() throws InterruptedException, IOException {
    CpuProfilerTestUtils.captureSuccessfully(myStage, myTransportService, CpuProfilerTestUtils.readValidTrace());
  }

  @Test
  public void testJumpToLiveIfOngoingRecording() throws InterruptedException {
    StreamingTimeline timeline = myStage.getTimeline();
    timeline.setStreaming(false);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(timeline.isStreaming()).isFalse();

    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
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
  public void rangeIntersectionReturnsASingleTraceId() {
    int traceId1 = 1;
    int traceId2 = 2;

    addTraceInfoHelper(traceId1, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), TimeUnit.MICROSECONDS.toNanos(10),
                       TimeUnit.MICROSECONDS.toNanos(20), Trace.TraceConfiguration.getDefaultInstance());
    addTraceInfoHelper(traceId2, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), TimeUnit.MICROSECONDS.toNanos(30),
                       TimeUnit.MICROSECONDS.toNanos(40), Trace.TraceConfiguration.getDefaultInstance());

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
  public void exitingAndReEnteringStageAgainShouldPreserveProfilingTime() throws InterruptedException {
    // Set a non-zero start time to test non-default values
    Range dataRange = myStage.getTimeline().getDataRange();
    double currentMax = dataRange.getMax() + TimeUnit.SECONDS.toMicros(10);
    dataRange.setMax(currentMax);
    myTimer.setCurrentTimeNs(TimeUnit.MICROSECONDS.toNanos((long)currentMax));

    // Start capturing
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);

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
  }

  @Test
  public void suggestedProfilingConfigurationIsSimpleperf() {
    // Make sure simpleperf is supported by setting an O device.
    addAndSetDevice(AndroidVersion.VersionCodes.O, "Any Serial");

    myServices.setNativeProfilingConfigurationPreferred(false);
    myStage = new CpuProfilerStage(myStage.getStudioProfilers());
    myStage.enter();
    // ART Sampled should be the default configuration when there is no preference for a native config.
    assertThat(
      myStage.getProfilerConfigModel().getProfilingConfiguration().getName()).isEqualTo(FakeIdeProfilerServices.FAKE_SIMPLEPERF_NAME);

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
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);

    // Go back to monitor stage and go back to a new Cpu profiler stage
    myStage.getStudioProfilers().setStage(new StudioMonitorStage(myStage.getStudioProfilers()));
    CpuProfilerStage stage = new CpuProfilerStage(myStage.getStudioProfilers());
    myStage.getStudioProfilers().setStage(stage);
    // Trigger an update to kick off the InProgressTraceHandler which syncs the capture state.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Make sure we're capturing
    assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
    CpuProfilerTestUtils.stopCapturing(stage, myTransportService, true,
                                       CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
    // Switches to the capture stage.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getStudioProfilers().getStage()).isInstanceOf(CpuCaptureStage.class);

    // Make sure we tracked the correct configuration
    ProfilingConfiguration trackedConfig =
      ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata().getProfilingConfiguration();
    assertThat(trackedConfig.getTraceType()).isEqualTo(Trace.TraceType.SIMPLEPERF);
  }

  @Test
  public void transitsToIdleWhenApiInitiatedTracingEnds() {
    // API-initiated tracing starts.
    Trace.TraceConfiguration apiTracingConfig = Trace.TraceConfiguration.newBuilder()
      .setInitiationType(Trace.TraceInitiationType.INITIATED_BY_API)
      .setUserOptions(Trace.UserOptions.newBuilder().setTraceType(Trace.TraceType.ART))
      .build();
    addTraceInfoHelper(1, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), 100, -1, apiTracingConfig);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);

    addTraceInfoHelper(1, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), 100, 101, apiTracingConfig);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
  }

  @Test
  public void apiInitiatedCaptureUsageTracking() {
    // Trace 1: not API-initiated. Shouldn't have API-tracing usage.
    addTraceInfoHelper(1, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), 10, 11,
                       Trace.TraceConfiguration.newBuilder().setInitiationType(Trace.TraceInitiationType.INITIATED_BY_UI).build());

    final FakeFeatureTracker featureTracker = (FakeFeatureTracker)myServices.getFeatureTracker();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(featureTracker.getApiTracingUsageCount()).isEqualTo(0);

    // Trace 2: API-initiated
    addTraceInfoHelper(2, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), 10, 11,
                       Trace.TraceConfiguration.newBuilder().setInitiationType(Trace.TraceInitiationType.INITIATED_BY_API).build());

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(featureTracker.getApiTracingUsageCount()).isGreaterThan(0);
    assertThat(featureTracker.getLastCpuAPiTracingPathProvided()).isTrue();
  }

  @Test
  public void rightOptionSelectedForApiInitTracing() {
    // API-initiated tracing starts.
    Trace.TraceConfiguration apiTracingConfig = Trace.TraceConfiguration.newBuilder()
      .setInitiationType(Trace.TraceInitiationType.INITIATED_BY_API)
      .setUserOptions(Trace.UserOptions.newBuilder().setTraceType(Trace.TraceType.ART))
      .build();
    addTraceInfoHelper(1, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), 100, -1, apiTracingConfig);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);

    assertThat(myStage.isApiInitiatedTracingInProgress()).isTrue();
    assertThat(myStage.getRecordingModel().isRecording()).isTrue();
    assertThat(myStage.getRecordingModel().getSelectedOption().getTitle())
      .isEqualTo(CpuProfilerStage.API_INITIATED_TRACING_PROFILING_CONFIG.getName());
  }

  @Test
  public void rightOptionSelectedForStartUpTracing() {
    Trace.TraceConfiguration startUpTracingConfig = Trace.TraceConfiguration.newBuilder()
      .setInitiationType(Trace.TraceInitiationType.INITIATED_BY_STARTUP)
      .setUserOptions(Trace.UserOptions.newBuilder()
                        .setName(FakeIdeProfilerServices.FAKE_ATRACE_NAME)
                        .setTraceType(Trace.TraceType.PERFETTO))
      .build();
    addTraceInfoHelper(1, FAKE_DEVICE_ID, FAKE_PROCESS.getPid(), 100, -1, startUpTracingConfig);

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING);
    assertThat(myStage.getCaptureInitiationType()).isEqualTo(Trace.TraceInitiationType.INITIATED_BY_STARTUP);
    assertThat(myStage.getRecordingModel().isRecording()).isTrue();
    assertThat(myStage.getRecordingModel().getSelectedOption()).isNotNull();
    assertThat(myStage.getRecordingModel().getSelectedOption().getTitle()).isEqualTo(FakeIdeProfilerServices.FAKE_ATRACE_NAME);
  }

  @Test
  public void captureStageTransitionTest() throws Exception {
    // Needs to be set true else null is inserted into the capture parser.
    myServices.setShouldProceedYesNoDialog(true);
    // Select the right configuration for trace.
    ProfilingConfiguration config = new ArtSampledConfiguration("My Config");
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    CpuProfilerTestUtils.captureSuccessfully(myStage, myTransportService, CpuProfilerTestUtils.readValidTrace());
    assertThat(myStage.getStudioProfilers().getStage().getClass()).isAssignableTo(CpuCaptureStage.class);
  }

  @Test
  public void implicitSelectionOfCpuCaptureSessionArtifactProtoIsMadePostRecording() throws Exception {
    // Capture a CPU Trace, generating a CpuSessionArtifact
    CpuProfilerTestUtils.captureSuccessfully(myStage, myTransportService, CpuProfilerTestUtils.readValidTrace());
    // Make sure implicit selection of recently recorded capture's respective artifact proto is saved
    assertThat(myStage.getStudioProfilers().getSessionsManager().getSelectedArtifactProto()).isInstanceOf(Cpu.CpuTraceInfo.class);
  }

  @Test
  public void configurationShouldBeTheOnGoingProfilingAfterExitAndEnter() throws InterruptedException {
    ProfilingConfiguration testConfig = new SimpleperfConfiguration(FakeIdeProfilerServices.FAKE_SIMPLEPERF_NAME);
    myStage.getProfilerConfigModel().setProfilingConfiguration(testConfig);
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
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

  @Ignore
  @Test
  public void setCaptureWhileCapturingShouldParseAndContinueInCapturingState() throws InterruptedException, IOException {
    // First generate a finished capture that we can select
    long traceId =
      CpuProfilerTestUtils.captureSuccessfully(myStage, myTransportService, CpuProfilerTestUtils.readValidTrace());

    myStage = new CpuProfilerStage(myStage.getStudioProfilers());
    // Invoke enter to update the profiling configuration model
    myStage.getStudioProfilers().setStage(myStage);
    myTimer.setCurrentTimeNs(2);  // Update the timer to generate a different trace id for the second trace.
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);

    // Select the previous capture
    AspectObserver observer = new AspectObserver();
    CountDownLatch parseLatch = CpuProfilerTestUtils.waitForParsingStartFinish(myStage, observer);
    myStage.setAndSelectCapture(traceId);
    parseLatch.await();
    assertThat(myStage.getCaptureState()).isEqualTo(CaptureState.CAPTURING);
    assertThat(myStage.getCaptureParser().isParsing()).isFalse();
  }

  @Ignore
  @Test
  public void setCaptureWhileIdleShouldParseAndStayInIdleState() throws InterruptedException, IOException {
    // First generate a finished capture that we can select
    long traceId =
      CpuProfilerTestUtils.captureSuccessfully(myStage, myTransportService, CpuProfilerTestUtils.readValidTrace());

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
    // Select the right configuration for trace.
    myStage.getProfilerConfigModel().setProfilingConfiguration(new ArtSampledConfiguration("My Config"));
    // Capture a new trace.
    long traceId =
      CpuProfilerTestUtils.captureSuccessfully(myStage, myTransportService, CpuProfilerTestUtils.readValidTrace());
    // Now change the recording type configuration.
    myStage.getProfilerConfigModel().setProfilingConfiguration(new SimpleperfConfiguration("new-simpleperf"));
    // Load the previously recorded trace. It should be parsed as the trace's own type, not the selected configuration type.
    myStage.setAndSelectCapture(traceId);
    assertThat(myStage.getStudioProfilers().getStage()).isInstanceOf(CpuCaptureStage.class);
  }

  @Test
  @Ignore("b/209669048")
  public void cpuMetadataSuccessfulCapture() throws InterruptedException, IOException {
    CpuCaptureParser.clearPreviouslyLoadedCaptures();
    ArtSampledConfiguration config = new ArtSampledConfiguration("My Config");
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    CpuProfilerTestUtils.captureSuccessfully(myStage, myTransportService, CpuProfilerTestUtils.readValidTrace());

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.SUCCESS);
    ArtSampledConfiguration metadataConfig = (ArtSampledConfiguration)metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getTraceType()).isEqualTo(Trace.TraceType.ART);
    assertThat(metadata.getParsingTimeMs()).isGreaterThan(0L);
    assertThat(metadata.getStoppingTimeMs()).isEqualTo(FakeCpuService.FAKE_STOPPING_DURATION_MS);
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
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);

    // Increment 3 seconds on data range to simulate time has passed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    CpuProfilerTestUtils.stopCapturing(myStage, myTransportService, false, null);
    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.STOP_FAILED_STOP_COMMAND_FAILED);
    // Profiling Configurations should remain the same
    ArtSampledConfiguration metadataConfig = (ArtSampledConfiguration)metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getTraceType()).isEqualTo(Trace.TraceType.ART);
    //assertThat(metadataConfig.getMode()).isEqualTo(Trace.TraceMode.SAMPLED);
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
  @Ignore("b/209669048")
  public void cpuMetadataFailureParsing() throws InterruptedException, IOException {
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ArtSampledConfiguration config = new ArtSampledConfiguration("My Config");
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);

    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
    // Simulate a 3 second capture.
    CpuProfilerTestUtils
      .stopCapturing(myStage, myTransportService, true, CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"),
                     TimeUnit.SECONDS.toNanos(3));

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_ERROR);
    ArtSampledConfiguration metadataConfig = (ArtSampledConfiguration)metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getTraceType()).isEqualTo(Trace.TraceType.ART);
    // Trace was generated, so trace size should be greater than 0
    assertThat(metadata.getTraceFileSizeBytes()).isGreaterThan(0);
    // Capture duration is calculated from the elapsed time since recording has started.
    assertThat(metadata.getCaptureDurationMs()).isEqualTo(TimeUnit.SECONDS.toMillis(3));
    // Trace was not parsed correctly, so parsing time and recording duration should be 0 (unset)
    assertThat(metadata.getParsingTimeMs()).isEqualTo(0);
    assertThat(metadata.getRecordDurationMs()).isEqualTo(0);
  }

  @Test
  @Ignore("b/209669048")
  public void cpuMetadataFailureUserAbort() throws InterruptedException {
    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    ArtSampledConfiguration config = new ArtSampledConfiguration("My Config");
    config.setProfilingSamplingIntervalUs(10);
    config.setProfilingBufferSizeInMb(15);
    ByteString largeTraceFile = ByteString.copyFrom(new byte[CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1]);
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    myServices.setShouldProceedYesNoDialog(false);

    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
    // Simulate a 3 second capture.
    CpuProfilerTestUtils.stopCapturing(myStage, myTransportService, true, largeTraceFile, TimeUnit.SECONDS.toNanos(3));

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING);
    // Profiling Configurations should remain the same.
    ArtSampledConfiguration metadataConfig = (ArtSampledConfiguration)metadata.getProfilingConfiguration();
    assertThat(metadataConfig.getProfilingSamplingIntervalUs()).isEqualTo(10);
    assertThat(metadataConfig.getProfilingBufferSizeInMb()).isEqualTo(15);
    assertThat(metadataConfig.getTraceType()).isEqualTo(Trace.TraceType.ART);
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

    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
    // Use a trace that is not a raw simpleperf trace. That should cause pre-process to return a failure.
    CpuProfilerTestUtils.stopCapturing(myStage, myTransportService, true,
                                       CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));

    CpuCaptureMetadata metadata = ((FakeFeatureTracker)myServices.getFeatureTracker()).getLastCpuCaptureMetadata();
    assertThat(metadata.getStatus()).isEqualTo(CpuCaptureMetadata.CaptureStatus.PREPROCESS_FAILURE);
    // We should still log the trace size if we fail to pre-process. As we're using "simpleperf.trace", the size should be greater than 0.
    assertThat(metadata.getTraceFileSizeBytes()).isGreaterThan(0);
  }

  @Test
  public void startCapturingJumpsToLiveData() throws InterruptedException {
    StreamingTimeline timeline = myStage.getTimeline();
    timeline.setStreaming(false);
    assertThat(timeline.isStreaming()).isFalse();

    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
    assertThat(timeline.isStreaming()).isTrue();
  }

  @Test
  public void testHasUserUsedCapture() throws InterruptedException {
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(0);
    assertThat(myStage.hasUserUsedCpuCapture()).isFalse();
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(1);
    assertThat(myStage.hasUserUsedCpuCapture()).isTrue();
  }

  @Test
  public void startCapturingFailureShowsErrorBalloon() throws InterruptedException {
    // Start a failing capture
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, false);
    // Sanity check to see if we reached the final capture state
    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.CAPTURE_START_FAILURE);
  }

  @Test
  public void stopCapturingFailureShowsErrorBalloon() throws InterruptedException {
    myStage.getProfilerConfigModel().setProfilingConfiguration(new SimpleperfConfiguration("My Config"));
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
    CpuProfilerTestUtils.stopCapturing(myStage, myTransportService, false, null);
    assertThat(myServices.getNotification()).isNotNull();
    assertThat(myServices.getNotification().getTitle()).contains("Recording failed to stop");
  }

  @Test
  @Ignore("b/209673164")
  public void captureParsingFailureShowsErrorBalloon() throws InterruptedException, IOException {
    ProfilingConfiguration config = new ArtSampledConfiguration("My Config");
    myStage.getProfilerConfigModel().setProfilingConfiguration(config);
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);

    // Try to parse a simpleperf trace with ART config. Parsing should fail.
    CpuProfilerTestUtils.stopCapturing(myStage, myTransportService, true,
                                       CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));
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

    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
    CpuProfilerTestUtils.stopCapturing(myStage, myTransportService, true,
                                       CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace"));

    assertThat(myServices.getNotification()).isEqualTo(CpuProfilerNotifications.PREPROCESS_FAILURE);

    assertThat(myStage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
  }

  @Test
  public void abortParsingRecordedTraceFileShowsABalloon() throws InterruptedException {
    myServices.setShouldProceedYesNoDialog(false);
    ByteString largeTraceFile = ByteString.copyFrom(new byte[CpuCaptureParser.MAX_SUPPORTED_TRACE_SIZE + 1]);
    CpuProfilerTestUtils.startCapturing(myStage, myTransportService, true);
    CpuProfilerTestUtils.stopCapturing(myStage, myTransportService, true, largeTraceFile);

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
  public void changingCaptureStateUpdatesOptionsModel() {
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
                                  Trace.TraceConfiguration configuration) {
    Cpu.CpuTraceInfo info = Cpu.CpuTraceInfo.newBuilder()
      .setTraceId(traceId)
      .setFromTimestamp(startTimestampNs)
      .setToTimestamp(endTimestampNs)
      .setConfiguration(configuration)
      .build();
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
      @NotNull File traceFile, long traceId, @Nullable Trace.TraceType preferredProfilerType, int idHint, @Nullable String nameHint) {
      CompletableFuture<CpuCapture> capture = new CompletableFuture<>();
      capture.cancel(true);
      return capture;
    }

    public boolean isAbortParsingCalled() {
      return myAbortParsingCalled;
    }
  }
}
