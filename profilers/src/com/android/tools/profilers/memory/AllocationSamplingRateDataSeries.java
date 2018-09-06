/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRateEvent;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

final class AllocationSamplingRateDataSeries implements DataSeries<AllocationSamplingRateDurationData> {
  @NotNull private MemoryServiceGrpc.MemoryServiceBlockingStub myClient;
  @NotNull private final Common.Session mySession;

  public AllocationSamplingRateDataSeries(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client,
                                          @NotNull Common.Session session) {
    myClient = client;
    mySession = session;
  }

  @Override
  public List<SeriesData<AllocationSamplingRateDurationData>> getDataForXRange(Range xRange) {
    long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
    long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());

    // TODO(b/113703171): Query only sampling rate events needed to construct range data.
    MemoryRequest.Builder dataRequestBuilder = MemoryRequest
      .newBuilder()
      .setSession(mySession)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE);
    List<AllocationSamplingRateEvent> events = myClient.getJvmtiData(dataRequestBuilder.build()).getAllocSamplingRateEventsList();

    // MemoryService returns all sampling rate events despite the start/end parameters. We need to filter out those out of range and
    // construct duration data from point data.
    List<SeriesData<AllocationSamplingRateDurationData>> seriesData = new ArrayList<>();
    AllocationSamplingRateEvent oldRateEvent = null;
    AllocationSamplingRateEvent newRateEvent = null;
    for (AllocationSamplingRateEvent event : events) {
      if (event.getTimestamp() > rangeMax) {
        break;
      }
      if (oldRateEvent == null) {
        oldRateEvent = event;
        continue;
      }
      if (event.getTimestamp() > rangeMin) {
        newRateEvent = event;
        seriesData.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(oldRateEvent.getTimestamp()),
                                        new AllocationSamplingRateDurationData(oldRateEvent, newRateEvent)));
        newRateEvent = null;
      }
      oldRateEvent = event;
    }
    if (oldRateEvent != null && newRateEvent == null) {
      // Current sampling rate is still ongoing, add a new duration of {oldRateEvent, INF}.
      newRateEvent = oldRateEvent.toBuilder().setTimestamp(Long.MAX_VALUE).build();
      seriesData.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(oldRateEvent.getTimestamp()),
                                      new AllocationSamplingRateDurationData(oldRateEvent, newRateEvent)));
    }
    return seriesData;
  }
}
