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
import com.android.tools.idea.monitor.profilerclient.DeviceProfilerService;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfilerServiceGrpc;
import gnu.trove.TLongArrayList;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Collects CPU data from the device.
 * Data is collected in a thread, which makes gRPC requests to get it.
 */
public class CpuDataPoller extends Poller {

  /**
   * Delay between CPU data requests (in milliseconds).
   */
  private static final long POLLING_DELAY_NS = TimeUnit.SECONDS.toNanos(1);

  private long myDataRequestStartTimestamp;

  private int myPid;

  private DeviceProfilerService myDeviceProfilerService;

  private CpuProfilerServiceGrpc.CpuProfilerServiceBlockingStub myCpuService;

  private TLongArrayList myTimestampArray = new TLongArrayList();

  private final long myDeviceTimeOffset;

  // TODO: Change them to double after refactoring LineChart
  private TLongArrayList myProcessCpuUsage = new TLongArrayList();
  private TLongArrayList myOtherProcessesCpuUsage = new TLongArrayList();

  public CpuDataPoller(@NotNull DeviceProfilerService service, int pid, @NotNull SeriesDataStore dataStore) {
    super(service, POLLING_DELAY_NS);
    myDeviceProfilerService = service;
    myPid = pid;
    myDeviceTimeOffset = dataStore.getDeviceTimeOffset();
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
    dataStore.registerAdapter(SeriesDataType.CPU_MY_PROCESS, new CpuUsageDataAdapter(myProcessCpuUsage));
    dataStore.registerAdapter(SeriesDataType.CPU_OTHER_PROCESSES, new CpuUsageDataAdapter(myOtherProcessesCpuUsage));
  }

  @Override
  protected void asyncInit() {
    myCpuService = myDeviceProfilerService.getCpuService();
    CpuProfiler.CpuStartRequest.Builder requestBuilder = CpuProfiler.CpuStartRequest.newBuilder().setAppId(myPid);
    myCpuService.startMonitoringApp(requestBuilder.build());
    myDataRequestStartTimestamp = Long.MIN_VALUE;
  }

  @Override
  protected void asyncShutdown() {
    try {
      myCpuService.stopMonitoringApp(CpuProfiler.CpuStopRequest.newBuilder().setAppId(myPid).build());
    } catch (StatusRuntimeException ignored) {
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
    } catch (StatusRuntimeException e) {
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
      myTimestampArray.add(TimeUnit.NANOSECONDS.toMillis(data.getBasicInfo().getEndTimestamp() - myDeviceTimeOffset));
      CpuUsageData usageData = getCpuUsageData(data, lastCpuData);
      myProcessCpuUsage.add((long) usageData.getAppUsage());
      myOtherProcessesCpuUsage.add((long) usageData.getOtherProcessesUsage());
      lastCpuData = data;
      // Update start timestamp inside the loop in case the thread is interrupted before its end.
      myDataRequestStartTimestamp = lastCpuData.getBasicInfo().getEndTimestamp();
    }
  }

  private final class CpuUsageDataAdapter implements DataAdapter<Long> {

    @NotNull
    private TLongArrayList myCpuUsage;

    CpuUsageDataAdapter(@NotNull TLongArrayList cpuUsage) {
      myCpuUsage = cpuUsage;
    }

    @Override
    public void setStartTime(long time) {
      // TODO: consider removing ths method from the adapter interface and using getDeviceTimeOffset() when adding data instead.
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
      data.value = myCpuUsage.get(index);
      return data;
    }

    @Override
    public void reset() {
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


}
