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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.FakeGrpcChannel;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class NetworkOpenConnectionsDataSeriesTest {
  private static final ImmutableList<com.android.tools.profiler.proto.NetworkProfiler.NetworkProfilerData> FAKE_DATA =
    new ImmutableList.Builder<com.android.tools.profiler.proto.NetworkProfiler.NetworkProfilerData>()
      .add(FakeNetworkService.newConnectionData(0, 1))
      .add(FakeNetworkService.newConnectionData(2, 2))
      .add(FakeNetworkService.newConnectionData(4, 3))
      .add(FakeNetworkService.newConnectionData(8, 4))
      .add(FakeNetworkService.newConnectionData(16, 5))
      .build();

  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("NetworkOpenConnectionsDataSeriesTest", FakeNetworkService.newBuilder().setNetworkDataList(FAKE_DATA).build());
  private NetworkOpenConnectionsDataSeries mySeries;

  @Before
  public void setUp() {
    StudioProfilers profiler = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), new FakeTimer());
    mySeries = new NetworkOpenConnectionsDataSeries(profiler.getClient().getNetworkClient(), FakeNetworkService.FAKE_APP_ID, ProfilersTestData.SESSION_DATA);
  }

  @Test
  public void dataAllInclusive() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 1L))
      .add(new SeriesData<>(2, 2L))
      .add(new SeriesData<>(4, 3L))
      .add(new SeriesData<>(8, 4L))
      .add(new SeriesData<>(16, 5L))
      .build();
    check(0, 16, expected);
  }

  @Test
  public void dataTailExcluded() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 1L))
      .add(new SeriesData<>(2, 2L))
      .add(new SeriesData<>(4, 3L))
      .build();
    check(0, 6, expected);
  }

  @Test
  public void dataHeadExcluded() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(4, 3L))
      .add(new SeriesData<>(8, 4L))
      .add(new SeriesData<>(16, 5L))
      .build();
    check(3, 19, expected);
  }

  @Test
  public void receivedDataEmpty() {
    check(180, 210, Collections.emptyList());
  }

  private void check(long startTimeSec, long endTimeSec, List<SeriesData<Long>> expectedResult) {
    Range range = new Range(TimeUnit.SECONDS.toMicros(startTimeSec), TimeUnit.SECONDS.toMicros(endTimeSec));
    List<SeriesData<Long>> dataList = mySeries.getDataForXRange(range);
    assertEquals(expectedResult.size(), dataList.size());
    for (int i = 0; i < dataList.size(); ++i) {
      assertEquals(dataList.get(i).value, expectedResult.get(i).value);
      assertEquals(dataList.get(i).x, TimeUnit.SECONDS.toMicros(expectedResult.get(i).x));
    }
  }
}