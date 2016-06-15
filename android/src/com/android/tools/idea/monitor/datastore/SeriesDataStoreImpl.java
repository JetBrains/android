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
package com.android.tools.idea.monitor.datastore;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.monitor.profilerclient.DeviceProfilerService;
import com.android.tools.profiler.proto.ProfilerService;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class SeriesDataStoreImpl implements SeriesDataStore {

  private static final int GENERATE_DATA_THREAD_DELAY = 100;

  private static final DataGenerator CPU_MY_PROCESS_GENERATOR = new DataGenerator(0, 60, 10);

  private static final DataGenerator CPU_OTHER_PROCESSES_GENERATOR = new DataGenerator(0, 30, 10);

  private static final DataGenerator CPU_NUM_THREADS_GENERATOR = new DataGenerator(0, 10, 1);

  private static final DataGenerator CPU_NUM_CONNECTIONS_GENERATOR = new DataGenerator(0, 20, 2);

  private static final DataGenerator DEFAULT_DATA_GENERATOR = new DataGenerator(0, 100, 20);

  private Map<SeriesDataType, DataAdapter<?>> myDataSeriesMap = new HashMap<>();

  private long myDeviceStartTime;

  private long myStartTime;

  @NotNull
  private final DeviceProfilerService myDeviceProfilerService;

  public SeriesDataStoreImpl(@NotNull DeviceProfilerService deviceProfilerService) {
    myDeviceProfilerService = deviceProfilerService;
    startGeneratingData();
  }

  @Override
  public void reset() {
    synchronizeStartTime();
    for (DataAdapter<?> adapter : myDataSeriesMap.values()) {
      adapter.reset();
      adapter.setStartTime(myStartTime);
    }
  }

  @Override
  public long getLatestTime() {
    return System.currentTimeMillis() - myStartTime;
  }

  @Override
  public int getClosestTimeIndex(SeriesDataType type, long timeValue) {
    DataAdapter<?> adapter = myDataSeriesMap.get(type);
    assert adapter != null;
    return adapter.getClosestTimeIndex(timeValue);
  }

  @Override
  public <T> SeriesData<T> getDataAt(SeriesDataType type, int index) {
    DataAdapter<?> adapter = myDataSeriesMap.get(type);
    assert adapter != null;
    return (SeriesData<T>)adapter.get(index);
  }

  @Override
  public <T> SeriesDataList<T> getSeriesData(SeriesDataType type, Range range) {
    return new SeriesDataList<>(range, this, type);
  }

  //TODO change the register API to
  // registerAdapter(SeriesDataType<T> DataAdapter<T>) to ensure type safety.
  public void registerAdapter(SeriesDataType type, DataAdapter adapter) {
    myDataSeriesMap.put(type, adapter);
    adapter.setStartTime(myStartTime);
  }

  private void startGeneratingData() {
    synchronizeStartTime();
    for (SeriesDataType type : SeriesDataType.values()) {
      switch (type) {
        case CPU_MY_PROCESS:
          registerAdapter(type, CPU_MY_PROCESS_GENERATOR);
          break;
        case CPU_OTHER_PROCESSES:
          registerAdapter(type, CPU_OTHER_PROCESSES_GENERATOR);
          break;
        case CPU_THREADS:
          registerAdapter(type, CPU_NUM_THREADS_GENERATOR);
          break;
        case NETWORK_CONNECTIONS:
          registerAdapter(type, CPU_NUM_CONNECTIONS_GENERATOR);
          break;
        default:
          registerAdapter(type, DEFAULT_DATA_GENERATOR);
          break;
      }
    }
  }

  /**
   * Initialize and synchronize the system start time with the device time. That way we can use (current time - start time) as the offset
   * when converting studio time to device time when making data requests.
   */
  private void synchronizeStartTime() {
    ProfilerService.TimesResponse response = myDeviceProfilerService.getDeviceService().getTimes(
      ProfilerService.TimesRequest.getDefaultInstance());
    myStartTime = System.currentTimeMillis();
    myDeviceStartTime = TimeUnit.NANOSECONDS.toMillis(response.getTimestamp());
  }

  /**
   * Simulated data generator.
   */
  private static class DataGenerator implements DataAdapter<Long> {

    private long myNext;

    private int myMaxVariance;

    private int myMin;

    private int myMax;

    private TLongArrayList myTime = new TLongArrayList();

    private TLongArrayList myData = new TLongArrayList();

    private long myStartTime;

    private Thread myDataThread;

    /**
     * Generates simulated data from {@code min} to {@code max} (inclusive).
     * Values generated don't differ more than {@code maxVariance} from the previous generated value.
     */
    DataGenerator(int min, int max, int maxVariance) {
      myMin = min;
      myMax = max;
      myMaxVariance = maxVariance;
      myNext = randomInInterval(min, max);
      myDataThread = new Thread() {
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
      reset();
    }

    @Override
    public void reset() {
      if (myDataThread != null) {
        myDataThread.interrupt();
        myTime.clear();
        myData.clear();
        myDataThread.start();
      }
    }

    @Override
    public int getClosestTimeIndex(long time) {
      //Test data time is stored in current time millis and time passed in is delta time.
      //To correct for this we add the start time to the delta time to find our time value.
      int index = myTime.binarySearch(time + myStartTime);
      if (index < 0) {
        // No exact match, returns position to the left of the insertion point.
        // NOTE: binarySearch returns -(insertion point + 1) if not found.
        index = -index - 2;
      }

      return Math.max(0, Math.min(myTime.size() - 1, index));
    }

    @Override
    public SeriesData<Long> get(int index) {
      SeriesData<Long> data = new SeriesData<>();
      data.x = myTime.get(index) - myStartTime;
      data.value = myData.get(index);
      return data;
    }

    @Override
    public void setStartTime(long startTime) {
      myStartTime = startTime;
    }

    private void generateData() {
      // Next value should not differ from current by more than max variance
      myTime.add(System.currentTimeMillis());
      long next = myNext + randomInInterval(-myMaxVariance, myMaxVariance);
      myData.add(Math.min(myMax, Math.max(myMin, next)));
    }

    /**
     * Returns a random number in an interval.
     *
     * @param min lower bound of the interval (inclusive)
     * @param max upper bound of the interval (inclusive)
     */
    private static long randomInInterval(long min, long max) {
      return (long)Math.floor(Math.random() * (max - min + 1) + min);
    }
  }
}
