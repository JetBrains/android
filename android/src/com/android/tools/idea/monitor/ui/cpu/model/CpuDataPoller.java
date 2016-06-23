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
package com.android.tools.idea.monitor.ui.cpu.model;

import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.monitor.datastore.DataAdapter;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfilerServiceGrpc;
import gnu.trove.TLongArrayList;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Collects CPU data from the device.
 */
public class CpuDataPoller extends Poller {

  /**
   * Delay between CPU data requests (in nanoseconds).
   */
  private static final long POLLING_DELAY_NS = TimeUnit.SECONDS.toNanos(1);

  private long myDataRequestStartTimestamp;

  private final int myPid;

  private CpuProfilerServiceGrpc.CpuProfilerServiceBlockingStub myCpuService;

  private final TLongArrayList myTimestampArray = new TLongArrayList();

  // TODO: Change them to double after refactoring LineChart
  private final TLongArrayList myProcessCpuUsage = new TLongArrayList();
  private final TLongArrayList myOtherProcessesCpuUsage = new TLongArrayList();

  // TODO: Integer should be enough. Review it later.
  private final TLongArrayList myNumberOfThreadsList = new TLongArrayList();

  private final Map<ThreadData, Cpu.ThreadActivity.State> myThreadsStateData = new HashMap<>();

  public CpuDataPoller(@NotNull SeriesDataStore dataStore,
                       int pid) {
    super(dataStore, POLLING_DELAY_NS);
    myPid = pid;
    registerAdapters(dataStore);
  }

  private static CpuUsageData getCpuUsageData(Cpu.CpuProfilerData data, Cpu.CpuProfilerData lastData) {
    long elapsed = (data.getCpuUsage().getElapsedTimeInMillisec() - lastData.getCpuUsage().getElapsedTimeInMillisec());
    // TODO: consider using raw data instead of percentage to improve efficiency.
    double app = 100.0 * (data.getCpuUsage().getAppCpuTimeInMillisec() - lastData.getCpuUsage().getAppCpuTimeInMillisec()) / elapsed;
    double system =
      100.0 * (data.getCpuUsage().getSystemCpuTimeInMillisec() - lastData.getCpuUsage().getSystemCpuTimeInMillisec()) / elapsed;

    return new CpuUsageData(app, system);
  }

  private void registerAdapters(@NotNull SeriesDataStore dataStore) {
    dataStore.registerAdapter(SeriesDataType.CPU_MY_PROCESS, new CpuDataLongAdapter(myProcessCpuUsage));
    dataStore.registerAdapter(SeriesDataType.CPU_OTHER_PROCESSES, new CpuDataLongAdapter(myOtherProcessesCpuUsage));
    dataStore.registerAdapter(SeriesDataType.CPU_THREADS, new CpuDataLongAdapter(myNumberOfThreadsList));
  }

  @Override
  protected void asyncInit() {
    myCpuService = myService.getCpuService();
    CpuProfiler.CpuStartRequest.Builder requestBuilder = CpuProfiler.CpuStartRequest.newBuilder().setAppId(myPid);
    myCpuService.startMonitoringApp(requestBuilder.build());
    myDataRequestStartTimestamp = Long.MIN_VALUE;
  }

  @Override
  protected void asyncShutdown() {
    try {
      myCpuService.stopMonitoringApp(CpuProfiler.CpuStopRequest.newBuilder().setAppId(myPid).build());
    }
    catch (StatusRuntimeException ignored) {
      // UNAVAILABLE
    }
  }

  @Override
  protected void poll() {
    CpuProfiler.CpuDataRequest.Builder dataRequestBuilder = CpuProfiler.CpuDataRequest.newBuilder();
    dataRequestBuilder.setAppId(myPid);
    dataRequestBuilder.setStartTimestamp(myDataRequestStartTimestamp);
    dataRequestBuilder.setEndTimestamp(Long.MAX_VALUE);
    CpuProfiler.CpuDataResponse response;
    try {
      response = myCpuService.getData(dataRequestBuilder.build());
    }
    catch (StatusRuntimeException e) {
      cancel(true);
      return;
    }

    List<Cpu.CpuProfilerData> cpuDataList = response.getDataList();
    if (cpuDataList.isEmpty()) {
      return;
    }

    // Create a cpuData with default values as the first value.
    Cpu.CpuProfilerData lastCpuData = Cpu.CpuProfilerData.newBuilder().build();

    for (Cpu.CpuProfilerData data : cpuDataList) {
      if (data.getDataCase() == Cpu.CpuProfilerData.DataCase.DATA_NOT_SET) {
        // No data to be handled.
        continue;
      }

      // Cpu Usage
      if (data.getDataCase() == Cpu.CpuProfilerData.DataCase.CPU_USAGE) {
        CpuUsageData usageData = getCpuUsageData(data, lastCpuData);
        myProcessCpuUsage.add((long)usageData.getAppUsage());
        myOtherProcessesCpuUsage.add((long)usageData.getOtherProcessesUsage());
        lastCpuData = data;
        // Update start timestamp inside the loop in case the thread is interrupted before its end.
        myDataRequestStartTimestamp = lastCpuData.getBasicInfo().getEndTimestamp();
      }
      // Threads
      else if (data.getDataCase() == Cpu.CpuProfilerData.DataCase.THREAD_ACTIVITIES) {
        // Repeat last Cpu Usage data to keep the size of the data arrays equals to the size of the timestamp array.
        int lastIndex = myTimestampArray.size() - 1;
        myProcessCpuUsage.add(lastIndex < 0 ? 0 : myProcessCpuUsage.get(lastIndex));
        myOtherProcessesCpuUsage.add(lastIndex < 0 ? 0 : myOtherProcessesCpuUsage.get(lastIndex));

        Cpu.ThreadActivities threadActivities = data.getThreadActivities();
        if (threadActivities == null) {
          continue; // nothing to do
        }

        for (Cpu.ThreadActivity threadActivity : threadActivities.getActivitiesList()) {
          ThreadData threadData = new ThreadData(threadActivity.getTid(), threadActivity.getNewState());
          if (!myThreadsStateData.containsKey(threadData)) {
            myThreadsStateData.put(threadData, threadData.getCurrentState());
          }
          else if (threadData.getCurrentState() == Cpu.ThreadActivity.State.DEAD) {
            myThreadsStateData.remove(threadData);
          }
          // TODO: Add support to threads state chart.
        }
      }
      long dataTimestamp = TimeUnit.NANOSECONDS.toMillis(data.getBasicInfo().getEndTimestamp() - myDeviceTimeOffset);
      myTimestampArray.add(dataTimestamp);
      myNumberOfThreadsList.add(myThreadsStateData.size());
    }
  }

  private final class CpuDataLongAdapter implements DataAdapter<Long> {

    @NotNull
    private TLongArrayList myCpuData;

    CpuDataLongAdapter(@NotNull TLongArrayList cpuData) {
      myCpuData = cpuData;
    }

    @Override
    public int getClosestTimeIndex(long time) {
      int index = myTimestampArray.binarySearch(time);
      if (index < 0) {
        // No exact match, returns position to the left of the insertion point.
        // NOTE: binarySearch returns -(insertion point + 1) if not found.
        index = -index - 2;
      }

      return Math.max(0, Math.min(myTimestampArray.size() - 1, index));
    }

    @Override
    public SeriesData<Long> get(int index) {
      SeriesData<Long> data = new SeriesData<>();
      data.x = myTimestampArray.get(index);
      data.value = myCpuData.get(index);
      return data;
    }

    @Override
    public void stop() {
      // Do nothing.
    }

    @Override
    public void reset(long startTime) {
      // TODO: Define reset mechanism.
    }
  }

  private static final class CpuUsageData {

    /**
     * App usage (in %) at a given point.
     */
    private double myAppUsage;

    /**
     * System usage (in %), including applications, at a given point.
     */
    private double mySystemUsage;

    CpuUsageData(double appUsage, double systemUsage) {
      myAppUsage = appUsage;
      mySystemUsage = systemUsage;
    }

    double getAppUsage() {
      return myAppUsage;
    }

    double getOtherProcessesUsage() {
      return mySystemUsage - myAppUsage;
    }
  }

  private static final class ThreadData {

    /**
     * Thread id.
     */
    private int myId;

    /**
     * Current thread state.
     */
    private Cpu.ThreadActivity.State myCurrentState;

    // TODO: add name field
    ThreadData(int id, Cpu.ThreadActivity.State state) {
      myId = id;
      myCurrentState = state;
    }

    @Override
    public boolean equals(Object obj) {
      return obj != null && obj instanceof ThreadData && ((ThreadData)obj).myId == myId;
    }

    @Override
    public int hashCode() {
      return myId;
    }

    Cpu.ThreadActivity.State getCurrentState() {
      return myCurrentState;
    }
  }
}
