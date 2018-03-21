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

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class EnergyEventTooltipView extends ProfilerTooltipView {
  @NotNull private final EnergyEventTooltip myTooltip;

  public EnergyEventTooltipView(@NotNull EnergyProfilerStageView stageView, @NotNull EnergyEventTooltip tooltip) {
    super(stageView.getTimeline(), "Energy Events");
    myTooltip = tooltip;
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    EnergyProfilerStage.EnergyEventLegends legends = myTooltip.getLegends();
    LegendComponent legendComponent = new LegendComponent.Builder(legends)
      .setOrientation(LegendComponent.Orientation.VERTICAL)
      .build();

    legendComponent.configure(legends.getLocationLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_LOCATION));
    legendComponent.configure(legends.getWakeLockLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_WAKE_LOCK));
    legendComponent
      .configure(legends.getAlarmAndJobLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_BACKGROUND));
    legends.changed(LegendComponentModel.Aspect.LEGEND);

    JLabel callToActionLabel = new JLabel("Select range to inspect");

    JPanel legendPanel = new JPanel(new TabularLayout("*").setVGap(10));
    legendPanel.setOpaque(false);
    legendPanel.add(legendComponent, new TabularLayout.Constraint(0, 0));
    legendPanel.add(new JSeparator(SwingConstants.HORIZONTAL), new TabularLayout.Constraint(1, 0));
    legendPanel.add(callToActionLabel, new TabularLayout.Constraint(2, 0));

    return legendPanel;
  }
}
