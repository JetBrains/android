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
import java.util.Collections;

import static com.android.tools.profilers.ProfilerLayout.*;

public class MemoryMonitorView extends ProfilerMonitorView<MemoryMonitor> {

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 2, 5);

  public MemoryMonitorView(@NotNull MemoryMonitor monitor) {
    super(monitor);
  }

  @Override
  protected void populateUi(JPanel container, Choreographer choreographer) {
    container.setLayout(new GridBagLayout());

    Range viewRange = getMonitor().getTimeline().getViewRange();
    Range dataRange = getMonitor().getTimeline().getDataRange();

    final JLabel label = new JLabel(getMonitor().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(JLabel.TOP);

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
      .setMargins(0, Y_AXIS_TOP_MARGIN);
    final AxisComponent leftAxis = builder.build();
    axisPanel.add(leftAxis, BorderLayout.WEST);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final LineChart lineChart = new LineChart();
    RangedContinuousSeries memSeries = new RangedContinuousSeries("Memory", viewRange, leftYRange, getMonitor().getTotalMemory());
    lineChart.addLine(memSeries, new LineConfig(ProfilerColors.MEMORY_TOTAL).setFilled(true));
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final LegendComponent legend = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, LEGEND_UPDATE_FREQUENCY_MS);
    legend.setLegendData(Collections.singletonList(lineChart.createLegendRenderData(memSeries, MEMORY_AXIS_FORMATTER, dataRange)));

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    choreographer.register(lineChart);
    choreographer.register(leftAxis);
    choreographer.register(legend);

    container.add(legendPanel, GBC_FULL);
    container.add(leftAxis, GBC_FULL);
    container.add(lineChartPanel, GBC_FULL);
    container.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        getMonitor().expand();
      }
    });
  }
}
