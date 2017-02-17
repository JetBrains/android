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
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.*;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.event.EventMonitor;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class CpuProfilerStage extends Stage {

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 5, 10, "%");
  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(1, 5, 1, "");

  private final CpuThreadsModel myThreadsStates;
  private final AxisComponentModel myCpuUsageAxis;
  private final AxisComponentModel myThreadCountAxis;
  private final DetailedCpuUsage myCpuUsage;
  private final CpuStageLegends myLegends;
  private final DurationDataModel<CpuCapture> myTraceDurations;
  private final EventMonitor myEventMonitor;
  private final SelectionModel mySelectionModel;

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

  public enum CaptureState {
    // Waiting for a capture to start (displaying the current capture or not)
    IDLE,
    // There is a capture in progress
    CAPTURING,
    // A capture is being parsed
    PARSING,
    // Waiting for the service to respond a start capturing call
    STARTING,
    // Waiting for the service to respond a stop capturing call
    STOPPING,
  }

  private static final Logger LOG = Logger.getInstance(CpuProfilerStage.class);

  @NotNull
  private final CpuTraceDataSeries myCpuTraceDataSeries;

  private AspectModel<CpuProfilerAspect> myAspect = new AspectModel<>();

  /**
   * The current capture.
   */
  @Nullable
  private CpuCapture myCapture;

  /**
   * Represents the current state of the capture.
   * TODO: instead of keeping/setting all these state here, we might want to get the current state from perfd.
   */
  private CaptureState myCaptureState;

  /**
   * Id of the current selected thread.
   */
  private int mySelectedThread;

  @NotNull
  private CpuProfiler.CpuProfilingAppStartRequest.Mode myProfilingMode;

  private int myProfilingBufferSizeInMb = 8;  // TODO: Make it configurable.

  private int myProfilingSamplingIntervalUs = 1000;  // TODO: Make it configurable.

  /**
   * A cache of already parsed captures, indexed by trace_id.
   */
  private Map<Integer, CpuCapture> myTraceCaptures = new HashMap<>();

  @Nullable
  private CaptureDetails myCaptureDetails;

  @Nullable
  private CodeLocation myCodeLocation;

  public CpuProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myCpuTraceDataSeries = new CpuTraceDataSeries();

    Range viewRange = getStudioProfilers().getTimeline().getViewRange();
    Range dataRange = getStudioProfilers().getTimeline().getDataRange();
    Range selectionRange = getStudioProfilers().getTimeline().getSelectionRange();

    myCpuUsage = new DetailedCpuUsage(profilers);

    myCpuUsageAxis = new AxisComponentModel(myCpuUsage.getCpuRange(), CPU_USAGE_FORMATTER);
    myCpuUsageAxis.setClampToMajorTicks(true);

    myThreadCountAxis = new AxisComponentModel(myCpuUsage.getThreadRange(), NUM_THREADS_AXIS);
    myThreadCountAxis.setClampToMajorTicks(true);

    myLegends = new CpuStageLegends(myCpuUsage, dataRange);

    // Create an event representing the traces within the range.
    myTraceDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, getCpuTraceDataSeries()));
    myThreadsStates = new CpuThreadsModel(viewRange, this, getStudioProfilers().getProcessId(), getStudioProfilers().getSession());

    myEventMonitor = new EventMonitor(profilers);
    myCaptureState = CaptureState.IDLE;
    myProfilingMode = CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED;

    mySelectionModel = new SelectionModel(selectionRange, viewRange);
    mySelectionModel.addConstraint(myTraceDurations);
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  public AxisComponentModel getCpuUsageAxis() {
    return myCpuUsageAxis;
  }

  public AxisComponentModel getThreadCountAxis() {
    return myThreadCountAxis;
  }

  public DetailedCpuUsage getCpuUsage() {
    return myCpuUsage;
  }

  public CpuStageLegends getLegends() {
    return myLegends;
  }

  public DurationDataModel<CpuCapture> getTraceDurations() {
    return myTraceDurations;
  }

  public String getName() {
    return "CPU";
  }

  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  @Override
  public void enter() {
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
    return myCodeLocation != null || myCapture == null ? ProfilerMode.NORMAL : ProfilerMode.EXPANDED;
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myAspect;
  }

  public void startCapturing() {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
    CpuProfiler.CpuProfilingAppStartRequest request = CpuProfiler.CpuProfilingAppStartRequest.newBuilder()
      .setAppPkgName(getStudioProfilers().getProcess().getName()) // TODO: Investigate if this is the right way of choosing the app
      .setSession(getStudioProfilers().getSession())
      .setMode(myProfilingMode)
      .setProfiler(CpuProfiler.CpuProfilingAppStartRequest.Profiler.ART) // TODO: support simpleperf
      .setBufferSizeInMb(myProfilingBufferSizeInMb)
      .setSamplingIntervalUs(myProfilingSamplingIntervalUs)
      .build();

    setCaptureState(CaptureState.STARTING);
    CompletableFuture.supplyAsync(
      () -> cpuService.startProfilingApp(request), getStudioProfilers().getIdeServices().getPoolExecutor())
      .thenAcceptAsync(this::startCapturingCallback, getStudioProfilers().getIdeServices().getMainExecutor());
  }

  private void startCapturingCallback(CpuProfiler.CpuProfilingAppStartResponse response) {
    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS)) {
      LOG.warn("Unable to start tracing: " + response.getStatus());
      LOG.warn(response.getErrorMessage());
      setCaptureState(CaptureState.IDLE);
    }
    else {
      setCaptureState(CaptureState.CAPTURING);
    }
  }

  public void stopCapturing() {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
    CpuProfiler.CpuProfilingAppStopRequest request = CpuProfiler.CpuProfilingAppStopRequest.newBuilder()
      .setAppPkgName(getStudioProfilers().getProcess().getName()) // TODO: Investigate if this is the right way of choosing the app
      .setProfiler(CpuProfiler.CpuProfilingAppStopRequest.Profiler.ART) // TODO: support simpleperf
      .setSession(getStudioProfilers().getSession())
      .build();

    setCaptureState(CaptureState.STOPPING);
    CompletableFuture.supplyAsync(
      () -> cpuService.stopProfilingApp(request), getStudioProfilers().getIdeServices().getPoolExecutor())
      .thenAcceptAsync(this::stopCapturingCallback, getStudioProfilers().getIdeServices().getMainExecutor());
  }

  private void stopCapturingCallback(CpuProfiler.CpuProfilingAppStopResponse response) {
    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS)) {
      LOG.warn("Unable to stop tracing: " + response.getStatus());
      LOG.warn(response.getErrorMessage());
      setCaptureState(CaptureState.IDLE);
    }
    else {
      setCaptureState(CaptureState.PARSING);
      CompletableFuture.supplyAsync(() -> new CpuCapture(response.getTrace()), getStudioProfilers().getIdeServices().getPoolExecutor())
        .handleAsync((capture, exception) -> {
          if (capture != null) {
            myTraceCaptures.put(response.getTraceId(), capture);
            // Intentionally not firing the aspect because it will be done by setCapture with the new capture value
            myCaptureState = CaptureState.IDLE;
            setAndSelectCapture(capture);
            setSelectedThread(capture.getMainThreadId());
          }
          else {
            assert exception != null;
            LOG.warn("Unable to parse capture: " + exception.getMessage());
            // Intentionally not firing the aspect because it will be done by setCapture with the new capture value
            myCaptureState = CaptureState.IDLE;
            setCapture(null);
          }
          return capture;
        }, getStudioProfilers().getIdeServices().getMainExecutor());
    }
  }

  public void setCapture(@Nullable CpuCapture capture) {
    myCapture = capture;
    if (capture != null) {
      setCaptureDetails(myCaptureDetails != null ? myCaptureDetails.getType() : CaptureDetails.Type.TOP_DOWN);
      getStudioProfilers().modeChanged();
    }
    myAspect.changed(CpuProfilerAspect.CAPTURE);
  }

  public void setAndSelectCapture(@Nullable CpuCapture capture) {
    if (capture != null) {
      ProfilerTimeline timeline = getStudioProfilers().getTimeline();
      timeline.getSelectionRange().set(capture.getRange());
    }
    setCapture(capture);
  }

  public int getSelectedThread() {
    return mySelectedThread;
  }

  public void setSelectedThread(int id) {
    mySelectedThread = id;
    setCaptureDetails(myCaptureDetails != null ? myCaptureDetails.getType() : CaptureDetails.Type.TOP_DOWN);
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

  public CaptureState getCaptureState() {
    return myCaptureState;
  }

  public void setCaptureState(CaptureState captureState) {
    myCaptureState = captureState;
    myAspect.changed(CpuProfilerAspect.CAPTURE);
  }

  @NotNull
  public CpuProfiler.CpuProfilingAppStartRequest.Mode getProfilingMode() {
    return myProfilingMode;
  }

  public void setProfilingMode(@NotNull CpuProfiler.CpuProfilingAppStartRequest.Mode mode) {
    myProfilingMode = mode;
    myAspect.changed(CpuProfilerAspect.PROFILING_MODE);
  }

  @NotNull
  public ImmutableList<CpuProfiler.CpuProfilingAppStartRequest.Mode> getProfilingModes() {
    return ContainerUtil.immutableList(CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED,
                                       CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED);
  }

  @NotNull
  public CpuTraceDataSeries getCpuTraceDataSeries() {
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
        .setProcessId(getStudioProfilers().getProcessId())
        .setSession(getStudioProfilers().getSession())
        .setTraceId(traceId)
        .build();
      CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
      CpuProfiler.GetTraceResponse trace = cpuService.getTrace(request);
      if (trace.getStatus() == CpuProfiler.GetTraceResponse.Status.SUCCESS) {
        // TODO: move this parsing to a separate thread
        try {
          capture = new CpuCapture(trace.getData());
        }
        catch (IllegalStateException e) {
          // Don't crash studio if parsing fails.
        }
      }
      // TODO: Limit how many captures we keep parsed in memory
      myTraceCaptures.put(traceId, capture);
    }
    return capture;
  }

  public void setCaptureDetails(@Nullable CaptureDetails.Type type) {
    if (type != null) {
      Range range = getStudioProfilers().getTimeline().getSelectionRange();
      HNode<MethodModel> node = myCapture != null ? myCapture.getCaptureNode(getSelectedThread()) : null;
      myCaptureDetails = type.build(range, node);
    }
    else {
      myCaptureDetails = null;
    }

    setCodeLocation(null);
    myAspect.changed(CpuProfilerAspect.CAPTURE_DETAILS);
  }

  @Nullable
  public CaptureDetails getCaptureDetails() {
    return myCaptureDetails;
  }

  public void setCodeLocation(@Nullable CodeLocation location) {
    myCodeLocation = location;
    getStudioProfilers().modeChanged();
  }

  public interface CaptureDetails {
    enum Type {
      TOP_DOWN(TopDown::new),
      BOTTOM_UP(BottomUp::new),
      CHART(TreeChart::new);

      @NotNull
      private final BiFunction<Range, HNode<MethodModel>, CaptureDetails> myBuilder;

      Type(@NotNull BiFunction<Range, HNode<MethodModel>, CaptureDetails> builder) {
        myBuilder = builder;
      }

      public CaptureDetails build(Range range, HNode<MethodModel> node) {
        return myBuilder.apply(range, node);
      }
    }

    Type getType();
  }

  public static class TopDown implements CaptureDetails {
    @Nullable private TopDownTreeModel myModel;

    public TopDown(@NotNull Range range, @Nullable HNode<MethodModel> node) {
      myModel = node == null ? null : new TopDownTreeModel(range, new TopDownNode(node));
    }

    @Nullable
    public TopDownTreeModel getModel() {
      return myModel;
    }

    @Override
    public Type getType() {
      return Type.TOP_DOWN;
    }
  }

  public static class BottomUp implements CaptureDetails {
    @Nullable private BottomUpTreeModel myModel;

    public BottomUp(@NotNull Range range, @Nullable HNode<MethodModel> node) {
      myModel = node == null ? null : new BottomUpTreeModel(range, new BottomUpNode(node));
    }

    @Nullable
    public BottomUpTreeModel getModel() {
      return myModel;
    }

    @Override
    public Type getType() {
      return Type.BOTTOM_UP;
    }
  }

  public static class TreeChart implements CaptureDetails {
    @NotNull private final Range myRange;
    @Nullable private HNode<MethodModel> myNode;

    public TreeChart(@NotNull Range range, @Nullable HNode<MethodModel> node) {
      myRange = range;
      myNode = node;
    }

    @NotNull
    public Range getRange() {
      return myRange;
    }

    @Nullable
    public HNode<MethodModel> getNode() {
      return myNode;
    }

    @Override
    public Type getType() {
      return Type.CHART;
    }
  }

  @VisibleForTesting
  class CpuTraceDataSeries implements DataSeries<CpuCapture> {
    @Override
    public ImmutableList<SeriesData<CpuCapture>> getDataForXRange(Range xRange) {
      long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
      long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());

      CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
      CpuProfiler.GetTraceInfoResponse response = cpuService.getTraceInfo(
        CpuProfiler.GetTraceInfoRequest.newBuilder().
          setProcessId(getStudioProfilers().getProcessId()).
          setSession(getStudioProfilers().getSession()).
          setFromTimestamp(rangeMin).setToTimestamp(rangeMax).build());

      List<SeriesData<CpuCapture>> seriesData = new ArrayList<>();
      for (CpuProfiler.TraceInfo traceInfo : response.getTraceInfoList()) {
        CpuCapture capture = getCapture(traceInfo.getTraceId());
        if (capture != null) {
          Range range = capture.getRange();
          seriesData.add(new SeriesData<>((long)range.getMin(), capture));
        }
      }
      return ContainerUtil.immutableList(seriesData);
    }
  }

  public static class CpuStageLegends extends LegendComponentModel {

    @NotNull private final SeriesLegend myCpuLegend;
    @NotNull private final SeriesLegend myOthersLegend;
    @NotNull private final SeriesLegend myThreadsLegend;

    public CpuStageLegends(@NotNull DetailedCpuUsage cpuUsage, @NotNull Range dataRange) {
      super(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
      myCpuLegend = new SeriesLegend(cpuUsage.getCpuSeries(), CPU_USAGE_FORMATTER, dataRange);
      myOthersLegend = new SeriesLegend(cpuUsage.getOtherCpuSeries(), CPU_USAGE_FORMATTER, dataRange);
      myThreadsLegend = new SeriesLegend(cpuUsage.getThreadsCountSeries(), NUM_THREADS_AXIS, dataRange);
      add(myCpuLegend);
      add(myOthersLegend);
      add(myThreadsLegend);
    }

    @NotNull
    public SeriesLegend getCpuLegend() {
      return myCpuLegend;
    }

    @NotNull
    public SeriesLegend getOthersLegend() {
      return myOthersLegend;
    }

    @NotNull
    public SeriesLegend getThreadsLegend() {
      return myThreadsLegend;
    }
  }
}
