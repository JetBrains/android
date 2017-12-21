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
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class EnergyMonitor extends ProfilerMonitor {

  private static final BaseAxisFormatter ENERGY_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 2, 10, "mAh");

  @NotNull private final EnergyUsage myUsage;
  @NotNull private final AxisComponentModel myAxis;
  @NotNull private final Legends myLegends;
  @NotNull private final Legends myTooltipLegends;

  public EnergyMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);
    myUsage = new EnergyUsage(profilers);
    myAxis = new AxisComponentModel(myUsage.getUsageRange(), ENERGY_AXIS_FORMATTER);
    myAxis.setClampToMajorTicks(true);
    myLegends = new Legends(myUsage, getTimeline().getDataRange(), false);
    myTooltipLegends = new Legends(myUsage, getTimeline().getTooltipRange(), true);
  }

  @Override
  public String getName() {
    return "ENERGY";
  }

  @Override
  public void expand() {
    // TODO: uncomment this when L2 is ready
    // myProfilers.setStage(new EnergyProfilerStage(getProfilers()));
  }

  @Override
  public void enter() {
    myProfilers.getUpdater().register(myUsage);
    myProfilers.getUpdater().register(myAxis);
    myProfilers.getUpdater().register(myLegends);
    myProfilers.getUpdater().register(myTooltipLegends);
  }

  @Override
  public void exit() {
    myProfilers.getUpdater().unregister(myUsage);
    myProfilers.getUpdater().unregister(myAxis);
    myProfilers.getUpdater().unregister(myLegends);
    myProfilers.getUpdater().unregister(myTooltipLegends);
  }

  @NotNull
  public EnergyUsage getUsage() {
     return myUsage;
  }

  @NotNull
  public AxisComponentModel getAxis() {
    return myAxis;
  }

  @NotNull
  public Legends getLegends() {
    return myLegends;
  }

  @NotNull
  public Legends getTooltipLegends() {
    return myTooltipLegends;
  }

  public static final class Legends extends LegendComponentModel {

    @NotNull
    private final SeriesLegend myUsageLegend;

    public Legends(@NotNull EnergyUsage usage, @NotNull Range range, boolean highlight) {
      super(highlight ? 0 : LEGEND_UPDATE_FREQUENCY_MS);
      myUsageLegend = new SeriesLegend(usage.getUsageDataSeries(), ENERGY_AXIS_FORMATTER, range);
      add(myUsageLegend);
    }

    @NotNull
    public SeriesLegend getUsageLegend() {
      return myUsageLegend;
    }
  }
}
