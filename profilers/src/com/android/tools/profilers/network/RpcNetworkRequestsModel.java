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
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A {@link NetworkRequestsModel} that uses an RPC mechanism to complete its queries.
 */
public final class RpcNetworkRequestsModel implements NetworkRequestsModel {
  @NotNull
  private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkService;

  private final int myPid;

  public RpcNetworkRequestsModel(@NotNull NetworkServiceGrpc.NetworkServiceBlockingStub service, int pid) {
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
      HttpData httpData = new HttpData();
      httpData.setId(connection.getConnId());
      httpData.setStartTimeUs(TimeUnit.NANOSECONDS.toMicros(connection.getStartTimestamp()));
      requestHttpRequest(httpData);

      if (connection.getEndTimestamp() != 0) {
        httpData.setEndTimeUs(TimeUnit.NANOSECONDS.toMicros(connection.getEndTimestamp()));
        httpData.setDownloadingTimeUs(TimeUnit.NANOSECONDS.toMicros(connection.getDownloadingTimestamp()));
        requestHttpResponse(httpData);
        requestHttpResponseBody(httpData);
      }
      httpDataList.add(httpData);
    }

    return httpDataList;
  }

  private void requestHttpRequest(@NotNull HttpData data) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(data.getId())
      .setType(NetworkProfiler.HttpDetailsRequest.Type.REQUEST)
      .build();
    NetworkProfiler.HttpDetailsResponse.Request result;
    try {
      result = myNetworkService.getHttpDetails(request).getRequest();
    } catch (StatusRuntimeException e) {
      return;
    }
    data.setUrl(result.getUrl());
    data.setMethod(result.getMethod());
  }

  private void requestHttpResponseBody(@NotNull HttpData data) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(data.getId())
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
    data.setHttpResponsePayloadId(payloadId);
  }

  @NotNull
  @Override
  public ByteString requestResponsePayload(@NotNull HttpData data) {
    if (StringUtil.isEmpty(data.getHttpResponsePayloadId())) {
      return ByteString.EMPTY;
    }

    NetworkProfiler.NetworkPayloadRequest payloadRequest = NetworkProfiler.NetworkPayloadRequest.newBuilder()
      .setPayloadId(data.getHttpResponsePayloadId())
      .build();
    NetworkProfiler.NetworkPayloadResponse payloadResponse;
    try {
      payloadResponse = myNetworkService.getPayload(payloadRequest);
    } catch (StatusRuntimeException e) {
      return ByteString.EMPTY;
    }
    return payloadResponse.getContents();
  }

  private void requestHttpResponse(@NotNull HttpData data) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(data.getId())
      .setType(NetworkProfiler.HttpDetailsRequest.Type.RESPONSE)
      .build();
    NetworkProfiler.HttpDetailsResponse response;
    try {
      response = myNetworkService.getHttpDetails(request);
    }
    catch (StatusRuntimeException e) {
      return;
    }
    data.setHttpResponseFields(response.getResponse().getFields());
  }
}
