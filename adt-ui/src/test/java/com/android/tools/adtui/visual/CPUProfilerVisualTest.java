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
import com.android.tools.adtui.config.LineConfig;
import com.android.tools.adtui.model.RangedContinuousSeries;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CPUProfilerVisualTest extends VisualTest {

  private static final String CPU_PROFILER_NAME = "CPU Profiler";

  private static final int AXIS_SIZE = 100;

  private long mStartTimeMs;

  private LineChart mLineChart;

  private List<RangedContinuousSeries> mData;

  private AxisComponent mCPUUsageAxis;

  private AxisComponent mTimeAxis;

  private GridComponent mGrid;

  private SelectionComponent mSelection;

  private RangeScrollbar mScrollbar;

  /**
   * Max y value of each series.
   */
  private Map<RangedContinuousSeries, Integer> mSeriesMaxValues = new HashMap<>();

  @Override
  protected List<Animatable> createComponentsList() {
    mData = new ArrayList<>();
    mLineChart = new LineChart();

    mStartTimeMs = System.currentTimeMillis();
    final Range xRange = new Range(0, 0);
    final Range xGlobalRange = new Range(0, 0);
    final AnimatedTimeRange animatedTimeRange = new AnimatedTimeRange(xGlobalRange, mStartTimeMs);
    mScrollbar = new RangeScrollbar(xGlobalRange, xRange);

    // add horizontal time axis
    mTimeAxis = new AxisComponent(xRange, xGlobalRange, "TIME", AxisComponent.AxisOrientation.BOTTOM, AXIS_SIZE, AXIS_SIZE, false,
                                  new TimeAxisDomain(5, 5, 5));

    Range myProcessYRange = new Range(0, 100);
    RangedContinuousSeries myProcessSeries = new RangedContinuousSeries(xRange, myProcessYRange);
    mSeriesMaxValues.put(myProcessSeries, 60);
    LineConfig myProcessLineConfig = new LineConfig(new Color(0x85c490));
    myProcessLineConfig.setStacked(true);
    myProcessLineConfig.setFilled(true);
    mData.add(myProcessSeries);
    mLineChart.addLine(myProcessSeries, myProcessLineConfig);

    Range otherProcessesYRange = new Range(0, 100);
    RangedContinuousSeries otherProcessesSeries = new RangedContinuousSeries(xRange, otherProcessesYRange);
    mSeriesMaxValues.put(otherProcessesSeries, 20);
    LineConfig otherProcessesLineConfig = new LineConfig(new Color(0xc9d8e1));
    otherProcessesLineConfig.setStacked(true);
    otherProcessesLineConfig.setFilled(true);
    mData.add(otherProcessesSeries);
    mLineChart.addLine(otherProcessesSeries, otherProcessesLineConfig);

    mCPUUsageAxis = new AxisComponent(myProcessYRange, myProcessYRange, "CPU", AxisComponent.AxisOrientation.LEFT, AXIS_SIZE, AXIS_SIZE,
                                      true, PercentageAxisDomain.getDefault());

    mGrid = new GridComponent();
    mGrid.addAxis(mTimeAxis);
    mGrid.addAxis(mCPUUsageAxis);

    final Range xSelectionRange = new Range(0, 0);
    mSelection = new SelectionComponent(mTimeAxis, xSelectionRange, xGlobalRange, xRange);

    // Note: the order below is important as some components depend on
    // others to be updated first. e.g. the ranges need to be updated before the axes.
    // The comment on each line highlights why the component needs to be in that position.
    return Arrays.asList(animatedTimeRange, // Update global time range immediate.
                         mSelection, // Update selection range immediate.
                         mScrollbar, // Update current range immediate.
                         mLineChart, // Set y's interpolation values.
                         myProcessYRange, // Interpolate y1.
                         otherProcessesYRange, // Interpolate y2.
                         mTimeAxis, // Read ranges.
                         mCPUUsageAxis, // Read ranges.
                         mGrid, // No-op.
                         xRange, // Reset flags.
                         xGlobalRange, // Reset flags.
                         xSelectionRange); // Reset flags.
  }

  @Override
  protected void registerComponents(List<AnimatedComponent> components) {
    components.add(mLineChart);
    components.add(mSelection);
    components.add(mTimeAxis);
    components.add(mCPUUsageAxis);
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
    LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
    controls.setLayout(manager);
    panel.add(controls, BorderLayout.WEST);

    final AtomicInteger variance = new AtomicInteger(10);
    final AtomicInteger delay = new AtomicInteger(10);
    mUpdateDataThread = new Thread() {
      @Override
      public void run() {
        super.run();
        try {
          while (true) {
            //  Insert new data point at now.
            long now = System.currentTimeMillis() - mStartTimeMs;
            int v = variance.get();
            for (RangedContinuousSeries rangedSeries : mData) {
              int size = rangedSeries.getSeries().size();
              long last = size > 0 ? rangedSeries.getSeries().getY(size - 1) : 0;
              // Difference between current and new values is going to be variance times a number in the interval [-0.5, 0.5)
              float delta = v * ((float) Math.random() - 0.5f);
              long current = last + (long) delta;
              int seriesMax = mSeriesMaxValues.containsKey(rangedSeries) ? mSeriesMaxValues.get(rangedSeries) : 50;

              current = Math.min(seriesMax, Math.max(current, 0));
              rangedSeries.getSeries().add(now, current);
            }

            Thread.sleep(delay.get());
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
}