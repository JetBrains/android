/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.event;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.event.LifecycleAction;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.LifecycleEvent;
import com.android.tools.adtui.model.event.LifecycleEventModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LifecycleTooltip implements TooltipModel {
  @NotNull private final Timeline myTimeline;
  @NotNull private final LifecycleEventModel myEventModel;

  public LifecycleTooltip(@NotNull Timeline timeline, @NotNull LifecycleEventModel eventModel) {
    myTimeline = timeline;
    myEventModel = eventModel;
  }

  @NotNull
  public Timeline getTimeline() {
    return myTimeline;
  }

  // Find the activity that overlaps a specific time, the activities are sorted so if there are multiple the first
  // one we encounter will be the one that is presented in the UI.
  @Nullable
  public LifecycleAction getActivityAt(double time) {
    List<SeriesData<EventAction<LifecycleEvent>>> activitySeries = myEventModel.getActivitySeries().getSeries();
    for (SeriesData<EventAction<LifecycleEvent>> series : activitySeries) {
      if (series.value.getStartUs() <= time && (series.value.getEndUs() > time || series.value.getEndUs() == 0)) {
        return (LifecycleAction)series.value;
      }
    }
    return null;
  }

  @NotNull
  public List<LifecycleAction> getFragmentsAt(@NotNull Range range) {
    List<SeriesData<EventAction<LifecycleEvent>>> fragmentSeries = myEventModel.getFragmentSeries().getSeries();
    ArrayList<LifecycleAction> fragments = new ArrayList<>();
    for (SeriesData<EventAction<LifecycleEvent>> series : fragmentSeries) {
      if (series.value.getStartUs() <= range.getMax() && (series.value.getEndUs() > range.getMin() || series.value.getEndUs() == 0)) {
        fragments.add((LifecycleAction)series.value);
      }
    }
    return fragments;
  }
}
