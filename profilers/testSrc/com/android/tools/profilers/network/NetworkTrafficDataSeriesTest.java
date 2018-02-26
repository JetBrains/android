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
import com.android.tools.profiler.proto.NetworkProfiler;
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

public class NetworkTrafficDataSeriesTest {
  private static final ImmutableList<com.android.tools.profiler.proto.NetworkProfiler.NetworkProfilerData> FAKE_DATA =
    new ImmutableList.Builder<com.android.tools.profiler.proto.NetworkProfiler.NetworkProfilerData>()
      .add(FakeNetworkService.newSpeedData(0, 1, 5))
      .add(FakeNetworkService.newSpeedData(2, 2, 4))
      .add(FakeNetworkService.newSpeedData(4, 3, 3))
      .add(FakeNetworkService.newSpeedData(8, 4, 2))
      .add(FakeNetworkService.newSpeedData(16, 5, 1))
      .build();
  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("NetworkTrafficDataSeriesTest", FakeNetworkService.newBuilder().setNetworkDataList(FAKE_DATA).build());
  private NetworkTrafficDataSeries mySentSeries;
  private NetworkTrafficDataSeries myReceivedSeries;

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), new FakeTimer());
    mySentSeries = new NetworkTrafficDataSeries(profilers.getClient().getNetworkClient(), ProfilersTestData.SESSION_DATA,
                                                NetworkTrafficDataSeries.Type.BYTES_SENT);
    myReceivedSeries = new NetworkTrafficDataSeries(profilers.getClient().getNetworkClient(),
                                                    ProfilersTestData.SESSION_DATA,
                                                    NetworkTrafficDataSeries.Type.BYTES_RECEIVED);
  }

  @Test
  public void typeGetBytes() {
    NetworkProfiler.SpeedData data = NetworkProfiler.SpeedData.newBuilder().setReceived(19).setSent(20).build();
    assertEquals(19, NetworkTrafficDataSeries.Type.BYTES_RECEIVED.getBytes(data));
    assertEquals(20, NetworkTrafficDataSeries.Type.BYTES_SENT.getBytes(data));
  }

  @Test
  public void sentDataAllInclusive() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 1L))
      .add(new SeriesData<>(2, 2L))
      .add(new SeriesData<>(4, 3L))
      .add(new SeriesData<>(8, 4L))
      .add(new SeriesData<>(16, 5L))
      .build();
    check(mySentSeries, 0, 16, expected);
  }

  @Test
  public void receivedDataAllInclusive() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 5L))
      .add(new SeriesData<>(2, 4L))
      .add(new SeriesData<>(4, 3L))
      .add(new SeriesData<>(8, 2L))
      .add(new SeriesData<>(16, 1L))
      .build();
    check(myReceivedSeries, 0, 16, expected);
  }

  @Test
  public void sentDataTailExcluded() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 1L))
      .add(new SeriesData<>(2, 2L))
      .add(new SeriesData<>(4, 3L))
      .build();
    check(mySentSeries, 0, 6, expected);
  }

  @Test
  public void receivedDataTailExcluded() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 5L))
      .add(new SeriesData<>(2, 4L))
      .add(new SeriesData<>(4, 3L))
      .build();
    check(myReceivedSeries, 0, 6, expected);
  }

  @Test
  public void sentDataHeadExcluded() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(4, 3L))
      .add(new SeriesData<>(8, 4L))
      .add(new SeriesData<>(16, 5L))
      .build();
    check(mySentSeries, 3, 19, expected);
  }

  @Test
  public void receivedDataEmpty() {
    check(myReceivedSeries, 180, 210, Collections.emptyList());
  }

  private static void check(NetworkTrafficDataSeries series, long startTimeSec, long endTimeSec, List<SeriesData<Long>> expectedResult) {
    Range range = new Range(TimeUnit.SECONDS.toMicros(startTimeSec), TimeUnit.SECONDS.toMicros(endTimeSec));
    List<SeriesData<Long>> dataList = series.getDataForXRange(range);
    assertEquals(expectedResult.size(), dataList.size());
    for (int i = 0; i < dataList.size(); ++i) {
      assertEquals(dataList.get(i).value, expectedResult.get(i).value);
      assertEquals(dataList.get(i).x, TimeUnit.SECONDS.toMicros(expectedResult.get(i).x));
    }
  }
}