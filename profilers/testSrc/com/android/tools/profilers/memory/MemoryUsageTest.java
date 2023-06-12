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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.google.common.truth.Truth;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MemoryUsageTest {
  // Use an arbitrary stream id because we don't care in the data series.
  private static final int STREAM_ID = 1;

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myService = new FakeTransportService(myTimer);
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryUsageTEst", myService);
  private FakeIdeProfilerServices myIdeProfilerServices;
  private StudioProfilers myProfilers;

  @Before
  public void setup() {
    myIdeProfilerServices = new FakeIdeProfilerServices();
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myIdeProfilerServices, myTimer);

    // insert memory data for new pipeline.
    for (int i = 0; i < 10; i++) {
      myService.addEventToStream(STREAM_ID,
                                 // Space out the data by 10 seconds to work around the 1 second buffer in UnifiedEventDataSeries.
                                 ProfilersTestData.generateMemoryUsageData(
                                       TimeUnit.SECONDS.toMicros(i * 10),
                                       Memory.MemoryUsageData.newBuilder().setTotalMem(i * 10).build()).build());
    }
    myProfilers.getTimeline().getDataRange().set(0, TimeUnit.SECONDS.toMicros(100));
  }

  @Test
  public void testNewPipelineGetData() {
    MemoryUsage usage = new MemoryUsage(myProfilers);

    RangedContinuousSeries rangedSeries = usage.getTotalMemorySeries();
    Range range = rangedSeries.getXRange();

    // Request full range
    range.set(0, TimeUnit.SECONDS.toMicros(100));
    List<SeriesData<Long>> series = rangedSeries.getSeries();
    Truth.assertThat(series.size()).isEqualTo(10);
    for (int i = 0; i < series.size(); i++) {
      Truth.assertThat(series.get(i).value).isEqualTo(MemoryUsage.KB_TO_B * i * 10);
    }

    // Request negative to mid range
    range.set(TimeUnit.SECONDS.toMicros(-50), TimeUnit.SECONDS.toMicros(45));
    series = rangedSeries.getSeries();
    Truth.assertThat(series.size()).isEqualTo(6);
    for (int i = 0; i < series.size(); i++) {
      Truth.assertThat(series.get(i).value).isEqualTo(MemoryUsage.KB_TO_B * i * 10);
    }

    // Request mid to high range
    range.set(TimeUnit.SECONDS.toMicros(45), TimeUnit.SECONDS.toMicros(200));
    series = rangedSeries.getSeries();
    Truth.assertThat(series.size()).isEqualTo(6);
    for (int i = 0; i < series.size(); i++) {
      Truth.assertThat(series.get(i).value).isEqualTo(MemoryUsage.KB_TO_B * ((i + 4) * 10));
    }
  }
}