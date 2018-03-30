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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class EnergyStageTooltipView extends ProfilerTooltipView {
  @NotNull private final EnergyStageTooltip myTooltip;

  public EnergyStageTooltipView(@NotNull EnergyProfilerStageView stageView, @NotNull EnergyStageTooltip tooltip) {
    super(stageView.getTimeline(), "Energy");
    myTooltip = tooltip;
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    EnergyProfilerStage.EnergyUsageLegends usageLegends = myTooltip.getUsageLegends();
    LegendComponent usageLegendComponent = new LegendComponent.Builder(usageLegends)
      .setVerticalPadding(0)
      .setOrientation(LegendComponent.Orientation.VERTICAL)
      .build();

    usageLegendComponent.configure(usageLegends.getCpuLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_CPU));
    usageLegendComponent
      .configure(usageLegends.getNetworkLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_NETWORK));

    EnergyProfilerStage.EnergyEventLegends eventLegends = myTooltip.getEventLegends();
    LegendComponent eventLegendComponent = new LegendComponent.Builder(eventLegends)
      .setVerticalPadding(0)
      .setOrientation(LegendComponent.Orientation.VERTICAL)
      .build();
    eventLegendComponent
      .configure(eventLegends.getLocationLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_LOCATION));
    eventLegendComponent
      .configure(eventLegends.getWakeLockLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_WAKE_LOCK));
    eventLegendComponent
      .configure(eventLegends.getAlarmAndJobLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_BACKGROUND));
    eventLegends.changed(LegendComponentModel.Aspect.LEGEND);


    JPanel legendPanel = new JPanel(new TabularLayout("*").setVGap(10));
    legendPanel.setOpaque(false);
    legendPanel.add(usageLegendComponent, new TabularLayout.Constraint(0, 0));

    JLabel eventLabel = new JLabel("Events");
    eventLabel.setFont(myFont);
    legendPanel.add(eventLabel, new TabularLayout.Constraint(1, 0));

    legendPanel.add(eventLegendComponent, new TabularLayout.Constraint(2, 0));

    legendPanel.add(new JSeparator(SwingConstants.HORIZONTAL), new TabularLayout.Constraint(3, 0));

    JLabel callToActionLabel = new JLabel("Select range to inspect");
    callToActionLabel.setFont(myFont);
    legendPanel.add(callToActionLabel, new TabularLayout.Constraint(4, 0));


    return legendPanel;
  }
}
