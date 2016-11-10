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

import com.android.tools.datastore.*;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
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

  private long myDataRequestStartTimestampNs;

  private final int myPid;

  private CpuServiceGrpc.CpuServiceBlockingStub myCpuService;

  private final TLongArrayList myTimestampArray = new TLongArrayList();

  // TODO: Change them to double after refactoring LineChart
  private final TLongArrayList myProcessCpuUsage = new TLongArrayList();
  private final TLongArrayList myOtherProcessesCpuUsage = new TLongArrayList();

  // TODO: Integer should be enough. Review it later.
  private final TLongArrayList myNumberOfThreadsList = new TLongArrayList();

  private final Map<Integer, ThreadStatesDataModel> myThreadsStateData = new HashMap<>();

  private ThreadAddedNotifier myThreadAddedNotifier;

  public CpuDataPoller(@NotNull SeriesDataStore dataStore,
                       int pid) {
    super(dataStore, POLLING_DELAY_NS);
    myPid = pid;
    registerAdapters(dataStore);
  }

  private static CpuUsageData getCpuUsageData(CpuProfiler.CpuProfilerData data, CpuProfiler.CpuProfilerData lastData) {
    long elapsed = (data.getCpuUsage().getElapsedTimeInMillisec() - lastData.getCpuUsage().getElapsedTimeInMillisec());
    // TODO: consider using raw data instead of percentage to improve efficiency.
    double app = 100.0 * (data.getCpuUsage().getAppCpuTimeInMillisec() - lastData.getCpuUsage().getAppCpuTimeInMillisec()) / elapsed;
    double system =
      100.0 * (data.getCpuUsage().getSystemCpuTimeInMillisec() - lastData.getCpuUsage().getSystemCpuTimeInMillisec()) / elapsed;

    // System and app usages are read from them device in slightly different times. Make sure that appUsage <= systemUsage <= 100%
    system = Math.min(system, 100.0);
    app = Math.min(app, system);

    return new CpuUsageData(app, system);
  }

  private void registerAdapters(@NotNull SeriesDataStore dataStore) {
    dataStore.registerAdapter(SeriesDataType.CPU_MY_PROCESS, new LongDataAdapter(myTimestampArray, myProcessCpuUsage));
    dataStore.registerAdapter(SeriesDataType.CPU_OTHER_PROCESSES, new LongDataAdapter(myTimestampArray, myOtherProcessesCpuUsage));
    dataStore.registerAdapter(SeriesDataType.CPU_THREADS, new LongDataAdapter(myTimestampArray, myNumberOfThreadsList));
  }

  @Override
  protected void asyncInit() {
    myCpuService = myService.getCpuService();
    CpuProfiler.CpuStartRequest.Builder requestBuilder = CpuProfiler.CpuStartRequest.newBuilder().setAppId(myPid);
    myCpuService.startMonitoringApp(requestBuilder.build());
    myDataRequestStartTimestampNs = Long.MIN_VALUE;
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
    dataRequestBuilder.setStartTimestamp(myDataRequestStartTimestampNs);
    dataRequestBuilder.setEndTimestamp(Long.MAX_VALUE);
    CpuProfiler.CpuDataResponse response;
    try {
      response = myCpuService.getData(dataRequestBuilder.build());
    }
    catch (StatusRuntimeException e) {
      cancel(true);
      return;
    }

    List<CpuProfiler.CpuProfilerData> cpuDataList = response.getDataList();
    if (cpuDataList.isEmpty()) {
      return;
    }

    CpuProfiler.CpuProfilerData lastCpuData = null;

    for (CpuProfiler.CpuProfilerData data : cpuDataList) {
      if (data.getDataCase() == CpuProfiler.CpuProfilerData.DataCase.DATA_NOT_SET) {
        // No data to be handled.
        continue;
      }

      // Cpu Usage
      if (data.getDataCase() == CpuProfiler.CpuProfilerData.DataCase.CPU_USAGE) {
        // If lastCpuData is null, it means the first CPU usage data was read. Assign it to lastCpuData and go to the next iteration.
        if (lastCpuData == null) {
          lastCpuData = data;
          continue;
        }
        CpuUsageData usageData = getCpuUsageData(data, lastCpuData);
        myProcessCpuUsage.add((long)usageData.getAppUsage());
        myOtherProcessesCpuUsage.add((long)usageData.getOtherProcessesUsage());
        lastCpuData = data;
        // Update start timestamp inside the loop in case the thread is interrupted before its end.
        myDataRequestStartTimestampNs = lastCpuData.getBasicInfo().getEndTimestamp();
      }
      // Threads
      else if (data.getDataCase() == CpuProfiler.CpuProfilerData.DataCase.THREAD_ACTIVITIES) {
        // Repeat last Cpu Usage data to keep the size of the data arrays equals to the size of the timestamp array.
        int lastIndex = myTimestampArray.size() - 1;
        myProcessCpuUsage.add(lastIndex < 0 ? 0 : myProcessCpuUsage.get(lastIndex));
        myOtherProcessesCpuUsage.add(lastIndex < 0 ? 0 : myOtherProcessesCpuUsage.get(lastIndex));

        CpuProfiler.ThreadActivities threadActivities = data.getThreadActivities();
        if (threadActivities == null) {
          continue; // nothing to do
        }
        for (CpuProfiler.ThreadActivity threadActivity : threadActivities.getActivitiesList()) {
          int tid = threadActivity.getTid();
          ThreadStatesDataModel threadData;
          if (!myThreadsStateData.containsKey(tid)) {
            threadData = new ThreadStatesDataModel(threadActivity.getName(), threadActivity.getTid());
            myThreadsStateData.put(tid, threadData);
            myDataStore.registerAdapter(
              SeriesDataType.CPU_THREAD_STATE, new DataAdapterImpl<>(threadData.getTimestamps(), threadData.getThreadStates()), threadData);
            if (myThreadAddedNotifier != null) {
              myThreadAddedNotifier.threadAdded(threadData);
            }
          }
          threadData = myThreadsStateData.get(tid);
          assert threadData != null;
          threadData.addState(threadActivity.getNewState(), TimeUnit.NANOSECONDS.toMicros(threadActivity.getTimestamp()));

          if (threadActivity.getNewState() == CpuProfiler.ThreadActivity.State.DEAD) {
            // TODO: maybe it's better not to remove it and keep track of the threads alive using an integer field.
            myThreadsStateData.remove(tid);
          }
        }
      }
      long dataTimestamp = TimeUnit.NANOSECONDS.toMicros(data.getBasicInfo().getEndTimestamp());
      myTimestampArray.add(dataTimestamp);

      // Add the current number of threads to the correspondent line chart
      myNumberOfThreadsList.add(myThreadsStateData.size());
    }
  }

  public void setThreadAddedNotifier(ThreadAddedNotifier threadAddedNotifier) {
    myThreadAddedNotifier = threadAddedNotifier;
    myThreadAddedNotifier.threadsAdded(myThreadsStateData.values().toArray(new ThreadStatesDataModel[myThreadsStateData.size()]));
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
}
