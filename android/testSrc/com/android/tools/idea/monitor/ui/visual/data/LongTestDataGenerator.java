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
package com.android.tools.idea.monitor.ui.visual.data;

import com.android.tools.adtui.model.SeriesData;
import gnu.trove.TLongArrayList;

import java.util.Random;

public class LongTestDataGenerator extends TestDataGenerator<Long> {

  private Random mRandom = new Random();
  private TLongArrayList mData = new TLongArrayList();
  private int mMin;
  private int mMax;
  private boolean mUseLast;

  /**
   * Generates simulated data from {@code min} to {@code max} (inclusive).
   */
  public LongTestDataGenerator(int min, int max, boolean useLast) {
    mMin = min;
    mMax = max;
    mUseLast = useLast;
  }

  @Override
  public SeriesData<Long> get(int index) {
    SeriesData<Long> data = new SeriesData<>();
    data.x = mTime.get(index) - mStartTime;
    data.value = mData.get(index);
    return data;
  }

  @Override
  public void generateData() {
    mTime.add(System.currentTimeMillis());
    if (mUseLast) {
      long x = (mData.isEmpty() ? 0 : mData.get(mData.size() - 1)) + randLong(-20, 100);
      mData.add(Math.max(0, x));
    } else {
      mData.add(randLong(mMin, mMax));
    }
  }


  private long randLong(int l, int r) {
    return mRandom.nextInt(r - l + 1) + l;
  }
}
