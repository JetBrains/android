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
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.profiler.proto.ProfilerService;
import com.intellij.util.EventDispatcher;
import gnu.trove.TLongArrayList;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public final class SeriesDataStoreImpl implements SeriesDataStore {

  private static final int GENERATE_DATA_THREAD_DELAY = 100;

  private static final DataGenerator NUM_CONNECTIONS_GENERATOR = new DataGenerator(0, 20, 2);

  private static final DataGenerator DEFAULT_DATA_GENERATOR = new DataGenerator(0, 100, 20);

  private Map<SeriesDataType, DataAdapter<?>> myDataSeriesMap = new HashMap<>();

  private long myDeviceStartTimeNs;

  private long myStartTime;

  @NotNull
  private final DeviceProfilerService myDeviceProfilerService;

  @NotNull
  private final EventDispatcher<ProfilerEventListener> myDispatcher;

  public SeriesDataStoreImpl(@NotNull DeviceProfilerService deviceProfilerService,
                             @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    myDeviceProfilerService = deviceProfilerService;
    myDispatcher = dispatcher;
    startGeneratingData();
  }

  @Override
  public DeviceProfilerService getDeviceProfilerService() {
    return myDeviceProfilerService;
  }

  @Override
  @NotNull
  public EventDispatcher<ProfilerEventListener> getEventDispatcher() {
    return myDispatcher;
  }

  @Override
  public void stop() {
    for (DataAdapter<?> adapter : myDataSeriesMap.values()) {
      adapter.stop();
    }
  }

  @Override
  public void reset() {
    synchronizeStartTime();
    for (DataAdapter<?> adapter : myDataSeriesMap.values()) {
      adapter.reset(myStartTime);
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
  @Override
  public void registerAdapter(SeriesDataType type, DataAdapter adapter) {
    myDataSeriesMap.put(type, adapter);
    adapter.reset(myStartTime);
  }

  @Override
  public long getDeviceTimeOffsetNs() {
    return myDeviceStartTimeNs;
  }

  private void startGeneratingData() {
    synchronizeStartTime();
    for (SeriesDataType type : SeriesDataType.values()) {
      switch (type) {
        case CPU_MY_PROCESS:
        case CPU_OTHER_PROCESSES:
        case CPU_THREADS:
        case NETWORK_RECEIVED:
        case NETWORK_SENT:
          // TODO: as we're moving the registerAdapter calls to the correspondent pollers, we can add the covered types here.
          // Once we're done with the move, we can remove this switch/case block.
          break;
        case NETWORK_CONNECTIONS:
          registerAdapter(type, NUM_CONNECTIONS_GENERATOR);
          break;
        case EVENT_ACTIVITY_ACTION:
        case EVENT_FRAGMENT_ACTION:
          // TODO: replace EmptyDataGenerator with actual data from perfd.
          registerAdapter(type, new EmptyDataGenerator<>());
          break;
        case EVENT_SIMPLE_ACTION:
          registerAdapter(type, new EmptyDataGenerator<>());
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
    myStartTime = System.currentTimeMillis();

    try {
      ProfilerService.TimesResponse response = myDeviceProfilerService.getDeviceService().getTimes(
        ProfilerService.TimesRequest.getDefaultInstance());
      myDeviceStartTimeNs = response.getTimestamp();
    }
    catch (StatusRuntimeException e) {
      myDispatcher.getMulticaster().profilerServerDisconnected();
    }
  }

  private static class EmptyDataGenerator<T> implements DataAdapter<T> {

    @Override
    public void reset(long startTime) {

    }

    @Override
    public void stop() {

    }

    @Override
    public int getClosestTimeIndex(long time) {
      return 0;
    }

    @Override
    public SeriesData<T> get(int index) {
      return null;
    }
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

    // TODO: we should change this and the other "generate*" names
    // when we change this class to get actual data.
    private Runnable myDataGenerator;

    private Thread myDataGeneratorThread;

    /**
     * Generates simulated data from {@code min} to {@code max} (inclusive).
     * Values generated don't differ more than {@code maxVariance} from the previous generated value.
     */
    DataGenerator(int min, int max, int maxVariance) {
      myMin = min;
      myMax = max;
      myMaxVariance = maxVariance;
      myNext = randomInInterval(min, max);
      myDataGenerator = () -> {
        try {
          while (true) {
            // TODO: come up with a better way of handling thread issues
            SwingUtilities.invokeLater(() -> generateData());

            Thread.sleep(GENERATE_DATA_THREAD_DELAY);
          }
        }
        catch (InterruptedException ignored) {
        }
      };
      myDataGeneratorThread = new Thread(myDataGenerator);
    }

    @Override
    public void stop() {
      if (myDataGeneratorThread != null) {
        myDataGeneratorThread.interrupt();
        myDataGeneratorThread = null;
      }
    }

    @Override
    public void reset(long startTime) {
      stop();

      myStartTime = startTime;
      myTime.clear();
      myData.clear();
      myDataGeneratorThread = new Thread(myDataGenerator);
      myDataGeneratorThread.start();
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
      return new SeriesData<>(myTime.get(index) - myStartTime, myData.get(index));
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
