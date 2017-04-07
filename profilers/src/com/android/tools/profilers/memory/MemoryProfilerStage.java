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
import com.android.tools.profilers.*;
import com.android.tools.profilers.memory.adapters.*;
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
  private static class MemoryProfilerSelection {
    // TODO this should persist across stages
    @Nullable private CaptureObject mySelectedCaptureObject;
    @Nullable private HeapObject mySelectedHeap;
    @Nullable private ClassObject mySelectedClass;
    @Nullable private InstanceObject mySelectedInstance;

    @Nullable
    public CaptureObject getSelectedCaptureObject() {
      return mySelectedCaptureObject;
    }

    @Nullable
    public HeapObject getSelectedHeap() {
      return mySelectedHeap;
    }

    @Nullable
    public ClassObject getSelectedClass() {
      return mySelectedClass;
    }

    @Nullable
    public InstanceObject getSelectedInstance() {
      return mySelectedInstance;
    }

    public void setSelectedCaptureObject(@Nullable CaptureObject captureObject) {
      if (mySelectedCaptureObject != null) {
        Disposer.dispose(mySelectedCaptureObject);
      }
      mySelectedCaptureObject = captureObject;
    }

    public void setSelectedHeap(@Nullable HeapObject heap) {
      mySelectedHeap = heap;
    }

    public void setSelectedClass(@Nullable ClassObject klass) {
      mySelectedClass = klass;
    }

    public void setSelectedInstance(@Nullable InstanceObject instance) {
      mySelectedInstance = instance;
    }

    public void set(@Nullable CaptureObject selectedCaptureObject,
                    @Nullable HeapObject heapObject,
                    @Nullable ClassObject classObject,
                    @Nullable InstanceObject instanceObject) {
      setSelectedCaptureObject(selectedCaptureObject);
      setSelectedHeap(heapObject);
      setSelectedClass(classObject);
      setSelectedInstance(instanceObject);
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

  private boolean myAllocationStatus;

  public MemoryProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myProcessId = profilers.getProcessId();
    myClient = profilers.getClient().getMemoryClient();
    myHeapDumpSampleDataSeries = new HeapDumpSampleDataSeries();
    myAllocationInfosDataSeries = new AllocationInfosDataSeries();
    myExclusiveMemoryObjectsSelection = new ExclusiveMemoryObjectsSelection();
    mySelection = new MemoryProfilerSelection();
    myAllocationStatus = false;
  }

  @Override
  public ProfilerMode getProfilerMode() {
    return mySelection.getSelectedCaptureObject() == null ? ProfilerMode.NORMAL : ProfilerMode.EXPANDED;
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


  public void selectInstance(@Nullable InstanceObject instanceObject) {
    InstanceObject previousInstance = mySelection.getSelectedInstance();
    if (previousInstance == instanceObject) {
      return;
    }

    mySelection.setSelectedInstance(instanceObject);
    myAspect.changed(MemoryProfilerAspect.CURRENT_INSTANCE);
  }

  @Nullable
  public InstanceObject getSelectedInstance() {
    return mySelection.getSelectedInstance();
  }

  public void selectClass(@Nullable ClassObject classObject) {
    ClassObject previousClass = mySelection.getSelectedClass();
    if (previousClass == classObject) {
      return;
    }

    mySelection.setSelectedClass(classObject);
    mySelection.setSelectedInstance(null);
    myAspect.changed(MemoryProfilerAspect.CURRENT_CLASS);
  }

  @Nullable
  public ClassObject getSelectedClass() {
    return mySelection.getSelectedClass();
  }

  public void selectHeap(@Nullable HeapObject heapObject) {
    HeapObject previousHeap = mySelection.getSelectedHeap();
    if (previousHeap == heapObject) {
      return;
    }

    mySelection.setSelectedHeap(heapObject);
    mySelection.setSelectedClass(null);
    mySelection.setSelectedInstance(null);
    myAspect.changed(MemoryProfilerAspect.CURRENT_HEAP);
  }

  @Nullable
  public HeapObject getSelectedHeap() {
    return mySelection.getSelectedHeap();
  }

  public void selectCaptureObject(@Nullable CaptureObject captureObject) {
    CaptureObject previousCaptureObject = mySelection.getSelectedCaptureObject();
    if (previousCaptureObject == captureObject) {
      return;
    }

    mySelection.set(captureObject, null, null, null);
    myAspect.changed(MemoryProfilerAspect.CURRENT_CAPTURE);
    getStudioProfilers().modeChanged();
  }

  @Nullable
  public CaptureObject getSelectedCaptureObject() {
    return mySelection.getSelectedCaptureObject();
  }

  @NotNull
  public MemoryProfilerSelection getSelection() {
    return mySelection;
  }

  /**
   * @return the actual status, which may be different from the input
   */
  public void trackAllocations(boolean enabled) {
    TrackAllocationsResponse response = myClient.trackAllocations(
      MemoryProfiler.TrackAllocationsRequest.newBuilder().setAppId(myProcessId).setEnabled(enabled).build());
    myAllocationStatus = enabled && (
      response.getStatus() == TrackAllocationsResponse.Status.SUCCESS ||
      response.getStatus() == TrackAllocationsResponse.Status.IN_PROGRESS);
    myAspect.changed(MemoryProfilerAspect.LEGACY_ALLOCATION);
  }

  public boolean isTrackingAllocations() {
    return myAllocationStatus;
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
        selectCaptureObject(new HeapDumpCaptureObject(myClient, myProcessId, myFocusedHeapDumpInfo, null));
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
        myAspect.changed(MemoryProfilerAspect.CURRENT_CAPTURE);
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
      selectCaptureObject(new AllocationsCaptureObject(myClient, myProcessId, startTime, endTime));
      myAspect.changed(MemoryProfilerAspect.CURRENT_CAPTURE);
    }

    public void clearSelection() {
      myFocusedHeapDumpInfo = null;
      myFocusedDiffHeapDumpInfo = null;
      mySelectionStartTime = Long.MAX_VALUE;
      mySelectionEndTime = Long.MIN_VALUE;
      selectCaptureObject(null);
      myAspect.changed(MemoryProfilerAspect.CURRENT_CAPTURE);
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
