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
package com.android.tools.idea.monitor.ui.energy.model;

import com.android.tools.datastore.*;
import com.android.tools.profiler.proto.EnergyProfiler;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class EnergyPoller extends Poller {
  public static final SeriesDataType[] ENERGY_DATA_TYPES = {
    SeriesDataType.ENERGY_SCREEN,
    SeriesDataType.ENERGY_CPU_SYSTEM,
    SeriesDataType.ENERGY_CPU_USER,
    SeriesDataType.ENERGY_CELL_NETWORK,
    SeriesDataType.ENERGY_WIFI_NETWORK,
    SeriesDataType.ENERGY_TOTAL,
  };
  private static final long POLL_PERIOD_NS = TimeUnit.MILLISECONDS.toNanos(1000);

  private final int myPid;
  private long myLatestSampleTimestampNs;
  private HashMap<SeriesDataType, EnergyDataAdapter> myAdapters;

  public EnergyPoller(@NotNull SeriesDataStore dataStore, int pid, boolean showInstantanenousByDefault) {
    super(dataStore, POLL_PERIOD_NS);
    myPid = pid;
    myLatestSampleTimestampNs = -1;

    myAdapters = new HashMap<>();
    for (SeriesDataType type : ENERGY_DATA_TYPES) {
      EnergyDataAdapter adapter = new EnergyDataAdapter();
      adapter.setReturnInstantaneousData(showInstantanenousByDefault);
      dataStore.registerAdapter(type, adapter);
      myAdapters.put(type, adapter);
    }
  }

  /**
   * Returns the adapter for given type, if it exists.
   */
  public EnergyDataAdapter getEnergyAdapter(SeriesDataType dataType) {
    return myAdapters.get(dataType);
  }

  @Override
  protected void asyncInit() throws StatusRuntimeException {
    EnergyProfiler.StartEnergyCollectionRequest request = EnergyProfiler.StartEnergyCollectionRequest.newBuilder().setAppId(myPid).build();
    EnergyProfiler.EnergyCollectionStatusResponse response = myService.getEnergyService().startCollection(request);
    myLatestSampleTimestampNs = response.getTimestamp(); // Initially the latest sample time is same as start time.
  }

  @Override
  protected void asyncShutdown() throws StatusRuntimeException {
    EnergyProfiler.StopEnergyCollectionRequest request = EnergyProfiler.StopEnergyCollectionRequest.newBuilder().setAppId(myPid).build();
    myService.getEnergyService().stopCollection(request);
  }

  @Override
  protected void poll() throws StatusRuntimeException {
    EnergyProfiler.EnergyDataRequest request = EnergyProfiler.EnergyDataRequest.newBuilder()
      .setAppId(myPid)
      .setStartTimeExcl(myLatestSampleTimestampNs)
      .setEndTimeIncl(Long.MAX_VALUE)
      .build();

    EnergyProfiler.EnergyDataResponse response = myService.getEnergyService().getEnergyData(request);

    long latestTimestampNs = 0;
    for (EnergyProfiler.EnergySample sample : response.getEnergySamplesList()) {
      long timestampUs = TimeUnit.NANOSECONDS.toMicros(sample.getTimestamp());
      long total = 0;
      myAdapters.get(SeriesDataType.ENERGY_SCREEN).update(timestampUs, sample.getScreenPowerUsage());
      myAdapters.get(SeriesDataType.ENERGY_CPU_SYSTEM).update(timestampUs, sample.getCpuSystemPowerUsage());
      myAdapters.get(SeriesDataType.ENERGY_CPU_USER).update(timestampUs, sample.getCpuUserPowerUsage());
      myAdapters.get(SeriesDataType.ENERGY_CELL_NETWORK).update(timestampUs, sample.getCellNetworkPowerUsage());
      myAdapters.get(SeriesDataType.ENERGY_WIFI_NETWORK).update(timestampUs, sample.getWifiNetworkPowerUsage());

      for (SeriesDataType type : ENERGY_DATA_TYPES) {
        if (type != SeriesDataType.ENERGY_TOTAL) {
          total += myAdapters.get(type).getLatest();
        }
      }
      myAdapters.get(SeriesDataType.ENERGY_TOTAL).update(timestampUs, total);

      if (latestTimestampNs < sample.getTimestamp()) {
        latestTimestampNs = sample.getTimestamp();
      }
    }

    // Update the start time for next query
    if (myLatestSampleTimestampNs < latestTimestampNs) {
      myLatestSampleTimestampNs = latestTimestampNs;
    }
  }
}
