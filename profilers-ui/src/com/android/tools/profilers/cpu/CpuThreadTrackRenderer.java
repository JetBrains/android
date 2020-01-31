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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.chart.statechart.StateChartColorProvider;
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.trackgroup.TrackRenderer;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.cpu.capturedetails.CaptureNodeHRenderer;
import java.awt.Color;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Track renderer for CPU threads in CPU capture stage.
 */
public class CpuThreadTrackRenderer implements TrackRenderer<CpuThreadTrackModel, ProfilerTrackRendererType> {
  @NotNull
  @Override
  public JComponent render(@NotNull TrackModel<CpuThreadTrackModel, ProfilerTrackRendererType> trackModel) {
    StateChart<CpuProfilerStage.ThreadState> threadStateChart = createStateChart(trackModel.getDataModel().getThreadStateChartModel());
    HTreeChart<CaptureNode> traceEventChart = createHChart(trackModel.getDataModel().getCallChartModel(),
                                                           trackModel.getDataModel().getCaptureRange());

    JPanel panel = new JPanel(new TabularLayout("*", "8px,*"));
    panel.add(threadStateChart, new TabularLayout.Constraint(0, 0));
    panel.add(traceEventChart, new TabularLayout.Constraint(1, 0));
    return panel;
  }

  private static StateChart<CpuProfilerStage.ThreadState> createStateChart(@NotNull StateChartModel<CpuProfilerStage.ThreadState> model) {
    StateChart<CpuProfilerStage.ThreadState> threadStateChart = new StateChart<>(model, new CpuThreadColorProvider());
    threadStateChart.setHeightGap(0.0f);
    return threadStateChart;
  }

  private static HTreeChart<CaptureNode> createHChart(@NotNull CaptureDetails.CallChart callChartModel,
                                                      @NotNull Range captureRange) {
    CaptureNode node = callChartModel.getNode();
    Range selectionRange = callChartModel.getRange();

    HTreeChart<CaptureNode> chart = new HTreeChart.Builder<>(node, selectionRange, new CaptureNodeHRenderer(CaptureDetails.Type.CALL_CHART))
      .setGlobalXRange(captureRange)
      .setOrientation(HTreeChart.Orientation.TOP_DOWN)
      .setRootVisible(false)
      .build();
    return chart;
  }

  private static class CpuThreadColorProvider extends StateChartColorProvider<CpuProfilerStage.ThreadState> {
    private EnumColors<CpuProfilerStage.ThreadState> myEnumColors = ProfilerColors.THREAD_STATES.build();

    @NotNull
    @Override
    public Color getColor(boolean isMouseOver, @NotNull CpuProfilerStage.ThreadState value) {
      myEnumColors.setColorIndex(isMouseOver ? 1 : 0);
      return myEnumColors.getColor(value);
    }
  }
}
