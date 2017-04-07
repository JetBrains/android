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
package com.android.tools.idea.monitor.ui.network.model;

import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.profilerclient.DeviceProfilerService;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link NetworkCaptureModel} that uses an RPC mechanism to complete its queries.
 */
public final class RpcNetworkCaptureModel implements NetworkCaptureModel {
  @NotNull
  private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkService;

  @NotNull
  private final HttpDataCache myDataCache;

  private final int myPid;

  public RpcNetworkCaptureModel(@NotNull DeviceProfilerService service, @NotNull HttpDataCache dataCache) {
    myNetworkService = service.getNetworkService();
    myDataCache = dataCache;
    myPid = service.getSelectedProcessId();
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
    String responseFilePath = response.getResponseBody().getPayloadId();
    data.setHttpResponsePayloadId(responseFilePath);
    // TODO: too slow
    /*
    File file = !StringUtil.isEmptyOrSpaces(responseFilePath) ? myDataCache.getFile(responseFilePath) : null;
    if (file != null) {
      data.setHttpResponseBodySize(file.length());
    }*/
  }
}
