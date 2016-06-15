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
import com.android.tools.idea.monitor.datastore.DataAdapter;
import gnu.trove.TLongArrayList;

import javax.swing.*;

/**
 * Simulated data generator.
 */
public abstract class TestDataGenerator<T> implements DataAdapter<T> {

  private static final int GENERATE_DATA_THREAD_DELAY = 100;

  protected TLongArrayList mTime = new TLongArrayList();

  protected long mStartTime;

  private Thread mDataThread;

  TestDataGenerator() {
    reset();
  }

  @Override
  public void reset() {
    if (mDataThread != null) {
      mDataThread.interrupt();
      mDataThread = null;
    }
    mDataThread = new Thread() {
      @Override
      public void run() {
        try {
          while (true) {
            // TODO: come up with a better way of handling thread issues
            SwingUtilities.invokeLater(() -> generateData());

            Thread.sleep(GENERATE_DATA_THREAD_DELAY);
          }
        }
        catch (InterruptedException ignored) {
        }
      }
    };
    mDataThread.start();
  }

  @Override
  public int getClosestTimeIndex(long time) {
    int index = mTime.binarySearch(time + mStartTime);
    if (index < 0) {
      // No exact match, returns position to the left of the insertion point.
      // NOTE: binarySearch returns -(insertion point + 1) if not found.
      index = -index - 2;
    }

    return Math.max(0, Math.min(mTime.size() - 1, index));
  }

  @Override
  public void setStartTime(long startTime) {
    mStartTime = startTime;
  }

  /**
   * Function for test to override, this function gets called on its own thread and is used to simulate
   * new data coming from the device.
   */
  //TODO refactor to move time into DataGenerators.
  abstract void generateData();
}
