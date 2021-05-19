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

public class CpuFrameTooltip extends AspectModel<CpuFrameTooltip.Aspect> implements TooltipModel {
  public enum Aspect {
    // The hovering frame state changed
    FRAME_CHANGED,
  }

  @NotNull private Timeline myTimeline;
  @Nullable private DataSeries<SystemTraceFrame> mySeries;
  @Nullable private SystemTraceFrame myFrame;

  public CpuFrameTooltip(@NotNull Timeline timeline) {
    myTimeline = timeline;
    myTimeline.getTooltipRange().addDependency(this).onChange(Range.Aspect.RANGE, this::updateState);
  }

  @Override
  public void dispose() {
    myTimeline.getTooltipRange().removeDependencies(this);
  }

  private void updateState() {
    myFrame = null;
    if (mySeries == null) {
      changed(Aspect.FRAME_CHANGED);
      return;
    }

    List<SeriesData<SystemTraceFrame>> series = mySeries.getDataForRange(myTimeline.getTooltipRange());
    myFrame = series.isEmpty() ? null : series.get(0).value;
    changed(Aspect.FRAME_CHANGED);
  }

  public void setFrameSeries(@Nullable DataSeries<SystemTraceFrame> stateSeries) {
    mySeries = stateSeries;
    updateState();
  }

  @Nullable
  public SystemTraceFrame getFrame() {
    return myFrame;
  }

  @NotNull
  public Timeline getTimeline() {
    return myTimeline;
  }
}
