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

import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SimpleEventModel;
import com.android.tools.adtui.model.StackedEventModel;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class EventMonitor extends ProfilerMonitor {

  @NotNull
  private final SimpleEventModel<EventActionType> mySimpleEvents;

  @NotNull
  private final StackedEventModel myActivityEvents;

  public EventMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);
    SimpleEventDataSeries events = new SimpleEventDataSeries(myProfilers.getClient(),
                                                             myProfilers.getProcessId(),
                                                             myProfilers.getSession());
    mySimpleEvents = new SimpleEventModel<>(new RangedSeries<>(getTimeline().getViewRange(), events));

    ActivityEventDataSeries activities = new ActivityEventDataSeries(myProfilers.getClient(),
                                                                     myProfilers.getProcessId(),
                                                                     myProfilers.getSession());
    myActivityEvents = new StackedEventModel(new RangedSeries<>(getTimeline().getViewRange(), activities));
  }

  @Override
  public void enter() {
    myProfilers.getUpdater().register(mySimpleEvents);
    myProfilers.getUpdater().register(myActivityEvents);
  }

  @Override
  public void exit() {
    myProfilers.getUpdater().unregister(mySimpleEvents);
    myProfilers.getUpdater().unregister(myActivityEvents);
  }

  @NotNull
  public SimpleEventModel<EventActionType> getSimpleEvents() {
    return mySimpleEvents;
  }

  @NotNull
  public StackedEventModel getActivityEvents() {
    return myActivityEvents;
  }

  @Override
  public String getName() {
    return "EVENTS";
  }
}
