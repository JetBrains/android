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

import com.android.tools.datastore.*;
import com.android.tools.idea.monitor.ui.network.view.NetworkRadioSegment;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import gnu.trove.TLongArrayList;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Collects Network data from the device.
 * TODO: currently, we just keep adding data, we need to come up with other approach,
 * e.g removing old data or keeping in external memory
 */
public class NetworkDataPoller extends Poller {

  private final SpeedData mySpeedData = new SpeedData();

  private final TLongArrayList myConnectionsTimeData = new TLongArrayList();

  private final TLongArrayList myConnectionsData = new TLongArrayList();

  private final TLongArrayList myRadioTimeData = new TLongArrayList();
  private final TLongArrayList myNetworkTypeTimeData = new TLongArrayList();

  private final List<NetworkRadioSegment.RadioState> myRadioData = new ArrayList<>();

  private final List<NetworkRadioSegment.NetworkType> myNetworkTypeData = new ArrayList<>();

  private NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkService;

  private int myPid;

  private long myLatestTimestampNs = Long.MIN_VALUE;

  public NetworkDataPoller(@NotNull SeriesDataStore dataStore, int pid) {
    super(dataStore, POLLING_DELAY_NS);
    myPid = pid;
    registerAdapters();
  }

  private void registerAdapters() {
    myDataStore.registerAdapter(SeriesDataType.NETWORK_RECEIVED, new LongDataAdapter(mySpeedData.getTimeData(), mySpeedData.getReceived()));
    myDataStore.registerAdapter(SeriesDataType.NETWORK_SENT, new LongDataAdapter(mySpeedData.getTimeData(), mySpeedData.getSent()));

    myDataStore.registerAdapter(SeriesDataType.NETWORK_CONNECTIONS, new LongDataAdapter(myConnectionsTimeData, myConnectionsData));

    myDataStore.registerAdapter(SeriesDataType.NETWORK_RADIO, new DataAdapterImpl<>(myRadioTimeData, myRadioData));
    myDataStore.registerAdapter(SeriesDataType.NETWORK_TYPE, new DataAdapterImpl<>(myNetworkTypeTimeData, myNetworkTypeData));
  }

  @Override
  protected void asyncInit() throws StatusRuntimeException {
    myNetworkService = myService.getNetworkService();
    NetworkProfiler.NetworkStartRequest.Builder requestBuilder = NetworkProfiler.NetworkStartRequest.newBuilder().setAppId(myPid);
    myNetworkService.startMonitoringApp(requestBuilder.build());
  }

  @Override
  protected void asyncShutdown() throws StatusRuntimeException {
    myNetworkService.stopMonitoringApp(NetworkProfiler.NetworkStopRequest.newBuilder().setAppId(myPid).build());
  }

  @Override
  protected void poll() throws StatusRuntimeException {
    NetworkProfiler.NetworkDataRequest request = NetworkProfiler.NetworkDataRequest.newBuilder()
      .setAppId(myPid)
      .setType(NetworkProfiler.NetworkDataRequest.Type.ALL)
      .setStartTimestamp(myLatestTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    NetworkProfiler.NetworkDataResponse response = myNetworkService.getData(request);

    for (NetworkProfiler.NetworkProfilerData data : response.getDataList()) {
      // Timestamp in ui/studio represented in microseconds and pulled from a device in nanoseconds
      long timestampNs = data.getBasicInfo().getEndTimestamp();
      long timestampUs = TimeUnit.NANOSECONDS.toMicros(timestampNs);

      if (data.getDataCase() == NetworkProfiler.NetworkProfilerData.DataCase.SPEED_DATA) {
        mySpeedData.add(data.getSpeedData().getSent(), data.getSpeedData().getReceived(), timestampUs);
      }
      else if (data.getDataCase() == NetworkProfiler.NetworkProfilerData.DataCase.CONNECTION_DATA) {
        myConnectionsTimeData.add(timestampUs);
        myConnectionsData.add(data.getConnectionData().getConnectionNumber());
      }
      else if (data.getDataCase() == NetworkProfiler.NetworkProfilerData.DataCase.CONNECTIVITY_DATA) {
        // TODO: consider using RadioState enum from proto
        {
          NetworkRadioSegment.RadioState type;
          switch (data.getConnectivityData().getRadioState()) {
            case ACTIVE:
              type = NetworkRadioSegment.RadioState.ACTIVE;
              break;
            case IDLE:
              type = NetworkRadioSegment.RadioState.IDLE;
              break;
            case SLEEPING:
              type = NetworkRadioSegment.RadioState.SLEEPING;
              break;
            default:
              type = NetworkRadioSegment.RadioState.NONE;
          }

          if (myRadioData.size() == 0 || myRadioData.get(myRadioData.size() - 1) != type) {
            myRadioData.add(type);
            myRadioTimeData.add(timestampUs);
          }
        }

        {
          NetworkRadioSegment.NetworkType type;
          switch (data.getConnectivityData().getDefaultNetworkType()) {
            case MOBILE:
              type = NetworkRadioSegment.NetworkType.MOBILE;
              break;
            case WIFI:
              type = NetworkRadioSegment.NetworkType.WIFI;
              break;
            default:
              type = NetworkRadioSegment.NetworkType.NONE;
          }

          if (myNetworkTypeData.size() == 0 || myNetworkTypeData.get(myNetworkTypeData.size() - 1) != type) {
            myNetworkTypeData.add(type);
            myNetworkTypeTimeData.add(timestampUs);
          }
        }
      }

      myLatestTimestampNs = Math.max(myLatestTimestampNs, timestampNs + 1);
    }
  }
}
