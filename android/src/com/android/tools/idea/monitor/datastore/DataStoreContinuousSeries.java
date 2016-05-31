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
import com.android.tools.adtui.model.ContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

public class DataStoreContinuousSeries implements ContinuousSeries {
  @NotNull
  private final SeriesDataStore mStore;

  @NotNull
  private final SeriesDataType mType;

  public DataStoreContinuousSeries(@NotNull SeriesDataStore store, @NotNull SeriesDataType type) {
    mStore = store;
    mType = type;
  }

  @Override
  public ImmutableList<SeriesData<Long>> getDataForXRange(@NotNull Range xRange) {
    return mStore.getSeriesData(mType, xRange);
  }

  @Override
  public SeriesData<Long> getDataAtXValue(long x) {
    int index = mStore.getClosestTimeIndex(x);
    SeriesData<Long> data = new SeriesData<>();
    data.time = mStore.getTimeAtIndex(index);
    data.value = mStore.getValueAtIndex(mType, index);
    return data;
  }
}
