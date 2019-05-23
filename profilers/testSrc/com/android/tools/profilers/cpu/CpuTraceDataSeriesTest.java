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
package com.android.tools.profilers.cpu;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CpuTraceDataSeriesTest {

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeCpuService myService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuTraceDataSeriesTest", myService, new FakeTransportService(myTimer), new FakeProfilerService(myTimer));

  private CpuProfilerStage.CpuTraceDataSeries mySeries;

  @Before
  public void setUp() {

    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getName()), new FakeIdeProfilerServices(), myTimer);
    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    CpuProfilerStage stage = new CpuProfilerStage(profilers);
    mySeries = stage.getCpuTraceDataSeries();
  }

  @Test
  public void emptySeries() {
    Range maxRange = new Range(-Double.MAX_VALUE, Double.MAX_VALUE);
    assertThat(mySeries.getDataForXRange(maxRange)).isEmpty();
  }

  @Test
  public void validTraceSuccessStatus() {
    Range maxRange = new Range(-Double.MAX_VALUE, Double.MAX_VALUE);
    Cpu.CpuTraceInfo info = Cpu.CpuTraceInfo.newBuilder()
      .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos(1))
      .setToTimestamp(TimeUnit.MICROSECONDS.toNanos(3))
      .build();
    myService.addTraceInfo(info);

    List<SeriesData<CpuTraceInfo>> seriesData = mySeries.getDataForXRange(maxRange);
    assertThat(seriesData).hasSize(1);
    SeriesData<CpuTraceInfo> data = seriesData.get(0);
    assertThat(data).isNotNull();
    assertThat(data.x).isEqualTo(1);
    CpuTraceInfo traceInfo = data.value;
    // Verify duration is also equal
    assertThat(traceInfo.getDurationUs()).isEqualTo(2);
    // As Range also doesn't have an equals method, compare max and min
    assertThat(traceInfo.getRange().getMin()).isWithin(0).of(1);
    assertThat(traceInfo.getRange().getMax()).isWithin(0).of(3);
  }
}
