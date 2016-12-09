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
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.TestGrpcChannel;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.NetworkProfiler.*;
import static org.junit.Assert.*;

public class NetworkProfilerStageTest {
  private static final ImmutableList<NetworkProfilerData> FAKE_RADIO_DATA =
    new ImmutableList.Builder<NetworkProfilerData>()
      .add(TestNetworkService.newRadioData(5, ConnectivityData.NetworkType.MOBILE, ConnectivityData.RadioState.ACTIVE))
      .build();

  private static final ImmutableList<HttpData> FAKE_HTTP_DATA =
    new ImmutableList.Builder<HttpData>()
      .add(TestNetworkService.newHttpData(7, 0, 7, 14))
      .build();

  @Rule public TestGrpcChannel<TestNetworkService> myGrpcChannel =
    new TestGrpcChannel<>("NetworkProfilerStageTest",
                          TestNetworkService.newBuilder().setNetworkDataList(FAKE_RADIO_DATA).setHttpDataList(FAKE_HTTP_DATA).build());

  private NetworkProfilerStage myStage;

  @Before
  public void setUp() {
    StudioProfilers profilers = myGrpcChannel.getProfilers();
    myStage = new NetworkProfilerStage(profilers);
  }

  @Test
  public void getConnectionsModel() {
    List<HttpData> dataList = myStage.getConnectionsModel().getData(new Range(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(16)));
    assertEquals(1, dataList.size());
    HttpData data= dataList.get(0);
    assertEquals(FAKE_HTTP_DATA.get(0).getStartTimeUs(), data.getStartTimeUs());
    assertEquals(FAKE_HTTP_DATA.get(0).getDownloadingTimeUs(), data.getDownloadingTimeUs());
    assertEquals(FAKE_HTTP_DATA.get(0).getEndTimeUs(), data.getEndTimeUs());
    assertEquals(FAKE_HTTP_DATA.get(0).getMethod(), data.getMethod());
    assertEquals(FAKE_HTTP_DATA.get(0).getUrl(), data.getUrl());
    assertEquals(FAKE_HTTP_DATA.get(0).getTrace(), data.getTrace());
    assertEquals(FAKE_HTTP_DATA.get(0).getResponsePayloadId(), data.getResponsePayloadId());
    assertEquals(FAKE_HTTP_DATA.get(0).getResponseField("connId"), data.getResponseField("connId"));
  }

  @Test
  public void getRadioDataSeries() {
    Range range = new Range(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(10));
    List<SeriesData<NetworkRadioDataSeries.RadioState>> dataList = myStage.getRadioDataSeries().getDataForXRange(range);

    assertEquals(1, dataList.size());
    assertEquals(TimeUnit.SECONDS.toMicros(5), dataList.get(0).x);
    assertEquals(NetworkRadioDataSeries.RadioState.HIGH, dataList.get(0).value);
  }

  @Test
  public void testSelectedConnection() {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 22, 22);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n Content-Type = image/jpeg; ")
      .setResponsePayloadId("payloadId");
    HttpData data = builder.build();

    final boolean[]  connectionChanged = {false};
    myStage.getAspect().addDependency().onChange(NetworkProfilerAspect.ACTIVE_CONNECTION, () ->
      connectionChanged[0] = true
    );

    myStage.setSelectedConnection(data);
    assertNotNull(data.getResponsePayloadFile());
    assertEquals(data, myStage.getSelectedConnection());
    assertEquals(true, connectionChanged[0]);
  }

  @Test
  public void getProfilerMode() {
    myStage.getStudioProfilers().getTimeline().getSelectionRange().clear();
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myStage.getStudioProfilers().getTimeline().getSelectionRange().set(0, 10);
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
  }
}