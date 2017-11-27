/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.TooltipComponent;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.model.HNode;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayeredPane;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.cpu.nodemodel.MethodModel;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class CpuChartTooltipView extends MouseAdapter {
  @NotNull
  private final HTreeChart<MethodModel> myChart;

  @NotNull
  private final TooltipComponent myTooltipComponent;

  @NotNull
  private final JLabel myContent;

  @NotNull
  private final CpuProfilerStageView myStageView;

  private CpuChartTooltipView(@NotNull HTreeChart<MethodModel> chart, @NotNull CpuProfilerStageView stageView) {
    myStageView = stageView;
    myChart = chart;

    myContent = new JLabel();
    myContent.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
    myContent.setBorder(ProfilerLayout.TOOLTIP_BORDER);
    myContent.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    myContent.setFont(myContent.getFont().deriveFont(ProfilerLayout.TOOLTIP_FONT_SIZE));
    myContent.setOpaque(true);

    myTooltipComponent = new TooltipComponent(myContent, chart, ProfilerLayeredPane.class);
    myTooltipComponent.registerListenersOn(chart);
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    myTooltipComponent.setVisible(false);
    HNode<MethodModel> node = myChart.getNodeAt(e.getPoint());
    if (node != null) {
      showTooltip(node);
    }
  }

  private void showTooltip(@NotNull HNode<MethodModel> node) {
    assert node.getData() != null;
    myTooltipComponent.setVisible(true);
    Range dataRange = myStageView.getTimeline().getDataRange();
    long start = (long)(node.getStart() - dataRange.getMin());
    long end = (long)(node.getEnd() - dataRange.getMin());

    String text = String.format("<html>" +
                                "<p style='margin-bottom:5px;'>%s</p>" +
                                "<p style='color:%s'>%s - %s</p>" +
                                "</html>",
                                node.getData().getFullName(),
                                ColorUtil.toHex(ProfilerColors.TOOLTIP_TIME_COLOR),
                                TimeAxisFormatter.DEFAULT.getClockFormattedString(start),
                                TimeAxisFormatter.DEFAULT.getClockFormattedString(end));
    myContent.setText(text);
  }

  static void install(@NotNull HTreeChart<MethodModel> chart, @NotNull CpuProfilerStageView stageView) {
    chart.addMouseMotionListener(new CpuChartTooltipView(chart, stageView));
  }
}
