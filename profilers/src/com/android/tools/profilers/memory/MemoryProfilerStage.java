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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.AspectModel;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.adtui.model.DurationData.UNSPECIFIED_DURATION;

public class MemoryProfilerStage extends Stage {

  public static class MemoryProfilerSelection {
    // TODO this should persist across stages
    @Nullable private MemoryObjects mySelectedHeap;
    @Nullable private MemoryObjects mySelectedClass;
    @Nullable private MemoryObjects mySelectedInstance;

    @Nullable
    public MemoryObjects getSelectedHeap() {
      return mySelectedHeap;
    }

    @Nullable
    public MemoryObjects getSelectedClass() {
      return mySelectedClass;
    }

    @Nullable
    public MemoryObjects getSelectedInstance() {
      return mySelectedInstance;
    }

    public void setSelectedHeap(@Nullable MemoryObjects heap) {
      mySelectedHeap = heap;
    }

    public void setSelectedClass(@Nullable MemoryObjects klass) {
      mySelectedClass = klass;
    }

    public void setSelectedInstance(@Nullable MemoryObjects instance) {
      mySelectedInstance = instance;
    }

    public void set(@Nullable MemoryObjects heap, @Nullable MemoryObjects klass, @Nullable MemoryObjects instance) {
      mySelectedHeap = heap;
      mySelectedClass = klass;
      mySelectedInstance = instance;
    }
  }

  private final int myProcessId;

  @NotNull
  private AspectModel<MemoryProfilerAspect> myAspect = new AspectModel<>();

  @NotNull
  private final MemoryServiceBlockingStub myClient;

  @NotNull
  private final HeapDumpSampleDataSeries myHeapDumpSampleDataSeries;

  @NotNull
  private final AllocationInfosDataSeries myAllocationInfosDataSeries;

  @NotNull
  private final ExclusiveMemoryObjectsSelection myExclusiveMemoryObjectsSelection;

  @NotNull
  private MemoryProfilerSelection mySelection;

  public MemoryProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myProcessId = profilers.getProcessId();
    myClient = profilers.getClient().getMemoryClient();
    myHeapDumpSampleDataSeries = new HeapDumpSampleDataSeries();
    myAllocationInfosDataSeries = new AllocationInfosDataSeries();
    myExclusiveMemoryObjectsSelection = new ExclusiveMemoryObjectsSelection();
    mySelection = new MemoryProfilerSelection();
  }

  @Override
  public void enter() {
  }

  @Override
  public void exit() {
  }

  @NotNull
  public AspectModel<MemoryProfilerAspect> getAspect() {
    return myAspect;
  }

  public void requestHeapDump() {
    myClient.triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.newBuilder().setAppId(myProcessId).build());
  }

  public DataSeries<HeapDumpDurationData> getHeapDumpSampleDurations() {
    return myHeapDumpSampleDataSeries;
  }

  public void setFocusedHeapDump(@NotNull HeapDumpInfo sample) {
    myExclusiveMemoryObjectsSelection.setFocusedHeapDumpInfo(sample);
    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    timeline.getSelectionRange().set(TimeUnit.NANOSECONDS.toMicros(sample.getStartTime()),
                                     TimeUnit.NANOSECONDS.toMicros(sample.getEndTime()));
  }

  public void setFocusedDiffHeapDump(@NotNull HeapDumpInfo diffSample) {
    myExclusiveMemoryObjectsSelection.setFocusedDiffHeapDumpInfo(diffSample);
  }

  public void setAllocationsTimeRange(long startNs, long endNs) {
    myExclusiveMemoryObjectsSelection.setSelectionRange(startNs, endNs);
    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    timeline.getSelectionRange().set(TimeUnit.NANOSECONDS.toMicros(startNs), TimeUnit.NANOSECONDS.toMicros(endNs));
  }


  public void selectInstance(@Nullable MemoryObjects instance) {
    MemoryObjects previousInstance = mySelection.getSelectedInstance();
    if (previousInstance == instance) {
      return;
    }

    if (previousInstance != null) {
      Disposer.dispose(previousInstance);
    }

    mySelection.setSelectedInstance(instance);
    myAspect.changed(MemoryProfilerAspect.MEMORY_OBJECTS);
  }

  public void selectClass(@Nullable MemoryObjects klass) {
    MemoryObjects previousClass = mySelection.getSelectedClass();
    if (previousClass == klass) {
      return;
    }

    if (previousClass != null) {
      Disposer.dispose(previousClass);
    }

    mySelection.setSelectedClass(klass);
    myAspect.changed(MemoryProfilerAspect.MEMORY_OBJECTS);
  }

  public void selectHeap(@Nullable MemoryObjects heap) {
    MemoryObjects previousHeap = mySelection.getSelectedHeap();
    if (previousHeap == heap) {
      return;
    }

    if (previousHeap != null) {
      Disposer.dispose(previousHeap);
    }

    mySelection.setSelectedHeap(heap);
    myAspect.changed(MemoryProfilerAspect.MEMORY_OBJECTS);
  }

  @NotNull
  public MemoryProfilerSelection getSelection() {
    return mySelection;
  }

  /**
   * @return the actual status, which may be different from the input
   */
  public boolean trackAllocations(boolean enabled) {
    TrackAllocationsResponse response = myClient.trackAllocations(
      MemoryProfiler.TrackAllocationsRequest.newBuilder().setAppId(myProcessId).setEnabled(enabled).build());
    switch (response.getStatus()) {
      case SUCCESS:
        return enabled;
      case IN_PROGRESS:
        return true;
      case NOT_ENABLED:
        return false;
      default:
        return false;
    }
  }

  @NotNull
  public DataSeries<AllocationsDurationData> getAllocationInfosDurations() {
    return myAllocationInfosDataSeries;
  }

  private class ExclusiveMemoryObjectsSelection {
    @Nullable
    private HeapDumpInfo myFocusedHeapDumpInfo = null;

    @Nullable
    private HeapDumpInfo myFocusedDiffHeapDumpInfo = null;

    private long mySelectionStartTime = Long.MAX_VALUE;

    private long mySelectionEndTime = Long.MIN_VALUE;

    public void setFocusedHeapDumpInfo(@NotNull HeapDumpInfo focusedHeapDumpInfo) {
      if (focusedHeapDumpInfo != myFocusedHeapDumpInfo) {
        myFocusedHeapDumpInfo = focusedHeapDumpInfo;
        mySelectionStartTime = Long.MAX_VALUE;
        mySelectionEndTime = Long.MIN_VALUE;
        selectHeap(new HeapDumpObjects(myClient, myProcessId, myFocusedHeapDumpInfo, null));
      }
    }

    @Nullable
    public HeapDumpInfo getFocusedHeapDumpInfo() {
      return myFocusedHeapDumpInfo;
    }

    public void setFocusedDiffHeapDumpInfo(@NotNull HeapDumpInfo focusedDiffHeapDumpInfo) {
      assert myFocusedDiffHeapDumpInfo != null && mySelectionStartTime == Long.MAX_VALUE && mySelectionEndTime == Long.MIN_VALUE;
      if (focusedDiffHeapDumpInfo != myFocusedDiffHeapDumpInfo) {
        myFocusedDiffHeapDumpInfo = focusedDiffHeapDumpInfo;
        myAspect.changed(MemoryProfilerAspect.MEMORY_OBJECTS);
        // TODO implement/set diff view
      }
    }

    @Nullable
    public HeapDumpInfo getFocusedDiffHeapDumpInfo() {
      return myFocusedDiffHeapDumpInfo;
    }

    public void setSelectionRange(long startTime, long endTime) {
      myFocusedHeapDumpInfo = null;
      myFocusedDiffHeapDumpInfo = null;
      mySelectionStartTime = startTime;
      mySelectionEndTime = endTime;
      selectHeap(new AllocationObjects(myClient, myProcessId, startTime, endTime));
      myAspect.changed(MemoryProfilerAspect.MEMORY_OBJECTS);
    }

    public void clearSelection() {
      myFocusedHeapDumpInfo = null;
      myFocusedDiffHeapDumpInfo = null;
      mySelectionStartTime = Long.MAX_VALUE;
      mySelectionEndTime = Long.MIN_VALUE;
      myAspect.changed(MemoryProfilerAspect.MEMORY_OBJECTS);
    }
  }

  private class HeapDumpSampleDataSeries implements DataSeries<HeapDumpDurationData> {
    @Override
    public ImmutableList<SeriesData<HeapDumpDurationData>> getDataForXRange(Range xRange) {
      long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
      long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());
      ListHeapDumpInfosResponse response =
        myClient
          .listHeapDumpInfos(ListDumpInfosRequest.newBuilder().setAppId(myProcessId).setStartTime(rangeMin).setEndTime(rangeMax).build());

      List<SeriesData<HeapDumpDurationData>> seriesData = new ArrayList<>();
      for (HeapDumpInfo info : response.getInfosList()) {
        long startTime = TimeUnit.NANOSECONDS.toMicros(info.getStartTime());
        long endTime = TimeUnit.NANOSECONDS.toMicros(info.getEndTime());
        seriesData.add(new SeriesData<>(startTime, new HeapDumpDurationData(
          info.getEndTime() == UNSPECIFIED_DURATION ? UNSPECIFIED_DURATION : endTime - startTime, info)));
      }

      return ContainerUtil.immutableList(seriesData);
    }
  }

  private class AllocationInfosDataSeries implements DataSeries<AllocationsDurationData> {
    @NotNull
    public List<AllocationsInfo> getDataForXRange(long rangeMinNs, long rangeMaxNs) {
      MemoryRequest.Builder dataRequestBuilder = MemoryRequest.newBuilder()
        .setAppId(myProcessId)
        .setStartTime(rangeMinNs)
        .setEndTime(rangeMaxNs);
      MemoryData response = myClient.getData(dataRequestBuilder.build());
      return response.getAllocationsInfoList();
    }

    @Override
    public ImmutableList<SeriesData<AllocationsDurationData>> getDataForXRange(Range xRange) {
      long bufferNs = TimeUnit.SECONDS.toNanos(1);
      long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin()) - bufferNs;
      long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax()) + bufferNs;

      List<AllocationsInfo> infos = getDataForXRange(rangeMin, rangeMax);

      List<SeriesData<AllocationsDurationData>> seriesData = new ArrayList<>();
      if (infos.size() == 0) {
        return ContainerUtil.immutableList(seriesData);
      }

      for (AllocationsInfo info : infos) {
        long startTimeNs = info.getStartTime();
        long endTimeNs = info.getEndTime();
        long durationUs = endTimeNs == UNSPECIFIED_DURATION ? UNSPECIFIED_DURATION : TimeUnit.NANOSECONDS.toMicros(endTimeNs - startTimeNs);
        seriesData.add(
          new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(startTimeNs), new AllocationsDurationData(durationUs, startTimeNs, endTimeNs)));
      }
      return ContainerUtil.immutableList(seriesData);
    }
  }
}
