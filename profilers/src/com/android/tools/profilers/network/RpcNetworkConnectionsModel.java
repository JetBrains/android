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
 import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.text.StringUtil;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A {@link NetworkConnectionsModel} that uses an RPC mechanism to complete its queries.
 */
public class RpcNetworkConnectionsModel implements NetworkConnectionsModel {
  @NotNull
  private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkService;

  private final int myPid;

  public RpcNetworkConnectionsModel(@NotNull NetworkServiceGrpc.NetworkServiceBlockingStub service, int pid) {
    myNetworkService = service;
    myPid = pid;
  }

  @NotNull
  @Override
  public List<HttpData> getData(@NotNull Range timeCurrentRangeUs) {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setAppId(myPid).setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax())).build();
    NetworkProfiler.HttpRangeResponse response;

    try {
      response = myNetworkService.getHttpRange(request);
    } catch (StatusRuntimeException e) {
      return Collections.emptyList();
    }

    List<HttpData> httpDataList = new ArrayList<>(response.getDataList().size());
    for (NetworkProfiler.HttpConnectionData connection: response.getDataList()) {
      long startTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getStartTimestamp());
      long endTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getEndTimestamp());
      long downloadTimeUs = TimeUnit.NANOSECONDS.toMicros(connection.getDownloadingTimestamp());

      HttpData.Builder httpBuilder = new HttpData.Builder(connection.getConnId(), startTimeUs, endTimeUs, downloadTimeUs);

      requestHttpRequest(connection.getConnId(), httpBuilder);
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
      .setType(NetworkProfiler.HttpDetailsRequest.Type.REQUEST)
      .build();
    NetworkProfiler.HttpDetailsResponse.Request result;
    try {
      result = myNetworkService.getHttpDetails(request).getRequest();
    } catch (StatusRuntimeException e) {
      return;
    }
    httpBuilder.setUrl(result.getUrl());
    httpBuilder.setMethod(result.getMethod());
    httpBuilder.setTrace(result.getTrace());
  }

  private void requestHttpResponseBody(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(connectionId)
      .setType(NetworkProfiler.HttpDetailsRequest.Type.RESPONSE_BODY)
      .build();
    NetworkProfiler.HttpDetailsResponse response;
    try {
      response = myNetworkService.getHttpDetails(request);
    }
    catch (StatusRuntimeException e) {
      return;
    }
    String payloadId = response.getResponseBody().getPayloadId();
    httpBuilder.setResponsePayloadId(payloadId);
  }

  @NotNull
  @Override
  public ByteString requestResponsePayload(@NotNull HttpData data) {
    if (StringUtil.isEmpty(data.getResponsePayloadId())) {
      return ByteString.EMPTY;
    }

    NetworkProfiler.NetworkPayloadRequest payloadRequest = NetworkProfiler.NetworkPayloadRequest.newBuilder()
      .setPayloadId(data.getResponsePayloadId())
      .build();
    NetworkProfiler.NetworkPayloadResponse payloadResponse;
    try {
      payloadResponse = myNetworkService.getPayload(payloadRequest);
    } catch (StatusRuntimeException e) {
      return ByteString.EMPTY;
    }
    return payloadResponse.getContents();
  }

  private void requestHttpResponse(long connectionId, @NotNull HttpData.Builder httpBuilder) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(connectionId)
      .setType(NetworkProfiler.HttpDetailsRequest.Type.RESPONSE)
      .build();
    NetworkProfiler.HttpDetailsResponse response;
    try {
      response = myNetworkService.getHttpDetails(request);
    }
    catch (StatusRuntimeException e) {
      return;
    }
    httpBuilder.setResponseFields(response.getResponse().getFields());
  }
}
