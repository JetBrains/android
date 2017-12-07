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
import com.android.tools.profiler.proto.Profiler.BytesRequest;
import com.android.tools.profiler.proto.Profiler.BytesResponse;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A {@link NetworkConnectionsModel} that uses an RPC mechanism to complete its queries. It sent queries to datastore, adding or removing
 * data queries may need change datastore.
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
      long uploadedTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getUploadedTimestamp());
      long downloadingTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getDownloadingTimestamp());
      long endTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getEndTimestamp());

      HttpData.Builder httpBuilder =
        new HttpData.Builder(
          connection.getConnId(),
          startTimeUs,
          uploadedTimeUs,
          downloadingTimeUs,
          endTimeUs,
          requestAccessingThreads(connection.getConnId()));

      requestHttpRequest(connection.getConnId(), httpBuilder);

      if (connection.getUploadedTimestamp() != 0) {
        requestHttpRequestBody(connection.getConnId(), httpBuilder);
      }
      if (connection.getEndTimestamp() != 0) {
        requestHttpResponse(connection.getConnId(), httpBuilder);
        requestHttpResponseBody(connection.getConnId(), httpBuilder);
      }
      httpDataList.add(httpBuilder.build());
    }

    return httpDataList;
  }

  private void requestHttpRequest(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsResponse.Request result =
      getDetails(connectionId, NetworkProfiler.HttpDetailsRequest.Type.REQUEST).getRequest();
    httpBuilder.setUrl(result.getUrl());
    httpBuilder.setMethod(result.getMethod());
    httpBuilder.setTraceId(result.getTraceId());
    httpBuilder.setRequestFields(result.getFields());
  }

  private void requestHttpRequestBody(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsResponse result = getDetails(connectionId, NetworkProfiler.HttpDetailsRequest.Type.REQUEST_BODY);
    httpBuilder.setRequestPayloadId(result.getRequestBody().getPayloadId());
  }

  private void requestHttpResponseBody(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsResponse response = getDetails(connectionId, NetworkProfiler.HttpDetailsRequest.Type.RESPONSE_BODY);
    String payloadId = response.getResponseBody().getPayloadId();
    httpBuilder.setResponsePayloadId(payloadId);
  }

  @NotNull
  @Override
  public ByteString requestBytes(@NotNull String id) {
    if (StringUtil.isEmpty(id)) {
      return ByteString.EMPTY;
    }

    BytesRequest request = BytesRequest.newBuilder()
      .setId(id)
      .setSession(mySession)
      .build();

    BytesResponse response = myProfilerService.getBytes(request);
    return response.getContents();
  }

  private void requestHttpResponse(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsResponse response = getDetails(connectionId, NetworkProfiler.HttpDetailsRequest.Type.RESPONSE);
    httpBuilder.setResponseFields(response.getResponse().getFields());
  }

  private List<HttpData.JavaThread> requestAccessingThreads(long connectionId) {
    NetworkProfiler.HttpDetailsResponse response = getDetails(connectionId, NetworkProfiler.HttpDetailsRequest.Type.ACCESSING_THREADS);
    return response.getAccessingThreads().getThreadList().stream().map(proto -> new HttpData.JavaThread(proto.getId(), proto.getName()))
      .collect(
        Collectors.toList());
  }

  private NetworkProfiler.HttpDetailsResponse getDetails(long connectionId, NetworkProfiler.HttpDetailsRequest.Type type) {
    return myNetworkService.getHttpDetails(
      NetworkProfiler.HttpDetailsRequest.newBuilder().setConnId(connectionId).setSession(mySession).setType(type).build());
  }
}
