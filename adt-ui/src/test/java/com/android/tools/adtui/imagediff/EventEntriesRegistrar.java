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

import com.android.tools.adtui.*;
import com.android.tools.adtui.eventrenderer.*;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.event.*;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class EventEntriesRegistrar extends ImageDiffEntriesRegistrar {

  public EventEntriesRegistrar() {
    registerAddActivityEvent();
    registerAddRunningActivityEvent();
    registerMultipleInlineActivitiesEvent();
    registerSimpleTapEvent();
    registerSimpleTapAndHoldEvent();
    registerSimpleEventExpandingComponent();
    registerMultipleTapEvents();
    registerAddActivityBeforeStartEvent();
  }

  private void registerAddActivityEvent() {
    // 1% should be a high enough similarity threshold to take into account the difference across OS/JDK when rendering activity name text.
    float similarityThreshold = 1f;
    register(new ActivityEventImageDiffEntry("event_activity_add_baseline.png", similarityThreshold) {
      @Override
      protected void generateComponent() {
        // Set the height to a low value to reduce the amount of unused space of the component.
        int mainComponentHeight = 50;
        setUpActivityComponent(mainComponentHeight);
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
    // 1% should be a high enough similarity threshold to take into account the difference across OS/JDK when rendering activity name text.
    float similarityThreshold = 1f;
    register(new ActivityEventImageDiffEntry("event_activity_add_running_baseline.png", similarityThreshold) {
      @Override
      protected void generateComponent() {
        // Set the height to a low value to reduce the amount of unused space of the component.
        int mainComponentHeight = 50;
        setUpActivityComponent(mainComponentHeight);
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
    // 1% should be a high enough similarity threshold to take into account the difference across OS/JDK when rendering activity name text.
    float similarityThreshold = 1f;
    register(new ActivityEventImageDiffEntry("event_activity_add_before_start_baseline.png", similarityThreshold) {
      @Override
      protected void generateComponent() {
        // Set the height to a low value to reduce the amount of unused space of the component.
        int mainComponentHeight = 50;
        setUpActivityComponent(mainComponentHeight);
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

  private void registerMultipleInlineActivitiesEvent() {
    // 1.5% should be a high enough similarity threshold to take into account the difference across OS/JDK when rendering activity name text.
    // This threshold is slightly higher than the others on this class because two activity names are rendered.
    float similarityThreshold = 1.5f;
    register(new ActivityEventImageDiffEntry("event_activity_add_multiple_inline_baseline.png", similarityThreshold) {
      @Override
      protected void generateComponent() {
        // Set the height to a low value to reduce the amount of unused space of the component.
        int mainComponentHeight = 50;
        setUpActivityComponent(mainComponentHeight);
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
    register(new SimpleEventImageDiffEntry("event_simple_tap_baseline.png") {

      @Override
      protected void generateTestData() {
        long eventTime = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(500); // Add some arbitrary offset to range start
        performEvent(eventTime, eventTime, EventTypeDiffEntry.TAP);
      }
    });
  }

  private void registerSimpleTapAndHoldEvent() {
    register(new SimpleEventImageDiffEntry("event_simple_tap_and_hold_baseline.png") {

      @Override
      protected void generateTestData() {
        long eventTime = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(500); // Add some arbitrary offset to range start
        performEvent(eventTime, 0, EventTypeDiffEntry.TAP);
      }
    });
  }

  private void registerMultipleTapEvents() {
    register(new SimpleEventImageDiffEntry("event_simple_multiple_tap_baseline.png") {

      @Override
      protected void generateTestData() {
        long eventTime1 = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(500); // Add some arbitrary offset to range start
        long eventTime2 = eventTime1 + TimeUnit.MILLISECONDS.toMicros(600); // Give some time between the events
        performEvent(eventTime1, eventTime1, EventTypeDiffEntry.TAP);
        performEvent(eventTime2, eventTime2, EventTypeDiffEntry.TAP);
      }
    });
  }

  private void registerSimpleEventExpandingComponent() {
    register(new SimpleEventImageDiffEntry("event_simple_expanding_event.png") {

      @Override
      protected void generateTestData() {
        // Add one event for reference.
        long startTime = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(400); // Add some arbitrary offset to range start
        long endTime = startTime + TimeUnit.MILLISECONDS.toMicros(600); // Give some time between the events
        performEvent(startTime, endTime, EventTypeDiffEntry.FRAME_GOOD);

        // Add a second event to mouse over.
        startTime = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(1000); // Add some arbitrary offset to range start
        endTime = startTime + TimeUnit.MILLISECONDS.toMicros(2000); // Give some time between the events
        performEvent(startTime, endTime, EventTypeDiffEntry.FRAME_BAD);

        // Add a third event to show that our mouse over frame is on top of overlapping frames.
        startTime = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(1300); // Add some arbitrary offset to range start
        endTime = startTime + TimeUnit.MILLISECONDS.toMicros(1300); // Give some time between the events
        performEvent(startTime, endTime, EventTypeDiffEntry.FRAME_GOOD);
      }

      @Override
      protected void setUp() {
        super.setUp();
        // Dispatch a mouse moved event to set our mose position.
        mySimpleEventComponent.dispatchEvent(new MouseEvent(mySimpleEventComponent, MouseEvent.MOUSE_MOVED, 0, 0, 32, 0, 0, false));
      }
    });
  }

  private static abstract class ActivityEventImageDiffEntry extends AnimatedComponentImageDiffEntry {

    protected static final String[] MOCK_ACTIVITY_NAMES = {"SignInActivity", "SplashActivity", "MainActivity"};

    private static final String PACKAGE_PREFIX = "com.example.myapplication.";

    protected StackedEventComponent myStackedEventComponent;
    private EventModel<StackedEventType> myStackedEventModel;

    private DefaultDataSeries<EventAction<StackedEventType>> myData;

    ActivityEventImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    ActivityEventImageDiffEntry(String baselineFilename, float similarityThreshold) {
      super(baselineFilename, similarityThreshold);
    }

    @Override
    protected void setUp() {
      myData = new DefaultDataSeries<>();
      myStackedEventModel = new EventModel<>(new RangedSeries<>(myXRange, myData));
      myStackedEventComponent = new StackedEventComponent(myStackedEventModel);
      myStackedEventComponent.setFont(ImageDiffUtil.getDefaultFont());
      myContentPane.add(myStackedEventComponent, BorderLayout.CENTER);
      myComponents.add(myStackedEventModel);
    }

    protected void setUpActivityComponent(int contentPaneHeight) {
      myStackedEventComponent.setFont(ImageDiffUtil.getDefaultFont());
      Dimension contentPaneDimension = new Dimension(myContentPane.getWidth(), contentPaneHeight);
      myContentPane.setSize(contentPaneDimension);
      myContentPane.setPreferredSize(contentPaneDimension);
    }

    protected void addActivity(long startTime, long endTime) {
      String activityName = PACKAGE_PREFIX + MOCK_ACTIVITY_NAMES[0]; // arbitrary activity

      // Start event
      StackedEventType action = StackedEventType.ACTIVITY_COMPLETED;
      myData.add(startTime, new ActivityAction(startTime, endTime, action, activityName));
    }
  }

  private static abstract class SimpleEventImageDiffEntry extends AnimatedComponentImageDiffEntry {
    enum EventTypeDiffEntry {
      TAP,
      FRAME_GOOD,
      FRAME_BAD,
    }

    private static final int ICON_WIDTH = 16;

    private static final int ICON_HEIGHT = 16;

    private static final Map<EventTypeDiffEntry, SimpleEventRenderer> MOCK_RENDERERS;

    static {
      MOCK_RENDERERS = new HashMap();
      MOCK_RENDERERS.put(EventTypeDiffEntry.TAP, new TouchEventRenderer());
      MOCK_RENDERERS.put(EventTypeDiffEntry.FRAME_GOOD, new ExpandingEventRenderer(Color.BLUE));
      MOCK_RENDERERS.put(EventTypeDiffEntry.FRAME_BAD, new ExpandingEventRenderer(Color.RED));
    }

    protected SimpleEventComponent mySimpleEventComponent;

    protected EventModel myEventModel;

    private DefaultDataSeries<EventAction<EventTypeDiffEntry>> myData;

    SimpleEventImageDiffEntry(String baselineFilename) {
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
      myEventModel = new EventModel(new RangedSeries<>(myXRange, myData));
      mySimpleEventComponent = new SimpleEventComponent(myEventModel, MOCK_RENDERERS);
      myContentPane.add(mySimpleEventComponent, BorderLayout.CENTER);
      myComponents.add(myEventModel);
    }

    protected void performEvent(long startTime, long endTime, EventTypeDiffEntry entry) {
      EventAction<EventTypeDiffEntry> event = new EventAction<>(startTime, endTime, entry);
      myData.add(startTime, event);
    }
  }
}