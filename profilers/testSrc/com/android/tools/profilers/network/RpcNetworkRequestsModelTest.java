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
import com.android.tools.profiler.proto.*;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.TestGrpcChannel;
import com.google.common.collect.ImmutableList;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RpcNetworkRequestsModelTest {
  private static final ImmutableList<HttpData> FAKE_DATA =
    new ImmutableList.Builder<HttpData>()
      .add(TestNetworkService.newHttpData(0, 0, 7, 14))
      .add(TestNetworkService.newHttpData(1, 2, 3, 6))
      .add(TestNetworkService.newHttpData(2, 4, 0, 0))
      .add(TestNetworkService.newHttpData(3, 8, 10, 12))
      .build();

  @Rule public TestGrpcChannel<TestNetworkService> myGrpcChannel =
    new TestGrpcChannel<>("RpcNetworkRequestsModelTest", new TestNetworkService(null, FAKE_DATA));
  private RpcNetworkRequestsModel myModel;

  @Before
  public void setUp() {
    StudioProfilers profilers = myGrpcChannel.getProfilers();
    myModel = new RpcNetworkRequestsModel(profilers.getClient().getNetworkClient(), 12);
  }

  @Test
  public void rangeCanIncludeAllRequests() {
    checkGetData(0, 10, 0, 1, 2, 3);
  }

  @Test
  public void rangeCanExcludeTailRequests() {
    checkGetData(0, 6, 0, 1, 2);
  }

  @Test
  public void rangeCanExcludeHeadRequests() {
    checkGetData(8, 12, 0, 2, 3);
  }

  @Test
  public void rangeCanIncludeRequestsThatAreStillDownloading() {
    checkGetData(1000, 1002, 2);
  }

  @Test
  public void testRequestStartAndEndAreInclusive() {
    checkGetData(6, 8, 0, 1, 2, 3);
  }

  private void checkGetData(long startTimeS, long endTimeS, long... expectedIds) {
    Range range = new Range(TimeUnit.SECONDS.toMicros(startTimeS), TimeUnit.SECONDS.toMicros(endTimeS));
    List<HttpData> actualData = myModel.getData(range);
    assertEquals(expectedIds.length, actualData.size());

    for (int i = 0; i < actualData.size(); ++i) {
      HttpData data = actualData.get(i);
      long id = expectedIds[i];
      assertEquals(id, data.getId());
      assertEquals(FAKE_DATA.get((int)id).getStartTimeUs(), data.getStartTimeUs());
      assertEquals(FAKE_DATA.get((int)id).getDownloadingTimeUs(), data.getDownloadingTimeUs());
      assertEquals(FAKE_DATA.get((int)id).getEndTimeUs(), data.getEndTimeUs());
      assertEquals(FAKE_DATA.get((int)id).getMethod(), data.getMethod());
      assertEquals(FAKE_DATA.get((int)id).getUrl(), data.getUrl());
      assertEquals(FAKE_DATA.get((int)id).getTrace(), data.getTrace());
      assertEquals(FAKE_DATA.get((int)id).getResponsePayloadId(), data.getResponsePayloadId());
      assertEquals(FAKE_DATA.get((int)id).getResponseField("connId"), data.getResponseField("connId"));
    }
  }
}