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
import com.android.tools.adtui.model.RangedListModel;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
    CpuProfiler.GetThreadsRequest.Builder request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)range.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)range.getMax()));
    CpuProfiler.GetThreadsResponse response = myService.getThreads(request.build());

    // Merge the two lists.
    int i = 0;
    int j = 0;
    while (i < getSize() && j < response.getThreadsCount()) {
      RangedCpuThread oldThread = getElementAt(i);
      CpuProfiler.GetThreadsResponse.Thread newThread = response.getThreads(j);
      if (oldThread.getThreadId() == newThread.getTid()) {
        i++;
        j++;
      } else {
        removeElementAt(i);
      }
    }
    while (i < getSize()) {
      removeElementAt(i);
      i++;
    }
    while (j < response.getThreadsCount()) {
      CpuProfiler.GetThreadsResponse.Thread newThread = response.getThreads(j);
      addElement(new RangedCpuThread(range, newThread.getTid(), newThread.getName()));
      j++;
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

    public RangedSeries<CpuProfiler.GetThreadsResponse.State> getDataSeries() {
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
