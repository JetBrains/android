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

import com.android.tools.idea.monitor.datastore.*;
import com.android.tools.profiler.proto.EnergyProfilerService.*;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

// TODO To be done in next CL:
// - Add support for energy status that's currently reporting 0 in poll()
public class EnergyPoller extends Poller {
  public static final SeriesDataType[] ENERGY_DATA_TYPES = {
    SeriesDataType.ENERGY_SCREEN,
    SeriesDataType.ENERGY_CPU_SYSTEM,
    SeriesDataType.ENERGY_CPU_USER,
    SeriesDataType.ENERGY_SENSORS,
    SeriesDataType.ENERGY_CELL_NETWORK,
    SeriesDataType.ENERGY_WIFI_NETWORK,
    SeriesDataType.ENERGY_TOTAL,
  };
  private static final long POLL_PERIOD_NS = TimeUnit.MILLISECONDS.toNanos(1000);
  private static Logger getLog() { return Logger.getInstance(EnergyPoller.class.getCanonicalName()); }

  private final int myAppId;
  private long myLatestSampleTimestampNs;
  private HashMap<SeriesDataType, EnergyDataAdapter> myAdapters;

  public EnergyPoller(@NotNull SeriesDataStore dataStore, int appId, boolean showInstantanenousByDefault) {
    super(dataStore, POLL_PERIOD_NS);
    myAppId = appId;
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
  protected void asyncInit() {
    StartEnergyCollectionRequest request = StartEnergyCollectionRequest.newBuilder().setAppId(myAppId).build();
    EnergyCollectionStatusResponse response = myService.getEnergyService().startCollection(request);
    myLatestSampleTimestampNs = response.getTimestamp(); // Initially the latest sample time is same as start time.
  }

  @Override
  protected void asyncShutdown() {
    StopEnergyCollectionRequest request = StopEnergyCollectionRequest.newBuilder().setAppId(myAppId).build();
    myService.getEnergyService().stopCollection(request);
    // TODO what if this fails?
  }

  @Override
  protected void poll() {
    try {
      EnergyDataRequest request = EnergyDataRequest.newBuilder()
        .setAppId(myAppId)
        .setStartTimeExcl(myLatestSampleTimestampNs)
        .setEndTimeIncl(Long.MAX_VALUE)
        .build();

      EnergyDataResponse response = myService.getEnergyService().getData(request);

      List<EnergyDataResponse.EnergySample> sampleList = response.getEnergySamplesList();
      for (EnergyDataResponse.EnergySample sample : sampleList) {
        long timestamp = TimeUnit.NANOSECONDS.toMicros(sample.getTimestamp());
        long total = 0;
        myAdapters.get(SeriesDataType.ENERGY_SCREEN).update(timestamp, sample.getScreenPowerUsage());
        myAdapters.get(SeriesDataType.ENERGY_CPU_SYSTEM).update(timestamp, sample.getCpuSystemPowerUsage());
        myAdapters.get(SeriesDataType.ENERGY_CPU_USER).update(timestamp, sample.getCpuUserPowerUsage());
        myAdapters.get(SeriesDataType.ENERGY_SENSORS).update(timestamp, 0);
        myAdapters.get(SeriesDataType.ENERGY_CELL_NETWORK).update(timestamp, 0);
        myAdapters.get(SeriesDataType.ENERGY_WIFI_NETWORK).update(timestamp, 0);

        for (SeriesDataType type : ENERGY_DATA_TYPES) {
          if (type != SeriesDataType.ENERGY_TOTAL) {
            total += myAdapters.get(type).getLatest();
          }
        }
        myAdapters.get(SeriesDataType.ENERGY_TOTAL).update(timestamp, total);
      }

      if (myLatestSampleTimestampNs < response.getLatestSampleTimestamp()) {
        myLatestSampleTimestampNs = response.getLatestSampleTimestamp();
      }

    } catch (StatusRuntimeException e) {
      getLog().info("Server most likely unreachable.");
      cancel(true);
    }
  }
}
