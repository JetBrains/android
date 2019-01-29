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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.ProfilerTooltip;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class CpuMonitor extends ProfilerMonitor {

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 2, 10, "%");

  @NotNull private final CpuUsage myThisProcessCpuUsage;
  @NotNull private final ClampedAxisComponentModel myCpuUsageAxis;
  @NotNull private final Legends myLegends;
  @NotNull private final Legends myTooltipLegends;

  public CpuMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);

    myThisProcessCpuUsage = new CpuUsage(profilers);

    myCpuUsageAxis = new ClampedAxisComponentModel.Builder(myThisProcessCpuUsage.getCpuRange(), CPU_USAGE_FORMATTER).build();
    myLegends = new Legends(myThisProcessCpuUsage, profilers.getTimeline().getDataRange());
    myTooltipLegends = new Legends(myThisProcessCpuUsage, profilers.getTimeline().getTooltipRange());
  }

  @Override
  public String getName() {
    return "CPU";
  }

  @Override
  public ProfilerTooltip buildTooltip() {
    return new CpuMonitorTooltip(this);
  }

  @Override
  public void exit() {
    myProfilers.getUpdater().unregister(myThisProcessCpuUsage);
    myProfilers.getUpdater().unregister(myCpuUsageAxis);
  }

  @Override
  public void enter() {
    myProfilers.getUpdater().register(myThisProcessCpuUsage);
    myProfilers.getUpdater().register(myCpuUsageAxis);
  }

  @Override
  public void expand() {
    myProfilers.setStage(new CpuProfilerStage(myProfilers));
  }

  @NotNull
  public AxisComponentModel getCpuUsageAxis() {
    return myCpuUsageAxis;
  }

  @NotNull
  public Legends getLegends() {
    return myLegends;
  }

  @NotNull
  public CpuUsage getThisProcessCpuUsage() {
    return myThisProcessCpuUsage;
  }

  @NotNull
  public Legends getTooltipLegends() {
    return myTooltipLegends;
  }

  public static class Legends extends LegendComponentModel {

    @NotNull
    private final SeriesLegend myCpuLegend;

    public Legends(@NotNull CpuUsage usage, @NotNull Range range) {
      super(range);
      myCpuLegend = new SeriesLegend(usage.getCpuSeries(), CPU_USAGE_FORMATTER, range);
      add(myCpuLegend);
    }

    @NotNull
    public SeriesLegend getCpuLegend() {
      return myCpuLegend;
    }
  }
}
