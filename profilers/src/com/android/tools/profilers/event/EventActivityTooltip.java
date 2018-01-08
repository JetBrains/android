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

import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.ActivityAction;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.StackedEventType;
import com.android.tools.profilers.ProfilerMonitorTooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EventActivityTooltip extends ProfilerMonitorTooltip<EventMonitor> {
  public EventActivityTooltip(@NotNull EventMonitor eventMonitor) {
    super(eventMonitor);
  }

  // Find the activity that overlaps a specific time, the activities are sorted so if there are multiple the first
  // one we encounter will be the one that is presented in the UI.
  @Nullable
  public ActivityAction getActivityAt(double time) {
    List<SeriesData<EventAction<StackedEventType>>> activitySeries =
      getMonitor().getActivityEvents().getRangedSeries().getSeries();
    for (SeriesData<EventAction<StackedEventType>> series : activitySeries) {
      if (series.value.getStartUs() <= time && (series.value.getEndUs() > time || series.value.getEndUs() == 0)) {
        return (ActivityAction)series.value;
      }
    }
    return null;
  }
}
