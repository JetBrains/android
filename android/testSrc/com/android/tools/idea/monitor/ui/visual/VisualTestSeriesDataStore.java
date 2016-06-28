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
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.network.view.NetworkRadioSegment;
import com.android.tools.idea.monitor.ui.visual.data.*;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class VisualTestSeriesDataStore implements SeriesDataStore {

  private Map<SeriesDataType, Map<Object, DataAdapter<?>>> myDataSeriesMap = new HashMap<>();

  private static final Object NO_TARGET = new Object();

  private long mStartTimeUs;

  public VisualTestSeriesDataStore() {
    mStartTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    startGeneratingData();
  }

  @Override
  public DeviceProfilerService getDeviceProfilerService() {
    return null;
  }

  @Override
  public EventDispatcher<ProfilerEventListener> getEventDispatcher() {
    return null;
  }

  @Override
  public void stop() {
  }

  @Override
  public void reset() {
    mStartTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    myDataSeriesMap.values().forEach(adaptersMap -> adaptersMap.values().forEach(
      adapter -> adapter.reset(mStartTimeUs, mStartTimeUs)));
  }

  @Override
  public long getLatestTimeUs() {
    return TimeUnit.NANOSECONDS.toMicros(System.nanoTime()) - mStartTimeUs;
  }

  @Override
  public int getClosestTimeIndex(SeriesDataType type, long timeValue, Object target) {
    return getAdapter(type, target).getClosestTimeIndex(timeValue);
  }

  @Override
  public <T> SeriesData<T> getDataAt(SeriesDataType type, int index, Object target) {
    return (SeriesData<T>)getAdapter(type, target).get(index);
  }

  @Override
  public <T> SeriesDataList<T> getSeriesData(SeriesDataType type, Range range, Object target) {
    return new SeriesDataList<>(range, this, type, target);
  }

  @Override
  public void registerAdapter(SeriesDataType type, DataAdapter adapter, Object target) {
    if (!myDataSeriesMap.containsKey(type)) {
      myDataSeriesMap.put(type, new HashMap<>());
    }
    Object key = target == null ? NO_TARGET : target;
    myDataSeriesMap.get(type).put(key, adapter);
    adapter.reset(mStartTimeUs, mStartTimeUs);
  }

  /**
   * Returns an adapter of a determined type. A target object can be used in case the data store has multiple adapters of the same type.
   * The target can be null and, in this case, the only adapter associated with the type will be returned.
   */
  @NotNull
  private DataAdapter<?> getAdapter(SeriesDataType type, @Nullable Object target) {
    if (target == null) {
      target = NO_TARGET;
    }
    assert myDataSeriesMap.containsKey(type);
    DataAdapter<?> adapter = myDataSeriesMap.get(type).get(target);
    assert adapter != null;
    return adapter;
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
        case CPU_THREAD_STATE:
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
        case NETWORK_RADIO:
          registerAdapter(type, new EnumTestDataGenerator<>(NetworkRadioSegment.RadioState.class));
          break;
        case NETWORK_TYPE:
          registerAdapter(type, new EnumTestDataGenerator<>(NetworkRadioSegment.NetworkType.class));
          break;
        default:
          registerAdapter(type, new LongTestDataGenerator(-20, 100, true));
          break;
      }
    }
  }

}
