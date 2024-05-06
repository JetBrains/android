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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.LifecycleEvent;
import com.android.tools.adtui.model.event.LifecycleEventModel;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public class EventMonitor extends ProfilerMonitor {

  @NotNull
  private final EventModel<UserEvent> myUserEvents;

  @NotNull
  private final LifecycleEventModel myLifecycleEvents;

  private boolean myEnabled;

  private Supplier<TooltipModel> myTooltipBuilder;

  public EventMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);

    UserEventDataSeries events = new UserEventDataSeries(myProfilers);
    myUserEvents = new EventModel<>(new RangedSeries<>(getTimeline().getViewRange(), events, getTimeline().getDataRange()));

    LifecycleEventDataSeries activities = new LifecycleEventDataSeries(myProfilers, false);
    LifecycleEventDataSeries fragments = new LifecycleEventDataSeries(myProfilers, true);

    myLifecycleEvents = new LifecycleEventModel(
      new RangedSeries<>(getTimeline().getViewRange(), activities, getTimeline().getDataRange()),
      new RangedSeries<>(getTimeline().getViewRange(), fragments, getTimeline().getDataRange()));

    myProfilers.addDependency(this).onChange(ProfilerAspect.AGENT, this::onAgentStatusChanged);
    onAgentStatusChanged();
  }

  @Override
  public void enter() {
  }

  @Override
  public void exit() {
  }

  @NotNull
  public EventModel<UserEvent> getUserEvents() {
    return myUserEvents;
  }

  @NotNull
  public LifecycleEventModel getLifecycleEvents() {
    return myLifecycleEvents;
  }

  @Override
  public String getName() {
    return "EVENTS";
  }

  @Override
  public TooltipModel buildTooltip() {
    if (myTooltipBuilder != null) {
      return myTooltipBuilder.get();
    }
    return new LifecycleTooltip(getTimeline(), getLifecycleEvents());
  }

  public void setTooltipBuilder(Supplier<TooltipModel> tooltipBuilder) {
    myTooltipBuilder = tooltipBuilder;
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
