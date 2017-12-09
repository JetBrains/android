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

import com.android.tools.adtui.model.AxisComponentModel;
import com.android.tools.adtui.model.Interpolatable;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.EventMonitor;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class EnergyProfilerStage extends Stage {

  @NotNull private final DetailedEnergyUsage myDetailedUsage;
  @NotNull private final AxisComponentModel myAxis;
  @NotNull private final EventMonitor myEventMonitor;
  @NotNull private final EnergyLegends myLegends;
  @NotNull private final EnergyLegends myTooltipLegends;

  public EnergyProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myDetailedUsage = new DetailedEnergyUsage(profilers);
    myAxis = new AxisComponentModel(myDetailedUsage.getUsageRange(), EnergyMonitor.ENERGY_AXIS_FORMATTER);
    myEventMonitor = new EventMonitor(profilers);
    myLegends = new EnergyLegends(myDetailedUsage, profilers.getTimeline().getDataRange(), false);
    myTooltipLegends = new EnergyLegends(myDetailedUsage, profilers.getTimeline().getTooltipRange(), true);
  }

  @Override
  public void enter() {
    getStudioProfilers().getUpdater().register(myLegends);
    getStudioProfilers().getUpdater().register(myTooltipLegends);
    getStudioProfilers().getUpdater().register(myDetailedUsage);
    getStudioProfilers().getUpdater().register(myAxis);
  }

  @Override
  public void exit() {
    getStudioProfilers().getUpdater().unregister(myLegends);
    getStudioProfilers().getUpdater().unregister(myTooltipLegends);
    getStudioProfilers().getUpdater().unregister(myDetailedUsage);
    getStudioProfilers().getUpdater().unregister(myAxis);
  }

  @NotNull
  public DetailedEnergyUsage getDetailedUsage() {
    return myDetailedUsage;
  }

  @NotNull
  public AxisComponentModel getAxis() {
    return myAxis;
  }

  @NotNull
  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  @NotNull
  public EnergyLegends getLegends() {
    return myLegends;
  }

  @NotNull
  public EnergyLegends getTooltipLegends() {
    return myTooltipLegends;
  }

  @NotNull
  public String getName() {
    return "ENERGY";
  }

  public static class EnergyLegends extends LegendComponentModel {

    @NotNull private final SeriesLegend myCpuLegend;
    @NotNull private final SeriesLegend myNetworkLegend;

    EnergyLegends(DetailedEnergyUsage detailedUsage, Range range, boolean isTooltip) {
      super(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
      myCpuLegend = new SeriesLegend(detailedUsage.getCpuUsageSeries(), EnergyMonitor.ENERGY_AXIS_FORMATTER, range, "CPU",
                                     Interpolatable.SegmentInterpolator);
      myNetworkLegend = new SeriesLegend(detailedUsage.getNetworkUsageSeries(), EnergyMonitor.ENERGY_AXIS_FORMATTER, range, "NETWORK",
                                         Interpolatable.SegmentInterpolator);

      List<SeriesLegend> legends = Lists.newArrayList(myCpuLegend, myNetworkLegend);
      if (isTooltip) {
        // Reversing legends, bottom-to-top, matches the line chart graph order
        Collections.reverse(legends);
      }
      legends.forEach(this::add);
    }

    @NotNull
    public SeriesLegend getCpuLegend() {
      return myCpuLegend;
    }

    @NotNull
    public SeriesLegend getNetworkLegend() {
      return myNetworkLegend;
    }
  }
}
