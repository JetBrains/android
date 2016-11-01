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
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.datastore.DataStoreSeries;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.events.model.SimpleEventDataSeries;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class EventSegment extends BaseSegment {

  private static final String SEGMENT_NAME = "Events";
  private static final int ACTIVITY_GRAPH_SIZE = 25; // TODO what size is this?
  private static final int FRAGMENT_GRAPH_SIZE = 25;
  private static final int WAKE_LOCK_GRAPH_SIZE = 25;

  public enum EventActionType {
    TOUCH,
    HOLD,
    DOUBLE_TAP,
    ROTATION
  }

  @NotNull private SimpleEventComponent mSystemEvents;

  @NotNull private StackedEventComponent mFragmentEvents;

  @NotNull private StackedEventComponent mActivityEvents;

  @NotNull private StackedEventComponent mWakeLockEvents;

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
    DataStoreSeries<EventAction<StackedEventComponent.Action, String>> fragmentEventData =
        new DataStoreSeries<>(mDataStore, SeriesDataType.EVENT_FRAGMENT_ACTION);
    DataStoreSeries<EventAction<StackedEventComponent.Action, String>> activityEventData =
        new DataStoreSeries<>(mDataStore, SeriesDataType.EVENT_ACTIVITY_ACTION);
    DataStoreSeries<EventAction<StackedEventComponent.Action, String>> wakeLockEventData =
        new DataStoreSeries<>(mDataStore, SeriesDataType.EVENT_WAKE_LOCK_ACTION);

    mSystemEvents =
        new SimpleEventComponent(new RangedSeries<>(myTimeCurrentRangeUs, systemEventData), mIcons);
    mFragmentEvents =
        new StackedEventComponent(
            new RangedSeries<>(myTimeCurrentRangeUs, fragmentEventData), FRAGMENT_GRAPH_SIZE);
    mActivityEvents =
        new StackedEventComponent(
            new RangedSeries<>(myTimeCurrentRangeUs, activityEventData), ACTIVITY_GRAPH_SIZE);
    mWakeLockEvents =
        new StackedEventComponent(
            new RangedSeries<>(myTimeCurrentRangeUs, wakeLockEventData), WAKE_LOCK_GRAPH_SIZE);

    animatables.add(mSystemEvents);
    animatables.add(mFragmentEvents);
    animatables.add(mActivityEvents);
    animatables.add(mWakeLockEvents);
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
    gbc.gridy = 3;
    layeredPane.add(mWakeLockEvents, gbc);
    panel.add(layeredPane, BorderLayout.CENTER);
  }
}
