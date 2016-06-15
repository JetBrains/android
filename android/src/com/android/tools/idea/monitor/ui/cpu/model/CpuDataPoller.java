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

import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfilerServiceGrpc;
import io.grpc.StatusRuntimeException;

import java.util.List;

/**
 * Collects CPU data from the device.
 * Data is collected in a thread, which makes gRPC requests to get it.
 */
// TODO: create a DataPoller interface/abstract class to be implemented/extended by this and other pollers.
public class CpuDataPoller {

  /**
   * Delay between CPU data requests (in milliseconds).
   */
  private static final int POLLING_DELAY_MS = 1000;

  private Thread myRequestCpuDataThread;

  /**
   * Start requesting CPU usage data.
   * @param pid process ID of the target app
   * @param cpuService gRPC service to get the CPU data from
   */
  public void startDataRequest(int pid, CpuProfilerServiceGrpc.CpuProfilerServiceBlockingStub cpuService) {
    myRequestCpuDataThread = new Thread(() -> {
      long dataRequestStartTimestamp = Long.MIN_VALUE;

      while (true) {
        CpuProfiler.CpuDataRequest.Builder dataRequestBuilder = CpuProfiler.CpuDataRequest.newBuilder();
        dataRequestBuilder.setAppId(pid);
        dataRequestBuilder.setStartTimestamp(dataRequestStartTimestamp);
        dataRequestBuilder.setEndTimestamp(Long.MAX_VALUE);
        CpuProfiler.CpuDataResponse response;
        try {
          response = cpuService.getData(dataRequestBuilder.build());
        } catch (StatusRuntimeException e) {
          break; // break the loop if channel is shutdown.
        }


        List<Cpu.CpuProfilerData> cpuDataList = response.getDataList();
        if (cpuDataList.isEmpty()) {
          continue; // Empty data. Nothing to do.
        }

        // Create a cpuData with default values as the first value.
        Cpu.CpuProfilerData lastCpuData = Cpu.CpuProfilerData.newBuilder().build();

        for (Cpu.CpuProfilerData data : cpuDataList) {
          // TODO: link this data with a SeriesDataStore to display it in the UI
          CpuUsageData usageData = getCpuUsageData(data, lastCpuData);
          lastCpuData = data;
          // Update start timestamp inside the loop in case the thread is interrupted before its end.
          dataRequestStartTimestamp = lastCpuData.getBasicInfo().getEndTimestamp();
        }

        try {
          Thread.sleep(POLLING_DELAY_MS);
        } catch (InterruptedException ignored) {
        }
      }
    });
    myRequestCpuDataThread.start();
  }

  /**
   * Stop requesting CPU data.
   */
  public void stopDataRequest() {
    if (myRequestCpuDataThread != null) {
      myRequestCpuDataThread.interrupt();
    }
  }

  private static CpuUsageData getCpuUsageData(Cpu.CpuProfilerData data, Cpu.CpuProfilerData lastData) {
    long elapsed = (data.getCpuUsage().getElapsedTimeInMillisec() - lastData.getCpuUsage().getElapsedTimeInMillisec());
    // TODO: consider using raw data instead of percentage to improve efficiency.
    double app = 100.0 * (data.getCpuUsage().getAppCpuTimeInMillisec() - lastData.getCpuUsage().getAppCpuTimeInMillisec()) / elapsed;
    double system =
      100.0 * (data.getCpuUsage().getSystemCpuTimeInMillisec() - lastData.getCpuUsage().getSystemCpuTimeInMillisec()) / elapsed;

    return new CpuUsageData(app, system, elapsed);
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

    // TODO: remove this parameter if we don't need it later.
    /**
     * Elapsed time (in ms) since system start.
     */
    private long myElapsedTime;

    CpuUsageData(double appUsage, double systemUsage, long elapsedTime) {
      myAppUsage = appUsage;
      mySystemUsage = systemUsage;
      myElapsedTime = elapsedTime;
    }

    double getAppUsage() {
      return myAppUsage;
    }

    double getSystemUsage() {
      return mySystemUsage;
    }

    long getElapsedTime() {
      return myElapsedTime;
    }
  }
}
