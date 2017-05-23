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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class MemoryProfilerStage extends Stage implements CodeNavigator.Listener {
  private static Logger getLogger() {
    return Logger.getInstance(MemoryProfilerStage.class);
  }

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 5, 5);
  private static final BaseAxisFormatter OBJECT_COUNT_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 5, "");

  private final DetailedMemoryUsage myDetailedMemoryUsage;
  private final AxisComponentModel myMemoryAxis;
  private final AxisComponentModel myObjectsAxis;
  private final MemoryStageLegends myLegends;
  private final MemoryStageLegends myTooltipLegends;

  private final int myProcessId;
  @Nullable
  private final Common.Session mySessionData;
  private DurationDataModel<GcDurationData> myGcStats;

  @NotNull
  private AspectModel<MemoryProfilerAspect> myAspect = new AspectModel<>();

  private final MemoryServiceBlockingStub myClient;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myHeapDumpDurations;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myAllocationDurations;
  private final CaptureObjectLoader myLoader;
  private final MemoryProfilerSelection mySelection;
  private final MemoryProfilerConfiguration myConfiguration;
  private final EventMonitor myEventMonitor;
  private final SelectionModel mySelectionModel;
  private final StackTraceModel myStackTraceModel;
  private boolean myTrackingAllocations;
  private boolean myUpdateCaptureOnSelection = true;

  public MemoryProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CaptureObjectLoader());
  }

  @VisibleForTesting
  MemoryProfilerStage(@NotNull StudioProfilers profilers, @NotNull CaptureObjectLoader loader) {
    super(profilers);
    myProcessId = profilers.getProcessId();
    mySessionData = profilers.getSession();
    myClient = profilers.getClient().getMemoryClient();
    HeapDumpSampleDataSeries heapDumpSeries =
      new HeapDumpSampleDataSeries(profilers.getClient().getMemoryClient(), mySessionData, myProcessId,
                                   profilers.getRelativeTimeConverter(), getStudioProfilers().getIdeServices().getFeatureTracker());
    AllocationInfosDataSeries allocationSeries =
      new AllocationInfosDataSeries(profilers.getClient().getMemoryClient(), mySessionData, myProcessId,
                                    profilers.getRelativeTimeConverter(), getStudioProfilers().getIdeServices().getFeatureTracker());
    myLoader = loader;

    Range viewRange = profilers.getTimeline().getViewRange();

    myHeapDumpDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, heapDumpSeries));
    myAllocationDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, allocationSeries));
    mySelection = new MemoryProfilerSelection(this);
    myConfiguration = new MemoryProfilerConfiguration(this);

    myDetailedMemoryUsage = new DetailedMemoryUsage(profilers);

    myMemoryAxis = new AxisComponentModel(myDetailedMemoryUsage.getMemoryRange(), MEMORY_AXIS_FORMATTER);
    myMemoryAxis.setClampToMajorTicks(true);

    myObjectsAxis = new AxisComponentModel(myDetailedMemoryUsage.getObjectsRange(), OBJECT_COUNT_AXIS_FORMATTER);
    myObjectsAxis.setClampToMajorTicks(true);

    myLegends = new MemoryStageLegends(profilers, myDetailedMemoryUsage, profilers.getTimeline().getDataRange());
    myTooltipLegends = new MemoryStageLegends(profilers, myDetailedMemoryUsage, profilers.getTimeline().getTooltipRange());

    myGcStats = new DurationDataModel<>(new RangedSeries<>(viewRange, new GcStatsDataSeries(myClient, myProcessId, mySessionData)));
    myGcStats.setAttachedSeries(myDetailedMemoryUsage.getObjectsSeries());

    myEventMonitor = new EventMonitor(profilers);

    mySelectionModel = new SelectionModel(profilers.getTimeline().getSelectionRange(), profilers.getTimeline().getViewRange());
    mySelectionModel.addConstraint(myAllocationDurations);
    mySelectionModel.addConstraint(myHeapDumpDurations);
    mySelectionModel.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        selectCaptureFromSelectionRange();
        profilers.getIdeServices().getFeatureTracker().trackSelectRange();
      }

      @Override
      public void selectionCleared() {
        selectCaptureFromSelectionRange();
      }
    });

    myStackTraceModel = new StackTraceModel(profilers.getIdeServices().getCodeNavigator());
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
    getStudioProfilers().getUpdater().register(myLegends);
    getStudioProfilers().getUpdater().register(myTooltipLegends);
    getStudioProfilers().getUpdater().register(myGcStats);

    getStudioProfilers().getIdeServices().getCodeNavigator().addListener(this);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getClass());

    if (useLiveAllocationTracking()) {
      trackAllocations(true, SwingUtilities::invokeLater);
    }
    else {
      myTrackingAllocations = false; // TODO sync with current legacy allocation tracker status
    }
  }

  @Override
  public void exit() {
    if (useLiveAllocationTracking()) {
      trackAllocations(false, null);
    }

    myEventMonitor.exit();
    getStudioProfilers().getUpdater().unregister(myDetailedMemoryUsage);
    getStudioProfilers().getUpdater().unregister(myHeapDumpDurations);
    getStudioProfilers().getUpdater().unregister(myAllocationDurations);
    getStudioProfilers().getUpdater().unregister(myMemoryAxis);
    getStudioProfilers().getUpdater().unregister(myObjectsAxis);
    getStudioProfilers().getUpdater().unregister(myLegends);
    getStudioProfilers().getUpdater().unregister(myTooltipLegends);
    getStudioProfilers().getUpdater().unregister(myGcStats);
    loadCaptureObject(null, SwingUtilities::invokeLater);
    myLoader.stop();

    getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(this);

    mySelectionModel.clearListeners();
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @NotNull
  public StackTraceModel getStackTraceModel() {
    return myStackTraceModel;
  }

  private void selectCaptureFromSelectionRange() {
    if (!myUpdateCaptureOnSelection) {
      return;
    }

    Range selectionRange = getStudioProfilers().getTimeline().getSelectionRange();
    Range intersection = new Range();
    CaptureDurationData<? extends CaptureObject> durationData = null;
    double overlap = 0.0f; // Weight value to determine which capture is "more" selected.

    List<SeriesData<CaptureDurationData<CaptureObject>>> series =
      new ArrayList<>(getAllocationInfosDurations().getSeries().getDataSeries().getDataForXRange(selectionRange));
    // Heap dumps break ties vs allocations.
    series.addAll(getHeapDumpSampleDurations().getSeries().getDataSeries().getDataForXRange(selectionRange));

    for (SeriesData<CaptureDurationData<CaptureObject>> data : series) {
      long duration = data.value.getDuration();
      if (duration == Long.MAX_VALUE && !data.value.getSelectableWhenMaxDuration()) {
        continue;
      }

      long dataMax = duration == Long.MAX_VALUE ? duration : data.x + duration;
      Range c = new Range(data.x, dataMax);
      intersection = c.getIntersection(selectionRange);
      if (!intersection.isEmpty() && intersection.getLength() >= overlap) {
        durationData = data.value;
        overlap = intersection.getLength();
      }
    }

    selectCaptureDuration(durationData, intersection, SwingUtilities::invokeLater);
  }

  @NotNull
  public AspectModel<MemoryProfilerAspect> getAspect() {
    return myAspect;
  }

  /**
   * @param loadJoiner if specified, the joiner executor will be passed down to {@link #loadCaptureObject(CaptureObject, Executor)} so
   *                   that the load operation of the CaptureObject will be joined and the CURRENT_LOAD_CAPTURE aspect would be
   *                   fired via the desired executor.
   */
  public void requestHeapDump(@Nullable Executor loadJoiner) {
    MemoryProfiler.TriggerHeapDumpResponse response =
      myClient
        .triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.newBuilder().setSession(mySessionData).setProcessId(myProcessId).build());
    switch (response.getStatus()) {
      case SUCCESS:
        MemoryProfiler.HeapDumpInfo info = response.getInfo();
        selectCaptureDuration(
          new CaptureDurationData<>(
            CaptureDataSeries.getDurationUs(info.getStartTime(), info.getEndTime()), false, false,
            new CaptureEntry<CaptureObject>(
              response.getInfo(),
              () -> new HeapDumpCaptureObject(myClient, mySessionData, myProcessId, info, null,
                                              getStudioProfilers().getRelativeTimeConverter(),
                                              getStudioProfilers().getIdeServices().getFeatureTracker()))
          ), null, loadJoiner);
        break;
      case IN_PROGRESS:
        getLogger().debug(String.format("A heap dump for %d is already in progress.", myProcessId));
        break;
      case UNSPECIFIED:
      case NOT_PROFILING:
      case FAILURE_UNKNOWN:
      case UNRECOGNIZED:
        break;
    }
  }

  public void forceGarbageCollection(@NotNull Executor executor) {
    executor.execute(() -> myClient.forceGarbageCollection(
      MemoryProfiler.ForceGarbageCollectionRequest.newBuilder().setProcessId(myProcessId).setSession(mySessionData).build()));
  }

  public DurationDataModel<CaptureDurationData<CaptureObject>> getHeapDumpSampleDurations() {
    return myHeapDumpDurations;
  }

  /**
   * @param enabled    whether to enable or disable allocation tracking.
   * @param loadJoiner if specified, the joiner executor will be passed down to {@link #loadCaptureObject(CaptureObject, Executor)} so
   *                   that the load operation of the CaptureObject will be joined and the CURRENT_LOAD_CAPTURE aspect would be
   *                   fired via the desired executor.
   * @return the actual status, which may be different from the input
   */
  public void trackAllocations(boolean enabled, @Nullable Executor loadJoiner) {
    // Allocation tracking can go through the legacy tracker which does not reach perfd, so we need to pass in the current device time.
    Profiler.TimeResponse timeResponse = getStudioProfilers().getClient().getProfilerClient()
      .getCurrentTime(Profiler.TimeRequest.newBuilder().setSession(mySessionData).build());
    long timeNs = timeResponse.getTimestampNs();

    try {
      TrackAllocationsResponse response = myClient.trackAllocations(
        MemoryProfiler.TrackAllocationsRequest.newBuilder().setRequestTime(timeNs).setSession(mySessionData).setProcessId(myProcessId)
          .setEnabled(enabled).build());
      MemoryProfiler.AllocationsInfo info = response.getInfo();
      long duration = CaptureDataSeries.getDurationUs(info.getStartTime(), info.getEndTime());
      FeatureTracker featureTracker = getStudioProfilers().getIdeServices().getFeatureTracker();

      switch (response.getStatus()) {
        case SUCCESS:
          myTrackingAllocations = enabled;
          if (!myTrackingAllocations && info.getLegacy()) {
            selectCaptureDuration(
              new CaptureDurationData<>(
                duration, false, false,
                new CaptureEntry<>(
                  response.getInfo(),
                  () -> new LegacyAllocationCaptureObject(
                    myClient, mySessionData, myProcessId, info, getStudioProfilers().getRelativeTimeConverter(), featureTracker))
              ), null, loadJoiner);
          }
          else if (myTrackingAllocations && !response.getInfo().getLegacy()) {
            selectCaptureDuration(
              new CaptureDurationData<>(
                duration, true, true,
                new CaptureEntry<>(
                  response.getInfo(),
                  () -> new LiveAllocationCaptureObject(myClient, mySessionData, myProcessId, info.getStartTime(), featureTracker))
              ), null, loadJoiner);
          }
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
    }
    catch (StatusRuntimeException e) {
      getLogger().debug(e);
    }
  }

  public boolean isTrackingAllocations() {
    return myTrackingAllocations;
  }

  public boolean useLiveAllocationTracking() {
    return getStudioProfilers().getIdeServices().getFeatureConfig().isLiveAllocationsEnabled() &&
           getStudioProfilers().getDevice().getFeatureLevel() >= AndroidVersion.VersionCodes.O;
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

  public void selectHeapSet(@Nullable HeapSet heapSet) {
    mySelection.selectHeapSet(heapSet);
  }

  @Nullable
  public HeapSet getSelectedHeapSet() {
    return mySelection.getHeapSet();
  }

  private void registerLiveAllocationCaptureObjectToTimeline(@NotNull LiveAllocationCaptureObject captureObject,
                                                             @NotNull Executor loadJoiner) {
    List<Range> rangesToRegister =
      ImmutableList.of(getStudioProfilers().getTimeline().getDataRange(), getStudioProfilers().getTimeline().getSelectionRange());

    // TODO move this to within LiveAllocationCaptureObject?
    rangesToRegister.forEach(
      range -> range.addDependency(this).onChange(Range.Aspect.RANGE, () -> {
        Range rangeToUse = getStudioProfilers().getTimeline().getSelectionRange();
        if (rangeToUse.isEmpty()) {
          rangeToUse = getStudioProfilers().getTimeline().getDataRange();
        }
        if (!rangeToUse.isEmpty()) {
          captureObject.loadTimeRange(TimeUnit.MICROSECONDS.toNanos((long)rangeToUse.getMin()),
                                      TimeUnit.MICROSECONDS.toNanos((long)rangeToUse.getMax()),
                                      loadJoiner);
        }
      })
    );
  }

  @VisibleForTesting
  void selectCaptureDuration(@Nullable CaptureDurationData<? extends CaptureObject> durationData,
                             @Nullable Range subRange,
                             @Nullable Executor joiner) {
    if (!mySelection.selectCaptureEntry(durationData == null ? null : durationData.getCaptureEntry())) {
      return;
    }

    CaptureObject captureObject = mySelection.getCaptureObject();
    loadCaptureObject(captureObject, joiner);
    CaptureObject selectedCapture = getSelectedCapture();

    if (selectedCapture == null) {
      return;
    }

    assert durationData != null;
    Range selectionRange = getStudioProfilers().getTimeline().getSelectionRange();
    myUpdateCaptureOnSelection = false;
    if (selectedCapture instanceof LiveAllocationCaptureObject) {
      registerLiveAllocationCaptureObjectToTimeline((LiveAllocationCaptureObject)selectedCapture,
                                                    joiner == null ? MoreExecutors.directExecutor() : joiner);
    }

    if (durationData.canSelectPartialRange() && subRange != null) {
      selectionRange.set(subRange.getMin(), subRange.getMax());
    }
    else {
      selectionRange.set(TimeUnit.NANOSECONDS.toMicros(selectedCapture.getStartTimeNs()),
                         TimeUnit.NANOSECONDS.toMicros(selectedCapture.getEndTimeNs()));
    }
    myUpdateCaptureOnSelection = true;
  }

  private void loadCaptureObject(@Nullable CaptureObject captureObject, @Nullable Executor joiner) {
    // First remove all aspect dependencies on the previously selected capture object.
    // TODO move this to within LiveAllocationCaptureObject?
    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    ImmutableList.of(timeline.getDataRange(), timeline.getSelectionRange()).forEach(range -> range.removeDependencies(this));

    if (captureObject == null) {
      mySelection.selectCaptureEntry(null);
      myAspect.changed(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE);
      timeline.getSelectionRange().clear();
      setProfilerMode(ProfilerMode.NORMAL);
      return;
    }

    ListenableFuture<CaptureObject> future = myLoader.loadCapture(captureObject);
    future.addListener(
      () -> {
        try {
          CaptureObject loadedCaptureObject = future.get();
          if (mySelection.finishSelectingCaptureObject(loadedCaptureObject)) {
            Collection<HeapSet> heaps = loadedCaptureObject.getHeapSets();
            if (heaps.size() == 0) {
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
            loadCaptureObject(null, null);
          }
        }
        catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          loadCaptureObject(null, null);
        }
        catch (ExecutionException exception) {
          loadCaptureObject(null, null);
          getLogger().error(exception);
        }
        catch (CancellationException ignored) {
          // No-op: a previous load-capture task is canceled due to another capture being selected and loaded.
        }
      },
      joiner == null ? MoreExecutors.directExecutor() : joiner);

    setProfilerMode(ProfilerMode.EXPANDED);
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

  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  public DurationDataModel<GcDurationData> getGcStats() {
    return myGcStats;
  }

  public String getName() {
    return "MEMORY";
  }

  @Override
  public void onNavigated(@NotNull CodeLocation location) {
    setProfilerMode(ProfilerMode.NORMAL);
  }

  public static class MemoryStageLegends extends LegendComponentModel {

    @NotNull private final StudioProfilers myProfilers;
    @NotNull private final SeriesLegend myJavaLegend;
    @NotNull private final SeriesLegend myNativeLegend;
    @NotNull private final SeriesLegend myGraphicsLegend;
    @NotNull private final SeriesLegend myStackLegend;
    @NotNull private final SeriesLegend myCodeLegend;
    @NotNull private final SeriesLegend myOtherLegend;
    @NotNull private final SeriesLegend myTotalLegend;
    @NotNull private final SeriesLegend myObjectsLegend;

    public MemoryStageLegends(@NotNull StudioProfilers profilers, @NotNull DetailedMemoryUsage usage, @NotNull Range range) {
      super(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
      myJavaLegend = new SeriesLegend(usage.getJavaSeries(), MEMORY_AXIS_FORMATTER, range);
      myNativeLegend = new SeriesLegend(usage.getNativeSeries(), MEMORY_AXIS_FORMATTER, range);
      myGraphicsLegend = new SeriesLegend(usage.getGraphicsSeries(), MEMORY_AXIS_FORMATTER, range);
      myStackLegend = new SeriesLegend(usage.getStackSeries(), MEMORY_AXIS_FORMATTER, range);
      myCodeLegend = new SeriesLegend(usage.getCodeSeries(), MEMORY_AXIS_FORMATTER, range);
      myOtherLegend = new SeriesLegend(usage.getOtherSeries(), MEMORY_AXIS_FORMATTER, range);
      myTotalLegend = new SeriesLegend(usage.getTotalMemorySeries(), MEMORY_AXIS_FORMATTER, range);
      myObjectsLegend = new SeriesLegend(usage.getObjectsSeries(), OBJECT_COUNT_AXIS_FORMATTER, range,
                                         Interpolatable.RoundedSegmentInterpolator);

      add(myTotalLegend);
      add(myJavaLegend);
      add(myNativeLegend);
      add(myGraphicsLegend);
      add(myStackLegend);
      add(myCodeLegend);
      add(myOtherLegend);

      myProfilers = profilers;
      myProfilers.addDependency(this).onChange(ProfilerAspect.AGENT, this::agentStatusChanged);
      agentStatusChanged();
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

    private void agentStatusChanged() {
      if (myProfilers.isAgentAttached()) {
        add(myObjectsLegend);
      }
      else {
        remove(myObjectsLegend);
      }
    }
  }
}
