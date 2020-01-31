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

import static com.android.tools.profiler.proto.NetworkProfiler.ConnectionData;
import static com.android.tools.profiler.proto.NetworkProfiler.ConnectivityData;
import static com.android.tools.profiler.proto.NetworkProfiler.HttpConnectionData;
import static com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsRequest;
import static com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsResponse;
import static com.android.tools.profiler.proto.NetworkProfiler.HttpRangeRequest;
import static com.android.tools.profiler.proto.NetworkProfiler.HttpRangeResponse;
import static com.android.tools.profiler.proto.NetworkProfiler.JavaThread;
import static com.android.tools.profiler.proto.NetworkProfiler.NetworkDataRequest;
import static com.android.tools.profiler.proto.NetworkProfiler.NetworkDataResponse;
import static com.android.tools.profiler.proto.NetworkProfiler.NetworkProfilerData;
import static com.android.tools.profiler.proto.NetworkProfiler.NetworkStartRequest;
import static com.android.tools.profiler.proto.NetworkProfiler.NetworkStartResponse;
import static com.android.tools.profiler.proto.NetworkProfiler.NetworkStopRequest;
import static com.android.tools.profiler.proto.NetworkProfiler.NetworkStopResponse;
import static com.android.tools.profiler.proto.NetworkProfiler.SpeedData;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Network;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profilers.network.httpdata.HttpData;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FakeNetworkService extends NetworkServiceGrpc.NetworkServiceImplBase {
  @NotNull private List<HttpData> myHttpDataList;
  @NotNull private List<NetworkProfilerData> myDataList;
  private Common.Session mySession;

  private FakeNetworkService(@NotNull Builder builder) {
    myDataList = builder.myDataList;
    myHttpDataList = builder.myHttpDataList;
  }

  @Override
  public void startMonitoringApp(NetworkStartRequest request,
                                 StreamObserver<NetworkStartResponse> responseObserver) {
    mySession = request.getSession();
    responseObserver.onNext(NetworkStartResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopMonitoringApp(NetworkStopRequest request,
                                StreamObserver<NetworkStopResponse> responseObserver) {
    mySession = request.getSession();
    responseObserver.onNext(NetworkStopResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  public Common.Session getSession() {
    return mySession;
  }

  @Override
  public void getData(NetworkDataRequest request, StreamObserver<NetworkDataResponse> responseObserver) {
    NetworkDataResponse.Builder response = NetworkDataResponse.newBuilder();
    long startTime = request.getStartTimestamp();
    long endTime = request.getEndTimestamp();

    for (NetworkProfilerData data : myDataList) {
      long current = data.getEndTimestamp();
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
    HttpRangeResponse.Builder builder = HttpRangeResponse.newBuilder();
    long requestStartTime = request.getStartTimestamp();
    long requestEndTime = request.getEndTimestamp();

    for (HttpData data : myHttpDataList) {
      long requestStartTimeNs = TimeUnit.MICROSECONDS.toNanos(data.getRequestStartTimeUs());
      long requestCompleteTimeNs = TimeUnit.MICROSECONDS.toNanos(data.getRequestCompleteTimeUs());
      long responseStarteTimeNs = TimeUnit.MICROSECONDS.toNanos(data.getResponseStartTimeUs());
      long connectionEndTimeNs = TimeUnit.MICROSECONDS.toNanos(data.getConnectionEndTimeUs());

      if (Math.max(requestStartTime, requestStartTimeNs) <= Math.min(requestEndTime, connectionEndTimeNs == 0 ? Long.MAX_VALUE : connectionEndTimeNs)) {
        HttpConnectionData.Builder dataBuilder = HttpConnectionData.newBuilder();
        dataBuilder.setConnId(data.getId())
          .setStartTimestamp(requestStartTimeNs)
          .setUploadedTimestamp(requestCompleteTimeNs)
          .setDownloadingTimestamp(responseStarteTimeNs)
          .setEndTimestamp(connectionEndTimeNs)
          .build();
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
        String requestHeaders = data.getRequestHeader().getFields().entrySet().stream().map(x -> x.getKey() + " = " + x.getValue())
          .collect(Collectors.joining("\n"));
        requestBuilder.setTraceId(TestHttpData.fakeStackTraceId(data.getTrace()))
          .setMethod(data.getMethod())
          .setUrl(data.getUrl())
          .setFields(requestHeaders);
        response.setRequest(requestBuilder.build());
        break;
      case REQUEST_BODY:
        HttpDetailsResponse.Body.Builder requestBodyBuilder = HttpDetailsResponse.Body.newBuilder();
        requestBodyBuilder.setPayloadId(data.getRequestPayloadId());
        response.setRequestBody(requestBodyBuilder.build());
        break;
      case RESPONSE:
        HttpDetailsResponse.Response.Builder responseBuilder = HttpDetailsResponse.Response.newBuilder();
        responseBuilder.setFields(TestHttpData.fakeResponseFields(data.getId()));
        response.setResponse(responseBuilder.build());
        break;
      case RESPONSE_BODY:
        HttpDetailsResponse.Body.Builder responseBodyBuilder = HttpDetailsResponse.Body.newBuilder();
        responseBodyBuilder.setPayloadId(data.getResponsePayloadId());
        responseBodyBuilder.setPayloadSize(TestHttpData.fakeContentSize(data.getId()));
        response.setResponseBody(responseBodyBuilder.build());
        break;
      case ACCESSING_THREADS:
        HttpDetailsResponse.AccessingThreads.Builder threadsBuilder = HttpDetailsResponse.AccessingThreads.newBuilder();
        data.getJavaThreads().forEach(t -> threadsBuilder.addThread(JavaThread.newBuilder().setName(t.getName()).setId(t.getId())));
        response.setAccessingThreads(threadsBuilder);
        break;
      default:
        assert false : "Unsupported request type " + request.getType();
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @NotNull
  public static NetworkProfilerData newSpeedData(long timestampSec,
                                                 long sent,
                                                 long received) {
    NetworkProfilerData.Builder builder = NetworkProfilerData.newBuilder();
    builder.setEndTimestamp(TimeUnit.SECONDS.toNanos(timestampSec));
    builder.setSpeedData(SpeedData.newBuilder().setReceived(received).setSent(sent));
    return builder.build();
  }

  @NotNull
  public static NetworkProfilerData newRadioData(long timestampSec, @NotNull Network.NetworkTypeData.NetworkType networkType) {
    NetworkProfilerData.Builder builder = NetworkProfilerData.newBuilder();
    builder.setEndTimestamp(TimeUnit.SECONDS.toNanos(timestampSec));
    builder.setConnectivityData(ConnectivityData.newBuilder()
                                  .setNetworkType(networkType));
    return builder.build();
  }

  @NotNull
  public static NetworkProfilerData newConnectionData(long timestampSec, int value) {
    NetworkProfilerData.Builder builder = NetworkProfilerData.newBuilder();
    builder.setEndTimestamp(TimeUnit.SECONDS.toNanos(timestampSec));
    builder.setConnectionData(ConnectionData.newBuilder().setConnectionNumber(value).build());
    return builder.build();
  }

  @Nullable
  private HttpData findHttpData(long id) {
    for (HttpData data : myHttpDataList) {
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
    public FakeNetworkService build() {
      return new FakeNetworkService(this);
    }
  }
}