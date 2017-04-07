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
package com.android.tools.datastore;

import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.profilerclient.DeviceProfilerService;
import com.android.tools.profiler.proto.Profiler;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class SeriesDataStoreImpl implements SeriesDataStore {

  /**
   * Key to be used when no target is provided.
   */
  private static final Object NO_TARGET = new Object();

  private static Logger getLogger() { return Logger.getInstance(SeriesDataStoreImpl.class); }

  /**
   * Maps a {@link SeriesDataType} with a map of Object -> {@link DataAdapter}.
   * In case some type has only one adapter associated with it, the map will contain a single <Key, Value> pair.
   * In this scenario, it's safe to return the first element of the Collection returned by map.values().
   * If there are multiple adapters associated with a type, however, it's important to provide the key associated with the adapter being
   * retrieved, as it's going to be obtained by calling map.get(key).
   */
  private Map<SeriesDataType, Map<Object, DataAdapter<?>>> myDataSeriesMap = new HashMap<>();

  private long myDeviceStartTimeNs;

  private long myStudioOffsetTimeNs;

  @NotNull
  private final DeviceProfilerService myDeviceProfilerService;

  public SeriesDataStoreImpl(@NotNull DeviceProfilerService deviceProfilerService) {
    myDeviceProfilerService = deviceProfilerService;
    synchronizeStartTime();
  }

  @Override
  public DeviceProfilerService getDeviceProfilerService() {
    return myDeviceProfilerService;
  }

  @Override
  public void stop() {
    myDataSeriesMap.values().forEach(adaptersMap -> adaptersMap.values().forEach(DataAdapter::stop));
  }

  @Override
  public void reset() {
    synchronizeStartTime();
    myDataSeriesMap.values().forEach(adaptersMap -> adaptersMap.values().forEach(adapter -> adapter.reset()));
  }

  @Override
  public long getLatestTimeUs() {
    return TimeUnit.NANOSECONDS.toMicros(myDeviceStartTimeNs + (System.nanoTime() - myStudioOffsetTimeNs));
  }

  @Override
  public long mapAbsoluteDeviceToRelativeTime(long absoluteTime) {
    return absoluteTime - myDeviceStartTimeNs;
  }

  @Override
  public int getClosestTimeIndex(SeriesDataType type, long timeValue, boolean leftClosest, @Nullable Object target) {
    return getAdapter(type, target).getClosestTimeIndex(timeValue, leftClosest);
  }

  @Override
  public <T> SeriesData<T> getDataAt(SeriesDataType type, int index, @Nullable Object target) {
    return (SeriesData<T>)getAdapter(type, target).get(index);
  }

  @Override
  public <T> SeriesDataList<T> getSeriesData(SeriesDataType type, Range range, @Nullable Object target) {
    return new SeriesDataList<>(range, this, type, target);
  }

  //TODO change the register API to
  // registerAdapter(SeriesDataType<T> DataAdapter<T>) to ensure type safety.
  @Override
  public void registerAdapter(SeriesDataType type, DataAdapter adapter, Object target) {
    if (!myDataSeriesMap.containsKey(type)) {
      myDataSeriesMap.put(type, new HashMap<>());
    }
    Object key = target == null ? NO_TARGET : target;
    myDataSeriesMap.get(type).put(key, adapter);
    adapter.reset();
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

  /**
   * Initialize and synchronize the system start time with the device time. That way we can use (current time - start time) as the offset
   * when converting studio time to device time when making data requests.
   */
  private void synchronizeStartTime() {
    try {
      Profiler.TimesResponse response = myDeviceProfilerService.getProfilerService().getTimes(
        Profiler.TimesRequest.getDefaultInstance());
      myDeviceStartTimeNs = response.getTimestampNs();
      myStudioOffsetTimeNs = System.nanoTime();
    }
    catch (StatusRuntimeException e) {
      getLogger().info("Error during gRPC communication.");
    }
  }
}
