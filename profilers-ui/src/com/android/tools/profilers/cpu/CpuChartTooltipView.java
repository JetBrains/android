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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipComponent;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayeredPane;
import com.android.tools.profilers.ProfilerLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.android.tools.profilers.ProfilerFonts.TOOLTIP_BODY_FONT;

class CpuChartTooltipView extends MouseAdapter {
  @NotNull
  private final HTreeChart<CaptureNode> myChart;

  @NotNull
  private final TooltipComponent myTooltipComponent;

  @NotNull
  private final JPanel myContent;

  @NotNull
  private final CpuProfilerStageView myStageView;

  private CpuChartTooltipView(@NotNull HTreeChart<CaptureNode> chart, @NotNull CpuProfilerStageView stageView) {
    myStageView = stageView;
    myChart = chart;

    myContent = new JPanel(new TabularLayout("*", "*"));
    myContent.setBorder(ProfilerLayout.TOOLTIP_BORDER);
    myContent.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);

    myTooltipComponent = new TooltipComponent.Builder(myContent, chart, myStageView.getProfilersView().getComponent()).build();
    myTooltipComponent.registerListenersOn(chart);
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    myTooltipComponent.setVisible(false);
    CaptureNode node = myChart.getNodeAt(e.getPoint());
    if (node != null) {
      showTooltip(node);
    }
  }

  private void showTooltip(@NotNull CaptureNode node) {
    myTooltipComponent.setVisible(true);
    Range dataRange = myStageView.getTimeline().getDataRange();
    long start = (long)(node.getStart() - dataRange.getMin());
    long end = (long)(node.getEnd() - dataRange.getMin());

    myContent.removeAll();
    JLabel nameLabel = new JLabel(node.getData().getFullName());
    nameLabel.setFont(TOOLTIP_BODY_FONT);
    nameLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    myContent.add(nameLabel, new TabularLayout.Constraint(0, 0));

    JLabel durationLabel = new JLabel(String.format("%s - %s (%s)", TimeFormatter.getFullClockString(start),
                                                    TimeFormatter.getFullClockString(end),
                                                    TimeFormatter.getSingleUnitDurationString(node.getDuration())));
    durationLabel.setFont(TOOLTIP_BODY_FONT);
    durationLabel.setForeground(ProfilerColors.TOOLTIP_TIME_COLOR);
    durationLabel.setBorder(new EmptyBorder(5, 0, 0, 0));
    myContent.add(durationLabel, new TabularLayout.Constraint(1, 0));
  }

  static void install(@NotNull HTreeChart<CaptureNode> chart, @NotNull CpuProfilerStageView stageView) {
    chart.addMouseMotionListener(new CpuChartTooltipView(chart, stageView));
  }
}
