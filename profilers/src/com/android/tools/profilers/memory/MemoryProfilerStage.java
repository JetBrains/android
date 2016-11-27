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
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryProfiler.ListDumpInfosRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ListHeapDumpInfosResponse;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.AspectModel;
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
  private final AllocationTrackingStatusDataSeries myAllocationTrackingStatusDataSeries;

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
    myAllocationTrackingStatusDataSeries = new AllocationTrackingStatusDataSeries();
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
    myClient.trackAllocations(
      MemoryProfiler.TrackAllocationsRequest.newBuilder().setAppId(myProcessId).setEnabled(enabled).build());
  }

  public DataSeries<DefaultDurationData> getAllocationDumpSampleDurations() {
    return myAllocationTrackingStatusDataSeries;
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

  private class AllocationTrackingStatusDataSeries implements DataSeries<DefaultDurationData> {
    @Override
    public ImmutableList<SeriesData<DefaultDurationData>> getDataForXRange(Range xRange) {
      long bufferNs = TimeUnit.SECONDS.toNanos(1);
      long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin()) - bufferNs;
      long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax()) + bufferNs;
      MemoryProfiler.MemoryRequest.Builder dataRequestBuilder = MemoryProfiler.MemoryRequest.newBuilder()
        .setAppId(myProcessId)
        .setStartTime(rangeMin)
        .setEndTime(rangeMax);
      MemoryProfiler.MemoryData response = myClient.getData(dataRequestBuilder.build());

      List<SeriesData<DefaultDurationData>> seriesData = new ArrayList<>();
      if (response.getAllocationsInfoCount() == 0) {
        return ContainerUtil.immutableList(seriesData);
      }

      int i = 0;
      // TODO use single entry for both start/stop?
      if (!response.getAllocationsInfo(0).getEnabled()) {
        // If the first setting is disabled, it means there was an enabled event outside our range. We'll have to manually patch it.
        long endTime = response.getAllocationsInfo(0).getTimestamp();
        seriesData.add(
          new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(rangeMin),
                           new DefaultDurationData(TimeUnit.NANOSECONDS.toMicros(endTime - rangeMin))));
        i++;
      }
      for (; i < response.getAllocationsInfoCount(); i += 2) {
        AllocationsInfo start = response.getAllocationsInfo(i);
        assert start.getEnabled();
        long startTime = start.getTimestamp();
        long endTime = rangeMax; // To handle the last sample being enabled.
        if (i + 1 < response.getAllocationsInfoCount()) {
          AllocationsInfo end = response.getAllocationsInfo(i + 1);
          assert !end.getEnabled();
          endTime = end.getTimestamp();
        }
        seriesData.add(
          new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(startTime),
                           new DefaultDurationData(TimeUnit.NANOSECONDS.toMicros(endTime - startTime))));
      }

      return ContainerUtil.immutableList(seriesData);
    }
  }
}
