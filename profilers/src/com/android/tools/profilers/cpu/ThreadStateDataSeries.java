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

public final class ThreadStateDataSeries implements DataSeries<CpuProfiler.GetThreadsResponse.State> {

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
  public ImmutableList<SeriesData<CpuProfiler.GetThreadsResponse.State>> getDataForXRange(Range xRange) {
    // TODO Investigate if this is too slow. We can then have them share a common "series", and return a view to that series.
    ArrayList<SeriesData<CpuProfiler.GetThreadsResponse.State>> data = new ArrayList<>();

    CpuProfiler.GetThreadsRequest.Builder request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax()));
    CpuProfiler.GetThreadsResponse response = myService.getThreads(request.build());


    for (CpuProfiler.GetThreadsResponse.Thread thread : response.getThreadsList()) {
      if (thread.getTid() == myThreadId) {
        for (CpuProfiler.GetThreadsResponse.ThreadActivity activity : thread.getActivitiesList()) {
          CpuProfiler.GetThreadsResponse.State state = activity.getNewState();
          long timestamp = TimeUnit.NANOSECONDS.toMicros(activity.getTimestamp());
          data.add(new SeriesData<>(timestamp, state));
        }
      }
    }
    return ContainerUtil.immutableList(data);
  }
}