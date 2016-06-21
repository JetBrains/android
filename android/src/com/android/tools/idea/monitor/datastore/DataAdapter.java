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

import com.android.tools.adtui.model.SeriesData;

/**
 * Interface to use with the {@link SeriesDataStore}. This object represents the in memory representation of the data available.
 * @param <T> The type of data to be returned when requested from the {@link SeriesDataStore}
 */
public interface DataAdapter<T> {

  /**
   * It is important that all adapters are synchronized to the same point as the adapters
   * a {@link SeriesDataStore} the start time gets set on that adapter.
   */
  void setStartTime(long time);

  /**
   * This function should return the closest index to the left for a specific time. The time passed in here will be the delta time
   * between the start time and the time requested. For example if the UI requested the first point in time it would pass 0.
   */
  // TODO: think about a refactoring that allows this method to be reused across classes that implement this interface.
  // All the implementations look like the same now.
  int getClosestTimeIndex(long time);

  /**
   * Each data adapter is responsible for creating a {@link SeriesData} object that will be returned to the UI in use for rendering.
   */
  SeriesData<T> get(int index);

  /**
   * If the UI gets reset, this function will get called followed by a setStartTime indicating that all previous data should be cleared.
   */
  void reset();
}
