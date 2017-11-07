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


import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class CpuProfilerStage extends Stage implements CodeNavigator.Listener {
  @VisibleForTesting static final String HAS_USED_CPU_CAPTURE = "profiler.used.cpu.capture";

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 5, 10, "%");
  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(1, 5, 1, "");
  /**
   * Fake configuration to represent "Edit configurations..." entry on the profiling configurations combobox.
   */
  static final ProfilingConfiguration EDIT_CONFIGURATIONS_ENTRY = new ProfilingConfiguration();
  /**
   * Fake configuration to represent a separator on the profiling configurations combobox.
   */
  static final ProfilingConfiguration CONFIG_SEPARATOR_ENTRY = new ProfilingConfiguration();

  private static final long INVALID_CAPTURE_START_TIME = Long.MAX_VALUE;


  /**
   * Default capture details to be set after stopping a capture.
   * {@link CaptureModel.Details.Type#CALL_CHART} is used by default because it's useful and fast to compute.
   */
  private static final CaptureModel.Details.Type DEFAULT_CAPTURE_DETAILS = CaptureModel.Details.Type.CALL_CHART;

  private final CpuThreadsModel myThreadsStates;
  private final AxisComponentModel myCpuUsageAxis;
  private final AxisComponentModel myThreadCountAxis;
  private final AxisComponentModel myTimeAxisGuide;
  private final DetailedCpuUsage myCpuUsage;
  private final CpuStageLegends myLegends;
  private final DurationDataModel<CpuTraceInfo> myTraceDurations;
  private final EventMonitor myEventMonitor;
  private final SelectionModel mySelectionModel;
  private final EaseOutModel myInstructionsEaseOutModel;
  private final CpuProfilerConfigModel myProfilerModel;

  /**
   * {@link DurationDataModel} used when a trace recording in progress.
   */
  @NotNull
  private final DurationDataModel<DefaultDurationData> myInProgressTraceDuration;

  /**
   * Series used by {@link #myInProgressTraceDuration} when a trace recording in progress.
   * {@code myInProgressTraceSeries} will contain zero or one unfinished duration depending on the state of recording.
   * Should be cleared when stop capturing.
   */
  @NotNull
  private final DefaultDataSeries<DefaultDurationData> myInProgressTraceSeries;

  /**
   * The thread states combined with the capture states.
   */
  public enum ThreadState {
    RUNNING,
    RUNNING_CAPTURED,
    SLEEPING,
    SLEEPING_CAPTURED,
    DEAD,
    DEAD_CAPTURED,
    WAITING,
    WAITING_CAPTURED,
    UNKNOWN
  }

  public enum CaptureState {
    // Waiting for a capture to start (displaying the current capture or not)
    IDLE,
    // There is a capture in progress
    CAPTURING,
    // A capture is being parsed
    PARSING,
    // Waiting for the service to respond a start capturing call
    STARTING,
    // Waiting for the service to respond a stop capturing call
    STOPPING,
  }

  @NotNull
  private final CpuTraceDataSeries myCpuTraceDataSeries;

  private final AspectModel<CpuProfilerAspect> myAspect = new AspectModel<>();

  @NotNull
  private final CaptureModel myCaptureModel;

  /**
   * Represents the current state of the capture.
   * It is initialized here but updated indirectly in the constructor, which calls {@link #updateProfilingState()}.
   */
  @NotNull
  private CaptureState myCaptureState = CaptureState.IDLE;

  /**
   * If there is a capture in progress, stores its start time.
   */
  private long myCaptureStartTimeNs;

  private CaptureElapsedTimeUpdatable myCaptureElapsedTimeUpdatable;

  @NotNull
  private final UpdatableManager myUpdatableManager;

  /**
   * Responsible for parsing trace files into {@link CpuCapture}.
   * Parsed captures should be obtained from this object.
   */
  private final CpuCaptureParser myCaptureParser;

  @Nullable
  private Tooltip myTooltip;

  /**
   * State to track if an invalid (excluding "cancel") selection has been made.
   */
  private boolean mySelectionFailure;

  public CpuProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myCpuTraceDataSeries = new CpuTraceDataSeries();
    myProfilerModel = new CpuProfilerConfigModel(profilers, this);

    Range viewRange = getStudioProfilers().getTimeline().getViewRange();
    Range dataRange = getStudioProfilers().getTimeline().getDataRange();
    Range selectionRange = getStudioProfilers().getTimeline().getSelectionRange();

    myCpuUsage = new DetailedCpuUsage(profilers);

    myCpuUsageAxis = new AxisComponentModel(myCpuUsage.getCpuRange(), CPU_USAGE_FORMATTER);
    myCpuUsageAxis.setClampToMajorTicks(true);

    myThreadCountAxis = new AxisComponentModel(myCpuUsage.getThreadRange(), NUM_THREADS_AXIS);
    myThreadCountAxis.setClampToMajorTicks(true);

    myTimeAxisGuide = new AxisComponentModel(viewRange, TimeAxisFormatter.DEFAULT_WITHOUT_MINOR_TICKS);
    myTimeAxisGuide.setGlobalRange(dataRange);

    myLegends = new CpuStageLegends(myCpuUsage, dataRange);

    // Create an event representing the traces within the range.
    myTraceDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, getCpuTraceDataSeries()));
    myThreadsStates = new CpuThreadsModel(viewRange, this, getStudioProfilers().getProcessId(), getStudioProfilers().getSession());

    myInProgressTraceSeries = new DefaultDataSeries<>();
    myInProgressTraceDuration = new DurationDataModel<>(new RangedSeries<>(viewRange, myInProgressTraceSeries));

    myEventMonitor = new EventMonitor(profilers);

    mySelectionModel = new SelectionModel(selectionRange);
    mySelectionModel.addConstraint(myTraceDurations);
    mySelectionModel.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        mySelectionFailure = false;
        profilers.getIdeServices().getFeatureTracker().trackSelectRange();
        selectionChanged();
      }

      @Override
      public void selectionCleared() {
        mySelectionFailure = false;
        selectionChanged();
      }

      @Override
      public void selectionCreationFailure() {
        mySelectionFailure = true;
        selectionChanged();
        setProfilerMode(ProfilerMode.EXPANDED);
      }
    });

    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);

    myCaptureElapsedTimeUpdatable = new CaptureElapsedTimeUpdatable();
    updateProfilingState();
    myProfilerModel.updateProfilingConfigurations();

    myCaptureModel = new CaptureModel(this);
    myUpdatableManager = new UpdatableManager(getStudioProfilers().getUpdater());
    myCaptureParser = new CpuCaptureParser(getStudioProfilers().getIdeServices());
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfilerStage.class);
  }

  public boolean hasUserUsedCpuCapture() {
    return getStudioProfilers().getIdeServices().getProfilerPreferences().getBoolean(HAS_USED_CPU_CAPTURE, false);
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @NotNull
  public EaseOutModel getInstructionsEaseOutModel() {
    return myInstructionsEaseOutModel;
  }

  public AxisComponentModel getCpuUsageAxis() {
    return myCpuUsageAxis;
  }

  public AxisComponentModel getThreadCountAxis() {
    return myThreadCountAxis;
  }

  public AxisComponentModel getTimeAxisGuide() {
    return myTimeAxisGuide;
  }

  public DetailedCpuUsage getCpuUsage() {
    return myCpuUsage;
  }

  public CpuStageLegends getLegends() {
    return myLegends;
  }

  public void setTooltip(@Nullable Tooltip.Type type) {
    if (type != null && myTooltip != null && type.equals(myTooltip.getType())) {
      return;
    }
    if (myTooltip != null) {
      myTooltip.dispose();
    }

    if (type != null) {
      myTooltip = type.build(this);
    }
    else {
      myTooltip = null;
    }

    getAspect().changed(CpuProfilerAspect.TOOLTIP);
  }

  @Nullable
  public Tooltip getTooltip() {
    return myTooltip;
  }

  public DurationDataModel<CpuTraceInfo> getTraceDurations() {
    return myTraceDurations;
  }

  @NotNull
  public DurationDataModel<DefaultDurationData> getInProgressTraceDuration() {
    return myInProgressTraceDuration;
  }

  public String getName() {
    return "CPU";
  }

  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  public boolean isSelectionFailure() {
    return mySelectionFailure;
  }

  @Override
  public void enter() {
    myEventMonitor.enter();
    getStudioProfilers().getUpdater().register(myCpuUsage);
    getStudioProfilers().getUpdater().register(myInProgressTraceDuration);
    getStudioProfilers().getUpdater().register(myTraceDurations);
    getStudioProfilers().getUpdater().register(myCpuUsageAxis);
    getStudioProfilers().getUpdater().register(myThreadCountAxis);
    getStudioProfilers().getUpdater().register(myTimeAxisGuide);
    getStudioProfilers().getUpdater().register(myLegends);
    getStudioProfilers().getUpdater().register(myThreadsStates);
    getStudioProfilers().getUpdater().register(myCaptureElapsedTimeUpdatable);

    getStudioProfilers().getIdeServices().getCodeNavigator().addListener(this);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getClass());

    getStudioProfilers().addDependency(this).onChange(ProfilerAspect.DEVICES, myProfilerModel::updateProfilingConfigurations);
  }

  @Override
  public void exit() {
    myEventMonitor.exit();
    getStudioProfilers().getUpdater().unregister(myCpuUsage);
    getStudioProfilers().getUpdater().unregister(myTraceDurations);
    getStudioProfilers().getUpdater().unregister(myInProgressTraceDuration);
    getStudioProfilers().getUpdater().unregister(myCpuUsageAxis);
    getStudioProfilers().getUpdater().unregister(myThreadCountAxis);
    getStudioProfilers().getUpdater().unregister(myTimeAxisGuide);
    getStudioProfilers().getUpdater().unregister(myLegends);
    getStudioProfilers().getUpdater().unregister(myThreadsStates);
    getStudioProfilers().getUpdater().unregister(myCaptureElapsedTimeUpdatable);

    getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(this);

    getStudioProfilers().removeDependencies(this);

    mySelectionModel.clearListeners();

    myUpdatableManager.releaseAll();
  }

  @NotNull
  public UpdatableManager getUpdatableManager() {
    return myUpdatableManager;
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myAspect;
  }

  public void startCapturing() {
    ProfilingConfiguration config = myProfilerModel.getProfilingConfiguration();
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
    CpuProfiler.CpuProfilingAppStartRequest request = CpuProfiler.CpuProfilingAppStartRequest.newBuilder()
      .setProcessId(getStudioProfilers().getProcessId())
      .setSession(getStudioProfilers().getSession())
      .setMode(config.getMode())
      .setProfilerType(config.getProfilerType())
      .setBufferSizeInMb(config.getProfilingBufferSizeInMb())
      .setSamplingIntervalUs(config.getProfilingSamplingIntervalUs())
      .build();

    setCaptureState(CaptureState.STARTING);
    CompletableFuture.supplyAsync(
      () -> cpuService.startProfilingApp(request), getStudioProfilers().getIdeServices().getPoolExecutor())
      .thenAcceptAsync(response -> this.startCapturingCallback(response, config),
                       getStudioProfilers().getIdeServices().getMainExecutor());

    getStudioProfilers().getIdeServices().getProfilerPreferences().setBoolean(HAS_USED_CPU_CAPTURE, true);
    myInstructionsEaseOutModel.setCurrentPercentage(1);
  }

  private void startCapturingCallback(CpuProfiler.CpuProfilingAppStartResponse response,
                                      ProfilingConfiguration profilingConfiguration) {
    if (response.getStatus().equals(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS)) {
      myProfilerModel.setActiveConfig(profilingConfiguration.getProfilerType(), profilingConfiguration.getMode(),
                                      profilingConfiguration.getProfilingBufferSizeInMb(),
                                      profilingConfiguration.getProfilingSamplingIntervalUs());
      setCaptureState(CaptureState.CAPTURING);
      myCaptureStartTimeNs = currentTimeNs();
      myInProgressTraceSeries.clear();
      myInProgressTraceSeries.add(TimeUnit.NANOSECONDS.toMicros(myCaptureStartTimeNs), new DefaultDurationData(Long.MAX_VALUE));
      // We should jump to live data when start recording.
      getStudioProfilers().getTimeline().setStreaming(true);
    }
    else {
      getLogger().warn("Unable to start tracing: " + response.getStatus());
      getLogger().warn(response.getErrorMessage());
      setCaptureState(CaptureState.IDLE);
    }
  }

  public void stopCapturing() {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
    CpuProfiler.CpuProfilingAppStopRequest request = CpuProfiler.CpuProfilingAppStopRequest.newBuilder()
      .setProcessId(getStudioProfilers().getProcessId())
      .setProfilerType(myProfilerModel.getActiveConfig().getProfilerType())
      .setSession(getStudioProfilers().getSession())
      .build();

    setCaptureState(CaptureState.STOPPING);
    myInProgressTraceSeries.clear();
    CompletableFuture.supplyAsync(
      () -> cpuService.stopProfilingApp(request), getStudioProfilers().getIdeServices().getPoolExecutor())
      .thenAcceptAsync(this::stopCapturingCallback, getStudioProfilers().getIdeServices().getMainExecutor());
  }

  public long getCaptureElapsedTimeUs() {
    return TimeUnit.NANOSECONDS.toMicros(currentTimeNs() - myCaptureStartTimeNs);
  }

  private void stopCapturingCallback(CpuProfiler.CpuProfilingAppStopResponse response) {
    CpuCaptureMetadata captureMetadata = new CpuCaptureMetadata(myProfilerModel.getActiveConfig());
    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS)) {
      getLogger().warn("Unable to stop tracing: " + response.getStatus());
      getLogger().warn(response.getErrorMessage());
      setCaptureState(CaptureState.IDLE);
      captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.STOP_CAPTURING_FAILURE);
      getStudioProfilers().getIdeServices().getFeatureTracker().trackCaptureTrace(captureMetadata);
    }
    else {
      setCaptureState(CaptureState.PARSING);
      ByteString traceBytes = response.getTrace();
      captureMetadata.setTraceFileSizeBytes(traceBytes.size());
      handleCaptureParsing(response.getTraceId(), traceBytes, captureMetadata);
    }
  }

  /**
   * Handles capture parsing after stopping a capture. Basically, this method checks if {@link CpuCaptureParser} has already parsed the
   * capture and delegates the parsing to such class if it hasn't yet. After that, it waits asynchronously for the parsing to happen
   * and sets the capture in the main executor after it's done. This method also takes care of updating the {@link CpuCaptureMetadata}
   * corresponding to the capture after parsing is finished (successfully or not).
   */
  private void handleCaptureParsing(int traceId, ByteString traceBytes, CpuCaptureMetadata captureMetadata) {
    long beforeParsingTime = System.currentTimeMillis();
    CompletableFuture<CpuCapture> capture = myCaptureParser.parse(traceId, traceBytes, myProfilerModel.getActiveConfig().getProfilerType());
    if (capture == null) {
      // Capture parsing was cancelled. Return to IDLE state and don't change the current capture.
      setCaptureState(CaptureState.IDLE);
      captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING);
      getStudioProfilers().getIdeServices().getFeatureTracker().trackCaptureTrace(captureMetadata);
      return;
    }

    Consumer<CpuCapture> parsingCallback = (parsedCapture) -> {
      // Intentionally not firing the aspect because it will be done by setCapture with the new capture value
      myCaptureState = CaptureState.IDLE;
      if (parsedCapture != null) {
        setAndSelectCapture(parsedCapture);
        setCaptureDetails(DEFAULT_CAPTURE_DETAILS);
        saveTraceInfo(traceId, parsedCapture);

        // Update capture metadata
        captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.SUCCESS);
        captureMetadata.setParsingTimeMs(System.currentTimeMillis() - beforeParsingTime);
        captureMetadata.setCaptureDurationMs(TimeUnit.MICROSECONDS.toMillis(parsedCapture.getDuration()));
        captureMetadata.setRecordDurationMs(calculateRecordDurationMs(parsedCapture));
      }
      else {
        captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILURE);
        setCapture(null);
      }
      getStudioProfilers().getIdeServices().getFeatureTracker().trackCaptureTrace(captureMetadata);
    };

    // Parsing is in progress. Handle it asynchronously and set the capture afterwards using the main executor.
    capture.handleAsync((parsedCapture, exception) -> {
      if (parsedCapture == null) {
        assert exception != null;
        getLogger().warn("Unable to parse capture: " + exception.getMessage());
      }
      parsingCallback.accept(parsedCapture);
      return parsedCapture;
    }, getStudioProfilers().getIdeServices().getMainExecutor());
  }

  /**
   * Iterates the threads of the capture to find the node with the minimum start time and the one with the maximum end time.
   * Maximum end - minimum start result in the record duration.
   */
  private static long calculateRecordDurationMs(CpuCapture capture) {
    Range maxDataRange = new Range();
    for (CpuThreadInfo thread : capture.getThreads()) {
      CaptureNode threadMainNode = capture.getCaptureNode(thread.getId());
      assert threadMainNode != null;
      maxDataRange.expand(threadMainNode.getStartGlobal(), threadMainNode.getEndGlobal());
    }
    return TimeUnit.MICROSECONDS.toMillis((long)maxDataRange.getLength());
  }

  private void saveTraceInfo(int traceId, @NotNull CpuCapture capture) {
    long captureFrom = TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMin());
    long captureTo = TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMax());

    List<CpuProfiler.Thread> threads = new ArrayList<>();
    for (CpuThreadInfo thread : capture.getThreads()) {
      threads.add(CpuProfiler.Thread.newBuilder()
                    .setTid(thread.getId())
                    .setName(thread.getName())
                    .build());
    }

    CpuProfiler.TraceInfo traceInfo = CpuProfiler.TraceInfo.newBuilder()
      .setTraceId(traceId)
      .setFromTimestamp(captureFrom)
      .setToTimestamp(captureTo)
      .setProfilerType(myProfilerModel.getActiveConfig().getProfilerType())
      .addAllThreads(threads).build();

    CpuProfiler.SaveTraceInfoRequest request = CpuProfiler.SaveTraceInfoRequest.newBuilder()
      .setSession(getStudioProfilers().getSession())
      .setProcessId(getStudioProfilers().getProcessId())
      .setTraceInfo(traceInfo)
      .build();

    CpuServiceGrpc.CpuServiceBlockingStub service = getStudioProfilers().getClient().getCpuClient();
    service.saveTraceInfo(request);
  }

  /**
   * Communicate with the device to retrieve the profiling state.
   * Update the capture state and the capture start time (if there is a capture in progress) accordingly.
   * This method should be called from the constructor.
   */
  private void updateProfilingState() {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
    CpuProfiler.ProfilingStateRequest request = CpuProfiler.ProfilingStateRequest.newBuilder()
      .setProcessId(getStudioProfilers().getProcessId())
      .setSession(getStudioProfilers().getSession())
      .setTimestamp(currentTimeNs())
      .build();
    // TODO: move this call to a separate thread if we identify it's not fast enough.
    CpuProfiler.ProfilingStateResponse response = cpuService.checkAppProfilingState(request);

    if (response.getBeingProfiled()) {
      // Make sure to consider the elapsed profiling time, obtained from the device, when setting the capture start time
      long elapsedTime = response.getCheckTimestamp() - response.getStartTimestamp();
      myCaptureStartTimeNs = currentTimeNs() - elapsedTime;
      myCaptureState = CaptureState.CAPTURING;
      myInProgressTraceSeries.clear();
      myInProgressTraceSeries.add(TimeUnit.NANOSECONDS.toMicros(myCaptureStartTimeNs), new DefaultDurationData(Long.MAX_VALUE));

      // Sets the properties of myActiveConfig
      CpuProfiler.CpuProfilingAppStartRequest startRequest = response.getStartRequest();
      myProfilerModel.setActiveConfig(startRequest.getProfilerType(), startRequest.getMode(), startRequest.getBufferSizeInMb(),
                                      startRequest.getSamplingIntervalUs());
    }
    else {
      // otherwise, invalidate capture start time
      myCaptureStartTimeNs = INVALID_CAPTURE_START_TIME;
      myCaptureState = CaptureState.IDLE;
    }
  }

  private void selectionChanged() {
    Range range = getStudioProfilers().getTimeline().getSelectionRange();

    List<SeriesData<CpuTraceInfo>> infoList = getTraceDurations().getSeries().getDataSeries().getDataForXRange(range);
    for (SeriesData<CpuTraceInfo> info : infoList) {
      Range captureRange = info.value.getRange();
      if (!captureRange.getIntersection(range).isEmpty()) {
        setCapture(info.value.getTraceId());
        return; // No need to check other captures if one is already selected
      }
    }

    // Didn't find anything, so set it to null.
    setCapture(null);
    myAspect.changed(CpuProfilerAspect.CAPTURE_SELECTION);
  }

  private long currentTimeNs() {
    return TimeUnit.MICROSECONDS.toNanos((long)getStudioProfilers().getTimeline().getDataRange().getMax()) +
           TimeUnit.SECONDS.toNanos(StudioProfilers.TIMELINE_BUFFER);
  }

  void setCapture(@Nullable CpuCapture capture) {
    myCaptureModel.setCapture(capture);
    setProfilerMode(capture == null ? ProfilerMode.NORMAL : ProfilerMode.EXPANDED);
  }

  private void setCapture(int traceId) {
    CompletableFuture<CpuCapture> future = getCaptureFuture(traceId);
    if (future != null) {
      future.handleAsync((capture, exception) -> {
        setCapture(capture);
        return capture;
      }, getStudioProfilers().getIdeServices().getMainExecutor());
    }
  }

  public void setAndSelectCapture(int traceId) {
    CompletableFuture<CpuCapture> future = getCaptureFuture(traceId);
    if (future != null) {
      future.handleAsync((capture, exception) -> {
        setAndSelectCapture(capture);
        return capture;
      }, getStudioProfilers().getIdeServices().getMainExecutor());
    }
  }

  public void setAndSelectCapture(@NotNull CpuCapture capture) {
    // Setting the selection range will cause the timeline to stop.
    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    timeline.getSelectionRange().set(capture.getRange());
    setCapture(capture);
  }

  public int getSelectedThread() {
    return myCaptureModel.getThread();
  }

  public void setSelectedThread(int id) {
    myCaptureModel.setThread(id);
    Range range = getStudioProfilers().getTimeline().getSelectionRange();
    if (range.isEmpty()) {
      mySelectionFailure = true;
      myAspect.changed(CpuProfilerAspect.SELECTED_THREADS);
      setProfilerMode(ProfilerMode.EXPANDED);
    }
  }

  @NotNull
  public List<ClockType> getClockTypes() {
    return ImmutableList.of(ClockType.GLOBAL, ClockType.THREAD);
  }

  @NotNull
  public ClockType getClockType() {
    return myCaptureModel.getClockType();
  }

  public void setClockType(@NotNull ClockType clockType) {
    myCaptureModel.setClockType(clockType);
  }

  /**
   * The current capture of the cpu profiler, if null there is no capture to display otherwise we need to be in
   * a capture viewing mode.
   */
  @Nullable
  public CpuCapture getCapture() {
    return myCaptureModel.getCapture();
  }

  @NotNull
  public CaptureState getCaptureState() {
    return myCaptureState;
  }

  public void setCaptureState(@NotNull CaptureState captureState) {
    myCaptureState = captureState;
    // invalidate the capture start time when setting the capture state
    myCaptureStartTimeNs = INVALID_CAPTURE_START_TIME;
    myAspect.changed(CpuProfilerAspect.CAPTURE_STATE);
  }

  public void setCaptureFilter(@NotNull String filter) {
    myCaptureModel.setFilter(filter);
  }

  @NotNull
  public String getCaptureFilter() {
    return myCaptureModel.getFilter();
  }

  @NotNull
  public Collection<String> getPossibleCaptureFilters() {
    return myCaptureModel.getPossibleFilters();
  }

  public void openProfilingConfigurationsDialog() {
    Consumer<ProfilingConfiguration> dialogCallback = (configuration) -> {
      myAspect.changed(CpuProfilerAspect.PROFILING_CONFIGURATION);
      // If there was a configuration selected when the dialog was closed,
      // make sure to select it in the combobox
      if (configuration != null) {
        setProfilingConfiguration(configuration);
      }
    };
    Profiler.Device selectedDevice = getStudioProfilers().getDevice();
    boolean isDeviceAtLeastO = selectedDevice != null && selectedDevice.getFeatureLevel() >= AndroidVersion.VersionCodes.O;
    getStudioProfilers().getIdeServices().openCpuProfilingConfigurationsDialog(myProfilerModel, isDeviceAtLeastO, dialogCallback);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackOpenProfilingConfigDialog();
  }

  @NotNull
  public ProfilingConfiguration getProfilingConfiguration() {
    return myProfilerModel.getProfilingConfiguration();
  }

  public void setProfilingConfiguration(@NotNull ProfilingConfiguration mode) {
    if (mode == EDIT_CONFIGURATIONS_ENTRY) {
      openProfilingConfigurationsDialog();
    }
    else if (mode != CONFIG_SEPARATOR_ENTRY) {
      myProfilerModel.setProfilingConfiguration(mode);
    }
    myAspect.changed(CpuProfilerAspect.PROFILING_CONFIGURATION);
  }

  @NotNull
  public List<ProfilingConfiguration> getProfilingConfigurations() {
    ArrayList<ProfilingConfiguration> configs = new ArrayList<>();
    configs.add(EDIT_CONFIGURATIONS_ENTRY);

    List<ProfilingConfiguration> customEntries = myProfilerModel.getCustomProfilingConfigurations();
    if (!customEntries.isEmpty()) {
      configs.add(CONFIG_SEPARATOR_ENTRY);
      configs.addAll(customEntries);
    }

    configs.add(CONFIG_SEPARATOR_ENTRY);
    configs.addAll(myProfilerModel.getDefaultProfilingConfigurations());
    return configs;
  }

  @NotNull
  public CpuTraceDataSeries getCpuTraceDataSeries() {
    return myCpuTraceDataSeries;
  }

  @NotNull
  public CpuThreadsModel getThreadStates() {
    return myThreadsStates;
  }

  /**
   * @return completableFuture from {@link CpuCaptureParser}.
   * If {@link CpuCaptureParser} doesn't manage the trace, this method will start parsing it.
   */
  @Nullable
  public CompletableFuture<CpuCapture> getCaptureFuture(int traceId) {
    CompletableFuture<CpuCapture> capture = myCaptureParser.getCapture(traceId);
    if (capture == null) {
      // Parser doesn't have any information regarding the capture. We need to request
      // trace data from CPU service and tell the parser to start parsing it.
      CpuProfiler.GetTraceRequest request = CpuProfiler.GetTraceRequest.newBuilder()
        .setProcessId(getStudioProfilers().getProcessId())
        .setSession(getStudioProfilers().getSession())
        .setTraceId(traceId)
        .build();
      CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
      // TODO: investigate if this call can take too much time as it's blocking.
      CpuProfiler.GetTraceResponse trace = cpuService.getTrace(request);
      if (trace.getStatus() == CpuProfiler.GetTraceResponse.Status.SUCCESS) {
        capture = myCaptureParser.parse(traceId, trace.getData(), trace.getProfilerType());
      }
    }
    return capture;
  }

  public void setCaptureDetails(@Nullable CaptureModel.Details.Type type) {
    myCaptureModel.setDetails(type);
  }

  @Nullable
  public CaptureModel.Details getCaptureDetails() {
    return myCaptureModel.getDetails();
  }

  @Override
  public void onNavigated(@NotNull CodeLocation location) {
    setProfilerMode(ProfilerMode.NORMAL);
  }

  private class CaptureElapsedTimeUpdatable implements Updatable {
    @Override
    public void update(long elapsedNs) {
      if (myCaptureState == CaptureState.CAPTURING) {
        myAspect.changed(CpuProfilerAspect.CAPTURE_ELAPSED_TIME);
      }
    }
  }

  @VisibleForTesting
  class CpuTraceDataSeries implements DataSeries<CpuTraceInfo> {
    @Override
    public List<SeriesData<CpuTraceInfo>> getDataForXRange(Range xRange) {
      long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
      long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());

      CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
      CpuProfiler.GetTraceInfoResponse response = cpuService.getTraceInfo(
        CpuProfiler.GetTraceInfoRequest.newBuilder().
          setProcessId(getStudioProfilers().getProcessId()).
          setSession(getStudioProfilers().getSession()).
          setFromTimestamp(rangeMin).setToTimestamp(rangeMax).build());

      List<SeriesData<CpuTraceInfo>> seriesData = new ArrayList<>();
      for (CpuProfiler.TraceInfo protoTraceInfo : response.getTraceInfoList()) {
        CpuTraceInfo info = new CpuTraceInfo(protoTraceInfo);
        seriesData.add(new SeriesData<>((long)info.getRange().getMin(), info));
      }
      return seriesData;
    }
  }

  public static class CpuStageLegends extends LegendComponentModel {

    @NotNull private final SeriesLegend myCpuLegend;
    @NotNull private final SeriesLegend myOthersLegend;
    @NotNull private final SeriesLegend myThreadsLegend;

    public CpuStageLegends(@NotNull DetailedCpuUsage cpuUsage, @NotNull Range dataRange) {
      super(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
      myCpuLegend = new SeriesLegend(cpuUsage.getCpuSeries(), CPU_USAGE_FORMATTER, dataRange);
      myOthersLegend = new SeriesLegend(cpuUsage.getOtherCpuSeries(), CPU_USAGE_FORMATTER, dataRange);
      myThreadsLegend = new SeriesLegend(cpuUsage.getThreadsCountSeries(), NUM_THREADS_AXIS, dataRange,
                                         Interpolatable.SteppedLineInterpolator);
      add(myCpuLegend);
      add(myOthersLegend);
      add(myThreadsLegend);
    }

    @NotNull
    public SeriesLegend getCpuLegend() {
      return myCpuLegend;
    }

    @NotNull
    public SeriesLegend getOthersLegend() {
      return myOthersLegend;
    }

    @NotNull
    public SeriesLegend getThreadsLegend() {
      return myThreadsLegend;
    }
  }

  public interface Tooltip {
    enum Type {
      USAGE(UsageTooltip::new),
      THREADS(ThreadsTooltip::new);

      @NotNull
      private final Function<CpuProfilerStage, Tooltip> myBuilder;

      Type(@NotNull Function<CpuProfilerStage, Tooltip> builder) {
        myBuilder = builder;
      }

      public Tooltip build(@NotNull CpuProfilerStage stage) {
        return myBuilder.apply(stage);
      }
    }

    @NotNull
    Type getType();

    /**
     * Invoked when the user exits the tooltip.
     */
    default void dispose() {
    }
  }


  public static class ThreadsTooltip extends AspectModel<ThreadsTooltip.Aspect> implements Tooltip {
    public enum Aspect {
      // The hovering thread state changed
      THREAD_STATE,
    }

    @NotNull private final CpuProfilerStage myStage;

    @Nullable private String myThreadName;
    @Nullable private ThreadStateDataSeries mySeries;
    @Nullable private CpuProfilerStage.ThreadState myThreadState;

    ThreadsTooltip(@NotNull CpuProfilerStage stage) {
      myStage = stage;
      Range tooltipRange = stage.getStudioProfilers().getTimeline().getTooltipRange();
      tooltipRange.addDependency(this).onChange(Range.Aspect.RANGE, this::updateThreadState);
    }

    private void updateThreadState() {
      myThreadState = null;
      if (mySeries == null) {
        changed(Aspect.THREAD_STATE);
        return;
      }

      Range tooltipRange = myStage.getStudioProfilers().getTimeline().getTooltipRange();
      // We could get data for [tooltipRange.getMin() - buffer, tooltipRange.getMin() - buffer],
      // However it is tricky to come up with the buffer duration, a thread state can be longer than any buffer.
      // So, lets get data what the user sees and extract the hovered state.
      List<SeriesData<ThreadState>> series = mySeries.getDataForXRange(myStage.getStudioProfilers().getTimeline().getViewRange());

      for (int i = 0; i < series.size(); ++i) {
        if (i + 1 == series.size() || tooltipRange.getMin() < series.get(i + 1).x) {
          myThreadState = series.get(i).value;
          break;
        }
      }
      changed(Aspect.THREAD_STATE);
    }

    @NotNull
    @Override
    public Type getType() {
      return Type.THREADS;
    }

    void setThread(@Nullable String threadName, @Nullable ThreadStateDataSeries stateSeries) {
      myThreadName = threadName;
      mySeries = stateSeries;
      updateThreadState();
    }

    @Nullable
    public String getThreadName() {
      return myThreadName;
    }

    @Nullable
    ThreadState getThreadState() {
      return myThreadState;
    }
  }

  public static class UsageTooltip implements Tooltip {
    @NotNull private final CpuProfilerStage myStage;
    @NotNull private final CpuStageLegends myLegends;

    UsageTooltip(@NotNull CpuProfilerStage stage) {
      myStage = stage;
      myLegends = new CpuStageLegends(stage.getCpuUsage(), stage.getStudioProfilers().getTimeline().getTooltipRange());
      myStage.getStudioProfilers().getUpdater().register(myLegends);
    }

    @NotNull
    @Override
    public Type getType() {
      return Type.USAGE;
    }

    @Override
    public void dispose() {
      myStage.getStudioProfilers().getUpdater().unregister(myLegends);
    }

    @NotNull
    public CpuStageLegends getLegends() {
      return myLegends;
    }
  }
}
