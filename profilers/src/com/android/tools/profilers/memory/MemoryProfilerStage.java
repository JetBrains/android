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
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.filter.FilterHandler;
import com.android.tools.adtui.model.filter.FilterResult;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.legend.EventLegend;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.idea.transport.poller.TransportEventListener;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData;
import com.android.tools.profiler.proto.MemoryProfiler.ForceGarbageCollectionRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.MemoryProfiler.SetAllocationSamplingRateRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.analytics.FilterMetadata;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.ClassSet;
import com.android.tools.profilers.memory.adapters.FieldObject;
import com.android.tools.profilers.memory.adapters.HeapSet;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.hash.HashMap;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryProfilerStage extends Stage implements CodeNavigator.Listener {
  private static final String HAS_USED_MEMORY_CAPTURE = "memory.used.capture";
  public static final String LIVE_ALLOCATION_SAMPLING_PREF = "memory.live.allocation.mode";
  public static final LiveAllocationSamplingMode DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE = LiveAllocationSamplingMode.SAMPLED;

  static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 5, 5);
  static final BaseAxisFormatter OBJECT_COUNT_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 5, "");

  private static final long INVALID_START_TIME = -1;

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

  @NotNull private FilterHandler myFilterHandler;
  @Nullable private Filter myLastFilter;

  private final MemoryServiceBlockingStub myClient;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myHeapDumpDurations;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myAllocationDurations;
  private final CaptureObjectLoader myLoader;
  private final MemoryProfilerSelection mySelection;
  private final MemoryProfilerConfiguration myConfiguration;
  private final EventMonitor myEventMonitor;
  private final RangeSelectionModel myRangeSelectionModel;
  private final StackTraceModel myAllocationStackTraceModel;
  private final StackTraceModel myDeallocationStackTraceModel;
  private boolean myTrackingAllocations;
  private boolean myUpdateCaptureOnSelection = true;
  private final CaptureElapsedTimeUpdatable myCaptureElapsedTimeUpdatable = new CaptureElapsedTimeUpdatable();
  private long myPendingCaptureStartTime = INVALID_START_TIME;
  private long myPendingLegacyAllocationStartTimeNs = INVALID_START_TIME;

  @NotNull private final AllocationSamplingRateDataSeries myAllocationSamplingRateDataSeries;
  @NotNull private final DurationDataModel<AllocationSamplingRateDurationData> myAllocationSamplingRateDurations;
  @NotNull private final AllocationSamplingRateUpdatable myAllocationSamplingRateUpdatable;
  @NotNull private LiveAllocationSamplingMode myLiveAllocationSamplingMode;

  public MemoryProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CaptureObjectLoader());
  }

  @VisibleForTesting
  public MemoryProfilerStage(@NotNull StudioProfilers profilers, @NotNull CaptureObjectLoader loader) {
    super(profilers);
    myIsMemoryCaptureOnly =
      profilers.getSessionsManager().getSelectedSessionMetaData().getType() == Common.SessionMetaData.SessionType.MEMORY_CAPTURE;
    mySessionData = profilers.getSession();
    myClient = profilers.getClient().getMemoryClient();
    HeapDumpSampleDataSeries heapDumpSeries =
      new HeapDumpSampleDataSeries(profilers.getClient(), mySessionData, getStudioProfilers().getIdeServices().getFeatureTracker(), this);
    AllocationInfosDataSeries allocationSeries =
      new AllocationInfosDataSeries(profilers.getClient(), mySessionData, getStudioProfilers().getIdeServices().getFeatureTracker(), this);
    myLoader = loader;

    Range viewRange = profilers.getTimeline().getViewRange();
    // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
    myHeapDumpDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, heapDumpSeries));
    myAllocationDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, allocationSeries));
    mySelection = new MemoryProfilerSelection(this);
    myConfiguration = new MemoryProfilerConfiguration(this);

    DataSeries<GcDurationData> gcSeries =
      getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled() ?
      new UnifiedEventDataSeries<>(getStudioProfilers().getClient().getTransportClient(),
                                   mySessionData.getStreamId(),
                                   mySessionData.getPid(),
                                   Common.Event.Kind.MEMORY_GC,
                                   UnifiedEventDataSeries.DEFAULT_GROUP_ID,
                                   events -> events.stream()
                                     .map(evt -> new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(evt.getTimestamp()),
                                                                  new GcDurationData(
                                                                    TimeUnit.NANOSECONDS.toMicros(evt.getMemoryGc().getDuration()))))
                                     .collect(Collectors.toList())) :
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
      data -> !useLiveAllocationTracking() ||
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
        (!useLiveAllocationTracking() ||
         data.value.getCurrentRate().getSamplingNumInterval() == LiveAllocationSamplingMode.FULL.getValue())
    );
    myAllocationSamplingRateUpdatable = new AllocationSamplingRateUpdatable();

    myMemoryAxis = new ClampedAxisComponentModel.Builder(myDetailedMemoryUsage.getMemoryRange(), MEMORY_AXIS_FORMATTER).build();
    myObjectsAxis = new ClampedAxisComponentModel.Builder(myDetailedMemoryUsage.getObjectsRange(), OBJECT_COUNT_AXIS_FORMATTER).build();

    myLegends = new MemoryStageLegends(this, profilers.getTimeline().getDataRange(), false);
    myTooltipLegends = new MemoryStageLegends(this, profilers.getTimeline().getTooltipRange(), true);

    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);

    myEventMonitor = new EventMonitor(profilers);

    myRangeSelectionModel = new RangeSelectionModel(profilers.getTimeline().getSelectionRange());
    myRangeSelectionModel.addConstraint(myAllocationDurations);
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

    myFilterHandler = new FilterHandler() {
      @Override
      @NotNull
      protected FilterResult applyFilter(@NotNull Filter filter) {
        selectCaptureFilter(filter);
        HeapSet heapSet = getSelectedHeapSet();
        return heapSet == null ? new FilterResult(0, false) : new FilterResult(heapSet.getFilterMatchCount(), true);
      }
    };

    myAllocationStackTraceModel = new StackTraceModel(profilers.getIdeServices().getCodeNavigator());
    myDeallocationStackTraceModel = new StackTraceModel(profilers.getIdeServices().getCodeNavigator());

    // Set the sampling mode based on the last user setting. If the current session (either alive or dead) has a different sampling setting,
    // It will be set properly in the AllocationSamplingRateUpdatable.
    myLiveAllocationSamplingMode = LiveAllocationSamplingMode.getModeFromFrequency(
      profilers.getIdeServices().getPersistentProfilerPreferences()
        .getInt(LIVE_ALLOCATION_SAMPLING_PREF, DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE.getValue())
    );
    myAllocationSamplingRateUpdatable.update(0);
  }

  public boolean hasUserUsedMemoryCapture() {
    return getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().getBoolean(HAS_USED_MEMORY_CAPTURE, false);
  }

  public DetailedMemoryUsage getDetailedMemoryUsage() {
    return myDetailedMemoryUsage;
  }

  @Override
  public void enter() {
    myLoader.start();
    myEventMonitor.enter();
    getStudioProfilers().getUpdater().register(myDetailedMemoryUsage);
    getStudioProfilers().getUpdater().register(myHeapDumpDurations);
    getStudioProfilers().getUpdater().register(myAllocationDurations);
    getStudioProfilers().getUpdater().register(myMemoryAxis);
    getStudioProfilers().getUpdater().register(myObjectsAxis);
    getStudioProfilers().getUpdater().register(myGcStatsModel);
    getStudioProfilers().getUpdater().register(myAllocationSamplingRateDurations);
    getStudioProfilers().getUpdater().register(myCaptureElapsedTimeUpdatable);
    getStudioProfilers().getUpdater().register(myAllocationSamplingRateUpdatable);

    getStudioProfilers().getIdeServices().getCodeNavigator().addListener(this);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getClass());

    // TODO Optimize this to not include non-legacy allocation tracking information.
    MemoryData data = myClient
      .getData(MemoryRequest.newBuilder().setSession(mySessionData).setStartTime(Long.MIN_VALUE).setEndTime(Long.MAX_VALUE).build());
    List<AllocationsInfo> allocationsInfos = data.getAllocationsInfoList();
    AllocationsInfo lastInfo = allocationsInfos.isEmpty() ? null : allocationsInfos.get(allocationsInfos.size() - 1);
    myTrackingAllocations = lastInfo != null && (lastInfo.getLegacy() && lastInfo.getEndTime() == Long.MAX_VALUE);
    if (myTrackingAllocations) {
      myPendingCaptureStartTime = lastInfo.getStartTime();
      myPendingLegacyAllocationStartTimeNs = lastInfo.getStartTime();
    }
    else {
      myPendingCaptureStartTime = INVALID_START_TIME;
      myPendingLegacyAllocationStartTimeNs = INVALID_START_TIME;
    }
  }

  @Override
  public void exit() {
    enableSelectLatestCapture(false, null);

    myEventMonitor.exit();
    getStudioProfilers().getUpdater().unregister(myDetailedMemoryUsage);
    getStudioProfilers().getUpdater().unregister(myHeapDumpDurations);
    getStudioProfilers().getUpdater().unregister(myAllocationDurations);
    getStudioProfilers().getUpdater().unregister(myMemoryAxis);
    getStudioProfilers().getUpdater().unregister(myObjectsAxis);
    getStudioProfilers().getUpdater().unregister(myGcStatsModel);
    getStudioProfilers().getUpdater().unregister(myAllocationSamplingRateDurations);
    getStudioProfilers().getUpdater().unregister(myCaptureElapsedTimeUpdatable);
    getStudioProfilers().getUpdater().unregister(myAllocationSamplingRateUpdatable);
    selectCaptureDuration(null, null);
    myLoader.stop();

    getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(this);

    myRangeSelectionModel.clearListeners();
  }

  @NotNull
  public RangeSelectionModel getRangeSelectionModel() {
    return myRangeSelectionModel;
  }

  @NotNull
  public StackTraceModel getAllocationStackTraceModel() {
    return myAllocationStackTraceModel;
  }

  @NotNull
  public StackTraceModel getDeallocationStackTraceModel() {
    return myDeallocationStackTraceModel;
  }

  private void selectCaptureFromSelectionRange() {
    if (!myUpdateCaptureOnSelection) {
      return;
    }

    myUpdateCaptureOnSelection = false;
    Range selectionRange = getStudioProfilers().getTimeline().getSelectionRange();
    selectCaptureDuration(getIntersectingCaptureDuration(selectionRange), SwingUtilities::invokeLater);
    myUpdateCaptureOnSelection = true;
  }

  /**
   * Toggle a behavior where if there is currently no CaptureObject selected, the model will attempt to select the next CaptureObject
   * that has been created either through {@link #trackAllocations(boolean)} or {@link #requestHeapDump()}.
   *
   * @param loadJoiner if specified, the joiner executor will be passed down to {@link #loadCaptureObject(CaptureObject, Executor)} so
   *                   that the load operation of the CaptureObject will be joined and the CURRENT_LOAD_CAPTURE aspect would be
   *                   fired via the desired executor.
   */
  public void enableSelectLatestCapture(boolean enable, @Nullable Executor loadJoiner) {
    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    if (enable) {
      timeline.getDataRange().addDependency(this)
        .onChange(Range.Aspect.RANGE, () -> queryAndSelectCaptureObject(loadJoiner == null ? MoreExecutors.directExecutor() : loadJoiner));
    }
    else {
      // Removing the aspect observers on Ranges.
      timeline.getDataRange().removeDependencies(this);
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
    Range dataRange = getStudioProfilers().getTimeline().getDataRange();
    if (myPendingCaptureStartTime != INVALID_START_TIME) {
      List<SeriesData<CaptureDurationData<CaptureObject>>> series =
        new ArrayList<>(getAllocationInfosDurations().getSeries().getSeriesForRange(dataRange));
      series.addAll(getHeapDumpSampleDurations().getSeries().getSeriesForRange(dataRange));

      long pendingCaptureStartTimeUs = TimeUnit.NANOSECONDS.toMicros(myPendingCaptureStartTime);
      SeriesData<CaptureDurationData<CaptureObject>> captureToSelect = null;
      for (int i = series.size() - 1; i >= 0; --i) {
        if (series.get(i).x == pendingCaptureStartTimeUs) {
          captureToSelect = series.get(i);
          break;
        }
      }

      if (captureToSelect != null &&
          (captureToSelect.value.getDurationUs() != Long.MAX_VALUE || captureToSelect.value.getSelectableWhenMaxDuration())) {
        selectCaptureDuration(captureToSelect.value, loadJoiner);
      }
    }
  }

  /**
   * Set the start time for pending capture object imported from hprof file.
   */
  public void setPendingCaptureStartTime(long pendingCaptureStartTime) {
    assert myIsMemoryCaptureOnly;
    myPendingCaptureStartTime = pendingCaptureStartTime;
  }

  @NotNull
  public AspectModel<MemoryProfilerAspect> getAspect() {
    return myAspect;
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

    getStudioProfilers().getTimeline().setStreaming(true);
    getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_MEMORY_CAPTURE, true);
    myInstructionsEaseOutModel.setCurrentPercentage(1);
  }

  private void handleHeapDumpStart(@NotNull Memory.HeapDumpStatus status) {
    switch (status.getStatus()) {
      case SUCCESS:
        myPendingCaptureStartTime = status.getStartTime();
        break;
      case IN_PROGRESS:
        getLogger().debug(String.format("A heap dump for %d is already in progress.", mySessionData.getPid()));
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
      getStudioProfilers().getClient().getTransportClient().execute(
        Transport.ExecuteRequest.newBuilder()
          .setCommand(Commands.Command.newBuilder()
                        .setStreamId(mySessionData.getStreamId())
                        .setPid(mySessionData.getPid())
                        .setType(Commands.Command.CommandType.GC))
          .build());
    }
    else {
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
          myPendingCaptureStartTime = status.getStartTime();
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
        getStudioProfilers().getTimeline().setStreaming(true);
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
      TimeResponse timeResponse = getStudioProfilers().getClient().getTransportClient()
        .getCurrentTime(TimeRequest.newBuilder().setStreamId(getStudioProfilers().getDevice().getDeviceId()).build());
      return timeResponse.getTimestampNs() - myPendingLegacyAllocationStartTimeNs;
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

    for (SeriesData<CaptureDurationData<CaptureObject>> data : series) {
      long duration = data.value.getDurationUs();
      if (duration == Long.MAX_VALUE && !data.value.getSelectableWhenMaxDuration()) {
        continue;
      }

      long dataMax = duration == Long.MAX_VALUE ? duration : data.x + duration;
      Range c = new Range(data.x, dataMax);
      intersection = c.getIntersection(range);
      if (!intersection.isEmpty() && intersection.getLength() >= overlap) {
        durationData = data.value;
        overlap = intersection.getLength();
      }
    }
    return durationData;
  }

  public boolean useLiveAllocationTracking() {
    return MemoryProfiler.isUsingLiveAllocation(getStudioProfilers(), mySessionData);
  }

  @NotNull
  public DurationDataModel<CaptureDurationData<CaptureObject>> getAllocationInfosDurations() {
    return myAllocationDurations;
  }

  public void selectFieldObjectPath(@NotNull List<FieldObject> fieldObjectPath) {
    mySelection.selectFieldObjectPath(fieldObjectPath);
  }

  @NotNull
  public List<FieldObject> getSelectedFieldObjectPath() {
    return mySelection.getFieldObjectPath();
  }

  public void selectInstanceObject(@Nullable InstanceObject instanceObject) {
    mySelection.selectInstanceObject(instanceObject);
  }

  @Nullable
  public InstanceObject getSelectedInstanceObject() {
    return mySelection.getInstanceObject();
  }

  public void selectClassSet(@Nullable ClassSet classSet) {
    mySelection.selectClassSet(classSet);
  }

  @Nullable
  public ClassSet getSelectedClassSet() {
    return mySelection.getClassSet();
  }

  public void refreshSelectedHeap() {
    myAspect.changed(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS);
    myFilterHandler.refreshFilterContent();
  }

  public void selectHeapSet(@Nullable HeapSet heapSet) {
    mySelection.selectHeapSet(heapSet);
    myFilterHandler.refreshFilterContent();
    if (heapSet != null) {
      getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectMemoryHeap(heapSet.getName());
    }
  }

  @Nullable
  public HeapSet getSelectedHeapSet() {
    return mySelection.getHeapSet();
  }

  private void selectCaptureFilter(@NotNull Filter filter) {
    // Only track filter usage when filter has been updated.
    if (Objects.equals(myLastFilter, filter)) {
      myLastFilter = filter;
      trackFilterUsage(filter);
    }
    if (getSelectedHeapSet() != null) {
      getSelectedHeapSet().selectFilter(filter);
    }
    // Clears the selected ClassSet if it's been filtered.
    if (getSelectedClassSet() != null && getSelectedClassSet().getIsFiltered()) {
      selectClassSet(ClassSet.EMPTY_SET);
    }
    myAspect.changed(MemoryProfilerAspect.CURRENT_FILTER);
  }

  private void trackFilterUsage(@NotNull Filter filter) {
    FilterMetadata filterMetadata = new FilterMetadata();
    FeatureTracker featureTracker = getStudioProfilers().getIdeServices().getFeatureTracker();
    switch (getConfiguration().getClassGrouping()) {
      case ARRANGE_BY_CLASS:
        filterMetadata.setView(FilterMetadata.View.MEMORY_CLASS);
        break;
      case ARRANGE_BY_PACKAGE:
        filterMetadata.setView(FilterMetadata.View.MEMORY_PACKAGE);
        break;
      case ARRANGE_BY_CALLSTACK:
        filterMetadata.setView(FilterMetadata.View.MEMORY_CALLSTACK);
        break;
    }
    filterMetadata.setFeaturesUsed(filter.isMatchCase(), filter.isRegex());
    if (getSelectedHeapSet() != null) {
      filterMetadata.setMatchedElementCount(getSelectedHeapSet().getFilteredObjectSetCount());
      filterMetadata.setTotalElementCount(getSelectedHeapSet().getTotalObjectSetCount());
    }
    filterMetadata.setFilterTextLength(filter.isEmpty() ? 0 : filter.getFilterString().length());
    featureTracker.trackFilterMetadata(filterMetadata);
  }

  @NotNull
  public FilterHandler getFilterHandler() {
    return myFilterHandler;
  }

  @VisibleForTesting
  void selectCaptureDuration(@Nullable CaptureDurationData<? extends CaptureObject> durationData,
                             @Nullable Executor joiner) {
    myPendingCaptureStartTime = INVALID_START_TIME;
    if (!mySelection.selectCaptureEntry(durationData == null ? null : durationData.getCaptureEntry())) {
      return;
    }

    myUpdateCaptureOnSelection = false;
    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    CaptureObject captureObject = mySelection.getCaptureObject();
    if (captureObject == null) {
      // Loading a capture can fail, in which case we reset everything.
      mySelection.selectCaptureEntry(null);
      timeline.getSelectionRange().clear();
      myAspect.changed(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE);
      setProfilerMode(ProfilerMode.NORMAL);
      return;
    }

    // Synchronize selection with the capture object. Do so only if the capture object is not ongoing.
    if (durationData != null && durationData.getDurationUs() != Long.MAX_VALUE) {
      // TODO: (revisit) we have an special case in interacting with RangeSelectionModel where if the user tries to select a heap dump that is on
      // top of an ongoing live allocation capture (duration == Long.MAX_VALUE), the live capture would take precedence given it always
      // intersects with the previous selection. Here we clear the previous selection first to avoid said interaction.
      timeline.getSelectionRange().clear();
      long startTimeUs = TimeUnit.NANOSECONDS.toMicros(captureObject.getStartTimeNs());
      long endTimeUs = TimeUnit.NANOSECONDS.toMicros(captureObject.getEndTimeNs());
      timeline.getSelectionRange().set(startTimeUs, endTimeUs);
    }
    myUpdateCaptureOnSelection = true;

    // TODO: (revisit) - do we want to pass in data range to loadCapture as well?
    ListenableFuture<CaptureObject> future = myLoader.loadCapture(captureObject, timeline.getSelectionRange(), joiner);
    future.addListener(
      () -> {
        try {
          CaptureObject loadedCaptureObject = future.get();
          if (mySelection.finishSelectingCaptureObject(loadedCaptureObject)) {
            Collection<HeapSet> heaps = loadedCaptureObject.getHeapSets();
            if (heaps.isEmpty()) {
              return;
            }

            for (HeapSet heap : heaps) {
              if (heap.getName().equals("app")) {
                selectHeapSet(heap);
                return;
              }
            }

            for (HeapSet heap : heaps) {
              if (heap.getName().equals("default")) {
                selectHeapSet(heap);
                return;
              }
            }

            HeapSet heap = new ArrayList<>(heaps).get(0);
            selectHeapSet(heap);
          }
          else {
            // Capture loading failed.
            // TODO: loading has somehow failed - we need to inform users about the error status.
            selectCaptureDuration(null, null);

            // Triggers the aspect to inform listeners that the heap content/filter has changed (become null).
            refreshSelectedHeap();
          }
        }
        catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          selectCaptureDuration(null, null);
        }
        catch (ExecutionException exception) {
          selectCaptureDuration(null, null);
          getLogger().error(exception);
        }
        catch (CancellationException ignored) {
          // No-op: a previous load-capture task is canceled due to another capture being selected and loaded.
        }
      },
      joiner == null ? MoreExecutors.directExecutor() : joiner);

    setProfilerMode(ProfilerMode.EXPANDED);
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
        getStudioProfilers().getClient().getTransportClient().execute(
          Transport.ExecuteRequest.newBuilder().setCommand(Commands.Command.newBuilder()
                                                             .setStreamId(mySessionData.getStreamId())
                                                             .setPid(mySessionData.getPid())
                                                             .setType(Commands.Command.CommandType.MEMORY_ALLOC_SAMPLING)
                                                             .setMemoryAllocSampling(samplingRate))
            .build());
      }
      else {
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

  @Nullable
  public CaptureObject getSelectedCapture() {
    return mySelection.getCaptureObject();
  }

  @NotNull
  public MemoryProfilerConfiguration getConfiguration() {
    return myConfiguration;
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

  public static class MemoryStageLegends extends LegendComponentModel {
    @NotNull private final SeriesLegend myJavaLegend;
    @NotNull private final SeriesLegend myNativeLegend;
    @NotNull private final SeriesLegend myGraphicsLegend;
    @NotNull private final SeriesLegend myStackLegend;
    @NotNull private final SeriesLegend myCodeLegend;
    @NotNull private final SeriesLegend myOtherLegend;
    @NotNull private final SeriesLegend myTotalLegend;
    @NotNull private final SeriesLegend myObjectsLegend;
    @NotNull private final EventLegend<GcDurationData> myGcDurationLegend;
    @NotNull private final EventLegend<AllocationSamplingRateDurationData> mySamplingRateDurationLegend;

    public MemoryStageLegends(@NotNull MemoryProfilerStage memoryStage, @NotNull Range range, boolean isTooltip) {
      super(range);
      DetailedMemoryUsage usage = memoryStage.getDetailedMemoryUsage();
      myJavaLegend = new SeriesLegend(usage.getJavaSeries(), MEMORY_AXIS_FORMATTER, range);
      myNativeLegend = new SeriesLegend(usage.getNativeSeries(), MEMORY_AXIS_FORMATTER, range);
      myGraphicsLegend = new SeriesLegend(usage.getGraphicsSeries(), MEMORY_AXIS_FORMATTER, range);
      myStackLegend = new SeriesLegend(usage.getStackSeries(), MEMORY_AXIS_FORMATTER, range);
      myCodeLegend = new SeriesLegend(usage.getCodeSeries(), MEMORY_AXIS_FORMATTER, range);
      myOtherLegend = new SeriesLegend(usage.getOtherSeries(), MEMORY_AXIS_FORMATTER, range);
      myTotalLegend = new SeriesLegend(usage.getTotalMemorySeries(), MEMORY_AXIS_FORMATTER, range);
      myObjectsLegend = new SeriesLegend(usage.getObjectsSeries(), OBJECT_COUNT_AXIS_FORMATTER, range, usage.getObjectsSeries().getName(),
                                         Interpolatable.RoundedSegmentInterpolator, r -> {
        if (!memoryStage.useLiveAllocationTracking()) {
          // if live allocation is not enabled, show the object series as long as there is data.
          return true;
        }

        // Controls whether the series should be shown by looking at whether there is a FULL tracking mode event within the query range.
        List<SeriesData<AllocationSamplingRateDurationData>> data =
          usage.getAllocationSamplingRateDurations().getSeries().getSeriesForRange(r);

        if (data.isEmpty()) {
          return false;
        }

        MemoryAllocSamplingData samplingInfo = data.get(data.size() - 1).value.getCurrentRate();
        return LiveAllocationSamplingMode.getModeFromFrequency(samplingInfo.getSamplingNumInterval()) == LiveAllocationSamplingMode.FULL;
      });
      myGcDurationLegend =
        new EventLegend<>("GC Duration", duration -> TimeAxisFormatter.DEFAULT
          .getFormattedString(TimeUnit.MILLISECONDS.toMicros(1), duration.getDurationUs(), true));
      mySamplingRateDurationLegend =
        new EventLegend<>("Tracking", duration -> LiveAllocationSamplingMode
          .getModeFromFrequency(duration.getCurrentRate().getSamplingNumInterval()).getDisplayName());

      List<Legend> legends = isTooltip ? Arrays.asList(myOtherLegend, myCodeLegend, myStackLegend, myGraphicsLegend,
                                                       myNativeLegend, myJavaLegend, myObjectsLegend, mySamplingRateDurationLegend,
                                                       myGcDurationLegend, myTotalLegend)
                                       : Arrays.asList(myTotalLegend, myJavaLegend, myNativeLegend,
                                                       myGraphicsLegend, myStackLegend, myCodeLegend, myOtherLegend, myObjectsLegend);
      legends.forEach(this::add);
    }

    @NotNull
    public SeriesLegend getJavaLegend() {
      return myJavaLegend;
    }

    @NotNull
    public SeriesLegend getNativeLegend() {
      return myNativeLegend;
    }

    @NotNull
    public SeriesLegend getGraphicsLegend() {
      return myGraphicsLegend;
    }

    @NotNull
    public SeriesLegend getStackLegend() {
      return myStackLegend;
    }

    @NotNull
    public SeriesLegend getCodeLegend() {
      return myCodeLegend;
    }

    @NotNull
    public SeriesLegend getOtherLegend() {
      return myOtherLegend;
    }

    @NotNull
    public SeriesLegend getTotalLegend() {
      return myTotalLegend;
    }

    @NotNull
    public SeriesLegend getObjectsLegend() {
      return myObjectsLegend;
    }

    @NotNull
    public EventLegend<GcDurationData> getGcDurationLegend() {
      return myGcDurationLegend;
    }

    @NotNull
    public EventLegend<AllocationSamplingRateDurationData> getSamplingRateDurationLegend() {
      return mySamplingRateDurationLegend;
    }
  }

  private class CaptureElapsedTimeUpdatable implements Updatable {
    @Override
    public void update(long elapsedNs) {
      if (myTrackingAllocations) {
        myAspect.changed(MemoryProfilerAspect.CURRENT_CAPTURE_ELAPSED_TIME);
      }
    }
  }

  private class AllocationSamplingRateUpdatable implements Updatable {
    @Override
    public void update(long elapsedNs) {
      if (!useLiveAllocationTracking()) {
        return;
      }

      // Find the last sampling info and see if it is different from the current, if so,
      double dataRangeMaxUs = getStudioProfilers().getTimeline().getDataRange().getMax();
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
