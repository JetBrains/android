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
import com.android.tools.adtui.TooltipView;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.StageView;
import java.awt.Color;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

class EnergyStageTooltipView extends TooltipView {
  @NotNull private final StageView myStageView;
  @NotNull private final EnergyStageTooltip myTooltip;

  public EnergyStageTooltipView(@NotNull StageView stageView, @NotNull EnergyStageTooltip tooltip) {
    super(stageView.getStage().getTimeline());
    myStageView = stageView;
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
    usageLegendComponent
      .configure(usageLegends.getLocationLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_LOCATION));

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


    JPanel legendPanel = new JPanel(new TabularLayout("*", "Fit,8px,Fit,8px,Fit,Fit,Fit"));
    legendPanel.setOpaque(false);
    legendPanel.add(usageLegendComponent, new TabularLayout.Constraint(0, 0));

    JLabel eventLabel = new JLabel("System Events");
    eventLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    eventLabel.setFont(ProfilerFonts.STANDARD_FONT);
    Color color = eventLabel.getForeground();
    eventLabel.setForeground(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * 0.6)));
    JPanel labelWithSeparator = new JPanel(new TabularLayout("Fit,8px,*", "Fit"));
    labelWithSeparator.add(eventLabel, new TabularLayout.Constraint(0, 0));
    labelWithSeparator.add(AdtUiUtils.createHorizontalSeparator(), new TabularLayout.Constraint(0, 2));
    labelWithSeparator.setOpaque(false);
    legendPanel.add(labelWithSeparator, new TabularLayout.Constraint(2, 0));

    legendPanel.add(eventLegendComponent, new TabularLayout.Constraint(4, 0));

    legendPanel.add(AdtUiUtils.createHorizontalSeparator(), new TabularLayout.Constraint(5, 0));

    // TODO(b/188695273): to be removed after migration.
    if (!myStageView.getStage().getStudioProfilers().getIdeServices().getAppInspectionMigrationServices().isMigrationEnabled()) {
      JLabel callToActionLabel = new JLabel("Select range to inspect");
      callToActionLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);
      callToActionLabel.setFont(ProfilerFonts.STANDARD_FONT);
      callToActionLabel.setForeground(eventLabel.getForeground());
      legendPanel.add(callToActionLabel, new TabularLayout.Constraint(6, 0));
    }

    return legendPanel;
  }
}
