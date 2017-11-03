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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.RelativeTimeConverter;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.LegacyAllocationCaptureObject;
import com.android.tools.profilers.memory.adapters.LiveAllocationCaptureObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class AllocationInfosDataSeries extends CaptureDataSeries<CaptureObject> {
  @Nullable private MemoryProfilerStage myStage;

  public AllocationInfosDataSeries(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client,
                                   @Nullable Common.Session session,
                                   int processId,
                                   @NotNull RelativeTimeConverter converter,
                                   @NotNull FeatureTracker featureTracker,
                                   @Nullable MemoryProfilerStage stage) {
    super(client, session, processId, converter, featureTracker);
    myStage = stage;
  }

  @NotNull
  private List<MemoryProfiler.AllocationsInfo> getDataForXRange(long rangeMinNs, long rangeMaxNs) {
    MemoryProfiler.MemoryRequest.Builder dataRequestBuilder = MemoryProfiler.MemoryRequest.newBuilder()
      .setProcessId(myProcessId)
      .setSession(mySession)
      .setStartTime(rangeMinNs)
      .setEndTime(rangeMaxNs);
    MemoryProfiler.MemoryData response = myClient.getData(dataRequestBuilder.build());
    return response.getAllocationsInfoList();
  }

  @Override
  public List<SeriesData<CaptureDurationData<CaptureObject>>> getDataForXRange(Range xRange) {
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin()) - bufferNs;
    long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax()) + bufferNs;

    List<MemoryProfiler.AllocationsInfo> infos = getDataForXRange(rangeMin, rangeMax);

    List<SeriesData<CaptureDurationData<CaptureObject>>> seriesData = new ArrayList<>();
    for (MemoryProfiler.AllocationsInfo info : infos) {
      long startTimeNs = info.getStartTime();
      long durationUs = getDurationUs(startTimeNs, info.getEndTime());

      seriesData.add(
        new SeriesData<>(
          getHostTime(startTimeNs),
          new CaptureDurationData<>(durationUs, !info.getLegacy(), !info.getLegacy(), new CaptureEntry<>(
            info,
            () -> {
              if (info.getLegacy()) {
                return new LegacyAllocationCaptureObject(myClient, mySession, myProcessId, info, myConverter, myFeatureTracker);
              }
              else {
                return new LiveAllocationCaptureObject(myClient, mySession, myProcessId, startTimeNs, null, myStage);
              }
            }))));
    }
    return seriesData;
  }
}
