/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.ProfilersTestData;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class GcStatsDataSeriesTest {

  private final FakeMemoryService myService = new FakeMemoryService();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("AllocationInfosDataSeriesTest", myService);

  @Test
  public void testGetDataForXRange() throws Exception {
    MemoryData memoryData = MemoryData.newBuilder()
      .setEndTimestamp(1)
      .addGcStatsSamples(
        MemoryData.GcStatsSample.newBuilder().setStartTime(TimeUnit.MICROSECONDS.toNanos(3))
          .setEndTime(TimeUnit.MICROSECONDS.toNanos(7)))
      .addGcStatsSamples(
        MemoryData.GcStatsSample.newBuilder().setStartTime(TimeUnit.MICROSECONDS.toNanos(14))
          .setEndTime(TimeUnit.MICROSECONDS.toNanos(17)))
      .build();
    myService.setMemoryData(memoryData);

    GcStatsDataSeries series = new GcStatsDataSeries(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA);
    List<SeriesData<GcDurationData>> dataList = series.getDataForXRange(new Range(0, Double.MAX_VALUE));

    assertEquals(2, dataList.size());
    SeriesData<GcDurationData> data1 = dataList.get(0);
    assertEquals(3, data1.x);
    assertEquals(4, data1.value.getDuration());

    SeriesData<GcDurationData> data2 = dataList.get(1);
    assertEquals(14, data2.x);
    assertEquals(3, data2.value.getDuration());
  }
}