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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipView;
import com.android.tools.profilers.ProfilerColors;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import org.jetbrains.annotations.NotNull;

/**
 * Tooltip view for {@link CpuCaptureStageCpuUsageTooltip}.
 */
public class CpuCaptureStageCpuUsageTooltipView extends TooltipView {
  private final CpuCaptureStageCpuUsageTooltip myTooltip;

  public CpuCaptureStageCpuUsageTooltipView(@NotNull CpuCaptureStageView stageView, @NotNull CpuCaptureStageCpuUsageTooltip tooltip) {
    // Uses the CpuCapture's timeline instead of ProfilerTimeline.
    super(stageView.getStage().getCaptureTimeline());
    myTooltip = tooltip;
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    JPanel panel = new JPanel(new TabularLayout("*", "*,Fit-,Fit,*,Fit-"));
    LegendComponent legendComponent = new LegendComponent.Builder(myTooltip.getLegendModel())
      .setVerticalPadding(0)
      .setOrientation(LegendComponent.Orientation.VERTICAL)
      .build();
    legendComponent.configure(myTooltip.getCpuLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.CPU_USAGE_CAPTURED));

    JLabel contextHelpLabel = new JLabel("Select to inspect");
    contextHelpLabel.setFont(TooltipView.TOOLTIP_BODY_FONT);
    contextHelpLabel.setForeground(ProfilerColors.TOOLTIP_LOW_CONTRAST);

    panel.add(new JSeparator(), new TabularLayout.Constraint(0, 0));
    panel.add(new JLabel("CPU Usage"), new TabularLayout.Constraint(1, 0));
    panel.add(legendComponent, new TabularLayout.Constraint(2, 0));
    panel.add(new JSeparator(), new TabularLayout.Constraint(3, 0));
    panel.add(contextHelpLabel, new TabularLayout.Constraint(4, 0));
    return panel;
  }
}
