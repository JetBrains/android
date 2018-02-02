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
import com.android.tools.adtui.LegendConfig.IconType;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class MemoryUsageTooltipView extends ProfilerTooltipView {
  @NotNull private final MemoryUsageTooltip myTooltip;

  MemoryUsageTooltipView(@NotNull MemoryProfilerStageView view, @NotNull MemoryUsageTooltip tooltip) {
    super(view.getTimeline(), "Memory");
    myTooltip = tooltip;
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    MemoryProfilerStage.MemoryStageLegends legends = myTooltip.getLegends();
    LegendComponent legend =
      new LegendComponent.Builder(legends).setVerticalPadding(0).setOrientation(LegendComponent.Orientation.VERTICAL).build();

    if (myTooltip.useLiveAllocationTracking()) {
      legend.configure(legends.getJavaLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_JAVA_CAPTURED));
      legend.configure(legends.getNativeLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_NATIVE_CAPTURED));
      legend.configure(legends.getGraphicsLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_GRAPHICS_CAPTURED));
      legend.configure(legends.getStackLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_STACK_CAPTURED));
      legend.configure(legends.getCodeLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_CODE_CAPTURED));
      legend.configure(legends.getOtherLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_OTHERS_CAPTURED));
      legend.configure(legends.getObjectsLegend(), new LegendConfig(IconType.DASHED_LINE, ProfilerColors.MEMORY_OBJECTS_CAPTURED));
      legend.configure(legends.getGcDurationLegend(), new LegendConfig(IconType.NONE, ProfilerColors.MEMORY_OBJECTS_CAPTURED));
    }
    else {
      legend.configure(legends.getJavaLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_JAVA));
      legend.configure(legends.getNativeLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_NATIVE));
      legend.configure(legends.getGraphicsLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_GRAPHICS));
      legend.configure(legends.getStackLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_STACK));
      legend.configure(legends.getCodeLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_CODE));
      legend.configure(legends.getOtherLegend(), new LegendConfig(IconType.BOX, ProfilerColors.MEMORY_OTHERS));
      legend.configure(legends.getObjectsLegend(), new LegendConfig(IconType.DASHED_LINE, ProfilerColors.MEMORY_OBJECTS));
    }
    legend.configure(legends.getTotalLegend(), new LegendConfig(IconType.NONE, ProfilerColors.DEFAULT_STAGE_BACKGROUND));

    return legend;
  }
}
