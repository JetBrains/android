/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.atrace;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.ProfilerTooltip;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tooltip model for hovering threads in the CPU kernel list.
 */
public class CpuKernelTooltip extends AspectModel<CpuKernelTooltip.Aspect> implements ProfilerTooltip {
  public enum Aspect {
    // Triggered when the CpuThreadInfo being tracked by the tooltip model changes.
    CPU_KERNEL_THREAD_INFO,
  }

  @NotNull private final CpuProfilerStage myStage;
  @Nullable private DataSeries<CpuThreadInfo> mySeries;
  @Nullable private CpuThreadInfo myCpuThreadInfo;

  public CpuKernelTooltip(@NotNull CpuProfilerStage stage) {
    myStage = stage;
    Range tooltipRange = stage.getStudioProfilers().getTimeline().getTooltipRange();
    tooltipRange.addDependency(this).onChange(Range.Aspect.RANGE, this::updateState);
  }

  @Override
  public void dispose() {
    myStage.getStudioProfilers().getTimeline().getTooltipRange().removeDependencies(this);
  }

  /**
   * Called when the tooltip range changes, or when the user mouses over a thread series.
   */
  private void updateState() {
    myCpuThreadInfo = null;
    if (mySeries == null) {
      changed(Aspect.CPU_KERNEL_THREAD_INFO);
      return;
    }

    Range tooltipRange = myStage.getStudioProfilers().getTimeline().getTooltipRange();
    List<SeriesData<CpuThreadInfo>> series = mySeries.getDataForXRange(tooltipRange);
    myCpuThreadInfo = series.isEmpty() ? null : series.get(0).value;
    if (myCpuThreadInfo == CpuThreadInfo.NULL_THREAD) {
      myCpuThreadInfo = null;
    }
    changed(Aspect.CPU_KERNEL_THREAD_INFO);
  }

  public void setCpuSeries(@Nullable DataSeries<CpuThreadInfo> stateSeries) {
    mySeries = stateSeries;
    updateState();
  }

  @Nullable
  public CpuThreadInfo getCpuThreadInfo() {
    return myCpuThreadInfo;
  }
}
