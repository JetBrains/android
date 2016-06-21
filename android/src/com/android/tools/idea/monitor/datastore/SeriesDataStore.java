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
package com.android.tools.idea.monitor.datastore;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.monitor.profilerclient.DeviceProfilerService;

/**
 * This interface is the minimal interface required for defining an object that
 * provides data to the UI. Each {@link SeriesDataType} expects to be backed by a {@link SeriesDataList}.
 */
public interface SeriesDataStore {

  DeviceProfilerService getDeviceProfilerService();

  /**
   * Resets all data sources to an empty state.
   */
  void reset();

  /**
   * @return the timestamp of the most current set of data.
   */
  long getLatestTime();

  /**
   * Function to return a typed {@link SeriesDataList} that is scoped to allow access to data within a specific range.
   *
   * @param type  The type of data being requested
   * @param range The range the list is scoped to
   * @param <T>   The template type the raw data is formatted as.
   * @return An immutable list that acts as a view into the data found in the data store.
   */
  <T> SeriesDataList<T> getSeriesData(SeriesDataType type, Range range);

  /**
   * Returns the {@link SeriesData} at a given index, used by the {@link SeriesDataList}.
   */
  <T> SeriesData<T> getDataAt(SeriesDataType type, int index);

  /**
   * Returns the closest index less than or equal to the time value.
   */
  int getClosestTimeIndex(SeriesDataType type, long timeValue);

  /**
   * Register a {@link DataAdapter} in the data store.
   * The adapter is associated with a {@link SeriesDataType} and will store data of this type.
   */
  void registerAdapter(SeriesDataType type, DataAdapter adapter);

  /**
   * Time offset, in nanoseconds, that should be subtracted from the timestamp of data collected from the device.
   */
  long getDeviceTimeOffset();
}
