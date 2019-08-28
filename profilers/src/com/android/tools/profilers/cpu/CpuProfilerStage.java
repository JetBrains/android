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

import static com.android.tools.profilers.StudioProfilers.DAEMON_DEVICE_DIR_PATH;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.EaseOutModel;
import com.android.tools.adtui.model.Interpolatable;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionListener;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
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
import com.android.tools.idea.transport.poller.TransportEventListener;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Cpu.CpuTraceMode;
import com.android.tools.profiler.proto.Cpu.CpuTraceType;
import com.android.tools.profiler.proto.Cpu.TraceInitiationType;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopRequest;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.analytics.FilterMetadata;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.cpu.capturedetails.CaptureModel;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CpuProfilerStage extends Stage implements CodeNavigator.Listener {
  private static final String HAS_USED_CPU_CAPTURE = "cpu.used.capture";

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 5, 10, "%");
  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(1, 5, 1, "");

  // Clamp the property value between 5 Seconds and 5 Minutes, otherwise the user could specify arbitrarily small or large value.
  public static final int CPU_ART_STOP_TIMEOUT_SEC = Math.max(5, Math.min(Integer.getInteger("profiler.cpu.art.stop.timeout.sec", 10),
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
  private final RangeSelectionModel myRangeSelectionModel;
  private final EaseOutModel myInstructionsEaseOutModel;
  private final CpuProfilerConfigModel myProfilerConfigModel;
  private final CpuFramesModel myFramesModel;

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

  private final InProgressTraceHandler myInProgressTraceHandler;
  @NotNull private Cpu.CpuTraceInfo myInProgressTraceInfo = Cpu.CpuTraceInfo.getDefaultInstance();

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
   * The trace info associated with the imported trace info. This is only generated and used in import mode.
   */
  @Nullable
  private Cpu.CpuTraceInfo myImportedTraceInfo;

  /**
   * Keep track of the {@link Common.Session} that contains this stage, otherwise tasks that happen in background (e.g. parsing a trace) can
   * refer to a different session later if the user changes the session selection in the UI.
   */
  private Common.Session mySession;

  /**
   * Mapping trace ids to completed CpuTraceInfo's.
   */
  private final Map<Long, CpuTraceInfo> myCompletedTraceIdToInfoMap = new HashMap<>();

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

    myThreadsStates = new CpuThreadsModel(viewRange, profilers, mySession, myIsImportTraceMode);
    myCpuKernelModel = new CpuKernelModel(viewRange, this);
    myFramesModel = new CpuFramesModel(viewRange, this);

    myEventMonitor = new EventMonitor(profilers);

    myRangeSelectionModel = buildRangeSelectionModel(selectionRange);

    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);

    myCaptureState = CaptureState.IDLE;

    myCaptureModel = new CaptureModel(this);
    myUpdatableManager = new UpdatableManager(getStudioProfilers().getUpdater());
    myCaptureParser = captureParser;

    List<Cpu.CpuTraceInfo> existingCompletedTraceInfoList =
      CpuProfiler.getTraceInfoFromSession(getStudioProfilers().getClient(), mySession,
                                          getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()).stream()
        .filter(info -> info.getToTimestamp() != -1).collect(Collectors.toList());
    existingCompletedTraceInfoList.forEach(info -> myCompletedTraceIdToInfoMap.put(info.getTraceId(), new CpuTraceInfo(info)));
    // Populate the iterator with all TraceInfo existing in the current session.
    myTraceIdsIterator = new TraceIdsIterator(this, existingCompletedTraceInfoList);
    myInProgressTraceHandler = new InProgressTraceHandler();
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfilerStage.class);
  }

  /**
   * Creates and returns a {@link RangeSelectionModel} given a {@link Range} representing the selection.
   */
  private RangeSelectionModel buildRangeSelectionModel(Range selectionRange) {
    RangeSelectionModel rangeSelectionModel = new RangeSelectionModel(selectionRange);
    rangeSelectionModel.addConstraint(myTraceDurations);
    if (myIsImportTraceMode) {
      rangeSelectionModel.addListener(new RangeSelectionListener() {
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
      rangeSelectionModel.addListener(new RangeSelectionListener() {
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
    return rangeSelectionModel;
  }

  public boolean isImportTraceMode() {
    return myIsImportTraceMode;
  }

  public boolean hasUserUsedCpuCapture() {
    return getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().getBoolean(HAS_USED_CPU_CAPTURE, false);
  }

  @NotNull
  public RangeSelectionModel getRangeSelectionModel() {
    return myRangeSelectionModel;
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
    getStudioProfilers().getUpdater().register(myTraceDurations);
    getStudioProfilers().getUpdater().register(myInProgressTraceHandler);
    getStudioProfilers().getUpdater().register(myCpuUsageAxis);
    getStudioProfilers().getUpdater().register(myThreadCountAxis);

    CodeNavigator navigator = getStudioProfilers().getIdeServices().getCodeNavigator();
    navigator.addListener(this);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getClass());

    getStudioProfilers().addDependency(this).onChange(ProfilerAspect.PROCESSES, myProfilerConfigModel::updateProfilingConfigurations);

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
    getStudioProfilers().getUpdater().unregister(myInProgressTraceHandler);
    getStudioProfilers().getUpdater().unregister(myCpuUsageAxis);
    getStudioProfilers().getUpdater().unregister(myThreadCountAxis);

    getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(this);
    getStudioProfilers().removeDependencies(this);

    // Asks the parser to interrupt any parsing in progress.
    myCaptureParser.abortParsing();
    myRangeSelectionModel.clearListeners();
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
    assert getStudioProfilers().getProcess() != null;
    Common.Process process = getStudioProfilers().getProcess();
    String traceFilePath = String.format("%s/%s-%d.trace", DAEMON_DEVICE_DIR_PATH, process.getName(), System.nanoTime());

    // Disable memory live allocation if config setting has the option set.
    if (config.isDisableLiveAllocation()) {
      getStudioProfilers().setMemoryLiveAllocationEnabled(false);
    }
    setCaptureState(CaptureState.STARTING);
    Cpu.CpuTraceConfiguration configuration = Cpu.CpuTraceConfiguration.newBuilder()
      .setAppName(process.getName())
      .setAbiCpuArch(process.getAbiCpuArch())
      .setInitiationType(TraceInitiationType.INITIATED_BY_UI)
      .setTempPath(traceFilePath) // TODO b/133321803 switch back to having daemon generates and provides the path.
      .setUserOptions(config.toProto())
      .addAllSymbolDirs(getStudioProfilers().getIdeServices().getNativeSymbolsDirectories())
      .build();

    if (getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      Commands.Command startCommand = Commands.Command.newBuilder()
        .setStreamId(mySession.getStreamId())
        .setPid(mySession.getPid())
        .setType(Commands.Command.CommandType.START_CPU_TRACE)
        .setStartCpuTrace(Cpu.StartCpuTrace.newBuilder().setConfiguration(configuration).build())
        .build();

      Transport.ExecuteResponse response = getStudioProfilers().getClient().getTransportClient().execute(
        Transport.ExecuteRequest.newBuilder().setCommand(startCommand).build());
      TransportEventListener statusListener = new TransportEventListener(Common.Event.Kind.CPU_TRACE_STATUS,
                                                                         getStudioProfilers().getIdeServices().getMainExecutor(),
                                                                         event -> event.getCommandId() == response.getCommandId(),
                                                                         () -> mySession.getStreamId(),
                                                                         () -> mySession.getPid(),
                                                                         event -> {
                                                                           startCapturingCallback(
                                                                             event.getCpuTraceStatus().getTraceStartStatus());
                                                                           // unregisters the listener.
                                                                           return true;
                                                                         });
      getStudioProfilers().getTransportPoller().registerListener(statusListener);
    }
    else {
      CpuProfilingAppStartRequest request = CpuProfilingAppStartRequest.newBuilder()
        .setSession(mySession)
        .setConfiguration(configuration)
        .build();
      CompletableFuture.supplyAsync(
        () -> getCpuClient().startProfilingApp(request), getStudioProfilers().getIdeServices().getPoolExecutor())
        .thenAcceptAsync(response -> this.startCapturingCallback(response.getStatus()),
                         getStudioProfilers().getIdeServices().getMainExecutor());
    }

    getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_CPU_CAPTURE, true);
    myInstructionsEaseOutModel.setCurrentPercentage(1);
  }

  private void startCapturingCallback(@NotNull Cpu.TraceStartStatus status) {
    if (status.getStatus().equals(Cpu.TraceStartStatus.Status.SUCCESS)) {
      setCaptureState(CaptureState.CAPTURING);
      myCaptureStartTimeNs = currentTimeNs();
      // We should jump to live data when start recording.
      getStudioProfilers().getTimeline().setStreaming(true);
    }
    else {
      getLogger().warn("Unable to start tracing: " + status.getStatus());
      getLogger().warn(status.getErrorMessage());
      getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.CAPTURE_START_FAILURE);
      // Return to IDLE state and set the current capture to null
      setCaptureState(CaptureState.IDLE);
      setCapture(null);
    }
  }

  @VisibleForTesting
  void stopCapturing() {
    // We need to send the trace configuration that was used to initiated the capture. Return early if no in-progress trace exists.
    if (Cpu.CpuTraceInfo.getDefaultInstance().equals(myInProgressTraceInfo)) {
      return;
    }

    setCaptureState(CaptureState.STOPPING);
    CpuProfiler.stopTracing(getStudioProfilers(), mySession, myInProgressTraceInfo.getConfiguration(), this::stopCapturingCallback);
  }

  public long getCaptureElapsedTimeUs() {
    return TimeUnit.NANOSECONDS.toMicros(currentTimeNs() - myCaptureStartTimeNs);
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

  private void stopCapturingCallback(@NotNull Cpu.TraceStopStatus status) {
    // Successful traces are tracked via the InProgressTraceHandler.
    if (!status.getStatus().equals(Cpu.TraceStopStatus.Status.SUCCESS)) {
      CpuCaptureMetadata captureMetadata = new CpuCaptureMetadata(myProfilerConfigModel.getProfilingConfiguration());
      long estimateDurationMs = TimeUnit.NANOSECONDS.toMillis(currentTimeNs() - myCaptureStartTimeNs);
      // Set the estimate duration of the capture, i.e. the time difference between device time when user clicked start and stop.
      // If the capture is successful, we can track a more accurate time, calculated from the capture itself.
      captureMetadata.setCaptureDurationMs(estimateDurationMs);
      captureMetadata.setStoppingTimeMs((int)TimeUnit.NANOSECONDS.toMillis(status.getStoppingTimeNs()));
      captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.fromStopStatus(status.getStatus()));
      getStudioProfilers().getIdeServices().getFeatureTracker().trackCaptureTrace(captureMetadata);

      getLogger().warn("Unable to stop tracing: " + status.getStatus());
      getLogger().warn(status.getErrorMessage());
      getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.CAPTURE_STOP_FAILURE);
      // Return to IDLE state and set the current capture to null
      setCaptureState(CaptureState.IDLE);
      setCapture(null);
    }

    // Re-enable memory live allocation.
    if (myProfilerConfigModel.getProfilingConfiguration().isDisableLiveAllocation()) {
      getStudioProfilers().setMemoryLiveAllocationEnabled(true);
    }
  }

  private void goToCaptureStage(long traceId) {
    // TODO (b/138677869): The technology should come from the trace / parser not the selected configuration.
    // If the user changes the dropdown and clicks on a trace in the sessions panel the technology name
    // will be wrong. While this behavior is a bug, it maps to what exists today as such will fix in
    // a follow up.
    CpuCaptureStage stage = CpuCaptureStage
      .create(getStudioProfilers(), ProfilingTechnology.fromConfig(getProfilerConfigModel().getProfilingConfiguration()).getName(),
              traceId);
    if (stage != null) {
      getStudioProfilers().getIdeServices().getMainExecutor()
        .execute(() -> getStudioProfilers().setStage(stage));
    }
    else {
      getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.IMPORT_TRACE_PARSING_FAILURE);
    }
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

        myImportedTraceInfo = Cpu.CpuTraceInfo.newBuilder()
          .setTraceId(CpuCaptureParser.IMPORTED_TRACE_ID)
          .setFromTimestamp((long)captureRangeNs.getMin())
          .setToTimestamp((long)captureRangeNs.getMax())
          .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                              .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                                .setTraceType(parsedCapture.getType())))
          .build();

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
        getStudioProfilers().getIdeServices().getFeatureTracker().trackImportTrace(CpuTraceType.UNSPECIFIED_TYPE, false);
      }
    };

    // Parsing is in progress. Handle it asynchronously and set the capture afterwards using the main executor.
    capture.handleAsync((parsedCapture, exception) -> {
      parsingCallback.accept(parsedCapture);
      return parsedCapture;
    }, getStudioProfilers().getIdeServices().getMainExecutor());
  }

  @NotNull
  private CpuServiceGrpc.CpuServiceBlockingStub getCpuClient() {
    return getStudioProfilers().getClient().getCpuClient();
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
    List<SeriesData<CpuTraceInfo>> infoList = getTraceDurations().getSeries().getSeriesForRange(range);
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
      if (capture.getType() == CpuTraceType.ATRACE) {
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
        setCaptureState(myInProgressTraceInfo.equals(Cpu.CpuTraceInfo.getDefaultInstance()) ? CaptureState.IDLE : CaptureState.CAPTURING);
        setCapture(capture);
        return capture;
      }, getStudioProfilers().getIdeServices().getMainExecutor());
    }
  }

  public void setAndSelectCapture(long traceId) {
    // This workflow is called when a capture is clicked in the UI.
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isCpuCaptureStageEnabled()) {
      // TODO (b/132268755): Request / Get Trace file if cached for CpuCaptureStage. Consider if we need to change how this is triggered.
      // Not implemented due to not yet parsing / loading traces in the CpuCaptureStage.
      goToCaptureStage(traceId);
    }
    else {
      CompletableFuture<CpuCapture> future = getCaptureFuture(traceId);
      if (future == null) {
        setCaptureState(myInProgressTraceInfo.equals(Cpu.CpuTraceInfo.getDefaultInstance()) ? CaptureState.IDLE : CaptureState.CAPTURING);
      }
      else {
        future.handleAsync((capture, exception) -> {
          setCaptureState(myInProgressTraceInfo.equals(Cpu.CpuTraceInfo.getDefaultInstance()) ? CaptureState.IDLE : CaptureState.CAPTURING);
          if (capture != null) {
            setAndSelectCapture(capture);
            setCaptureDetails(DEFAULT_CAPTURE_DETAILS);
          }
          return capture;
        }, getStudioProfilers().getIdeServices().getMainExecutor());
      }
    }
  }

  @VisibleForTesting
  public void setAndSelectCapture(@NotNull CpuCapture capture) {
    // We must build the thread model before setting the capture, otherwise we won't be able to properly select the thread after
    // CpuCaptureModel#setCapture updates the thread id.
    myThreadsStates.updateTraceThreadsForCapture(capture);

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
    return myInProgressTraceInfo.getConfiguration().getInitiationType();
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

  @NotNull
  public CaptureModel getCaptureModel() { return myCaptureModel; }

  /**
   * @return completableFuture from {@link CpuCaptureParser}.
   * If {@link CpuCaptureParser} doesn't manaCpge the trace, this method will start parsing it.
   */
  @VisibleForTesting
  @Nullable
  CompletableFuture<CpuCapture> getCaptureFuture(long traceId) {
    CompletableFuture<CpuCapture> capture = myCaptureParser.getCapture(traceId);
    if (capture == null) {
      // Then, set the profiler mode to EXPANDED, as we're going to L3.
      setProfilerMode(ProfilerMode.EXPANDED);

      // TODO: investigate if this call can take too much time as it's blocking. Should/can this byte fetch happen async?
      Transport.BytesRequest traceRequest = Transport.BytesRequest.newBuilder()
        .setStreamId(mySession.getStreamId())
        .setId(String.valueOf(traceId))
        .build();
      Transport.BytesResponse traceResponse = getStudioProfilers().getClient().getTransportClient().getBytes(traceRequest);
      if (!traceResponse.getContents().isEmpty()) {
        capture =
          myCaptureParser.parse(mySession, traceId, traceResponse.getContents(), myCompletedTraceIdToInfoMap.get(traceId).getTraceType());
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

  /**
   * Handles the starting and completion of the latest ongoing trace.
   */
  private class InProgressTraceHandler implements Updatable {
    @Override
    public void update(long elapsedNs) {
      // If we are parsing a trace we also trigger the aspect to update the UI.
      if (getCaptureParser().isParsing()) {
        myAspect.changed(CpuProfilerAspect.CAPTURE_ELAPSED_TIME);
        return;
      }
      Cpu.CpuTraceInfo finishedTraceToSelect = null;
      // Request for the entire data range as we don't expect too many (100s) traces withing a single session.
      Range dataRange = getStudioProfilers().getTimeline().getDataRange();
      List<Cpu.CpuTraceInfo> traceInfoList =
        CpuProfiler.getTraceInfoFromRange(getStudioProfilers().getClient(), mySession, dataRange,
                                          getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled());
      for (int i = 0; i < traceInfoList.size(); i++) {
        Cpu.CpuTraceInfo trace = traceInfoList.get(i);
        if (trace.getToTimestamp() == -1) {
          // an ongoing trace should be the most recent trace
          assert i == traceInfoList.size() - 1;
          // Do not select a trace if there is one in-progress.
          finishedTraceToSelect = null;
          handleInProgressTrace(trace);
          break;
        }

        if (!myTraceIdsIterator.contains(trace.getTraceId())) {
          myTraceIdsIterator.addTrace(trace.getTraceId());
          myCompletedTraceIdToInfoMap.put(trace.getTraceId(), new CpuTraceInfo(trace));
          // queried trace info list should be sorted by time so we can always assume the latest one should be selected.
          finishedTraceToSelect = trace;

          if (trace.getConfiguration().getInitiationType().equals(TraceInitiationType.INITIATED_BY_API)) {
            // Handcraft the metadata, since that is not generated by the profilers UI.
            CpuCaptureMetadata metadata =
              new CpuCaptureMetadata(new ProfilingConfiguration("API tracing", CpuTraceType.ART, CpuTraceMode.INSTRUMENTED));
            myCaptureParser.trackCaptureMetadata(trace.getTraceId(), metadata);

            // Track usage for API-initiated tracing.
            // TODO(b/72832167): When more tracing APIs are supported, update the tracking logic.
            // TODO correctly determine whether trace path was provided.
            getStudioProfilers().getIdeServices().getFeatureTracker().trackCpuApiTracing(false, true, -1, -1, -1);
          }

          // Inform CpuCaptureParser to track metrics when the successful trace is parsed.
          if (trace.getStopStatus().getStatus().equals(Cpu.TraceStopStatus.Status.SUCCESS)) {
            CpuCaptureMetadata captureMetadata =
              new CpuCaptureMetadata(ProfilingConfiguration.fromProto(finishedTraceToSelect.getConfiguration().getUserOptions()));
            // If the capture is successful, we can track a more accurate time, calculated from the capture itself.
            captureMetadata.setCaptureDurationMs(TimeUnit.NANOSECONDS.toMillis(trace.getToTimestamp() - trace.getFromTimestamp()));
            captureMetadata.setStoppingTimeMs((int)TimeUnit.NANOSECONDS.toMillis(trace.getStopStatus().getStoppingTimeNs()));
            myCaptureParser.trackCaptureMetadata(trace.getTraceId(), captureMetadata);
          }
        }
      }

      if (finishedTraceToSelect != null) {
        myInProgressTraceInfo = Cpu.CpuTraceInfo.getDefaultInstance();
        if (finishedTraceToSelect.getStopStatus().getStatus() == Cpu.TraceStopStatus.Status.SUCCESS) {
          setAndSelectCapture(finishedTraceToSelect.getTraceId());
        }
        else {
          setCaptureState(CaptureState.IDLE);
        }

        // When API-initiated tracing ends, we want to update the config combo box back to the entry before API tracing.
        // This is done by fire aspect PROFILING_CONFIGURATION.
        myAspect.changed(CpuProfilerAspect.PROFILING_CONFIGURATION);
      }
    }

    /**
     * When an in-progress trace is first detected, we ensure that:
     * 1. The capture state is switched to capturing
     * 2. The timeline is streaming
     * 3. The profile configuration is synchronized with the trace info.
     */
    private void handleInProgressTrace(@NotNull Cpu.CpuTraceInfo traceInfo) {
      if (traceInfo.equals(myInProgressTraceInfo)) {
        myAspect.changed(CpuProfilerAspect.CAPTURE_ELAPSED_TIME);
        return;
      }

      myInProgressTraceInfo = traceInfo;
      myCaptureStartTimeNs = myInProgressTraceInfo.getFromTimestamp();
      setCaptureState(CaptureState.CAPTURING);
      getStudioProfilers().getTimeline().setStreaming(true);

      if (myInProgressTraceInfo.getConfiguration().getInitiationType() == TraceInitiationType.INITIATED_BY_API) {
        // For API-initiated tracing, we want to update the config combo box to show API_INITIATED_TRACING_PROFILING_CONFIG.
        // Don't update the myProfilerConfigModel. First, this config is by definition transitory. Passing the reference outside
        // CpuProfilerStage may indicate a longer life span. Second, it is not a real configuration. For example, each
        // configuration's name should be unique, but all API-initiated captures should show the the same text even if they
        // are different such as in sample interval.
        myAspect.changed(CpuProfilerAspect.PROFILING_CONFIGURATION);
      }
      else {
        // Updates myProfilerConfigModel to the ongoing profiler configuration.
        myProfilerConfigModel.setProfilingConfiguration(ProfilingConfiguration.fromProto(traceInfo.getConfiguration().getUserOptions()));
      }
    }
  }

  @VisibleForTesting
  class CpuTraceDataSeries implements DataSeries<CpuTraceInfo> {
    @Override
    public List<SeriesData<CpuTraceInfo>> getDataForRange(Range range) {
      List<Cpu.CpuTraceInfo> traceInfos;
      if (myIsImportTraceMode) {
        // The imported trace info may not be immediately available.
        traceInfos = myImportedTraceInfo == null ? Collections.emptyList() : Collections.singletonList(myImportedTraceInfo);
      }
      else {
        traceInfos = CpuProfiler.getTraceInfoFromRange(getStudioProfilers().getClient(), mySession, range,
                                                       getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled());
      }
      List<SeriesData<CpuTraceInfo>> seriesData = new ArrayList<>();
      for (Cpu.CpuTraceInfo protoTraceInfo : traceInfos) {
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
