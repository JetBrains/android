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
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRange;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

final class AllocationSamplingRangeDataSeries implements DataSeries<AllocationSamplingRangeDurationData> {
  @NotNull private MemoryServiceGrpc.MemoryServiceBlockingStub myClient;
  @NotNull private final Common.Session mySession;

  public AllocationSamplingRangeDataSeries(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client,
                                          @NotNull Common.Session session) {
    myClient = client;
    mySession = session;
  }

  @Override
  public List<SeriesData<AllocationSamplingRangeDurationData>> getDataForXRange(Range xRange) {
    long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
    long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());

    MemoryProfiler.MemoryRequest.Builder dataRequestBuilder = MemoryProfiler.MemoryRequest.newBuilder()
                                                                                          .setSession(mySession)
                                                                                          .setStartTime(rangeMin)
                                                                                          .setEndTime(rangeMax);
    List<AllocationSamplingRange> infos = myClient.getData(dataRequestBuilder.build()).getAllocationSamplingRangesList();
    List<SeriesData<AllocationSamplingRangeDurationData>> seriesData = new ArrayList<>();
    for (AllocationSamplingRange info : infos) {
      long hostStartTimeUs = TimeUnit.NANOSECONDS.toMicros(info.getStartTime());
      seriesData.add(new SeriesData<>(hostStartTimeUs, new AllocationSamplingRangeDurationData(info)));
    }
    return seriesData;
  }
}
