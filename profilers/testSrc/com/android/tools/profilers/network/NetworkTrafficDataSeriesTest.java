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
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.TestGrpcChannel;
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
      .add(TestNetworkService.newSpeedData(0, 1, 5))
      .add(TestNetworkService.newSpeedData(2, 2, 4))
      .add(TestNetworkService.newSpeedData(4, 3, 3))
      .add(TestNetworkService.newSpeedData(8, 4, 2))
      .add(TestNetworkService.newSpeedData(16, 5, 1))
      .build();
  @Rule public TestGrpcChannel<TestNetworkService> myGrpcChannel =
    new TestGrpcChannel<>("NetworkTrafficDataSeriesTest", TestNetworkService.getInstanceForNetworkData(FAKE_DATA));
  private NetworkTrafficDataSeries mySentSeries;
  private NetworkTrafficDataSeries myReceivedSeries;

  @Before
  public void setUp() {
    StudioProfilers profiler = myGrpcChannel.getProfilers();
    mySentSeries = new NetworkTrafficDataSeries(profiler.getClient().getNetworkClient(), TestNetworkService.FAKE_APP_ID,
                                                NetworkTrafficDataSeries.Type.BYTES_SENT);
    myReceivedSeries = new NetworkTrafficDataSeries(profiler.getClient().getNetworkClient(), TestNetworkService.FAKE_APP_ID,
                                                NetworkTrafficDataSeries.Type.BYTES_RECEIVED);
  }

  @Test
  public void sentDataAllInclusive() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 1l))
      .add(new SeriesData<>(2, 2l))
      .add(new SeriesData<>(4, 3l))
      .add(new SeriesData<>(8, 4l))
      .add(new SeriesData<>(16, 5l))
      .build();
    check(mySentSeries, 0, 16, expected);
  }

  @Test
  public void receivedDataAllInclusive() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 5l))
      .add(new SeriesData<>(2, 4l))
      .add(new SeriesData<>(4, 3l))
      .add(new SeriesData<>(8, 2l))
      .add(new SeriesData<>(16, 1l))
      .build();
    check(myReceivedSeries, 0, 16, expected);
  }

  @Test
  public void sentDataTailExcluded() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 1l))
      .add(new SeriesData<>(2, 2l))
      .add(new SeriesData<>(4, 3l))
      .build();
    check(mySentSeries, 0, 6, expected);
  }

  @Test
  public void receivedDataTailExcluded() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 5l))
      .add(new SeriesData<>(2, 4l))
      .add(new SeriesData<>(4, 3l))
      .build();
    check(myReceivedSeries, 0, 6, expected);
  }

  @Test
  public void sentDataHeadExcluded() {
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(4, 3l))
      .add(new SeriesData<>(8, 4l))
      .add(new SeriesData<>(16, 5l))
      .build();
    check(mySentSeries, 3, 19, expected);
  }

  @Test
  public void receivedDataEmpty() {
    check(myReceivedSeries, 180, 210, Collections.emptyList());
  }

  private void check(NetworkTrafficDataSeries series, long startTimeSec, long endTimeSec, List<SeriesData<Long>> expectedResult) {
    Range range = new Range(TimeUnit.SECONDS.toMicros(startTimeSec), TimeUnit.SECONDS.toMicros(endTimeSec));
    List<SeriesData<Long>> dataList = series.getDataForXRange(range);
    assertEquals(expectedResult.size(), dataList.size());
    for (int i = 0; i < dataList.size(); ++i) {
      assertEquals(dataList.get(i).value, expectedResult.get(i).value);
      assertEquals(dataList.get(i).x, TimeUnit.SECONDS.toMicros(expectedResult.get(i).x));
    }
  }
}