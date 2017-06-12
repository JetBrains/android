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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipView;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

class MemoryStageTooltipView extends ProfilerTooltipView {
  @NotNull private final MemoryProfilerStage myStage;

  MemoryStageTooltipView(@NotNull MemoryProfilerStage stage) {
    super(stage.getStudioProfilers().getTimeline(), "Memory");
    myStage = stage;
  }

  @Override
  protected Component createTooltip() {
    MemoryProfilerStage.MemoryStageLegends legends = myStage.getTooltipLegends();
    LegendComponent legend =
      new LegendComponent.Builder(legends).setVerticalPadding(0).setOrientation(LegendComponent.Orientation.VERTICAL).build();

    legend.configure(legends.getJavaLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.MEMORY_JAVA));
    legend.configure(legends.getNativeLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.MEMORY_NATIVE));
    legend.configure(legends.getGraphicsLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.MEMORY_GRAPHICS));
    legend.configure(legends.getStackLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.MEMORY_STACK));
    legend.configure(legends.getCodeLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.MEMORY_CODE));
    legend.configure(legends.getOtherLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.MEMORY_OTHERS));

    legend.configure(legends.getTotalLegend(), new LegendConfig(LegendConfig.IconType.NONE, ProfilerColors.DEFAULT_STAGE_BACKGROUND));
    legend.configure(legends.getObjectsLegend(), new LegendConfig(LegendConfig.IconType.DASHED_LINE, ProfilerColors.MEMORY_OBJECTS));

    return legend;
  }
}
