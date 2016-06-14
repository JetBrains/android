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

import gnu.trove.TLongArrayList;

import java.util.Random;

public class LongTestDataGenerator implements TestDataGenerator<Long> {

  private Random mRandom = new Random();
  private TLongArrayList data = new TLongArrayList();
  private int mMin;
  private int mMax;
  private boolean mUseLast;


  public LongTestDataGenerator(int min, int max, boolean useLast) {
    mMin = min;
    mMax = max;
    mUseLast = useLast;
  }

  @Override
  public Long get(int index) {
    return data.get(index);
  }

  @Override
  public void generateData() {
    if (mUseLast) {
      long x = (data.isEmpty() ? 0 : data.get(data.size() - 1)) + randLong(-20, 100);
      data.add(Math.max(0, x));
    }
    else {
      data.add(randLong(mMin, mMax));
    }
  }


  private long randLong(int l, int r) {
    return mRandom.nextInt(r - l + 1) + l;
  }
}
