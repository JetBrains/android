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
import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.EaseOutModel;
import com.android.tools.adtui.model.Interpolatable;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.idea.transport.TransportFileManager;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Trace.TraceInitiationType;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.LogUtils;
import com.android.tools.profilers.NullMonitorStage;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.RecordingOption;
import com.android.tools.profilers.RecordingOptionsModel;
import com.android.tools.profilers.StreamingStage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.InterimStage;
import com.android.tools.profilers.cpu.adapters.CpuDataProvider;
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration;
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.AdditionalOptions;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.taskbased.task.interim.RecordingScreenModel;
import com.android.tools.profilers.tasks.TaskEventTrackerUtils;
import com.android.tools.profilers.tasks.TaskMetadataStatus;
import com.android.tools.profilers.tasks.TaskStartFailedMetadata;
import com.android.tools.profilers.tasks.TaskStopFailedMetadata;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.google.wireless.android.sdk.stats.TaskFailedMetadata;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CpuProfilerStage extends StreamingStage implements InterimStage {
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
  private final CpuDataProvider myCpuDataProvider;

  private final CpuProfilerConfigModel myProfilerConfigModel;

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
  @NotNull private Trace.TraceInfo myInProgressTraceInfo = Trace.TraceInfo.getDefaultInstance();

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

  /**
   * The way in which the user entered the CpuProfilerStage (e.g. Startup Profiling, Monitor Stage).
   */
  private final CpuCaptureMetadata.CpuProfilerEntryPoint myEntryPoint;

  private final boolean isTraceboxEnabled;

  @NotNull
  private final Runnable myStopAction;

  @Nullable
  private final RecordingScreenModel<CpuProfilerStage> myRecordingScreenModel;

  public CpuProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CpuCaptureParser(profilers), CpuCaptureMetadata.CpuProfilerEntryPoint.UNKNOWN, () -> {});
  }

  @VisibleForTesting
  public CpuProfilerStage(@NotNull StudioProfilers profilers, @NotNull CpuCaptureParser captureParser) {
    this(profilers, captureParser, CpuCaptureMetadata.CpuProfilerEntryPoint.UNKNOWN, () -> {});
  }

  /**
   * This constructor is utilized to create instances of CpuProfilerStage that allow the owning class to supply a custom runnable called
   * stopAction. This enables the bound view to invoke this custom behavior on stoppage of whatever flow this stage is facilitating (e.g.
   * if this stage is facilitating a recording, this can serve as the handler for the "Stop Recording" button).
   */
  public CpuProfilerStage(@NotNull StudioProfilers profilers, @NotNull Runnable stopAction) {
    this(profilers, new CpuCaptureParser(profilers), CpuCaptureMetadata.CpuProfilerEntryPoint.UNKNOWN, stopAction);
  }

  /**
   * This constructor is used for creating CpuProfilerStage instances that allow the user to take CPU captures.
   * Hence, the entry point is taken as a parameter to be tracked for when the user takes said capture.
   */
  public CpuProfilerStage(@NotNull StudioProfilers profilers, CpuCaptureMetadata.CpuProfilerEntryPoint entryPoint) {
    this(profilers, new CpuCaptureParser(profilers), entryPoint, () -> {});
  }

  private CpuProfilerStage(@NotNull StudioProfilers profilers,
                           @NotNull CpuCaptureParser captureParser,
                           CpuCaptureMetadata.CpuProfilerEntryPoint entryPoint,
                           @NotNull Runnable stopAction) {
    super(profilers);
    mySession = profilers.getSession();
    myCpuDataProvider = new CpuDataProvider(profilers, getTimeline());
    myProfilerConfigModel = new CpuProfilerConfigModel(profilers, this);
    myRecordingOptionsModel = new RecordingOptionsModel();

    myCaptureState = CaptureState.IDLE;
    myCaptureParser = captureParser;

    // Store and track how the user entered the CpuProfilerStage to take a cpu trace.
    myEntryPoint = entryPoint;

    myStopAction = stopAction;
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled()) {
      myRecordingScreenModel = new RecordingScreenModel<>(this);
    }
    else {
      myRecordingScreenModel = null;
    }

    List<Trace.TraceInfo> existingCompletedTraceInfoList =
      CpuProfiler.getTraceInfoFromSession(getStudioProfilers().getClient(), mySession).stream()
        .filter(info -> info.getToTimestamp() != -1).collect(Collectors.toList());
    existingCompletedTraceInfoList.forEach(info -> myCompletedTraceIdToInfoMap.put(info.getTraceId(), new CpuTraceInfo(info)));
    myInProgressTraceHandler = new InProgressTraceHandler();

    isTraceboxEnabled = getStudioProfilers().getIdeServices().getFeatureConfig().isTraceboxEnabled();
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfilerStage.class);
  }

  public boolean hasUserUsedCpuCapture() {
    return getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().getBoolean(HAS_USED_CPU_CAPTURE, false);
  }

  @NotNull
  public RangeSelectionModel getRangeSelectionModel() {
    return myCpuDataProvider.getRangeSelectionModel();
  }

  @NotNull
  public EaseOutModel getInstructionsEaseOutModel() {
    return myCpuDataProvider.getInstructionsEaseOutModel();
  }

  public AxisComponentModel getCpuUsageAxis() {
    return myCpuDataProvider.getCpuUsageAxis();
  }

  public AxisComponentModel getThreadCountAxis() {
    return myCpuDataProvider.getThreadCountAxis();
  }

  public AxisComponentModel getTimeAxisGuide() {
    return myCpuDataProvider.getTimeAxisGuide();
  }

  public DetailedCpuUsage getCpuUsage() {
    return myCpuDataProvider.getCpuUsage();
  }

  public CpuStageLegends getLegends() {
    return myCpuDataProvider.getLegends();
  }

  public DurationDataModel<CpuTraceInfo> getTraceDurations() {
    return myCpuDataProvider.getTraceDurations();
  }

  public String getName() {
    return "CPU";
  }

  public EventMonitor getEventMonitor() {
    return myCpuDataProvider.getEventMonitor();
  }

  public RecordingOptionsModel getRecordingModel() {
    return myRecordingOptionsModel;
  }

  @NotNull
  public CpuProfilerConfigModel getProfilerConfigModel() {
    return myProfilerConfigModel;
  }

  public CpuCaptureMetadata.CpuProfilerEntryPoint getEntryPoint() { return myEntryPoint; }

  @NotNull
  @Override
  public Runnable getStopAction() {
    return myStopAction;
  }

  @Nullable
  @Override
  public RecordingScreenModel<CpuProfilerStage> getRecordingScreenModel() {
    return myRecordingScreenModel;
  }

  @Override
  public void enter() {
    logEnterStage();
    getEventMonitor().enter();
    getStudioProfilers().getUpdater().register(getCpuUsage());
    getStudioProfilers().getUpdater().register(getTraceDurations());
    getStudioProfilers().getUpdater().register(myInProgressTraceHandler);
    getStudioProfilers().getUpdater().register((ClampedAxisComponentModel)getCpuUsageAxis());
    getStudioProfilers().getUpdater().register((ClampedAxisComponentModel)getThreadCountAxis());
    // Register recording screen model updatable so timer can update on tick.
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled() && myRecordingScreenModel != null) {
      getStudioProfilers().getUpdater().register(myRecordingScreenModel);
    }

    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getStageType());

    getStudioProfilers().addDependency(this).onChange(ProfilerAspect.PROCESSES, myProfilerConfigModel::updateProfilingConfigurations);

    myProfilerConfigModel.updateProfilingConfigurations();
    setupRecordingOptions();
  }

  @Override
  public void exit() {
    getEventMonitor().exit();
    getStudioProfilers().getUpdater().unregister(getCpuUsage());
    getStudioProfilers().getUpdater().unregister(getTraceDurations());
    getStudioProfilers().getUpdater().unregister(myInProgressTraceHandler);
    getStudioProfilers().getUpdater().unregister((ClampedAxisComponentModel)getCpuUsageAxis());
    getStudioProfilers().getUpdater().unregister((ClampedAxisComponentModel)getThreadCountAxis());
    // Deregister recording screen model updatable so timer does not continue in background.
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled() && myRecordingScreenModel != null) {
      getStudioProfilers().getUpdater().unregister(myRecordingScreenModel);
    }

    // Asks the parser to interrupt any parsing in progress.
    myCaptureParser.abortParsing();
    getRangeSelectionModel().clearListeners();
    getUpdatableManager().releaseAll();
  }

  @Override
  public AndroidProfilerEvent.Stage getStageType() {
    return AndroidProfilerEvent.Stage.CPU_STAGE;
  }

  @NotNull
  public UpdatableManager getUpdatableManager() {
    return myCpuDataProvider.getUpdatableManager();
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myCpuDataProvider.getAspect();
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
    LogUtils.log(getClass(), "CPU capture start attempted");
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

    // Execute a start trace command for cpu-based tracing and registers a listener for event reception and handling.
    // The startCapturingCallback with be called on event reception.
    CpuProfiler.startTracing(getStudioProfilers(), mySession, configuration, this::startCapturingCallback, null);

    getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_CPU_CAPTURE, true);
    getInstructionsEaseOutModel().setCurrentRatio(1);
  }

  private void startCapturingCallback(@NotNull Trace.TraceStartStatus status) {
    if (status.getStatus().equals(Trace.TraceStartStatus.Status.SUCCESS)) {
      LogUtils.log(getClass(), "CPU capture start succeeded");
      // Set myCaptureStartTimeNs before updating the state because the timestamp may be used to construct recording panel.
      myCaptureStartTimeNs = currentTimeNs();
      setCaptureState(CaptureState.CAPTURING);
      // We should jump to live data when start recording.
      getTimeline().setStreaming(true);
    }
    else {
      getLogger().warn("Unable to start tracing: " + status.getStatus() + " due to error code " + status.getErrorCode());
      getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.CAPTURE_START_FAILURE);
      // Return to IDLE state and set the current capture to null
      setCaptureState(CaptureState.IDLE);
      TaskEventTrackerUtils.trackStartTaskFailed(getStudioProfilers(), getStudioProfilers().getSessionsManager().isSessionAlive(),
                                                 new TaskStartFailedMetadata(status, null, null));
    }
  }

  @VisibleForTesting
  void stopCapturing() {
    LogUtils.log(getClass(), "CPU capture stop attempted");
    // We need to send the trace configuration that was used to initiate the capture. Return early if no in-progress trace exists.
    if (Trace.TraceInfo.getDefaultInstance().equals(myInProgressTraceInfo)) {
      return;
    }

    // Set myCaptureStopTimeNs before updating the state because the timestamp may be used to construct stopping panel.
    myCaptureStopTimeNs = currentTimeNs();
    setCaptureState(CaptureState.STOPPING);
    CpuProfiler.stopTracing(getStudioProfilers(), mySession, myInProgressTraceInfo.getConfiguration(), this::stopCapturingCallback, null);
  }

  public long getCaptureStartTimeNs() {
    return myCaptureStartTimeNs;
  }

  public long getCaptureStopTimeNs() {
    return myCaptureStopTimeNs;
  }

  private void stopCapturingCallback(@NotNull Trace.TraceStopStatus status) {
    // Whether the stop was successful or resulted in failure, call setFinished() to indicate
    // that the recording has stopped so that the user is able to start a new capture.
    myRecordingOptionsModel.setFinished();
    if (status.getStatus().equals(Trace.TraceStopStatus.Status.UNSPECIFIED)) {
      // Daemon reports a matching ongoing recording has been found. Stopping is in progress. Do nothing.
      // When the stopping is done, a CPU_TRACE event will be generated, and it will be tracked via the InProgressTraceHandler.
    }
    else if (!status.getStatus().equals(Trace.TraceStopStatus.Status.SUCCESS)) {
      CpuCaptureMetadata captureMetadata = trackAndLogTraceStopFailures(status);
      TaskEventTrackerUtils.trackStopTaskFailed(getStudioProfilers(), getStudioProfilers().getSessionsManager().isSessionAlive(),
                                                new TaskStopFailedMetadata(null, null, captureMetadata));
      // Return to IDLE state and set the current capture to null
      setCaptureState(CaptureState.IDLE);
    }
  }

  /**
   * Populate CpuCaptureMetadata attributes. Also, track the session-based era metrics.
   * @return: CpuCaptureMetadata
   */
  private CpuCaptureMetadata trackAndLogTraceStopFailures(@NotNull Trace.TraceStopStatus status) {
    CpuCaptureMetadata captureMetadata = new CpuCaptureMetadata(myProfilerConfigModel.getProfilingConfiguration());
    long estimateDurationMs = TimeUnit.NANOSECONDS.toMillis(currentTimeNs() - myCaptureStartTimeNs);
    // Set the estimate duration of the capture, i.e. the time difference between device time when user clicked start and stop.
    // If the capture is successful, we can track a more accurate time, calculated from the capture itself.
    captureMetadata.setCaptureDurationMs(estimateDurationMs);
    captureMetadata.setStoppingTimeMs((int)TimeUnit.NANOSECONDS.toMillis(status.getStoppingDurationNs()));
    captureMetadata.setStatus(CpuCaptureMetadata.CaptureStatus.fromStopStatus(status.getStatus()));
    captureMetadata.setCpuProfilerEntryPoint(myEntryPoint);
    if (captureMetadata.getProfilingConfiguration().getTraceType() == ProfilingConfiguration.TraceType.ART) {
      captureMetadata.setArtStopTimeoutSec(CpuProfilerStage.CPU_ART_STOP_TIMEOUT_SEC);
    }
    getStudioProfilers().getIdeServices().getFeatureTracker().trackCaptureTrace(captureMetadata);

    getLogger().warn("Unable to stop tracing: " + status.getStatus() +" error code " + status.getErrorCode());
    getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.getCaptureStopFailure(status.getStatus().toString()));
    return captureMetadata;
  }

  private void goToCaptureStage(long traceId) {
    // Trace ID handled by the Profiler
    if (myCompletedTraceIdToInfoMap.containsKey(traceId)) {
      // The creation of the CpuCaptureStage has been offloaded to a separate thread and therefore computed asynchronously so that
      // (1) it does not block the main thread with some of the downstream, potentially blocking grpc calls (e.g. call to getBytes()) and
      // (2) it allows us to set a time limitation/timeout on the creation of the capture stage to prevent indefinite freezing in cases
      // where creating the CpuCaptureStage failed.
      getStudioProfilers().getIdeServices().runAsync(
        () -> {
          myRecordingOptionsModel.setLoading(true);
          return CpuCaptureStage.create(getStudioProfilers(), ProfilingConfiguration.fromProto(
            myCompletedTraceIdToInfoMap.get(traceId).getTraceInfo().getConfiguration(), isTraceboxEnabled), myEntryPoint, traceId);
        },
        stage -> {
          myRecordingOptionsModel.setLoading(false);
          if (stage != null) {
            getStudioProfilers().getIdeServices().getMainExecutor().execute(() -> getStudioProfilers().setStage(stage));
          }
          else {
            // Trace ID is not found or the capture stage cannot retrieve the trace.
            setCaptureState(CaptureState.IDLE);
            getStudioProfilers().getIdeServices().showNotification(CpuProfilerNotifications.IMPORT_TRACE_PARSING_FAILURE);
          }
        }, 10000);
    }
  }

  @NotNull
  public CpuCaptureParser getCaptureParser() {
    return myCaptureParser;
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
    return getThreadStates().getThread();
  }

  public void setSelectedThread(int id) {
    getThreadStates().setThread(id);
    Range range = getTimeline().getSelectionRange();
    if (range.isEmpty()) {
      getAspect().changed(CpuProfilerAspect.SELECTED_THREADS);
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
      getAspect().changed(CpuProfilerAspect.CAPTURE_STATE);

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
  public CpuDataProvider.CpuTraceDataSeries getCpuTraceDataSeries() {
    return myCpuDataProvider.getCpuTraceDataSeries();
  }

  @NotNull
  public CpuThreadsModel getThreadStates() {
    return myCpuDataProvider.getThreadStates();
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

  /**
   * In the Task-Based UX, the recording config will be set by the respective task handler in the RecordingModel,
   * allowing us to invoke the start() method of the RecordingModel to start the recording.
   */
  public void startCpuRecording() {
    getRecordingModel().start();
  }

  /**
   * In the Task-Based UX, the recording config will be set by the respective task handler in the RecordingModel,
   * allowing us to invoke the stop() method of the RecordingModel to stop the recording.
   */
  public void stopCpuRecording() {
    getRecordingModel().stop();
  }

  private void setupRecordingOptions() {
    List<ProfilingConfiguration> configurations;
    // Add task configs if task-based ux flag is enabled, and default configs if disabled.
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled()) {
      configurations = myProfilerConfigModel.getTaskProfilingConfigurations();
    }
    else {
      configurations = myProfilerConfigModel.getDefaultProfilingConfigurations();
    }

    for (ProfilingConfiguration configuration : configurations) {
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
        getAspect().changed(CpuProfilerAspect.CAPTURE_ELAPSED_TIME);
        return;
      }
      Trace.TraceInfo finishedTraceToSelect = null;
      // Request for the entire data range as we don't expect too many (100s) traces withing a single session.
      Range dataRange = getTimeline().getDataRange();
      List<Trace.TraceInfo> traceInfoList =
        CpuProfiler.getTraceInfoFromRange(getStudioProfilers().getClient(), mySession, dataRange);
      for (int i = 0; i < traceInfoList.size(); i++) {
        Trace.TraceInfo trace = traceInfoList.get(i);
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
            LogUtils.log(getClass(), "CPU capture stop succeeded");
            CpuCaptureMetadata captureMetadata =
              new CpuCaptureMetadata(ProfilingConfiguration.fromProto(finishedTraceToSelect.getConfiguration(), isTraceboxEnabled));
            // If the capture is successful, we can track a more accurate time, calculated from the capture itself.
            captureMetadata.setCaptureDurationMs(TimeUnit.NANOSECONDS.toMillis(trace.getToTimestamp() - trace.getFromTimestamp()));
            captureMetadata.setStoppingTimeMs((int)TimeUnit.NANOSECONDS.toMillis(trace.getStopStatus().getStoppingDurationNs()));
            captureMetadata.setCpuProfilerEntryPoint(myEntryPoint);
            myCaptureParser.trackCaptureMetadata(trace.getTraceId(), captureMetadata);
          }
          else {
            trackAndLogTraceStopFailures(trace.getStopStatus());
          }
        }
      }

      if (finishedTraceToSelect != null) {
        myInProgressTraceInfo = Trace.TraceInfo.getDefaultInstance();
        if (finishedTraceToSelect.getStopStatus().getStatus() == Trace.TraceStopStatus.Status.SUCCESS) {
          setAndSelectCapture(finishedTraceToSelect.getTraceId());
          // The following registration of the selected artifact is done to make sure that api-initiated
          // trace artifacts that cause a jump to the cpu capture stage to be displayed are taken into account.
          // We cannot include this implicit selection with all other implicit artifact selections (in SessionManger's
          // registerImplicitlySelectedArtifactProto) as there are multiple cases where api-initiated, unlike other
          // initiation techniques, does not jump to the capture stage on artifact generation.
          // Note: In the scenario of non-api initiated trace generation where the profiler automatically jumps
          // to the recorded capture, the selected artifact proto will be set twice. Once by the following
          // line and once by SessionManager's registerImplicitlySelectedArtifactProto method. This is known
          // and harmless as both times it will set the exact same proto.
          getStudioProfilers().getSessionsManager().registerSelectedArtifactProto(finishedTraceToSelect);
        }
        else {
          setCaptureState(CaptureState.IDLE);
        }

        // When API-initiated tracing ends, we want to update the config combo box back to the entry before API tracing.
        // This is done by fire aspect PROFILING_CONFIGURATION.
        getAspect().changed(CpuProfilerAspect.PROFILING_CONFIGURATION);
      }
    }

    /**
     * When an in-progress trace is first detected, we ensure that:
     * 1. The capture state is switched to capturing
     * 2. The timeline is streaming
     * 3. The profile configuration is synchronized with the trace info.
     */
    private void handleInProgressTrace(@NotNull Trace.TraceInfo traceInfo) {
      if (traceInfo.equals(myInProgressTraceInfo)) {
        getAspect().changed(CpuProfilerAspect.CAPTURE_ELAPSED_TIME);
        return;
      }

      myInProgressTraceInfo = traceInfo;
      // Set myCaptureStartTimeNs/myCaptureStopTimeNs before updating the state because the timestamp may be used to construct
      // recording panel.
      CaptureState state = CaptureState.CAPTURING;
      myCaptureStartTimeNs = myInProgressTraceInfo.getFromTimestamp();
      Common.Event statusEvent = CpuProfiler.getTraceStatusEventFromId(getStudioProfilers(), myInProgressTraceInfo.getTraceId());
      if (statusEvent.getKind() == Common.Event.Kind.TRACE_STATUS && statusEvent.getTraceStatus().hasTraceStopStatus()) {
        // A STOP_TRACE command has been issued.
        state = CaptureState.STOPPING;
        myCaptureStopTimeNs = statusEvent.getTimestamp();
      }

      if (myInProgressTraceInfo.getConfiguration().getInitiationType() == TraceInitiationType.INITIATED_BY_API) {
        // For API-initiated tracing, we want to update the config combo box to show API_INITIATED_TRACING_PROFILING_CONFIG.
        // Don't update the myProfilerConfigModel. First, this config is by definition transitory. Passing the reference outside
        // CpuProfilerStage may indicate a longer life span. Second, it is not a real configuration. For example, each
        // configuration's name should be unique, but all API-initiated captures should show the same text even if they
        // are different such as in sample interval.
        getAspect().changed(CpuProfilerAspect.PROFILING_CONFIGURATION);
      }
      else {
        // Updates myProfilerConfigModel to the ongoing profiler configuration.
        myProfilerConfigModel.setProfilingConfiguration(ProfilingConfiguration.fromProto(traceInfo.getConfiguration(), isTraceboxEnabled));
      }
      setCaptureState(state);
      getTimeline().setStreaming(true);
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
