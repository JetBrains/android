// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.profilers.*;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.ProfilerLayout.*;

public class EnergyMonitorView extends ProfilerMonitorView<EnergyMonitor> {
  private static final String ENERGY_PROFILER_URL = "https://developer.android.com/studio/profile/energy-profiler";

  /**
   * Energy data buckets' interval time, data unit is same as x axis which is micro seconds.
   */
  public static final double CHART_INTERVAL_US = TimeUnit.MILLISECONDS.toMicros(200);

  public EnergyMonitorView(@NotNull StudioProfilersView profilersView, @NotNull EnergyMonitor monitor) {
    super(monitor);
  }

  @Override
  protected void populateDisabledView(JPanel container) {
    container.setLayout(new TabularLayout("*,Fit-,*", "*,Fit-,Fit-,*"));
    JLabel disabledMessage = new JLabel("Energy profiler unavailable");
    disabledMessage.setFont(ProfilerFonts.H2_FONT);
    disabledMessage.setHorizontalAlignment(SwingConstants.CENTER);
    disabledMessage.setBorder(new JBEmptyBorder(0, 0, 6, 0));
    container.add(disabledMessage, new TabularLayout.Constraint(1, 0, 3));
    HyperlinkLabel detailedMessage = new HyperlinkLabel();
    detailedMessage.setFont(ProfilerFonts.STANDARD_FONT);
    detailedMessage.setHyperlinkTarget(ENERGY_PROFILER_URL);
    detailedMessage.setHyperlinkText("Supported only on devices running Android 8.0 (API level 26) and higher. ", "Learn More", "");
    container.add(detailedMessage, new TabularLayout.Constraint(2, 1));
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
    final AxisComponent leftAxis = new AxisComponent(getMonitor().getAxis(), AxisComponent.AxisOrientation.RIGHT, true);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowMax(false);
    leftAxis.setOnlyShowUnitAtMax(false);
    leftAxis.setHideTickAtMin(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));

    LineChart lineChart = new LineChart(getMonitor().getUsage());
    lineChart.setMaxLineColor(ProfilerColors.MONITOR_MAX_LINE);
    lineChart.setMaxLineMargin(40);
    lineChart.setFillEndGap(true);
    LineConfig config = new LineConfig(ProfilerColors.ENERGY_USAGE)
      .setFilled(true)
      .setLegendIconType(LegendConfig.IconType.NONE)
      .setDataBucketInterval(CHART_INTERVAL_US);
    lineChart.configure(getMonitor().getUsage().getTotalUsageDataSeries(), config);
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);
    getMonitor().addDependency(this).onChange(ProfilerMonitor.Aspect.FOCUS, () -> lineChart.setShowMaxLine(getMonitor().isFocused()));

    JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);

    EnergyMonitor.Legends legends = getMonitor().getLegends();
    LegendComponent legendComponent = new LegendComponent.Builder(legends).setRightPadding(MONITOR_LEGEND_RIGHT_PADDING).build();
    legendComponent.configure(legends.getUsageLegend(), new LegendConfig(config));
    legendPanel.add(legendComponent, BorderLayout.EAST);

    container.add(legendPanel, new TabularLayout.Constraint(0, 0));
    container.add(leftAxis, new TabularLayout.Constraint(0, 0));
    container.add(lineChartPanel, new TabularLayout.Constraint(0, 0));
  }
}
