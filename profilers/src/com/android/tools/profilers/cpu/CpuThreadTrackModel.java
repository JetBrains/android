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
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisChartModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;

/**
 * Track model for CPU threads in CPU capture stage. Consists of thread states and trace events.
 */
public class CpuThreadTrackModel implements CpuAnalyzable<CpuThreadTrackModel> {
  private final StateChartModel<CpuProfilerStage.ThreadState> myThreadStateChartModel;
  private final CaptureDetails.CallChart myCallChartModel;
  private final CpuCapture myCapture;
  private final Range mySelectionRange;
  private final CpuThreadInfo myThreadInfo;

  public CpuThreadTrackModel(@NotNull DataSeries<CpuProfilerStage.ThreadState> threadStateDataSeries,
                             @NotNull Range range,
                             @NotNull CpuCapture capture,
                             @NotNull CpuThreadInfo threadInfo) {
    myThreadStateChartModel = new StateChartModel<>();
    myThreadStateChartModel.addSeries(new RangedSeries<>(range, threadStateDataSeries));

    myCallChartModel = new CaptureDetails.CallChart(range, capture.getCaptureNode(threadInfo.getId()), capture);
    myCapture = capture;
    mySelectionRange = range;
    myThreadInfo = threadInfo;
  }

  @NotNull
  public StateChartModel<CpuProfilerStage.ThreadState> getThreadStateChartModel() {
    return myThreadStateChartModel;
  }

  @NotNull
  public CaptureDetails.CallChart getCallChartModel() {
    return myCallChartModel;
  }

  @NotNull
  public Range getCaptureRange() {
    return myCapture.getRange();
  }

  @NotNull
  @Override
  public CpuAnalysisModel<CpuThreadTrackModel> getAnalysisModel() {
    CpuAnalysisChartModel<CpuThreadTrackModel> flameChart =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.FLAME_CHART, mySelectionRange, myCapture, CpuThreadTrackModel::getCaptureNode);
    CpuAnalysisChartModel<CpuThreadTrackModel> topDown =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.TOP_DOWN, mySelectionRange, myCapture, CpuThreadTrackModel::getCaptureNode);
    CpuAnalysisChartModel<CpuThreadTrackModel> bottomUp =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.BOTTOM_UP, mySelectionRange, myCapture, CpuThreadTrackModel::getCaptureNode);
    flameChart.getDataSeries().add(this);
    topDown.getDataSeries().add(this);
    bottomUp.getDataSeries().add(this);

    CpuAnalysisModel<CpuThreadTrackModel> model = new CpuAnalysisModel<>(myThreadInfo.getName(), "%d threads");
    model.addTabModel(new CpuAnalysisTabModel<>(CpuAnalysisTabModel.Type.SUMMARY));
    model.addTabModel(flameChart);
    model.addTabModel(topDown);
    model.addTabModel(bottomUp);
    return model;
  }

  private Collection<CaptureNode> getCaptureNode() {
    assert myCapture.containsThread(myThreadInfo.getId());
    return Collections.singleton(myCapture.getCaptureNode(myThreadInfo.getId()));
  }
}
