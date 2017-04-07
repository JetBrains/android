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

/**
 * This interface is the minimal interface required for defining an object that
 * provides data to the UI. Each {@link SeriesDataType} expects to be backed by a {@link SeriesDataList}.
 */
public interface SeriesDataStore {

  DeviceProfilerService getDeviceProfilerService();

  /**
   * Stops any further data requests.
   */
  void stop();

  /**
   * Resets all data sources to an empty state.
   */
  void reset();

  /**
   * @return the estimated current time on the device.
   */
  long getLatestTimeUs();

  /**
   * @param absoluteTime  The absolute device time.
   * @return the relative time to t=0 of this {@link SeriesDataStore}
   */
  long mapAbsoluteDeviceToRelativeTime(long absoluteTime);

  /**
   * Function to return a typed {@link SeriesDataList} that is scoped to allow access to data within a specific range.
   *
   * @param type  The type of data being requested
   * @param range The range the list is scoped to
   * @param target (optional) Object that can be mapped to an adapter in case there are multiple adapters of the same type registered.
   * @param <T>   The template type the raw data is formatted as.
   * @return An immutable list that acts as a view into the data found in the data store.
   */
  <T> SeriesDataList<T> getSeriesData(SeriesDataType type, Range range, Object target);

  /**
   * Returns the {@link SeriesData} at a given index, used by the {@link SeriesDataList}.
   */
  <T> SeriesData<T> getDataAt(SeriesDataType type, int index, Object target);

  /**
   * Returns the closest index to the time value.
   */
  int getClosestTimeIndex(SeriesDataType type, long timeValue, boolean leftClosest, Object target);

  /**
   * Register a {@link DataAdapter} in the data store.
   * The adapter is associated with a {@link SeriesDataType} and will store data of this type.
   * An optional parameter (target) can be set in case there are multiple adapters of the same type.
   */
  void registerAdapter(SeriesDataType type, DataAdapter adapter, Object target);

  default void registerAdapter(SeriesDataType type, DataAdapter adapter) {
    registerAdapter(type, adapter, null);
  }

  default int getClosestTimeIndex(SeriesDataType type, long timeValue, boolean leftClosest) {
    return getClosestTimeIndex(type, timeValue, leftClosest, null);
  }

  default <T> SeriesDataList<T> getSeriesData(SeriesDataType type, Range range) {
    return getSeriesData(type, range, null);
  }

  default <T> SeriesData<T> getDataAt(SeriesDataType type, int index) {
    return getDataAt(type, index, null);
  }
}
