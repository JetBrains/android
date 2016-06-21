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
package com.android.tools.idea.monitor.ui.visual;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.monitor.datastore.DataAdapter;
import com.android.tools.idea.monitor.datastore.SeriesDataList;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.idea.monitor.profilerclient.DeviceProfilerService;
import com.android.tools.idea.monitor.ui.visual.data.LongTestDataGenerator;
import com.android.tools.idea.monitor.ui.visual.data.MemoryTestDataGenerator;
import com.android.tools.idea.monitor.ui.visual.data.SimpleEventTestDataGenerator;
import com.android.tools.idea.monitor.ui.visual.data.StackedEventTestDataGenerator;

import java.util.HashMap;
import java.util.Map;

public final class VisualTestSeriesDataStore implements SeriesDataStore {

  private Map<SeriesDataType, DataAdapter<?>> myDataSeriesMap = new HashMap<>();

  private long mStartTime;

  public VisualTestSeriesDataStore() {
    mStartTime = System.currentTimeMillis();
    startGeneratingData();
  }

  @Override
  public DeviceProfilerService getDeviceProfilerService() {
    return null;
  }

  @Override
  public void reset() {
    for (DataAdapter<?> adapter : myDataSeriesMap.values()) {
      adapter.reset();
    }
  }

  @Override
  public long getLatestTime() {
    long now = System.currentTimeMillis();
    return now - mStartTime;
  }

  @Override
  public int getClosestTimeIndex(SeriesDataType type, long timeValue) {
    DataAdapter<?> adapter = myDataSeriesMap.get(type);
    assert adapter != null;
    return adapter.getClosestTimeIndex(timeValue);
  }

  @Override
  public <T> SeriesData<T> getDataAt(SeriesDataType type, int index) {
    DataAdapter<?> adapter = myDataSeriesMap.get(type);
    assert adapter != null;
    return (SeriesData<T>)adapter.get(index);
  }

  @Override
  public <T> SeriesDataList<T> getSeriesData(SeriesDataType type, Range range) {
    return new SeriesDataList<>(range, this, type);
  }

  @Override
  public void registerAdapter(SeriesDataType type, DataAdapter adapter) {
    myDataSeriesMap.put(type, adapter);
    adapter.setStartTime(mStartTime);
  }

  @Override
  public long getDeviceTimeOffset() {
    return 0;
  }

  private void startGeneratingData() {
    for (SeriesDataType type : SeriesDataType.values()) {
      switch (type) {
        case CPU_MY_PROCESS:
          registerAdapter(type, new LongTestDataGenerator(0, 60, false));
          break;
        case CPU_OTHER_PROCESSES:
          registerAdapter(type, new LongTestDataGenerator(0, 20, false));
          break;
        case CPU_THREADS:
        case NETWORK_CONNECTIONS:
          registerAdapter(type, new LongTestDataGenerator(0, 10, false));
          break;
        case MEMORY_TOTAL:
        case MEMORY_JAVA:
          registerAdapter(type, new MemoryTestDataGenerator(true));
          break;
        case MEMORY_OTHERS:
          registerAdapter(type, new MemoryTestDataGenerator(false));
          break;
        case EVENT_ACTIVITY_ACTION:
          registerAdapter(type, new StackedEventTestDataGenerator("Activities"));
          break;
        case EVENT_FRAGMENT_ACTION:
          registerAdapter(type, new StackedEventTestDataGenerator("Fragments"));
          break;
        case EVENT_SIMPLE_ACTION:
          registerAdapter(type, new SimpleEventTestDataGenerator());
          break;
        default:
          registerAdapter(type, new LongTestDataGenerator(-20, 100, true));
          break;
      }
    }
  }

}
