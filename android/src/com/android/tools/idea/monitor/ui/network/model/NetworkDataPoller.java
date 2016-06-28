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
import com.android.tools.idea.monitor.ui.network.view.NetworkRadioSegment;
import com.android.tools.profiler.proto.*;
import gnu.trove.TLongArrayList;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Collects Network data from the device.
 * TODO: currently, we just keep adding data, we need to come up with other approach,
 * e.g removing old data or keeping in external memory
 */
public class NetworkDataPoller extends Poller {
  private static final long POLLING_DELAY_NS = TimeUnit.SECONDS.toNanos(1);

  private final TLongArrayList myTrafficTimeData = new TLongArrayList();

  private final TLongArrayList myReceivedData = new TLongArrayList();

  private final TLongArrayList mySentData = new TLongArrayList();

  private final TLongArrayList myConnectionsTimeData = new TLongArrayList();

  private final TLongArrayList myConnectionsData = new TLongArrayList();

  private final TLongArrayList myConnectivityTimeData = new TLongArrayList();

  private final List<NetworkRadioSegment.RadioState> myRadioData = new ArrayList<>();

  private final List<NetworkRadioSegment.NetworkType> myNetworkTypeData = new ArrayList<>();

  private final Map<NetworkProfiler.NetworkDataRequest.NetworkDataType, Long> myLatestTimestamps = new EnumMap<>(
    NetworkProfiler.NetworkDataRequest.NetworkDataType.class);

  private NetworkProfilerServiceGrpc.NetworkProfilerServiceBlockingStub myNetworkService;

  private int myPid;

  public NetworkDataPoller(@NotNull SeriesDataStore dataStore, int pid) {
    super(dataStore, POLLING_DELAY_NS);
    myPid = pid;
    registerAdapters();
  }

  private void registerAdapters() {
    myDataStore.registerAdapter(SeriesDataType.NETWORK_RECEIVED, new LongDataAdapter(myTrafficTimeData, myReceivedData));
    myDataStore.registerAdapter(SeriesDataType.NETWORK_SENT, new LongDataAdapter(myTrafficTimeData, mySentData));

    myDataStore.registerAdapter(SeriesDataType.NETWORK_CONNECTIONS, new LongDataAdapter(myConnectionsTimeData, myConnectionsData));

    myDataStore.registerAdapter(SeriesDataType.NETWORK_RADIO, new DataAdapterImpl<>(myConnectivityTimeData, myRadioData));
    myDataStore.registerAdapter(SeriesDataType.NETWORK_TYPE, new DataAdapterImpl<>(myConnectivityTimeData, myNetworkTypeData));
  }

  private void requestData(NetworkProfiler.NetworkDataRequest.NetworkDataType dataType) {
    long latestTimestamp = myLatestTimestamps.containsKey(dataType) ? myLatestTimestamps.get(dataType) : Long.MIN_VALUE;

    NetworkProfiler.NetworkDataRequest.Builder requestBuilder = NetworkProfiler.NetworkDataRequest.newBuilder();
    requestBuilder.setAppId(myPid).setDataType(dataType).setStartTimestamp(latestTimestamp).setEndTimestamp(Long.MAX_VALUE);
    NetworkProfiler.NetworkDataResponse response;
    try {
      response = myNetworkService.getData(requestBuilder.build());
    }
    catch (StatusRuntimeException e) {
      cancel(true);
      return;
    }

    for (NetworkProfiler.NetworkProfilerData data : response.getDataList()) {
      // Timestamp in ui/studio represented in microseconds and pulled from a device in nanoseconds
      long timestamp = TimeUnit.NANOSECONDS.toMicros(data.getBasicInfo().getEndTimestamp());

      if (dataType == NetworkProfiler.NetworkDataRequest.NetworkDataType.TRAFFIC) {
        // Traffics in ui/studio represented in kb and pulled from a device in bytes
        myTrafficTimeData.add(timestamp);
        myReceivedData.add(data.getTrafficData().getBytesReceived() / 1024);
        mySentData.add(data.getTrafficData().getBytesSent() / 1024);
      }
      else if (dataType == NetworkProfiler.NetworkDataRequest.NetworkDataType.CONNECTIONS) {
        myConnectionsTimeData.add(timestamp);
        myConnectionsData.add(data.getConnectionData().getConnectionNumber());
      }
      else if (dataType == NetworkProfiler.NetworkDataRequest.NetworkDataType.CONNECTIVITY) {
        myConnectivityTimeData.add(timestamp);
        // TODO: consider using RadioState enum from proto
        switch (data.getConnectivityData().getRadioState()) {
          case ACTIVE:
            myRadioData.add(NetworkRadioSegment.RadioState.ACTIVE);
            break;
          case IDLE:
            myRadioData.add(NetworkRadioSegment.RadioState.IDLE);
            break;
          case SLEEPING:
            myRadioData.add(NetworkRadioSegment.RadioState.SLEEPING);
            break;
          default:
            myRadioData.add(NetworkRadioSegment.RadioState.NONE);
        }

        switch (data.getConnectivityData().getDefaultNetworkType()) {
          case MOBILE:
            myNetworkTypeData.add(NetworkRadioSegment.NetworkType.MOBILE);
            break;
          case WIFI:
            myNetworkTypeData.add(NetworkRadioSegment.NetworkType.WIFI);
            break;
          default:
            myNetworkTypeData.add(NetworkRadioSegment.NetworkType.NONE);
        }
      }

      latestTimestamp = Math.max(latestTimestamp, data.getBasicInfo().getEndTimestamp() + 1);
    }
    myLatestTimestamps.put(dataType, latestTimestamp);
  }

  @Override
  protected void asyncInit() {
    myNetworkService = myService.getNetworkService();
    NetworkProfiler.NetworkStartRequest.Builder requestBuilder = NetworkProfiler.NetworkStartRequest.newBuilder().setAppId(myPid);
    myNetworkService.startMonitoringApp(requestBuilder.build());
  }

  @Override
  protected void poll() {
    requestData(NetworkProfiler.NetworkDataRequest.NetworkDataType.TRAFFIC);
    requestData(NetworkProfiler.NetworkDataRequest.NetworkDataType.CONNECTIONS);
    requestData(NetworkProfiler.NetworkDataRequest.NetworkDataType.CONNECTIVITY);
  }

  @Override
  protected void asyncShutdown() {
    myNetworkService.stopMonitoringApp(NetworkProfiler.NetworkStopRequest.newBuilder().setAppId(myPid).build());
  }
}
