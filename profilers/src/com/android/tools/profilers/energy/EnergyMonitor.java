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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.formatter.EnergyAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.ProfilerTooltip;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class EnergyMonitor extends ProfilerMonitor {
  // Shows the left axis range as "Medium" by default.
  public static final int MAX_EXPECTED_USAGE = EnergyAxisFormatter.DEFAULT_MAJOR_INTERVAL * 2;
  public static final Range AXIS_MARKER_RANGE =
    new Range(0, EnergyAxisFormatter.DEFAULT_MAJOR_INTERVAL * EnergyAxisFormatter.LABELS.length);

  @NotNull private final EnergyUsage myUsage;
  @NotNull private final ClampedAxisComponentModel myAxis;
  @NotNull private final Legends myLegends;
  @NotNull private final Legends myTooltipLegends;

  public EnergyMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);
    myUsage = new EnergyUsage(profilers);
    myAxis = new ClampedAxisComponentModel.Builder(myUsage.getUsageRange(), EnergyAxisFormatter.DEFAULT)
      .setMarkerRange(AXIS_MARKER_RANGE).build();
    myLegends = new Legends(myUsage, getTimeline().getDataRange());
    myTooltipLegends = new Legends(myUsage, getTimeline().getTooltipRange());
    changed(Aspect.ENABLE);
  }

  @Override
  public String getName() {
    return "ENERGY";
  }

  @Override
  public ProfilerTooltip buildTooltip() {
    return new EnergyMonitorTooltip(this);
  }

  @Override
  public void expand() {
    if (canExpand()) {
      myProfilers.setStage(new EnergyProfilerStage(getProfilers()));
    }
  }

  @Override
  public boolean canExpand() {
    return isEnabled();
  }

  @Override
  public void enter() {
    if (isEnabled()) {
      myProfilers.getUpdater().register(myUsage);
      myProfilers.getUpdater().register(myAxis);
    }
  }

  @Override
  public void exit() {
    if (isEnabled()) {
      myProfilers.getUpdater().unregister(myUsage);
      myProfilers.getUpdater().unregister(myAxis);
    }
  }

  /**
   * The energy monitor is valid when the session has JVMTI enabled or the device is above O.
   */
  @Override
  public boolean isEnabled() {
    if (myProfilers.getSession().getSessionId() != 0) {
      return myProfilers.getSessionsManager().getSelectedSessionMetaData().getJvmtiEnabled();
    }
    return myProfilers.getDevice() == null || myProfilers.getDevice().getFeatureLevel() >= AndroidVersion.VersionCodes.O;
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

    public Legends(@NotNull EnergyUsage usage, @NotNull Range range) {
      super(range);
      myUsageLegend = new SeriesLegend(usage.getTotalUsageDataSeries(), EnergyAxisFormatter.LEGEND_FORMATTER, range);
      add(myUsageLegend);
    }

    @NotNull
    public SeriesLegend getUsageLegend() {
      return myUsageLegend;
    }
  }
}
