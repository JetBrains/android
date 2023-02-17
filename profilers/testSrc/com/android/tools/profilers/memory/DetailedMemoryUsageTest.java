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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.LineChartModel;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DetailedMemoryUsageTest {
  // Use an arbitrary stream id because we don't care in the data series.
  private static final int STREAM_ID = 1;

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myService = new FakeTransportService(myTimer);
  // FakeMemoryService needed only for legacy pipeline.
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryUsageTEst", myService);
  private FakeIdeProfilerServices myIdeProfilerServices;
  private StudioProfilers myProfilers;
  private MainMemoryProfilerStage myStage;

  @Before
  public void setup() {
    myIdeProfilerServices = new FakeIdeProfilerServices();
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myIdeProfilerServices, myTimer);
    myStage = new MainMemoryProfilerStage(myProfilers);

    // insert memory data for new pipeline.
    for (int i = 0; i < 10; i++) {
      myService.addEventToStream(STREAM_ID,
                                 // Space out the data by 10 seconds to work around the 1 second buffer in UnifiedEventDataSeries.
                                 ProfilersTestData.generateMemoryUsageData(
                                       TimeUnit.SECONDS.toMicros(i * 10),
                                       Memory.MemoryUsageData.newBuilder()
                                         .setTotalMem(i * 10)
                                         .setJavaMem(i * 10 + 1)
                                         .setNativeMem(i * 10 + 2)
                                         .setGraphicsMem(i * 10 + 3)
                                         .setCodeMem(i * 10 + 4)
                                         .setStackMem(i * 10 + 5)
                                         .setOthersMem(i * 10 + 6).build()).build());
    }
    myProfilers.getTimeline().getDataRange().set(0, TimeUnit.SECONDS.toMicros(100));
  }

  @Test
  public void testNewPipelineGetData() {
    DetailedMemoryUsage usage = new DetailedMemoryUsage(myProfilers, myStage);

    List<RangedContinuousSeries> allSeries = new ArrayList<>();
    allSeries.add(usage.getTotalMemorySeries());
    allSeries.add(usage.getJavaSeries());
    allSeries.add(usage.getNativeSeries());
    allSeries.add(usage.getGraphicsSeries());
    allSeries.add(usage.getCodeSeries());
    allSeries.add(usage.getStackSeries());
    allSeries.add(usage.getOtherSeries());

    Range range = allSeries.get(0).getXRange();

    // Request full range
    range.set(0, TimeUnit.SECONDS.toMicros(100));
    for (int i = 0; i < allSeries.size(); i++) {
      List<SeriesData<Long>> series = allSeries.get(i).getSeries();
      Truth.assertThat(series.size()).isEqualTo(10);
      for (int j = 0; j < series.size(); j++) {
        Truth.assertThat(series.get(j).value).isEqualTo(MemoryUsage.KB_TO_B * (j * 10 + i));
      }
    }

    // Request negative to mid range
    range.set(TimeUnit.SECONDS.toMicros(-50), TimeUnit.SECONDS.toMicros(45));
    for (int i = 0; i < allSeries.size(); i++) {
      List<SeriesData<Long>> series = allSeries.get(i).getSeries();
      Truth.assertThat(series.size()).isEqualTo(6);
      for (int j = 0; j < series.size(); j++) {
        Truth.assertThat(series.get(j).value).isEqualTo(MemoryUsage.KB_TO_B * (j * 10 + i));
      }
    }

    // Request mid to high range
    range.set(TimeUnit.SECONDS.toMicros(45), TimeUnit.SECONDS.toMicros(200));
    for (int i = 0; i < allSeries.size(); i++) {
      List<SeriesData<Long>> series = allSeries.get(i).getSeries();
      Truth.assertThat(series.size()).isEqualTo(6);
      for (int j = 0; j < series.size(); j++) {
        Truth.assertThat(series.get(j).value).isEqualTo(MemoryUsage.KB_TO_B * ((j + 4) * 10 + i));
      }
    }
  }

  @Test
  public void rangeChangeTriggersLineCharUpdate() throws Exception {
    DetailedMemoryUsage usage = new DetailedMemoryUsage(myProfilers, myStage);
    CountDownLatch latch = new CountDownLatch(1);
    usage.addDependency(new AspectObserver()).onChange(LineChartModel.Aspect.LINE_CHART, () -> latch.countDown());
    usage.getMemoryRange().set(0, 100);
    Truth.assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
  }
}