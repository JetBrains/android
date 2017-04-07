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
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ThreadStateDataSeries implements DataSeries<CpuProfilerStage.ThreadState> {

  private final int myProcessId;
  private final int myThreadId;
  private final CpuProfilerStage myStage;

  public ThreadStateDataSeries(@NotNull CpuProfilerStage stage, int pid, int tid) {
    myStage = stage;
    myProcessId = pid;
    myThreadId = tid;
  }

  public int getProcessId() {
    return myProcessId;
  }

  @Override
  public ImmutableList<SeriesData<CpuProfilerStage.ThreadState>> getDataForXRange(Range xRange) {
    // TODO Investigate if this is too slow. We can then have them share a common "series", and return a view to that series.
    ArrayList<SeriesData<CpuProfilerStage.ThreadState>> data = new ArrayList<>();

    long min = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
    long max = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());
    CpuServiceGrpc.CpuServiceBlockingStub client = myStage.getStudioProfilers().getClient().getCpuClient();
    CpuProfiler.GetThreadsResponse threads = client.getThreads(CpuProfiler.GetThreadsRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(min)
      .setEndTimestamp(max)
      .build());

    CpuProfiler.GetTraceInfoResponse traces = client.getTraceInfo(CpuProfiler.GetTraceInfoRequest.newBuilder()
        .setAppId(myProcessId)
        .setFromTimestamp(min)
        .setToTimestamp(max)
        .build());

    for (CpuProfiler.GetThreadsResponse.Thread thread : threads.getThreadsList()) {
      if (thread.getTid() == myThreadId) {
        // Merges information from traces and samples:
        ArrayList<Double> captureTimes = new ArrayList<>(traces.getTraceInfoCount() * 2);
        for (CpuProfiler.TraceInfo traceInfo : traces.getTraceInfoList()) {
          CpuCapture capture = myStage.getCapture(traceInfo.getTraceId());
          if (capture != null && capture.containsThread(myThreadId)) {
            captureTimes.add(capture.getRange().getMin());
            captureTimes.add(capture.getRange().getMax());
          }
        }

        List<CpuProfiler.GetThreadsResponse.ThreadActivity> list = thread.getActivitiesList();
        int i = 0;
        int j = 0;
        boolean inCapture = false;
        CpuProfiler.GetThreadsResponse.State state = CpuProfiler.GetThreadsResponse.State.UNSPECIFIED;
        while (i < list.size()) {
          CpuProfiler.GetThreadsResponse.ThreadActivity activity = list.get(i);

          long timestamp = TimeUnit.NANOSECONDS.toMicros(activity.getTimestamp());
          long captureTime = j < captureTimes.size() ? captureTimes.get(j).longValue() : Long.MAX_VALUE;

          long time;
          if (captureTime < timestamp) {
            inCapture = !inCapture;
            time = captureTime;
            j++;
          }
          else {
            state = activity.getNewState();
            time = timestamp;
            i++;
          }
          data.add(new SeriesData<>(time, getState(state, inCapture)));
        }
        while (j < captureTimes.size()) {
          inCapture = !inCapture;
          data.add(new SeriesData<>(captureTimes.get(j).longValue(), getState(state, inCapture)));
          j++;
        }
      }
    }
    return ContainerUtil.immutableList(data);
  }

  private static CpuProfilerStage.ThreadState getState(CpuProfiler.GetThreadsResponse.State state, boolean captured) {
    switch (state) {
      case RUNNING:
        return captured ? CpuProfilerStage.ThreadState.RUNNING_CAPTURED : CpuProfilerStage.ThreadState.RUNNING;
      case DEAD:
        return captured ? CpuProfilerStage.ThreadState.DEAD_CAPTURED : CpuProfilerStage.ThreadState.DEAD;
      case SLEEPING:
        return captured ? CpuProfilerStage.ThreadState.SLEEPING_CAPTURED : CpuProfilerStage.ThreadState.SLEEPING;
      default:
        return CpuProfilerStage.ThreadState.UNKNOWN;
    }
  }
}