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

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuThreadsModel extends DefaultListModel<CpuThreadsModel.RangedCpuThread> implements Updatable {
  @NotNull
  private final CpuProfilerStage myStage;

  private final int myProcessId;

  private final Common.Session mySession;

  private final Range myRange;

  private final AspectObserver myAspectObserver;

  public CpuThreadsModel(@NotNull Range range, @NotNull CpuProfilerStage stage, int id, Common.Session session) {
    myRange = range;
    myStage = stage;
    myProcessId = id;
    mySession = session;
    myAspectObserver = new AspectObserver();

    myRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::rangeChanged);
    rangeChanged();
  }

  public void rangeChanged() {
    CpuProfiler.GetThreadsRequest.Builder request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setProcessId(myProcessId)
      .setSession(mySession)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)myRange.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)myRange.getMax()));
    CpuServiceGrpc.CpuServiceBlockingStub client = myStage.getStudioProfilers().getClient().getCpuClient();
    CpuProfiler.GetThreadsResponse response = client.getThreads(request.build());

    // Merge the two lists.
    int i = 0;
    int j = 0;
    while (i < getSize() && j < response.getThreadsCount()) {
      RangedCpuThread oldThread = getElementAt(i);
      CpuProfiler.GetThreadsResponse.Thread newThread = response.getThreads(j);
      if (oldThread.getThreadId() == newThread.getTid()) {
        i++;
        j++;
      }
      else {
        removeElementAt(i);
      }
    }
    while (i < getSize()) {
      removeElementAt(i);
      i++;
    }
    while (j < response.getThreadsCount()) {
      CpuProfiler.GetThreadsResponse.Thread newThread = response.getThreads(j);
      addElement(new RangedCpuThread(myRange, newThread.getTid(), newThread.getName()));
      j++;
    }
  }

  @Override
  public void update(long elapsedNs) {
    fireContentsChanged(this, 0, size());
  }

  public class RangedCpuThread {

    private final int myThreadId;
    private final String myName;
    private final Range myRange;
    private final ThreadStateDataSeries mySeries;
    private final StateChartModel<CpuProfilerStage.ThreadState> myModel;

    public RangedCpuThread(Range range, int threadId, String name) {
      myRange = range;
      myThreadId = threadId;
      myName = name;
      myModel = new StateChartModel<>();
      mySeries = new ThreadStateDataSeries(myStage, myProcessId, mySession, myThreadId);
      myModel.addSeries(new RangedSeries<>(myRange, mySeries));
    }

    public int getThreadId() {
      return myThreadId;
    }

    public String getName() {
      return myName;
    }

    public StateChartModel<CpuProfilerStage.ThreadState> getModel() {
      return myModel;
    }

    public ThreadStateDataSeries getStateSeries() {
      return mySeries;
    }
  }
}
