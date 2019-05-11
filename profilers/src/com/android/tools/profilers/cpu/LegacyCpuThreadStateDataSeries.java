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
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler.GetThreadsRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetThreadsResponse;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Legacy class responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class LegacyCpuThreadStateDataSeries implements DataSeries<CpuProfilerStage.ThreadState> {

  @NotNull
  private CpuServiceGrpc.CpuServiceBlockingStub myClient;
  @NotNull
  private final Common.Session mySession;
  private final int myThreadId;
  @Nullable private final CpuCapture mySelectedCapture;

  public LegacyCpuThreadStateDataSeries(@NotNull CpuServiceGrpc.CpuServiceBlockingStub client,
                                        @NotNull Common.Session session,
                                        int tid,
                                        @Nullable CpuCapture selectedCapture) {
    myClient = client;
    mySession = session;
    myThreadId = tid;
    mySelectedCapture = selectedCapture;
  }

  @Override
  public List<SeriesData<CpuProfilerStage.ThreadState>> getDataForXRange(Range xRange) {
    // TODO Investigate if this is too slow. We can then have them share a common "series", and return a view to that series.
    ArrayList<SeriesData<CpuProfilerStage.ThreadState>> data = new ArrayList<>();

    long min = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
    long max = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());
    GetThreadsResponse threads = myClient.getThreads(GetThreadsRequest.newBuilder()
                                                       .setSession(mySession)
                                                       .setStartTimestamp(min)
                                                       .setEndTimestamp(max)
                                                       .build());

    for (GetThreadsResponse.Thread thread : threads.getThreadsList()) {
      if (thread.getTid() == myThreadId) {
        // Merges information from current capture and samples:
        ArrayList<Double> captureTimes = new ArrayList<>(2);
        if (mySelectedCapture != null && mySelectedCapture.getThreads().stream().anyMatch(t -> t.getId() == myThreadId)) {
          captureTimes.add(mySelectedCapture.getRange().getMin());
          captureTimes.add(mySelectedCapture.getRange().getMax());
        }

        List<GetThreadsResponse.ThreadActivity> list = thread.getActivitiesList();
        int i = 0;
        int j = 0;
        boolean inCapture = false;
        Cpu.CpuThreadData.State state = Cpu.CpuThreadData.State.UNSPECIFIED;
        while (i < list.size()) {
          GetThreadsResponse.ThreadActivity activity = list.get(i);

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
          // We shouldn't add an activity if capture has started before the first activity for the current thread.
          if (state != Cpu.CpuThreadData.State.UNSPECIFIED) {
            data.add(new SeriesData<>(time, CpuThreadsModel.getState(state, inCapture)));
          }
        }
        while (j < captureTimes.size()) {
          inCapture = !inCapture;
          data.add(new SeriesData<>(captureTimes.get(j).longValue(), CpuThreadsModel.getState(state, inCapture)));
          j++;
        }
      }
    }
    return data;
  }
}