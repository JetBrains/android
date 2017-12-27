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
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link NetworkConnectionsModel} that uses an RPC mechanism to complete its queries.
 */
public class RpcNetworkConnectionsModel implements NetworkConnectionsModel {
  @NotNull
  private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myProfilerService;
  private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkService;

  private final int myPid;
  private final Common.Session mySession;

  public RpcNetworkConnectionsModel(@NotNull ProfilerServiceGrpc.ProfilerServiceBlockingStub profilerService,
                                    @NotNull NetworkServiceGrpc.NetworkServiceBlockingStub networkService,
                                    int pid, Common.Session session) {
    myProfilerService = profilerService;
    myNetworkService = networkService;
    myPid = pid;
    mySession = session;
  }

  @NotNull
  @Override
  public List<HttpData> getData(@NotNull Range timeCurrentRangeUs) {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setProcessId(myPid)
      .setSession(mySession)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax())).build();
    NetworkProfiler.HttpRangeResponse response = myNetworkService.getHttpRange(request);

    List<HttpData> httpDataList = new ArrayList<>(response.getDataList().size());
    for (NetworkProfiler.HttpConnectionData connection : response.getDataList()) {
      long startTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getStartTimestamp());
      long endTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getEndTimestamp());
      long downloadTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getDownloadingTimestamp());
      HttpData.Builder httpBuilder = new HttpData.Builder(connection.getConnId(), startTimeUs, endTimeUs, downloadTimeUs);

      requestHttpRequest(connection.getConnId(), httpBuilder);
      requestAccessingThreads(connection.getConnId(), httpBuilder);

      if (connection.getEndTimestamp() != 0) {
        requestHttpResponse(connection.getConnId(), httpBuilder);
        requestHttpResponseBody(connection.getConnId(), httpBuilder);
      }
      httpDataList.add(httpBuilder.build());
    }

    return httpDataList;
  }

  private void requestHttpRequest(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(connectionId)
      .setSession(mySession)
      .setType(NetworkProfiler.HttpDetailsRequest.Type.REQUEST)
      .build();
    NetworkProfiler.HttpDetailsResponse.Request result = myNetworkService.getHttpDetails(request).getRequest();
    httpBuilder.setUrl(result.getUrl());
    httpBuilder.setMethod(result.getMethod());
    httpBuilder.setTrace(result.getTrace());
    httpBuilder.setRequestFields(result.getFields());
  }

  private void requestHttpResponseBody(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(connectionId)
      .setSession(mySession)
      .setType(NetworkProfiler.HttpDetailsRequest.Type.RESPONSE_BODY)
      .build();
    NetworkProfiler.HttpDetailsResponse response = myNetworkService.getHttpDetails(request);

    String payloadId = response.getResponseBody().getPayloadId();
    httpBuilder.setResponsePayloadId(payloadId);
  }

  @NotNull
  @Override
  public ByteString requestResponsePayload(@NotNull HttpData data) {
    if (StringUtil.isEmpty(data.getResponsePayloadId())) {
      return ByteString.EMPTY;
    }

    Profiler.BytesRequest request = Profiler.BytesRequest.newBuilder()
      .setId(data.getResponsePayloadId())
      .setSession(mySession)
      .build();

    Profiler.BytesResponse response = myProfilerService.getBytes(request);
    return response.getContents();
  }

  private void requestHttpResponse(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(connectionId)
      .setSession(mySession)
      .setType(NetworkProfiler.HttpDetailsRequest.Type.RESPONSE)
      .build();
    NetworkProfiler.HttpDetailsResponse response = myNetworkService.getHttpDetails(request);

    httpBuilder.setResponseFields(response.getResponse().getFields());
  }

  private void requestAccessingThreads(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(connectionId)
      .setSession(mySession)
      .setType(NetworkProfiler.HttpDetailsRequest.Type.ACCESSING_THREADS)
      .build();
    NetworkProfiler.HttpDetailsResponse response = myNetworkService.getHttpDetails(request);
    for (NetworkProfiler.JavaThread thread : response.getAccessingThreads().getThreadList()) {
      httpBuilder.addJavaThread(new HttpData.JavaThread(thread.getId(), thread.getName()));
    }
  }
}
