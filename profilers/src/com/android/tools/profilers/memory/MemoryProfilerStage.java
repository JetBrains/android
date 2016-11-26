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
import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.*;
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

public class MemoryProfilerStage extends Stage {
  private final int myProcessId;

  @NotNull
  private AspectModel<MemoryProfilerAspect> myAspect = new AspectModel<>();

  @NotNull
  private final MemoryServiceBlockingStub myClient;

  @NotNull
  private final HeapDumpSampleDataSeries myHeapDumpSampleDataSeries;

  @NotNull
  private final AllocationDumpSampleDataSeries myAllocationDumpSampleDataSeries;

  @Nullable
  private HeapDumpInfo myFocusedHeapDumpInfo;

  @Nullable
  private HeapDumpInfo myFocusedDiffHeapDumpInfo;

  @Nullable
  private MemoryObjects myMemoryObjects = null;

  public MemoryProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myProcessId = profilers.getProcessId();
    myClient = profilers.getClient().getMemoryClient();
    myHeapDumpSampleDataSeries = new HeapDumpSampleDataSeries();
    myAllocationDumpSampleDataSeries = new AllocationDumpSampleDataSeries();
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
    if (myFocusedHeapDumpInfo != sample) {
      myFocusedHeapDumpInfo = sample;
      setMemoryObjects(new HeapDumpObjects(myClient, myProcessId, myFocusedHeapDumpInfo, null));
    }

    if (myFocusedHeapDumpInfo != null) {
      ProfilerTimeline timeline = getStudioProfilers().getTimeline();
      timeline.getSelectionRange().set(TimeUnit.NANOSECONDS.toMicros(myFocusedHeapDumpInfo.getStartTime()),
                                       TimeUnit.NANOSECONDS.toMicros(myFocusedHeapDumpInfo.getEndTime()));
    }
  }

  public void setFocusedDiffHeapDump(@NotNull HeapDumpInfo diffSample) {
    if (myFocusedDiffHeapDumpInfo != diffSample) {
      myFocusedDiffHeapDumpInfo = diffSample;
      myAspect.changed(MemoryProfilerAspect.MEMORY_DETAILS);
    }
  }

  public void setTimeRange(@NotNull Range timeRange) {
    if (getAllocationDumpSampleDurations().getDataForXRange(timeRange).size() > 0) {
      // TODO pull data based on new time range and signal aspect changed
    }
  }

  private void setMemoryObjects(@Nullable MemoryObjects memoryObjects) {
    if (myMemoryObjects == memoryObjects) {
      return;
    }

    if (myMemoryObjects != null) {
      Disposer.dispose(myMemoryObjects);
    }
    myMemoryObjects = memoryObjects;
    myAspect.changed(MemoryProfilerAspect.MEMORY_DETAILS);
  }

  @Nullable
  public MemoryObjects getMemoryObjects() {
    return myMemoryObjects;
  }

  public void setAllocationTracking(boolean enabled) {
    myClient.setAllocationTracking(
      MemoryProfiler.AllocationTrackingRequest.newBuilder().setAppId(myProcessId)
        .setRequestTime(getStudioProfilers().getCurrentDeviceTime()).setEnabled(enabled).build());
  }

  public DataSeries<DurationData> getAllocationDumpSampleDurations() {
    return myAllocationDumpSampleDataSeries;
  }

  private class HeapDumpSampleDataSeries implements DataSeries<HeapDumpDurationData> {
    @Override
    public ImmutableList<SeriesData<HeapDumpDurationData>> getDataForXRange(Range xRange) {
      long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
      long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());
      ListHeapDumpInfosResponse response =
        myClient
          .listHeapDumpInfos(ListDumpInfosRequest.newBuilder().setAppId(myProcessId).setStartTime(rangeMin).setEndTime(rangeMax).build());

      HeapDumpInfo lastCompleted = null;
      List<SeriesData<HeapDumpDurationData>> seriesData = new ArrayList<>();
      for (HeapDumpInfo info : response.getInfosList()) {
        long startTime = TimeUnit.NANOSECONDS.toMicros(info.getStartTime());
        long endTime = TimeUnit.NANOSECONDS.toMicros(info.getEndTime());
        seriesData.add(new SeriesData<>(startTime, new HeapDumpDurationData(
          info.getEndTime() == DurationData.UNSPECIFIED_DURATION ? DurationData.UNSPECIFIED_DURATION : endTime - startTime, info)));
        if (info.getEndTime() != DurationData.UNSPECIFIED_DURATION) {
          lastCompleted = info;
        }
      }

      if (lastCompleted != null) {
        setFocusedHeapDump(lastCompleted);
      }

      return ContainerUtil.immutableList(seriesData);
    }
  }

  private class AllocationDumpSampleDataSeries implements DataSeries<DurationData> {
    @Override
    public ImmutableList<SeriesData<DurationData>> getDataForXRange(Range xRange) {
      long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
      long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());
      ListAllocationDumpInfosResponse response = myClient
        .listAllocationDumpInfos(
          ListDumpInfosRequest.newBuilder().setAppId(myProcessId).setStartTime(rangeMin).setEndTime(rangeMax).build());

      List<SeriesData<DurationData>> seriesData = new ArrayList<>();
      for (AllocationDumpInfo info : response.getInfosList()) {
        long startTime = TimeUnit.NANOSECONDS.toMicros(info.getStartTime());
        long endTime = TimeUnit.NANOSECONDS.toMicros(info.getEndTime());
        seriesData.add(new SeriesData<>(startTime, new DurationData(
          info.getEndTime() == DurationData.UNSPECIFIED_DURATION ? DurationData.UNSPECIFIED_DURATION : endTime - startTime)));
      }

      return ContainerUtil.immutableList(seriesData);
    }
  }
}
