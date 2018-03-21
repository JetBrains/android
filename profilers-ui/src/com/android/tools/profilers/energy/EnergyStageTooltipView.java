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
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class EnergyStageTooltipView extends ProfilerTooltipView {
  @NotNull private final EnergyProfilerStage myStage;

  EnergyStageTooltipView(@NotNull EnergyProfilerStage stage) {
    super(stage.getStudioProfilers().getTimeline(), "Energy");
    myStage = stage;
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    EnergyProfilerStage.EnergyLegends legends = myStage.getTooltipLegends();
    LegendComponent legend = new LegendComponent.Builder(legends)
      .setVerticalPadding(0)
      .setOrientation(LegendComponent.Orientation.VERTICAL)
      .build();

    legend.configure(legends.getCpuLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_CPU));
    legend.configure(legends.getNetworkLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.ENERGY_NETWORK));

    return legend;
  }
}
