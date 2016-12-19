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
package com.android.tools.profilers.network;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.FakeGrpcChannel;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.NetworkProfiler.*;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;

public class NetworkMonitorTest {
  private static final ImmutableList<NetworkProfilerData> FAKE_DATA =
    new ImmutableList.Builder<NetworkProfilerData>()
      .add(FakeNetworkService.newSpeedData(0, 1, 2))
      .add(FakeNetworkService.newConnectionData(2, 4))
      .build();

  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("NetworkMonitorTest", FakeNetworkService.newBuilder().setNetworkDataList(FAKE_DATA).build());
  private NetworkMonitor myMonitor;
  private StudioProfilers myProfilers;

  @Before
  public void setUp() {
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices());
    myMonitor = new NetworkMonitor(myProfilers);
  }

  @Test
  public void getName() {
    assertEquals("Network", myMonitor.getName());
  }

  @Test
  public void getSpeedSeries() {
    NetworkTrafficDataSeries series = myMonitor.getSpeedSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED);
    List<SeriesData<Long>> result =series.getDataForXRange(new Range(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(5)));
    assertEquals(1, result.size());
    assertEquals(0, result.get(0).x);
    assertEquals(2, result.get(0).value.longValue());

    series = myMonitor.getSpeedSeries(NetworkTrafficDataSeries.Type.BYTES_SENT);
    result =series.getDataForXRange(new Range(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(5)));
    assertEquals(1, result.size());
    assertEquals(0, result.get(0).x);
    assertEquals(1, result.get(0).value.longValue());
  }

  @Test
  public void getOpenConnectionsSeries() {
    NetworkOpenConnectionsDataSeries series = myMonitor.getOpenConnectionsSeries();
    List<SeriesData<Long>> result =series.getDataForXRange(new Range(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(5)));
    assertEquals(1, result.size());
    assertEquals(TimeUnit.SECONDS.toMicros(2), result.get(0).x);
    assertEquals(4, result.get(0).value.longValue());
  }

  @Test
  public void testExpand() {
    assertNull(myProfilers.getStage());
    myMonitor.expand();
    assertThat(myProfilers.getStage(), instanceOf(NetworkProfilerStage.class));
  }
}