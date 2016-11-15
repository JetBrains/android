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
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public final class ThreadStateDataSeries implements DataSeries<CpuProfiler.ThreadActivity.State> {

  private final int myProcessId;
  private final int myThreadId;
  private final CpuServiceGrpc.CpuServiceBlockingStub myService;

  public ThreadStateDataSeries(@NotNull CpuServiceGrpc.CpuServiceBlockingStub service, int pid, int tid) {
    myService = service;
    myProcessId = pid;
    myThreadId = tid;
  }

  public int getProcessId() {
    return myProcessId;
  }

  @Override
  public ImmutableList<SeriesData<CpuProfiler.ThreadActivity.State>> getDataForXRange(Range xRange) {
    // TODO Investigate if this is too slow. We can then have them share a common "series", and return a view to that series.
    ArrayList<SeriesData<CpuProfiler.ThreadActivity.State>> data = new ArrayList<>();
    //CpuProfiler.CpuDataRequest.Builder dataRequestBuilder = CpuProfiler.CpuDataRequest.newBuilder()
    //  .setAppId(myProcessId)
    //  .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin()))
    //  .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax()));
    //CpuProfiler.CpuDataResponse response = myService.getData(dataRequestBuilder.build());
    //for (CpuProfiler.CpuProfilerData cpuData : response.getDataList()) {
    //  if (cpuData.getDataCase() != CpuProfiler.CpuProfilerData.DataCase.THREAD_ACTIVITIES) {
    //    // No data to be handled.
    //    continue;
    //  }
    //  CpuProfiler.ThreadActivities threadActivities = cpuData.getThreadActivities();
    //  if (threadActivities == null) {
    //    continue; // nothing to do
    //  }
    //
    //  for (CpuProfiler.ThreadActivity threadActivity : threadActivities.getActivitiesList()) {
    //    if (threadActivity.getTid() == myThreadId) {
    //      long dataTimestamp = TimeUnit.NANOSECONDS.toMicros(cpuData.getBasicInfo().getEndTimestamp());
    //      data.add(new SeriesData<>(dataTimestamp, threadActivity.getNewState()));
    //    }
    //  }
    //}
    // TODO: Fake the data until the CPU rpc is fixed.
    long t = (long)(xRange.getMin() / 1000000);
    while (t * 1000000 < xRange.getMax()) {
      if (((t + myThreadId) % 2) == 0) {
        data.add(new SeriesData<>(t * 1000000, CpuProfiler.ThreadActivity.State.RUNNING));
      } else {
        data.add(new SeriesData<>(t * 1000000, CpuProfiler.ThreadActivity.State.SLEEPING));
      }
      t += 1;
    }
    return ContainerUtil.immutableList(data);
  }
}