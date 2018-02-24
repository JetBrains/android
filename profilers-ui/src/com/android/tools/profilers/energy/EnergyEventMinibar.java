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

import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.openapi.ui.VerticalFlowLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.profilers.ProfilerLayout.MONITOR_BORDER;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING;

public final class EnergyEventMinibar {
  @NotNull private final JComponent myComponent;

  public EnergyEventMinibar(@NotNull EnergyProfilerStageView stageView) {
    StateChart<EnergyProfiler.EnergyEvent> chart = EnergyEventStateChart.create(stageView.getStage().getEventModel());
    myComponent = createUi(chart);
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  private JPanel createUi(@NotNull StateChart<EnergyProfiler.EnergyEvent> eventChart) {
    JPanel root = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
    root.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    root.setBorder(MONITOR_BORDER);

    JPanel labelContainer = new JPanel(new BorderLayout());
    labelContainer.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    JLabel label = new JLabel("SYSTEM");
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);
    labelContainer.add(label, BorderLayout.WEST);

    root.add(labelContainer);
    root.add(eventChart);

    return root;
  }
}
