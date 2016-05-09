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
import com.android.tools.adtui.model.RangedContinuousSeries;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ProfilerOverviewVisualTest extends VisualTest {

  private static final int EVENT_HEIGHT = 100;
  private static final int MONITOR_MAX_HEIGHT = Integer.MAX_VALUE;
  private static final int MONITOR_PREFERRED_HEIGHT = 200;

  private static final int TIME_AXIS_HEIGHT = 20;

  private static final int DATA_DELAY_MS = 100;

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

  @Override
  protected List<Animatable> createComponentsList() {
    mData = new ArrayList();

    mStartTimeMs = System.currentTimeMillis();
    mXRange = new Range();
    mXGlobalRange = new Range();
    mAnimatedTimeRange = new AnimatedTimeRange(mXGlobalRange, mStartTimeMs);
    mXSelectionRange = new Range();

    mScrollbar = new RangeScrollbar(mXGlobalRange, mXRange);

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

    return componentsList;
  }

  @Override
  protected void registerComponents(List<AnimatedComponent> components) {
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
          while (true) {
            long now = System.currentTimeMillis() - mStartTimeMs;
            for (RangedContinuousSeries rangedSeries : mData) {
              int size = rangedSeries.getSeries().size();
              long last = size > 0 ? rangedSeries.getSeries().getY(size - 1) : 0;
              float delta = 10 * ((float)Math.random() - 0.45f);
              rangedSeries.getSeries().add(now, last + (long)delta);
            }
            Thread.sleep(DATA_DELAY_MS);
          }
        }
        catch (InterruptedException e) {
        }
      }
    };
    mUpdateDataThread.start();

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


    // TODO replace with event timeline.
    JComponent eventPanel = createMonitorPanel("Events", EVENT_HEIGHT, EVENT_HEIGHT, EVENT_HEIGHT);
    mSegmentsContainer.add(eventPanel);

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

    JComboBox<String> box1 = new JComboBox<>(new String[] {"Test1", "Test2", "Test3"});
    JComboBox<String> box2 = new JComboBox<>(new String[] {"Test1", "Test2", "Test3"});

    panel.add(box1);
    panel.add(box2);
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
      RangedContinuousSeries ranged = new RangedContinuousSeries(mXRange, mYRange,
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
}