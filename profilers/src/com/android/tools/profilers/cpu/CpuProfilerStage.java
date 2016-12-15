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
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class CpuProfilerStage extends Stage {

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 5, 10, "%");
  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(1, 5, 1, "");
  private final CpuMonitor myMonitor;
  private final CpuThreadsModel myThreadsStates;
  private final AxisComponentModel myCpuUsageAxis;
  private final AxisComponentModel myThreadCountAxis;
  private final LineChartModel myCpuUsage;
  private final LegendComponentModel myLegends;
  private final DurationDataModel<CpuCapture> myTraceDurations;
  private final EventMonitor myEventMonitor;

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
    WAITING,
    WAITING_CAPTURED,
    UNKNOWN
  }

  private static final Logger LOG = Logger.getInstance(CpuProfilerStage.class);
  @NotNull
  private final CpuServiceGrpc.CpuServiceBlockingStub myCpuService;
  @NotNull
  private final CpuTraceDataSeries myCpuTraceDataSeries;
  private AspectModel<CpuProfilerAspect> myAspect = new AspectModel<>();

  /**
   * The current capture.
   */
  @Nullable
  private CpuCapture myCapture;
  /**
   * Whether there is a capture in progress.
   * TODO: Timeouts. Also, capturing state should come from the device instead of being kept here.
   */
  private boolean myCapturing;
  /**
   * Id of the current selected thread.
   * If this variable has a valid thread id, {@link #myCaptureNode} should store the value of the {@link HNode} correspondent to the thread.
   */
  private int mySelectedThread;

  /**
   * A cache of already parsed captures, indexed by trace_id.
   */
  private Map<Integer, CpuCapture> myTraceCaptures = new HashMap<>();

  public CpuProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myCpuService = getStudioProfilers().getClient().getCpuClient();
    myCpuTraceDataSeries = new CpuTraceDataSeries();

    Range viewRange = getStudioProfilers().getTimeline().getViewRange();
    Range dataRange = getStudioProfilers().getTimeline().getDataRange();
    Range cpuUsageYRange = new Range(0, 100);
    Range threadYRange = new Range(0, 8);

    //TODO, enter exit ->
    myMonitor = new CpuMonitor(profilers);
    RangedContinuousSeries thisCpuSeries = new RangedContinuousSeries("App", viewRange, cpuUsageYRange, myMonitor.getThisProcessCpuUsageSeries());
    RangedContinuousSeries otherCpuSeries = new RangedContinuousSeries("Others", viewRange, cpuUsageYRange, myMonitor.getOtherProcessesCpuUsage());
    RangedContinuousSeries threadsCountSeries = new RangedContinuousSeries("Threads", viewRange, threadYRange, myMonitor.getThreadsCount());
    myCpuUsage = new LineChartModel();
    myCpuUsage.add(thisCpuSeries);
    myCpuUsage.add(otherCpuSeries);
    myCpuUsage.add(threadsCountSeries);

    myCpuUsageAxis = new AxisComponentModel(cpuUsageYRange, CPU_USAGE_FORMATTER, AxisComponentModel.AxisOrientation.RIGHT);
    myCpuUsageAxis.clampToMajorTicks(true);

    myThreadCountAxis = new AxisComponentModel(threadYRange, NUM_THREADS_AXIS, AxisComponentModel.AxisOrientation.LEFT);
    myThreadCountAxis.clampToMajorTicks(true);


    myLegends = new LegendComponentModel(100);
    ArrayList<LegendData> legends = new ArrayList<>();
    legends.add(new LegendData(thisCpuSeries, CPU_USAGE_FORMATTER, dataRange));
    legends.add(new LegendData(otherCpuSeries, CPU_USAGE_FORMATTER, dataRange));
    legends.add(new LegendData(threadsCountSeries, NUM_THREADS_AXIS, dataRange));
    myLegends.setLegendData(legends);

    // Create an event representing the traces within the range.
    myTraceDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, getCpuTraceDataSeries()));
    myThreadsStates = new CpuThreadsModel(viewRange, this, getStudioProfilers().getProcessId());

    myEventMonitor = new EventMonitor(profilers);
  }

  public AxisComponentModel getCpuUsageAxis() {
    return myCpuUsageAxis;
  }

  public AxisComponentModel getThreadCountAxis() {
    return myThreadCountAxis;
  }

  public LineChartModel getCpuUsage() {
    return myCpuUsage;
  }

  public LegendComponentModel getLegends() {
    return myLegends;
  }

  public DurationDataModel<CpuCapture> getTraceDurations() {
    return myTraceDurations;
  }

  public String getName() {
    return myMonitor.getName();
  }

  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  @Override
  public void enter() {
    myMonitor.enter();
    myEventMonitor.enter();
    getStudioProfilers().getUpdater().register(myCpuUsage);
    getStudioProfilers().getUpdater().register(myTraceDurations);
    getStudioProfilers().getUpdater().register(myCpuUsageAxis);
    getStudioProfilers().getUpdater().register(myThreadCountAxis);
    getStudioProfilers().getUpdater().register(myLegends);
    getStudioProfilers().getUpdater().register(myThreadsStates);
  }

  @Override
  public void exit() {
    myMonitor.exit();
    myEventMonitor.exit();
    getStudioProfilers().getUpdater().unregister(myCpuUsage);
    getStudioProfilers().getUpdater().unregister(myTraceDurations);
    getStudioProfilers().getUpdater().unregister(myCpuUsageAxis);
    getStudioProfilers().getUpdater().unregister(myThreadCountAxis);
    getStudioProfilers().getUpdater().unregister(myLegends);
    getStudioProfilers().getUpdater().unregister(myThreadsStates);
  }

  @Override
  public ProfilerMode getProfilerMode() {
    return myCapture == null ? ProfilerMode.NORMAL : ProfilerMode.EXPANDED;
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
      LOG.warn("Unable to start tracing: " + response.getStatus());
      LOG.warn(response.getErrorMessage());
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
      LOG.warn("Unable to stop tracing: " + response.getStatus());
      LOG.warn(response.getErrorMessage());
    }
    else {
      try {
        capture = new CpuCapture(response.getTrace());
        // Force parsing the capture. TODO: avoid parsing the capture twice.
        getCapture(response.getTraceId());
      } catch (IllegalStateException e) {
        LOG.warn("Unable to parse capture: " + e.getMessage());
      }
    }
    if (capture != null) {
      setCapture(capture);
      setSelectedThread(capture.getMainThreadId());
    }
    myCapturing = false;
  }

  public void setCapture(@NotNull CpuCapture capture) {
    myCapture = capture;
    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    timeline.setStreaming(false);
    timeline.getSelectionRange().set(myCapture.getRange());
    getStudioProfilers().modeChanged();
    myAspect.changed(CpuProfilerAspect.CAPTURE);
  }

  public int getSelectedThread() {
    return mySelectedThread;
  }

  public void setSelectedThread(int id) {
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
  public CpuThreadsModel getThreadStates() {
    return myThreadsStates;
  }

  public CpuCapture getCapture(int traceId) {
    CpuCapture capture = myTraceCaptures.get(traceId);
    if (capture == null) {
      CpuProfiler.GetTraceRequest request = CpuProfiler.GetTraceRequest.newBuilder()
        .setAppId(getStudioProfilers().getProcessId())
        .setTraceId(traceId)
        .build();
      CpuProfiler.GetTraceResponse trace = myCpuService.getTrace(request);
      if (trace.getStatus() == CpuProfiler.GetTraceResponse.Status.SUCCESS) {
        capture = new CpuCapture(trace.getData());
      }
      myTraceCaptures.put(traceId, capture);
    }
    return capture;
  }

  // TODO: add tests for CpuTraceDataSeries. Consider moving it to its own class as the others CPU-related DataSeries.
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
        CpuCapture capture = getCapture(traceInfo.getTraceId());
        Range range = capture.getRange();

        seriesData.add(new SeriesData<>((long)range.getMin(), capture));
      }
      return ContainerUtil.immutableList(seriesData);
    }
  }
}
