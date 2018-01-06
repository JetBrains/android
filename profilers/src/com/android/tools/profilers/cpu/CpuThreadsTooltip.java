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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.ProfilerTooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CpuThreadsTooltip extends AspectModel<CpuThreadsTooltip.Aspect> implements ProfilerTooltip {
  public enum Aspect {
    // The hovering thread state changed
    THREAD_STATE,
  }

  @NotNull private final CpuProfilerStage myStage;

  @Nullable private String myThreadName;
  @Nullable private ThreadStateDataSeries mySeries;
  @Nullable private CpuProfilerStage.ThreadState myThreadState;

  CpuThreadsTooltip(@NotNull CpuProfilerStage stage) {
    myStage = stage;
    Range tooltipRange = stage.getStudioProfilers().getTimeline().getTooltipRange();
    tooltipRange.addDependency(this).onChange(Range.Aspect.RANGE, this::updateThreadState);
  }

  @Override
  public void dispose() {
    myStage.getStudioProfilers().getTimeline().getTooltipRange().removeDependencies(this);
  }

  private void updateThreadState() {
    myThreadState = null;
    if (mySeries == null) {
      changed(Aspect.THREAD_STATE);
      return;
    }

    Range tooltipRange = myStage.getStudioProfilers().getTimeline().getTooltipRange();
    // We could get data for [tooltipRange.getMin() - buffer, tooltipRange.getMin() - buffer],
    // However it is tricky to come up with the buffer duration, a thread state can be longer than any buffer.
    // So, lets get data what the user sees and extract the hovered state.
    List<SeriesData<CpuProfilerStage.ThreadState>> series =
      mySeries.getDataForXRange(myStage.getStudioProfilers().getTimeline().getViewRange());

    int threadStateIndex = Collections.binarySearch(
      series,
      new SeriesData<CpuProfilerStage.ThreadState>((long)tooltipRange.getMin(), null), // Dummy object so we can compare.
      Comparator.comparingDouble(seriesData -> seriesData.x)
    );
    // Collections.binarySearch returns (-(insertion point)-1) if not found, in which case we want to find the largest value smaller than
    // tooltip position, which is (insertion point) - 1.
    if (threadStateIndex < 0) {
      threadStateIndex = -threadStateIndex - 2;
    }
    myThreadState = threadStateIndex < 0 ? null : series.get(threadStateIndex).value;
    changed(Aspect.THREAD_STATE);
  }

  void setThread(@Nullable String threadName, @Nullable ThreadStateDataSeries stateSeries) {
    myThreadName = threadName;
    mySeries = stateSeries;
    updateThreadState();
  }

  @Nullable
  public String getThreadName() {
    return myThreadName;
  }

  @Nullable
  CpuProfilerStage.ThreadState getThreadState() {
    return myThreadState;
  }
}
