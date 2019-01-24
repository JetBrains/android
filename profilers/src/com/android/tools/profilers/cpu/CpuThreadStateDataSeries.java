/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * This class is responsible for querying CPU thread data from unified pipeline {@link Common.Event} and extract a single thread data.
 */
public class CpuThreadStateDataSeries implements DataSeries<CpuProfilerStage.ThreadState> {
  @NotNull private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myClient;
  private final long myStreamId;
  private final int myPid;
  private final int myThreadId;

  public CpuThreadStateDataSeries(@NotNull ProfilerServiceGrpc.ProfilerServiceBlockingStub client,
                                  long streamId, int pid, int threadId) {
    myClient = client;
    myStreamId = streamId;
    myPid = pid;
    myThreadId = threadId;
  }

  @Override
  public List<SeriesData<CpuProfilerStage.ThreadState>> getDataForXRange(Range xRangeUs) {
    List<SeriesData<CpuProfilerStage.ThreadState>> series = new ArrayList<>();
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)xRangeUs.getMax());
    // Query from the beginning because we need the last state of the thread before range min.
    Profiler.GetEventGroupsResponse response = myClient.getEventGroups(
      Profiler.GetEventGroupsRequest.newBuilder()
        .setStreamId(myStreamId)
        .setPid(myPid)
        .setKind(Common.Event.Kind.CPU_THREAD)
        .setGroupId(myThreadId)
        .setToTimestamp(maxNs)
        .build());
    // We don't expect more than one data group for the given group ID.
    assert response.getGroupsCount() <= 1;
    if (response.getGroupsCount() == 1) {
      for (Common.Event event : response.getGroups(0).getEventsList()) {
        series.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(event.getTimestamp()),
                                    CpuThreadsModel.getState(event.getCpuThread().getState(), false)));
      }
    }
    return series;
  }
}
