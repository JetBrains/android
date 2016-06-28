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
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Default implementation of {@link DataAdapter} interface
 */
public class DataAdapterImpl<T> implements DataAdapter<T> {
  @NotNull
  private final TLongArrayList myTimestampData;

  @NotNull
  private final List<T> myValues;

  public DataAdapterImpl(@NotNull TLongArrayList timestampData, @NotNull List<T> values) {
    myTimestampData = timestampData;
    myValues = values;
  }

  @Override
  public int getClosestTimeIndex(long timeUs) {
    int index = myTimestampData.binarySearch(timeUs);
    if (index < 0) {
      // No exact match, so return position to the left of the insertion point.
      index = -index - 2;
    }

    return Math.max(0, Math.min(myTimestampData.size() - 1, index));
  }

  @Override
  public SeriesData<T> get(int index) {
    return new SeriesData<>(myTimestampData.get(index), myValues.get(index));
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
