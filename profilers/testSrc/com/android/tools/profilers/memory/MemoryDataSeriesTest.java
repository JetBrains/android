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

public class MemoryDataSeriesTest {

  private final FakeMemoryService myService = new FakeMemoryService();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryDataSeriesTestChannel", myService);

  @Test
  public void testTransformSingleSampleData() {
    MemoryData memoryData = MemoryData.newBuilder()
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos(222))
      .addMemSamples(MemoryData.MemorySample.newBuilder()
                       .setJavaMem(222).setTimestamp(TimeUnit.MICROSECONDS.toNanos(222)).setTotalMem(222))
      .build();
    myService.setMemoryData(memoryData);
    MemoryDataSeries series =
      new MemoryDataSeries(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, data -> 111L);
    List<SeriesData<Long>> seriesDataList = series.getDataForXRange(new Range(0, Double.MAX_VALUE));
    assertEquals(1, seriesDataList.size());
    assertEquals(222, seriesDataList.get(0).x);
    assertEquals(111, seriesDataList.get(0).value.longValue());
  }

  @Test
  public void testDataIncludeMultipleSamples() {
    MemoryData memoryData = MemoryData.newBuilder()
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos(555))
      .addMemSamples(0, MemoryData.MemorySample.newBuilder()
        .setJavaMem(333).setTimestamp(TimeUnit.MICROSECONDS.toNanos(333)).setTotalMem(333))
      .addMemSamples(1, MemoryData.MemorySample.newBuilder()
        .setNativeMem(444).setTimestamp(TimeUnit.MICROSECONDS.toNanos(444)).setTotalMem(444))
      .build();
    myService.setMemoryData(memoryData);
    MemoryDataSeries series =
      new MemoryDataSeries(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, data -> 111L);
    List<SeriesData<Long>> seriesDataList = series.getDataForXRange(new Range(0, Double.MAX_VALUE));
    assertEquals(2, seriesDataList.size());
    assertEquals(333, seriesDataList.get(0).x);
    assertEquals(111, seriesDataList.get(0).value.longValue());
    assertEquals(444, seriesDataList.get(1).x);
    assertEquals(111, seriesDataList.get(1).value.longValue());
  }
}
