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
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.TooltipModel;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CpuThreadsTooltip extends AspectModel<CpuThreadsTooltip.Aspect> implements TooltipModel {
  public enum Aspect {
    // The hovering thread state changed
    THREAD_STATE,
  }

  @NotNull private final Timeline myTimeline;

  @Nullable private String myThreadName;
  @Nullable private DataSeries<ThreadState> mySeries;
  @Nullable private ThreadState myThreadState;
  private long myDurationUs;

  public CpuThreadsTooltip(@NotNull Timeline timeline) {
    myTimeline = timeline;
    myTimeline.getTooltipRange().addDependency(this).onChange(Range.Aspect.RANGE, this::updateThreadState);
  }

  @Override
  public void dispose() {
    myTimeline.getTooltipRange().removeDependencies(this);
  }

  private void updateThreadState() {
    myThreadState = null;
    myDurationUs = 0;
    if (mySeries == null) {
      changed(Aspect.THREAD_STATE);
      return;
    }

    Range tooltipRange = myTimeline.getTooltipRange();
    // We could get data for [tooltipRange.getMin() - buffer, tooltipRange.getMin() - buffer],
    // However it is tricky to come up with the buffer duration, a thread state can be longer than any buffer.
    // So, lets get data what the user sees and extract the hovered state.
    List<SeriesData<ThreadState>> series = mySeries.getDataForRange(myTimeline.getViewRange());

    int threadStateIndex = Collections.binarySearch(
      series,
      new SeriesData<ThreadState>((long)tooltipRange.getMin(), null), // Sample object so we can compare.
      Comparator.comparingDouble(seriesData -> seriesData.x)
    );
    // Collections.binarySearch returns (-(insertion point)-1) if not found, in which case we want to find the largest value smaller than
    // tooltip position, which is (insertion point) - 1.
    if (threadStateIndex < 0) {
      threadStateIndex = -threadStateIndex - 2;
    }
    if (threadStateIndex >= 0) {
      SeriesData<ThreadState> currentState = series.get(threadStateIndex);
      myThreadState = currentState.value;
      // If the state is not the last of the list, calculate the duration from the difference between the current and the next timestamps
      // TODO(b/80240220): Calculate the duration of the last state, unless it's in progress.
      if (threadStateIndex < series.size() - 1) {
        myDurationUs = series.get(threadStateIndex + 1).x - currentState.x;
      }
    }

    changed(Aspect.THREAD_STATE);
  }

  public void setThread(@Nullable String threadName, @Nullable DataSeries<ThreadState> stateSeries) {
    myThreadName = threadName;
    mySeries = stateSeries;
    updateThreadState();
  }

  @Nullable
  public String getThreadName() {
    return myThreadName;
  }

  @Nullable
  ThreadState getThreadState() {
    return myThreadState;
  }

  public long getDurationUs() {
    return myDurationUs;
  }

  @NotNull
  public Timeline getTimeline() {
    return myTimeline;
  }
}
