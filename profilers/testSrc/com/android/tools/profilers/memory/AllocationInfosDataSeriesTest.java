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

import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.RelativeTimeConverter;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.memory.adapters.AllocationsCaptureObject;
import com.intellij.util.containers.ImmutableList;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class AllocationInfosDataSeriesTest {

  private final FakeMemoryService myService = new FakeMemoryService();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("AllocationInfosDataSeriesTest", myService);

  @Test
  public void testGetDataForXRange() throws Exception {
    MemoryProfiler.MemoryData memoryData = MemoryProfiler.MemoryData.newBuilder()
      .setEndTimestamp(1)
      .addAllocationsInfo(MemoryProfiler.AllocationsInfo.newBuilder().setStartTime(TimeUnit.MICROSECONDS.toNanos(2))
                            .setEndTime(TimeUnit.MICROSECONDS.toNanos(7)))
      .addAllocationsInfo(
        MemoryProfiler.AllocationsInfo.newBuilder().setStartTime(TimeUnit.MICROSECONDS.toNanos(17)).setEndTime(
          DurationData.UNSPECIFIED_DURATION))
      .build();
    myService.setMemoryData(memoryData);

    AllocationInfosDataSeries series =
      new AllocationInfosDataSeries(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, 1, new RelativeTimeConverter(0));
    ImmutableList<SeriesData<CaptureDurationData<AllocationsCaptureObject>>> dataList =
      series.getDataForXRange(new Range(0, Double.MAX_VALUE));

    assertEquals(2, dataList.size());
    SeriesData<CaptureDurationData<AllocationsCaptureObject>> data1 = dataList.get(0);
    assertEquals(2, data1.x);
    assertEquals(5, data1.value.getDuration());
    assertEquals(TimeUnit.MICROSECONDS.toNanos(2), data1.value.getCaptureObject().getStartTimeNs());
    assertEquals(TimeUnit.MICROSECONDS.toNanos(7), data1.value.getCaptureObject().getEndTimeNs());

    SeriesData<CaptureDurationData<AllocationsCaptureObject>> data2 = dataList.get(1);
    assertEquals(17, data2.x);
    assertEquals(DurationData.UNSPECIFIED_DURATION, data2.value.getDuration());
    assertEquals(TimeUnit.MICROSECONDS.toNanos(17), data2.value.getCaptureObject().getStartTimeNs());
    assertEquals(DurationData.UNSPECIFIED_DURATION, data2.value.getCaptureObject().getEndTimeNs());
  }
}