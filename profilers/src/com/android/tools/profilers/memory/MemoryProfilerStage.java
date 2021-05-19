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
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.idea.transport.poller.TransportEventListener;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData;
import com.android.tools.profiler.proto.MemoryProfiler.ForceGarbageCollectionRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ForceGarbageCollectionResponse;
import com.android.tools.profiler.proto.MemoryProfiler.SetAllocationSamplingRateRequest;
import com.android.tools.profiler.proto.MemoryProfiler.SetAllocationSamplingRateResponse;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryProfilerStage extends BaseMemoryProfilerStage implements CodeNavigator.Listener {
  private static final String HAS_USED_MEMORY_CAPTURE = "memory.used.capture";
  public static final String LIVE_ALLOCATION_SAMPLING_PREF = "memory.live.allocation.mode";
  public static final LiveAllocationSamplingMode DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE = LiveAllocationSamplingMode.SAMPLED;

  static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 5, 5);
  static final BaseAxisFormatter OBJECT_COUNT_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 5, "");

  // The safe factor estimating how many times of memory is needed compared to hprof file size
  public static final int MEMORY_HPROF_SAFE_FACTOR =
    Math.max(1, Math.min(Integer.getInteger("profiler.memory.hprof.safeFactor", 10), 1000));

  private static Logger getLogger() {
    return Logger.getInstance(MemoryProfilerStage.class);
  }

  private final DetailedMemoryUsage myDetailedMemoryUsage;
  private final ClampedAxisComponentModel myMemoryAxis;
  private final ClampedAxisComponentModel myObjectsAxis;
  private final MemoryStageLegends myLegends;
  private final MemoryStageLegends myTooltipLegends;
  private final EaseOutModel myInstructionsEaseOutModel;

  /**
   * Whether the stage only contains heap dump data imported from hprof file
   */
  private final boolean myIsMemoryCaptureOnly;

  @NotNull
  private final Common.Session mySessionData;
  private DurationDataModel<GcDurationData> myGcStatsModel;

  @NotNull
  private AspectModel<MemoryProfilerAspect> myAspect = new AspectModel<>();

  private final MemoryServiceBlockingStub myClient;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myHeapDumpDurations;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myAllocationDurations;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myNativeAllocationDurations;
  private final EventMonitor myEventMonitor;
  private final RangeSelectionModel myRangeSelectionModel;
  private boolean myTrackingAllocations;
  private final CaptureElapsedTimeUpdatable myCaptureElapsedTimeUpdatable = new CaptureElapsedTimeUpdatable();
  private long myPendingLegacyAllocationStartTimeNs = BaseMemoryProfilerStage.INVALID_START_TIME;
  private boolean myNativeAllocationTracking = false;

  @NotNull private final AllocationSamplingRateDataSeries myAllocationSamplingRateDataSeries;
  @NotNull private final DurationDataModel<AllocationSamplingRateDurationData> myAllocationSamplingRateDurations;
  @NotNull private final AllocationSamplingRateUpdatable myAllocationSamplingRateUpdatable;
  @NotNull private LiveAllocationSamplingMode myLiveAllocationSamplingMode;

  public MemoryProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CaptureObjectLoader());
  }

  public MemoryProfilerStage(@NotNull StudioProfilers profilers, @NotNull CaptureObjectLoader loader) {
    super(profilers, loader);
    myIsMemoryCaptureOnly =
      profilers.getSessionsManager().getSelectedSessionMetaData().getType() == Common.SessionMetaData.SessionType.MEMORY_CAPTURE;
    mySessionData = profilers.getSession();
    myClient = profilers.getClient().getMemoryClient();

    FeatureTracker featureTracker = getStudioProfilers().getIdeServices().getFeatureTracker();
    HeapDumpSampleDataSeries heapDumpSeries =
      new HeapDumpSampleDataSeries(profilers.getClient(), mySessionData, featureTracker, getStudioProfilers().getIdeServices());
    AllocationInfosDataSeries allocationSeries =
      new AllocationInfosDataSeries(profilers.getClient(), mySessionData, featureTracker, this);
    NativeAllocationSamplesSeries nativeSampleSeries =
      new NativeAllocationSamplesSeries(profilers.getClient(), mySessionData, featureTracker, this);

    Range viewRange = getTimeline().getViewRange();
    // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
    myHeapDumpDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, heapDumpSeries));
    myAllocationDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, allocationSeries));
    myNativeAllocationDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, nativeSampleSeries));

    DataSeries<GcDurationData> gcSeries =
      getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled() ?
      new UnifiedEventDataSeries<>(getStudioProfilers().getClient().getTransportClient(),
                                   mySessionData.getStreamId(),
                                   mySessionData.getPid(),
                                   Common.Event.Kind.MEMORY_GC,
                                   UnifiedEventDataSeries.DEFAULT_GROUP_ID,
                                   events -> ContainerUtil
                                     .map(events, evt -> new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(evt.getTimestamp()),
                                                                          new GcDurationData(
                                                                            TimeUnit.NANOSECONDS
                                                                              .toMicros(evt.getMemoryGc().getDuration()))))) :
      new LegacyGcStatsDataSeries(myClient, mySessionData);
    myGcStatsModel = new DurationDataModel<>(new RangedSeries<>(viewRange, gcSeries));
    myAllocationSamplingRateDataSeries =
      new AllocationSamplingRateDataSeries(getStudioProfilers().getClient(),
                                           mySessionData,
                                           getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled());
    myAllocationSamplingRateDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, myAllocationSamplingRateDataSeries));
    myDetailedMemoryUsage = new DetailedMemoryUsage(profilers, this);

    myHeapDumpDurations.setRenderSeriesPredicate((data, series) ->
                                                   // Do not show the object series during a heap dump.
                                                   !series.getName().equals(myDetailedMemoryUsage.getObjectsSeries().getName())
    );
    myGcStatsModel.setAttachedSeries(myDetailedMemoryUsage.getObjectsSeries(), Interpolatable.SegmentInterpolator);
    myGcStatsModel.setAttachPredicate(
      // Only attach to the object series if live allocation is disabled or the gc event happens within full-tracking mode.
      data -> !isLiveAllocationTrackingReady() ||
              MemoryProfiler.hasOnlyFullAllocationTrackingWithinRegion(getStudioProfilers(), mySessionData, data.x, data.x));
    myAllocationSamplingRateDurations.setAttachedSeries(myDetailedMemoryUsage.getObjectsSeries(), Interpolatable.SegmentInterpolator);
    myAllocationSamplingRateDurations.setAttachPredicate(
      data ->
        // The DurationData should attach to the Objects series at both the start and end of the FULL tracking mode region.
        (data.value.getPreviousRate() != null &&
         data.value.getPreviousRate().getSamplingNumInterval() == LiveAllocationSamplingMode.FULL.getValue()) ||
        data.value.getCurrentRate().getSamplingNumInterval() == LiveAllocationSamplingMode.FULL.getValue()
    );
    myAllocationSamplingRateDurations.setRenderSeriesPredicate(
      (data, series) ->
        // Only show the object series if live allocation is not enabled or if the current sampling rate is FULL.
        !series.getName().equals(myDetailedMemoryUsage.getObjectsSeries().getName()) ||
        (!isLiveAllocationTrackingReady() ||
         data.value.getCurrentRate().getSamplingNumInterval() == LiveAllocationSamplingMode.FULL.getValue())
    );
    myAllocationSamplingRateUpdatable = new AllocationSamplingRateUpdatable();

    myMemoryAxis = new ClampedAxisComponentModel.Builder(myDetailedMemoryUsage.getMemoryRange(), MEMORY_AXIS_FORMATTER).build();
    myObjectsAxis = new ClampedAxisComponentModel.Builder(myDetailedMemoryUsage.getObjectsRange(), OBJECT_COUNT_AXIS_FORMATTER).build();

    myLegends = new MemoryStageLegends(this, getTimeline().getDataRange(), false);
    myTooltipLegends = new MemoryStageLegends(this, getTimeline().getTooltipRange(), true);

    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);

    myEventMonitor = new EventMonitor(profilers);

    // Get ready to fire LIVE_ALLOCATION_STATUS if applicable.
    if (getStudioProfilers().getSessionsManager().isSessionAlive() && isLiveAllocationTrackingSupported()) {
      // Note the max of current data range as isLiveAllocationTrackingReady() returns info before it.
      long currentRangeMax = TimeUnit.MICROSECONDS.toNanos((long)profilers.getTimeline().getDataRange().getMax());
      if (!isLiveAllocationTrackingReady()) {
        TransportEventListener listener = new TransportEventListener(
          Common.Event.Kind.MEMORY_ALLOC_SAMPLING, getStudioProfilers().getIdeServices().getMainExecutor(),
          event -> true, () -> mySessionData.getStreamId(), () -> mySessionData.getPid(), null,
          // wait for only new events, not old ones such as those from previous sessions
          () -> currentRangeMax,
          event -> {
            myAspect.changed(MemoryProfilerAspect.LIVE_ALLOCATION_STATUS);
            // unregisters the listener.
            return true;
          });
        getStudioProfilers().getTransportPoller().registerListener(listener);
      }
    }

    myRangeSelectionModel = new RangeSelectionModel(getTimeline().getSelectionRange(), getTimeline().getViewRange());
    myRangeSelectionModel.addConstraint(myAllocationDurations);
    myRangeSelectionModel.addConstraint(myNativeAllocationDurations);
    myRangeSelectionModel.addConstraint(myHeapDumpDurations);
    myRangeSelectionModel.addListener(new RangeSelectionListener() {
      @Override
      public void selectionCreated() {
        selectCaptureFromSelectionRange();
        profilers.getIdeServices().getFeatureTracker().trackSelectRange();
        profilers.getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_MEMORY_CAPTURE, true);
        myInstructionsEaseOutModel.setCurrentPercentage(1);
      }

      @Override
      public void selectionCleared() {
        selectCaptureFromSelectionRange();
      }
    });

    // Set the sampling mode based on the last user setting. If the current session (either alive or dead) has a different sampling setting,
    // It will be set properly in the AllocationSamplingRateUpdatable.
    myLiveAllocationSamplingMode = LiveAllocationSamplingMode.getModeFromFrequency(
      profilers.getIdeServices().getPersistentProfilerPreferences()
        .getInt(LIVE_ALLOCATION_SAMPLING_PREF, DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE.getValue())
    );
    myAllocationSamplingRateUpdatable.update(0);
    getStudioProfilers().getSessionsManager().addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION, this::stopRecordingOnSessionStop);
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

  public DetailedMemoryUsage getDetailedMemoryUsage() {
    return myDetailedMemoryUsage;
  }

  @Override
  public void enter() {
    getLoader().start();
    myEventMonitor.enter();
    forEachUpdatable(getStudioProfilers().getUpdater()::register);

    getStudioProfilers().getIdeServices().getCodeNavigator().addListener(this);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getClass());

    updateAllocationTrackingStatus();
    updateNativeAllocationTrackingStatus();
  }

  @Override
  public void exit() {
    enableSelectLatestCapture(false, null);

    myEventMonitor.exit();
    forEachUpdatable(getStudioProfilers().getUpdater()::unregister);
    selectCaptureDuration(null, null);
    getLoader().stop();

    getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(this);

    myRangeSelectionModel.clearListeners();
  }

  private void forEachUpdatable(Consumer<Updatable> f) {
    for (Updatable u : new Updatable[]{
      myDetailedMemoryUsage,
      myHeapDumpDurations,
      myAllocationDurations,
      myNativeAllocationDurations,
      myMemoryAxis,
      myObjectsAxis,
      myGcStatsModel,
      myAllocationSamplingRateDurations,
      myCaptureElapsedTimeUpdatable,
      myAllocationSamplingRateUpdatable
    }) {
      f.accept(u);
    }
  }

  public RangeSelectionModel getRangeSelectionModel() {
    return myRangeSelectionModel;
  }

  private void selectCaptureFromSelectionRange() {
    if (!getUpdateCaptureOnSelection()) {
      return;
    }

    setUpdateCaptureOnSelection(false);
    Range selectionRange = getTimeline().getSelectionRange();
    selectCaptureDuration(getIntersectingCaptureDuration(selectionRange), SwingUtilities::invokeLater);
    setUpdateCaptureOnSelection(true);
  }

  /**
   * Toggle a behavior where if there is currently no CaptureObject selected, the model will attempt to select the next CaptureObject
   * that has been created either through {@link #trackAllocations(boolean)} or {@link #requestHeapDump()}.
   *
   * @param loadJoiner if specified, the joiner executor will be passed down to {@link CaptureObjectLoader#loadCapture(CaptureObject, Range,
   *                   Executor)} so that the load operation of the CaptureObject will be joined and the CURRENT_LOAD_CAPTURE aspect would
   *                   be fired via the desired executor.
   */
  public void enableSelectLatestCapture(boolean enable, @Nullable Executor loadJoiner) {
    if (enable) {
      getTimeline().getDataRange().addDependency(this)
        .onChange(Range.Aspect.RANGE, () -> queryAndSelectCaptureObject(loadJoiner == null ? MoreExecutors.directExecutor() : loadJoiner));
    }
    else {
      // Removing the aspect observers on Ranges.
      getTimeline().getDataRange().removeDependencies(this);
    }
  }

  public boolean isMemoryCaptureOnly() {
    return myIsMemoryCaptureOnly;
  }

  /**
   * Find a pending allocation or heap dump capture matching {@code myPendingCaptureStartTime} if no capture is currently selected.
   * Selection range will also be updated to match if the capture isn't ongoing.
   */
  private void queryAndSelectCaptureObject(@NotNull Executor loadJoiner) {
    Range dataRange = getTimeline().getDataRange();
    if (getPendingCaptureStartTime() != INVALID_START_TIME) {
      List<SeriesData<CaptureDurationData<CaptureObject>>> series =
        new ArrayList<>(getAllocationInfosDurations().getSeries().getSeriesForRange(dataRange));
      series.addAll(getHeapDumpSampleDurations().getSeries().getSeriesForRange(dataRange));
      series.addAll(getNativeAllocationInfosDurations().getSeries().getSeriesForRange(dataRange));

      long pendingCaptureStartTimeUs = TimeUnit.NANOSECONDS.toMicros(getPendingCaptureStartTime());
      SeriesData<CaptureDurationData<CaptureObject>> captureToSelect = null;
      for (int i = series.size() - 1; i >= 0; --i) {
        if (series.get(i).x == pendingCaptureStartTimeUs) {
          captureToSelect = series.get(i);
          break;
        }
      }

      if (captureToSelect != null &&
          (captureToSelect.value.getDurationUs() != Long.MAX_VALUE || captureToSelect.value.getSelectableWhenMaxDuration())) {
        long x = captureToSelect.x;
        if (getHeapDumpSampleDurations().getSeries().getSeriesForRange(dataRange).stream().anyMatch(s -> s.x == x)) {
          myAspect.changed(MemoryProfilerAspect.HEAP_DUMP_FINISHED);
        }
        selectCaptureDuration(captureToSelect.value, loadJoiner);
      }
    }
  }

  /**
   * Set the start time for pending capture object imported from hprof file.
   */
  public void setPendingCaptureStartTimeGuarded(long pendingCaptureStartTime) {
    assert myIsMemoryCaptureOnly;
    super.setPendingCaptureStartTime(pendingCaptureStartTime);
  }

  @NotNull
  public AspectModel<MemoryProfilerAspect> getAspect() {
    return myAspect;
  }

  private Transport.ExecuteResponse startNativeAllocationTracking() {
    IdeProfilerServices ide = getStudioProfilers().getIdeServices();
    ide.getFeatureTracker().trackRecordAllocations();
    getStudioProfilers().setMemoryLiveAllocationEnabled(false);
    Common.Process process = getStudioProfilers().getProcess();
    String traceFilePath = String.format(Locale.getDefault(), "%s/%s.trace", DAEMON_DEVICE_DIR_PATH, process.getName());
    Commands.Command dumpCommand = Commands.Command.newBuilder()
      .setStreamId(mySessionData.getStreamId())
      .setPid(mySessionData.getPid())
      .setType(Commands.Command.CommandType.START_NATIVE_HEAP_SAMPLE)
      .setStartNativeSample(Memory.StartNativeSample.newBuilder()
                              // Note: This will use the config for the one that is loaded (in the drop down) vs the one used to launch
                              // the app.
                              .setSamplingIntervalBytes(ide.getNativeMemorySamplingRateForCurrentConfig())
                              .setSharedMemoryBufferBytes(64 * 1024 * 1024)
                              .setAbiCpuArch(process.getAbiCpuArch())
                              .setTempPath(traceFilePath)
                              .setAppName(process.getName()))
      .build();
    return getStudioProfilers().getClient().getTransportClient().execute(
      Transport.ExecuteRequest.newBuilder().setCommand(dumpCommand).build());
  }

  private Transport.ExecuteResponse stopNativeAllocationTracking(long startTime) {
    getStudioProfilers().setMemoryLiveAllocationEnabled(true);
    Commands.Command dumpCommand = Commands.Command.newBuilder()
      .setStreamId(mySessionData.getStreamId())
      .setPid(mySessionData.getPid())
      .setType(Commands.Command.CommandType.STOP_NATIVE_HEAP_SAMPLE)
      .setStopNativeSample(Memory.StopNativeSample.newBuilder()
                             .setStartTime(startTime))
      .build();
    return getStudioProfilers().getClient().getTransportClient().execute(
      Transport.ExecuteRequest.newBuilder().setCommand(dumpCommand).build());
  }

  public void toggleNativeAllocationTracking() {
    assert getStudioProfilers().getProcess() != null;
    Transport.ExecuteResponse response;
    if (!myNativeAllocationTracking) {
      response = startNativeAllocationTracking();
    }
    else {
      response = stopNativeAllocationTracking(getPendingCaptureStartTime());
    }
    TransportEventListener statusListener = new TransportEventListener(Common.Event.Kind.MEMORY_NATIVE_SAMPLE_STATUS,
                                                                       getStudioProfilers().getIdeServices().getMainExecutor(),
                                                                       event -> event.getCommandId() == response.getCommandId(),
                                                                       () -> mySessionData.getStreamId(),
                                                                       () -> mySessionData.getPid(),
                                                                       event -> {
                                                                         nativeAllocationTrackingStart(
                                                                           event.getMemoryNativeTrackingStatus());
                                                                         // unregisters the listener.
                                                                         return true;
                                                                       });
    getStudioProfilers().getTransportPoller().registerListener(statusListener);
  }

  private void nativeAllocationTrackingStart(@NotNull Memory.MemoryNativeTrackingData status) {
    switch (status.getStatus()) {
      case SUCCESS:
        myNativeAllocationTracking = true;
        setPendingCaptureStartTime(status.getStartTime());
        myTrackingAllocations = true;
        myPendingLegacyAllocationStartTimeNs = status.getStartTime();
        break;
      case IN_PROGRESS:
        myNativeAllocationTracking = true;
        myTrackingAllocations = true;
        getLogger().debug(String.format(Locale.getDefault(), "A heap dump for %d is already in progress.", mySessionData.getPid()));
        break;
      case FAILURE:
        getLogger().error(status.getFailureMessage());
        // fall through
      case NOT_RECORDING:
      case UNSPECIFIED:
      case UNRECOGNIZED:
        myNativeAllocationTracking = false;
        myTrackingAllocations = false;
        break;
    }
    myAspect.changed(MemoryProfilerAspect.TRACKING_ENABLED);
  }

  public void requestHeapDump() {
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      assert getStudioProfilers().getProcess() != null;
      Commands.Command dumpCommand = Commands.Command.newBuilder()
        .setStreamId(mySessionData.getStreamId())
        .setPid(mySessionData.getPid())
        .setType(Commands.Command.CommandType.HEAP_DUMP)
        .build();
      Transport.ExecuteResponse response = getStudioProfilers().getClient().getTransportClient().execute(
        Transport.ExecuteRequest.newBuilder().setCommand(dumpCommand).build());
      TransportEventListener statusListener = new TransportEventListener(Common.Event.Kind.MEMORY_HEAP_DUMP_STATUS,
                                                                         getStudioProfilers().getIdeServices().getMainExecutor(),
                                                                         event -> event.getCommandId() == response.getCommandId(),
                                                                         () -> mySessionData.getStreamId(),
                                                                         () -> mySessionData.getPid(),
                                                                         event -> {
                                                                           handleHeapDumpStart(event.getMemoryHeapdumpStatus().getStatus());
                                                                           // unregisters the listener.
                                                                           return true;
                                                                         });
      getStudioProfilers().getTransportPoller().registerListener(statusListener);
    }
    else {
      TriggerHeapDumpResponse response = myClient.triggerHeapDump(TriggerHeapDumpRequest.newBuilder().setSession(mySessionData).build());
      handleHeapDumpStart(response.getStatus());
    }

    getTimeline().setStreaming(true);
    getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_MEMORY_CAPTURE, true);
    myInstructionsEaseOutModel.setCurrentPercentage(1);
  }

  private void handleHeapDumpStart(@NotNull Memory.HeapDumpStatus status) {
    switch (status.getStatus()) {
      case SUCCESS:
        setPendingCaptureStartTime(status.getStartTime());
        myAspect.changed(MemoryProfilerAspect.HEAP_DUMP_STARTED);
        break;
      case IN_PROGRESS:
        getLogger().debug(String.format(Locale.getDefault(), "A heap dump for %d is already in progress.", mySessionData.getPid()));
        break;
      case UNSPECIFIED:
      case NOT_PROFILING:
      case FAILURE_UNKNOWN:
      case UNRECOGNIZED:
        break;
    }
  }

  public void forceGarbageCollection() {
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      // TODO(b/150503095)
      Transport.ExecuteResponse response = getStudioProfilers().getClient().getTransportClient().execute(
        Transport.ExecuteRequest.newBuilder()
          .setCommand(Commands.Command.newBuilder()
                        .setStreamId(mySessionData.getStreamId())
                        .setPid(mySessionData.getPid())
                        .setType(Commands.Command.CommandType.GC))
          .build());
    }
    else {
      // TODO(b/150503095)
      ForceGarbageCollectionResponse response =
        myClient.forceGarbageCollection(ForceGarbageCollectionRequest.newBuilder().setSession(mySessionData).build());
    }
  }

  public DurationDataModel<CaptureDurationData<CaptureObject>> getHeapDumpSampleDurations() {
    return myHeapDumpDurations;
  }

  /**
   * @param enable whether to enable or disable allocation tracking.
   * @return the actual status, which may be different from the input
   */
  public void trackAllocations(boolean enable) {
    MemoryProfiler.trackAllocations(getStudioProfilers(), mySessionData, enable, status -> {
      switch (status.getStatus()) {
        case SUCCESS:
          myTrackingAllocations = enable;
          setPendingCaptureStartTime(status.getStartTime());
          myPendingLegacyAllocationStartTimeNs = enable ? status.getStartTime() : INVALID_START_TIME;
          break;
        case IN_PROGRESS:
          myTrackingAllocations = true;
          break;
        case NOT_ENABLED:
          myTrackingAllocations = false;
          break;
        case UNSPECIFIED:
        case NOT_PROFILING:
        case FAILURE_UNKNOWN:
        case UNRECOGNIZED:
          break;
      }
      myAspect.changed(MemoryProfilerAspect.TRACKING_ENABLED);

      if (myTrackingAllocations) {
        getTimeline().setStreaming(true);
        getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_MEMORY_CAPTURE, true);
        myInstructionsEaseOutModel.setCurrentPercentage(1);
      }
    });
  }

  public boolean isTrackingAllocations() {
    return myTrackingAllocations;
  }

  public long getAllocationTrackingElapsedTimeNs() {
    if (myTrackingAllocations) {
      try {
        TimeResponse timeResponse = getStudioProfilers().getClient().getTransportClient()
          .getCurrentTime(TimeRequest.newBuilder().setStreamId(mySessionData.getStreamId()).build());
        return timeResponse.getTimestampNs() - myPendingLegacyAllocationStartTimeNs;
      }
      catch (StatusRuntimeException exception) {
        getLogger().warn(exception);
      }
    }
    return INVALID_START_TIME;
  }

  /**
   * Returns the capture object whose range overlaps with a given range. If multiple captures overlap with it,
   * the first object found is returned.
   */
  @Nullable
  public CaptureDurationData<? extends CaptureObject> getIntersectingCaptureDuration(@NotNull Range range) {
    Range intersection;
    CaptureDurationData<? extends CaptureObject> durationData = null;
    double overlap = 0.0f; // Weight value to determine which capture is "more" selected.

    List<SeriesData<CaptureDurationData<CaptureObject>>> series =
      new ArrayList<>(getAllocationInfosDurations().getSeries().getSeriesForRange(range));
    // Heap dumps break ties vs allocations.
    series.addAll(getHeapDumpSampleDurations().getSeries().getSeriesForRange(range));
    series.addAll(getNativeAllocationInfosDurations().getSeries().getSeriesForRange(range));

    for (SeriesData<CaptureDurationData<CaptureObject>> data : series) {
      long duration = data.value.getDurationUs();
      if (duration == Long.MAX_VALUE && !data.value.getSelectableWhenMaxDuration()) {
        continue;
      }

      long dataMax = duration == Long.MAX_VALUE ? duration : data.x + duration;
      double intersectionLen = range.getIntersectionLength(data.x, dataMax);
      // We need both an intersection check and length requirement because the intersection might be a point.
      if (range.intersectsWith(data.x, dataMax) && intersectionLen >= overlap) {
        durationData = data.value;
        overlap = intersectionLen;
      }
    }
    return durationData;
  }

  @Nullable
  private Common.Device getDeviceForSelectedSession() {
    StudioProfilers profilers = getStudioProfilers();
    Common.Stream stream = profilers.getStream(profilers.getSession().getStreamId());
    if (stream.getType() == Common.Stream.Type.DEVICE) {
      return stream.getDevice();
    }
    return null;
  }

  public boolean isNativeAllocationSamplingEnabled() {
    Common.Device device = getDeviceForSelectedSession();
    return getStudioProfilers().getIdeServices().getFeatureConfig().isNativeMemorySampleEnabled() &&
           device != null &&
           device.getFeatureLevel() >= AndroidVersion.VersionCodes.Q;
  }

  public boolean isLiveAllocationTrackingSupported() {
    Common.Device device = getDeviceForSelectedSession();
    return getStudioProfilers().getIdeServices().getFeatureConfig().isLiveAllocationsEnabled() &&
           device != null &&
           device.getFeatureLevel() >= AndroidVersion.VersionCodes.O;
  }

  public boolean isLiveAllocationTrackingReady() {
    return MemoryProfiler.isUsingLiveAllocation(getStudioProfilers(), mySessionData);
  }

  @NotNull
  public DurationDataModel<CaptureDurationData<CaptureObject>> getAllocationInfosDurations() {
    return myAllocationDurations;
  }

  @NotNull
  public DurationDataModel<CaptureDurationData<CaptureObject>> getNativeAllocationInfosDurations() {
    return myNativeAllocationDurations;
  }

  @VisibleForTesting
  public void selectCaptureDuration(@Nullable CaptureDurationData<? extends CaptureObject> durationData,
                             @Nullable Executor joiner) {
    StudioProfilers profilers = getStudioProfilers();
    if (durationData != null &&
        durationData.isHeapDumpData() &&
        getStudioProfilers().getIdeServices().getFeatureConfig().isSeparateHeapDumpUiEnabled()) {
      profilers.setStage(new HeapDumpStage(profilers, getLoader(), durationData, joiner));
    }
    else {
      doSelectCaptureDuration(durationData, joiner);
    }
  }

  @NotNull
  DurationDataModel<AllocationSamplingRateDurationData> getAllocationSamplingRateDurations() {
    return myAllocationSamplingRateDurations;
  }

  @NotNull
  public List<LiveAllocationSamplingMode> getSupportedLiveAllocationSamplingMode() {
    return Arrays.asList(LiveAllocationSamplingMode.values());
  }

  @NotNull
  public LiveAllocationSamplingMode getLiveAllocationSamplingMode() {
    return myLiveAllocationSamplingMode;
  }

  /**
   * Trigger a change to the sampling mode that should be used for live allocation tracking.
   */
  public void requestLiveAllocationSamplingModeUpdate(@NotNull LiveAllocationSamplingMode mode) {
    getStudioProfilers().getIdeServices().getPersistentProfilerPreferences().setInt(
      LIVE_ALLOCATION_SAMPLING_PREF, mode.getValue(), DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE.getValue()
    );

    try {
      MemoryAllocSamplingData samplingRate = MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(mode.getValue()).build();

      if (getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
        // TODO(b/150503095)
        Transport.ExecuteResponse response = getStudioProfilers().getClient().getTransportClient().execute(
          Transport.ExecuteRequest.newBuilder().setCommand(Commands.Command.newBuilder()
                                                             .setStreamId(mySessionData.getStreamId())
                                                             .setPid(mySessionData.getPid())
                                                             .setType(Commands.Command.CommandType.MEMORY_ALLOC_SAMPLING)
                                                             .setMemoryAllocSampling(samplingRate))
            .build());
      }
      else {
        // TODO(b/150503095)
        SetAllocationSamplingRateResponse response =
          getStudioProfilers().getClient().getMemoryClient().setAllocationSamplingRate(SetAllocationSamplingRateRequest.newBuilder()
                                                                                         .setSession(mySessionData)
                                                                                         .setSamplingRate(samplingRate)
                                                                                         .build());
      }
    }
    catch (StatusRuntimeException e) {
      getLogger().debug(e);
    }
  }

  private void setLiveAllocationSamplingModelInternal(@NotNull LiveAllocationSamplingMode mode) {
    if (mode == myLiveAllocationSamplingMode) {
      return;
    }

    myLiveAllocationSamplingMode = mode;
    myAspect.changed(MemoryProfilerAspect.LIVE_ALLOCATION_SAMPLING_MODE);
  }

  private void updateAllocationTrackingStatus() {
    List<AllocationsInfo> allocationsInfos = MemoryProfiler.getAllocationInfosForSession(getStudioProfilers().getClient(),
                                                                                         mySessionData,
                                                                                         new Range(Long.MIN_VALUE, Long.MAX_VALUE),
                                                                                         getStudioProfilers().getIdeServices());
    AllocationsInfo lastInfo = allocationsInfos.isEmpty() ? null : allocationsInfos.get(allocationsInfos.size() - 1);
    myTrackingAllocations = lastInfo != null && (lastInfo.getLegacy() && lastInfo.getEndTime() == Long.MAX_VALUE);
    if (myTrackingAllocations) {
      setPendingCaptureStartTime(lastInfo.getStartTime());
      myPendingLegacyAllocationStartTimeNs = lastInfo.getStartTime();
    }
    else {
      setPendingCaptureStartTime(INVALID_START_TIME);
      myPendingLegacyAllocationStartTimeNs = INVALID_START_TIME;
    }
  }

  private void updateNativeAllocationTrackingStatus() {
    List<Memory.MemoryNativeTrackingData> samples = MemoryProfiler
      .getNativeHeapStatusForSession(getStudioProfilers().getClient(), mySessionData, new Range(Long.MIN_VALUE, Long.MAX_VALUE));
    if (samples.isEmpty()) {
      return;
    }
    Memory.MemoryNativeTrackingData last = samples.get(samples.size() - 1);
    // If there is an ongoing recording.
    if (last.getStatus() == Memory.MemoryNativeTrackingData.Status.SUCCESS) {
      nativeAllocationTrackingStart(last);
    }
  }

  public AxisComponentModel getMemoryAxis() {
    return myMemoryAxis;
  }

  public AxisComponentModel getObjectsAxis() {
    return myObjectsAxis;
  }

  public MemoryStageLegends getLegends() {
    return myLegends;
  }

  public MemoryStageLegends getTooltipLegends() {
    return myTooltipLegends;
  }

  @NotNull
  public EaseOutModel getInstructionsEaseOutModel() {
    return myInstructionsEaseOutModel;
  }

  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  @NotNull
  public DurationDataModel<GcDurationData> getGcStatsModel() {
    return myGcStatsModel;
  }

  public String getName() {
    return "MEMORY";
  }

  @Override
  public void onNavigated(@NotNull CodeLocation location) {
    setProfilerMode(ProfilerMode.NORMAL);
  }

  public static boolean canSafelyLoadHprof(long fileSize) {
    System.gc(); // To avoid overly conservative estimation of free memory
    long leeway = 300 * 1024 * 1024; // Studio needs ~300MB to run without major freezes
    long requestableMemory = Runtime.getRuntime().maxMemory() -
                             Runtime.getRuntime().totalMemory() +
                             Runtime.getRuntime().freeMemory();
    return requestableMemory >= MEMORY_HPROF_SAFE_FACTOR * fileSize + leeway;
  }

  private class CaptureElapsedTimeUpdatable implements Updatable {
    @Override
    public void update(long elapsedNs) {
      if (myTrackingAllocations) {
        getCaptureSelection().getAspect().changed(CaptureSelectionAspect.CURRENT_CAPTURE_ELAPSED_TIME);
      }
    }
  }

  private class AllocationSamplingRateUpdatable implements Updatable {
    @Override
    public void update(long elapsedNs) {
      if (!isLiveAllocationTrackingReady()) {
        return;
      }

      // Find the last sampling info and see if it is different from the current, if so,
      double dataRangeMaxUs = getTimeline().getDataRange().getMax();
      List<SeriesData<AllocationSamplingRateDurationData>> data =
        myAllocationSamplingRateDataSeries.getDataForRange(new Range(dataRangeMaxUs, dataRangeMaxUs));

      if (data.isEmpty()) {
        // No data available. Keep the current settings.
        return;
      }

      MemoryAllocSamplingData samplingInfo = data.get(data.size() - 1).value.getCurrentRate();
      LiveAllocationSamplingMode mode = LiveAllocationSamplingMode.getModeFromFrequency(samplingInfo.getSamplingNumInterval());
      setLiveAllocationSamplingModelInternal(mode);
    }
  }

  public enum LiveAllocationSamplingMode {
    // 0 is a special value for disabling tracking.
    NONE(0, "None"),
    // Sample every 10 allocations
    SAMPLED(10, "Sampled"),
    // Sample every allocation
    FULL(1, "Full");

    static final Map<Integer, LiveAllocationSamplingMode> SAMPLING_RATE_MAP;
    static final Map<String, LiveAllocationSamplingMode> NAME_MAP;

    static {
      Map<Integer, LiveAllocationSamplingMode> samplingRateMap = new HashMap<>();
      Map<String, LiveAllocationSamplingMode> nameMap = new HashMap<>();
      for (LiveAllocationSamplingMode mode : LiveAllocationSamplingMode.values()) {
        samplingRateMap.put(mode.getValue(), mode);
        nameMap.put(mode.getDisplayName(), mode);
      }
      SAMPLING_RATE_MAP = ImmutableMap.copyOf(samplingRateMap);
      NAME_MAP = ImmutableMap.copyOf(nameMap);
    }

    private String myDisplayName;
    private int mySamplingFrequency;

    LiveAllocationSamplingMode(int frequency, String displayName) {
      myDisplayName = displayName;
      mySamplingFrequency = frequency;
    }

    public String getDisplayName() {
      return myDisplayName;
    }

    public int getValue() {
      return mySamplingFrequency;
    }

    @NotNull
    static LiveAllocationSamplingMode getModeFromFrequency(int frequency) {
      return SAMPLING_RATE_MAP.getOrDefault(frequency, DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE);
    }

    @NotNull
    static LiveAllocationSamplingMode getModeFromDisplayName(String displayName) {
      return NAME_MAP.getOrDefault(displayName, DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE);
    }
  }
}