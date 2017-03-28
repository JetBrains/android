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
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of {@link DataAdapter} interface.
 * This class uses gnu trove lists for the performance reason.
 */
public class LongDataAdapter implements DataAdapter<Long> {
  @NotNull
  private final TLongArrayList myTimestampData;

  @NotNull
  private final TLongArrayList myValues;

  public LongDataAdapter(@NotNull TLongArrayList timestampData, @NotNull TLongArrayList trafficData) {
    myTimestampData = timestampData;
    myValues = trafficData;
  }

  @Override
  public int getClosestTimeIndex(long timeUs, boolean leftClosest) {
    return DataAdapter.getClosestIndex(myTimestampData, timeUs, leftClosest);
  }

  @Override
  public SeriesData<Long> get(int index) {
    return new SeriesData<>(myTimestampData.get(index), myValues.get(index));
  }

  @Override
  public void reset() {
    myTimestampData.clear();
    myValues.clear();
  }

  @Override
  public void stop() {
    // TODO: implement
  }
}
