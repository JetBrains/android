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
import com.android.tools.datastore.SeriesDataType;
import gnu.trove.TLongArrayList;

import java.util.concurrent.TimeUnit;

public class MemoryTestDataGenerator extends TestDataGenerator<Long> {
  private TLongArrayList mData = new TLongArrayList();
  private SeriesDataType mDataType;
  private Runtime mRuntime;

  public MemoryTestDataGenerator(SeriesDataType memoryDataType) {
    mDataType = memoryDataType;
    mRuntime = Runtime.getRuntime();
  }

  @Override
  public SeriesData<Long> get(int index) {
    return new SeriesData<>(mTime.get(index), mData.get(index));
  }

  @Override
  public void generateData() {
    mTime.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime()));
    switch (mDataType) {
      case MEMORY_TOTAL:
      case MEMORY_JAVA:
        long usedMem = (mRuntime.totalMemory() - mRuntime.freeMemory()) / 1024;
        mData.add(usedMem);
        break;
      case MEMORY_OTHERS:
      case MEMORY_CODE:
      case MEMORY_GRAPHICS:
      case MEMORY_NATIVE:
        mData.add(mRuntime.freeMemory() / 1024);
        break;
      default:
        break;
    }
  }
}
