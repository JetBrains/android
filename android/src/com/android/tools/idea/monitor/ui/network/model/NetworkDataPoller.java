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

import com.android.tools.idea.monitor.datastore.*;
import com.android.tools.profiler.proto.*;
import gnu.trove.TLongArrayList;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Collects Network data from the device.
 * TODO: currently, we just keep adding data, we need to come up with other approach,
 *       e.g removing old data or keeping in external memory
 */
public class NetworkDataPoller extends Poller {
  private static final long POLLING_DELAY_NS = TimeUnit.SECONDS.toNanos(1);

  private final TLongArrayList myTimeData = new TLongArrayList();

  private final TLongArrayList myReceivedData = new TLongArrayList();

  private final TLongArrayList mySentData = new TLongArrayList();

  private long myDataRequestStartTimestamp;

  private int myPid;

  private NetworkProfilerServiceGrpc.NetworkProfilerServiceBlockingStub myNetworkService;

  public NetworkDataPoller(@NotNull SeriesDataStore dataStore, int pid) {
    super(dataStore, POLLING_DELAY_NS);
    myPid = pid;

    dataStore.registerAdapter(SeriesDataType.NETWORK_RECEIVED, new LongDataAdapter(myTimeData, myReceivedData));
    dataStore.registerAdapter(SeriesDataType.NETWORK_SENT, new LongDataAdapter(myTimeData, mySentData));
  }

  @Override
  protected void asyncInit() {
    myNetworkService = myService.getNetworkService();
    NetworkProfiler.NetworkStartRequest.Builder requestBuilder = NetworkProfiler.NetworkStartRequest.newBuilder().setAppId(myPid);
    myNetworkService.startMonitoringApp(requestBuilder.build());
    myDataRequestStartTimestamp = Long.MIN_VALUE;
  }

  @Override
  protected void asyncShutdown() {
    myNetworkService.stopMonitoringApp(NetworkProfiler.NetworkStopRequest.newBuilder().setAppId(myPid).build());
  }

  @Override
  protected void poll() {
    NetworkProfiler.NetworkDataRequest.Builder dataRequestBuilder = NetworkProfiler.NetworkDataRequest.newBuilder();
    dataRequestBuilder.setAppId(myPid);
    dataRequestBuilder.setDataType(NetworkProfiler.NetworkDataRequest.NetworkDataType.TRAFFIC);
    dataRequestBuilder.setStartTimestamp(myDataRequestStartTimestamp);
    dataRequestBuilder.setEndTimestamp(Long.MAX_VALUE);
    NetworkProfiler.NetworkDataResponse response;
    try {
      response = myNetworkService.getData(dataRequestBuilder.build());
    } catch (StatusRuntimeException e) {
      cancel(true);
      return;
    }

    List<NetworkProfiler.NetworkProfilerData> dataList = response.getDataList();
    if (dataList.isEmpty()) {
      return;
    }
    for (NetworkProfiler.NetworkProfilerData data : dataList) {
      // Timestamp in ui/studio represented in milliseconds and pulled from a device in nanoseconds
      myTimeData.add(TimeUnit.NANOSECONDS.toMillis(data.getBasicInfo().getEndTimestamp() - myDeviceTimeOffsetNs));
      // Traffics in ui/studio represented in kb and pulled from a device in bytes
      myReceivedData.add(data.getTrafficData().getBytesReceived() / 1024);
      mySentData.add(data.getTrafficData().getBytesSent() / 1024);
      myDataRequestStartTimestamp = Math.max(myDataRequestStartTimestamp, data.getBasicInfo().getEndTimestamp() + 1);
    }
  }
}
