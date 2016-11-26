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
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.AspectModel;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class CpuProfilerStage extends Stage {

  /**
   * The thread states combined with the capture states.
   */
  public enum ThreadState {
    RUNNING,
    RUNNING_CAPTURED,
    SLEEPING,
    SLEEPING_CAPTURED,
    DEAD,
    DEAD_CAPTURED,
    UNKNOWN
  }

  private static final Logger LOG = Logger.getInstance(CpuProfilerStage.class);
  @NotNull
  private final CpuServiceGrpc.CpuServiceBlockingStub myCpuService;
  @NotNull
  private final CpuTraceDataSeries myCpuTraceDataSeries;
  private AspectModel<CpuProfilerAspect> myAspect = new AspectModel<>();
  @Nullable
  private CpuCapture myCapture;
  /**
   * Whether there is a capture in progress.
   * TODO: Timeouts
   */
  private boolean myCapturing;
  /**
   * Id of the current selected thread.
   * If this variable has a valid thread id, {@link #myCaptureNode} should store the value of the {@link HNode} correspondent to the thread.
   */
  private int mySelectedThread;

  /**
   * Maps trace ids to their correspondent captures.
   */
  private Map<Integer, CpuCapture> myTraceCaptures = new HashMap<>();

  public CpuProfilerStage(@NotNull StudioProfilers profiler) {
    super(profiler);
    myCpuService = getStudioProfilers().getClient().getCpuClient();
    myCpuTraceDataSeries = new CpuTraceDataSeries();
  }

  @Override
  public void enter() {
  }

  @Override
  public void exit() {
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myAspect;
  }

  public void startCapturing() {
    CpuProfiler.CpuProfilingAppStartRequest request = CpuProfiler.CpuProfilingAppStartRequest.newBuilder()
      .setAppPkgName(getStudioProfilers().getProcess().getName()) // TODO: Investigate if this is the right way of choosing the app
      .setProfiler(CpuProfiler.CpuProfilingAppStartRequest.Profiler.ART) // TODO: support simpleperf
      .setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED) // TODO: support instrumented mode
      .build();

    CpuProfiler.CpuProfilingAppStartResponse response = myCpuService.startProfilingApp(request);

    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS)) {
      LOG.error("Unable to start tracing:" + response.getStatus());
      LOG.error(response.getErrorMessage());
      myCapturing = false;
    }
    else {
      myCapturing = true;
    }
    myAspect.changed(CpuProfilerAspect.CAPTURE);
  }

  public void stopCapturing() {
    CpuProfiler.CpuProfilingAppStopRequest request = CpuProfiler.CpuProfilingAppStopRequest.newBuilder()
      .setAppPkgName(getStudioProfilers().getProcess().getName()) // TODO: Investigate if this is the right way of choosing the app
      .setProfiler(CpuProfiler.CpuProfilingAppStopRequest.Profiler.ART) // TODO: support simpleperf
      .build();

    CpuProfiler.CpuProfilingAppStopResponse response = myCpuService.stopProfilingApp(request);
    CpuCapture capture = null;

    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS)) {
      LOG.error("Unable to stop tracing:" + response.getStatus());
      LOG.error(response.getErrorMessage());
    }
    else {
      capture = new CpuCapture(response);
      // Add the capture to the map using the trace from response as key
      myTraceCaptures.put(response.getTraceId(), capture);
    }
    myAspect.changed(CpuProfilerAspect.CAPTURE);

    if (capture != null) {
      setCapture(capture);
    }
    myCapturing = false;
  }

  public void setCapture(CpuCapture capture) {
    myCapture = capture;
    mySelectedThread = myCapture.getMainThreadId();
    myAspect.changed(CpuProfilerAspect.SELECTED_THREADS);

    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    timeline.setStreaming(false);
    timeline.getSelectionRange().set(myCapture.getRange());
  }

  public int getSelectedThread() {
    return mySelectedThread;
  }

  public void setSelectedThread(int id) {
    if (myCapture != null) {
      myCapture.setSelectedThread(id);
    }
    mySelectedThread = id;
    myAspect.changed(CpuProfilerAspect.SELECTED_THREADS);
  }

  /**
   * The current capture of the cpu profiler, if null there is no capture to display otherwise we need to be in
   * a capture viewing mode.
   */
  @Nullable
  public CpuCapture getCapture() {
    return myCapture;
  }

  public boolean isCapturing() {
    return myCapturing;
  }

  public DataSeries<CpuCapture> getCpuTraceDataSeries() {
    return myCpuTraceDataSeries;
  }


  @NotNull
  public RangedListModel<CpuThreadsModel.RangedCpuThread> getThreadStates() {
    return new CpuThreadsModel(this, getStudioProfilers().getProcessId());
  }

  public CpuCapture getCapture(int id) {
    return myTraceCaptures.get(id);
  }


  private class CpuTraceDataSeries implements DataSeries<CpuCapture> {
    @Override
    public ImmutableList<SeriesData<CpuCapture>> getDataForXRange(Range xRange) {
      long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
      long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());

      CpuProfiler.GetTraceInfoResponse response = myCpuService.getTraceInfo(
        CpuProfiler.GetTraceInfoRequest.newBuilder().
        setAppId(getStudioProfilers().getProcessId()).
        setFromTimestamp(rangeMin).setToTimestamp(rangeMax).build());

      List<SeriesData<CpuCapture>> seriesData = new ArrayList<>();
      for (CpuProfiler.TraceInfo traceInfo : response.getTraceInfoList()) {
        CpuCapture capture = myTraceCaptures.get(traceInfo.getTraceId());
        Range range = capture.getRange();

        seriesData.add(new SeriesData<>((long)range.getMin(), capture));
      }
      return ContainerUtil.immutableList(seriesData);
    }
  }
}
