/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.adtui.model.MultiSelectionModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisChartModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisEventsTabModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable;
import com.android.tools.profilers.cpu.analysis.CpuThreadAnalysisEventsTabModel;
import com.android.tools.profilers.cpu.analysis.CpuThreadAnalysisSummaryTabModel;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.cpu.capturedetails.CpuCaptureNodeTooltip;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Track model for CPU threads in CPU capture stage. Consists of thread states and trace events.
 */
public class CpuThreadTrackModel implements CpuAnalyzable<CpuThreadTrackModel> {
  @NotNull private final StateChartModel<ThreadState> myThreadStateChartModel;
  @NotNull private final CaptureDetails.CallChart myCallChartModel;
  @NotNull private final CpuCapture myCapture;
  @NotNull private final Timeline myTimeline;
  @NotNull private final CpuThreadInfo myThreadInfo;
  @NotNull private final CpuThreadsTooltip myThreadStateTooltip;
  @NotNull private final Function<CaptureNode, CpuCaptureNodeTooltip> myTraceEventTooltipBuilder;
  @NotNull private final MultiSelectionModel<CpuAnalyzable<?>> myMultiSelectionModel;
  @Nullable private final DataSeries<ThreadState> myThreadStateSeries;

  @NotNull private final Function1<Runnable, Unit> myRunInBackground;

  public CpuThreadTrackModel(@NotNull CpuCapture capture,
                             @NotNull CpuThreadInfo threadInfo,
                             @NotNull Timeline timeline,
                             @NotNull MultiSelectionModel<CpuAnalyzable<?>> multiSelectionModel,
                             @NotNull Function1<Runnable, Unit> runModelUpdate) {
    myThreadStateChartModel = new StateChartModel<>();
    myThreadStateTooltip = new CpuThreadsTooltip(timeline);
    // CallChart always uses wall-clock time, a.k.a. ClockType.GLOBAL
    myCallChartModel =
      new CaptureDetails.CallChart(ClockType.GLOBAL, timeline.getViewRange(),
                                   Collections.singletonList(capture.getCaptureNode(threadInfo.getId())), capture);
    myCapture = capture;
    myThreadInfo = threadInfo;
    myTimeline = timeline;
    myMultiSelectionModel = multiSelectionModel;
    myRunInBackground = runModelUpdate;

    if (capture.getSystemTraceData() != null) {
      myThreadStateSeries = new LazyDataSeries<>(() -> capture.getSystemTraceData().getThreadStatesForThread(threadInfo.getId()));
      myThreadStateChartModel.addSeries(new RangedSeries<>(timeline.getViewRange(), myThreadStateSeries));
      myThreadStateTooltip.setThread(threadInfo.getName(), myThreadStateSeries);
    }
    else {
      myThreadStateSeries = null;
    }
    myTraceEventTooltipBuilder = captureNode -> new CpuCaptureNodeTooltip(timeline, captureNode);
  }

  @NotNull
  public StateChartModel<ThreadState> getThreadStateChartModel() {
    return myThreadStateChartModel;
  }

  @NotNull
  public CaptureDetails.CallChart getCallChartModel() {
    return myCallChartModel;
  }

  @NotNull
  public CpuCapture getCapture() {
    return myCapture;
  }

  @NotNull
  @Override
  public CpuAnalysisModel<CpuThreadTrackModel> getAnalysisModel() {
    CpuAnalysisModel<CpuThreadTrackModel> model = new CpuAnalysisModel<>(myThreadInfo.getName(), "%d threads");
    Range selectionRange = myTimeline.getSelectionRange().isEmpty() ? myTimeline.getViewRange() : myTimeline.getSelectionRange();

    // Summary
    CpuThreadAnalysisSummaryTabModel summary = new CpuThreadAnalysisSummaryTabModel(myCapture.getRange(), selectionRange);
    summary.getDataSeries().add(this);
    model.addTabModel(summary);

    // Flame Chart
    CpuAnalysisChartModel<CpuThreadTrackModel> flameChart =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.FLAME_CHART, selectionRange, myCapture,
                                  CpuThreadTrackModel::getCaptureNode, myRunInBackground);
    flameChart.getDataSeries().add(this);
    model.addTabModel(flameChart);

    // Top Down
    CpuAnalysisChartModel<CpuThreadTrackModel> topDown =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.TOP_DOWN, selectionRange, myCapture,
                                  CpuThreadTrackModel::getCaptureNode, myRunInBackground);
    topDown.getDataSeries().add(this);
    model.addTabModel(topDown);

    // Bottom Up
    CpuAnalysisChartModel<CpuThreadTrackModel> bottomUp =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.BOTTOM_UP, selectionRange, myCapture,
                                  CpuThreadTrackModel::getCaptureNode, myRunInBackground);
    bottomUp.getDataSeries().add(this);
    model.addTabModel(bottomUp);

    // Events
    CpuAnalysisEventsTabModel<CpuThreadTrackModel> events = new CpuThreadAnalysisEventsTabModel(myCapture.getRange());
    events.getDataSeries().add(this);
    model.addTabModel(events);

    return model;
  }

  /**
   * @return a tooltip model for thread states.
   */
  @NotNull
  public CpuThreadsTooltip getThreadStateTooltip() {
    return myThreadStateTooltip;
  }

  /**
   * @return a function that produces a tooltip model for trace events.
   */
  @NotNull
  public Function<CaptureNode, CpuCaptureNodeTooltip> getTraceEventTooltipBuilder() {
    return myTraceEventTooltipBuilder;
  }

  @NotNull
  public MultiSelectionModel<CpuAnalyzable<?>> getMultiSelectionModel() {
    return myMultiSelectionModel;
  }

  @NotNull
  public CpuThreadInfo getThreadInfo() {
    return myThreadInfo;
  }

  @NotNull
  public Timeline getTimeline() {
    return myTimeline;
  }

  /**
   * @return data series of thread states, if the capture contains thread state data (e.g. SysTrace). Null otherwise.
   */
  @Nullable
  public DataSeries<ThreadState> getThreadStateSeries() {
    return myThreadStateSeries;
  }

  private Collection<CaptureNode> getCaptureNode() {
    assert myCapture.containsThread(myThreadInfo.getId());
    return Collections.singleton(myCapture.getCaptureNode(myThreadInfo.getId()));
  }
}
