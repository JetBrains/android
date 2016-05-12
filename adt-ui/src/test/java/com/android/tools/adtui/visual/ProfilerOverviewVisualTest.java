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

package com.android.tools.adtui.visual;

import com.android.annotations.NonNull;
import com.android.tools.adtui.*;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedSimpleSeries;
import com.android.tools.adtui.segment.EventSegment;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ProfilerOverviewVisualTest extends VisualTest {

  private static final int EVENT_HEIGHT = 100;
  private static final int MONITOR_MAX_HEIGHT = Integer.MAX_VALUE;
  private static final int MONITOR_PREFERRED_HEIGHT = 200;
  private static final int IMAGE_WIDTH = 16;
  private static final int IMAGE_HEIGHT = 16;
  private static final float CLICK_PROBABILITY = 1/5.0f; // 1 in 5 chance to click and release
  private static final float FRAGMENT_PROBABILITY = 1/10.0f; // 1 in 10 change to create / destroy a fragment
  private static final float ACTIVITY_PROBABILITY = 1/20.0f; // 1 in 20 chance to create / destroy a activity
  private static final double CREATE_DESTROY_PROBABILITY = .5; // 50% chance to create a new fragment/activity
  private static final int EVENT_LIMIT = 5; // create a maximum of X fragment/activities;

  private static final int TIME_AXIS_HEIGHT = 20;

  private static final int DATA_DELAY_MS = 100;

  //TODO refactor this to a common location.
  private static BufferedImage buildStaticImage(Color color) {
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT,
                                            BufferedImage.TYPE_4BYTE_ABGR);
    for (int y = 0; y < IMAGE_HEIGHT; y++) {
      for (int x = 0; x < IMAGE_WIDTH; x++) {
        image.setRGB(x, y, color.getRGB());
      }
    }
    return image;
  }

  private static final BufferedImage[] MOCK_ICONS = {
    buildStaticImage(Color.red),
    buildStaticImage(Color.green),
    buildStaticImage(Color.blue),
  };

  private long mStartTimeMs;

  @NonNull
  private Range mXRange;

  @NonNull
  private Range mXGlobalRange;

  @NonNull
  private AnimatedTimeRange mAnimatedTimeRange;

  @NonNull
  private Range mXSelectionRange;

  @NonNull
  private AxisComponent mTimeAxis;

  @NonNull
  private SelectionComponent mSelection;

  @NonNull
  private RangeScrollbar mScrollbar;

  private ArrayList<RangedContinuousSeries> mData;

  private JPanel mSegmentsContainer;

  private AccordionLayout mLayout;

  private EventSegment mEventSegment;

  private RangedSimpleSeries<EventAction<SimpleEventComponent.Action, EventVisualTest.ActionType>> mSystemEventData;

  private RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> mFragmentEventData;

  private RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> mActivityEventData;

  @Override
  protected List<Animatable> createComponentsList() {
    mData = new ArrayList();

    mStartTimeMs = System.currentTimeMillis();
    mXRange = new Range();
    mXGlobalRange = new Range();
    mAnimatedTimeRange = new AnimatedTimeRange(mXGlobalRange, mStartTimeMs);
    mXSelectionRange = new Range();

    mScrollbar = new RangeScrollbar(mXGlobalRange, mXRange);

    mSystemEventData = new RangedSimpleSeries<>(mXGlobalRange);
    mFragmentEventData = new RangedSimpleSeries<>(mXGlobalRange);
    mActivityEventData = new RangedSimpleSeries<>(mXGlobalRange);
    mEventSegment = new EventSegment(mXGlobalRange, mSystemEventData, mFragmentEventData, mActivityEventData, MOCK_ICONS);

    // add horizontal time axis
    mTimeAxis = new AxisComponent(mXRange,
                                  mXGlobalRange,
                                  "TIME",
                                  AxisComponent.AxisOrientation.BOTTOM,
                                  0,
                                  0,
                                  false,
                                  new TimeAxisDomain(10, 5, 5));

    mSegmentsContainer = new JPanel();
    mLayout = new AccordionLayout(mSegmentsContainer, AccordionLayout.Orientation.VERTICAL);
    mSegmentsContainer.setLayout(mLayout);

    mSelection = new SelectionComponent(mSegmentsContainer,
                                        mTimeAxis,
                                        mXSelectionRange,
                                        mXGlobalRange,
                                        mXRange);

    List<Animatable> componentsList = new ArrayList<>();
    componentsList.add(mLayout);
    componentsList.add(mAnimatedTimeRange);    // Update global range immediate.
    componentsList.add(mSelection);            // Update selection range immediate.
    componentsList.add(mScrollbar);            // Update current range immediate.
    componentsList.add(mTimeAxis);             // Read ranges.
    componentsList.add(mXRange);               // Reset flags.
    componentsList.add(mXGlobalRange);         // Reset flags.
    componentsList.add(mXSelectionRange);      // Reset flags.
    mEventSegment.createComponentsList(componentsList);
    return componentsList;
  }

  @Override
  protected void registerComponents(List<AnimatedComponent> components) {
    mEventSegment.registerComponents(components);
    components.add(mSelection);
  }

  @Override
  public String getName() {
    return "L1";
  }

  @Override
  public void populateUi(JPanel panel) {
    mUpdateDataThread = new Thread() {
      @Override
      public void run() {
        super.run();
        try {
          boolean downEvent = false;
          // We need to cache the lifetime of fragments/activities to be represenative of
          // Actual event data that we will get from the device.
          ArrayList<Long> fragments = new ArrayList<>();
          ArrayList<Long> activities = new ArrayList<>();
          while (true) {
            long now = System.currentTimeMillis() - mStartTimeMs;
            for (RangedContinuousSeries rangedSeries : mData) {
              int size = rangedSeries.getSeries().size();
              long last = size > 0 ? rangedSeries.getSeries().getY(size - 1) : 0;
              float delta = 10 * ((float)Math.random() - 0.45f);
              rangedSeries.getSeries().add(now, (long)((last + delta) * Math.random()));
            }
            downEvent = generateEventData(downEvent, fragments, activities, now);
            generateStackedEventData(mFragmentEventData, fragments, now, FRAGMENT_PROBABILITY);
            generateStackedEventData(mActivityEventData, activities, now, ACTIVITY_PROBABILITY);
            Thread.sleep(DATA_DELAY_MS);
          }
        }
        catch (InterruptedException e) {
        }
      }
    };
    mUpdateDataThread.start();
    mEventSegment.initializeComponents();

    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel gridBagPanel = new JPanel();
    gridBagPanel.setLayout(gbl);

    // TODO create some controls.
    final JPanel controls = VisualTests.createControlledPane(panel, gridBagPanel);

    // Add Mock Toolbar
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.gridwidth = 4;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gridBagPanel.add(createToolbarPanel(), gbc);

    // Add Selection Overlay
    // TODO define sizes for x columns 0 and 1
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy = 1;
    gbc.gridx = 2;
    gbc.gridwidth = 1;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gridBagPanel.add(mSelection, gbc);

    // Add Accordion Control
    gbc.gridx = 0;
    gbc.gridwidth = 4;  // LABEL, AXIS, CHART, RIGHT SPACE
    gbc.gridy = 1;
    gbc.weighty = 1;
    gbc.weightx = 1;
    gridBagPanel.add(mSegmentsContainer, gbc);

    // Add Scrollbar
    gbc.gridy = 2;
    gbc.weighty = 0;
    gridBagPanel.add(mScrollbar, gbc);

    mSegmentsContainer.add(mEventSegment);

    // Mock monitor segments.
    JComponent networkPanel = createMonitorPanel("Network", 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);
    JComponent memoryPanel = createMonitorPanel("Memory", 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);
    JComponent cpuPanel = createMonitorPanel("CPU", 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);
    JComponent gpuPanel = createMonitorPanel("GPU", 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);
    mSegmentsContainer.add(networkPanel);
    mSegmentsContainer.add(memoryPanel);
    mSegmentsContainer.add(cpuPanel);
    mSegmentsContainer.add(gpuPanel);

    // Timeline
    mSegmentsContainer.add(createTimeAxisPanel());
  }

  private JComponent createToolbarPanel() {
    JPanel panel = new JPanel();
    BoxLayout layout = new BoxLayout(panel, BoxLayout.X_AXIS);
    panel.setLayout(layout);
    panel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

    JComboBox<String> deviceCb = new JComboBox(new String[]{"Device1", "Device2"});
    deviceCb.addActionListener(e -> getChoreographer().reset());
    JComboBox<String> processCb = new JComboBox(new String[]{"Process1", "Process2"});
    processCb.addActionListener(e -> getChoreographer().reset());

    panel.add(deviceCb);
    panel.add(processCb);
    return panel;
  }

  // TODO switch to using Segments when they are ready.
  private JComponent createMonitorPanel(String name, int minHeight, int preferredHeight, int maxHeight) {
    LineChart lineChart = new LineChart(name);
    lineChart.setMinimumSize(new Dimension(0, minHeight));
    lineChart.setPreferredSize(new Dimension(0, preferredHeight));
    lineChart.setMaximumSize(new Dimension(0, maxHeight));
    lineChart.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    addToChoreographer(lineChart);

    Range mYRange = new Range();
    for (int i = 0; i < 2; i++) {
      if (i % 2 == 0) {
        mYRange = new Range();
        addToChoreographer(mYRange);
      }
      RangedContinuousSeries ranged = new RangedContinuousSeries("Widgets", mXRange, mYRange,
                                                                 TimeAxisDomain.DEFAULT, MemoryAxisDomain.DEFAULT);
      mData.add(ranged);
      lineChart.addLine(ranged);
    }

    return lineChart;
  }

  // TODO account for Label and Axis column width.
  private JComponent createTimeAxisPanel() {
    mTimeAxis.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    mTimeAxis.setMinimumSize(new Dimension(0, TIME_AXIS_HEIGHT));
    mTimeAxis.setPreferredSize(mTimeAxis.getMinimumSize());
    mTimeAxis.setMaximumSize(mTimeAxis.getMinimumSize());
    return mTimeAxis;
  }

  private void generateStackedEventData(RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> rangedSeries,
                                        List<Long> activeEvents,
                                        long deltaTime,
                                        float probability) {
    //Determine if we are going to generate an event.
    if (Math.random() < probability) {
      //If we decide we can generate an event, we can generate either a started event, or completed event.
      //If we do not have any events we will generate a started event.
      //If we are at our event limit we will generate a completed event.
      if ((Math.random() < CREATE_DESTROY_PROBABILITY || activeEvents.size() == 0) && activeEvents.size() != EVENT_LIMIT) {
        rangedSeries.getSeries().add(new EventAction<>(deltaTime, 0, StackedEventComponent.Action.ACTIVITY_STARTED, "Widgets"));
        activeEvents.add(deltaTime);
      } else {
        long startTime = activeEvents.remove(activeEvents.size() - 1);
        rangedSeries.getSeries().add(new EventAction<>(startTime, deltaTime, StackedEventComponent.Action.ACTIVITY_COMPLETED, "Widgets"));
      }
    }
  }

  private boolean generateEventData(boolean downEvent, List<Long> fragments, List<Long> activities, long deltaTime) {
    boolean downState = downEvent;
    if (Math.random() < CLICK_PROBABILITY) {
      if (!downEvent) {
        mSystemEventData.getSeries()
          .add(new EventAction<SimpleEventComponent.Action, EventVisualTest.ActionType>(deltaTime, 0, SimpleEventComponent.Action.DOWN,
                                                                                        EventVisualTest.ActionType.HOLD));
      } else {
        mSystemEventData.getSeries()
          .add(
            new EventAction<SimpleEventComponent.Action, EventVisualTest.ActionType>(deltaTime, deltaTime, SimpleEventComponent.Action.UP,
                                                                                     EventVisualTest.ActionType.TOUCH));
      }
      downState = !downEvent;
    }
    return downState;
  }
}