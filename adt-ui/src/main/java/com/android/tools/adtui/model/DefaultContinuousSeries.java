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
package com.android.tools.adtui.model;

import com.android.annotations.concurrency.Immutable;
import com.android.tools.adtui.Range;
import com.intellij.util.containers.ImmutableList;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;


public class DefaultContinuousSeries implements ContinuousSeries {
  @NotNull
  private final TLongArrayList mX = new TLongArrayList();

  @NotNull
  private final TLongArrayList mY = new TLongArrayList();

  @NotNull
  private ImmutableList<SeriesData<Long>> getDataSubList(final int fromIndex,final int toIndex) {
    return new ImmutableList<SeriesData<Long>>() {
      @Override
      public int size() {
        return toIndex - fromIndex;
      }

      @Override
      public SeriesData<Long> get(int index) {
        assert index < size();
        SeriesData<Long> data = new SeriesData<>();
        data.time = getX(index + fromIndex);
        data.value = getY(index + fromIndex);
        return data;
      }
    };
  }

  @Override
  public ImmutableList<SeriesData<Long>> getDataForXRange(Range xRange) {
    int fromIndex = getNearestXIndex((long)xRange.getMin());
    int toIndex = getNearestXIndex((long)xRange.getMax()) + 1;
    return getDataSubList(fromIndex, toIndex);
  }

  public ImmutableList<SeriesData<Long>> getAllData() {
    return getDataSubList(0, size());
  }

  @Override
  public SeriesData<Long> getDataAtXValue(long x) {
    int index = getNearestXIndex(x);
    SeriesData<Long> data = new SeriesData<>();
    data.time = getX(index);
    data.value = getY(index);
    return data;
  }

  public void add(long x, long y) {
    mX.add(x);
    mY.add(y);
  }

  public int size() {
    return mX.size();
  }

  public long getX(int index) {
    return mX.get(index);
  }

  public long getY(int index) {
    return mY.get(index);
  }

  public int getNearestXIndex(long x) {
    int index = mX.binarySearch(x);

    if (index < 0) {
      // No exact match, returns position to the left of the insertion point.
      // NOTE: binarySearch returns -(insertion point + 1) if not found.
      index = -index - 1;
    }

    return Math.max(0, Math.min(index, size() - 1));
  }
}
