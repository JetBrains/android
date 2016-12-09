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

import com.android.tools.profiler.proto.*;
import com.google.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.NetworkProfiler.*;

public final class TestNetworkService extends NetworkServiceGrpc.NetworkServiceImplBase {
  public static final int FAKE_APP_ID = 1111;
  public static final String FAKE_PAYLOAD = "Test Payload";

  @NotNull private List<HttpData> myHttpDataList;
  @NotNull private List<NetworkProfilerData> myDataList;

  private TestNetworkService(@NotNull Builder builder) {
    myDataList = builder.myDataList;
    myHttpDataList = builder.myHttpDataList;
  }

  @Override
  public void getData(NetworkDataRequest request, StreamObserver<NetworkDataResponse> responseObserver) {
    NetworkDataResponse.Builder response = NetworkDataResponse.newBuilder();
    long startTime = request.getStartTimestamp();
    long endTime = request.getEndTimestamp();

    for (NetworkProfilerData data : myDataList) {
      long current = data.getBasicInfo().getEndTimestamp();
      if (current > startTime && current <= endTime) {
        if ((request.getType() == NetworkDataRequest.Type.ALL) ||
            (request.getType() == NetworkDataRequest.Type.SPEED &&
             data.getDataCase() == NetworkProfilerData.DataCase.SPEED_DATA) ||
            (request.getType() == NetworkDataRequest.Type.CONNECTIONS &&
             data.getDataCase() == NetworkProfilerData.DataCase.CONNECTION_DATA) ||
            (request.getType() == NetworkDataRequest.Type.CONNECTIVITY &&
             data.getDataCase() == NetworkProfilerData.DataCase.CONNECTIVITY_DATA)) {
          response.addData(data);
        }
      }
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getHttpRange(HttpRangeRequest request,
                           StreamObserver<HttpRangeResponse> responseObserver) {
    HttpRangeResponse.Builder builder =
      HttpRangeResponse.newBuilder();
    long requestStartTime = request.getStartTimestamp();
    long requestEndTime = request.getEndTimestamp();

    for (HttpData data : myHttpDataList) {
      long startTime = TimeUnit.MICROSECONDS.toNanos(data.getStartTimeUs());
      long downloadTime = TimeUnit.MICROSECONDS.toNanos(data.getDownloadingTimeUs());
      long endTime = TimeUnit.MICROSECONDS.toNanos(data.getEndTimeUs());

      if (Math.max(requestStartTime, startTime) <= Math.min(requestEndTime, endTime == 0 ? Long.MAX_VALUE : endTime)) {
        HttpConnectionData.Builder dataBuilder = HttpConnectionData.newBuilder();
        dataBuilder.setConnId(data.getId())
          .setStartTimestamp(startTime)
          .setDownloadingTimestamp(downloadTime)
          .setEndTimestamp(endTime);
        builder.addData(dataBuilder.build());
      }
    }
    HttpRangeResponse response = builder.build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getHttpDetails(HttpDetailsRequest request,
                             StreamObserver<HttpDetailsResponse> responseObserver) {
    HttpDetailsResponse.Builder response = HttpDetailsResponse.newBuilder();
    HttpData data = findHttpData(request.getConnId());
    switch (request.getType()) {
      case REQUEST:
        HttpDetailsResponse.Request.Builder requestBuilder = HttpDetailsResponse.Request.newBuilder();
        requestBuilder.setTrace(data.getTrace())
          .setMethod(data.getMethod())
          .setUrl(data.getUrl());
        response.setRequest(requestBuilder.build());
        break;
      case RESPONSE:
        HttpDetailsResponse.Response.Builder responseBuilder = HttpDetailsResponse.Response.newBuilder();
        responseBuilder.setFields(formatFakeResponseFields(data.getId()));
        response.setResponse(responseBuilder.build());
        break;
      case RESPONSE_BODY:
        HttpDetailsResponse.Body.Builder bodyBuilder = HttpDetailsResponse.Body.newBuilder();
        bodyBuilder.setPayloadId(data.getResponsePayloadId());
        response.setResponseBody(bodyBuilder.build());
        break;
      default:
        assert false : "Unsupported request type " + request.getType();
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getPayload(NetworkPayloadRequest request,
                         StreamObserver<NetworkPayloadResponse> responseObserver) {
    responseObserver.onNext(NetworkPayloadResponse.newBuilder().setContents(ByteString.copyFromUtf8(FAKE_PAYLOAD)).build());
    responseObserver.onCompleted();
  }

  @NotNull
  public static HttpData newHttpData(long id, long startS, long downloadS, long endS) {
    long startUs = TimeUnit.SECONDS.toMicros(startS);
    long downloadUs = TimeUnit.SECONDS.toMicros(downloadS);
    long endUs = TimeUnit.SECONDS.toMicros(endS);
    HttpData.Builder builder = new HttpData.Builder(id, startUs, endUs, downloadUs);
    builder.setTrace("Trace " + id);
    builder.setUrl("Url " + id);
    builder.setMethod("method " + id);
    if (endS != 0) {
      builder.setResponsePayloadId("payloadId " + id);
      builder.setResponseFields(formatFakeResponseFields(id));
    }
    return builder.build();
  }

  @NotNull
  public static NetworkProfilerData newSpeedData(long timestampSec,
                                                 long sent,
                                                 long received) {
    NetworkProfilerData.Builder builder = NetworkProfilerData.newBuilder();
    builder.setBasicInfo(Common.CommonData.newBuilder()
                           .setAppId(FAKE_APP_ID)
                           .setEndTimestamp(TimeUnit.SECONDS.toNanos(timestampSec)));
    builder.setSpeedData(SpeedData.newBuilder().setReceived(received).setSent(sent));
    return builder.build();
  }

  @NotNull
  public static NetworkProfilerData newRadioData(long timestampSec,
                                                 @NotNull ConnectivityData.NetworkType networkType,
                                                 @NotNull ConnectivityData.RadioState radioState) {
    NetworkProfilerData.Builder builder = NetworkProfilerData.newBuilder();
    builder.setBasicInfo(Common.CommonData.newBuilder()
                           .setAppId(FAKE_APP_ID)
                           .setEndTimestamp(TimeUnit.SECONDS.toNanos(timestampSec)));
    builder.setConnectivityData(ConnectivityData.newBuilder()
                                  .setDefaultNetworkType(networkType)
                                  .setRadioState(radioState));
    return builder.build();
  }

  @NotNull
  public static NetworkProfilerData newConnectionData(long timestampSec, int value) {
    NetworkProfilerData.Builder builder = NetworkProfilerData.newBuilder();
    builder.setBasicInfo(Common.CommonData.newBuilder()
                           .setAppId(FAKE_APP_ID)
                           .setEndTimestamp(TimeUnit.SECONDS.toNanos(timestampSec)));
    builder.setConnectionData(ConnectionData.newBuilder().setConnectionNumber(value).build());
    return builder.build();
  }

  @NotNull
  private static String formatFakeResponseFields(long id) {
    return "status line = HTTP/1.1 302 Found \n" +
           String.format("connId = %d", id);
  }

  @Nullable
  private HttpData findHttpData(long id) {
    for (HttpData data: myHttpDataList) {
      if (data.getId() == id) {
        return data;
      }
    }
    return null;
  }

  @NotNull
  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    @NotNull private List<NetworkProfilerData> myDataList = new ArrayList<>();
    @NotNull private List<HttpData> myHttpDataList = new ArrayList<>();

    @NotNull
    public Builder setNetworkDataList(@NotNull List<NetworkProfilerData> dataList) {
      myDataList = dataList;
      return this;
    }

    @NotNull
    public Builder setHttpDataList(@NotNull List<HttpData> dataList) {
      myHttpDataList = dataList;
      return this;
    }

    @NotNull
    public TestNetworkService build() {
      return new TestNetworkService(this);
    }
  }
}