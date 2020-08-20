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
package com.android.tools.profilers.cpu.systemtrace;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.TooltipModel;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tooltip model for hovering threads in the CPU kernel list.
 */
public class CpuKernelTooltip extends AspectModel<CpuKernelTooltip.Aspect> implements TooltipModel {
  public enum Aspect {
    // Triggered when the CpuThreadSliceInfo being tracked by the tooltip model changes.
    CPU_KERNEL_THREAD_SLICE_INFO,
  }

  @NotNull private final Timeline myTimeline;
  @Nullable private DataSeries<CpuThreadSliceInfo> mySeries;
  @Nullable private CpuThreadSliceInfo myCpuThreadSliceInfo;
  private int myCpuId;
  private final int myProcessId;

  public CpuKernelTooltip(@NotNull Timeline timeline, int pid) {
    myTimeline = timeline;
    myProcessId = pid;
    timeline.getTooltipRange().addDependency(this).onChange(Range.Aspect.RANGE, this::updateState);
  }

  @Override
  public void dispose() {
    myTimeline.getTooltipRange().removeDependencies(this);
  }

  /**
   * Called when the tooltip range changes, or when the user mouses over a thread series.
   */
  private void updateState() {
    myCpuThreadSliceInfo = null;
    if (mySeries == null) {
      changed(Aspect.CPU_KERNEL_THREAD_SLICE_INFO);
      return;
    }

    Range tooltipRange = myTimeline.getTooltipRange();
    List<SeriesData<CpuThreadSliceInfo>> series = mySeries.getDataForRange(tooltipRange);
    myCpuThreadSliceInfo = series.isEmpty() ? null : series.get(0).value;
    if (myCpuThreadSliceInfo == CpuThreadSliceInfo.NULL_THREAD) {
      myCpuThreadSliceInfo = null;
    }
    changed(Aspect.CPU_KERNEL_THREAD_SLICE_INFO);
  }

  public void setCpuSeries(int cpuId, @Nullable DataSeries<CpuThreadSliceInfo> stateSeries) {
    mySeries = stateSeries;
    myCpuId = cpuId;
    updateState();
  }

  @Nullable
  public CpuThreadSliceInfo getCpuThreadSliceInfo() {
    return myCpuThreadSliceInfo;
  }

  public int getCpuId() {
    return myCpuId;
  }

  public int getProcessId() {
    return myProcessId;
  }

  @NotNull
  public Timeline getTimeline() {
    return myTimeline;
  }
}
