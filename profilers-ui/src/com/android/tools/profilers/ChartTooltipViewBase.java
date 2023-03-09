/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipComponent;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.model.HNode;
import com.google.common.annotations.VisibleForTesting;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public abstract class ChartTooltipViewBase<T extends HNode<T>> extends MouseAdapter {
  @NotNull
  private final HTreeChart<T> myChart;

  @NotNull
  private final TooltipComponent myTooltipComponent;

  @NotNull
  private final JPanel myContent;

  protected ChartTooltipViewBase(@NotNull HTreeChart<T> chart, @NotNull JComponent tooltipRoot) {
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
    T node = myChart.getNodeAt(e.getPoint());
    if (node != null) {
      myTooltipComponent.setVisible(true);
      showTooltip(node);
    }
  }

  @VisibleForTesting
  public TooltipComponent getTooltipComponent() {
    return myTooltipComponent;
  }

  protected JPanel getTooltipContainer() {
    return myContent;
  }

  @VisibleForTesting
  abstract public void showTooltip(@NotNull T node);
}
