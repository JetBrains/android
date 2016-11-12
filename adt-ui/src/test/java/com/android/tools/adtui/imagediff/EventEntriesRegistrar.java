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
package com.android.tools.adtui.imagediff;

import com.android.tools.adtui.SimpleEventComponent;
import com.android.tools.adtui.StackedEventComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.RangedSeries;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

class EventEntriesRegistrar extends ImageDiffEntriesRegistrar {

  public EventEntriesRegistrar() {
    registerAddActivityEvent();
    registerAddRunningActivityEvent();
    registerMultipleStackedActivitiesEvent();
    registerMultipleInlineActivitiesEvent();
    registerSimpleTapEvent();
    registerMultipleTapEvents();
    registerAddActivityBeforeStartEvent();
  }

  private void registerAddActivityEvent() {
    register(new ActivityEventImageDiffEntry("add_activity_event_baseline.png") {
      @Override
      protected void generateComponent() {
        // Set a reasonable thickness to the line so it can fill a good portion of the screen.
        float lineThickness = 7f;
        // Set the height to a low value to reduce the amount of unused space of the component.
        int mainComponentHeight = 50;
        setUpActivityComponent(mainComponentHeight, lineThickness);
      }

      @Override
      protected void generateTestData() {
        long startTime = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(150); // Add some arbitrary offset to range start
        long endTime = myRangeEndUs - TimeUnit.MILLISECONDS.toMicros(600); // Finish activity before range end
        addActivity(startTime, endTime);
      }
    });
  }

  private void registerAddRunningActivityEvent() {
    register(new ActivityEventImageDiffEntry("add_running_activity_event_baseline.png") {
      @Override
      protected void generateComponent() {
        // Set a reasonable thickness to the line so it can fill a good portion of the screen.
        float lineThickness = 7f;
        // Set the height to a low value to reduce the amount of unused space of the component.
        int mainComponentHeight = 50;
        setUpActivityComponent(mainComponentHeight, lineThickness);
      }

      @Override
      protected void generateTestData() {
        long startTime = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(400); // Add some arbitrary offset to range start
        long endTime = myRangeEndUs + 1; // Finish activity after range end
        addActivity(startTime, endTime);
      }
    });
  }

  private void registerAddActivityBeforeStartEvent() {
    register(new ActivityEventImageDiffEntry("add_activity_before_start_event_baseline.png") {
      @Override
      protected void generateComponent() {
        // Set a reasonable thickness to the line so it can fill a good portion of the screen.
        float lineThickness = 7f;
        // Set the height to a low value to reduce the amount of unused space of the component.
        int mainComponentHeight = 50;
        setUpActivityComponent(mainComponentHeight, lineThickness);
      }

      @Override
      protected void generateTestData() {
        long startTime = myRangeStartUs - TimeUnit.MILLISECONDS.toMicros(400); // Add a negative offset to range start
        long endTime = myRangeEndUs - TimeUnit.MILLISECONDS.toMicros(600); // Finish activity before range end
        // Activity should start before the range
        addActivity(startTime, endTime);
      }
    });
  }

  private void registerMultipleStackedActivitiesEvent() {
    register(new ActivityEventImageDiffEntry("add_multiple_stacked_activities_event_baseline.png") {
      @Override
      protected void generateComponent() {
        // Set a reasonable thickness to the line so it can fill a good portion of the screen.
        float lineThickness = 5f;
        // Set the height to a low value to reduce the amount of unused space of the component.
        int mainComponentHeight = 50;
        setUpActivityComponent(mainComponentHeight, lineThickness);
      }

      @Override
      protected void generateTestData() {
        long startTime1 = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(100); // Add some arbitrary offset to range start
        long endTime1 = startTime1 + TimeUnit.MILLISECONDS.toMicros(1500);  // Finish activity after some time
        long startTime2 = endTime1 - TimeUnit.MILLISECONDS.toMicros(600); // Start this activity before the first one ends to stack them
        long endTime2 = myRangeEndUs + 1; // Finish activity after range end
        addMultipleActivities(new long[]{startTime1, startTime2}, new long[]{endTime1, endTime2});
      }
    });
  }

  private void registerMultipleInlineActivitiesEvent() {
    register(new ActivityEventImageDiffEntry("add_multiple_inline_activities_event_baseline.png") {
      @Override
      protected void generateComponent() {
        // Set a reasonable thickness to the line so it can fill a good portion of the screen.
        float lineThickness = 7f;
        // Set the height to a low value to reduce the amount of unused space of the component.
        int mainComponentHeight = 50;
        setUpActivityComponent(mainComponentHeight, lineThickness);
      }

      @Override
      protected void generateTestData() {
        long startTime1 = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(100); // Add some arbitrary offset to range start
        long endTime1 = startTime1 + TimeUnit.MILLISECONDS.toMicros(600);  // Finish activity after some time
        long startTime2 = endTime1 + TimeUnit.MILLISECONDS.toMicros(100); // Start this activity after the first one ends to inline them
        long endTime2 = myRangeEndUs - TimeUnit.MILLISECONDS.toMicros(100); // Finish activity before range end
        // Start and end the activities one after another, to simulates what happens in reality.
        addActivity(startTime1, endTime1);
        addActivity(startTime2, endTime2);
      }
    });
  }

  private void registerSimpleTapEvent() {
    register(new SimpleTapEventImageDiffEntry("simple_tap_event_baseline.png") {

      @Override
      protected void generateTestData() {
        long eventTime = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(500); // Add some arbitrary offset to range start
        performTap(eventTime);
      }
    });
  }

  private void registerMultipleTapEvents() {
    register(new SimpleTapEventImageDiffEntry("multiple_tap_events_baseline.png") {

      @Override
      protected void generateTestData() {
        long eventTime1 = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(500); // Add some arbitrary offset to range start
        long eventTime2 = eventTime1 + TimeUnit.MILLISECONDS.toMicros(1500); // Give some time between the events
        performTap(eventTime1);
        performTap(eventTime2);
      }
    });
  }

  private static abstract class ActivityEventImageDiffEntry extends AnimatedComponentImageDiffEntry {

    protected static final String[] MOCK_ACTIVITY_NAMES = {"SignInActivity", "SplashActivity", "MainActivity"};

    private static final String PACKAGE_PREFIX = "com.example.myapplication.";

    private static final int ACTIVITY_GRAPH_HEIGHT = 31;

    protected StackedEventComponent myStackedEventComponent;

    private DefaultDataSeries<EventAction<EventAction.ActivityAction, String>> myData;

    ActivityEventImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    @Override
    protected void setUp() {
      myData = new DefaultDataSeries<>();
      myStackedEventComponent = new StackedEventComponent(new RangedSeries<>(myXRange, myData), ACTIVITY_GRAPH_HEIGHT);
      myContentPane.add(myStackedEventComponent, BorderLayout.CENTER);
      myComponents.add(myStackedEventComponent);
    }

    protected void setUpActivityComponent(int contentPaneHeight, float eventComponentLineThickness) {
      myStackedEventComponent.setFont(ImageDiffUtil.getDefaultFont());
      myStackedEventComponent.setLineThickness(eventComponentLineThickness);
      Dimension contentPaneDimension = new Dimension(myContentPane.getWidth(), contentPaneHeight);
      myContentPane.setSize(contentPaneDimension);
      myContentPane.setPreferredSize(contentPaneDimension);
    }

    protected void addActivity(long startTime, long endTime) {
      String activityName = PACKAGE_PREFIX + MOCK_ACTIVITY_NAMES[0]; // arbitrary activity

      // Start event
      EventAction<EventAction.ActivityAction, String> event =
        new EventAction<>(startTime, 0, EventAction.ActivityAction.ACTIVITY_STARTED, activityName);
      myData.add(startTime, event);

      // Completed event
      event = new EventAction<>(startTime, endTime, EventAction.ActivityAction.ACTIVITY_COMPLETED, activityName);
      myData.add(endTime, event);
    }

    /**
     * Create all the start events and then the completed events. This is what happens in reality when adding overlapping activities.
     */
    protected void addMultipleActivities(long[] startTimeList, long[] endTimeList) {
      EventAction<EventAction.ActivityAction, String> event;
      for (int i = 0; i  < startTimeList.length; i++) {
        String activityName = PACKAGE_PREFIX + MOCK_ACTIVITY_NAMES[i % MOCK_ACTIVITY_NAMES.length];
        // Start event
        event = new EventAction<>(startTimeList[i], 0, EventAction.ActivityAction.ACTIVITY_STARTED, activityName);
        myData.add(startTimeList[i], event);
      }

      for (int i = 0; i  < endTimeList.length; i++) {
        String activityName = PACKAGE_PREFIX + MOCK_ACTIVITY_NAMES[i % MOCK_ACTIVITY_NAMES.length];
        // Completed event
        event = new EventAction<>(startTimeList[i], endTimeList[i], EventAction.ActivityAction.ACTIVITY_COMPLETED, activityName);
        myData.add(endTimeList[i], event);
      }
    }
  }

  private static abstract class SimpleTapEventImageDiffEntry extends AnimatedComponentImageDiffEntry {

    private static final int ICON_WIDTH = 16;

    private static final int ICON_HEIGHT = 16;

    private static final Icon[] MOCK_ICONS = {
      AdtUiUtils.buildStaticImage(Color.red, ICON_WIDTH, ICON_HEIGHT),
      AdtUiUtils.buildStaticImage(Color.green, ICON_WIDTH, ICON_HEIGHT),
      AdtUiUtils.buildStaticImage(Color.blue, ICON_WIDTH, ICON_HEIGHT)
    };

    /**
     * Enum that defines what Icon to draw for an event action.
     */
    private enum ActionType {
      TOUCH,
      HOLD,
      DOUBLE_TAP
    }

    protected SimpleEventComponent mySimpleEventComponent;

    private DefaultDataSeries<EventAction<EventAction.Action, ActionType>> myData;

    SimpleTapEventImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    @Override
    protected void generateComponent() {
      // Set the main component dimension in a way that the event icon(s) will occupy a good amount of the it.
      Dimension contentPaneDimension = new Dimension(3 * ICON_WIDTH, ICON_HEIGHT);
      myContentPane.setSize(contentPaneDimension);
      myContentPane.setPreferredSize(contentPaneDimension);
    }

    @Override
    protected void setUp() {
      myData = new DefaultDataSeries<>();
      mySimpleEventComponent = new SimpleEventComponent<>(new RangedSeries<>(myXRange, myData), MOCK_ICONS);
      myContentPane.add(mySimpleEventComponent, BorderLayout.CENTER);
      myComponents.add(mySimpleEventComponent);
    }

    protected void performTap(long eventTime) {
      EventAction<EventAction.Action, ActionType> event =
        new EventAction<>(eventTime, 0, EventAction.Action.DOWN, ActionType.HOLD);
      myData.add(eventTime, event);
      event = new EventAction<>(eventTime, eventTime, EventAction.Action.UP, ActionType.TOUCH);
      myData.add(eventTime, event);
    }
  }
}