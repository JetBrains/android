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
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.SimpleEventType;
import com.android.tools.adtui.model.event.StackedEventType;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.ProfilerTooltip;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class EventMonitor extends ProfilerMonitor {

  @NotNull
  private final EventModel<SimpleEventType> mySimpleEvents;

  @NotNull
  private final EventModel<StackedEventType> myActivityEvents;

  @NotNull
  private final EventModel<StackedEventType> myFragmentEvents;

  private boolean myEnabled;

  public EventMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);
    SimpleEventDataSeries events = new SimpleEventDataSeries(myProfilers.getClient(),
                                                             myProfilers.getSession());
    mySimpleEvents = new EventModel<>(new RangedSeries<>(getTimeline().getViewRange(), events));

    ActivityEventDataSeries activities = new ActivityEventDataSeries(myProfilers.getClient(),
                                                                     myProfilers.getSession(),
                                                                     false);
    myActivityEvents = new EventModel<>(new RangedSeries<>(getTimeline().getViewRange(), activities));

    ActivityEventDataSeries fragments = new ActivityEventDataSeries(myProfilers.getClient(),
                                                                     myProfilers.getSession(),
                                                                    true);
    myFragmentEvents = new EventModel<>(new RangedSeries<>(getTimeline().getViewRange(), fragments));

    myProfilers.addDependency(this).onChange(ProfilerAspect.AGENT, this::onAgentStatusChanged);
    onAgentStatusChanged();
  }

  @Override
  public void enter() {
    myProfilers.getUpdater().register(mySimpleEvents);
    myProfilers.getUpdater().register(myActivityEvents);
    myProfilers.getUpdater().register(myFragmentEvents);
  }

  @Override
  public void exit() {
    myProfilers.getUpdater().unregister(mySimpleEvents);
    myProfilers.getUpdater().unregister(myActivityEvents);
    myProfilers.getUpdater().unregister(myFragmentEvents);
  }

  @NotNull
  public EventModel<SimpleEventType> getSimpleEvents() {
    return mySimpleEvents;
  }

  @NotNull
  public EventModel<StackedEventType> getActivityEvents() {
    return myActivityEvents;
  }

  @NotNull
  public EventModel<StackedEventType> getFragmentEvents() {
    return myFragmentEvents;
  }

  @Override
  public String getName() {
    return "EVENTS";
  }

  @Override
  public ProfilerTooltip buildTooltip() {
    return new EventMonitorTooltip(this);
  }

  @Override
  public boolean canExpand() {
    return false;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  private void onAgentStatusChanged() {
    boolean agentAttached = myProfilers.isAgentAttached();
    if (myEnabled != agentAttached) {
      myEnabled = agentAttached;
      changed(Aspect.ENABLE);
    }
  }
}
