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
package com.android.tools.profilers.energy;

import static com.android.tools.profilers.ProfilerLayout.MONITOR_BORDER;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.ui.ColorUtil;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

public final class EnergyEventMinibar {
  @NotNull private final JComponent myComponent;

  public EnergyEventMinibar(@NotNull EnergyProfilerStageView stageView) {
    StateChart<Common.Event> chart = EnergyEventStateChart.create(stageView.getStage().getEventModel());
    myComponent = createUi(chart);
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  private JPanel createUi(@NotNull StateChart<Common.Event> eventChart) {
    JPanel root = new JPanel(new TabularLayout("Fit,*", "*"));
    root.setBorder(MONITOR_BORDER);

    JPanel labelContainer = new JPanel(new BorderLayout());
    labelContainer.setBackground(ColorUtil.withAlpha(ProfilerColors.DEFAULT_STAGE_BACKGROUND, 0.75));
    labelContainer.setBorder(MONITOR_LABEL_PADDING);
    JLabel label = new JLabel("SYSTEM");
    label.setVerticalAlignment(SwingConstants.CENTER);
    // TODO(b/77921079): Add help icon and tooltip for this area
    labelContainer.add(label);

    JPanel eventChartContainer = new JPanel(new BorderLayout());
    eventChartContainer.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    eventChartContainer.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
    eventChartContainer.add(eventChart);

    root.add(labelContainer, new TabularLayout.Constraint(0, 0));
    root.add(eventChartContainer, new TabularLayout.Constraint(0, 0, 2));

    return root;
  }
}
