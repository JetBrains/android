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

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.*;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.analytics.FilterMetadata;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.cpu.capturedetails.CaptureModel;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CpuProfilerStage extends Stage implements CodeNavigator.Listener {
  private static final String HAS_USED_CPU_CAPTURE = "cpu.used.capture";

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 5, 10, "%");
  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(1, 5, 1, "");

  // Clamp the property value between 5 Seconds and 5 Minutes, otherwise the user could specify arbitrarily small or large value.
  public static final int CPU_ART_STOP_TIMEOUT_SEC = Math.max(5, Math.min(Integer.getInteger("profiler.cpu.art.stop.timeout.sec", 5),
                                                                    5 * 60));
  /**
   * Percentage of space on either side of an imported trace.
   */
  static final double IMPORTED_TRACE_VIEW_EXPAND_PERCENTAGE = 0.1;

  /**
   * Default capture details to be set after stopping a capture.
   * {@link CaptureDetails.Type#CALL_CHART} is used by default because it's useful and fast to compute.
   */
  private static final CaptureDetails.Type DEFAULT_CAPTURE_DETAILS = CaptureDetails.Type.CALL_CHART;

  private final CpuThreadsModel myThreadsStates;
  private final CpuKernelModel myCpuKernelModel;
  private final ClampedAxisComponentModel myCpuUsageAxis;
  private final ClampedAxisComponentModel myThreadCountAxis;
  private final ResizingAxisComponentModel myTimeAxisGuide;
  private final DetailedCpuUsage myCpuUsage;
  private final CpuStageLegends myLegends;
  private final DurationDataModel<CpuTraceInfo> myTraceDurations;
  private final EventMonitor myEventMonitor;
  private final SelectionModel mySelectionModel;
  private final EaseOutModel myInstructionsEaseOutModel;
  private final CpuProfilerConfigModel myProfilerConfigModel;
  private final CpuFramesModel myFramesModel;

  private final DurationDataModel<CpuTraceInfo> myRecentTraceDurations;

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

  private TraceInitiationType myInProgressTraceInitiationType;

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
    // The two values below are used by imported trace captures to indicate which
    // slices of the thread contain method trace activity and which ones don't.
    HAS_ACTIVITY,
    NO_ACTIVITY,
    // These values are captured from Atrace as such we only have a captured state.
    RUNNABLE_CAPTURED,
    WAITING_IO_CAPTURED,
    UNKNOWN
  }

  public enum CaptureState {
    // Waiting for a capture to start (displaying the current capture or not)
    IDLE,
    // There is a capture in progress
    CAPTURING,
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
   */
  @NotNull
  private CaptureState myCaptureState;

  /**
   * If there is a capture in progress, stores its start time.
   */
  private long myCaptureStartTimeNs;

  private CaptureElapsedTimeUpdatable myCaptureElapsedTimeUpdatable;

  private final CpuCaptureStateUpdatable myCaptureStateUpdatable;

  @NotNull
  private final UpdatableManager myUpdatableManager;

  /**
   * Responsible for parsing trace files into {@link CpuCapture}.
   * Parsed captures should be obtained from this object.
   */
  private final CpuCaptureParser myCaptureParser;

  /**
   * Used to navigate across {@link CpuCapture}. The iterator navigates through trace IDs of captures generated in the current session.
   * It's responsibility of the stage to notify to populate the iterator initially with the trace IDs already created before the stage
   * creation, and notifying the iterator about newly parsed captures.
   */
  @NotNull
  private final TraceIdsIterator myTraceIdsIterator;

  /**
   * Whether the stage was initiated in Import Trace mode. In this mode, some data might be missing (e.g. thread states and CPU usage in
   * ART and simpleperf captures), the {@link ProfilerTimeline} is static and just big enough to display a {@link CpuCapture} entirely.
   * Import Trace mode is triggered when importing a CPU trace.
   */
  private final boolean myIsImportTraceMode;

  /**
   * The imported trace file, it is only used when the stage was initiated in Import Trace mode, otherwise null.
   */
  @Nullable
  private File myImportedTrace;

  /**
   * Keep track of the {@link Common.Session} that contains this stage, otherwise tasks that happen in background (e.g. parsing a trace) can
   * refer to a different session later if the user changes the session selection in the UI.
   */
  private Common.Session mySession;

  public CpuProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, null);
  }

  public CpuProfilerStage(@NotNull StudioProfilers profilers, @Nullable File importedTrace) {
    this(profilers, importedTrace, new CpuCaptureParser(profilers.getIdeServices()));
  }

  @VisibleForTesting
  CpuProfilerStage(@NotNull StudioProfilers profilers, @Nullable File importedTrace, @NotNull CpuCaptureParser captureParser) {
    super(profilers);
    myImportedTrace = importedTrace;
    mySession = profilers.getSession();
    // Only allow import trace mode if Import CPU trace and sessions flag are enabled.
    myIsImportTraceMode = getStudioProfilers().getIdeServices().getFeatureConfig().isImportCpuTraceEnabled() && importedTrace != null;

    myCpuTraceDataSeries = new CpuTraceDataSeries();
    myProfilerConfigModel = new CpuProfilerConfigModel(profilers, this);

    Range viewRange = getStudioProfilers().getTimeline().getViewRange();
    Range dataRange = getStudioProfilers().getTimeline().getDataRange();
    Range selectionRange = getStudioProfilers().getTimeline().getSelectionRange();

    myCpuUsage = new DetailedCpuUsage(profilers);

    myCpuUsageAxis = new ClampedAxisComponentModel.Builder(myCpuUsage.getCpuRange(), CPU_USAGE_FORMATTER).build();
    myThreadCountAxis = new ClampedAxisComponentModel.Builder(myCpuUsage.getThreadRange(), NUM_THREADS_AXIS).build();
    myTimeAxisGuide =
      new ResizingAxisComponentModel.Builder(viewRange, TimeAxisFormatter.DEFAULT_WITHOUT_MINOR_TICKS).setGlobalRange(dataRange).build();

    myLegends = new CpuStageLegends(myCpuUsage, dataRange);

    // Create an event representing the traces within the view range.
    myTraceDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, getCpuTraceDataSeries()));

    myThreadsStates = new CpuThreadsModel(viewRange, this, mySession);
    myCpuKernelModel = new CpuKernelModel(viewRange, this);
    myFramesModel = new CpuFramesModel(viewRange, this);

    myInProgressTraceSeries = new DefaultDataSeries<>();
    myInProgressTraceDuration = new DurationDataModel<>(new RangedSeries<>(viewRange, myInProgressTraceSeries));
    myInProgressTraceInitiationType = TraceInitiationType.UNSPECIFIED_INITIATION;

    myEventMonitor = new EventMonitor(profilers);

    mySelectionModel = buildSelectionModel(selectionRange);

    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);

    myCaptureState = CaptureState.IDLE;
    myCaptureElapsedTimeUpdatable = new CaptureElapsedTimeUpdatable();
    myCaptureStateUpdatable = new CpuCaptureStateUpdatable(() -> updateProfilingState(true));

    myCaptureModel = new CaptureModel(this);
    myUpdatableManager = new UpdatableManager(getStudioProfilers().getUpdater());
    myCaptureParser = captureParser;
    // Populate the iterator with all TraceInfo existing in the current session.
    myTraceIdsIterator = new TraceIdsIterator(this, getTraceInfoFromRange(new Range(-Double.MAX_VALUE, Double.MAX_VALUE)));

    // Create an event representing recently completed traces appearing in the unexplored data range.
    myRecentTraceDurations =
      new DurationDataModel<>(new RangedSeries<>(new Range(-Double.MAX_VALUE, Double.MAX_VALUE), getCpuTraceDataSeries()));
    myRecentTraceDurations.addDependency(this).onChange(DurationDataModel.Aspect.DURATION_DATA, () -> {
      Range xRange = myRecentTraceDurations.getSeries().getXRange();

      CpuTraceInfo candidateToSelect = null;  // candidate trace to automatically set and select
      List<SeriesData<CpuTraceInfo>> recentTraceInfo =
        myRecentTraceDurations.getSeries().getDataSeries().getDataForXRange(xRange);
      for (SeriesData<CpuTraceInfo> series : recentTraceInfo) {
        CpuTraceInfo trace = series.value;
        if (trace.getInitiationType().equals(TraceInitiationType.INITIATED_BY_API)) {
          if (!myTraceIdsIterator.contains(trace.getTraceId())) {
            myTraceIdsIterator.addTrace(trace.getTraceId());
            if (candidateToSelect == null || trace.getRange().getMax() > candidateToSelect.getRange().getMax()) {
              candidateToSelect = trace;
            }
            // Track usage for API-initiated tracing.
            // TODO(b/72832167): When more tracing APIs are supported, update the tracking logic.
            getStudioProfilers().getIdeServices().getFeatureTracker()
                                .trackCpuApiTracing(false, !trace.getTraceFilePath().isEmpty(), -1, -1, -1);
          }
        }
        // Update xRange's min to the latest end point we have seen. When we query next time, we want new traces only; not all traces.
        if (trace.getRange().getMax() > xRange.getMin()) {
          xRange.setMin(trace.getRange().getMax());
        }
      }
      if (candidateToSelect != null) {
        setAndSelectCapture(candidateToSelect.getTraceId());
      }
    });
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfilerStage.class);
  }

  /**
   * Creates and returns a {@link SelectionModel} given a {@link Range} representing the selection.
   */
  private SelectionModel buildSelectionModel(Range selectionRange) {
    SelectionModel selectionModel = new SelectionModel(selectionRange);
    selectionModel.addConstraint(myTraceDurations);
    if (myIsImportTraceMode) {
      selectionModel.addListener(new SelectionListener() {
        @Override
        public void selectionCreated() {
          getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectRange();
        }

        @Override
        public void selectionCleared() {
          if (myCaptureModel.getCapture() != null) {
            // when we switch from a session into another session of import trace mode, we first create a new stage, and then the selection
            // on timeline is cleared which would trigger this method in the new stage, but at that point myCaptureModel.getCapture()
            // isn't set yet. That's why we need a null check.
            setAndSelectCapture(myCaptureModel.getCapture());
          }
        }

        @Override
        public void selectionCreationFailure() {
          if (myCaptureModel.getCapture() != null) {
            setAndSelectCapture(myCaptureModel.getCapture());
          }
        }
      });
    }
    else {
      selectionModel.addListener(new SelectionListener() {
        @Override
        public void selectionCreated() {
          getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectRange();
          selectionChanged();
        }

        @Override
        public void selectionCleared() {
          selectionChanged();
        }

        @Override
        public void selectionCreationFailure() {
          selectionChanged();
        }
      });
    }
    return selectionModel;
  }

  public boolean isImportTraceMode() {
    return myIsImportTraceMode;
  }

  public boolean hasUserUsedCpuCapture() {
    return getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().getBoolean(HAS_USED_CPU_CAPTURE, false);
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

  @NotNull
  public CpuProfilerConfigModel getProfilerConfigModel() {
    return myProfilerConfigModel;
  }

  @Override
  public void enter() {
    myEventMonitor.enter();
    getStudioProfilers().getUpdater().register(myCpuUsage);
    getStudioProfilers().getUpdater().register(myInProgressTraceDuration);
    getStudioProfilers().getUpdater().register(myTraceDurations);
    getStudioProfilers().getUpdater().register(myRecentTraceDurations);
    getStudioProfilers().getUpdater().register(myCpuUsageAxis);
    getStudioProfilers().getUpdater().register(myThreadCountAxis);
    getStudioProfilers().getUpdater().register(myCaptureElapsedTimeUpdatable);

    if (getStudioProfilers().getIdeServices().getFeatureConfig().isCpuApiTracingEnabled()) {
      // TODO (b/75259594): We need to fix this issue before enabling the flag.
      getStudioProfilers().getUpdater().register(myCaptureStateUpdatable);
    }

    CodeNavigator navigator = getStudioProfilers().getIdeServices().getCodeNavigator();
    navigator.addListener(this);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getClass());

    getStudioProfilers().addDependency(this).onChange(ProfilerAspect.PROCESSES, myProfilerConfigModel::updateProfilingConfigurations);

    // This actions are here instead of in the constructor, because only after this method the UI (i.e {@link CpuProfilerStageView}
    // will be visible to the user. As well as, the feature tracking will link the correct stage to the events that happened
    // during this actions.
    updateProfilingState(false);
    myProfilerConfigModel.updateProfilingConfigurations();
    if (myIsImportTraceMode) {
      assert myImportedTrace != null;
      // When in import trace mode, immediately import the trace from the given file and set the resulting capture.
      parseAndSelectImportedTrace(myImportedTrace);
      // Set the profiler mode to EXPANDED to make sure that L3 panel is shown.
      setProfilerMode(ProfilerMode.EXPANDED);
    }

    if (getStudioProfilers().getIdeServices().getFeatureConfig().isCpuNewRecordingWorkflowEnabled()) {
      // In the new recording workflow it is always expanded mode.
      setProfilerMode(ProfilerMode.EXPANDED);
    }
  }

  @Override
  public void exit() {
    myEventMonitor.exit();
    getStudioProfilers().getUpdater().unregister(myCpuUsage);
    getStudioProfilers().getUpdater().unregister(myTraceDurations);
    getStudioProfilers().getUpdater().unregister(myRecentTraceDurations);
    getStudioProfilers().getUpdater().unregister(myInProgressTraceDuration);
    getStudioProfilers().getUpdater().unregister(myCpuUsageAxis);
    getStudioProfilers().getUpdater().unregister(myThreadCountAxis);
    getStudioProfilers().getUpdater().unregister(myCaptureElapsedTimeUpdatable);

    if (getStudioProfilers().getIdeServices().getFeatureConfig().isCpuApiTracingEnabled()) {
      // TODO (b/75259594): We need to fix this issue before enabling the flag.
      getStudioProfilers().getUpdater().unregister(myCaptureStateUpdatable);
    }

    getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(this);
    getStudioProfilers().removeDependencies(this);

    // Asks the parser to interrupt any parsing in progress.
    myCaptureParser.abortParsing();
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

  public void toggleCapturing() {
    if (myCaptureState == CpuProfilerStage.CaptureState.CAPTURING) {
      stopCapturing();
    }
    else {
      startCapturing();
    }
  }

  public void startCapturing() {
    ProfilingConfiguration config = myProfilerConfigModel.getProfilingConfiguration();
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getCpuClient();
    assert getStudioProfilers().getProcess() != null;
    CpuProfilingAppStartRequest request = CpuProfilingAppStartRequest.newBuilder()
                                                                     .setSession(mySession)
                                                                     .setConfiguration(config.toProto())
                                                                     .setAbiCpuArch(getStudioProfilers().getProcess().getAbiCpuArch())
                                                                     .build();

    // Set myInProgressTraceInitiationType before calling setCaptureState() because the latter may fire an
    // aspect that depends on the former.
    myInProgressTraceInitiationType = TraceInitiationType.INITIATED_BY_UI;

    // Disable memory live allocation if config setting has the option set.
    if (config.isDisableLiveAllocation()) {
      getStudioProfilers().setMemoryLiveAllocationEnabled(false);
    }

    setCaptureState(CaptureState.STARTING);
    CompletableFuture.supplyAsync(
      () -> cpuService.startProfilingApp(request), getStudioProfilers().getIdeServices().getPoolExecutor())
                     .thenAcceptAsync(response -> this.startCapturingCallback(response, config),
                                      getStudioProfilers().getIdeServices().getMainExecutor());

    getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_CPU_CAPTURE, true);
    myInstructionsEaseOutModel.setCurrentPercentage(1);
  }

  private void startCapturingCallback(CpuProfilingAppStartResponse response,
                                      ProfilingConfiguration profilingConfiguration) {
    if (response.getStatus().equals(CpuProfilingAppStartResponse.Status.SUCCESS)) {
      myProfilerConfigModel.setProfilingConfiguration(profilingConfiguration);
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
      getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.CAPTURE_START_FAILURE);
      // Return to IDLE state and set the current capture to null
      setCaptureState(CaptureState.IDLE);
      setCapture(null);
    }
  }

  public void stopCapturing() {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getCpuClient();
    CpuProfilingAppStopRequest request = CpuProfilingAppStopRequest.newBuilder()
                                                                   .setProfilerType(
                                                                     myProfilerConfigModel.getProfilingConfiguration().getProfilerType())
                                                                   .setProfilerMode(
                                                                     myProfilerConfigModel.getProfilingConfiguration().getMode()
                                                                   )
                                                                   .setSession(mySession)
                                                                   .build();
    // Setting duration of the in progress trace series, so it's temporarily displayed in the chart while the trace is being parsed.
    myInProgressTraceSeries.clear();
    long captureStartTimeUs = TimeUnit.NANOSECONDS.toMicros(myCaptureStartTimeNs);
    long currentTimeUs = TimeUnit.NANOSECONDS.toMicros(currentTimeNs());
    myInProgressTraceSeries.add(captureStartTimeUs, new DefaultDurationData(currentTimeUs - captureStartTimeUs));

    setCaptureState(CaptureState.STOPPING);
    CompletableFuture.supplyAsync(
      () -> cpuService.stopProfilingApp(request), getStudioProfilers().getIdeServices().getPoolExecutor())
                     .thenAcceptAsync(this::stopCapturingCallback, getStudioProfilers().getIdeServices().getMainExecutor());
  }

  public long getCaptureElapsedTimeUs() {
    return TimeUnit.NANOSECONDS.toMicros(currentTimeNs() - myCaptureStartTimeNs);
  }

  /**
   * Returns the list of {@link TraceInfo} that intersect with the given range.
   */
  private List<TraceInfo> getTraceInfoFromRange(Range rangeUs) {
    // Converts the range to nanoseconds before calling the service.
    long rangeMinNs = TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin());
    long rangeMaxNs = TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax());
    GetTraceInfoResponse response = getCpuClient().getTraceInfo(
      GetTraceInfoRequest.newBuilder().
        setSession(mySession).
                           setFromTimestamp(rangeMinNs).setToTimestamp(rangeMaxNs).build());
    return response.getTraceInfoList();
  }

  @NotNull
  public TraceIdsIterator getTraceIdsIterator() {
    return myTraceIdsIterator;
  }

  /**
   * Sets and selects the next capture. No-op if there is none.
   */
  void navigateNext() {
    handleCaptureNavigation(myTraceIdsIterator.next());
  }

  /**
   * Sets and selects the previous capture. No-op if there is none.
   */
  void navigatePrevious() {
    handleCaptureNavigation(myTraceIdsIterator.previous());
  }

  private void handleCaptureNavigation(long traceId) {
    // Sanity check to see if myTraceIdsIterator returned a valid trace. Return early otherwise.
    if (traceId == TraceIdsIterator.INVALID_TRACE_ID) {
      return;
    }
    // Select the next capture if a valid trace was returned.
    setAndSelectCapture(traceId);
  }

  private void stopCapturingCallback(CpuProfilingAppStopResponse response) {
    CpuCaptureMetadata captureMetadata = new CpuCaptureMetadata(myProfilerConfigModel.getProfilingConfiguration());
    long estimateDurationMs = TimeUnit.NANOSECONDS.toMillis(currentTimeNs() - myCaptureStartTimeNs);
    // Set the estimate duration of the capture, i.e. the time difference between device time when user clicked start and stop.
    // If the capture is successful, it will be overridden by a more accurate time, calculated from the capture itself.
    captureMetadata.setCaptureDurationMs(estimateDurationMs);
    if (!response.getStatus().equals(CpuProfilingAppStopResponse.Status.SUCCESS)) {
      getLogger().warn("Unable to stop tracing: " + response.getStatus());
      getLogger().warn(response.getErrorMessage());
      getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.CAPTURE_STOP_FAILURE);
      // Return to IDLE state and set the current capture to null
      setCaptureState(CaptureState.IDLE);
      setCapture(null);
      captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.fromStopStatus(response.getStatus()));
      getStudioProfilers().getIdeServices().getFeatureTracker().trackCaptureTrace(captureMetadata);
    }
    else {
      // Capture was successful, pre-process the trace and parse it afterwards.
      CompletableFuture
        .supplyAsync(() -> preProcessTrace(response.getTrace()), getStudioProfilers().getIdeServices().getPoolExecutor())
        .thenAcceptAsync((bytes) -> {
          captureMetadata.setTraceFileSizeBytes(bytes.size());
          boolean failedToPreProcess = bytes.equals(TracePreProcessor.FAILURE);
          if (failedToPreProcess) {
            captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.PREPROCESS_FAILURE);
            getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.PREPROCESS_FAILURE);
            getStudioProfilers().getIdeServices().getFeatureTracker().trackCaptureTrace(captureMetadata);
            getLogger().warn("Unable to pre-process trace file.");
            // Return to IDLE state and set the current capture to null. Parsing should not happen.
            setCaptureState(CaptureState.IDLE);
            setCapture(null);
            return;
          }

          handleCaptureParsing(response.getTraceId(), bytes, captureMetadata);
        }, getStudioProfilers().getIdeServices().getMainExecutor());
    }

    // Re-enable memory live allocation.
    if (myProfilerConfigModel.getProfilingConfiguration().isDisableLiveAllocation()) {
      getStudioProfilers().setMemoryLiveAllocationEnabled(true);
    }
  }

  private ByteString preProcessTrace(ByteString trace) {
    // For simpleperf captures, if the flag is enabled, we need to pre-process the raw traces obtained from the device to the format that
    // can be parsed by SimpleperfTraceParser.
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isSimpleperfHostEnabled()
        && myProfilerConfigModel.getProfilingConfiguration().getProfilerType() == CpuProfilerType.SIMPLEPERF) {
      return getStudioProfilers().getIdeServices().getSimpleperfTracePreProcessor().preProcessTrace(trace);
    }
    return trace;
  }

  @NotNull
  public CpuCaptureParser getCaptureParser() {
    return myCaptureParser;
  }

  /**
   * Parses a trace {@link File} and set the resulting {@link CpuCapture} as the current capture. If parsing fails, warn the user through an
   * error balloon.
   */
  private void parseAndSelectImportedTrace(@NotNull File traceFile) {
    assert myIsImportTraceMode;
    // We tell CaptureParser to update its parsing state before sending out the parse instruction, so users can see a UI feedback promptly.
    myCaptureParser.updateParsingStateWhenStarting();
    CompletableFuture<CpuCapture> capture = myCaptureParser.parse(traceFile);
    if (capture == null) {
      // User aborted the capture, or the model received an invalid file (e.g. from tests) canceled. Log and return early.
      getLogger().info("Imported trace file was not parsed.");
      return;
    }
    // TODO (b/79244375): extract callback to its own method
    Consumer<CpuCapture> parsingCallback = (parsedCapture) -> {
      if (parsedCapture != null) {
        ProfilerTimeline timeline = getStudioProfilers().getTimeline();
        Range captureRangeNs = new Range(TimeUnit.MICROSECONDS.toNanos((long)parsedCapture.getRange().getMin()),
                                         TimeUnit.MICROSECONDS.toNanos((long)parsedCapture.getRange().getMax()));
        // Give some room to the end of the timeline, so we can properly use the handle to select the capture.
        double expandAmountNs = IMPORTED_TRACE_VIEW_EXPAND_PERCENTAGE * captureRangeNs.getLength();
        // Reset expects time in Ns and will convert to Us internally
        timeline.reset((long)(captureRangeNs.getMin()), (long)(captureRangeNs.getMax() + expandAmountNs));
        timeline.setIsPaused(true);

        // We must set build the thread model before setting the capture, otherwise we won't be able to properly set the thread after
        // CpuCaptureModel#setCapture updates the thread id.
        myThreadsStates.buildImportedTraceThreads(parsedCapture);
        setCaptureState(CaptureState.IDLE);
        setAndSelectCapture(parsedCapture);
        // We need to expand the end of the data range. Giving us the padding on the right side to show the view. If we don't do this
        // and only expand the view we end up with the trace aligned to the right.
        // [] = view range
        // - = data range
        // [    ---------]
        // This is not the intended result as we want equal spacing on both sides of the
        // capture. However in the current model we need to expand the data range.
        double expandAmountUs = TimeUnit.NANOSECONDS.toMicros((long)expandAmountNs);
        timeline.getViewRange().set(parsedCapture.getRange().getMin() - expandAmountUs,
                                    parsedCapture.getRange().getMax() + expandAmountUs);
        setCaptureDetails(DEFAULT_CAPTURE_DETAILS);
        // Save trace info if not already saved
        if (!myTraceIdsIterator.contains(CpuCaptureParser.IMPORTED_TRACE_ID)) {
          saveTraceInfo(CpuCaptureParser.IMPORTED_TRACE_ID, parsedCapture,
                        // We don't know the CpuProfilerMode used to generate the trace.
                        CpuProfilerMode.UNSPECIFIED_MODE);
          myTraceIdsIterator.addTrace(CpuCaptureParser.IMPORTED_TRACE_ID);
        }
        // Track import trace success
        getStudioProfilers().getIdeServices().getFeatureTracker().trackImportTrace(parsedCapture.getType(), true);
      }
      else if (capture.isCancelled()) {
        getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.IMPORT_TRACE_PARSING_ABORTED);
      }
      else {
        getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.IMPORT_TRACE_PARSING_FAILURE);
        // After notifying the listeners that the parser has failed, we set the status to IDLE.
        setCaptureState(CaptureState.IDLE);
        // Track import trace failure
        // TODO (b/78557952): try to get the profiler type from the trace, which should be possible as long as it has a valid header.
        getStudioProfilers().getIdeServices().getFeatureTracker().trackImportTrace(CpuProfilerType.UNSPECIFIED_PROFILER, false);
      }
    };

    // Parsing is in progress. Handle it asynchronously and set the capture afterwards using the main executor.
    capture.handleAsync((parsedCapture, exception) -> {
      parsingCallback.accept(parsedCapture);
      return parsedCapture;
    }, getStudioProfilers().getIdeServices().getMainExecutor());
  }

  /**
   * Handles capture parsing after stopping a capture. Basically, this method checks if {@link CpuCaptureParser} has already parsed the
   * capture and delegates the parsing to such class if it hasn't yet. After that, it waits asynchronously for the parsing to happen
   * and sets the capture in the main executor after it's done. This method also takes care of updating the {@link CpuCaptureMetadata}
   * corresponding to the capture after parsing is finished (successfully or not).
   */
  private void handleCaptureParsing(long traceId, ByteString traceBytes, CpuCaptureMetadata captureMetadata) {
    long beforeParsingTime = System.currentTimeMillis();
    myCaptureParser.updateParsingStateWhenStarting();
    CompletableFuture<CpuCapture> capture =
      myCaptureParser.parse(mySession, traceId, traceBytes, myProfilerConfigModel.getProfilingConfiguration().getProfilerType());
    if (capture == null) {
      // Capture parsing was cancelled. Return to IDLE state and don't change the current capture.
      setCaptureState(CaptureState.IDLE);
      captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING);
      getStudioProfilers().getIdeServices().getFeatureTracker().trackCaptureTrace(captureMetadata);
      return;
    }

    // TODO (b/79244375): extract callback to its own method
    Consumer<CpuCapture> parsingCallback = (parsedCapture) -> {
      myInProgressTraceSeries.clear();
      if (parsedCapture != null) {
        setCaptureState(CaptureState.IDLE);
        setAndSelectCapture(parsedCapture);
        setCaptureDetails(DEFAULT_CAPTURE_DETAILS);
        saveTraceInfo(traceId, parsedCapture, myProfilerConfigModel.getProfilingConfiguration().getMode());

        // Update capture metadata
        captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.SUCCESS);
        captureMetadata.setParsingTimeMs(System.currentTimeMillis() - beforeParsingTime);
        captureMetadata.setCaptureDurationMs(TimeUnit.MICROSECONDS.toMillis(parsedCapture.getDurationUs()));
        captureMetadata.setRecordDurationMs(calculateRecordDurationMs(parsedCapture));
      }
      else if (capture.isCancelled()) {
        getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.PARSING_ABORTED);
      }
      else {
        captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILURE);
        getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.PARSING_FAILURE);
        // After notifying the listeners that the parser has failed, we set the status to IDLE.
        setCaptureState(CaptureState.IDLE);
        setCapture(null);
      }
      getStudioProfilers().getIdeServices().getFeatureTracker().trackCaptureTrace(captureMetadata);
    };

    // Parsing is in progress. Handle it asynchronously and set the capture afterwards using the main executor.
    capture.handleAsync((parsedCapture, exception) -> {
      if (parsedCapture == null) {
        assert exception != null;
        getLogger().warn("Unable to parse capture: " + exception.getMessage(), exception);
      }
      parsingCallback.accept(parsedCapture);
      // If capture is correctly parsed, notify the iterator.
      myTraceIdsIterator.addTrace(traceId);
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

  private void saveTraceInfo(long traceId, @NotNull CpuCapture capture, CpuProfilerMode mode) {
    long captureFrom = TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMin());
    long captureTo = TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMax());

    List<CpuProfiler.Thread> threads = new ArrayList<>();
    for (CpuThreadInfo thread : capture.getThreads()) {
      threads.add(CpuProfiler.Thread.newBuilder()
                                    .setTid(thread.getId())
                                    .setName(thread.getName())
                                    .build());
    }

    TraceInfo traceInfo = TraceInfo.newBuilder()
                                   .setTraceId(traceId)
                                   .setFromTimestamp(captureFrom)
                                   .setToTimestamp(captureTo)
                                   .setProfilerType(capture.getType())
                                   .setProfilerMode(mode)
                                   .setTraceFilePath(Strings.nullToEmpty(myCaptureParser.getTraceFilePath(traceId)))
                                   .addAllThreads(threads).build();

    SaveTraceInfoRequest request = SaveTraceInfoRequest.newBuilder()
                                                       .setSession(mySession)
                                                       .setTraceInfo(traceInfo)
                                                       .build();
    getCpuClient().saveTraceInfo(request);
  }

  /**
   * Requests the current profiling state of the device and returns the resulting {@link ProfilingStateResponse}.
   */
  private ProfilingStateResponse checkProfilingState() {
    ProfilingStateRequest request = ProfilingStateRequest.newBuilder().setSession(mySession).build();
    return getCpuClient().checkAppProfilingState(request);
  }

  @NotNull
  private CpuServiceGrpc.CpuServiceBlockingStub getCpuClient() {
    return getStudioProfilers().getClient().getCpuClient();
  }

  /**
   * Communicate with the device to retrieve the profiling state.
   * Update the capture state and the capture start time (if there is a capture in progress) accordingly.
   * This method puts the stage in the correct mode when being called from the constructor; it's also called by
   * {@link CpuCaptureStateUpdatable} to respond to API tracing status.
   *
   * @param calledFromUpdatable Whether the method was called from {@link #myCaptureStateUpdatable}. When this is true, we should only
   *                            handle state changes if the trace was initiated from API.
   */
  @VisibleForTesting
  void updateProfilingState(boolean calledFromUpdatable) {
    ProfilingStateResponse response = checkProfilingState();

    if (response.getBeingProfiled()) {
      if (response.getInitiationType() != TraceInitiationType.INITIATED_BY_API && calledFromUpdatable) {
        // If this method was called from the CaptureStateUpdatable, we shouldn't continue if the current trace was not triggered from API.
        return;
      }

      ProfilingConfiguration configuration = ProfilingConfiguration.fromProto(response.getConfiguration());
      // Update capture state only if it was idle to avoid disrupting state that's invisible to device such as STOPPING.
      if (myCaptureState == CaptureState.IDLE) {
        if (response.getInitiationType() == TraceInitiationType.INITIATED_BY_STARTUP) {
          getStudioProfilers().getIdeServices().getFeatureTracker().trackCpuStartupProfiling(configuration);
        }

        // Set myInProgressTraceInitiationType before calling setCaptureState() because the latter may fire an
        // aspect that depends on the former.
        myInProgressTraceInitiationType = response.getInitiationType();
        setCaptureState(CaptureState.CAPTURING);
        myCaptureStartTimeNs = response.getStartTimestamp();
        myInProgressTraceSeries.clear();
        myInProgressTraceSeries.add(TimeUnit.NANOSECONDS.toMicros(myCaptureStartTimeNs), new DefaultDurationData(Long.MAX_VALUE));
        // We should jump to live data when there is an ongoing recording.
        getStudioProfilers().getTimeline().setStreaming(true);

        if (myInProgressTraceInitiationType == TraceInitiationType.INITIATED_BY_API) {
          // For API-initiated tracing, we want to update the config combo box to show API_INITIATED_TRACING_PROFILING_CONFIG.
          // Don't update the myProfilerConfigModel. First, this config is by definition transitory. Passing the reference outside
          // CpuProfilerStage may indicate a longer life span. Second, it is not a real configuration. For example, each
          // configuration's name should be unique, but all API-initiated captures should show the the same text even if they
          // are different such as in sample interval.
          myAspect.changed(CpuProfilerAspect.PROFILING_CONFIGURATION);
        }
        else {
          // Updates myProfilerConfigModel to the ongoing profiler configuration.
          myProfilerConfigModel.setProfilingConfiguration(configuration);
        }
      }
    }
    else {
      // Update capture state only if it was capturing an API initiated tracing
      // to avoid disrupting state that's invisible to device such as PARSING.
      if (isApiInitiatedTracingInProgress()) {
        setCaptureState(CaptureState.IDLE);
        myInProgressTraceSeries.clear();
        // When API-initiated tracing ends, we want to update the config combo box back to the entry before API tracing.
        // This is done by fire aspect PROFILING_CONFIGURATION. Don't fire the aspect while the user is using the combo box
        // because otherwise the dropdown menu would be flashing.
        myAspect.changed(CpuProfilerAspect.PROFILING_CONFIGURATION);
      }
    }
  }

  private void selectionChanged() {
    CpuTraceInfo intersectingTraceInfo = getIntersectingTraceInfo(getStudioProfilers().getTimeline().getSelectionRange());
    if (intersectingTraceInfo == null) {
      // Didn't find anything, so set the capture to null.
      setCapture(null);
    }
    else {
      // Otherwise, set the capture to the trace found
      setCapture(intersectingTraceInfo.getTraceId());
    }
  }

  /**
   * Returns the trace ID of a capture whose range overlaps with a given range. If multiple captures overlap with it,
   * the first trace ID found is returned.
   */
  @Nullable
  CpuTraceInfo getIntersectingTraceInfo(Range range) {
    List<SeriesData<CpuTraceInfo>> infoList = getTraceDurations().getSeries().getDataSeries().getDataForXRange(range);
    for (SeriesData<CpuTraceInfo> info : infoList) {
      Range captureRange = info.value.getRange();
      if (!captureRange.getIntersection(range).isEmpty()) {
        return info.value;
      }
    }
    return null;
  }

  private long currentTimeNs() {
    return TimeUnit.MICROSECONDS.toNanos((long)getStudioProfilers().getTimeline().getDataRange().getMax());
  }

  @VisibleForTesting
  public void setCapture(@Nullable CpuCapture capture) {
    if (!myCaptureModel.setCapture(capture)) {
      return;
    }

    // If there's a capture, expand the profiler UI. Otherwise keep it the same.
    if (capture != null) {
      setProfilerMode(ProfilerMode.EXPANDED);
      onCaptureSelection();
    }
  }

  private void onCaptureSelection() {
    CpuCapture capture = getCapture();
    if (capture == null) {
      return;
    }
    if ((getCaptureState() == CpuProfilerStage.CaptureState.IDLE)
        || (getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING)) {
      // Capture has finished parsing.
      ensureCaptureInViewRange();
      if (capture.getType() == CpuProfilerType.ATRACE) {
        if (!isImportTraceMode() && ((AtraceCpuCapture)capture).isMissingData()) {
          getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.ATRACE_BUFFER_OVERFLOW);
        }
      }
    }
  }

  /**
   * Makes sure the selected capture fits entirely in user's view range.
   */
  private void ensureCaptureInViewRange() {
    CpuCapture capture = getCapture();
    assert capture != null;

    // Give a padding to the capture. 5% of the view range on each side.
    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    double padding = timeline.getViewRange().getLength() * 0.05;
    // Now makes sure the capture range + padding is within view range and in the middle if possible.
    timeline.adjustRangeCloseToMiddleView(new Range(capture.getRange().getMin() - padding, capture.getRange().getMax() + padding));
  }

  private void setCapture(long traceId) {
    CompletableFuture<CpuCapture> future = getCaptureFuture(traceId);
    if (future != null) {
      future.handleAsync((capture, exception) -> {
        setCaptureState(checkProfilingState().getBeingProfiled() ? CaptureState.CAPTURING : CaptureState.IDLE);
        setCapture(capture);
        return capture;
      }, getStudioProfilers().getIdeServices().getMainExecutor());
    }
  }

  public void setAndSelectCapture(long traceId) {
    CompletableFuture<CpuCapture> future = getCaptureFuture(traceId);
    if (future != null) {
      future.handleAsync((capture, exception) -> {
        setCaptureState(checkProfilingState().getBeingProfiled() ? CaptureState.CAPTURING : CaptureState.IDLE);
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

  @NotNull
  public TraceInitiationType getCaptureInitiationType() {
    return myInProgressTraceInitiationType;
  }

  public void setCaptureState(@NotNull CaptureState captureState) {
    if (!myCaptureState.equals(captureState)) {
      myCaptureState = captureState;
      myAspect.changed(CpuProfilerAspect.CAPTURE_STATE);

      if (captureState == CaptureState.CAPTURING) {
        // When going to CAPTURING state, we should make sure the profiler mode is set to EXPANDED, so we show the Recording panel in L3.
        setProfilerMode(ProfilerMode.EXPANDED);
      }
    }
  }

  public void setCaptureFilter(@NotNull Filter filter) {
    myCaptureModel.setFilter(filter);
    trackFilterUsage(filter);
  }

  @NotNull
  public Filter getCaptureFilter() {
    return myCaptureModel.getFilter();
  }

  public int getCaptureFilterNodeCount() {
    return myCaptureModel.getFilterNodeCount();
  }

  private void trackFilterUsage(@NotNull Filter filter) {
    CaptureDetails details = getCaptureDetails();
    if (details == null) {
      // Not likely, but can happen if you modify a filter and then clear the selection. Filters
      // fire delayed events, so we might get a "filter changed" notification with no details set.
      return;
    }

    FilterMetadata filterMetadata = new FilterMetadata();
    FeatureTracker featureTracker = getStudioProfilers().getIdeServices().getFeatureTracker();
    switch (details.getType()) {
      case TOP_DOWN:
        filterMetadata.setView(FilterMetadata.View.CPU_TOP_DOWN);
        break;
      case BOTTOM_UP:
        filterMetadata.setView(FilterMetadata.View.CPU_BOTTOM_UP);
        break;
      case CALL_CHART:
        filterMetadata.setView(FilterMetadata.View.CPU_CALL_CHART);
        break;
      case FLAME_CHART:
        filterMetadata.setView(FilterMetadata.View.CPU_FLAME_CHART);
        break;
    }
    filterMetadata.setFeaturesUsed(filter.isMatchCase(), filter.isRegex());
    filterMetadata.setMatchedElementCount(myCaptureModel.getFilterNodeCount());
    filterMetadata.setTotalElementCount(myCaptureModel.getNodeCount());
    filterMetadata.setFilterTextLength(filter.isEmpty() ? 0 : filter.getFilterString().length());
    featureTracker.trackFilterMetadata(filterMetadata);
  }

  public boolean isApiInitiatedTracingInProgress() {
    return myCaptureState == CaptureState.CAPTURING && getCaptureInitiationType().equals(TraceInitiationType.INITIATED_BY_API);
  }

  @NotNull
  public CpuTraceDataSeries getCpuTraceDataSeries() {
    return myCpuTraceDataSeries;
  }

  @NotNull
  public CpuThreadsModel getThreadStates() {
    return myThreadsStates;
  }

  @NotNull
  public CpuKernelModel getCpuKernelModel() {
    return myCpuKernelModel;
  }

  @NotNull
  public CpuFramesModel getFramesModel() {
    return myFramesModel;
  }

  /**
   * @return completableFuture from {@link CpuCaptureParser}.
   * If {@link CpuCaptureParser} doesn't manage the trace, this method will start parsing it.
   */
  @VisibleForTesting
  @Nullable
  CompletableFuture<CpuCapture> getCaptureFuture(long traceId) {
    CompletableFuture<CpuCapture> capture = myCaptureParser.getCapture(traceId);
    if (capture == null) {
      // Parser doesn't have any information regarding the capture. We need to request trace data from CPU service and tell the parser
      // to start parsing it. Tell the parser to update its parsing state.
      myCaptureParser.updateParsingStateWhenStarting();
      // Then, set the profiler mode to EXPANDED, as we're going to L3.
      setProfilerMode(ProfilerMode.EXPANDED);
      GetTraceRequest request = GetTraceRequest.newBuilder().setSession(mySession).setTraceId(traceId).build();
      // TODO: investigate if this call can take too much time as it's blocking.
      GetTraceResponse trace = getCpuClient().getTrace(request);
      if (trace.getStatus() == GetTraceResponse.Status.SUCCESS) {
        capture = myCaptureParser.parse(mySession, traceId, trace.getData(), trace.getProfilerType());
      }
    }
    return capture;
  }

  public void setCaptureDetails(@Nullable CaptureDetails.Type type) {
    myCaptureModel.setDetails(type);
  }

  @Nullable
  public CaptureDetails getCaptureDetails() {
    return myCaptureModel.getDetails();
  }

  @Override
  public void onNavigated(@NotNull CodeLocation location) {
    setProfilerMode(ProfilerMode.NORMAL);
  }

  private class CaptureElapsedTimeUpdatable implements Updatable {
    @Override
    public void update(long elapsedNs) {
      if (myCaptureState == CaptureState.CAPTURING || myCaptureParser.isParsing()) {
        myAspect.changed(CpuProfilerAspect.CAPTURE_ELAPSED_TIME);
      }
    }
  }

  @VisibleForTesting
  static class CpuCaptureStateUpdatable implements Updatable {
    @NotNull private final Runnable myCallback;

    /**
     * Number of update() runs before the callback is called.
     * <p>
     * Updater is running 60 times per second, which is too frequent for checking capture state which
     * requires a RPC call. Therefore, we check the state less often.
     */
    @VisibleForTesting
    static final int UPDATE_COUNT_TO_CALL_CALLBACK = 6;
    private int myUpdateCount = 0;

    public CpuCaptureStateUpdatable(@NotNull Runnable callback) {
      myCallback = callback;
    }

    @Override
    public void update(long elapsedNs) {
      if (myUpdateCount++ >= UPDATE_COUNT_TO_CALL_CALLBACK) {
        myCallback.run();         // call callback
        myUpdateCount = 0;        // reset update count
      }
    }
  }

  @VisibleForTesting
  class CpuTraceDataSeries implements DataSeries<CpuTraceInfo> {
    @Override
    public List<SeriesData<CpuTraceInfo>> getDataForXRange(Range xRange) {
      List<TraceInfo> traceInfo = getTraceInfoFromRange(xRange);

      List<SeriesData<CpuTraceInfo>> seriesData = new ArrayList<>();
      for (TraceInfo protoTraceInfo : traceInfo) {
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
      super(dataRange);
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
}
