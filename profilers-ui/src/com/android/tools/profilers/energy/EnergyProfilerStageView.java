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

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitorView;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.profilers.ProfilerLayout.*;

public class EnergyProfilerStageView extends StageView<EnergyProfilerStage> {

  @NotNull private final EnergyStageTooltipView myTooltipView = new EnergyStageTooltipView(getStage());

  public EnergyProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull EnergyProfilerStage energyProfilerStage) {
    super(profilersView, energyProfilerStage);
    getComponent().add(buildMonitorUi(), BorderLayout.CENTER);
  }

  @NotNull
  private JPanel buildMonitorUi() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();

    TabularLayout layout = new TabularLayout("*");
    JPanel panel = new JBPanel(layout);
    panel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar scrollbar = new ProfilerScrollbar(timeline, panel);
    panel.add(scrollbar, new TabularLayout.Constraint(4, 0));

    JComponent timeAxis = buildTimeAxis(profilers);
    panel.add(timeAxis, new TabularLayout.Constraint(3, 0));

    EventMonitorView eventsView = new EventMonitorView(getProfilersView(), getStage().getEventMonitor());
    JComponent eventsComponent = eventsView.getComponent();
    panel.add(eventsComponent, new TabularLayout.Constraint(0, 0));

    JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JLabel label = new JLabel(getStage().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    DetailedEnergyUsage usage = getStage().getDetailedUsage();

    final LineChart lineChart = new LineChart(usage);

    LineConfig cpuConfig =
      new LineConfig(ProfilerColors.ENERGY_CPU).setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX);
    lineChart.configure(usage.getCpuUsageSeries(), cpuConfig);
    LineConfig networkConfig =
      new LineConfig(ProfilerColors.ENERGY_NETWORK).setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX);
    lineChart.configure(usage.getNetworkUsageSeries(), networkConfig);
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    RangeTooltipComponent tooltip =
      new RangeTooltipComponent(timeline.getTooltipRange(),
                                timeline.getViewRange(),
                                timeline.getDataRange(),
                                myTooltipView.createComponent(),
                                ProfilerLayeredPane.class);

    tooltip.registerListenersOn(lineChart);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent leftAxis = new AxisComponent(getStage().getAxis(), AxisComponent.AxisOrientation.RIGHT);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowMax(true);
    leftAxis.setShowUnitAtMax(true);
    leftAxis.setHideTickAtMin(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    EnergyProfilerStage.EnergyLegends legends = getStage().getLegends();
    LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(PROFILER_LEGEND_RIGHT_PADDING).build();
    legend.configure(legends.getCpuLegend(), new LegendConfig(lineChart.getLineConfig(usage.getCpuUsageSeries())));
    legend.configure(legends.getNetworkLegend(), new LegendConfig(lineChart.getLineConfig(usage.getNetworkUsageSeries())));

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    monitorPanel.add(tooltip, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));
    layout.setRowSizing(2, "*"); // Give as much space as possible to the main monitor panel
    panel.add(monitorPanel, new TabularLayout.Constraint(2, 0));

    return panel;
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }
}
