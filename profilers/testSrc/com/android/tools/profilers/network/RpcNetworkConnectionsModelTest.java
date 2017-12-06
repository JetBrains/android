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
import com.android.tools.profilers.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf3jarjar.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class RpcNetworkConnectionsModelTest {
  private static final String FAKE_PAYLOAD_ID = "Test Payload";
  private static final String FAKE_REQUEST_HEADERS = "User-Agent = Customized\n Accept = text/plain";

  private static final ImmutableList<HttpData> FAKE_DATA =
    new ImmutableList.Builder<HttpData>()
      .add(FakeNetworkService.newHttpDataBuilder(0, 0, 7, 14)
             .setRequestFields(FAKE_REQUEST_HEADERS)
             .addJavaThread(new HttpData.JavaThread(0, "threadA"))
             .build())
      .add(FakeNetworkService.newHttpDataBuilder(1, 2, 3, 6)
             .setRequestFields(FAKE_REQUEST_HEADERS)
             .addJavaThread(new HttpData.JavaThread(1, "threadB"))
             .build())
      .add(FakeNetworkService.newHttpDataBuilder(2, 4, 0, 0)
             .setRequestFields(FAKE_REQUEST_HEADERS)
             .addJavaThread(new HttpData.JavaThread(2, "threadC"))
             .build())
      .add(FakeNetworkService.newHttpDataBuilder(3, 8, 10, 12)
             .setRequestFields(FAKE_REQUEST_HEADERS)
             .addJavaThread(new HttpData.JavaThread(3, "threadD"))
             .build())
      .build();

  private FakeProfilerService myProfilerService = new FakeProfilerService(false);

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("RpcNetworkConnectionsModelTest", myProfilerService,
                                                                   FakeNetworkService.newBuilder().setHttpDataList(FAKE_DATA).build());
  private NetworkConnectionsModel myModel;
  private StudioProfilers myProfilers;

  @Before
  public void setUp() {
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices());
    myModel = new RpcNetworkConnectionsModel(myProfilers.getClient().getProfilerClient(), myProfilers.getClient().getNetworkClient(), 12,
                                             ProfilersTestData.SESSION_DATA);
  }

  @After
  public void tearDown() throws Exception {
    myProfilers.stop();
  }

  @Test
  public void requestResponsePayload() {
    myProfilerService.addFile(FAKE_PAYLOAD_ID, ByteString.copyFromUtf8("Dummy Contents"));
    HttpData data = new HttpData.Builder(0, 0, 0, 0).setResponsePayloadId(FAKE_PAYLOAD_ID).build();
    assertEquals("Dummy Contents", myModel.requestResponsePayload(data).toStringUtf8());
  }

  @Test
  public void emptyRequestResponsePayload() {
    HttpData data = new HttpData.Builder(0, 0, 0, 0).build();
    assertEquals(ByteString.EMPTY, myModel.requestResponsePayload(data));
    data = new HttpData.Builder(0, 0, 0, 0).setResponsePayloadId("").build();
    assertEquals(ByteString.EMPTY, myModel.requestResponsePayload(data));
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
      assertEquals(FAKE_DATA.get((int)id).getStackTrace().getTrace(), data.getStackTrace().getTrace());
      assertEquals(FAKE_DATA.get((int)id).getResponsePayloadId(), data.getResponsePayloadId());
      assertEquals(FAKE_DATA.get((int)id).getResponseField("connId"), data.getResponseField("connId"));
      assertEquals(FAKE_DATA.get((int)id).getJavaThreads().get(0).getId(), data.getJavaThreads().get(0).getId());
      assertEquals(FAKE_DATA.get((int)id).getJavaThreads().get(0).getName(), data.getJavaThreads().get(0).getName());
      ImmutableMap<String, String> requestHeaders = data.getRequestHeaders();
      assertEquals(2, requestHeaders.size());
      assertEquals("Customized", requestHeaders.get("user-agent"));
      assertEquals("text/plain", requestHeaders.get("accept"));
    }
  }
}