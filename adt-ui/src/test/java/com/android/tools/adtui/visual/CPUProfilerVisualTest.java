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
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.RangedContinuousSeries;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;

public class CPUProfilerVisualTest extends VisualTest {

  private static final String CPU_PROFILER_NAME = "CPU Profiler";

  private static final String CPU_USAGE_AXIS_LABEL = "CPU";

  private static final String TIME_AXIS_LABEL = "TIME";

  private static final int MY_PROCESS_MAX_VALUE = 60;

  private static final Color MY_PROCESS_LINE_COLOR = new Color(0x85c490);

  private static final String MY_PROCESS_SERIES_LABEL = "My Process";

  private static final int OTHER_PROCESSES_MAX_VALUE = 20;

  private static final Color OTHER_PROCESSES_LINE_COLOR = new Color(0xc9d8e1);

  private static final String OTHER_PROCESSES_SERIES_LABEL = "Other Processes";

  private static final String THREADS_SERIES_LABEL = "Threads";

  private static final Color THREADS_LINE_COLOR = new Color(0x5a9240);

  private static final int AXIS_SIZE = 100;

  private static final int THREADS_SLEEP_TIME_MS = 5000;

  private static final int UPDATE_THREAD_SLEEP_DELAY_MS = 10;

  private static final int PROCESSES_LINE_CHART_VARIANCE = 10;

  /**
   * The active threads should be copied into this array when getThreadGroup().enumerate() is called.
   * It is initialized with a safe size.
   */
  private static final Thread[] ACTIVE_THREADS = new Thread[1000];

  private long mStartTimeMs;

  private LineChart mLineChart;

  /**
   * Series that represent processes share some properties, so they can grouped in a list.
   */
  private List<RangedContinuousSeries> mProcessesSeries;

  private RangedContinuousSeries mNumberOfThreadsSeries;

  private AxisComponent mCPUUsageAxis;

  private AxisComponent mThreadsAxis;

  private AxisComponent mTimeAxis;

  private GridComponent mGrid;

  private SelectionComponent mSelection;

  private RangeScrollbar mScrollbar;

  private Range mXRange;

  private Range mCPUUsageRange;

  /**
   * Max y value of each series.
   */
  private Map<RangedContinuousSeries, Integer> mSeriesMaxValues = new HashMap<>();

  @Override
  protected List<Animatable> createComponentsList() {
    mProcessesSeries = new ArrayList<>();
    mLineChart = new LineChart();

    mStartTimeMs = System.currentTimeMillis();
    mXRange = new Range();
    final Range xGlobalRange = new Range();
    final AnimatedTimeRange animatedTimeRange = new AnimatedTimeRange(xGlobalRange, mStartTimeMs);
    mScrollbar = new RangeScrollbar(xGlobalRange, mXRange);

    // add horizontal time axis
    mTimeAxis = new AxisComponent(mXRange, xGlobalRange, TIME_AXIS_LABEL, AxisComponent.AxisOrientation.BOTTOM, AXIS_SIZE, AXIS_SIZE, false,
                                  new TimeAxisFormatter(5, 5, 5));

    mCPUUsageRange = new Range(0, 100);
    createProcessLine(MY_PROCESS_MAX_VALUE, MY_PROCESS_LINE_COLOR, MY_PROCESS_SERIES_LABEL);

    //Range otherProcessesYRange = new Range(0, 100);
    createProcessLine(OTHER_PROCESSES_MAX_VALUE, OTHER_PROCESSES_LINE_COLOR, OTHER_PROCESSES_SERIES_LABEL);

    int maxNumberOfThreads = Runtime.getRuntime().availableProcessors();
    Range numThreadsYRange = new Range(0, maxNumberOfThreads + 1 /* Using +1 to get some extra space. */);
    mNumberOfThreadsSeries = new RangedContinuousSeries(THREADS_SERIES_LABEL, mXRange, numThreadsYRange);
    mSeriesMaxValues.put(mNumberOfThreadsSeries, maxNumberOfThreads);
    LineConfig numberOfThreadsLineConfig = new LineConfig(THREADS_LINE_COLOR);
    numberOfThreadsLineConfig.setStepped(true);
    mLineChart.addLine(mNumberOfThreadsSeries, numberOfThreadsLineConfig);

    mCPUUsageAxis = new AxisComponent(mCPUUsageRange, mCPUUsageRange, CPU_USAGE_AXIS_LABEL, AxisComponent.AxisOrientation.LEFT, AXIS_SIZE,
                                      AXIS_SIZE, true, new SingleUnitAxisFormatter(10, 10, 10, "%"));
    mThreadsAxis = new AxisComponent(numThreadsYRange, numThreadsYRange, THREADS_SERIES_LABEL, AxisComponent.AxisOrientation.RIGHT,
                                     AXIS_SIZE, AXIS_SIZE, true, new SingleUnitAxisFormatter(1, maxNumberOfThreads, 1, ""));

    mGrid = new GridComponent();
    mGrid.addAxis(mTimeAxis);
    mGrid.addAxis(mCPUUsageAxis);
    mGrid.addAxis(mThreadsAxis);

    final Range xSelectionRange = new Range();
    mSelection = new SelectionComponent(mLineChart, mTimeAxis, xSelectionRange, xGlobalRange, mXRange);

    // Note: the order below is important as some components depend on
    // others to be updated first. e.g. the ranges need to be updated before the axes.
    // The comment on each line highlights why the component needs to be in that position.
    return Arrays.asList(animatedTimeRange, // Update global time range immediate.
                         mSelection, // Update selection range immediate.
                         mScrollbar, // Update current range immediate.
                         mLineChart, // Set y's interpolation values.
                         mCPUUsageRange, // Interpolate CPUUsage y.
                         numThreadsYRange, // Interpolate numThreads y.
                         mTimeAxis, // Read ranges.
                         mCPUUsageAxis, // Read ranges.
                         mThreadsAxis, // Read ranges.
                         mGrid, // No-op.
                         mXRange, // Reset flags.
                         xGlobalRange, // Reset flags.
                         xSelectionRange); // Reset flags.
  }

  @Override
  protected void registerComponents(List<AnimatedComponent> components) {
    components.add(mLineChart);
    components.add(mSelection);
    components.add(mTimeAxis);
    components.add(mCPUUsageAxis);
    components.add(mThreadsAxis);
    components.add(mGrid);
  }

  @Override
  public String getName() {
    return CPU_PROFILER_NAME;
  }

  @Override
  protected void populateUi(@NonNull JPanel panel) {
    panel.setLayout(new BorderLayout());

    JLayeredPane mockTimelinePane = createMockTimeline();
    panel.add(mockTimelinePane, BorderLayout.CENTER);

    final JPanel controls = new JPanel();
    final JButton addThreadButton = VisualTests.createButton("Add Thread");
    LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
    controls.setLayout(manager);
    controls.add(addThreadButton);
    panel.add(controls, BorderLayout.WEST);

    mUpdateDataThread = new Thread() {
      @Override
      public void run() {
        try {
          // Add a thread and let and terminate it after a few seconds.
          addThreadButton.addActionListener(e -> {
            Thread t = new Thread(mUpdateDataThread.getThreadGroup(), () -> {
              try {
                sleep(THREADS_SLEEP_TIME_MS);
              }
              catch (InterruptedException e1) {
                // Nothing to to here.
              }
            });

            t.start();
          });
          while (true) {
            //  Insert new data point at now.
            long now = System.currentTimeMillis() - mStartTimeMs;
            for (RangedContinuousSeries rangedSeries : mProcessesSeries) {
              int size = rangedSeries.getSeries().size();
              long last = size > 0 ? rangedSeries.getSeries().getY(size - 1) : 0;
              // Difference between current and new values is going to be variance times a number in the interval [-0.5, 0.5)
              float delta = PROCESSES_LINE_CHART_VARIANCE * ((float) Math.random() - 0.5f);
              long current = last + (long) delta;
              assert mSeriesMaxValues.containsKey(rangedSeries);
              current = Math.min(mSeriesMaxValues.get(rangedSeries), Math.max(current, 0));
              rangedSeries.getSeries().add(now, current);
            }

            // Copy active threads into ACTIVE_THREADS array
            int numActiveThreads = getThreadGroup().enumerate(ACTIVE_THREADS);
            int targetThreads = 0;
            for (int i = 0; i < numActiveThreads; i++) {
              // We're only interested in threads that are alive
              if (ACTIVE_THREADS[i].isAlive()) {
                targetThreads++;
              }
            }
            mNumberOfThreadsSeries.getSeries().add(now, targetThreads);

            Thread.sleep(UPDATE_THREAD_SLEEP_DELAY_MS);
          }
        } catch (InterruptedException e) {
        }
      }
    };
    mUpdateDataThread.start();
  }

  private JLayeredPane createMockTimeline() {
    JLayeredPane timelinePane = new JLayeredPane();

    timelinePane.add(mCPUUsageAxis);
    timelinePane.add(mThreadsAxis);
    timelinePane.add(mTimeAxis);
    timelinePane.add(mLineChart);
    timelinePane.add(mSelection);
    timelinePane.add(mGrid);
    timelinePane.add(mScrollbar);
    timelinePane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane) e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          for (Component c : host.getComponents()) {
            if (c instanceof AxisComponent) {
              AxisComponent axis = (AxisComponent) c;
              switch (axis.getOrientation()) {
                case LEFT:
                  axis.setBounds(0, 0, AXIS_SIZE, dim.height);
                  break;
                case BOTTOM:
                  axis.setBounds(0, dim.height - AXIS_SIZE, dim.width, AXIS_SIZE);
                  break;
                case RIGHT:
                  axis.setBounds(dim.width - AXIS_SIZE, 0, AXIS_SIZE, dim.height);
                  break;
                case TOP:
                  axis.setBounds(0, 0, dim.width, AXIS_SIZE);
                  break;
              }
            } else if (c instanceof RangeScrollbar) {
              int sbHeight = c.getPreferredSize().height;
              c.setBounds(0, dim.height - sbHeight, dim.width, sbHeight);
            } else {
              c.setBounds(AXIS_SIZE, AXIS_SIZE, dim.width - AXIS_SIZE * 2,
                          dim.height - AXIS_SIZE * 2);
            }
          }
        }
      }
    });

    return timelinePane;
  }

  /**
   * Given a series, add a filled, stacked line to a line chart to represent the data.
   */
  private void createProcessLine(int maxSeriesValue, Color lineColor, String seriesLabel) {
    RangedContinuousSeries series = new RangedContinuousSeries(seriesLabel, mXRange, mCPUUsageRange);
    mSeriesMaxValues.put(series, maxSeriesValue);
    LineConfig lineConfig = new LineConfig(lineColor);
    lineConfig.setStacked(true);
    lineConfig.setFilled(true);
    mProcessesSeries.add(series);
    mLineChart.addLine(series, lineConfig);
  }
}