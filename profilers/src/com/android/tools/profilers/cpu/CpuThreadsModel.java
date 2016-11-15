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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuThreadsModel extends DefaultListModel<CpuThreadsModel.RangedCpuThread>
  implements RangedListModel<CpuThreadsModel.RangedCpuThread> {
  @NotNull
  private CpuServiceGrpc.CpuServiceBlockingStub myService;

  private final int myProcessId;

  public CpuThreadsModel(@NotNull CpuServiceGrpc.CpuServiceBlockingStub service, int id) {
    myService = service;
    myProcessId = id;
  }

  @Override
  public void update(Range range) {
    CpuProfiler.CpuDataRequest.Builder dataRequestBuilder = CpuProfiler.CpuDataRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)range.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)range.getMax()));
    CpuProfiler.CpuDataResponse response = myService.getData(dataRequestBuilder.build());
    // Keep the threads in order of creation
    Map<Integer, String> threads = new TreeMap<>();
    for (CpuProfiler.CpuProfilerData data : response.getDataList()) {
      if (data.getDataCase() != CpuProfiler.CpuProfilerData.DataCase.THREAD_ACTIVITIES) {
        // No data to be handled.
        continue;
      }
      CpuProfiler.ThreadActivities threadActivities = data.getThreadActivities();
      if (threadActivities != null) {
        for (CpuProfiler.ThreadActivity threadActivity : threadActivities.getActivitiesList()) {
          threads.put(threadActivity.getTid(), threadActivity.getName());
        }
      }
      // Add the new threads (at the end and in order) and removes the ones that don't exist anymore.
      for (int i = 0; i < getSize(); i++) {
        RangedCpuThread thread = getElementAt(i);
        String prev = threads.remove(thread.getThreadId());
        if (prev == null) {
          // The thread doesn't exist anymore
          removeElementAt(i);
          i--;
        }
      }
      // Add the ones that were not updated
      for (Map.Entry<Integer, String> entry : threads.entrySet()) {
        addElement(new RangedCpuThread(range, entry.getKey(), entry.getValue()));
      }
    }
  }

  public class RangedCpuThread {

    private final int myThreadId;
    private final String myName;
    private final Range myRange;

    public RangedCpuThread(Range range, int threadId, String name) {
      myRange = range;
      myThreadId = threadId;
      myName = name;
    }

    public RangedSeries<CpuProfiler.ThreadActivity.State> getDataSeries() {
      ThreadStateDataSeries series = new ThreadStateDataSeries(myService, myProcessId, myThreadId);
      return new RangedSeries<>(myRange, series);
    }

    public int getThreadId() {
      return myThreadId;
    }

    public String getName() {
      return myName;
    }
  }
}
