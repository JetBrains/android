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

import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.SeriesData;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MemoryTestDurationDataGenerator extends TestDataGenerator<DurationData> {

  private ArrayList<DurationData> mData = new ArrayList();
  private long mStartVariance;
  private long mDurationVariance;

  public MemoryTestDurationDataGenerator(long startVariance, long durationVariance) {
    mStartVariance = startVariance;
    mDurationVariance = durationVariance;
  }

  @Override
  public SeriesData<DurationData> get(int index) {
    return new SeriesData<>(mTime.get(index), mData.get(index));
  }

  @Override
  public void generateData() {
    long currentTime = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    long previousEnd = mTime.size() == 0 ? currentTime : mTime.get(mTime.size() - 1) + mData.get(mData.size() - 1).getDuration();

    // Adds a new DurationData sample if the current time has passed the end time of the previous sample.
    if (previousEnd <= currentTime) {
      long newStart = previousEnd + (long)(Math.random() * mStartVariance);
      long newDuration = (long)(Math.random() * mDurationVariance);

      mTime.add(newStart);
      mData.add(new DurationData(newDuration));
    }

  }
}
