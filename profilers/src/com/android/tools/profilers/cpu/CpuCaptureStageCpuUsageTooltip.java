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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import org.jetbrains.annotations.NotNull;

/**
 * Tooltip model for displaying CPU usage in the {@link CpuCaptureStage}.
 */
public class CpuCaptureStageCpuUsageTooltip implements TooltipModel {
  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 5, 10, "%");

  private final LegendComponentModel myLegendModel;
  private final SeriesLegend myCpuLegend;

  public CpuCaptureStageCpuUsageTooltip(@NotNull CpuUsage cpuUsage, @NotNull Range tooltipRange) {
    myCpuLegend = new SeriesLegend(cpuUsage.getCpuSeries(), CPU_USAGE_FORMATTER, tooltipRange);
    myLegendModel = new LegendComponentModel(tooltipRange);
    myLegendModel.add(myCpuLegend);
  }

  @NotNull
  public LegendComponentModel getLegendModel() {
    return myLegendModel;
  }

  @NotNull
  public SeriesLegend getCpuLegend() {
    return myCpuLegend;
  }
}
