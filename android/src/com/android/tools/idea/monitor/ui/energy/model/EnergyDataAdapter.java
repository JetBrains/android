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
package com.android.tools.idea.monitor.ui.energy.model;


import com.android.tools.adtui.model.SeriesData;
import com.android.tools.datastore.DataAdapter;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * A long (as in the primitive type 'long') data adapter that stores two streams of data: deltas and totals.
 * This adapter gives the option to dynamically set which stream to render, and also ways to add new data
 * to the adapter with either new deltas or totals.
 */
public class EnergyDataAdapter implements DataAdapter<Long> {
  private boolean myRenderInstantaneousData = true;

  @NotNull
  private final TLongArrayList myTimestampData = new TLongArrayList();

  @NotNull
  private final TLongArrayList myData = new TLongArrayList();

  public void setReturnInstantaneousData(boolean useInstantaneous) {
    myRenderInstantaneousData = useInstantaneous;
  }

  public void updateByDelta(long timestamp, long delta) {
    update(timestamp, myData.get(myData.size() - 1) + delta);
  }

  public void update(long timestamp, long value) {
    myTimestampData.add(timestamp);
    myData.add(value);
  }

  @Override
  public int getClosestTimeIndex(long timeUs, boolean leftClosest) {
    return DataAdapter.getClosestIndex(myTimestampData, timeUs, leftClosest);
  }

  @Override
  public SeriesData<Long> get(int index) {
    if (myRenderInstantaneousData) {
      return new SeriesData<>(myTimestampData.get(index), getInternal(index) - getInternal(index - 1));
    }
    return new SeriesData<>(myTimestampData.get(index), getInternal(index));
  }

  public long getInternal(int index) {
    if (index <= 0) {
      return myData.get(0);
    }
    if (index >= myData.size()) {
      return myData.get(myData.size());
    }
    return myData.get(index);
  }

  public long getLatest() {
    return getInternal(myData.size() - 1);
  }

  public long getLatestDelta() {
    return getInternal(myData.size() - 1) - getInternal(myData.size() - 2);
  }

  @Override
  public void reset() {
    myTimestampData.clear();
    myData.clear();
  }

  @Override
  public void stop() {
    // TODO: implement
  }
}