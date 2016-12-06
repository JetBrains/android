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
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.memory.adapters.AllocationsCaptureObject;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.adtui.model.DurationData.UNSPECIFIED_DURATION;

class AllocationInfosDataSeries implements DataSeries<CaptureDurationData<AllocationsCaptureObject>> {
  @NotNull private final MemoryServiceGrpc.MemoryServiceBlockingStub myClient;
  private final int myProcessId;

  public AllocationInfosDataSeries(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client, int processId) {
    myClient = client;
    myProcessId = processId;
  }

  @NotNull
  public List<MemoryProfiler.MemoryData.AllocationsInfo> getDataForXRange(long rangeMinNs, long rangeMaxNs) {
    MemoryProfiler.MemoryRequest.Builder dataRequestBuilder = MemoryProfiler.MemoryRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTime(rangeMinNs)
      .setEndTime(rangeMaxNs);
    MemoryProfiler.MemoryData response = myClient.getData(dataRequestBuilder.build());
    return response.getAllocationsInfoList();
  }

  @Override
  public ImmutableList<SeriesData<CaptureDurationData<AllocationsCaptureObject>>> getDataForXRange(Range xRange) {
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin()) - bufferNs;
    long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax()) + bufferNs;

    List<MemoryProfiler.MemoryData.AllocationsInfo> infos = getDataForXRange(rangeMin, rangeMax);

    List<SeriesData<CaptureDurationData<AllocationsCaptureObject>>> seriesData = new ArrayList<>();
    if (infos.size() == 0) {
      return ContainerUtil.immutableList(seriesData);
    }

    for (MemoryProfiler.MemoryData.AllocationsInfo info : infos) {
      long startTimeNs = info.getStartTime();
      long endTimeNs = info.getEndTime();
      long durationUs = endTimeNs == UNSPECIFIED_DURATION ? UNSPECIFIED_DURATION : TimeUnit.NANOSECONDS.toMicros(endTimeNs - startTimeNs);
      seriesData.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(startTimeNs),
                                      new CaptureDurationData<>(
                                        durationUs, new AllocationsCaptureObject(myClient, myProcessId, startTimeNs, endTimeNs))));
    }
    return ContainerUtil.immutableList(seriesData);
  }
}
