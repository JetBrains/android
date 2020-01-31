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

import com.android.tools.adtui.ActivityComponent;
import com.android.tools.adtui.EventComponent;
import com.android.tools.adtui.eventrenderer.EventRenderer;
import com.android.tools.adtui.eventrenderer.ExpandingEventRenderer;
import com.android.tools.adtui.eventrenderer.TouchEventRenderer;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.LifecycleAction;
import com.android.tools.adtui.model.event.LifecycleEvent;
import com.android.tools.adtui.model.event.LifecycleEventModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
    register(new EventIconsImageDiffEntry("event_simple_tap_baseline.png") {

      @Override
      protected void generateTestData() {
        long eventTime = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(500); // Add some arbitrary offset to range start
        performEvent(eventTime, eventTime, EventTypeDiffEntry.TAP);
      }
    });
  }

  private void registerSimpleTapAndHoldEvent() {
    register(new EventIconsImageDiffEntry("event_simple_tap_and_hold_baseline.png") {

      @Override
      protected void generateTestData() {
        long eventTime = myRangeStartUs + TimeUnit.MILLISECONDS.toMicros(500); // Add some arbitrary offset to range start
        performEvent(eventTime, 0, EventTypeDiffEntry.TAP);
      }
    });
  }

  private void registerMultipleTapEvents() {
    register(new EventIconsImageDiffEntry("event_simple_multiple_tap_baseline.png") {

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
    register(new EventIconsImageDiffEntry("event_simple_expanding_event.png") {

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
        myEventComponent.dispatchEvent(new MouseEvent(myEventComponent, MouseEvent.MOUSE_MOVED, 0, 0, 32, 0, 0, false));
      }
    });
  }

  private static abstract class ActivityEventImageDiffEntry extends AnimatedComponentImageDiffEntry {

    protected static final String[] MOCK_ACTIVITY_NAMES = {"SignInActivity", "SplashActivity", "MainActivity"};

    private static final String PACKAGE_PREFIX = "com.example.myapplication.";

    protected ActivityComponent myActivityComponent;

    private DefaultDataSeries<EventAction<LifecycleEvent>> myActivityData;
    private DefaultDataSeries<EventAction<LifecycleEvent>> myFragmentData;

    ActivityEventImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    ActivityEventImageDiffEntry(String baselineFilename, float similarityThreshold) {
      super(baselineFilename, similarityThreshold);
    }

    @Override
    protected void setUp() {
      myActivityData = new DefaultDataSeries<>();
      myFragmentData = new DefaultDataSeries<>();
      myActivityComponent = new ActivityComponent(new LifecycleEventModel(new RangedSeries<>(myXRange, myActivityData),
                                                                          new RangedSeries<>(myXRange, myFragmentData)));
      myActivityComponent.setFont(ImageDiffUtil.getDefaultFont());
      myContentPane.add(myActivityComponent, BorderLayout.CENTER);
    }

    protected void setUpActivityComponent(int contentPaneHeight) {
      myActivityComponent.setFont(ImageDiffUtil.getDefaultFont());
      Dimension contentPaneDimension = new Dimension(myContentPane.getWidth(), contentPaneHeight);
      myContentPane.setSize(contentPaneDimension);
      myContentPane.setPreferredSize(contentPaneDimension);
    }

    protected void addActivity(long startTime, long endTime) {
      String activityName = PACKAGE_PREFIX + MOCK_ACTIVITY_NAMES[0]; // arbitrary activity

      // Start event
      LifecycleEvent action = LifecycleEvent.COMPLETED;
      myActivityData.add(startTime, new LifecycleAction(startTime, endTime, action, activityName));
    }
  }

  private static abstract class EventIconsImageDiffEntry extends AnimatedComponentImageDiffEntry {
    enum EventTypeDiffEntry {
      TAP,
      FRAME_GOOD,
      FRAME_BAD,
    }

    private static final int ICON_WIDTH = 16;

    private static final int ICON_HEIGHT = 16;

    private static final Map<EventTypeDiffEntry, EventRenderer<EventTypeDiffEntry>> MOCK_RENDERERS;

    static {
      MOCK_RENDERERS = new HashMap<>();
      MOCK_RENDERERS.put(EventTypeDiffEntry.TAP, new TouchEventRenderer<>());
      MOCK_RENDERERS.put(EventTypeDiffEntry.FRAME_GOOD, new ExpandingEventRenderer<>(Color.BLUE));
      MOCK_RENDERERS.put(EventTypeDiffEntry.FRAME_BAD, new ExpandingEventRenderer<>(Color.RED));
    }

    protected EventComponent<EventTypeDiffEntry> myEventComponent;

    protected EventModel<EventTypeDiffEntry> myEventModel;

    private DefaultDataSeries<EventAction<EventTypeDiffEntry>> myData;

    EventIconsImageDiffEntry(String baselineFilename) {
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
      myEventModel = new EventModel<>(new RangedSeries<>(myXRange, myData));
      myEventComponent = new EventComponent<>(myEventModel, MOCK_RENDERERS);
      myContentPane.add(myEventComponent, BorderLayout.CENTER);
    }

    protected void performEvent(long startTime, long endTime, EventTypeDiffEntry entry) {
      EventAction<EventTypeDiffEntry> event = new EventAction<>(startTime, endTime, entry);
      myData.add(startTime, event);
    }
  }
}