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
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.LifecycleEventModel;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public class EventMonitor extends ProfilerMonitor {

  @NotNull
  private final EventModel<UserEvent> myUserEvents;

  @NotNull
  private final LifecycleEventModel myLifecycleEvents;

  private boolean myEnabled;

  private Common.AgentData.Status myAgentStatus = Common.AgentData.Status.UNSPECIFIED;

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

  /**
   * Before an agent attaches itself to process its status is UNSPECIFIED. If an agent is able to attach itself within a specific timeframe
   * its status is set to ATTACHED. The timeframe is defined in begin_session.cc (kAgentStatusRateUs, kAgentStatusRetries). If unable to
   * attach within timeframe it sets its status to UNATTACHABLE.
   */
  private void onAgentStatusChanged() {
    boolean agentAttached = myProfilers.isAgentAttached();
    Common.AgentData.Status agentStatus = myProfilers.getAgentData().getStatus();
    if (myAgentStatus != agentStatus) {
      myEnabled = agentAttached;
      changed(Aspect.ENABLE);
    }
    myAgentStatus = agentStatus;
  }
}
