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

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.RangeSelectionComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.event.DelegateMouseEventHandler;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayeredPane;
import com.android.tools.profilers.ProfilerLayout;
import com.intellij.ui.components.JBPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * View for navigating track groups in the {@link CpuCaptureStageView}. Contains CPU Usage chart and enables range-selection.
 */
public class CpuCaptureMinimapView {
  private final JPanel myPanel;

  public CpuCaptureMinimapView(@NotNull CpuCaptureMinimapModel model) {
    myPanel = new JPanel(new TabularLayout("*", "60px"));
    RangeSelectionComponent rangeSelectionComponent = createRangeSelectionComponent(model.getRangeSelectionModel());

    // Order is important
    myPanel.add(createOverlayComponent(rangeSelectionComponent), new TabularLayout.Constraint(0, 0));
    myPanel.add(rangeSelectionComponent, new TabularLayout.Constraint(0, 0));
    myPanel.add(createLineChartPanel(model.getCpuUsage()), new TabularLayout.Constraint(0, 0));
    myPanel.add(createAxisPanel(model.getMaxRange()), new TabularLayout.Constraint(1, 0));

    myPanel.setBorder(ProfilerLayout.MONITOR_BORDER);
    myPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  private JComponent createOverlayComponent(@NotNull JComponent component) {
    OverlayComponent overlayComponent = new OverlayComponent(component);
    DelegateMouseEventHandler.delegateTo(getComponent()).installListenerOn(overlayComponent).installMotionListenerOn(overlayComponent);
    return overlayComponent;
  }

  private static RangeSelectionComponent createRangeSelectionComponent(@NotNull RangeSelectionModel model) {
    // Copy capture range as view range
    RangeSelectionComponent rangeSelectionComponent = new RangeSelectionComponent(model, new Range(model.getSelectionRange()), true);
    rangeSelectionComponent.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);
    return rangeSelectionComponent;
  }

  private static JComponent createLineChartPanel(@NotNull CpuUsage cpuUsage) {
    JPanel lineChartPanel = new JBPanel<>(new BorderLayout());
    lineChartPanel.setOpaque(false);

    LineChart lineChart = new LineChart(cpuUsage);
    lineChart.configure(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE).setFilled(true));
    lineChart.setFillEndGap(true);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    return lineChartPanel;
  }

  private static JComponent createAxisPanel(@NotNull Range cpuUsageRange) {
    JPanel axisPanel = new JPanel(new BorderLayout());
    axisPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    AxisComponent timeAxis = new AxisComponent(
      new ResizingAxisComponentModel.Builder(cpuUsageRange, TimeAxisFormatter.DEFAULT).setGlobalRange(cpuUsageRange).build(),
      AxisComponent.AxisOrientation.BOTTOM);
    timeAxis.setShowAxisLine(false);
    timeAxis.setMinimumSize(new Dimension(0, ProfilerLayout.TIME_AXIS_HEIGHT));
    timeAxis.setPreferredSize(new Dimension(Integer.MAX_VALUE, ProfilerLayout.TIME_AXIS_HEIGHT));
    axisPanel.add(timeAxis, BorderLayout.CENTER);
    return axisPanel;
  }
}
