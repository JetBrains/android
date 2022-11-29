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
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.idea.transport.TransportFileManager;
import com.android.tools.idea.transport.poller.TransportEventListener;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Trace.TraceInitiationType;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.RecordingOption;
import com.android.tools.profilers.RecordingOptionsModel;
import com.android.tools.profilers.StreamingStage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration;
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.AdditionalOptions;
import com.android.tools.profilers.event.EventMonitor;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CpuProfilerStage extends StreamingStage {
  private static final String HAS_USED_CPU_CAPTURE = "cpu.used.capture";

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 5, 10, "%");
  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(1, 5, 1, "");

  // Clamp the property value between 5 Seconds and 5 Minutes, otherwise the user could specify arbitrarily small or large value.
  // Default timeout value is 2 mintues (120 seconds).
  public static final int CPU_ART_STOP_TIMEOUT_SEC = Math.max(5, Math.min(Integer.getInteger("profiler.cpu.art.stop.timeout.sec", 120),
                                                                          5 * 60));

  /**
   * A fake configuration shown when an API-initiated tracing is in progress. It exists for UX purpose only and isn't something
   * we want to preserve across stages. Therefore, it exists inside {@link CpuProfilerStage}.
   */
  @VisibleForTesting static final ProfilingConfiguration API_INITIATED_TRACING_PROFILING_CONFIG =
    new ArtInstrumentedConfiguration("API tracing");

  private final CpuThreadsModel myThreadsStates;
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
  private final RecordingOptionsModel myRecordingOptionsModel;

  /**
   * Represents the current state of the capture.
   */
  @NotNull
  private CaptureState myCaptureState;

  /**
   * If there is a capture in progress, stores its start time.
   */
  private long myCaptureStartTimeNs;

  /**
   * If there is a catpure being stopped, stores the time when the stop is initiated.
   */
  private long myCaptureStopTimeNs;

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
   * Keep track of the {@link Common.Session} that contains this stage, otherwise tasks that happen in background (e.g. parsing a trace) can
   * refer to a different session later if the user changes the session selection in the UI.
   */
  @NotNull
  private final Common.Session mySession;

  /**
   * Mapping trace ids to completed CpuTraceInfo's.
   */
  private final Map<Long, CpuTraceInfo> myCompletedTraceIdToInfoMap = new HashMap<>();

  public CpuProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CpuCaptureParser(profilers.getIdeServices()));
  }

  @VisibleForTesting
  CpuProfilerStage(@NotNull StudioProfilers profilers, @NotNull CpuCaptureParser captureParser) {
    super(profilers);
    mySession = profilers.getSession();

    myCpuTraceDataSeries = new CpuTraceDataSeries();
    myProfilerConfigModel = new CpuProfilerConfigModel(profilers, this);
    myRecordingOptionsModel = new RecordingOptionsModel();

    Range viewRange = getTimeline().getViewRange();
    Range dataRange = getTimeline().getDataRange();
    Range selectionRange = getTimeline().getSelectionRange();

    myCpuUsage = new DetailedCpuUsage(profilers);

    myCpuUsageAxis = new ClampedAxisComponentModel.Builder(myCpuUsage.getCpuRange(), CPU_USAGE_FORMATTER).build();
    myThreadCountAxis = new ClampedAxisComponentModel.Builder(myCpuUsage.getThreadRange(), NUM_THREADS_AXIS).build();
    myTimeAxisGuide =
      new ResizingAxisComponentModel.Builder(viewRange, TimeAxisFormatter.DEFAULT_WITHOUT_MINOR_TICKS).setGlobalRange(dataRange).build();

    myLegends = new CpuStageLegends(myCpuUsage, dataRange);

    // Create an event representing the traces within the view range.
    myTraceDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, getCpuTraceDataSeries()));

    myThreadsStates = new CpuThreadsModel(viewRange, profilers, mySession);

    myEventMonitor = new EventMonitor(profilers);

    myRangeSelectionModel = buildRangeSelectionModel(selectionRange, viewRange);

    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);

    myCaptureState = CaptureState.IDLE;

    myUpdatableManager = new UpdatableManager(getStudioProfilers().getUpdater());
    myCaptureParser = captureParser;

    List<Cpu.CpuTraceInfo> existingCompletedTraceInfoList =
      CpuProfiler.getTraceInfoFromSession(getStudioProfilers().getClient(), mySession).stream()
        .filter(info -> info.getToTimestamp() != -1).collect(Collectors.toList());
    existingCompletedTraceInfoList.forEach(info -> myCompletedTraceIdToInfoMap.put(info.getTraceId(), new CpuTraceInfo(info)));
    myInProgressTraceHandler = new InProgressTraceHandler();
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfilerStage.class);
  }

  /**
   * Creates and returns a {@link RangeSelectionModel} given a {@link Range} representing the selection.
   */
  private RangeSelectionModel buildRangeSelectionModel(@NotNull Range selectionRange, @NotNull Range viewRange) {
    RangeSelectionModel rangeSelectionModel = new RangeSelectionModel(selectionRange, viewRange);
    rangeSelectionModel.addConstraint(myTraceDurations);
    return rangeSelectionModel;
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

  public RecordingOptionsModel getRecordingModel() {
    return myRecordingOptionsModel;
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

    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getStageType());

    getStudioProfilers().addDependency(this).onChange(ProfilerAspect.PROCESSES, myProfilerConfigModel::updateProfilingConfigurations);

    myProfilerConfigModel.updateProfilingConfigurations();
    setupRecordingOptions();
  }

  @Override
  public void exit() {
    myEventMonitor.exit();
    getStudioProfilers().getUpdater().unregister(myCpuUsage);
    getStudioProfilers().getUpdater().unregister(myTraceDurations);
    getStudioProfilers().getUpdater().unregister(myInProgressTraceHandler);
    getStudioProfilers().getUpdater().unregister(myCpuUsageAxis);
    getStudioProfilers().getUpdater().unregister(myThreadCountAxis);

    // Asks the parser to interrupt any parsing in progress.
    myCaptureParser.abortParsing();
    myRangeSelectionModel.clearListeners();
    myUpdatableManager.releaseAll();
  }

  @Override
  public AndroidProfilerEvent.Stage getStageType() {
    return AndroidProfilerEvent.Stage.CPU_STAGE;
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
    String traceFilePath = String.format(Locale.US, "%s/%s-%d.trace", DAEMON_DEVICE_DIR_PATH, process.getName(), System.nanoTime());

    setCaptureState(CaptureState.STARTING);
    Trace.TraceConfiguration.Builder configurationBuilder = Trace.TraceConfiguration.newBuilder()
      .setAppName(process.getName())
      .setAbiCpuArch(TransportFileManager.getShortAbiName(getStudioProfilers().getDevice().getCpuAbi()))
      .setInitiationType(TraceInitiationType.INITIATED_BY_UI)
      .setTempPath(traceFilePath);

    config.addOptions(configurationBuilder, Map.of(AdditionalOptions.APP_PKG_NAME, process.getName(), AdditionalOptions.SYMBOL_DIRS,
                                                   getStudioProfilers().getIdeServices().getNativeSymbolsDirectories()));
    Trace.TraceConfiguration configuration = configurationBuilder.build();

    Executor poolExecutor = getStudioProfilers().getIdeServices().getPoolExecutor();
    Commands.Command startCommand = Commands.Command.newBuilder()
      .setStreamId(mySession.getStreamId())
      .setPid(mySession.getPid())
      .setType(Commands.Command.CommandType.START_CPU_TRACE)
      .setStartCpuTrace(Cpu.StartCpuTrace.newBuilder().setConfiguration(configuration).build())
      .build();

    getStudioProfilers().getClient().executeAsync(startCommand, poolExecutor)
      .thenAcceptAsync(response -> {
        TransportEventListener statusListener = new TransportEventListener(
          Common.Event.Kind.TRACE_STATUS,
          getStudioProfilers().getIdeServices().getMainExecutor(),
          event -> event.getCommandId() == response.getCommandId(),
          mySession::getStreamId,
          mySession::getPid,
          event -> {
            startCapturingCallback(event.getTraceStatus().getTraceStartStatus());
            // unregisters the listener.
            return true;
          });
        getStudioProfilers().getTransportPoller().registerListener(statusListener);
      }, poolExecutor);

    getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_CPU_CAPTURE, true);
    myInstructionsEaseOutModel.setCurrentPercentage(1);
  }

  private void startCapturingCallback(@NotNull Trace.TraceStartStatus status) {
    if (status.getStatus().equals(Trace.TraceStartStatus.Status.SUCCESS)) {
      // Set myCaptureStartTimeNs before updating the state because the timestamp may be used to construct recording panel.
      myCaptureStartTimeNs = currentTimeNs();
      setCaptureState(CaptureState.CAPTURING);
      // We should jump to live data when start recording.
      getTimeline().setStreaming(true);
    }
    else {
      getLogger().warn("Unable to start tracing: " + status.getStatus());
      getLogger().warn(status.getErrorMessage());
      getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.CAPTURE_START_FAILURE);
      // Return to IDLE state and set the current capture to null
      setCaptureState(CaptureState.IDLE);
    }
  }

  @VisibleForTesting
  void stopCapturing() {
    // We need to send the trace configuration that was used to initiate the capture. Return early if no in-progress trace exists.
    if (Cpu.CpuTraceInfo.getDefaultInstance().equals(myInProgressTraceInfo)) {
      return;
    }

    // Set myCaptureStopTimeNs before updating the state because the timestamp may be used to construct stopping panel.
    myCaptureStopTimeNs = currentTimeNs();
    setCaptureState(CaptureState.STOPPING);
    CpuProfiler.stopTracing(getStudioProfilers(), mySession, myInProgressTraceInfo.getConfiguration(), this::stopCapturingCallback);
  }

  public long getCaptureStartTimeNs() {
    return myCaptureStartTimeNs;
  }

  public long getCaptureStopTimeNs() {
    return myCaptureStopTimeNs;
  }

  private void stopCapturingCallback(@NotNull Trace.TraceStopStatus status) {
    if (status.getStatus().equals(Trace.TraceStopStatus.Status.UNSPECIFIED)) {
      // Daemon reports a matching ongoing recording has been found. Stopping is in progress. Do nothing.
      // When the stopping is done, a CPU_TRACE event will be generated, and it will be tracked via the InProgressTraceHandler.
    }
    else if (!status.getStatus().equals(Trace.TraceStopStatus.Status.SUCCESS)) {
      trackAndLogTraceStopFailures(status);
      // Return to IDLE state and set the current capture to null
      setCaptureState(CaptureState.IDLE);
    }
  }

  private void trackAndLogTraceStopFailures(@NotNull Trace.TraceStopStatus status) {
    CpuCaptureMetadata captureMetadata = new CpuCaptureMetadata(myProfilerConfigModel.getProfilingConfiguration());
    long estimateDurationMs = TimeUnit.NANOSECONDS.toMillis(currentTimeNs() - myCaptureStartTimeNs);
    // Set the estimate duration of the capture, i.e. the time difference between device time when user clicked start and stop.
    // If the capture is successful, we can track a more accurate time, calculated from the capture itself.
    captureMetadata.setCaptureDurationMs(estimateDurationMs);
    captureMetadata.setStoppingTimeMs((int)TimeUnit.NANOSECONDS.toMillis(status.getStoppingDurationNs()));
    captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.fromStopStatus(status.getStatus()));
    getStudioProfilers().getIdeServices().getFeatureTracker().trackCaptureTrace(captureMetadata);

    getLogger().warn("Unable to stop tracing: " + status.getStatus());
    getLogger().warn(status.getErrorMessage());
    getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.getCaptureStopFailure(status.getStatus().toString()));
  }

  private void goToCaptureStage(long traceId) {
    if (myCompletedTraceIdToInfoMap.containsKey(traceId)) {
      CpuCaptureStage stage = CpuCaptureStage.create(
        getStudioProfilers(),
        ProfilingConfiguration.fromProto(myCompletedTraceIdToInfoMap.get(traceId).getTraceInfo().getConfiguration()),
        traceId);
      if (stage != null) {
        getStudioProfilers().getIdeServices().getMainExecutor().execute(() -> getStudioProfilers().setStage(stage));
        return;
      }
    }
    // Trace ID is not found or the capture stage cannot retrieve the trace.
    setCaptureState(CaptureState.IDLE);
    getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.IMPORT_TRACE_PARSING_FAILURE);
  }

  @NotNull
  public CpuCaptureParser getCaptureParser() {
    return myCaptureParser;
  }

  @NotNull
  private CpuServiceGrpc.CpuServiceBlockingStub getCpuClient() {
    return getStudioProfilers().getClient().getCpuClient();
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
      if (captureRange.intersectsWith(range)) {
        return info.value;
      }
    }
    return null;
  }

  public long currentTimeNs() {
    return TimeUnit.MICROSECONDS.toNanos((long)getTimeline().getDataRange().getMax());
  }

  public void setAndSelectCapture(long traceId) {
    goToCaptureStage(traceId);
  }

  public int getSelectedThread() {
    return myThreadsStates.getThread();
  }

  public void setSelectedThread(int id) {
    myThreadsStates.setThread(id);
    Range range = getTimeline().getSelectionRange();
    if (range.isEmpty()) {
      myAspect.changed(CpuProfilerAspect.SELECTED_THREADS);
    }
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
        // When going to CAPTURING state need to keep the recording options model in sync.
        // This is needed when a startup recording or API recording has started.
        if (!myRecordingOptionsModel.isRecording()) {
          if (isApiInitiatedTracingInProgress()) {
            RecordingOption option = addConfiguration(API_INITIATED_TRACING_PROFILING_CONFIG);
            myRecordingOptionsModel.getCustomConfigurationModel().setSelectedItem(option);
            myRecordingOptionsModel.selectCurrentCustomConfiguration();
          }
          else if (getCaptureInitiationType().equals(TraceInitiationType.INITIATED_BY_STARTUP)) {
            myRecordingOptionsModel.selectOptionBy(
              option -> option.getTitle().equals(myProfilerConfigModel.getProfilingConfiguration().getName()));
          }
        }
        myRecordingOptionsModel.setRecording();
      }
    }
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

  /**
   * Clears and reapplies the custom configuration options on the recording options model.
   */
  public void refreshRecordingConfigurations() {
    myRecordingOptionsModel.clearConfigurations();
    // Add custom configs.
    myProfilerConfigModel.getCustomProfilingConfigurationsDeviceFiltered().forEach(this::addConfiguration);
  }

  private RecordingOption addConfiguration(ProfilingConfiguration config) {
    RecordingOption option =
      new RecordingOption(config.getName(), "", () -> startRecordingConfig(config), this::stopCapturing);
    myRecordingOptionsModel.addConfigurations(option);
    return option;
  }

  private void startRecordingConfig(ProfilingConfiguration config) {
    myProfilerConfigModel.setProfilingConfiguration(config);
    startCapturing();
  }

  private void setupRecordingOptions() {
    // Add default configs
    for (ProfilingConfiguration configuration : myProfilerConfigModel.getDefaultProfilingConfigurations()) {
      ProfilingTechnology tech = ProfilingTechnology.fromConfig(configuration);
      myRecordingOptionsModel.addBuiltInOptions(
        new RecordingOption(configuration.getName(), tech.getDescription(), () -> startRecordingConfig(configuration),
                            this::stopCapturing));
    }
    refreshRecordingConfigurations();
    myRecordingOptionsModel.selectOptionBy(
      recordingOption -> recordingOption.getTitle().equals(myProfilerConfigModel.getProfilingConfiguration().getName()));
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
      Range dataRange = getTimeline().getDataRange();
      List<Cpu.CpuTraceInfo> traceInfoList =
        CpuProfiler.getTraceInfoFromRange(getStudioProfilers().getClient(), mySession, dataRange);
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

        if (!myCompletedTraceIdToInfoMap.containsKey(trace.getTraceId())) {
          myCompletedTraceIdToInfoMap.put(trace.getTraceId(), new CpuTraceInfo(trace));
          // queried trace info list should be sorted by time so we can always assume the latest one should be selected.
          finishedTraceToSelect = trace;

          if (trace.getConfiguration().getInitiationType().equals(TraceInitiationType.INITIATED_BY_API)) {
            // Handcraft the metadata, since that is not generated by the profilers UI.
            CpuCaptureMetadata metadata = new CpuCaptureMetadata(API_INITIATED_TRACING_PROFILING_CONFIG);
            myCaptureParser.trackCaptureMetadata(trace.getTraceId(), metadata);

            // Track usage for API-initiated tracing.
            getStudioProfilers().getIdeServices().getFeatureTracker().trackCpuApiTracing(false, true, -1, -1, -1);
          }

          // Inform CpuCaptureParser to track metrics when the successful trace is parsed.
          if (trace.getStopStatus().getStatus().equals(Trace.TraceStopStatus.Status.SUCCESS)) {
            CpuCaptureMetadata captureMetadata =
              new CpuCaptureMetadata(ProfilingConfiguration.fromProto(finishedTraceToSelect.getConfiguration()));
            // If the capture is successful, we can track a more accurate time, calculated from the capture itself.
            captureMetadata.setCaptureDurationMs(TimeUnit.NANOSECONDS.toMillis(trace.getToTimestamp() - trace.getFromTimestamp()));
            captureMetadata.setStoppingTimeMs((int)TimeUnit.NANOSECONDS.toMillis(trace.getStopStatus().getStoppingDurationNs()));
            myCaptureParser.trackCaptureMetadata(trace.getTraceId(), captureMetadata);
          }
          else {
            trackAndLogTraceStopFailures(trace.getStopStatus());
          }
        }
      }

      if (finishedTraceToSelect != null) {
        myInProgressTraceInfo = Cpu.CpuTraceInfo.getDefaultInstance();
        if (finishedTraceToSelect.getStopStatus().getStatus() == Trace.TraceStopStatus.Status.SUCCESS) {
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
      // Set myCaptureStartTimeNs/myCaptureStopTimeNs before updating the state because the timestamp may be used to construct
      // recording panel.
      CaptureState state = CaptureState.CAPTURING;
      myCaptureStartTimeNs = myInProgressTraceInfo.getFromTimestamp();
      Common.Event statusEvent = CpuProfiler.getTraceStatusEventFromId(getStudioProfilers(), myInProgressTraceInfo.getTraceId());
      if (statusEvent.getKind() == Common.Event.Kind.TRACE_STATUS && statusEvent.getTraceStatus().hasTraceStopStatus()) {
        // A STOP_CPU_TRACE command has been issued.
        state = CaptureState.STOPPING;
        myCaptureStopTimeNs = statusEvent.getTimestamp();
      }

      if (myInProgressTraceInfo.getConfiguration().getInitiationType() == TraceInitiationType.INITIATED_BY_API) {
        // For API-initiated tracing, we want to update the config combo box to show API_INITIATED_TRACING_PROFILING_CONFIG.
        // Don't update the myProfilerConfigModel. First, this config is by definition transitory. Passing the reference outside
        // CpuProfilerStage may indicate a longer life span. Second, it is not a real configuration. For example, each
        // configuration's name should be unique, but all API-initiated captures should show the same text even if they
        // are different such as in sample interval.
        myAspect.changed(CpuProfilerAspect.PROFILING_CONFIGURATION);
      }
      else {
        // Updates myProfilerConfigModel to the ongoing profiler configuration.
        myProfilerConfigModel.setProfilingConfiguration(ProfilingConfiguration.fromProto(traceInfo.getConfiguration()));
      }
      setCaptureState(state);
      getTimeline().setStreaming(true);
    }
  }

  @VisibleForTesting
  class CpuTraceDataSeries implements DataSeries<CpuTraceInfo> {
    @Override
    public List<SeriesData<CpuTraceInfo>> getDataForRange(Range range) {
      List<Cpu.CpuTraceInfo> traceInfos = CpuProfiler.getTraceInfoFromRange(getStudioProfilers().getClient(), mySession, range);
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
      myCpuLegend.setCachingLastValue(true);
      myOthersLegend.setCachingLastValue(true);
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
