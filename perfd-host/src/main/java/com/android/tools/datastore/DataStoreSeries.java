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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.SeriesData;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataStoreSeries<E> implements DataSeries<E> {
  @NotNull
  private final SeriesDataStore mStore;

  @NotNull
  private final SeriesDataType mType;

  /**
   * This target object is passed to the data store so it can know from which adapter the data should be pulled from.
   * If it's null, the only adapter associated with the type will be used.
   */
  @Nullable
  private final Object mTarget;

  public DataStoreSeries(@NotNull SeriesDataStore store, @NotNull SeriesDataType type, @Nullable Object target) {
    mStore = store;
    mType = type;
    mTarget = target;
  }

  public DataStoreSeries(@NotNull SeriesDataStore store, @NotNull SeriesDataType type) {
    this(store, type, null);
  }


  @Override
  public ImmutableList<SeriesData<E>> getDataForXRange(@NotNull Range xRange) {
    return mStore.getSeriesData(mType, xRange, mTarget);
  }
}
