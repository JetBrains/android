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
package com.android.tools.profilers.memory;

import static com.android.tools.profilers.StudioProfilers.DAEMON_DEVICE_DIR_PATH;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import com.android.tools.idea.transport.TransportFileManager;
import com.android.tools.idea.transport.poller.TransportEventListener;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.LogUtils;
import com.android.tools.profilers.RecordingOption;
import com.android.tools.profilers.RecordingOptionsModel;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.SupportLevel;
import com.android.tools.profilers.InterimStage;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject;
import com.android.tools.profilers.memory.adapters.LegacyAllocationCaptureObject;
import com.android.tools.profilers.memory.adapters.NativeAllocationSampleCaptureObject;
import com.android.tools.profilers.perfetto.config.PerfettoTraceConfigBuilders;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.taskbased.task.interim.RecordingScreenModel;
import com.android.tools.profilers.tasks.TaskEventTrackerUtils;
import com.android.tools.profilers.tasks.TaskMetadataStatus;
import com.android.tools.profilers.tasks.TaskStartFailedMetadata;
import com.android.tools.profilers.tasks.TaskStopFailedMetadata;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.TaskFailedMetadata;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;
import kotlin.Lazy;
import kotlin.LazyKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import perfetto.protos.PerfettoConfig;

public class MainMemoryProfilerStage extends BaseStreamingMemoryProfilerStage implements InterimStage {
  private static final String HEAP_DUMP_TOOLTIP = "View objects in your app that are using memory at a specific point in time";
  private static final String CAPTURE_HEAP_DUMP_TEXT = "Capture heap dump";
  private static final String RECORD_JAVA_TEXT = "Record Java / Kotlin allocations";
  private static final String RECORD_JAVA_TOOLTIP = "View how each Java / Kotlin object was allocated over a period of time";
  @VisibleForTesting static final String RECORD_NATIVE_TEXT = "Record native allocations";
  @VisibleForTesting static final String X86_RECORD_NATIVE_TOOLTIP = "Native memory recording is unavailable on x86 or x86_64 devices";
  private static final String RECORD_NATIVE_DESC = "View how each C / C++ object was allocated over a period of time";


  // The safe factor estimating how many times of memory is needed compared to hprof file size
  public static final int MEMORY_HPROF_SAFE_FACTOR =
    Math.max(1, Math.min(Integer.getInteger("profiler.memory.hprof.safeFactor", 10), 1000));

  /**
   * Whether the stage only contains heap dump data imported from hprof file
   */
  private final boolean myIsMemoryCaptureOnly;

  private final DurationDataModel<CaptureDurationData<? extends CaptureObject>> myHeapDumpDurations;
  private final DurationDataModel<CaptureDurationData<? extends CaptureObject>> myAllocationDurations;
  private final DurationDataModel<CaptureDurationData<? extends CaptureObject>> myNativeAllocationDurations;
  private long myPendingLegacyAllocationStartTimeNs = BaseMemoryProfilerStage.INVALID_START_TIME;

  @VisibleForTesting boolean myNativeAllocationTracking = false;

  private final RecordingOptionsModel myRecordingOptionsModel;

  @NotNull
  private final Runnable myStopAction;

  @Nullable
  private final RecordingScreenModel<MainMemoryProfilerStage> myRecordingScreenModel;

  @VisibleForTesting
  public Lazy<RecordingOption> lazyHeapDumpRecordingOption =
    LazyKt.lazyOf(new RecordingOption(CAPTURE_HEAP_DUMP_TEXT, HEAP_DUMP_TOOLTIP, () -> {
      requestHeapDump();
      getStudioProfilers().getIdeServices().getFeatureTracker().trackDumpHeap();
    }));

  @VisibleForTesting
  public Lazy<RecordingOption> lazyNativeRecordingOption =
    LazyKt.lazyOf(makeToggleOption(RECORD_NATIVE_TEXT, RECORD_NATIVE_DESC, this::toggleNativeAllocationTracking));

  @VisibleForTesting
  public Lazy<RecordingOption> lazyJavaKotlinAllocationsRecordingOption =
    LazyKt.lazyOf(makeToggleOption(RECORD_JAVA_TEXT, RECORD_JAVA_TOOLTIP,
                                   isLiveAllocationTrackingSupported() ?
                                   // post-O
                                   () -> getStudioProfilers().setStage(
                                     AllocationStage.makeLiveStage(
                                       getStudioProfilers(), getStopAction())) :
                                   // legacy
                                   () -> {
                                     if (isTrackingAllocations()) {
                                       getStudioProfilers().getIdeServices()
                                         .getFeatureTracker()
                                         .trackRecordAllocations();
                                     }
                                     trackAllocations(
                                       !isTrackingAllocations());
                                   }
    ));

  public MainMemoryProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CaptureObjectLoader(), () -> {});
  }

  public MainMemoryProfilerStage(@NotNull StudioProfilers profilers, @NotNull CaptureObjectLoader loader) {
    this(profilers, loader, () -> {});
  }

  /**
   * This constructor is utilized to create instances of MainMemoryProfilerStage that allow the owning class to supply a custom runnable
   * called stopAction. This enables the bound view to invoke this custom behavior on stoppage of whatever flow this stage is facilitating
   * (e.g. if this stage is facilitating a recording, this can serve as the handler for the "Stop Recording" button).
   */
  public MainMemoryProfilerStage(@NotNull StudioProfilers profilers, @NotNull Runnable stopAction) {
    this(profilers, new CaptureObjectLoader(), stopAction);
  }

  public MainMemoryProfilerStage(@NotNull StudioProfilers profilers,
                                 @NotNull CaptureObjectLoader loader,
                                 @NotNull Runnable stopAction) {
    super(profilers, loader);
    myIsMemoryCaptureOnly =
      profilers.getSessionsManager().getSelectedSessionMetaData().getType() == Common.SessionMetaData.SessionType.MEMORY_CAPTURE;

    // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
    myHeapDumpDurations = makeModel(CaptureDataSeries::ofHeapDumpSamples);
    myAllocationDurations = isLiveAllocationTrackingSupported()
                            ? makeModel(CaptureDataSeries::ofAllocationInfos)
                            : makeModel(CaptureDataSeries::ofLegacyAllocationInfos);
    myNativeAllocationDurations = makeModel(CaptureDataSeries::ofNativeAllocationSamples);

    myHeapDumpDurations.setRenderSeriesPredicate((data, series) ->
                                                   // Do not show the object series during a heap dump.
                                                   !series.getName().equals(getDetailedMemoryUsage().getObjectsSeries().getName())
    );

    getRangeSelectionModel().addConstraint(myAllocationDurations);
    getRangeSelectionModel().addConstraint(myNativeAllocationDurations);
    getRangeSelectionModel().addConstraint(myHeapDumpDurations);

    getStudioProfilers().getSessionsManager().addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION, this::stopRecordingOnSessionStop);

    myRecordingOptionsModel = new RecordingOptionsModel();
    myStopAction = stopAction;
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled()) {
      myRecordingScreenModel = new RecordingScreenModel<>(this);
    }
    else {
      myRecordingScreenModel = null;
    }
  }

  public RecordingOptionsModel getRecordingOptionsModel() {
    return myRecordingOptionsModel;
  }

  void stopRecordingOnSessionStop() {
    boolean isAlive = getStudioProfilers().getSessionsManager().isSessionAlive();
    if (!isAlive && myNativeAllocationTracking) {
      toggleNativeAllocationTracking();
    }
  }

  public boolean hasUserUsedMemoryCapture() {
    return getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().getBoolean(HAS_USED_MEMORY_CAPTURE, false);
  }

  @Override
  public void enter() {
    logEnterStage();
    super.enter();

    BiConsumer<SupportLevel.Feature, RecordingOption> adder = (feature, option) -> {
      myRecordingOptionsModel.addBuiltInOptions(option);
      if (!getStudioProfilers().getSelectedSessionSupportLevel().isFeatureSupported(feature)) {
        myRecordingOptionsModel.setOptionNotReady(option, feature.getTitle() + " is not supported for profileable processes");
      }
    };
    adder.accept(SupportLevel.Feature.MEMORY_HEAP_DUMP, lazyHeapDumpRecordingOption.getValue());
    if (isNativeAllocationSamplingEnabled()) {
      adder.accept(SupportLevel.Feature.MEMORY_NATIVE_RECORDING, lazyNativeRecordingOption.getValue());
    }
    adder.accept(SupportLevel.Feature.MEMORY_JVM_RECORDING, lazyJavaKotlinAllocationsRecordingOption.getValue());

    // Update statuses after recording options model has been initialized
    updateAllocationTrackingStatus();
    updateNativeAllocationTrackingStatus();

    // Register recording screen model updatable so timer can update on tick.
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled() && myRecordingScreenModel != null) {
      getStudioProfilers().getUpdater().register(myRecordingScreenModel);
    }
  }

  @Override
  public void exit() {
    super.exit();
    enableSelectLatestCapture(false, null);
    selectCaptureDuration(null, null);
    // Deregister recording screen model updatable so timer does not continue in background.
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled() && myRecordingScreenModel != null) {
      getStudioProfilers().getUpdater().unregister(myRecordingScreenModel);
    }
  }

  @NotNull
  @Override
  public List<DurationDataModel<CaptureDurationData<? extends CaptureObject>>> getCaptureSeries() {
    return Arrays.asList(myAllocationDurations, myHeapDumpDurations, myNativeAllocationDurations);
  }

  @Override
  protected void selectCaptureFromSelectionRange() {
    if (!getUpdateCaptureOnSelection()) {
      return;
    }

    setUpdateCaptureOnSelection(false);
    Range selectionRange = getTimeline().getSelectionRange();
    selectCaptureDuration(getIntersectingCaptureDuration(selectionRange), SwingUtilities::invokeLater);
    setUpdateCaptureOnSelection(true);
  }

  public boolean isMemoryCaptureOnly() {
    return myIsMemoryCaptureOnly;
  }

  @Override
  protected void onCaptureToSelect(SeriesData<CaptureDurationData<? extends CaptureObject>> captureToSelect, @NotNull Executor loadJoiner) {
    long x = captureToSelect.x;
    if (getHeapDumpSampleDurations().getSeries().getSeriesForRange(getTimeline().getDataRange()).stream().anyMatch(s -> s.x == x)) {
      getAspect().changed(MemoryProfilerAspect.HEAP_DUMP_FINISHED);
      LogUtils.log(getClass(), "Heap dump capture has finished");
    }
    selectCaptureDuration(captureToSelect.value, loadJoiner);
  }

  /**
   * Set the start time for pending capture object imported from hprof file.
   */
  public void setPendingCaptureStartTimeGuarded(long pendingCaptureStartTime) {
    assert myIsMemoryCaptureOnly;
    super.setPendingCaptureStartTime(pendingCaptureStartTime);
  }

  public void startHeapDumpCapture() {
    startMemoryRecording(lazyHeapDumpRecordingOption.getValue());
  }

  public void startNativeAllocationCapture() {
    startMemoryRecording(lazyNativeRecordingOption.getValue());
  }

  public void startJavaKotlinAllocationCapture() {
    startMemoryRecording(lazyJavaKotlinAllocationsRecordingOption.getValue());
  }

  private void startMemoryRecording(RecordingOption recordingOption) {
    getRecordingOptionsModel().selectBuiltInOption(recordingOption);
    getRecordingOptionsModel().start();
  }

  public void stopMemoryRecording() {
    getRecordingOptionsModel().stop();
  }

  private void startNativeAllocationTracking() {
    IdeProfilerServices ide = getStudioProfilers().getIdeServices();
    ide.getFeatureTracker().trackRecordAllocations();
    Common.Process process = getStudioProfilers().getProcess();
    String traceFilePath = String.format(Locale.getDefault(), "%s/%s.trace", DAEMON_DEVICE_DIR_PATH, process.getName());

    Trace.TraceConfiguration configuration = Trace.TraceConfiguration.newBuilder()
      .setAbiCpuArch(
        TransportFileManager.getShortAbiName(getStudioProfilers().getDevice().getCpuAbi()))
      .setTempPath(traceFilePath)
      .setAppName(process.getName())
      .setPerfettoOptions(
        PerfettoTraceConfigBuilders.INSTANCE.getMemoryTraceConfig(process.getName(), ide.getNativeAllocationsMemorySamplingRate()))
      .build();

    Commands.Command dumpCommand = Commands.Command.newBuilder()
      .setStreamId(getSessionData().getStreamId())
      .setPid(getSessionData().getPid())
      .setType(Commands.Command.CommandType.START_TRACE)
      .setStartTrace(Trace.StartTrace.newBuilder()
                       .setProfilerType(Trace.ProfilerType.MEMORY)
                       // Note: This will use the config for the one that is loaded (in the drop down) vs the one used to launch
                       // the app.
                       .setConfiguration(configuration))
      .build();

    getStudioProfilers().getClient().executeAsync(dumpCommand, ide.getPoolExecutor())
      .thenAcceptAsync(response -> {
        TransportEventListener statusListener = new TransportEventListener(Common.Event.Kind.TRACE_STATUS,
                                                                           getStudioProfilers().getIdeServices().getMainExecutor(),
                                                                           event -> event.getCommandId() == response.getCommandId(),
                                                                           () -> getSessionData().getStreamId(),
                                                                           () -> getSessionData().getPid(),
                                                                           event -> {
                                                                             if (event.getTraceStatus().hasTraceStartStatus()) {
                                                                               // trace status event is a start tracing event
                                                                               nativeAllocationTrackingStart(event.getTraceStatus()
                                                                                                               .getTraceStartStatus());
                                                                             }
                                                                             else {
                                                                               // unknown/undefined trace status event found
                                                                               getLogger().error("Invalid trace status event received.");
                                                                             }
                                                                             // unregisters the listener.
                                                                             return true;
                                                                           });
        getStudioProfilers().getTransportPoller().registerListener(statusListener);
      }, ide.getPoolExecutor());
  }

  private void stopNativeAllocationTracking() {
    Trace.TraceConfiguration configuration = Trace.TraceConfiguration.newBuilder()
      .setAppName(getStudioProfilers().getProcess().getName())
      .setAbiCpuArch(
        TransportFileManager.getShortAbiName(getStudioProfilers().getDevice().getCpuAbi()))
      .setInitiationType(Trace.TraceInitiationType.INITIATED_BY_UI)
      .setPerfettoOptions(PerfettoConfig.TraceConfig.getDefaultInstance())
      .build();

    Commands.Command dumpCommand = Commands.Command.newBuilder()
      .setStreamId(getSessionData().getStreamId())
      .setPid(getSessionData().getPid())
      .setSessionId(getSessionData().getSessionId())
      .setType(Commands.Command.CommandType.STOP_TRACE)
      .setStopTrace(Trace.StopTrace.newBuilder()
                      .setProfilerType(Trace.ProfilerType.MEMORY)
                      .setConfiguration(configuration))
      .build();

    getStudioProfilers().getClient().executeAsync(dumpCommand, getStudioProfilers().getIdeServices().getPoolExecutor())
      .thenAcceptAsync(response -> {
        TransportEventListener statusListener = new TransportEventListener(Common.Event.Kind.TRACE_STATUS,
                                                                           getStudioProfilers().getIdeServices().getMainExecutor(),
                                                                           event -> event.getCommandId() == response.getCommandId(),
                                                                           () -> getSessionData().getStreamId(),
                                                                           () -> getSessionData().getPid(),
                                                                           event -> {
                                                                             if (event.getTraceStatus().hasTraceStopStatus()) {
                                                                               // trace status event is a stop tracing event
                                                                               nativeAllocationTrackingStop(
                                                                                 event.getTraceStatus().getTraceStopStatus());
                                                                             }
                                                                             else {
                                                                               // unknown/undefined trace status event found
                                                                               getLogger().error("Invalid trace status event received.");
                                                                             }
                                                                             // unregisters the listener.
                                                                             return true;
                                                                           });
        getStudioProfilers().getTransportPoller().registerListener(statusListener);
      }, getStudioProfilers().getIdeServices().getPoolExecutor());
  }

  @VisibleForTesting
  public void toggleNativeAllocationTracking() {
    if (!myNativeAllocationTracking) {
      assert getStudioProfilers().getProcess() != null;
      startNativeAllocationTracking();
    }
    else {
      // Not asserting on `getStudioProfilers().getProcess()` because it would be null if the user stops the session
      // before stopping native allocation tracking first.
      stopNativeAllocationTracking();
    }
  }

  /**
   * Handles start tracing status events received by the transport event listener.
   */
  @VisibleForTesting
  void nativeAllocationTrackingStart(@NotNull Trace.TraceStartStatus status) {
    switch (status.getStatus()) {
      case SUCCESS:
        LogUtils.log(getClass(), "Native allocations capture start succeeded");
        myNativeAllocationTracking = true;
        setModelToRecordingNative();
        setPendingCaptureStartTime(status.getStartTimeNs());
        setTrackingAllocations(true);
        myPendingLegacyAllocationStartTimeNs = status.getStartTimeNs();
        getTimeline().setStreaming(true);
        break;
      case FAILURE:
        getLogger().error("Failure with error code " + status.getErrorCode());
        if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled()) {
          TaskEventTrackerUtils.trackStartTaskFailed(getStudioProfilers(),
                                                     getStudioProfilers().getSessionsManager().isSessionAlive(),
                                                     new TaskStartFailedMetadata(status, null, null)
          );
        }
        break;
      case UNSPECIFIED:
        break;
    }
  }

  /**
   * Handles stop tracing status events received by the transport event listener.
   */
  @VisibleForTesting
  void nativeAllocationTrackingStop(@NotNull Trace.TraceStopStatus status) {
    // Whether the stop was successful or resulted in failure, call setFinished() to indicate
    // that the recording has stopped so that the user is able to start a new capture.
    myRecordingOptionsModel.setFinished();

    switch (status.getStatus()) {
      case SUCCESS:
        LogUtils.log(getClass(), "Native allocations capture stop succeeded");
        // stop allocation tracing
        myNativeAllocationTracking = false;
        setTrackingAllocations(false);
        break;
      case NO_ONGOING_PROFILING:
        // TODO: Integrate the ongoing profile detection cpu profiler has with memory profiler
        break;
      default:
        getLogger().error(status.getErrorMessage());
        if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled()) {
          TaskEventTrackerUtils.trackStopTaskFailed(getStudioProfilers(), getStudioProfilers().getSessionsManager().isSessionAlive(),
                                                    new TaskStopFailedMetadata(status, null, null));
        }
        break;
    }
  }

  private void setModelToRecordingNative() {
    RecordingOption option = getRecordingOptionsModel().getBuiltInOptions().stream()
      .filter(opt -> opt.getTitle().equals(RECORD_NATIVE_TEXT)).findFirst().orElse(null);
    if (option != null) {
      getRecordingOptionsModel().selectBuiltInOption(option);
      getRecordingOptionsModel().setRecording();
    }
  }

  public void requestHeapDump() {
    assert getStudioProfilers().getProcess() != null;
    Commands.Command dumpCommand = Commands.Command.newBuilder()
      .setStreamId(getSessionData().getStreamId())
      .setPid(getSessionData().getPid())
      .setSessionId(getSessionData().getSessionId())
      .setType(Commands.Command.CommandType.HEAP_DUMP)
      .build();
    CompletableFuture.runAsync(() -> {
      Transport.ExecuteResponse response = getStudioProfilers().getClient().getTransportClient().execute(
        Transport.ExecuteRequest.newBuilder().setCommand(dumpCommand).build());
      TransportEventListener statusListener = new TransportEventListener(Common.Event.Kind.MEMORY_HEAP_DUMP_STATUS,
                                                                         getStudioProfilers().getIdeServices().getMainExecutor(),
                                                                         event -> event.getCommandId() == response.getCommandId(),
                                                                         () -> getSessionData().getStreamId(),
                                                                         () -> getSessionData().getPid(),
                                                                         event -> {
                                                                           handleHeapDumpStart(
                                                                             event.getMemoryHeapdumpStatus().getStatus());
                                                                           // unregisters the listener.
                                                                           return true;
                                                                         });
      getStudioProfilers().getTransportPoller().registerListener(statusListener);
    }, getStudioProfilers().getIdeServices().getPoolExecutor());
    getTimeline().setStreaming(true);
    getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_MEMORY_CAPTURE, true);
  }

  private void handleHeapDumpStart(@NotNull Memory.HeapDumpStatus status) {
    switch (status.getStatus()) {
      case SUCCESS:
        setPendingCaptureStartTime(status.getStartTime());
        getAspect().changed(MemoryProfilerAspect.HEAP_DUMP_STARTED);
        LogUtils.log(getClass(), "Heap dump capture start succeeded");
        break;
      case IN_PROGRESS:
        getLogger().debug(String.format(Locale.getDefault(), "A heap dump for %d is already in progress.", getSessionData().getPid()));
        break;
      case UNSPECIFIED:
      case NOT_PROFILING:
      case FAILURE_UNKNOWN:
      case UNRECOGNIZED:
        if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled()) {
          TaskEventTrackerUtils.trackStartTaskFailed(getStudioProfilers(), getStudioProfilers().getSessionsManager().isSessionAlive(),
                                                     new TaskStartFailedMetadata(null, null, status));
        }
        break;
    }
  }

  public DurationDataModel<CaptureDurationData<? extends CaptureObject>> getHeapDumpSampleDurations() {
    return myHeapDumpDurations;
  }

  /**
   * @param enable whether to enable or disable allocation tracking.
   * @return the actual status, which may be different from the input
   */
  public void trackAllocations(boolean enable) {
    MemoryProfiler.trackAllocations(getStudioProfilers(), getSessionData(), enable, true, status -> {
      switch (status.getStatus()) {
        case SUCCESS:
          setTrackingAllocations(enable);
          setPendingCaptureStartTime(status.getStartTime());
          myPendingLegacyAllocationStartTimeNs = enable ? status.getStartTime() : INVALID_START_TIME;
          break;
        case IN_PROGRESS:
          setTrackingAllocations(true);
          break;
        case NOT_ENABLED:
          setTrackingAllocations(false);
          break;
        default:
          if (getStudioProfilers().getIdeServices().getFeatureConfig().isTaskBasedUxEnabled()) {
            if (enable) {
              // Start task failure
              TaskEventTrackerUtils.trackStartTaskFailed(
                getStudioProfilers(),
                getStudioProfilers().getSessionsManager().isSessionAlive(),
                new TaskStartFailedMetadata(null, status, null));
            }
            else {
              // Stop task failure
              TaskEventTrackerUtils.trackStopTaskFailed(
                getStudioProfilers(),
                getStudioProfilers().getSessionsManager().isSessionAlive(),
                new TaskStopFailedMetadata(null, status, null));
            }
          }
          break;
      }
      getAspect().changed(MemoryProfilerAspect.TRACKING_ENABLED);

      if (isTrackingAllocations()) {
        getTimeline().setStreaming(true);
        getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_MEMORY_CAPTURE, true);
      }
    });
  }

  public long getAllocationTrackingElapsedTimeNs() {
    if (isTrackingAllocations()) {
      try {
        TimeResponse timeResponse = getStudioProfilers().getClient().getTransportClient()
          .getCurrentTime(TimeRequest.newBuilder().setStreamId(getSessionData().getStreamId()).build());
        return timeResponse.getTimestampNs() - myPendingLegacyAllocationStartTimeNs;
      }
      catch (StatusRuntimeException exception) {
        getLogger().warn(exception);
      }
    }
    return INVALID_START_TIME;
  }

  public boolean isNativeAllocationSamplingEnabled() {
    Common.Device device = getDeviceForSelectedSession();
    return device != null && device.getFeatureLevel() >= AndroidVersion.VersionCodes.Q;
  }

  @NotNull
  public DurationDataModel<CaptureDurationData<? extends CaptureObject>> getAllocationInfosDurations() {
    return myAllocationDurations;
  }

  @NotNull
  public DurationDataModel<CaptureDurationData<? extends CaptureObject>> getNativeAllocationInfosDurations() {
    return myNativeAllocationDurations;
  }

  @Override
  @NotNull
  public Runnable getStopAction() {
    return myStopAction;
  }

  @Nullable
  @Override
  public RecordingScreenModel<MainMemoryProfilerStage> getRecordingScreenModel() {
    return myRecordingScreenModel;
  }

  @VisibleForTesting
  public void selectCaptureDuration(@Nullable CaptureDurationData<? extends CaptureObject> durationData,
                                    @Nullable Executor joiner) {
    StudioProfilers profilers = getStudioProfilers();
    if (durationData instanceof AllocationDurationData) {
      profilers.setStage(AllocationStage.makeStaticStage(profilers,
                                                         ((AllocationDurationData<?>)durationData).getStart(),
                                                         ((AllocationDurationData<?>)durationData).getEnd()));
    }
    else if (durationData != null &&
             (HeapDumpCaptureObject.class.isAssignableFrom(durationData.getCaptureObjectType()) ||
              NativeAllocationSampleCaptureObject.class.isAssignableFrom(durationData.getCaptureObjectType()) ||
              LegacyAllocationCaptureObject.class.isAssignableFrom(durationData.getCaptureObjectType()))) {
      profilers.setStage(new MemoryCaptureStage(profilers, getLoader(), durationData, joiner));
    }
    else {
      doSelectCaptureDuration(durationData, joiner);
    }
  }

  private void updateAllocationTrackingStatus() {
    List<AllocationsInfo> allocationsInfos = MemoryProfiler.getAllocationInfosForSession(getStudioProfilers().getClient(),
                                                                                         getSessionData(),
                                                                                         new Range(Long.MIN_VALUE, Long.MAX_VALUE));
    AllocationsInfo lastInfo = allocationsInfos.isEmpty() ? null : allocationsInfos.get(allocationsInfos.size() - 1);
    setTrackingAllocations(lastInfo != null && (lastInfo.getLegacy() && lastInfo.getEndTime() == Long.MAX_VALUE));
    if (isTrackingAllocations()) {
      setPendingCaptureStartTime(lastInfo.getStartTime());
      myPendingLegacyAllocationStartTimeNs = lastInfo.getStartTime();
    }
    else {
      setPendingCaptureStartTime(INVALID_START_TIME);
      myPendingLegacyAllocationStartTimeNs = INVALID_START_TIME;
    }
  }

  private void updateNativeAllocationTrackingStatus() {
    List<Common.Event> events =
      MemoryProfiler.getNativeHeapEventsForSessionSortedByTimestamp(getStudioProfilers().getClient(), getSessionData(),
                                                                    new Range(Long.MIN_VALUE, Long.MAX_VALUE));
    if (events.isEmpty()) {
      return;
    }
    Trace.TraceStartStatus lastStartStatus = events.get(events.size() - 1).getTraceStatus().getTraceStartStatus();
    // If there is an ongoing recording.
    if (lastStartStatus.getStatus() == Trace.TraceStartStatus.Status.SUCCESS) {
      nativeAllocationTrackingStart(lastStartStatus);
    }
  }

  private static RecordingOption makeToggleOption(String title, String desc, Runnable toggle) {
    return new RecordingOption(title, desc, toggle, toggle);
  }

  public static boolean canSafelyLoadHprof(long fileSize) {
    System.gc(); // To avoid overly conservative estimation of free memory
    long leeway = 300 * 1024 * 1024; // Studio needs ~300MB to run without major freezes
    long requestableMemory = Runtime.getRuntime().maxMemory() -
                             Runtime.getRuntime().totalMemory() +
                             Runtime.getRuntime().freeMemory();
    return requestableMemory >= MEMORY_HPROF_SAFE_FACTOR * fileSize + leeway;
  }
}