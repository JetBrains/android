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
import com.intellij.util.containers.ImmutableList;

/**
 * Immutable list that all UI components get their data from. SeriesDataList are the
 * interface the UI uses to access data from the SeriesDataStore.
 * @param <E> The type of data that is suppose to be accessed from the SeriesDataStore.
 */
public class SeriesDataList<E> extends ImmutableList<SeriesData<E>> {

  // Get closest time index returns the closest time index less than or equal to the value we pass in.
  // As such to avoid any rounding error, we pad the amount of data we request by one second.
  // TODO Change getClosestTimeIndex to have the ability to return closest - 1, and + 1 without stepping beyond the bounds
  // of the underlying data.
  private static final int RANGE_PADDING_MS = 1000;

  private int mStartIndex;
  private int mEndIndex;
  private SeriesDataStore mDataStore;
  private SeriesDataType mDataType;

  public SeriesDataList(Range range, SeriesDataStore dataStore, SeriesDataType dataType) {
    mDataStore = dataStore;
    mDataType = dataType;
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
    return mDataStore.getDataAt(mDataType, mStartIndex + index);
  }

  public int getIndexForTime(long time) {
    int index = mDataStore.getClosestTimeIndex(mDataType, time) - mStartIndex;
    return Math.max(0,Math.min(size()-1,index));
  }

  /**
   * Initializes the iterator to be based on a new range, this function copies the range passed in to
   * an internal range.
   */
  private void initialize(Range range) {
    mStartIndex = mDataStore.getClosestTimeIndex(mDataType, (long)range.getMin() - RANGE_PADDING_MS);
    mEndIndex = mDataStore.getClosestTimeIndex(mDataType, (long)range.getMax() + RANGE_PADDING_MS);
    //TODO When we cache data to disk here we can tell the datastore to preload it for this range.
  }

}
