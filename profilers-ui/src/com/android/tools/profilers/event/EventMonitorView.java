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

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.SimpleEventComponent;
import com.android.tools.adtui.StackedEventComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.profilers.ProfilerMonitorView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class EventMonitorView extends ProfilerMonitorView {

  private static final int ACTIVITY_GRAPH_HEIGHT = 31;

  private static final int ICON_WIDTH = 16;

  private static final int ICON_HEIGHT = 16;

  private static final Icon[] ICONS = {
    AdtUiUtils.buildStaticImage(Color.red, ICON_WIDTH, ICON_HEIGHT),
    AdtUiUtils.buildStaticImage(Color.green, ICON_WIDTH, ICON_HEIGHT),
    AdtUiUtils.buildStaticImage(Color.blue, ICON_WIDTH, ICON_HEIGHT)
  };

  @NotNull
  private final EventMonitor myMonitor;

  public EventMonitorView(@NotNull EventMonitor monitor) {
    myMonitor = monitor;
  }

  @Override
  protected void populateUi(JLayeredPane container, Choreographer choreographer) {
    final JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.weighty = 0.5;
    SimpleEventComponent<EventActionType> events = new SimpleEventComponent<>(myMonitor.getSimpleEvents(), ICONS);
    panel.add(events, c);
    choreographer.register(events);

    c.gridy = 1;
    c.weighty = 0.5;
    StackedEventComponent activities = new StackedEventComponent(myMonitor.getActivityEvents(), ACTIVITY_GRAPH_HEIGHT);
    panel.add(activities, c);
    choreographer.register(activities);

    container.add(panel);
    container.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        Dimension size = e.getComponent().getSize();
        panel.setBounds(0, 0, size.width, size.height);
      }
    });
  }
}
