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

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.SimpleEventComponent;
import com.android.tools.adtui.StackedEventComponent;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.events.model.ActivityEventDataSeries;
import com.android.tools.idea.monitor.ui.events.model.SimpleEventDataSeries;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class EventSegment extends BaseSegment {

  private static final String SEGMENT_NAME = "Events";
  private static final int ACTIVITY_GRAPH_SIZE = 25; // TODO what size is this?

  public enum EventActionType {
    TOUCH,
    HOLD,
    DOUBLE_TAP,
    ROTATION
  }

  @NotNull private SimpleEventComponent mSystemEvents;

  @NotNull private StackedEventComponent mActivityEvents;

  @NotNull private final SeriesDataStore mDataStore;

  @NotNull private Icon[] mIcons;

  //TODO Add labels for series data.

  public EventSegment(
      @NotNull Range timeCurrentRangeUs,
      @NotNull SeriesDataStore dataStore,
      @NotNull Icon[] icons,
      @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(SEGMENT_NAME, timeCurrentRangeUs, dispatcher);
    mDataStore = dataStore;
    mIcons = icons;
  }

  @Override
  public void createComponentsList(@NotNull List<Animatable> animatables) {
    SimpleEventDataSeries systemEventData = new SimpleEventDataSeries(mDataStore.getDeviceProfilerService());
    ActivityEventDataSeries activityEventData = new ActivityEventDataSeries(mDataStore.getDeviceProfilerService());

    mSystemEvents = new SimpleEventComponent(new RangedSeries<>(myTimeCurrentRangeUs, systemEventData), mIcons);
    mActivityEvents = new StackedEventComponent(new RangedSeries<>(myTimeCurrentRangeUs, activityEventData), ACTIVITY_GRAPH_SIZE);

    animatables.add(mSystemEvents);
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
    layeredPane.add(mActivityEvents, gbc);
    panel.add(layeredPane, BorderLayout.CENTER);
  }
}
