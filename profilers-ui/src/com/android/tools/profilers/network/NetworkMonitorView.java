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
package com.android.tools.profilers.network;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.NetworkTrafficFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profilers.ProfilerMonitorView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class NetworkMonitorView extends ProfilerMonitorView {

  private static final BaseAxisFormatter BANDWIDTH_AXIS_FORMATTER_L1 = new NetworkTrafficFormatter(1, 2, 5);

  @NotNull
  private final NetworkMonitor myMonitor;

  public NetworkMonitorView(@NotNull NetworkMonitor monitor) {
    myMonitor = monitor;
  }

  @Override
  protected void populateUi(JLayeredPane container, Choreographer choreographer) {
    final JLabel label = new JLabel(myMonitor.getName());
    label.setBorder(LABEL_PADDING);
    final Dimension labelSize = label.getPreferredSize();

    Range leftYRange = new Range();
    AxisComponent.Builder builder = new AxisComponent.Builder(leftYRange, BANDWIDTH_AXIS_FORMATTER_L1,
                                                              AxisComponent.AxisOrientation.RIGHT)
      .showAxisLine(false)
      .showMax(true)
      .showUnitAtMax(true)
      .setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
      .clampToMajorTicks(true).setMargins(0, labelSize.height);
    final AxisComponent leftAxis = builder.build();

    final LineChart lineChart = new LineChart();
    lineChart.addLine(new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED.getLabel(),
                                                 myMonitor.getViewRange(),
                                                 leftYRange,
                                                 myMonitor.getSpeedSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED)));
    lineChart.addLine(new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_SENT.getLabel(),
                                                 myMonitor.getViewRange(),
                                                 leftYRange,
                                                 myMonitor.getSpeedSeries(NetworkTrafficDataSeries.Type.BYTES_SENT)));
    lineChart.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myMonitor.expand();
      }
    });

    choreographer.register(lineChart);
    choreographer.register(leftAxis);

    container.add(label);
    container.add(leftAxis);
    container.add(lineChart);
    container.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        Dimension size = e.getComponent().getSize();
        lineChart.setBounds(0, labelSize.height, size.width, size.height - labelSize.height);
        leftAxis.setBounds(0, 0, MAX_AXIS_WIDTH, size.height);
        label.setBounds(0, 0, labelSize.width, labelSize.height);
      }
    });
  }
}
