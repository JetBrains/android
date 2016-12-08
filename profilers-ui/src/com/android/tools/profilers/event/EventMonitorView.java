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

import com.android.tools.adtui.*;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.profilers.ProfilerMonitorView;
import com.android.tools.profilers.StudioProfilersView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class EventMonitorView extends ProfilerMonitorView<EventMonitor> {

  private static final Map<EventActionType, SimpleEventRenderer> RENDERERS;
  static {
    RENDERERS = new HashMap();
    RENDERERS.put(EventActionType.TOUCH, new TouchEventRenderer());
    RENDERERS.put(EventActionType.ROTATION, new EventIconRenderer("/icons/events/rotate-event.png", "/icons/events/rotate-event_dark.png"));
    RENDERERS.put(EventActionType.KEYBOARD, new EventIconRenderer("/icons/events/keyboard-event.png", "/icons/events/keyboard-event_dark.png"));
  }

  public EventMonitorView(@NotNull StudioProfilersView profilersView, @NotNull EventMonitor monitor) {
    super(profilersView, monitor);
  }

  @Override
  public float getVerticalWeight() {
    /**
     * This forces the monitor to use its specified minimum size
     * Also see {@link ProfilerMonitorView#initialize(Choreographer)}.
     */
    return 0;
  }

  @Override
  protected void populateUi(JPanel container, Choreographer choreographer) {
    container.setLayout(new TabularLayout("*", "*,*"));
    SimpleEventComponent<EventActionType> events =
      new SimpleEventComponent<>(new RangedSeries<>(getMonitor().getTimeline().getViewRange(), getMonitor().getSimpleEvents()), RENDERERS);
    container.add(events, new TabularLayout.Constraint(0, 0));
    choreographer.register(events);

    StackedEventComponent activities =
      new StackedEventComponent(new RangedSeries<>(getMonitor().getTimeline().getViewRange(), getMonitor().getActivityEvents()));
    container.add(activities, new TabularLayout.Constraint(1, 0));
    choreographer.register(activities);
  }
}
