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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import org.jetbrains.annotations.NotNull;

public class DetailedEnergyUsage extends EnergyUsage {

  @NotNull private final RangedContinuousSeries myCpuUsageSeries;
  @NotNull private final RangedContinuousSeries myNetworkUsageSeries;
  @NotNull private final RangedContinuousSeries myLocationUsageSeries;

  public DetailedEnergyUsage(@NotNull StudioProfilers profilers) {
    super(profilers);

    long streamId = profilers.getSession().getStreamId();
    int pid = profilers.getSession().getPid();
    // TODO(b/133430804): investigate ways to not query database multiple times.
    DataSeries<Long> cpuDataSeries = new UnifiedEventDataSeries<>(
      profilers.getClient().getTransportClient(),
      streamId,
      pid,
      Common.Event.Kind.ENERGY_USAGE,
      UnifiedEventDataSeries.DEFAULT_GROUP_ID,
      UnifiedEventDataSeries.fromFieldToDataExtractor(event -> (long)event.getEnergyUsage().getCpuUsage()));
    DataSeries<Long> networkDataSeries = new UnifiedEventDataSeries<>(
      profilers.getClient().getTransportClient(),
      streamId,
      pid,
      Common.Event.Kind.ENERGY_USAGE,
      UnifiedEventDataSeries.DEFAULT_GROUP_ID,
      UnifiedEventDataSeries.fromFieldToDataExtractor(event -> (long)event.getEnergyUsage().getNetworkUsage()));
    DataSeries<Long> locationDataSeries = new UnifiedEventDataSeries<>(
      profilers.getClient().getTransportClient(),
      streamId,
      pid,
      Common.Event.Kind.ENERGY_USAGE,
      UnifiedEventDataSeries.DEFAULT_GROUP_ID,
      UnifiedEventDataSeries.fromFieldToDataExtractor(event -> (long)event.getEnergyUsage().getLocationUsage()));

    myLocationUsageSeries =
      new RangedContinuousSeries("Location", profilers.getTimeline().getViewRange(), getUsageRange(), locationDataSeries);
    add(myLocationUsageSeries);
    myNetworkUsageSeries =
      new RangedContinuousSeries("Network", profilers.getTimeline().getViewRange(), getUsageRange(), networkDataSeries);
    add(myNetworkUsageSeries);
    myCpuUsageSeries = new RangedContinuousSeries("CPU", profilers.getTimeline().getViewRange(), getUsageRange(), cpuDataSeries);
    add(myCpuUsageSeries);
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
