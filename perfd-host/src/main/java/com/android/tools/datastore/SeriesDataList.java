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
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable list that all UI components get their data from. SeriesDataList are the
 * interface the UI uses to access data from the SeriesDataStore.
 *
 * @param <E> The type of data that is suppose to be accessed from the SeriesDataStore.
 */
public class SeriesDataList<E> extends ImmutableList<SeriesData<E>> {

  private int mStartIndex;
  private int mEndIndex;

  @NotNull
  private SeriesDataStore mDataStore;

  @NotNull
  private SeriesDataType mDataType;

  /**
   * Target object to be used by the data store to get the correspondent adapter.
   */
  @Nullable
  private Object mTarget;

  public SeriesDataList(@NotNull Range range, @NotNull SeriesDataStore dataStore, @NotNull SeriesDataType dataType) {
    this(range, dataStore, dataType, null);
  }

  public SeriesDataList(@NotNull Range range,
                        @NotNull SeriesDataStore dataStore,
                        @NotNull SeriesDataType dataType,
                        @Nullable Object target) {
    mDataStore = dataStore;
    mDataType = dataType;
    mTarget = target;
    initialize(range);
  }

  /**
   * Returns the length between the first element and the last element in the iterator.
   */
  @Override
  public int size() {
    return mEndIndex - mStartIndex;
  }

  @Override
  public SeriesData<E> get(int index) {
    if (index < 0 || index >= size()) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
    }
    return mDataStore.getDataAt(mDataType, mStartIndex + index, mTarget);
  }

  /**
   * Initializes the iterator to be based on a new range, this function copies the range passed in to
   * an internal range.
   */
  private void initialize(Range range) {
    mStartIndex = mDataStore.getClosestTimeIndex(mDataType, (long)range.getMin(), true, mTarget);
    mEndIndex = mDataStore.getClosestTimeIndex(mDataType, (long)range.getMax(), false, mTarget);
    //TODO When we cache data to disk here we can tell the datastore to preload it for this range.
  }

}
