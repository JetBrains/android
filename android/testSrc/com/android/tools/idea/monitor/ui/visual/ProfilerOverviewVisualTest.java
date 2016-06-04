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

package com.android.tools.idea.monitor.ui.visual;

import com.android.tools.adtui.*;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.RangedSimpleSeries;
import com.android.tools.adtui.visual.EventVisualTest;
import com.android.tools.adtui.visual.VisualTest;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.TimeAxisSegment;
import com.android.tools.idea.monitor.ui.cpu.view.CpuUsageSegment;
import com.android.tools.idea.monitor.ui.events.view.EventSegment;
import com.android.tools.idea.monitor.ui.memory.view.MemorySegment;
import com.android.tools.idea.monitor.ui.network.view.NetworkSegment;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ProfilerOverviewVisualTest extends VisualTest {

  // Segment dimensions.
  private static final int EVENT_MIN_HEIGHT = 100;
  private static final int MONITOR_MAX_HEIGHT = Short.MAX_VALUE;
  private static final int MONITOR_PREFERRED_HEIGHT = 200;
  private static final int TIME_AXIS_HEIGHT = 20;

  // Event data generation constants.
  private static final int IMAGE_WIDTH = 16;
  private static final int IMAGE_HEIGHT = 16;
  private static final float CLICK_PROBABILITY = 1 / 5.0f; // 1 in 5 chance to click and release
  private static final double CREATE_DESTROY_PROBABILITY = .5; // 50% chance to create a new fragment/activity
  private static final int EVENT_LIMIT = 5; // create a maximum of X fragment/activities;

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

  private SeriesDataStore mDataStore;

  @NotNull
  private Range mXRange;

  @NotNull
  private Range mXGlobalRange;

  @NotNull
  private Range mXSelectionRange;

  @NotNull
  private AxisComponent mTimeAxis;

  @NotNull
  private SelectionComponent mSelection;

  @NotNull
  private RangeScrollbar mScrollbar;

  private JPanel mSegmentsContainer;

  private AccordionLayout mLayout;

  private RangedSimpleSeries<EventAction<SimpleEventComponent.Action, EventVisualTest.ActionType>> mSystemEventData;

  private RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> mFragmentEventData;

  private RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> mActivityEventData;

  private EventDispatcher<ProfilerEventListener> mEventDispatcher;

  private Component mResetProfilersButton;

  @Override
  protected void initialize() {
    mEventDispatcher = EventDispatcher.create(ProfilerEventListener.class);
    mDataStore = new VisualTestSeriesDataStore();
    super.initialize();
  }

  @Override
  protected void reset() {
    if (mDataStore != null) {
      mDataStore.reset();
    }

    super.reset();
  }

  @Override
  protected List<Animatable> createComponentsList() {
    mXRange = new Range();
    mXGlobalRange = new Range();
    mXSelectionRange = new Range();

    mScrollbar = new RangeScrollbar(mXGlobalRange, mXRange);

    mSystemEventData = new RangedSimpleSeries<>(mXGlobalRange);
    mFragmentEventData = new RangedSimpleSeries<>(mXGlobalRange);
    mActivityEventData = new RangedSimpleSeries<>(mXGlobalRange);

    // add horizontal time axis
    mTimeAxis = new AxisComponent(mXRange,
                                  mXGlobalRange,
                                  "TIME",
                                  AxisComponent.AxisOrientation.BOTTOM,
                                  0,
                                  0,
                                  false,
                                  TimeAxisFormatter.DEFAULT);

    mSegmentsContainer = new JBPanel();
    mLayout = new AccordionLayout(mSegmentsContainer, AccordionLayout.Orientation.VERTICAL);
    // TODO animation is disabled at the moment as it causes visual artifacts on the SelectionComponent due to overlapping painting regions.
    mLayout.setLerpFraction(1f);
    mSegmentsContainer.setLayout(mLayout);

    mSelection = new SelectionComponent(mSegmentsContainer,
                                        mTimeAxis,
                                        mXSelectionRange,
                                        mXGlobalRange,
                                        mXRange);

    List<Animatable> componentsList = new ArrayList<>();
    componentsList.add(mLayout);
    // Get latest data time from the data store.
    componentsList.add(frameLength -> mXGlobalRange.setMaxTarget(mDataStore.getLatestTime()));
    componentsList.add(mSelection);            // Update selection range immediate.
    componentsList.add(mScrollbar);            // Update current range immediate.
    componentsList.add(mTimeAxis);             // Read ranges.
    componentsList.add(mXRange);               // Reset flags.
    componentsList.add(mXGlobalRange);         // Reset flags.
    componentsList.add(mXSelectionRange);      // Reset flags.
    return componentsList;
  }

  @Override
  public String getName() {
    return "Overview";
  }

  @Override
  public void populateUi(JPanel panel) {
    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JBPanel gridBagPanel = new JBPanel();
    gridBagPanel.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR, 1));
    gridBagPanel.setLayout(gbl);

    // TODO create some controls.
    final JPanel controls = VisualTest.createControlledPane(panel, gridBagPanel);

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
    gbc.gridwidth = 4;
    gbc.gridy = 1;
    gbc.weighty = 0;
    gbc.weightx = 0;
    gridBagPanel.add(mSegmentsContainer, gbc);

    // Add Scrollbar
    gbc.gridy = 2;
    gridBagPanel.add(mScrollbar, gbc);

    // Mock event segment
    BaseSegment eventSegment = createSegment(BaseSegment.SegmentType.EVENT, EVENT_MIN_HEIGHT, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);
    // Mock monitor segments
    BaseSegment networkSegment = createSegment(BaseSegment.SegmentType.NETWORK, 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);
    BaseSegment memorySegment = createSegment(BaseSegment.SegmentType.MEMORY, 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);
    BaseSegment cpuSegment = createSegment(BaseSegment.SegmentType.CPU, 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);
    // Timeline segment
    BaseSegment timeSegment = createSegment(BaseSegment.SegmentType.TIME, TIME_AXIS_HEIGHT, TIME_AXIS_HEIGHT, TIME_AXIS_HEIGHT);

    mSegmentsContainer.add(eventSegment);
    mSegmentsContainer.add(networkSegment);
    mSegmentsContainer.add(memorySegment);
    mSegmentsContainer.add(cpuSegment);
    mSegmentsContainer.add(timeSegment);

    // Add left spacer
    Dimension leftSpacer = new Dimension(BaseSegment.getSpacerWidth() + eventSegment.getLabelColumnWidth(), 0);
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gridBagPanel.add(new Box.Filler(leftSpacer, leftSpacer, leftSpacer), gbc);

    // Add right spacer
    Dimension rightSpacerWidth = new Dimension(BaseSegment.getSpacerWidth(), 0);
    Box.Filler rightSpacer = new Box.Filler(rightSpacerWidth, rightSpacerWidth, rightSpacerWidth);
    gbc.gridy = 0;
    gbc.gridx = 3;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gridBagPanel.add(rightSpacer, gbc);
    rightSpacer.setVisible(false);  // hide right space in L1 by default.

    // Resize the SelectionComponent based on whether we are in L1 or L2 view.
    // TODO construct/destroyed Level3 segment/elements as we expand/collapse segments
    mEventDispatcher.addListener(new ProfilerEventListener() {
      @Override
      public void profilerExpanded(@NotNull BaseSegment.SegmentType segmentType) {
        switch (segmentType) {
          case NETWORK:
            mLayout.setState(networkSegment, AccordionLayout.AccordionState.MAXIMIZE);
            break;
          case CPU:
            mLayout.setState(cpuSegment, AccordionLayout.AccordionState.MAXIMIZE);
            break;
          case MEMORY:
            mLayout.setState(memorySegment, AccordionLayout.AccordionState.MAXIMIZE);
            break;
        }

        rightSpacer.setVisible(true);
        mResetProfilersButton.setEnabled(true);
      }

      @Override
      public void profilersReset() {
        // Sets all the components back to their preferred states.
        mLayout.resetComponents();
        rightSpacer.setVisible(false);
        mResetProfilersButton.setEnabled(false);
      }
    });
  }

  private JComponent createToolbarPanel() {
    JBPanel panel = new JBPanel();
    BoxLayout layout = new BoxLayout(panel, BoxLayout.X_AXIS);
    panel.setLayout(layout);
    panel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

    JComboBox<String> deviceCb = new JComboBox<>(new String[]{"Device1", "Device2"});
    deviceCb.addActionListener(e -> getChoreographer().reset());
    JComboBox<String> processCb = new JComboBox<>(new String[]{"Process1", "Process2"});
    processCb.addActionListener(e -> getChoreographer().reset());

    mResetProfilersButton = VisualTest.createButton("Back to L1", e -> mEventDispatcher.getMulticaster().profilersReset());
    mResetProfilersButton.setEnabled(false);

    panel.add(mResetProfilersButton);
    panel.add(deviceCb);
    panel.add(processCb);
    return panel;
  }

  private BaseSegment createSegment(BaseSegment.SegmentType type, int minHeight, int preferredHeight, int maxHeight) {
    BaseSegment segment;
    switch (type) {
      case TIME:
        segment = new TimeAxisSegment(mXRange, mTimeAxis, mEventDispatcher);
        break;
      case EVENT:
        segment = new EventSegment<>(mXGlobalRange, mSystemEventData, mFragmentEventData, mActivityEventData, MOCK_ICONS, mEventDispatcher);
        break;
      case CPU:
        segment = new CpuUsageSegment(mXRange, mDataStore, mEventDispatcher);
        break;
      case NETWORK:
        segment = new NetworkSegment(mXRange, mDataStore, mEventDispatcher);
        break;
      case MEMORY:
      default:
        segment = new MemorySegment(mXRange, mDataStore, mEventDispatcher);
        break;
    }

    segment.setMinimumSize(new Dimension(0, minHeight));
    segment.setPreferredSize(new Dimension(0, preferredHeight));
    segment.setMaximumSize(new Dimension(0, maxHeight));
    segment.setBorder(BorderFactory.createLineBorder(Color.GRAY));

    List<Animatable> segmentAnimatables = new ArrayList<>();
    segment.createComponentsList(segmentAnimatables);
    addToChoreographer(segmentAnimatables);

    segment.initializeComponents();

    return segment;
  }

  // TODO refactor to VisualTestSeriesDataStore.
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

  // TODO refactor to VisualTestSeriesDataStore.
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