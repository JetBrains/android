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
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import org.jetbrains.annotations.NotNull;

public class EnergyUsage extends LineChartModel {

  @NotNull private final RangedContinuousSeries myTotalUsageDataSeries;
  @NotNull private final Range myUsageRange;

  public EnergyUsage(@NotNull StudioProfilers profilers) {
    myUsageRange = new Range(0, EnergyMonitor.MAX_EXPECTED_USAGE);
    DataSeries<Long> dataSeries;
    if (profilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      dataSeries = new UnifiedEventDataSeries<>(
        profilers.getClient().getTransportClient(),
        profilers.getSession().getStreamId(),
        profilers.getSession().getPid(),
        Common.Event.Kind.ENERGY_USAGE,
        UnifiedEventDataSeries.DEFAULT_GROUP_ID,
        UnifiedEventDataSeries.fromFieldToDataExtractor(event -> (long)EnergyUsageDataSeries.getTotalUsage(event.getEnergyUsage()))
      );
    }
    else {
      dataSeries = new EnergyUsageDataSeries(profilers.getClient(), profilers.getSession());
    }
    myTotalUsageDataSeries = new RangedContinuousSeries(getSeriesLabel(), profilers.getTimeline().getViewRange(), myUsageRange, dataSeries,
                                                        profilers.getTimeline().getDataRange());
    add(myTotalUsageDataSeries);
  }

  @NotNull
  public RangedContinuousSeries getTotalUsageDataSeries() {
    return myTotalUsageDataSeries;
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
