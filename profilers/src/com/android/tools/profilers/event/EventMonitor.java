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
package com.android.tools.profilers.event;

import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import org.jetbrains.annotations.NotNull;

public class EventMonitor extends ProfilerMonitor {
  private final int myProcessId;

  @NotNull
  private final StudioProfilers myProfilers;

  public EventMonitor(@NotNull StudioProfilers profilers, int pid) {
    myProcessId = pid;
    myProfilers = profilers;
  }

  @Override
  public String getName() {
    return "Events";
  }

  @NotNull
  public RangedSeries<EventAction<EventAction.Action, EventActionType>> getSimpleEvents() {
    SimpleEventDataSeries series = new SimpleEventDataSeries(myProfilers.getClient(), myProcessId);
    return new RangedSeries<>(myProfilers.getViewRange(), series);
  }

  @NotNull
  public RangedSeries<EventAction<EventAction.ActivityAction, String>> getActivityEvents() {
    ActivityEventDataSeries series = new ActivityEventDataSeries(myProfilers.getClient(), myProcessId);
    return new RangedSeries<>(myProfilers.getViewRange(), series);
  }

  public void expand() {
    myProfilers.setStage(new MemoryProfilerStage(myProfilers));
  }
}
