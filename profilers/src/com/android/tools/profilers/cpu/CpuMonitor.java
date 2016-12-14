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

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class CpuMonitor extends ProfilerMonitor {

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 2, 10, "%");

  private final LineChartModel myThisProcessCpuUsage;
  private final AxisComponentModel myCpuUsageAxis;
  private final LegendComponentModel myLegends;

  public CpuMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);

    Range viewRange = profilers.getTimeline().getViewRange();
    Range dataRange = profilers.getTimeline().getDataRange();

    // Cpu usage is shown as percentages (e.g. 0 - 100) and no range animation is needed.
    Range leftYRange = new Range(0, 100);

    myThisProcessCpuUsage = new LineChartModel();
    RangedContinuousSeries cpuSeries = new RangedContinuousSeries("CPU", viewRange, leftYRange, getThisProcessCpuUsageSeries());
    myThisProcessCpuUsage.add(cpuSeries);

    myCpuUsageAxis = new AxisComponentModel(leftYRange, CPU_USAGE_FORMATTER, AxisComponentModel.AxisOrientation.RIGHT);
    myCpuUsageAxis.clampToMajorTicks(true);

    myLegends = new LegendComponentModel(100);
    myLegends.setLegendData(Collections.singletonList(new LegendData(cpuSeries, CPU_USAGE_FORMATTER, dataRange)));
  }

  @NotNull
  private CpuUsageDataSeries getCpuUsage(boolean other) {
    CpuServiceGrpc.CpuServiceBlockingStub client = myProfilers.getClient().getCpuClient();
    return new CpuUsageDataSeries(client, other, myProfilers.getProcessId());
  }

  @NotNull
  public CpuUsageDataSeries getThisProcessCpuUsageSeries() {
    return getCpuUsage(false);
  }

  @NotNull
  public CpuUsageDataSeries getOtherProcessesCpuUsage() {
    return getCpuUsage(true);
  }

  @NotNull
  public CpuThreadCountDataSeries getThreadsCount() {
    return new CpuThreadCountDataSeries(myProfilers.getClient().getCpuClient(), myProfilers.getProcessId());
  }

  public LineChartModel getThisProcessCpuUsage() {
    return myThisProcessCpuUsage;
  }

  @Override
  public String getName() {
    return "CPU";
  }

  @Override
  public void exit() {

  }

  @Override
  public void enter() {
    myProfilers.getUpdater().register(myThisProcessCpuUsage);
    myProfilers.getUpdater().register(myCpuUsageAxis);
    myProfilers.getUpdater().register(myLegends);
  }

  public void expand() {
    myProfilers.setStage(new CpuProfilerStage(myProfilers));
  }

  public AxisComponentModel getCpuUsageAxis() {
    return myCpuUsageAxis;
  }

  public LegendComponentModel getLegends() {
    return myLegends;
  }
}
