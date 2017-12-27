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
import com.android.tools.adtui.model.event.SimpleEventType;
import com.android.tools.profilers.ProfilerMonitorView;
import com.android.tools.profilers.StudioProfilersView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class EventMonitorView extends ProfilerMonitorView<EventMonitor> {

  private static final Map<SimpleEventType, SimpleEventRenderer<SimpleEventType>> RENDERERS;

  static {
    RENDERERS = new HashMap<>();
    RENDERERS.put(SimpleEventType.TOUCH, new TouchEventRenderer<>());
    RENDERERS.put(SimpleEventType.ROTATION, new EventIconRenderer<>("/icons/events/rotate-event.png"));
    RENDERERS.put(SimpleEventType.KEYBOARD, new KeyboardEventRenderer<>());
  }

  public EventMonitorView(@NotNull StudioProfilersView profilersView, @NotNull EventMonitor monitor) {
    super(monitor);
  }

  @Override
  public float getVerticalWeight() {
    // This forces the monitor to use its specified minimum size
    return 0;
  }

  @Override
  protected void populateUi(JPanel container) {
    container.setLayout(new TabularLayout("*", "*,*"));
    SimpleEventComponent<SimpleEventType> events = new SimpleEventComponent<>(getMonitor().getSimpleEvents(), RENDERERS);
    container.add(events, new TabularLayout.Constraint(0, 0));

    StackedEventComponent component = new StackedEventComponent(getMonitor().getActivityEvents());
    container.add(component, new TabularLayout.Constraint(1, 0));
  }
}
