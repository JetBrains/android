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

import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.monitor.datastore.*;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * TODO consider putting these into maps?
 */
public class EnergyPoller extends Poller {
  private static final long POLL_PERIOD_NS = TimeUnit.MILLISECONDS.toNanos(250);
  private static final SeriesDataType[] DATA_TYPES = {
    SeriesDataType.ENERGY_SCREEN,
    SeriesDataType.ENERGY_CPU,
    SeriesDataType.ENERGY_SENSORS,
    SeriesDataType.ENERGY_CELL_NETWORK,
    SeriesDataType.ENERGY_WIFI_NETWORK,
    SeriesDataType.ENERGY_TOTAL,
  };

  private final int myAppId;
  private long myStartPollingTimeNs = -1; // TODO remove these temporary variables when we have real data.
  private long myDeviceStartTime = -1;    // TODO remove these temporary variables when we have real data.
  private boolean myIsDisplayingDeltas = false;
  private HashMap<SeriesDataType, EnergyDataAdapter> myAdapters;

  public EnergyPoller(@NotNull SeriesDataStore dataStore, int appId) {
    super(dataStore, POLL_PERIOD_NS);
    myAppId = appId;
    myDeviceStartTime = dataStore.getLatestTimeUs();

    myAdapters = new HashMap<>();
    for (SeriesDataType type : DATA_TYPES) {
      myAdapters.put(type, new EnergyDataAdapter());
      dataStore.registerAdapter(type, myAdapters.get(type));
    }
  }

  public void toggleDataDisplay() {
    myIsDisplayingDeltas = !myIsDisplayingDeltas;

    for (SeriesDataType type : DATA_TYPES) {
      myAdapters.get(type).setUseCumulative(myIsDisplayingDeltas);
    }
  }

  @Override
  protected void asyncInit() {

  }

  @Override
  protected void asyncShutdown() {

  }

  @Override
  protected void poll() {
    // TODO change to polling real data from perfd
    long timeOffsetNs;
    long currentDeviceTimeUs;
    if (myStartPollingTimeNs < 0) {
      myStartPollingTimeNs = System.nanoTime();
      timeOffsetNs = 0;
    }
    else {
      timeOffsetNs = System.nanoTime() - myStartPollingTimeNs;
    }
    currentDeviceTimeUs = TimeUnit.NANOSECONDS.toMicros(timeOffsetNs) + myDeviceStartTime;

    long totalDelta = 0;
    for (SeriesDataType type : DATA_TYPES) {
      if (type == SeriesDataType.ENERGY_TOTAL) {
        continue; // Skip as it's a special stat that aggregates all other stats.
      }
      long temp = Math.max(0, (long)(Math.random() * 100 - 90));
      myAdapters.get(type).updateByDelta(currentDeviceTimeUs, temp);
      totalDelta += temp;
    }
    myAdapters.get(SeriesDataType.ENERGY_TOTAL).updateByDelta(currentDeviceTimeUs, totalDelta);
  }

  private class EnergyDataAdapter implements DataAdapter<Long> {
    private long myLatestTotal = 0;
    private long myLatestDelta = 0;
    private boolean myUseCumulative = false;

    @NotNull
    private final TLongArrayList myTimestampData = new TLongArrayList();

    @NotNull
    private final TLongArrayList myCumulativeData = new TLongArrayList();

    @NotNull
    private final TLongArrayList myDeltaData = new TLongArrayList();

    public void setUseCumulative(boolean useCumulative) {
      myUseCumulative = useCumulative;
    }

    public void updateByDelta(long timestamp, long delta) {
      myLatestDelta = delta;
      myLatestTotal += delta;
      insertLatestToList(timestamp);
    }

    public void updateByTotal(long timestamp, long total) {
      myLatestDelta = myLatestTotal - total;
      myLatestTotal += total;
      insertLatestToList(timestamp);
    }

    private void insertLatestToList(long timestamp) {
      myTimestampData.add(timestamp);
      myDeltaData.add(myLatestDelta);
      myCumulativeData.add(myLatestTotal);
    }

    @Override
    public int getClosestTimeIndex(long timeUs, boolean leftClosest) {
      return DataAdapter.getClosestIndex(myTimestampData, timeUs, leftClosest);
    }

    @Override
    public SeriesData<Long> get(int index) {
      if (myUseCumulative) {
        return new SeriesData<>(myTimestampData.get(index), myCumulativeData.get(index));
      }
      return new SeriesData<>(myTimestampData.get(index), myDeltaData.get(index));
    }

    @Override
    public void reset(long deviceStartTimeUs, long studioStartTimeUs) {
      // TODO: implement
    }

    @Override
    public void stop() {
      // TODO: implement
    }
  }
}
