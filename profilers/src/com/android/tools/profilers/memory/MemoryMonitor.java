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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.MemorySample;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class MemoryMonitor extends ProfilerMonitor {

  private final int myProcessId;

  @NotNull
  private final MemoryServiceGrpc.MemoryServiceBlockingStub myClient;
  private final AxisComponentModel myYAxis;
  private final Range myYRange;

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 2, 5);
  private final LineChartModel myTotalMemory;
  private final LegendComponentModel myMemoryLegend;
  private DurationDataModel<GcDurationData> myGcCount;

  public MemoryMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);
    myProcessId = profilers.getProcessId();
    myClient = profilers.getClient().getMemoryClient();

    myYRange = new Range(0, 0);
    myYAxis = new AxisComponentModel(myYRange, MEMORY_AXIS_FORMATTER);
    myYAxis.setClampToMajorTicks(true);

    myTotalMemory = new LineChartModel();
    RangedContinuousSeries memSeries = new RangedContinuousSeries("Memory", getTimeline().getViewRange(), myYRange, getTotalMemorySeries());
    myTotalMemory.add(memSeries);

    // Only update these values every 0.1s
    myMemoryLegend = new LegendComponentModel(100);
    LegendData data = new LegendData(memSeries, MEMORY_AXIS_FORMATTER, getTimeline().getDataRange());
    myMemoryLegend.setLegendData(Collections.singletonList(data));

    myGcCount = new DurationDataModel<>(new RangedSeries<>(getTimeline().getViewRange(), new GcStatsDataSeries(myClient, myProcessId)));
  }

  @NotNull
  public MemoryDataSeries getTotalMemorySeries() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getTotalMem);
  }

  @NotNull
  public MemoryDataSeries getJavaMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getJavaMem);
  }

  @NotNull
  public MemoryDataSeries getNativeMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getNativeMem);
  }

  @NotNull
  public MemoryDataSeries getGraphicsMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getGraphicsMem);
  }

  @NotNull
  public MemoryDataSeries getStackMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getStackMem);
  }

  @NotNull
  public MemoryDataSeries getCodeMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getCodeMem);
  }

  @NotNull
  public MemoryDataSeries getOthersMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getOthersMem);
  }

  @NotNull
  public VmStatsDataSeries getObjectCount() {
    return new VmStatsDataSeries(myClient, myProcessId, sample -> (long)(sample.getJavaAllocationCount() - sample.getJavaFreeCount()));
  }

  @NotNull
  public DurationDataModel<GcDurationData> getGcCount() {
    return myGcCount;
  }

  @Override
  public String getName() {
    return "Memory";
  }

  @Override
  public void enter() {
    // TODO: NOT ALL MONITORS HAVE ENTER AND EXIT
    myProfilers.getUpdater().register(myTotalMemory);
    myProfilers.getUpdater().register(myYAxis);
    myProfilers.getUpdater().register(myMemoryLegend);
    myProfilers.getUpdater().register(myGcCount);
  }

  @Override
  public void exit() {
    myProfilers.getUpdater().unregister(myTotalMemory);
    myProfilers.getUpdater().unregister(myYAxis);
    myProfilers.getUpdater().unregister(myMemoryLegend);
    myProfilers.getUpdater().unregister(myGcCount);
  }

  public void expand() {
    myProfilers.setStage(new MemoryProfilerStage(myProfilers));
  }

  public AxisComponentModel getYAxis() {
    return myYAxis;
  }

  public LineChartModel getTotalMemory() {
    return myTotalMemory;
  }

  public LegendComponentModel getMemoryLegend() {
    return myMemoryLegend;
  }
}