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
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profiler.proto.Transport.BytesRequest;
import com.android.tools.profiler.proto.Transport.BytesResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.FeatureConfig;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.intellij.openapi.util.text.StringUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link NetworkConnectionsModel} that uses the legacy network RPC mechanism to fetch http connection data originated from an app.
 * Note that if {@link FeatureConfig#isUnifiedPipelineEnabled()} is true, the {@link RpcNetworkConnectionsModel} should be used instead.
 */
public class LegacyRpcNetworkConnectionsModel implements NetworkConnectionsModel {
  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myTransportService;
  @NotNull private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkService;
  @NotNull private final Common.Session mySession;

  public LegacyRpcNetworkConnectionsModel(@NotNull TransportServiceGrpc.TransportServiceBlockingStub transportService,
                                          @NotNull NetworkServiceGrpc.NetworkServiceBlockingStub networkService,
                                          @NotNull Common.Session session) {
    myTransportService = transportService;
    myNetworkService = networkService;
    mySession = session;
  }

  @NotNull
  @Override
  public List<HttpData> getData(@NotNull Range timeCurrentRangeUs) {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax())).build();
    NetworkProfiler.HttpRangeResponse response = myNetworkService.getHttpRange(request);

    List<HttpData> httpDataList = new ArrayList<>(response.getDataList().size());
    for (NetworkProfiler.HttpConnectionData connection : response.getDataList()) {
      long startTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getStartTimestamp());
      long uploadedTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getUploadedTimestamp());
      long downloadingTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getDownloadingTimestamp());
      // In the legacy pipeline, endTimeUs refers to either when the response was downloaded or if the connection was aborted earlier.
      long endTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getEndTimestamp());

      HttpData.Builder httpBuilder =
        new HttpData.Builder(
          connection.getConnId(),
          startTimeUs,
          uploadedTimeUs,
          downloadingTimeUs,
          endTimeUs,
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
    httpBuilder.setRequestFields(result.getFields());

    if (!result.getTraceId().isEmpty()) {
      String trace = requestBytes(result.getTraceId()).toStringUtf8();
      httpBuilder.setTrace(trace);
    }
  }

  private void requestHttpRequestBody(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsResponse result = getDetails(connectionId, NetworkProfiler.HttpDetailsRequest.Type.REQUEST_BODY);
    httpBuilder.setRequestPayloadId(result.getRequestBody().getPayloadId());
  }

  private void requestHttpResponseBody(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsResponse response = getDetails(connectionId, NetworkProfiler.HttpDetailsRequest.Type.RESPONSE_BODY);
    String payloadId = response.getResponseBody().getPayloadId();
    httpBuilder.setResponsePayloadId(payloadId);
    httpBuilder.setResponsePayloadSize(response.getResponseBody().getPayloadSize());
  }

  @NotNull
  @Override
  public ByteString requestBytes(@NotNull String id) {
    if (StringUtil.isEmpty(id)) {
      return ByteString.EMPTY;
    }

    BytesRequest request = BytesRequest.newBuilder()
      .setStreamId(mySession.getStreamId())
      .setId(id)
      .build();

    BytesResponse response = myTransportService.getBytes(request);
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
