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
package com.android.tools.profilers.customevent;

import static com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_LEGEND_RIGHT_PADDING;
import static com.android.tools.profilers.ProfilerLayout.Y_AXIS_TOP_MARGIN;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerMonitorView;
import com.android.tools.profilers.StudioProfilersView;
import com.intellij.ui.components.JBPanel;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class CustomEventMonitorView extends ProfilerMonitorView<CustomEventMonitor> {

  public CustomEventMonitorView(@NotNull StudioProfilersView profilersView, @NotNull CustomEventMonitor monitor) {
    super(profilersView, monitor);
  }

  @Override
  protected void populateUi(JPanel container) {
    // Current Monitor View contains a state chart to show user event counts and a legend
    container.setLayout(new TabularLayout("*", "*"));
    container.setFocusable(true);

    final JLabel label = new JLabel(getMonitor().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(JLabel.TOP);
    label.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);

    // Legend Panel
    CustomEventMonitor.CustomEventMonitorLegend legends = getMonitor().getLegend();
    LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(MONITOR_LEGEND_RIGHT_PADDING).build();
    legend.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
    // Color here does not matter, since the legend icon is none.
    legend.configure(legends.getUsageLegend(), new LegendConfig(LegendConfig.IconType.NONE, ProfilerColors.USER_COUNTER_EVENT_LIGHT));

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    // State Chart Panel
    StateChart<Long> stateChart = UserCounterStateChartFactory.create(getMonitor().getEventModel());
    JPanel stateChartPanel = new JBPanel(new BorderLayout());
    stateChartPanel.setOpaque(false);
    stateChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    stateChartPanel.add(stateChart, BorderLayout.CENTER);

    container.add(legendPanel, new TabularLayout.Constraint(0, 0));
    container.add(stateChartPanel, new TabularLayout.Constraint(0, 0));
  }

  @Override
  public float getVerticalWeight() {
    // Make Custom Event Monitor half the size of the regular monitors.
    return 0.5f;
  }
}
