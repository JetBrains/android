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
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.monitor.datastore.SeriesDataList;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import gnu.trove.TLongArrayList;

import javax.swing.*;
import java.util.*;

public final class VisualTestSeriesDataStore implements SeriesDataStore {

  // The following Table data structure just provides an example implementation on how we can achieve having different timestamps for
  // different series. The profiler datastore implementation should not rely on using generic List<?> to store numerical arrays as that
  // is memory-inefficient.
  private Table<SeriesDataType, TLongArrayList, List<?>> mDataSeriesMap = HashBasedTable.create();
  private Thread mDataThread;
  private Random mRandom = new Random();
  private long mStartTime;
  private long mCurrentTime;

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
  public long getLatestTime() {
    return mCurrentTime;
  }

  @Override
  public long getTimeAtIndex(SeriesDataType type, int index) {
    Map<TLongArrayList, List<?>> dataMap = mDataSeriesMap.row(type);
    assert dataMap.size() == 1;

    TLongArrayList timeData = dataMap.keySet().iterator().next();
    return timeData.get(index);
  }

  @Override
  public int getClosestTimeIndex(SeriesDataType type, long timeValue) {
    Map<TLongArrayList, List<?>> dataMap = mDataSeriesMap.row(type);
    assert dataMap.size() == 1;

    TLongArrayList timeData = dataMap.keySet().iterator().next();
    int index = timeData.binarySearch(timeValue);
    if (index < 0) {
      // No exact match, returns position to the left of the insertion point.
      // NOTE: binarySearch returns -(insertion point + 1) if not found.
      index = -index - 1;
    }

    return Math.max(0, Math.min(timeData.size() - 1, index));
  }

  @Override
  public <T> T getValueAtIndex(SeriesDataType type, int index) {
    Map<TLongArrayList, List<?>> dataMap = mDataSeriesMap.row(type);
    assert dataMap.size() == 1;

    List<?> seriesData = dataMap.values().iterator().next();
    return (T)seriesData.get(index);
  }

  @Override
  public <T> SeriesDataList<T> getSeriesData(SeriesDataType type, Range range) {
    return new SeriesDataList<>(range, this, type);
  }

  private void generateData() {
    long now = System.currentTimeMillis();
    mCurrentTime = now - mStartTime;

    Map<SeriesDataType, Map<TLongArrayList, List<?>>> rowMap = mDataSeriesMap.rowMap();
    for (SeriesDataType dataType : rowMap.keySet()) {
      Map<TLongArrayList, List<?>> series = rowMap.get(dataType);
      for (Map.Entry<TLongArrayList, List<?>> dataList : series.entrySet()) {
        // TODO: come up with cleaner API, as this casting is wrong, i.e mDataSeriesMap returns a generic list
        dataList.getKey().add(mCurrentTime);
        List<Long> castedData = (List<Long>)dataList.getValue();
        Runtime rt = Runtime.getRuntime();
        switch (dataType) {
          case CPU_MY_PROCESS:
            castedData.add(randLong(0, 60));
            break;
          case CPU_OTHER_PROCESSES:
            castedData.add(randLong(0, 20));
            break;
          case CPU_THREADS:
          case NETWORK_CONNECTIONS:
            castedData.add(randLong(0, 10));
            break;
          case MEMORY_TOTAL:
          case MEMORY_JAVA:
            long usedMem = rt.totalMemory() - rt.freeMemory();
            castedData.add(usedMem);
            break;
          case MEMORY_OTHERS:
            castedData.add(rt.freeMemory());
            break;
          default:
            long x = (castedData.isEmpty() ? 0 : castedData.get(castedData.size() - 1)) + randLong(-20, 100);
            castedData.add(Math.max(0, x));
            break;
        }
      }
    }
  }

  private void startGeneratingData() {
    for (SeriesDataType type : SeriesDataType.values()) {
      mDataSeriesMap.put(type, new TLongArrayList(), new ArrayList<>());
    }
    mStartTime = System.currentTimeMillis();

    mDataThread = new Thread() {
      @Override
      public void run() {
        try {
          while (true) {
            // TODO: come up with better thread issues handling
            SwingUtilities.invokeLater(() -> generateData());

            // TODO support configurable timing
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
