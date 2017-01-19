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
package com.android.tools.datastore.poller;

import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;

public class NetworkDataPollerTest extends DataStorePollerTest {

  private static final int TEST_APP_ID = 1234;
  private static final long BASE_TIME_NS = System.nanoTime();
  private static final long SENT_VALUE = 1024;
  private static final long RECEIVED_VALUE = 2048;
  private static final int CONNECTION_COUNT = 4;
  private static final int CONNECTION_ID = 1;

  private static final String PAYLOAD_ID_1 = "0123456789";
  private static final String PAYLOAD_ID_2 = "9876543210";
  private static final String BAD_PAYLOAD_ID = "0000000000";
  private static final ByteString PAYLOAD_1 = ByteString.copyFromUtf8("PAYLOAD1");
  private static final ByteString PAYLOAD_2 = ByteString.copyFromUtf8("PAYLOAD2");

  private static final Common.CommonData STARTUP_BASIC_INFO = Common.CommonData.newBuilder()
    .setAppId(TEST_APP_ID)
    .setEndTimestamp(BASE_TIME_NS)
    .build();

  private static final Common.CommonData DELAY_BASIC_INFO = Common.CommonData.newBuilder()
    .setAppId(TEST_APP_ID)
    .setEndTimestamp(BASE_TIME_NS + TimeUnit.SECONDS.toNanos(1))
    .build();

  private static final Common.CommonData GLOBAL_APP_INFO = Common.CommonData.newBuilder()
    .setAppId(Common.AppId.ANY_VALUE)
    .setEndTimestamp(BASE_TIME_NS)
    .build();

  private static final NetworkProfiler.HttpRangeResponse HTTP_RANGE_RESPONSE = NetworkProfiler.HttpRangeResponse.newBuilder()
    .addData(NetworkProfiler.HttpConnectionData.newBuilder()
               .setConnId(CONNECTION_ID)
               .setDownloadingTimestamp(BASE_TIME_NS)
               .setEndTimestamp(BASE_TIME_NS + TimeUnit.SECONDS.toNanos(1))
               .setStartTimestamp(BASE_TIME_NS)
               .build())
    .build();

  private static final NetworkProfiler.SpeedData NETWORK_SPEED_DATA = NetworkProfiler.SpeedData.newBuilder()
    .setSent(SENT_VALUE)
    .setReceived(RECEIVED_VALUE)
    .build();
  private static final NetworkProfiler.ConnectionData NETWORK_CONNECTION_DATA = NetworkProfiler.ConnectionData.newBuilder()
    .setConnectionNumber(CONNECTION_COUNT)
    .build();
  private static final NetworkProfiler.ConnectivityData NETWORK_CONNECTIVITY_DATA = NetworkProfiler.ConnectivityData.newBuilder()
    .setRadioState(NetworkProfiler.ConnectivityData.RadioState.ACTIVE)
    .setDefaultNetworkType(NetworkProfiler.ConnectivityData.NetworkType.WIFI)
    .build();
  private static final NetworkProfiler.NetworkDataResponse NETWORK_DATA_RESPONSE = NetworkProfiler.NetworkDataResponse.newBuilder()
    .addData(NetworkProfiler.NetworkProfilerData.newBuilder()
               .setBasicInfo(STARTUP_BASIC_INFO)
               .setSpeedData(NETWORK_SPEED_DATA)
               .build())
    .addData(NetworkProfiler.NetworkProfilerData.newBuilder()
               .setBasicInfo(DELAY_BASIC_INFO)
               .setConnectionData(NETWORK_CONNECTION_DATA)
               .build())
    .addData(NetworkProfiler.NetworkProfilerData.newBuilder()
               .setBasicInfo(GLOBAL_APP_INFO)
               .setConnectivityData(NETWORK_CONNECTIVITY_DATA)
               .build())
    .build();

  private static final Map<String, ByteString> PAYLOAD_CACHE = new ImmutableMap.Builder<String, ByteString>().
    put(PAYLOAD_ID_1, PAYLOAD_1).
    put(PAYLOAD_ID_2, PAYLOAD_2).
    build();

  private NetworkDataPoller myNetworkDataPoller = new NetworkDataPoller();
  @Rule
  public TestGrpcService<FakeNetworkService> myService = new TestGrpcService<>(myNetworkDataPoller, new FakeNetworkService());

  @Before
  public void setUp() throws Exception {
    myNetworkDataPoller.startMonitoringApp(NetworkProfiler.NetworkStartRequest.newBuilder().setAppId(TEST_APP_ID).build(), mock(StreamObserver.class));
    myNetworkDataPoller.poll();
  }

  @After
  public void tearDown() throws Exception {
    // Not strictly necessary to do this but it ensures we run all code paths
    myNetworkDataPoller
      .stopMonitoringApp(NetworkProfiler.NetworkStopRequest.newBuilder().setAppId(TEST_APP_ID).build(), mock(StreamObserver.class));
  }

  @Test
  public void testGetHttpDetailsRequest() {
    NetworkProfiler.HttpDetailsResponse request = NetworkProfiler.HttpDetailsResponse.newBuilder()
      .setRequest(NetworkProfiler.HttpDetailsResponse.Request.getDefaultInstance()).build();
    getHttpDetails(NetworkProfiler.HttpDetailsRequest.Type.REQUEST, request);
  }

  @Test
  public void testGetHttpDetailsResponse() {
    NetworkProfiler.HttpDetailsResponse response = NetworkProfiler.HttpDetailsResponse.newBuilder()
      .setResponse(NetworkProfiler.HttpDetailsResponse.Response.getDefaultInstance()).build();
    getHttpDetails(NetworkProfiler.HttpDetailsRequest.Type.RESPONSE, response);
  }

  @Test
  public void testGetHttpDetailsBody() {
    NetworkProfiler.HttpDetailsResponse body = NetworkProfiler.HttpDetailsResponse.newBuilder()
      .setResponseBody(NetworkProfiler.HttpDetailsResponse.Body.getDefaultInstance()).build();
    getHttpDetails(NetworkProfiler.HttpDetailsRequest.Type.RESPONSE_BODY, body);
  }


  @Test
  public void testGetHttpRangeInvalidAppId() {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setAppId(0)
      .setStartTimestamp(0)
      .setEndTimestamp(Long.MAX_VALUE)
      .build();

    StreamObserver<NetworkProfiler.HttpRangeResponse> observer = mock(StreamObserver.class);
    myNetworkDataPoller.getHttpRange(request, observer);
    validateResponse(observer, NetworkProfiler.HttpRangeResponse.getDefaultInstance());
  }

  @Test
  public void testGetHttpRangeInRange() {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTimestamp(0)
      .setEndTimestamp(Long.MAX_VALUE)
      .build();

    StreamObserver<NetworkProfiler.HttpRangeResponse> observer = mock(StreamObserver.class);
    myNetworkDataPoller.getHttpRange(request, observer);
    validateResponse(observer, HTTP_RANGE_RESPONSE);
  }

  @Test
  public void testGetHttpRangeOutOfRange_StartTimeAfterLastRequest() {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS + TimeUnit.SECONDS.toNanos(1))
      .setEndTimestamp(Long.MAX_VALUE)
      .build();

    StreamObserver<NetworkProfiler.HttpRangeResponse> observer = mock(StreamObserver.class);
    myNetworkDataPoller.getHttpRange(request, observer);
    validateResponse(observer, NetworkProfiler.HttpRangeResponse.getDefaultInstance());
  }

  @Test
  public void testGetHttpRangeOutOfRange_EndTimeBeforeFirstRequest() {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTimestamp(Long.MIN_VALUE)
      .setEndTimestamp(Long.MIN_VALUE + 1)
      .build();

    StreamObserver<NetworkProfiler.HttpRangeResponse> observer = mock(StreamObserver.class);
    myNetworkDataPoller.getHttpRange(request, observer);
    validateResponse(observer, NetworkProfiler.HttpRangeResponse.getDefaultInstance());
  }

  @Test
  public void testGetDataAll() {
    getData(NetworkProfiler.NetworkDataRequest.Type.ALL, NETWORK_DATA_RESPONSE);
  }

  @Test
  public void testGetDataSpeed() {
    NetworkProfiler.NetworkDataResponse expected = NetworkProfiler.NetworkDataResponse.newBuilder()
      .addData(NetworkProfiler.NetworkProfilerData.newBuilder()
                 .setBasicInfo(STARTUP_BASIC_INFO)
                 .setSpeedData(NETWORK_SPEED_DATA)
                 .build())
      .build();
    getData(NetworkProfiler.NetworkDataRequest.Type.SPEED, expected);
  }

  @Test
  public void testGetDataConnections() {
    NetworkProfiler.NetworkDataResponse expected = NetworkProfiler.NetworkDataResponse.newBuilder()
      .addData(NetworkProfiler.NetworkProfilerData.newBuilder()
                 .setBasicInfo(DELAY_BASIC_INFO)
                 .setConnectionData(NETWORK_CONNECTION_DATA)
                 .build())
      .build();

    getData(NetworkProfiler.NetworkDataRequest.Type.CONNECTIONS, expected);
  }

  @Test
  public void testGetDataConnectivity() {
    NetworkProfiler.NetworkDataResponse expected = NetworkProfiler.NetworkDataResponse.newBuilder()
      .addData(NetworkProfiler.NetworkProfilerData.newBuilder()
                 .setBasicInfo(GLOBAL_APP_INFO)
                 .setConnectivityData(NETWORK_CONNECTIVITY_DATA)
                 .build())
      .build();

    getData(NetworkProfiler.NetworkDataRequest.Type.CONNECTIVITY, expected);
  }

  @Test
  public void testGetDataOutOfRange() {
    NetworkProfiler.NetworkDataRequest request = NetworkProfiler.NetworkDataRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS + TimeUnit.SECONDS.toNanos(1))
      .setEndTimestamp(Long.MAX_VALUE)
      .setType(NetworkProfiler.NetworkDataRequest.Type.ALL)
      .build();
    StreamObserver<NetworkProfiler.NetworkDataResponse> observer = mock(StreamObserver.class);
    myNetworkDataPoller.getData(request, observer);
    validateResponse(observer, NetworkProfiler.NetworkDataResponse.getDefaultInstance());
  }

  @Test
  public void testGetDataInvalidAppId() {
    NetworkProfiler.NetworkDataRequest request = NetworkProfiler.NetworkDataRequest.newBuilder()
      .setAppId(0)
      .setStartTimestamp(0)
      .setEndTimestamp(Long.MAX_VALUE)
      .setType(NetworkProfiler.NetworkDataRequest.Type.ALL)
      .build();

    // Connectivity data is global to the entire phone and will be returned always, regardless of
    // app ID (even invalid ones).
    NetworkProfiler.NetworkDataResponse expected = NetworkProfiler.NetworkDataResponse.newBuilder()
      .addData(NetworkProfiler.NetworkProfilerData.newBuilder()
                 .setBasicInfo(GLOBAL_APP_INFO)
                 .setConnectivityData(NETWORK_CONNECTIVITY_DATA)
                 .build())
      .build();

    StreamObserver<NetworkProfiler.NetworkDataResponse> observer = mock(StreamObserver.class);
    myNetworkDataPoller.getData(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetPayload() {
    StreamObserver<NetworkProfiler.NetworkPayloadResponse> observer1 = mock(StreamObserver.class);
    NetworkProfiler.NetworkPayloadRequest request1 = NetworkProfiler.NetworkPayloadRequest.newBuilder().setPayloadId(PAYLOAD_ID_1).build();
    NetworkProfiler.NetworkPayloadResponse response1 = NetworkProfiler.NetworkPayloadResponse.newBuilder().setContents(PAYLOAD_1).build();
    myNetworkDataPoller.getPayload(request1, observer1);
    validateResponse(observer1, response1);

    StreamObserver<NetworkProfiler.NetworkPayloadResponse> observer2 = mock(StreamObserver.class);
    NetworkProfiler.NetworkPayloadRequest request2 = NetworkProfiler.NetworkPayloadRequest.newBuilder().setPayloadId(PAYLOAD_ID_2).build();
    NetworkProfiler.NetworkPayloadResponse response2 = NetworkProfiler.NetworkPayloadResponse.newBuilder().setContents(PAYLOAD_2).build();
    myNetworkDataPoller.getPayload(request2, observer2);
    validateResponse(observer2, response2);

    StreamObserver<NetworkProfiler.NetworkPayloadResponse> observerNoMatch = mock(StreamObserver.class);
    NetworkProfiler.NetworkPayloadRequest requestBad =
      NetworkProfiler.NetworkPayloadRequest.newBuilder().setPayloadId(BAD_PAYLOAD_ID).build();
    NetworkProfiler.NetworkPayloadResponse responseNoMatch = NetworkProfiler.NetworkPayloadResponse.getDefaultInstance();
    myNetworkDataPoller.getPayload(requestBad, observerNoMatch);
    validateResponse(observerNoMatch, responseNoMatch);

  }

  private void getData(NetworkProfiler.NetworkDataRequest.Type type, NetworkProfiler.NetworkDataResponse expected) {
    getData(TEST_APP_ID, type, expected);
  }

  private void getData(int appId, NetworkProfiler.NetworkDataRequest.Type type, NetworkProfiler.NetworkDataResponse expected) {
    NetworkProfiler.NetworkDataRequest request = NetworkProfiler.NetworkDataRequest.newBuilder()
      .setAppId(appId)
      .setStartTimestamp(0)
      .setEndTimestamp(Long.MAX_VALUE)
      .setType(type)
      .build();
    StreamObserver<NetworkProfiler.NetworkDataResponse> observer = mock(StreamObserver.class);
    myNetworkDataPoller.getData(request, observer);
    validateResponse(observer, expected);
  }

  private void getHttpDetails(NetworkProfiler.HttpDetailsRequest.Type type,
                              NetworkProfiler.HttpDetailsResponse expectedResponse) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(CONNECTION_ID)
      .setType(type)
      .build();
    StreamObserver<NetworkProfiler.HttpDetailsResponse> observer = mock(StreamObserver.class);
    myNetworkDataPoller.getHttpDetails(request, observer);
    validateResponse(observer, expectedResponse);
  }

  private static class FakeNetworkService extends NetworkServiceGrpc.NetworkServiceImplBase {

    @Override
    public void getData(NetworkProfiler.NetworkDataRequest request, StreamObserver<NetworkProfiler.NetworkDataResponse> responseObserver) {
      responseObserver.onNext(NETWORK_DATA_RESPONSE);
      responseObserver.onCompleted();
    }

    @Override
    public void startMonitoringApp(NetworkProfiler.NetworkStartRequest request,
                                   StreamObserver<NetworkProfiler.NetworkStartResponse> responseObserver) {
      responseObserver.onNext(NetworkProfiler.NetworkStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopMonitoringApp(NetworkProfiler.NetworkStopRequest request,
                                  StreamObserver<NetworkProfiler.NetworkStopResponse> responseObserver) {
      responseObserver.onNext(NetworkProfiler.NetworkStopResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getHttpRange(NetworkProfiler.HttpRangeRequest request, StreamObserver<NetworkProfiler.HttpRangeResponse> responseObserver) {
      responseObserver.onNext(HTTP_RANGE_RESPONSE);
      responseObserver.onCompleted();
    }

    @Override
    public void getHttpDetails(NetworkProfiler.HttpDetailsRequest request,
                               StreamObserver<NetworkProfiler.HttpDetailsResponse> responseObserver) {
      NetworkProfiler.HttpDetailsRequest.Type type = request.getType();
      NetworkProfiler.HttpDetailsResponse.Builder response = NetworkProfiler.HttpDetailsResponse.newBuilder();
      if (type == NetworkProfiler.HttpDetailsRequest.Type.REQUEST) {
        response.setRequest(NetworkProfiler.HttpDetailsResponse.Request.getDefaultInstance());
      }
      else if (type == NetworkProfiler.HttpDetailsRequest.Type.RESPONSE) {
        response.setResponse(NetworkProfiler.HttpDetailsResponse.Response.getDefaultInstance());
      }
      else if (type == NetworkProfiler.HttpDetailsRequest.Type.RESPONSE_BODY) {
        response.setResponseBody(NetworkProfiler.HttpDetailsResponse.Body.getDefaultInstance());
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getPayload(NetworkProfiler.NetworkPayloadRequest request,
                           StreamObserver<NetworkProfiler.NetworkPayloadResponse> responseObserver) {
      NetworkProfiler.NetworkPayloadResponse.Builder builder = NetworkProfiler.NetworkPayloadResponse.newBuilder();
      ByteString payload = PAYLOAD_CACHE.get(request.getPayloadId());
      if (payload != null) {
        builder.setContents(payload);
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }
  }
}
