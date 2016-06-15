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

public class MemoryTestDataGenerator implements TestDataGenerator<Long> {

  private TLongArrayList data = new TLongArrayList();
  private boolean mUseFreeMemory;
  private Runtime mRuntime;

  public MemoryTestDataGenerator(boolean useFreeMemory) {
    mUseFreeMemory = useFreeMemory;
    mRuntime = Runtime.getRuntime();
  }

  @Override
  public Long get(int index) {
    return data.get(index);
  }

  @Override
  public void generateData() {
    if (mUseFreeMemory) {
      data.add(mRuntime.freeMemory());
    }
    else {
      long usedMem = mRuntime.totalMemory() - mRuntime.freeMemory();
      data.add(usedMem);
    }
  }
}