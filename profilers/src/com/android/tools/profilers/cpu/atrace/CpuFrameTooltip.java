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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CpuFrameTooltip extends AspectModel<CpuFrameTooltip.Aspect> implements ProfilerTooltip {
  public enum Aspect {
    // The hovering frame state changed
    FRAME_CHANGED,
  }

  @NotNull private final CpuProfilerStage myStage;
  @Nullable private DataSeries<AtraceFrame> mySeries;
  @Nullable private AtraceFrame myFrame;

  public CpuFrameTooltip(@NotNull CpuProfilerStage stage) {
    myStage = stage;
    Range tooltipRange = stage.getStudioProfilers().getTimeline().getTooltipRange();
    tooltipRange.addDependency(this).onChange(Range.Aspect.RANGE, this::updateState);
  }

  @Override
  public void dispose() {
    myStage.getStudioProfilers().getTimeline().getTooltipRange().removeDependencies(this);
  }

  private void updateState() {
    myFrame = null;
    if (mySeries == null) {
      changed(Aspect.FRAME_CHANGED);
      return;
    }

    Range tooltipRange = myStage.getStudioProfilers().getTimeline().getTooltipRange();
    List<SeriesData<AtraceFrame>> series = mySeries.getDataForXRange(tooltipRange);
    myFrame = series.isEmpty() ? null : series.get(0).value;
    changed(Aspect.FRAME_CHANGED);
  }

  public void setFrameSeries(@Nullable DataSeries<AtraceFrame> stateSeries) {
    mySeries = stateSeries;
    updateState();
  }

  @Nullable
  public AtraceFrame getFrame() {
    return myFrame;
  }
}
