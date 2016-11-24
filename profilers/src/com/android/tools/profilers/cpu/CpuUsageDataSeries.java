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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuUsageDataSeries implements DataSeries<Long> {
  @NotNull
  private CpuServiceGrpc.CpuServiceBlockingStub myClient;

  private boolean myOtherProcesses;
  private final int myProcessId;

  public CpuUsageDataSeries(@NotNull CpuServiceGrpc.CpuServiceBlockingStub client, boolean otherProcesses, int id) {
    myClient = client;
    myOtherProcesses = otherProcesses;
    myProcessId = id;
  }

  @Override
  public ImmutableList<SeriesData<Long>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    List<SeriesData<Long>> seriesData = new ArrayList<>();
    // Get an extra padding on each side, to have a smooth rendering at the edges.
    // TODO: Change the CPU API to allow specifying this padding in the request as number of samples.
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    CpuProfiler.CpuDataRequest.Builder dataRequestBuilder = CpuProfiler.CpuDataRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()) - bufferNs)
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()) + bufferNs);
    CpuProfiler.CpuDataResponse response = myClient.getData(dataRequestBuilder.build());
    CpuProfiler.CpuProfilerData lastCpuData = null;
    for (CpuProfiler.CpuProfilerData data : response.getDataList()) {
      if (data.getDataCase() != CpuProfiler.CpuProfilerData.DataCase.CPU_USAGE) {
        // No data to be handled.
        continue;
      }
      long dataTimestamp = TimeUnit.NANOSECONDS.toMicros(data.getBasicInfo().getEndTimestamp());

      // If lastCpuData is null, it means the first CPU usage data was read. Assign it to lastCpuData and go to the next iteration.
      if (lastCpuData == null) {
        lastCpuData = data;
        continue;
      }
      CpuUsageDataSeries.CpuUsageData usageData = getCpuUsageData(data, lastCpuData);
      if (myOtherProcesses) {
        seriesData.add(new SeriesData<>(dataTimestamp, (long)usageData.getOtherProcessesUsage()));
      }
      else {
        seriesData.add(new SeriesData<>(dataTimestamp, (long)usageData.getAppUsage()));
      }
      lastCpuData = data;
    }
    return ContainerUtil.immutableList(seriesData);
  }

  private static CpuUsageDataSeries.CpuUsageData getCpuUsageData(CpuProfiler.CpuProfilerData data, CpuProfiler.CpuProfilerData lastData) {
    long elapsed = (data.getCpuUsage().getElapsedTimeInMillisec() - lastData.getCpuUsage().getElapsedTimeInMillisec());
    // TODO: consider using raw data instead of percentage to improve efficiency.
    double app = 100.0 * (data.getCpuUsage().getAppCpuTimeInMillisec() - lastData.getCpuUsage().getAppCpuTimeInMillisec()) / elapsed;
    double system =
      100.0 * (data.getCpuUsage().getSystemCpuTimeInMillisec() - lastData.getCpuUsage().getSystemCpuTimeInMillisec()) / elapsed;

    // System and app usages are read from them device in slightly different times. Make sure that appUsage <= systemUsage <= 100%
    system = Math.min(system, 100.0);
    app = Math.min(app, system);

    return new CpuUsageDataSeries.CpuUsageData(app, system);
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
