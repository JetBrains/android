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
import com.android.tools.profiler.proto.CpuProfiler.CpuDataRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuUsageData;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuUsageDataSeries implements DataSeries<Long> {
  @NotNull
  private CpuServiceGrpc.CpuServiceBlockingStub myClient;

  private boolean myOtherProcesses;
  private final Common.Session mySession;

  public CpuUsageDataSeries(@NotNull CpuServiceGrpc.CpuServiceBlockingStub client, boolean otherProcesses, Common.Session session) {
    myClient = client;
    myOtherProcesses = otherProcesses;
    mySession = session;
  }

  @Override
  public List<SeriesData<Long>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    List<SeriesData<Long>> seriesData = new ArrayList<>();
    // Get an extra padding on each side, to have a smooth rendering at the edges.
    // TODO: Change the CPU API to allow specifying this padding in the request as number of samples.
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    CpuDataRequest.Builder dataRequestBuilder = CpuDataRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()) - bufferNs)
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()) + bufferNs);
    CpuDataResponse response = myClient.getData(dataRequestBuilder.build());
    CpuUsageData lastCpuData = null;
    for (CpuUsageData data : response.getDataList()) {
      long dataTimestamp = TimeUnit.NANOSECONDS.toMicros(data.getEndTimestamp());

      // If lastCpuData is null, it means the first CPU usage data was read. Assign it to lastCpuData and go to the next iteration.
      if (lastCpuData == null) {
        lastCpuData = data;
        continue;
      }
      CpuUsageDataSeries.UsageData usageData = getCpuUsageData(data, lastCpuData);
      if (myOtherProcesses) {
        seriesData.add(new SeriesData<>(dataTimestamp, (long)usageData.getOtherProcessesUsage()));
      }
      else {
        seriesData.add(new SeriesData<>(dataTimestamp, (long)usageData.getAppUsage()));
      }
      lastCpuData = data;
    }
    return seriesData;
  }

  private static UsageData getCpuUsageData(CpuUsageData data, CpuUsageData lastData) {
    long elapsed = (data.getElapsedTimeInMillisec() - lastData.getElapsedTimeInMillisec());
    // TODO: consider using raw data instead of percentage to improve efficiency.
    double app = 100.0 * (data.getAppCpuTimeInMillisec() - lastData.getAppCpuTimeInMillisec()) / elapsed;
    double system = 100.0 * (data.getSystemCpuTimeInMillisec() - lastData.getSystemCpuTimeInMillisec()) / elapsed;

    // System and app usages are read from them device in slightly different times. That can cause app usage to be slightly higher than
    // system usage and we need to adjust our values to cover these scenarios. Also, we use iowait (time waiting for I/O to complete) when
    // calculating the total elapsed CPU time. The problem is this value is not reliable (http://man7.org/linux/man-pages/man5/proc.5.html)
    // and can actually decrease. In these case we could end up with slightly negative system and app usages. That also needs adjustment and
    // we should make sure that 0% <= appUsage <= systemUsage <= 100%
    system = Math.max(0, Math.min(system, 100.0));
    app = Math.max(0, Math.min(app, system));

    return new UsageData(app, system);
  }

  private static final class UsageData {
    /**
     * App usage (in %) at a given point.
     */
    private double myAppUsage;

    /**
     * System usage (in %), including applications, at a given point.
     */
    private double mySystemUsage;

    UsageData(double appUsage, double systemUsage) {
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
