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
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkProfiler.ConnectivityData;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class NetworkRadioDataSeriesTest {
  private static final ImmutableList<NetworkProfiler.NetworkProfilerData> FAKE_DATA =
    new ImmutableList.Builder<NetworkProfiler.NetworkProfilerData>()
      .add(FakeNetworkService.newRadioData(0, ConnectivityData.NetworkType.WIFI, ConnectivityData.RadioState.UNSPECIFIED))
      .add(FakeNetworkService.newRadioData(5, ConnectivityData.NetworkType.MOBILE, ConnectivityData.RadioState.HIGH))
      .add(FakeNetworkService.newRadioData(10, ConnectivityData.NetworkType.MOBILE, ConnectivityData.RadioState.LOW))
      .add(FakeNetworkService.newRadioData(15, ConnectivityData.NetworkType.MOBILE, ConnectivityData.RadioState.LOW))
      .add(FakeNetworkService.newRadioData(1000, ConnectivityData.NetworkType.INVALID, ConnectivityData.RadioState.HIGH))
      .add(FakeNetworkService.newRadioData(1005, ConnectivityData.NetworkType.MOBILE, ConnectivityData.RadioState.UNSPECIFIED))
      .build();
  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("NetworkRadioDataSeriesTest", FakeNetworkService.newBuilder().setNetworkDataList(FAKE_DATA).build());
  private NetworkRadioDataSeries mySeries;

  @Before
  public void setUp() {
    StudioProfilers profiler = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices());
    mySeries = new NetworkRadioDataSeries(profiler.getClient().getNetworkClient(), ProfilersTestData.SESSION_DATA);
  }

  @Test
  public void testAllDataIncluded() {
    List<SeriesData<NetworkRadioDataSeries.RadioState>> expected =
      new ImmutableList.Builder<SeriesData<NetworkRadioDataSeries.RadioState>>()
        .add(new SeriesData<>(0, NetworkRadioDataSeries.RadioState.WIFI))
        .add(new SeriesData<>(5, NetworkRadioDataSeries.RadioState.HIGH))
        .add(new SeriesData<>(10, NetworkRadioDataSeries.RadioState.LOW))
        .add(new SeriesData<>(15, NetworkRadioDataSeries.RadioState.LOW))
        .build();
    check(0, 15, expected);
  }

  @Test
  public void testExcludedHeadData() {
    List<SeriesData<NetworkRadioDataSeries.RadioState>> expected =
      new ImmutableList.Builder<SeriesData<NetworkRadioDataSeries.RadioState>>()
        .add(new SeriesData<>(5, NetworkRadioDataSeries.RadioState.HIGH))
        .add(new SeriesData<>(10, NetworkRadioDataSeries.RadioState.LOW))
        .add(new SeriesData<>(15, NetworkRadioDataSeries.RadioState.LOW))
        .build();

    check(4, 16, expected);
  }

  @Test
  public void testExcludedTailData() {
    List<SeriesData<NetworkRadioDataSeries.RadioState>> expected =
      new ImmutableList.Builder<SeriesData<NetworkRadioDataSeries.RadioState>>()
        .add(new SeriesData<>(0, NetworkRadioDataSeries.RadioState.WIFI))
        .add(new SeriesData<>(5, NetworkRadioDataSeries.RadioState.HIGH))
        .add(new SeriesData<>(10, NetworkRadioDataSeries.RadioState.LOW))
        .build();
    check(0, 11, expected);
  }

  @Test
  public void testSingleData() {
    check(9, 13, Collections.singletonList(new SeriesData<>(10, NetworkRadioDataSeries.RadioState.LOW)));
  }

  @Test
  public void invalidNetworkType() {
    check(999, 1002, Collections.singletonList(new SeriesData<>(1000, NetworkRadioDataSeries.RadioState.NONE)));
  }

  @Test
  public void unspecifiedRadioState() {
    check(1004, 1005, Collections.singletonList(new SeriesData<>(1005, NetworkRadioDataSeries.RadioState.NONE)));
  }

  @Test
  public void testEmpty() {
    check(151, 200, Collections.emptyList());
  }

  private void check(long startTimeSec, long endTimeSec, List<SeriesData<NetworkRadioDataSeries.RadioState>> expectedResult) {
    Range range = new Range(TimeUnit.SECONDS.toMicros(startTimeSec), TimeUnit.SECONDS.toMicros(endTimeSec));
    List<SeriesData<NetworkRadioDataSeries.RadioState>> dataList = mySeries.getDataForXRange(range);
    assertEquals(expectedResult.size(), dataList.size());
    for (int i = 0; i < dataList.size(); ++i) {
      assertEquals(dataList.get(i).value, expectedResult.get(i).value);
      assertEquals(dataList.get(i).x, TimeUnit.SECONDS.toMicros(expectedResult.get(i).x));
    }
  }
}