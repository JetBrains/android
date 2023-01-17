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

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.ProfilerMonitorView;
import com.android.tools.profilers.StudioProfilersView;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.profilers.ProfilerLayout.*;

public class CpuMonitorView extends ProfilerMonitorView<CpuMonitor> {

  public CpuMonitorView(@NotNull StudioProfilersView profilersView, @NotNull CpuMonitor monitor) {
    super(monitor);
  }

  @Override
  protected void populateUi(JPanel container) {
    container.setLayout(new TabularLayout("*", "*"));
    container.setFocusable(true);

    final JLabel label = new JLabel(getMonitor().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(JLabel.TOP);
    label.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent leftAxis = new AxisComponent(getMonitor().getCpuUsageAxis(), AxisComponent.AxisOrientation.RIGHT, true);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowMax(true);
    leftAxis.setOnlyShowUnitAtMax(false);
    leftAxis.setHideTickAtMin(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));

    CpuUsage cpuUsage = getMonitor().getThisProcessCpuUsage();
    final LineChart lineChart = new LineChart(cpuUsage);
    lineChart.setMaxLineColor(ProfilerColors.MONITOR_MAX_LINE);
    lineChart.setMaxLineMargin(40);
    lineChart.setFillEndGap(true);
    getMonitor().addDependency(this).onChange(ProfilerMonitor.Aspect.FOCUS, () -> lineChart.setShowMaxLine(getMonitor().isFocused()));

    LineConfig config = new LineConfig(ProfilerColors.CPU_USAGE).setFilled(true).setLegendIconType(LegendConfig.IconType.NONE);
    lineChart.configure(cpuUsage.getCpuSeries(), config);
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    CpuMonitor.Legends legends = getMonitor().getLegends();
    LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(MONITOR_LEGEND_RIGHT_PADDING).build();
    legend.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
    legend.configure(legends.getCpuLegend(), new LegendConfig(config));

    JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    container.add(legendPanel, new TabularLayout.Constraint(0, 0));
    container.add(leftAxis, new TabularLayout.Constraint(0, 0));
    container.add(lineChartPanel, new TabularLayout.Constraint(0, 0));
  }
}
