/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class CpuUsage extends LineChartModel {
  // Cpu usage is shown as percentages (e.g. 0 - 100) and no range animation is needed.
  @NotNull private final Range myCpuRange;
  @NotNull private final RangedContinuousSeries myCpuSeries;

  public CpuUsage(@NotNull StudioProfilers profilers) {
    myCpuRange = new Range(0, 100);
    CpuUsageDataSeries series = new CpuUsageDataSeries(profilers.getClient().getCpuClient(), false, profilers.getProcessId(),
                                                       profilers.getSession());
    myCpuSeries = new RangedContinuousSeries(getCpuSeriesLabel(), profilers.getTimeline().getViewRange(), myCpuRange, series);
    add(myCpuSeries);
  }

  @NotNull
  public Range getCpuRange() {
    return myCpuRange;
  }

  @NotNull
  public RangedContinuousSeries getCpuSeries() {
    return myCpuSeries;
  }

  protected String getCpuSeriesLabel() {
    return "";
  }
}
