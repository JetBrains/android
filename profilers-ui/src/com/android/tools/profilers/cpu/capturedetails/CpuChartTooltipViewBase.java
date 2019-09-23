/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipComponent;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

abstract class CpuChartTooltipViewBase extends MouseAdapter {
  @NotNull
  private final HTreeChart<CaptureNode> myChart;

  @NotNull
  private final TooltipComponent myTooltipComponent;

  @NotNull
  private final JPanel myContent;

  protected CpuChartTooltipViewBase(@NotNull HTreeChart<CaptureNode> chart, @NotNull JLayeredPane tooltipRoot) {
    myChart = chart;

    myContent = new JPanel(new TabularLayout("*", "*"));
    myContent.setBorder(ProfilerLayout.TOOLTIP_BORDER);
    myContent.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);

    myTooltipComponent = new TooltipComponent.Builder(myContent, chart, tooltipRoot).build();
    myTooltipComponent.registerListenersOn(chart);
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    myTooltipComponent.setVisible(false);
    CaptureNode node = myChart.getNodeAt(e.getPoint());
    if (node != null) {
      myTooltipComponent.setVisible(true);
      showTooltip(node);
    }
  }

  protected JPanel getTooltipContainer() {
    return myContent;
  }

  abstract protected void showTooltip(@NotNull CaptureNode node);
}
