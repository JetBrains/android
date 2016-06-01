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
package com.android.tools.idea.monitor.ui.visual;

import com.android.tools.adtui.Range;
import com.android.tools.idea.monitor.datastore.SeriesDataList;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import gnu.trove.TLongArrayList;

import java.util.*;

public final class VisualTestSeriesDataStore implements SeriesDataStore {

  private TLongArrayList mTimingData = new TLongArrayList();
  private Map<SeriesDataType, List<?>> mDataSeriesMap = new HashMap<>();

  private Thread mDataThread;
  private Random mRandom = new Random();
  private long mStartTime;

  public VisualTestSeriesDataStore() {
    startGeneratingData();
  }

  @Override
  public void reset() {
    if(mDataThread != null) {
      mDataThread.interrupt();
    }
  }

  @Override
  public long getTimeAtIndex(int index) {
    return mTimingData.get(index);
  }

  @Override
  public int getClosestTimeIndex(long timeValue) {
    int index = mTimingData.binarySearch(timeValue);
    if (index < 0) {
      // No exact match, returns position to the left of the insertion point.
      // NOTE: binarySearch returns -(insertion point + 1) if not found.
      index = -index - 1;
    }
    return Math.max(0, Math.min(mTimingData.size() - 1, index));
  }

  @Override
  public <T> T getValueAtIndex(SeriesDataType type, int index) {
    return (T)mDataSeriesMap.get(type).get(index);
  }

  @Override
  public <T> SeriesDataList<T> getSeriesData(SeriesDataType type, Range range) {
    return new SeriesDataList<>(range, this, type);
  }

  private void generateData() {
    long now = System.currentTimeMillis();
    mTimingData.add(now - mStartTime);

    for (SeriesDataType type : mDataSeriesMap.keySet()) {
      // TODO: come up with cleaner API, as this casting is wrong, i.e mDataSeriesMap returns a generic list
      List<Long> data = (List<Long>)mDataSeriesMap.get(type);

      switch (type) {
        case CPU_MY_PROCESS:
          data.add(randLong(0, 60));
          break;
        case CPU_OTHER_PROCESSES:
          data.add(randLong(0, 20));
          break;
        case CPU_THREADS:
        case NETWORK_CONNECTIONS:
          data.add(randLong(0, 10));
          break;
        default:
          long x = (data.isEmpty() ? 0 : data.get(data.size() - 1)) + randLong(-20, 100);
          data.add(Math.max(0, x));
          break;
      }
    }
  }

  private void startGeneratingData() {
    for (SeriesDataType type : SeriesDataType.values()) {
      mDataSeriesMap.put(type, new ArrayList<>());
    }
    mStartTime = System.currentTimeMillis();

    mDataThread = new Thread() {
      @Override
      public void run() {
        try {
          while (true) {
            generateData();
            Thread.sleep(100);
          }
        }
        catch (InterruptedException e) {
        }
      }
    };
    mDataThread.start();
  }

  private long randLong(int l, int r) {
    return mRandom.nextInt(r - l + 1) + l;
  }
}
