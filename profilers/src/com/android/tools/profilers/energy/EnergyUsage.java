// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy;

import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class EnergyUsage extends LineChartModel {

  @NotNull private final RangedContinuousSeries myUsageSeries;
  @NotNull private final Range myUsageRange;

  public EnergyUsage(@NotNull StudioProfilers profilers) {
    myUsageRange = new Range(0, 100);
    EnergyUsageDataSeries dataSeries = new EnergyUsageDataSeries(profilers.getClient(), profilers.getSession());
    myUsageSeries = new RangedContinuousSeries(getSeriesLabel(), profilers.getTimeline().getViewRange(), myUsageRange, dataSeries);
    add(myUsageSeries);
  }

  @NotNull
  public RangedContinuousSeries getUsageDataSeries() {
    return myUsageSeries;
  }

  @NotNull
  public Range getUsageRange() {
    return myUsageRange;
  }

  @NotNull
  private static String getSeriesLabel() {
    return "";
  }
}
