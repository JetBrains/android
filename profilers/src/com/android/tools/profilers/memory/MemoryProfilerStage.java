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

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.memory.adapters.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class MemoryProfilerStage extends Stage {
  private static Logger getLogger() {
    return Logger.getInstance(MemoryProfilerStage.class);
  }

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 5, 5);
  private static final BaseAxisFormatter OBJECT_COUNT_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 5, "");

  private final DetailedMemoryUsage myDetailedMemoryUsage;
  private final AxisComponentModel myMemoryAxis;
  private final AxisComponentModel myObjectsAxis;
  private final MemoryStageLegends myLegends;

  private final int myProcessId;
  private DurationDataModel<GcDurationData> myGcCount;

  @NotNull
  private AspectModel<MemoryProfilerAspect> myAspect = new AspectModel<>();

  private final MemoryServiceBlockingStub myClient;
  private final DurationDataModel<CaptureDurationData<HeapDumpCaptureObject>> myHeapDumpDurations;
  private final DurationDataModel<CaptureDurationData<AllocationsCaptureObject>> myAllocationDurations;
  private final CaptureObjectLoader myLoader;
  private final MemoryProfilerSelection mySelection;
  private final MemoryProfilerConfiguration myConfiguration;
  private final EventMonitor myEventMonitor;
  private boolean myTrackingAllocations;

  public MemoryProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CaptureObjectLoader());
  }

  @VisibleForTesting
  MemoryProfilerStage(@NotNull StudioProfilers profilers, @NotNull CaptureObjectLoader loader) {
    super(profilers);
    myProcessId = profilers.getProcessId();
    myClient = profilers.getClient().getMemoryClient();
    HeapDumpSampleDataSeries heapDumpSeries =
      new HeapDumpSampleDataSeries(profilers.getClient().getMemoryClient(), profilers.getProcessId());
    AllocationInfosDataSeries allocationSeries =
      new AllocationInfosDataSeries(profilers.getClient().getMemoryClient(), profilers.getProcessId());
    myLoader = loader;

    Range viewRange = profilers.getTimeline().getViewRange();

    myHeapDumpDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, heapDumpSeries));
    myAllocationDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, allocationSeries));
    mySelection = new MemoryProfilerSelection(this);
    myConfiguration = new MemoryProfilerConfiguration(this);

    myTrackingAllocations = false; // TODO sync with current legacy allocation tracker status

    myDetailedMemoryUsage = new DetailedMemoryUsage(profilers);

    myMemoryAxis = new AxisComponentModel(myDetailedMemoryUsage.getMemoryRange(), MEMORY_AXIS_FORMATTER);
    myMemoryAxis.setClampToMajorTicks(true);

    myObjectsAxis = new AxisComponentModel(myDetailedMemoryUsage.getObjectsRange(), OBJECT_COUNT_AXIS_FORMATTER);
    myObjectsAxis.setClampToMajorTicks(true);

    myLegends = new MemoryStageLegends(myDetailedMemoryUsage, profilers.getTimeline().getDataRange());

    myGcCount = new DurationDataModel<>(new RangedSeries<>(viewRange, new GcStatsDataSeries(myClient, myProcessId)));

    myEventMonitor = new EventMonitor(profilers);
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
    getStudioProfilers().getUpdater().register(myGcCount);
  }

  @Override
  public void exit() {
    myEventMonitor.exit();
    getStudioProfilers().getUpdater().unregister(myDetailedMemoryUsage);
    getStudioProfilers().getUpdater().unregister(myHeapDumpDurations);
    getStudioProfilers().getUpdater().unregister(myAllocationDurations);
    getStudioProfilers().getUpdater().unregister(myMemoryAxis);
    getStudioProfilers().getUpdater().unregister(myObjectsAxis);
    getStudioProfilers().getUpdater().unregister(myLegends);
    getStudioProfilers().getUpdater().unregister(myGcCount);
    selectCapture(null, null);
    myLoader.stop();
  }

  @Override
  public ProfilerMode getProfilerMode() {
    return mySelection.getCaptureObject() == null ? ProfilerMode.NORMAL : ProfilerMode.EXPANDED;
  }

  @NotNull
  public AspectModel<MemoryProfilerAspect> getAspect() {
    return myAspect;
  }

  /**
   * @param loadJoiner if specified, the joiner executor will be passed down to {@link #selectCapture(CaptureObject, Executor)} so
   *                   that the load operation of the CaptureObject will be joined and the CURRENT_LOAD_CAPTURE aspect would be
   *                   fired via the desired executor.
   */
  public void requestHeapDump(@Nullable Executor loadJoiner) {
    MemoryProfiler.TriggerHeapDumpResponse response =
      myClient.triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.newBuilder().setAppId(myProcessId).build());
    switch (response.getStatus()) {
      case SUCCESS:
        selectCapture(new HeapDumpCaptureObject(myClient, myProcessId, response.getInfo(), null), loadJoiner);
        break;
      case IN_PROGRESS:
        getLogger().debug(String.format("A heap dump for %d is already in progress.", myProcessId));
        break;
      case UNSPECIFIED:
      case FAILURE_UNKNOWN:
      case UNRECOGNIZED:
        break;
    }
  }

  public DurationDataModel<CaptureDurationData<HeapDumpCaptureObject>> getHeapDumpSampleDurations() {
    return myHeapDumpDurations;
  }

  /**
   * @param enabled    whether to enable or disable allocation tracking.
   * @param loadJoiner if specified, the joiner executor will be passed down to {@link #selectCapture(CaptureObject, Executor)} so
   *                   that the load operation of the CaptureObject will be joined and the CURRENT_LOAD_CAPTURE aspect would be
   *                   fired via the desired executor.
   * @return the actual status, which may be different from the input
   */
  public void trackAllocations(boolean enabled, @Nullable Executor loadJoiner) {
    TrackAllocationsResponse response = myClient.trackAllocations(
      MemoryProfiler.TrackAllocationsRequest.newBuilder().setAppId(myProcessId).setEnabled(enabled).build());
    switch (response.getStatus()) {
      case SUCCESS:
        myTrackingAllocations = enabled;
        if (!myTrackingAllocations) {
          selectCapture(new AllocationsCaptureObject(myClient, myProcessId, response.getInfo()), loadJoiner);
        }
        break;
      case IN_PROGRESS:
        myTrackingAllocations = true;
        break;
      case NOT_ENABLED:
        myTrackingAllocations = false;
        break;
      case UNSPECIFIED:
      case FAILURE_UNKNOWN:
      case UNRECOGNIZED:
        break;
    }
    myAspect.changed(MemoryProfilerAspect.LEGACY_ALLOCATION);
  }

  public boolean isTrackingAllocations() {
    return myTrackingAllocations;
  }

  @NotNull
  public DurationDataModel<CaptureDurationData<AllocationsCaptureObject>> getAllocationInfosDurations() {
    return myAllocationDurations;
  }

  public void selectInstance(@Nullable InstanceObject instanceObject) {
    mySelection.setInstanceObject(instanceObject);
  }

  @Nullable
  public InstanceObject getSelectedInstance() {
    return mySelection.getInstanceObject();
  }

  public void selectClass(@Nullable ClassObject classObject) {
    mySelection.setClassObject(classObject);
  }

  @Nullable
  public ClassObject getSelectedClass() {
    return mySelection.getClassObject();
  }

  public void selectHeap(@Nullable HeapObject heapObject) {
    mySelection.setHeapObject(heapObject);
  }

  @Nullable
  public HeapObject getSelectedHeap() {
    return mySelection.getHeapObject();
  }

  public void selectCapture(@Nullable CaptureObject captureObject, @Nullable Executor joiner) {
    if (!mySelection.setCaptureObject(captureObject)) {
      return;
    }

    getStudioProfilers().modeChanged();

    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    if (captureObject != null) {
      timeline.getSelectionRange().set(TimeUnit.NANOSECONDS.toMicros(captureObject.getStartTimeNs()),
                                       TimeUnit.NANOSECONDS.toMicros(captureObject.getEndTimeNs()));
      ListenableFuture<CaptureObject> future = myLoader.loadCapture(captureObject);
      future.addListener(() -> {
                           try {
                             CaptureObject loadedCaptureObject = future.get();
                             if (loadedCaptureObject != null && loadedCaptureObject == mySelection.getCaptureObject()) {
                               myAspect.changed(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE);
                             }
                           }
                           catch (InterruptedException ignored) {
                             Thread.currentThread().interrupt();
                             selectCapture(null, null);
                           }
                           catch (ExecutionException | CancellationException ignored) {
                             selectCapture(null, null);
                           }
                         },
                         joiner == null ? MoreExecutors.directExecutor() : joiner);
    }
    else {
      timeline.getSelectionRange().clear();
    }
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

  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  public DurationDataModel<GcDurationData> getGcCount() {
    return myGcCount;
  }

  public String getName() {
    return "Memory";
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

    public MemoryStageLegends(@NotNull DetailedMemoryUsage usage, @NotNull Range range) {
      myJavaLegend = new SeriesLegend(usage.getJavaSeries(), MEMORY_AXIS_FORMATTER, range);
      myNativeLegend = new SeriesLegend(usage.getNativeSeries(), MEMORY_AXIS_FORMATTER, range);
      myGraphicsLegend = new SeriesLegend(usage.getGraphicsSeries(), MEMORY_AXIS_FORMATTER, range);
      myStackLegend = new SeriesLegend(usage.getStackSeries(), MEMORY_AXIS_FORMATTER, range);
      myCodeLegend = new SeriesLegend(usage.getCodeSeries(), MEMORY_AXIS_FORMATTER, range);
      myOtherLegend = new SeriesLegend(usage.getOtherSeries(), MEMORY_AXIS_FORMATTER, range);
      myTotalLegend = new SeriesLegend(usage.getTotalMemorySeries(), MEMORY_AXIS_FORMATTER, range);
      myObjectsLegend = new SeriesLegend(usage.getObjectsSeries(), OBJECT_COUNT_AXIS_FORMATTER, range);

      add(myJavaLegend);
      add(myNativeLegend);
      add(myGraphicsLegend);
      add(myStackLegend);
      add(myCodeLegend);
      add(myOtherLegend);
      add(myTotalLegend);
      add(myObjectsLegend);
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
  }
}
