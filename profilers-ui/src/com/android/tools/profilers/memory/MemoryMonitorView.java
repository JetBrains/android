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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerMonitorView;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.android.tools.profilers.ProfilerLayout.*;

public class MemoryMonitorView extends ProfilerMonitorView {

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 2, 5);

  @NotNull private final MemoryMonitor myMonitor;

  public MemoryMonitorView(@NotNull MemoryMonitor monitor) {
    myMonitor = monitor;
  }

  @Override
  protected void populateUi(JPanel container, Choreographer choreographer) {
    container.setLayout(new GridBagLayout());

    final JLabel label = new JLabel(myMonitor.getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(JLabel.TOP);
    final Dimension labelSize = label.getPreferredSize();

    Range leftYRange = new Range();
    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    AxisComponent.Builder builder = new AxisComponent.Builder(leftYRange, MEMORY_AXIS_FORMATTER,
                                                              AxisComponent.AxisOrientation.RIGHT)
      .showAxisLine(false)
      .showMax(true)
      .showUnitAtMax(true)
      .clampToMajorTicks(true)
      .setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
      .setMargins(0, labelSize.height);
    final AxisComponent leftAxis = builder.build();
    choreographer.register(leftAxis);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(labelSize.height, 0, 0, 0));
    final LineChart lineChart = new LineChart();
    lineChart.addLine(new RangedContinuousSeries("Memory", myMonitor.getViewRange(), leftYRange, myMonitor.getTotalMemory()),
                      new LineConfig(ProfilerColors.TOTAL_MEMORY).setFilled(true));
    choreographer.register(lineChart);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final LegendComponent legend = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, LEGEND_UPDATE_FREQUENCY_MS);
    legend.setLegendData(lineChart.getLegendDataFromLineChart());
    choreographer.register(legend);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    container.add(legendPanel, GBC_FULL);
    container.add(leftAxis, GBC_FULL);
    container.add(lineChartPanel, GBC_FULL);
    container.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        myMonitor.expand();
      }
    });
  }
}
