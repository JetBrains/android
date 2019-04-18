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
import com.android.tools.profiler.proto.Energy;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class DetailedEnergyUsage extends LineChartModel {

  @NotNull private final RangedContinuousSeries myCpuUsageSeries;
  @NotNull private final RangedContinuousSeries myNetworkUsageSeries;
  @NotNull private final RangedContinuousSeries myLocationUsageSeries;
  @NotNull private final Range myUsageRange;

  public DetailedEnergyUsage(@NotNull StudioProfilers profilers) {
    myUsageRange = new Range(0, EnergyMonitor.MAX_EXPECTED_USAGE);

    EnergyUsageDataSeries locationDataSeries =
      new EnergyUsageDataSeries(profilers.getClient(), profilers.getSession(), Energy.EnergyUsageData::getLocationUsage);
    myLocationUsageSeries =
      new RangedContinuousSeries("Location", profilers.getTimeline().getViewRange(), myUsageRange, locationDataSeries);
    add(myLocationUsageSeries);

    EnergyUsageDataSeries networkDataSeries =
      new EnergyUsageDataSeries(profilers.getClient(), profilers.getSession(), Energy.EnergyUsageData::getNetworkUsage);
    myNetworkUsageSeries = new RangedContinuousSeries("Network", profilers.getTimeline().getViewRange(), myUsageRange, networkDataSeries);
    add(myNetworkUsageSeries);

    EnergyUsageDataSeries cpuDataSeries =
      new EnergyUsageDataSeries(profilers.getClient(), profilers.getSession(), Energy.EnergyUsageData::getCpuUsage);
    myCpuUsageSeries = new RangedContinuousSeries("CPU", profilers.getTimeline().getViewRange(), myUsageRange, cpuDataSeries);
    add(myCpuUsageSeries);
  }

  @NotNull
  public Range getUsageRange() {
    return myUsageRange;
  }

  @NotNull
  public RangedContinuousSeries getCpuUsageSeries() {
    return myCpuUsageSeries;
  }

  @NotNull
  public RangedContinuousSeries getNetworkUsageSeries() {
    return myNetworkUsageSeries;
  }

  @NotNull
  public RangedContinuousSeries getLocationUsageSeries() {
    return myLocationUsageSeries;
  }
}
