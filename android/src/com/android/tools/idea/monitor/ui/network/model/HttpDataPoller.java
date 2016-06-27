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

import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.monitor.datastore.DataAdapter;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkProfilerServiceGrpc;
import com.intellij.util.containers.hash.HashMap;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Class to poll http url connections data on an individual frequency.
 */
public class HttpDataPoller extends Poller {
  private static final long POLLING_DELAY_NS = TimeUnit.SECONDS.toNanos(1);

  private static final int NS_TO_MS = 1000000;

  private final int myPid;

  // Data from device are sorted by connection start time, so we copy all into a list.
  private final List<HttpData> myHttpDataList = new ArrayList<>();
  // A connection id is key to identify different data, start time can be the same.
  private final Map<Long, HttpData> myHttpDataMap = new HashMap<>();

  private long myDataRequestStartTimeNs;

  private NetworkProfilerServiceGrpc.NetworkProfilerServiceBlockingStub myNetworkService;

  public HttpDataPoller(@NotNull SeriesDataStore dataStore, int pid) {
    super(dataStore, POLLING_DELAY_NS);
    myPid = pid;

    dataStore.registerAdapter(SeriesDataType.NETWORK_HTTP_DATA, new HttpDataAdapter(myHttpDataList));
  }

  @Override
  protected void asyncInit() {
    myNetworkService = myService.getNetworkService();
    myDataRequestStartTimeNs = Long.MIN_VALUE;
  }

  @Override
  protected void poll() {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setAppId(myPid).setStartTimestamp(myDataRequestStartTimeNs).setEndTimestamp(Long.MAX_VALUE).build();
    NetworkProfiler.HttpRangeResponse response;
    try {
      response = myNetworkService.getHttpRange(request);
    } catch (StatusRuntimeException e) {
      cancel(true);
      return;
    }

    for (NetworkProfiler.HttpConnectionData connection : response.getDataList()) {
      HttpData httpData;
      if (myHttpDataMap.containsKey(connection.getConnId())) {
        httpData = myHttpDataMap.get(connection.getConnId());
      }
      else {
        httpData = new HttpData();
        httpData.myId = connection.getConnId();
        httpData.myStartTimeMs = connection.getStartTimestamp() / NS_TO_MS;
        getHttpRequest(httpData);
        myHttpDataList.add(httpData);
        myHttpDataMap.put(httpData.myId, httpData);
        myDataRequestStartTimeNs = Math.max(myDataRequestStartTimeNs, connection.getStartTimestamp() + 1);
      }
      if (connection.getEndTimestamp() != 0) {
        httpData.myEndTimeMs = connection.getEndTimestamp() / NS_TO_MS;
        getHttpResponseBody(httpData);
        // Checks both start and end timestamps.
        myDataRequestStartTimeNs = Math.max(myDataRequestStartTimeNs, connection.getEndTimestamp() + 1);
      }
    }
  }

  @Override
  protected void asyncShutdown() {
  }

  private void getHttpRequest(HttpData data) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(data.myId)
      .setType(NetworkProfiler.HttpDetailsRequest.Type.REQUEST)
      .build();
    NetworkProfiler.HttpDetailsResponse.Request result;
    try {
      result = myNetworkService.getHttpDetails(request).getRequest();
    } catch (StatusRuntimeException e) {
      cancel(true);
      return;
    }
    data.myUrl = result.getUrl();
    data.myMethod = result.getMethod();
  }

  private void getHttpResponseBody(HttpData data) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(data.myId)
      .setType(NetworkProfiler.HttpDetailsRequest.Type.RESPONSE_BODY)
      .build();
    NetworkProfiler.HttpDetailsResponse response;
    try {
      response = myNetworkService.getHttpDetails(request);
    } catch (StatusRuntimeException e) {
      cancel(true);
      return;
    }
    data.myHttpResponseBodyPath = response.getResponseBody().getFilePath();
  }

  private static class HttpDataAdapter implements DataAdapter<HttpData> {

    private static final Comparator<HttpData> COMPARATOR_BY_START_TIME = new Comparator<HttpData>() {
      @Override
      public int compare(HttpData o1, HttpData o2) {
        return o1.myStartTimeMs == o2.myStartTimeMs ? 0 : o1.myStartTimeMs < o2.myStartTimeMs ? -1 : 1;
      }
    };

    @NotNull
    private final List<HttpData> myHttpDataList;

    public HttpDataAdapter(List<HttpData> httpDatas) {
      myHttpDataList = httpDatas;
    }

    // TODO: Specify the result is before or after given time.
    @Override
    public int getClosestTimeIndex(long timeMs) {
      HttpData dataForClosestTime = new HttpData();
      dataForClosestTime.myStartTimeMs = timeMs;
      int index = Collections.binarySearch(myHttpDataList, dataForClosestTime, COMPARATOR_BY_START_TIME);
      if (index < 0) {
        index = -1 * index - 2;
      }
      return Math.max(0, Math.min(myHttpDataList.size() - 1, index));
    }

    @Override
    public SeriesData<HttpData> get(int index) {
      HttpData httpData = myHttpDataList.get(index);
      return new SeriesData<>(httpData.myStartTimeMs, httpData);
    }

    @Override
    public void reset(long deviceStartTimeMs, long studioStartTimeMs) {
    }

    @Override
    public void stop() {
    }
  }
}
