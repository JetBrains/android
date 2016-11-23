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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
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

  public EventMonitorView(@NotNull EventMonitor monitor) {
    super(monitor);
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
    container.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.weighty = 0.5;
    SimpleEventComponent<EventActionType> events =
      new SimpleEventComponent<>(new RangedSeries<>(getMonitor().getTimeline().getViewRange(), getMonitor().getSimpleEvents()), RENDERERS);
    container.add(events, c);
    choreographer.register(events);

    c.gridy = 1;
    c.weighty = 0.5;
    StackedEventComponent activities =
      new StackedEventComponent(new RangedSeries<>(getMonitor().getTimeline().getViewRange(), getMonitor().getActivityEvents()));
    container.add(activities, c);
    choreographer.register(activities);
  }
}
