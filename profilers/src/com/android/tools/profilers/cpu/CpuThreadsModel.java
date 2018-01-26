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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuThreadsModel extends DefaultListModel<CpuThreadsModel.RangedCpuThread> implements Updatable {
  @NotNull private final CpuProfilerStage myStage;

  @NotNull private final Common.Session mySession;

  @NotNull private final Range myRange;

  @NotNull private final AspectObserver myAspectObserver;

  @VisibleForTesting
  protected final HashMap<Integer, RangedCpuThread> myThreadIdToCpuThread;

  public CpuThreadsModel(@NotNull Range range, @NotNull CpuProfilerStage stage, @NotNull Common.Session session) {
    myRange = range;
    myStage = stage;
    mySession = session;
    myAspectObserver = new AspectObserver();
    myThreadIdToCpuThread = new HashMap<>();

    myRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::rangeChanged);
    myStage.getAspect().addDependency(myAspectObserver).onChange(CpuProfilerAspect.CAPTURE_SELECTION, this::captureSelectionChanged);
    rangeChanged();
  }

  public void captureSelectionChanged() {
    CpuCapture capture = myStage.getCapture();
    if (capture instanceof AtraceCpuCapture) {
      Map<Integer, List<SeriesData<CpuProfilerStage.ThreadState>>> threadToStateSeries =
        ((AtraceCpuCapture)capture).getThreadIdToThreadStates();
      threadToStateSeries.forEach((key,val) -> {
        if (myThreadIdToCpuThread.containsKey(key)) {
          myThreadIdToCpuThread.get(key).addAtraceCaptureSeries(capture.getRange(), val);
        }});
    }
  }

  public void rangeChanged() {
    CpuProfiler.GetThreadsRequest.Builder request = CpuProfiler.GetThreadsRequest.newBuilder()
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
      RangedCpuThread cpuThread = new RangedCpuThread(myRange, newThread.getTid(), newThread.getName());
      myThreadIdToCpuThread.put(newThread.getTid(), cpuThread);
      addElement(cpuThread);
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
    private final StateChartModel<CpuProfilerStage.ThreadState> myModel;
    // This data series combines the sampled data series pulled from perfd, and the Atrace data series
    // populated when an atrace capture is parsed.
    private final MergeCaptureDataSeries<CpuProfilerStage.ThreadState> mySeries;
    // The Atrace data series is added to the MergeCaptureDataSeries, however it is only populated
    // when an Atrace capture is parsed. When the data series is populated the results from the
    // Atrace data series are used in place of the ThreadStateDataSeries for the range that
    // overlap.
    private final AtraceDataSeries<CpuProfilerStage.ThreadState> myAtraceDataSeries;

    public RangedCpuThread(Range range, int threadId, String name) {
      myRange = range;
      myThreadId = threadId;
      myName = name;
      myModel = new StateChartModel<>();
      ThreadStateDataSeries threadStateDataSeries = new ThreadStateDataSeries(myStage, mySession, myThreadId);
      myAtraceDataSeries = new AtraceDataSeries();
      mySeries = new MergeCaptureDataSeries(threadStateDataSeries, myAtraceDataSeries);
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

    public MergeCaptureDataSeries getStateSeries() {
      return mySeries;
    }

    /**
     * @param range the range of the capture. The range is expected to cover the full range of the series data.
     * @param seriesData to be added to {@link AtraceDataSeries}.
     */
    public void addAtraceCaptureSeries(Range range, List<SeriesData<CpuProfilerStage.ThreadState>> seriesData) {
      myAtraceDataSeries.addCaptureSeriesData(range, seriesData);
    }
  }
}
