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
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.Payload;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.profiler.protobuf3jarjar.ByteString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

public class RpcNetworkConnectionsModelTest {
  private static final String FAKE_REQUEST_PAYLOAD_ID = "payloadRequest";
  private static final String FAKE_RESPONSE_PAYLOAD_ID = "payloadResponse";
  private static final String FAKE_REQUEST_HEADERS = "User-Agent = Customized\n Accept = text/plain";

  private static final ImmutableList<HttpData> FAKE_DATA =
    new ImmutableList.Builder<HttpData>()
      // Finished request (1-6)
      .add(TestHttpData.newBuilder(1, 1, 6, new HttpData.JavaThread(1, "threadA"))
             .setRequestFields(FAKE_REQUEST_HEADERS)
             .setRequestPayloadId(FAKE_REQUEST_PAYLOAD_ID + 1)
             .setResponsePayloadId(FAKE_RESPONSE_PAYLOAD_ID + 1)
             .build())
      // Finished request (2-5)
      .add(TestHttpData.newBuilder(2, 2, 5, new HttpData.JavaThread(2, "threadB"))
             .setRequestFields(FAKE_REQUEST_HEADERS)
             .setRequestPayloadId(FAKE_REQUEST_PAYLOAD_ID + 2)
             .setResponsePayloadId(FAKE_RESPONSE_PAYLOAD_ID + 2)
             .build())
      // Unfinished request (3-?)
      .add(TestHttpData.newBuilder(3, 3, 0, 0, 0, new HttpData.JavaThread(3, "threadC"))
             .setRequestFields(FAKE_REQUEST_HEADERS)
             // No request / response payload, hasn't started uploading yet
             .build())
      // Unfinished request (4-?)
      .add(TestHttpData.newBuilder(4, 4, 5, 0, 0, new HttpData.JavaThread(4, "threadD"))
             .setRequestFields(FAKE_REQUEST_HEADERS)
             // No response payload, hasn't finished downloading yet
             .build())
      // Finished request (8-12)
      .add(TestHttpData.newBuilder(5, 8, 9, 10, 12, new HttpData.JavaThread(5, "threadE"))
             .setRequestFields(FAKE_REQUEST_HEADERS)
             .setRequestPayloadId(FAKE_REQUEST_PAYLOAD_ID + 5)
             .setResponsePayloadId(FAKE_RESPONSE_PAYLOAD_ID + 5)
             .build())
      .build();

  private FakeProfilerService myProfilerService = new FakeProfilerService(false);

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("RpcNetworkConnectionsModelTest", myProfilerService,
                                                                   FakeNetworkService.newBuilder().setHttpDataList(FAKE_DATA).build());
  private NetworkConnectionsModel myModel;

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices());
    myModel = new RpcNetworkConnectionsModel(profilers.getClient().getProfilerClient(), profilers.getClient().getNetworkClient(), 12,
                                             ProfilersTestData.SESSION_DATA);

    for (int i = 0; i < FAKE_DATA.size(); i++) {
      long id = FAKE_DATA.get(i).getId();
      myProfilerService.addFile(FAKE_REQUEST_PAYLOAD_ID + id, ByteString.copyFromUtf8("Request Body " + i));
      myProfilerService.addFile(FAKE_RESPONSE_PAYLOAD_ID + id, ByteString.copyFromUtf8("Response Body " + i));
    }
  }

  @Test
  public void nonEmptyBytes() {
    myProfilerService.addFile("dummyid", ByteString.copyFromUtf8("Dummy Contents"));
    assertThat(myModel.requestBytes("dummyid").toStringUtf8()).isEqualTo("Dummy Contents");
  }

  @Test
  public void notFoundBytes_ReturnsEmptyByteString() {
    assertThat(myModel.requestBytes("invalid id")).isEqualTo(ByteString.EMPTY);
  }

  @Test
  public void rangeCanIncludeAllRequests() {
    checkGetData(0, 10, 1, 2, 3, 4, 5);
  }

  @Test
  public void rangeCanExcludeTailRequests() {
    checkGetData(0, 6, 1, 2, 3, 4);
  }

  @Test
  public void rangeCanExcludeHeadRequests() {
    checkGetData(8, 12, 3, 4, 5);
  }

  @Test
  public void rangeCanIncludeRequestsThatAreStillDownloading() {
    checkGetData(1000, 1001, 3, 4);
  }

  @Test
  public void testRequestStartAndEndAreInclusive() {
    checkGetData(6, 8, 1, 3, 4, 5);
  }

  private void checkGetData(long startTimeS, long endTimeS, long... expectedIds) {
    Range range = new Range(TimeUnit.SECONDS.toMicros(startTimeS), TimeUnit.SECONDS.toMicros(endTimeS));
    List<HttpData> actualData = myModel.getData(range);
    assertThat(actualData).hasSize(expectedIds.length);

    for (int i = 0; i < actualData.size(); ++i) {
      HttpData data = actualData.get(i);
      long id = expectedIds[i];
      assertThat(data.getId()).isEqualTo(id);
      HttpData expectedData = FAKE_DATA.stream().filter(d -> d.getId() == id).findFirst().get();

      assertThat(data.getStartTimeUs()).isEqualTo(expectedData.getStartTimeUs());
      assertThat(data.getUploadedTimeUs()).isEqualTo(expectedData.getUploadedTimeUs());
      assertThat(data.getDownloadingTimeUs()).isEqualTo(expectedData.getDownloadingTimeUs());
      assertThat(data.getEndTimeUs()).isEqualTo(expectedData.getEndTimeUs());
      assertThat(data.getMethod()).isEqualTo(expectedData.getMethod());
      assertThat(data.getUrl()).isEqualTo(expectedData.getUrl());
      assertThat(data.getTraceId()).isEqualTo(expectedData.getTraceId());
      assertThat(data.getRequestPayloadId()).isEqualTo(expectedData.getRequestPayloadId());
      assertThat(data.getResponsePayloadId()).isEqualTo(expectedData.getResponsePayloadId());
      assertThat(data.getResponseHeader().getField("connId")).isEqualTo(expectedData.getResponseHeader().getField("connId"));
      assertThat(data.getJavaThreads().get(0).getId()).isEqualTo(expectedData.getJavaThreads().get(0).getId());
      assertThat(data.getJavaThreads().get(0).getName()).isEqualTo(expectedData.getJavaThreads().get(0).getName());

      ImmutableMap<String, String> requestHeaders = data.getRequestHeader().getFields();
      assertThat(requestHeaders).hasSize(2);
      assertThat(requestHeaders.get("user-agent")).isEqualTo("Customized");
      assertThat(requestHeaders.get("accept")).isEqualTo("text/plain");

      assertThat(Payload.newRequestPayload(myModel, data).getBytes())
        .isEqualTo(Payload.newRequestPayload(myModel, expectedData).getBytes());
      assertThat(Payload.newResponsePayload(myModel, data).getBytes())
        .isEqualTo(Payload.newResponsePayload(myModel, expectedData).getBytes());
    }
  }
}