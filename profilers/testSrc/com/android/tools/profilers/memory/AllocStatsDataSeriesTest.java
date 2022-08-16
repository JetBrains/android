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

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE;
import static org.junit.Assert.assertEquals;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory.MemoryAllocStatsData;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

public final class AllocStatsDataSeriesTest {
  private final FakeTimer myTimer = new FakeTimer();
  private final FakeMemoryService myService = new FakeMemoryService();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer);
  @NotNull private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("AllocStatsDataSeriesTest", myTransportService, myService,
                                                                   new FakeProfilerService(myTimer),
                                                                   new FakeEventService(),
                                                                   new FakeCpuService());

  public AllocStatsDataSeriesTest() {
    myIdeProfilerServices.enableEventsPipeline(true);
  }

  @Test
  public void testGetDataForXRange() {
    myTransportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE);
    StudioProfilers studioProfilers =
      new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myIdeProfilerServices, myTimer);
    studioProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));

    MemoryAllocStatsData stats1 = MemoryAllocStatsData.newBuilder().setJavaAllocationCount(1000).setJavaFreeCount(2000).build();
    MemoryAllocStatsData stats2 = MemoryAllocStatsData.newBuilder().setJavaAllocationCount(1500).setJavaFreeCount(2500).build();
    long timestamp1 = TimeUnit.MICROSECONDS.toNanos(3);
    long timestamp2 = TimeUnit.MICROSECONDS.toNanos(14);

    myTransportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, Common.Event.newBuilder()
      .setPid(FakeTransportService.FAKE_PROCESS.getPid())
      .setTimestamp(timestamp1)
      .setKind(Common.Event.Kind.MEMORY_ALLOC_STATS)
      .setMemoryAllocStats(stats1)
      .build());
    myTransportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, Common.Event.newBuilder()
      .setPid(FakeTransportService.FAKE_PROCESS.getPid())
      .setTimestamp(timestamp2)
      .setKind(Common.Event.Kind.MEMORY_ALLOC_STATS)
      .setMemoryAllocStats(stats2)
      .build());

    AllocStatsDataSeries series =
      new AllocStatsDataSeries(studioProfilers,
                               sample -> (long)sample.getJavaAllocationCount());
    List<SeriesData<Long>> dataList = series.getDataForRange(new Range(0, Double.MAX_VALUE));
    assertEquals(2, dataList.size());
    assertEquals(3, dataList.get(0).x);
    assertEquals(1000, dataList.get(0).value.longValue());
    assertEquals(14, dataList.get(1).x);
    assertEquals(1500, dataList.get(1).value.longValue());

    series = new AllocStatsDataSeries(studioProfilers,
                                      sample -> (long)sample.getJavaFreeCount());
    dataList = series.getDataForRange(new Range(0, Double.MAX_VALUE));
    assertEquals(2, dataList.size());
    assertEquals(3, dataList.get(0).x);
    assertEquals(2000, dataList.get(0).value.longValue());
    assertEquals(14, dataList.get(1).x);
    assertEquals(2500, dataList.get(1).value.longValue());
  }
}