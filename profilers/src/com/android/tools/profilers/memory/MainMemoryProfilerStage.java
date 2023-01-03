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
import com.android.tools.idea.transport.TransportFileManager;
import com.android.tools.idea.transport.poller.TransportEventListener;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpResponse;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.RecordingOption;
import com.android.tools.profilers.RecordingOptionsModel;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.SupportLevel;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject;
import com.android.tools.profilers.memory.adapters.NativeAllocationSampleCaptureObject;
import com.android.tools.profilers.perfetto.config.PerfettoTraceConfigBuilders;
import com.android.tools.profilers.sessions.SessionAspect;
import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import perfetto.protos.PerfettoConfig;

public class MainMemoryProfilerStage extends BaseStreamingMemoryProfilerStage {
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

  public MainMemoryProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CaptureObjectLoader());
  }

  public MainMemoryProfilerStage(@NotNull StudioProfilers profilers, @NotNull CaptureObjectLoader loader) {
    super(profilers, loader);
    myIsMemoryCaptureOnly =
      profilers.getSessionsManager().getSelectedSessionMetaData().getType() == Common.SessionMetaData.SessionType.MEMORY_CAPTURE;

    // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
    myHeapDumpDurations = makeModel(CaptureDataSeries::ofHeapDumpSamples);
    myAllocationDurations = makeModel(CaptureDataSeries::ofAllocationInfos);
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
    super.enter();

    BiConsumer<SupportLevel.Feature, RecordingOption> adder = (feature, option) -> {
      myRecordingOptionsModel.addBuiltInOptions(option);
      if (!getStudioProfilers().getSelectedSessionSupportLevel().isFeatureSupported(feature)) {
        myRecordingOptionsModel.setOptionNotReady(option, feature.getTitle() + " is not supported for profileable processes");
      }
    };
    adder.accept(SupportLevel.Feature.MEMORY_HEAP_DUMP, makeHeapDumpOption());
    if (isNativeAllocationSamplingEnabled()) {
      adder.accept(SupportLevel.Feature.MEMORY_NATIVE_RECORDING, makeNativeRecordingOption());
    }
    RecordingOption javaRecordingOption = makeJavaRecodingOption();
    adder.accept(SupportLevel.Feature.MEMORY_JVM_RECORDING, javaRecordingOption);

    // Update statuses after recording options model has been initialized
    updateAllocationTrackingStatus();
    updateNativeAllocationTrackingStatus();
  }

  @Override
  public void exit() {
    super.exit();
    enableSelectLatestCapture(false, null);
    selectCaptureDuration(null, null);
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
      .setPerfettoOptions(PerfettoTraceConfigBuilders.INSTANCE.getMemoryTraceConfig(process.getName(),
                                                                                    ide.getNativeMemorySamplingRateForCurrentConfig()))
      .build();

    Commands.Command dumpCommand = Commands.Command.newBuilder()
      .setStreamId(getSessionData().getStreamId())
      .setPid(getSessionData().getPid())
      .setType(Commands.Command.CommandType.START_NATIVE_HEAP_SAMPLE)
      .setStartNativeSample(Memory.StartNativeSample.newBuilder()
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
      .setType(Commands.Command.CommandType.STOP_NATIVE_HEAP_SAMPLE)
      .setStopNativeSample(Memory.StopNativeSample.newBuilder()
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
        myNativeAllocationTracking = true;
        setModelToRecordingNative();
        setPendingCaptureStartTime(status.getStartTimeNs());
        setTrackingAllocations(true);
        myPendingLegacyAllocationStartTimeNs = status.getStartTimeNs();
        getTimeline().setStreaming(true);
        break;
      case FAILURE:
        getLogger().error(status.getErrorMessage());
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
    switch (status.getStatus()) {
      case SUCCESS:
        // stop allocation tracing
        myNativeAllocationTracking = false;
        setTrackingAllocations(false);
        break;
      case OTHER_FAILURE:
        // other_failure encompasses all non-explicit defined failure statuses
        getLogger().error(status.getErrorMessage());
        break;
      case NO_ONGOING_PROFILING:
        // TODO: Integrate the ongoing profile detection cpu profiler has with memory profiler
        break;
      default:
        // handle explicitly defined failure statuses
        handleNativeAllocationStopFailures(status);
        break;
    }
  }

  /**
   * Placeholder method to handle failure statuses that will be reported as metadata.
   * TODO: Merge this method with "fromStopStatus" in CpuCaptureMetadata.java
   */
  private void handleNativeAllocationStopFailures(@NotNull Trace.TraceStopStatus status) {
    switch (status.getStatus()) {
      case UNSPECIFIED:
      case APP_PROCESS_DIED:
      case APP_PID_CHANGED:
      case PROFILER_PROCESS_DIED:
      case STOP_COMMAND_FAILED:
      case STILL_PROFILING_AFTER_STOP:
      case CANNOT_START_WAITING:
      case WAIT_TIMEOUT:
      case WAIT_FAILED:
      case CANNOT_READ_WAIT_EVENT:
      case CANNOT_COPY_FILE:
      case CANNOT_FORM_FILE:
      case CANNOT_READ_FILE:
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
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      assert getStudioProfilers().getProcess() != null;
      Commands.Command dumpCommand = Commands.Command.newBuilder()
        .setStreamId(getSessionData().getStreamId())
        .setPid(getSessionData().getPid())
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
    }
    else {
      TriggerHeapDumpResponse response =
        getClient().triggerHeapDump(TriggerHeapDumpRequest.newBuilder().setSession(getSessionData()).build());
      handleHeapDumpStart(response.getStatus());
    }

    getTimeline().setStreaming(true);
    getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_MEMORY_CAPTURE, true);
  }

  private void handleHeapDumpStart(@NotNull Memory.HeapDumpStatus status) {
    switch (status.getStatus()) {
      case SUCCESS:
        setPendingCaptureStartTime(status.getStartTime());
        getAspect().changed(MemoryProfilerAspect.HEAP_DUMP_STARTED);
        break;
      case IN_PROGRESS:
        getLogger().debug(String.format(Locale.getDefault(), "A heap dump for %d is already in progress.", getSessionData().getPid()));
        break;
      case UNSPECIFIED:
      case NOT_PROFILING:
      case FAILURE_UNKNOWN:
      case UNRECOGNIZED:
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
    MemoryProfiler.trackAllocations(getStudioProfilers(), getSessionData(), enable, status -> {
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
        case UNSPECIFIED:
        case NOT_PROFILING:
        case FAILURE_UNKNOWN:
        case UNRECOGNIZED:
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
              NativeAllocationSampleCaptureObject.class.isAssignableFrom(durationData.getCaptureObjectType()))) {
      profilers.setStage(new MemoryCaptureStage(profilers, getLoader(), durationData, joiner));
    }
    else {
      doSelectCaptureDuration(durationData, joiner);
    }
  }

  private void updateAllocationTrackingStatus() {
    List<AllocationsInfo> allocationsInfos = MemoryProfiler.getAllocationInfosForSession(getStudioProfilers().getClient(),
                                                                                         getSessionData(),
                                                                                         new Range(Long.MIN_VALUE, Long.MAX_VALUE),
                                                                                         getStudioProfilers().getIdeServices());
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
    List<Trace.TraceStatusData> samples = MemoryProfiler
      .getNativeHeapStatusForSession(getStudioProfilers().getClient(), getSessionData(), new Range(Long.MIN_VALUE, Long.MAX_VALUE));
    if (samples.isEmpty()) {
      return;
    }
    Trace.TraceStartStatus lastStartStatus = samples.get(samples.size() - 1).getTraceStartStatus();
    // If there is an ongoing recording.
    if (lastStartStatus.getStatus() == Trace.TraceStartStatus.Status.SUCCESS) {
      nativeAllocationTrackingStart(lastStartStatus);
    }
  }

  private RecordingOption makeHeapDumpOption() {
    return new RecordingOption(CAPTURE_HEAP_DUMP_TEXT, HEAP_DUMP_TOOLTIP, () -> {
      requestHeapDump();
      getStudioProfilers().getIdeServices().getFeatureTracker().trackDumpHeap();
    });
  }

  private RecordingOption makeNativeRecordingOption() {
    return makeToggleOption(RECORD_NATIVE_TEXT, RECORD_NATIVE_DESC, this::toggleNativeAllocationTracking);
  }

  private RecordingOption makeJavaRecodingOption() {
    Runnable toggle =
      isLiveAllocationTrackingSupported() ?
      // post-O
      () -> getStudioProfilers().setStage(AllocationStage.makeLiveStage(getStudioProfilers())) :
      // legacy
      () -> {
        if (isTrackingAllocations()) {
          getStudioProfilers().getIdeServices().getFeatureTracker().trackRecordAllocations();
        }
        trackAllocations(!isTrackingAllocations());
      };
    return makeToggleOption(RECORD_JAVA_TEXT, RECORD_JAVA_TOOLTIP, toggle);
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