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
package com.android.tools.idea.monitor.ui.events.view;

import com.android.tools.adtui.*;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.RangedSimpleSeries;
import com.android.tools.idea.monitor.ui.BaseSegment;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class EventSegment<E extends Enum<E>> extends BaseSegment {

  private static final String SEGMENT_NAME = "Events";
  private static final int ACTIVITY_GRAPH_SIZE = 25;
  private static final int FRAGMENT_GRAPH_SIZE = 25;

  @NotNull
  private SimpleEventComponent mSystemEvents;

  @NotNull
  private StackedEventComponent mFragmentEvents;

  @NotNull
  private StackedEventComponent mActivityEvents;

  @NotNull
  private final RangedSimpleSeries<EventAction<SimpleEventComponent.Action, E>> mSystemEventData;

  @NotNull
  private final RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> mFragmentEventData;

  @NotNull
  private final RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> mActivityEventData;

  @NotNull
  private BufferedImage[] mIcons;

  //TODO Add labels for series data.

  public EventSegment(@NotNull Range scopedRange,
                      @NotNull RangedSimpleSeries<EventAction<SimpleEventComponent.Action, E>> systemData,
                      @NotNull RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> fragmentData,
                      @NotNull RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> activityData,
                      @NotNull BufferedImage[] icons) {
    super(SEGMENT_NAME, scopedRange);
    mSystemEventData = systemData;
    mFragmentEventData = fragmentData;
    mActivityEventData = activityData;
    mIcons = icons;
  }

  @Override
  public void createComponentsList(@NotNull List<Animatable> animatables) {
    mSystemEvents = new SimpleEventComponent(mSystemEventData, mIcons);
    mFragmentEvents = new StackedEventComponent(FRAGMENT_GRAPH_SIZE, mFragmentEventData);
    mActivityEvents = new StackedEventComponent(ACTIVITY_GRAPH_SIZE, mActivityEventData);

    animatables.add(mSystemEvents);
    animatables.add(mFragmentEvents);
    animatables.add(mActivityEvents);
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    JPanel layeredPane = new JPanel();
    layeredPane.setLayout(new GridBagLayout());
    //Divide up the space equally amongst the child components. Each of these may change to a specific height
    //as decided by design.
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.gridx = 0;

    gbc.gridy = 0;
    layeredPane.add(mSystemEvents, gbc);
    gbc.gridy = 1;
    layeredPane.add(mFragmentEvents, gbc);
    gbc.gridy = 2;
    layeredPane.add(mActivityEvents, gbc);
    panel.add(layeredPane, BorderLayout.CENTER);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    assert mIcons.length > 0 && mIcons[0] != null;
    return new Dimension(size.width, (ACTIVITY_GRAPH_SIZE + FRAGMENT_GRAPH_SIZE) * 2 + mIcons[0].getHeight());
  }
}
