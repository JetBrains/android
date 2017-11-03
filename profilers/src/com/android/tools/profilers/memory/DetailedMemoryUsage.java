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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.MemorySample;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class DetailedMemoryUsage extends MemoryUsage {

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final Range myObjectsRange;
  @NotNull private final RangedContinuousSeries myJavaSeries;
  @NotNull private final RangedContinuousSeries myNativeSeries;
  @NotNull private final RangedContinuousSeries myGraphicsSeries;
  @NotNull private final RangedContinuousSeries myStackSeries;
  @NotNull private final RangedContinuousSeries myCodeSeries;
  @NotNull private final RangedContinuousSeries myOtherSeries;
  @NotNull private final RangedContinuousSeries myObjectsSeries;

  public DetailedMemoryUsage(@NotNull StudioProfilers profilers) {
    super(profilers);

    myProfilers = profilers;
    myObjectsRange = new Range(0, 0);

    myJavaSeries = createRangedSeries(profilers, "Java", getMemoryRange(), MemorySample::getJavaMem);
    myNativeSeries = createRangedSeries(profilers, "Native", getMemoryRange(), MemorySample::getNativeMem);
    myGraphicsSeries = createRangedSeries(profilers, "Graphics", getMemoryRange(), MemorySample::getGraphicsMem);
    myStackSeries = createRangedSeries(profilers, "Stack", getMemoryRange(), MemorySample::getStackMem);
    myCodeSeries = createRangedSeries(profilers, "Code", getMemoryRange(), MemorySample::getCodeMem);
    myOtherSeries = createRangedSeries(profilers, "Others", getMemoryRange(), MemorySample::getOthersMem);

    MemoryServiceGrpc.MemoryServiceBlockingStub client = profilers.getClient().getMemoryClient();
    AllocStatsDataSeries series = new AllocStatsDataSeries(client, profilers.getProcessId(), profilers.getSession(),
                                                     sample -> (long)(sample.getJavaAllocationCount() - sample.getJavaFreeCount()));
    myObjectsSeries = new RangedContinuousSeries("Allocated", profilers.getTimeline().getViewRange(), getObjectsRange(), series);

    add(myJavaSeries);
    add(myNativeSeries);
    add(myGraphicsSeries);
    add(myStackSeries);
    add(myCodeSeries);
    add(myOtherSeries);

    myProfilers.addDependency(this).onChange(ProfilerAspect.AGENT, this::agentStatusChanged);
    agentStatusChanged();
  }

  @NotNull
  public Range getObjectsRange() {
    return myObjectsRange;
  }

  @NotNull
  public RangedContinuousSeries getJavaSeries() {
    return myJavaSeries;
  }

  @NotNull
  public RangedContinuousSeries getNativeSeries() {
    return myNativeSeries;
  }

  @NotNull
  public RangedContinuousSeries getGraphicsSeries() {
    return myGraphicsSeries;
  }

  @NotNull
  public RangedContinuousSeries getStackSeries() {
    return myStackSeries;
  }

  @NotNull
  public RangedContinuousSeries getCodeSeries() {
    return myCodeSeries;
  }

  @NotNull
  public RangedContinuousSeries getOtherSeries() {
    return myOtherSeries;
  }

  @NotNull
  public RangedContinuousSeries getObjectsSeries() {
    return myObjectsSeries;
  }

  @Override
  protected String getTotalSeriesLabel() {
    return "Total";
  }

  private void agentStatusChanged() {
    if (myProfilers.isAgentAttached()) {
      add(myObjectsSeries);
    }
    else {
      remove(myObjectsSeries);
    }
  }
}
