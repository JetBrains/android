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
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is responsible for querying CPU thread data from unified pipeline {@link Common.Event} and extract a single thread data.
 */
public class CpuThreadStateDataSeries implements DataSeries<CpuProfilerStage.ThreadState> {
  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myClient;
  private final long myStreamId;
  private final int myPid;
  private final int myThreadId;
  @Nullable private final CpuCapture mySelectedCapture;

  public CpuThreadStateDataSeries(@NotNull TransportServiceGrpc.TransportServiceBlockingStub client,
                                  long streamId,
                                  int pid,
                                  int threadId,
                                  @Nullable CpuCapture selectedCapture) {
    myClient = client;
    myStreamId = streamId;
    myPid = pid;
    myThreadId = threadId;
    mySelectedCapture = selectedCapture;
  }

  @Override
  public List<SeriesData<CpuProfilerStage.ThreadState>> getDataForRange(Range rangeUs) {
    List<SeriesData<CpuProfilerStage.ThreadState>> series = new ArrayList<>();
    long minNs = TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin());
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax());
    GetEventGroupsResponse response = myClient.getEventGroups(
      GetEventGroupsRequest.newBuilder()
        .setStreamId(myStreamId)
        .setPid(myPid)
        .setKind(Common.Event.Kind.CPU_THREAD)
        .setGroupId(myThreadId)
        .setFromTimestamp(minNs)
        .setToTimestamp(maxNs)
        .build());
    // We don't expect more than one data group for the given group ID.
    assert response.getGroupsCount() <= 1;
    if (response.getGroupsCount() == 1) {
      // Merges information from traces and samples:
      ArrayList<Double> captureTimes = new ArrayList<>(2);
      if (mySelectedCapture != null && mySelectedCapture.getThreads().stream().anyMatch(t -> t.getId() == myThreadId)) {
        captureTimes.add(mySelectedCapture.getRange().getMin());
        captureTimes.add(mySelectedCapture.getRange().getMax());
      }

      int i = 0;
      int j = 0;
      boolean inCapture = false;
      Cpu.CpuThreadData.State state = Cpu.CpuThreadData.State.UNSPECIFIED;
      List<Common.Event> events = response.getGroups(0).getEventsList();
      while (i < events.size()) {
        Common.Event event = events.get(i);
        long timestamp = TimeUnit.NANOSECONDS.toMicros(event.getTimestamp());
        long captureTime = j < captureTimes.size() ? captureTimes.get(j).longValue() : Long.MAX_VALUE;

        long time;
        if (captureTime < timestamp) {
          inCapture = !inCapture;
          time = captureTime;
          j++;
        }
        else {
          state = event.getCpuThread().getState();
          time = timestamp;
          i++;
        }
        // We shouldn't add an activity if capture has started before the first activity for the current thread.
        if (state != Cpu.CpuThreadData.State.UNSPECIFIED) {
          series.add(new SeriesData<>(time, CpuThreadsModel.getState(state, inCapture)));
        }
      }
      while (j < captureTimes.size()) {
        inCapture = !inCapture;
        series.add(new SeriesData<>(captureTimes.get(j).longValue(), CpuThreadsModel.getState(state, inCapture)));
        j++;
      }
    }

    return series;
  }
}
