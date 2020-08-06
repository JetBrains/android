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

import static com.android.tools.profilers.ProfilersTestData.generateNetworkConnectionData;
import static com.android.tools.profilers.ProfilersTestData.generateNetworkThreadData;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.Payload;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RpcNetworkConnectionsModelTest {
  @Parameterized.Parameters
  public static Collection<Boolean> useNewEventPipelineParameter() {
    return Arrays.asList(false, true);
  }

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
             .setResponsePayloadSize(TestHttpData.fakeContentSize(1))
             .build())
      // Finished request (2-5)
      .add(TestHttpData.newBuilder(2, 2, 5, new HttpData.JavaThread(2, "threadB"))
             .setRequestFields(FAKE_REQUEST_HEADERS)
             .setRequestPayloadId(FAKE_REQUEST_PAYLOAD_ID + 2)
             .setResponsePayloadId(FAKE_RESPONSE_PAYLOAD_ID + 2)
             .setResponsePayloadSize(TestHttpData.fakeContentSize(2))
             .build())
      // Unfinished request (3-?)
      .add(TestHttpData.newBuilder(3, 3, 0, 0, 0, 0, new HttpData.JavaThread(3, "threadC"))
             .setRequestFields(FAKE_REQUEST_HEADERS)
             // No request / response payload, hasn't started uploading yet
             .build())
      // Unfinished request (4-?)
      .add(TestHttpData.newBuilder(4, 4, 5, 0, 0, 0, new HttpData.JavaThread(4, "threadD"))
             .setRequestFields(FAKE_REQUEST_HEADERS)
             // No response payload, hasn't finished downloading yet
             .build())
      // Finished request (8-12)
      .add(TestHttpData.newBuilder(5, 8, 9, 10, 12, 12, new HttpData.JavaThread(5, "threadE"))
             .setRequestFields(FAKE_REQUEST_HEADERS)
             .setRequestPayloadId(FAKE_REQUEST_PAYLOAD_ID + 5)
             .setResponsePayloadId(FAKE_RESPONSE_PAYLOAD_ID + 5)
             .setResponsePayloadSize(TestHttpData.fakeContentSize(5))
             .build())
      .build();

  private final FakeTimer myTimer = new FakeTimer();
  private FakeTransportService myTransportService = new FakeTransportService(myTimer, false);

  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("RpcNetworkConnectionsModelTest", myTransportService, new FakeProfilerService(myTimer),
                        FakeNetworkService.newBuilder().setHttpDataList(FAKE_DATA).build());
  private boolean myUseNewEventPipeline;
  private NetworkConnectionsModel myModel;

  public RpcNetworkConnectionsModelTest(boolean useNewEventPipeline) {
    myUseNewEventPipeline = useNewEventPipeline;
  }

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), new FakeIdeProfilerServices(), myTimer);

    if (myUseNewEventPipeline) {
      myModel = new RpcNetworkConnectionsModel(profilers.getClient().getTransportClient(), Common.Session.getDefaultInstance());

      for (HttpData data : FAKE_DATA) {
        // Add the http connection events
        EventGroup group = generateNetworkConnectionData(data).build();
        for (Common.Event event : group.getEventsList()) {
          myTransportService.addEventToStream(0, event);
        }

        // Add the thread data associated with the connection events.
        myTransportService.addEventToStream(0, generateNetworkThreadData(data).build());
      }
    }
    else {
      myModel = new LegacyRpcNetworkConnectionsModel(profilers.getClient().getTransportClient(), profilers.getClient().getNetworkClient(),
                                                     ProfilersTestData.SESSION_DATA);
    }

    for (int i = 0; i < FAKE_DATA.size(); i++) {
      long id = FAKE_DATA.get(i).getId();
      myTransportService.addFile(FAKE_REQUEST_PAYLOAD_ID + id, ByteString.copyFromUtf8("Request Body " + i));
      myTransportService.addFile(FAKE_RESPONSE_PAYLOAD_ID + id, ByteString.copyFromUtf8("Response Body " + i));

      // TODO remove once we remove the legacy pipeline codebase.
      String stackTrace = TestHttpData.fakeStackTrace(id);
      myTransportService.addFile(TestHttpData.fakeStackTraceId(stackTrace), ByteString.copyFromUtf8(stackTrace));
    }
  }

  @Test
  public void nonEmptyBytes() {
    myTransportService.addFile("sampleid", ByteString.copyFromUtf8("Sample Contents"));
    assertThat(myModel.requestBytes("sampleid").toStringUtf8()).isEqualTo("Sample Contents");
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

      assertThat(data.getRequestStartTimeUs()).isEqualTo(expectedData.getRequestStartTimeUs());
      assertThat(data.getRequestCompleteTimeUs()).isEqualTo(expectedData.getRequestCompleteTimeUs());
      assertThat(data.getResponseStartTimeUs()).isEqualTo(expectedData.getResponseStartTimeUs());
      assertThat(data.getResponseCompleteTimeUs()).isEqualTo(expectedData.getResponseCompleteTimeUs());
      assertThat(data.getConnectionEndTimeUs()).isEqualTo(expectedData.getConnectionEndTimeUs());
      assertThat(data.getMethod()).isEqualTo(expectedData.getMethod());
      assertThat(data.getUrl()).isEqualTo(expectedData.getUrl());
      assertThat(data.getTrace()).isEqualTo(expectedData.getTrace());
      assertThat(data.getRequestPayloadId()).isEqualTo(expectedData.getRequestPayloadId());
      assertThat(data.getResponsePayloadId()).isEqualTo(expectedData.getResponsePayloadId());
      assertThat(data.getResponsePayloadSize()).isEqualTo(expectedData.getResponsePayloadSize());
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