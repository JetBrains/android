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
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.model.*;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.containers.ImmutableList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AxisLineChartVisualTest extends VisualTest {

  private static final int AXIS_SIZE = 100;

  private static final int LABEL_UPDATE_MILLIS = 100;

  private static final String SERIES1_LABEL = "Memory1";
  private static final String SERIES2_LABEL = "Memory2";

  private long mStartTimeMs;

  @NonNull
  private Range mXGlobalRange;

  @NonNull
  private LineChart mLineChart;

  @NonNull
  private AnimatedTimeRange mAnimatedTimeRange;

  @NonNull
  private List<RangedContinuousSeries> mRangedData;

  @NonNull
  private List<DefaultContinuousSeries> mData;

  @NonNull
  private AxisComponent mMemoryAxis1;

  @NonNull
  private AxisComponent mMemoryAxis2;

  @NonNull
  private AxisComponent mTimeAxis;

  @NonNull
  private GridComponent mGrid;

  @NonNull
  private SelectionComponent mSelection;

  @NonNull
  private RangeScrollbar mScrollbar;

  @NonNull
  private LegendComponent mLegendComponent;


  @Override
  protected List<Animatable> createComponentsList() {
    mRangedData = new ArrayList<>();
    mData = new ArrayList<>();
    mLineChart = new LineChart();

    mStartTimeMs = System.currentTimeMillis();
    final Range xRange = new Range(0, 0);
    mXGlobalRange = new Range(0, 0);
    mAnimatedTimeRange = new AnimatedTimeRange(mXGlobalRange, mStartTimeMs);
    mScrollbar = new RangeScrollbar(mXGlobalRange, xRange);

    // add horizontal time axis
    mTimeAxis = new AxisComponent(xRange, mXGlobalRange, "TIME",
                                  AxisComponent.AxisOrientation.BOTTOM,
                                  AXIS_SIZE, AXIS_SIZE, false, TimeAxisFormatter.DEFAULT);
    mTimeAxis.setLabelVisible(false);

    // left memory data + axis
    Range yRange1Animatable = new Range(0, 100);
    mMemoryAxis1 = new AxisComponent(yRange1Animatable, yRange1Animatable, SERIES1_LABEL,
                                     AxisComponent.AxisOrientation.LEFT, AXIS_SIZE, AXIS_SIZE, true,
                                     MemoryAxisFormatter.DEFAULT);
    DefaultContinuousSeries series1 = new DefaultContinuousSeries();
    RangedContinuousSeries ranged1 = new RangedContinuousSeries(SERIES1_LABEL, xRange, yRange1Animatable, series1);
    mRangedData.add(ranged1);
    mData.add(series1);

    // right memory data + axis
    Range yRange2Animatable = new Range(0, 100);
    mMemoryAxis2 = new AxisComponent(yRange2Animatable, yRange2Animatable, SERIES2_LABEL,
                                     AxisComponent.AxisOrientation.RIGHT, AXIS_SIZE, AXIS_SIZE, true,
                                     MemoryAxisFormatter.DEFAULT);
    DefaultContinuousSeries series2 = new DefaultContinuousSeries();
    RangedContinuousSeries ranged2 = new RangedContinuousSeries(SERIES2_LABEL, xRange, yRange2Animatable, series2);
    mRangedData.add(ranged2);
    mData.add(series2);

    mLineChart.addLines(mRangedData);
    List<LegendRenderData> legendRenderInfo = new ArrayList<>();

    //Test the populated series case
    legendRenderInfo.add(new LegendRenderData(LegendRenderData.IconType.BOX, LineConfig.COLORS[0], mRangedData.get(0)));
    //Test the null series case
    legendRenderInfo.add(new LegendRenderData(LegendRenderData.IconType.LINE, LineConfig.COLORS[1], null));

    mLegendComponent = new LegendComponent(legendRenderInfo, LegendComponent.Orientation.VERTICAL, LABEL_UPDATE_MILLIS, new MemoryAxisFormatter(4, 10, 5));

    mGrid = new GridComponent();
    mGrid.addAxis(mTimeAxis);
    mGrid.addAxis(mMemoryAxis1);

    final Range xSelectionRange = new Range(0, 0);
    mSelection = new SelectionComponent(mLineChart, mTimeAxis, xSelectionRange, mXGlobalRange, xRange);

    // Note: the order below is important as some components depend on
    // others to be updated first. e.g. the ranges need to be updated before the axes.
    // The comment on each line highlights why the component needs to be in that position.
    return Arrays.asList(mAnimatedTimeRange, // Update global time range immediate.
                         mSelection, // Update selection range immediate.
                         mScrollbar, // Update current range immediate.
                         mLineChart, // Set y's interpolation values.
                         mMemoryAxis1, // Clamp/interpolate ranges to major ticks if enabled.
                         mMemoryAxis2, // Sync with mMemoryAxis1 if enabled.
                         mTimeAxis, // Read ranges.
                         yRange1Animatable, // Interpolate y1.
                         yRange2Animatable, // Interpolate y2.
                         mGrid, // No-op.
                         xRange, // Reset flags.
                         mXGlobalRange, // Reset flags.
                         xSelectionRange,
                         mLegendComponent); // Reset flags.
  }

  @Override
  public String getName() {
    return "Axis+Scroll+Zoom";
  }

  @Override
  protected void populateUi(@NonNull JPanel panel) {
    panel.setLayout(new BorderLayout());

    JLayeredPane mockTimelinePane = createMockTimeline();
    panel.add(mockTimelinePane, BorderLayout.CENTER);

    final JBPanel controls = new JBPanel();
    LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
    controls.setLayout(manager);
    panel.add(controls, BorderLayout.WEST);

    final AtomicInteger variance = new AtomicInteger(10);
    final AtomicInteger delay = new AtomicInteger(10);

    Thread mUpdateDataThread = new Thread() {
      @Override
      public void run() {
        try {
          while (true) {
            long now = System.currentTimeMillis() - mStartTimeMs;
            for (DefaultContinuousSeries series : mData) {
              ImmutableList<SeriesData<Long>> data = series.getAllData();
              long last = data.isEmpty() ? 0 : data.get(data.size() - 1).value;
              float delta = 10 * ((float)Math.random() - 0.45f);
              series.add(now, last + (long)delta);
            }
            Thread.sleep(delay.get());
          }
        }
        catch (InterruptedException e) {
        }
      }
    };
    mUpdateDataThread.start();
    controls.add(VisualTest.createVariableSlider("Delay", 10, 5000, new VisualTests.Value() {
      @Override
      public void set(int v) {
        delay.set(v);
      }

      @Override
      public int get() {
        return delay.get();
      }
    }));
    controls.add(VisualTest.createVariableSlider("Variance", 0, 50, new VisualTests.Value() {
      @Override
      public void set(int v) {
        variance.set(v);
      }

      @Override
      public int get() {
        return variance.get();
      }
    }));
    controls.add(VisualTest.createCheckbox("Stable Scroll",
                  itemEvent -> mScrollbar.setStableScrolling(itemEvent.getStateChange() == ItemEvent.SELECTED)));
    controls.add(VisualTest.createCheckbox("Clamp To Major Ticks",
                  itemEvent -> mMemoryAxis1.setClampToMajorTicks(itemEvent.getStateChange() == ItemEvent.SELECTED)));
    controls.add(VisualTest.createCheckbox("Sync Vertical Axes",
                  itemEvent -> mMemoryAxis2.setParentAxis(itemEvent.getStateChange() == ItemEvent.SELECTED ? mMemoryAxis1 : null)));
    controls.add(VisualTest.createButton("Zoom In", e -> mSelection.zoom(-SelectionComponent.ZOOM_FACTOR)));
    controls.add(VisualTest.createButton("Zoom Out", e -> mSelection.zoom(SelectionComponent.ZOOM_FACTOR)));
    controls.add(VisualTest.createButton("Reset Zoom", e -> mSelection.resetZoom()));
    controls.add(VisualTest.createButton("Clear Selection", e -> mSelection.clear()));

    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  private JLayeredPane createMockTimeline() {
    JBLayeredPane timelinePane = new JBLayeredPane();

    timelinePane.add(mMemoryAxis1);
    timelinePane.add(mMemoryAxis2);
    timelinePane.add(mTimeAxis);
    timelinePane.add(mLineChart);
    timelinePane.add(mSelection);
    timelinePane.add(mGrid);
    timelinePane.add(mScrollbar);
    JBPanel labelPanel = new JBPanel(); // TODO move to ProfilerOverviewVisualTest.
    labelPanel.setLayout(new FlowLayout());
    labelPanel.add(mLegendComponent);
    timelinePane.add(labelPanel);
    timelinePane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          for (Component c : host.getComponents()) {
            if (c instanceof AxisComponent) {
              AxisComponent axis = (AxisComponent)c;
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
            }
            else if (c instanceof RangeScrollbar) {
              int sbHeight = c.getPreferredSize().height;
              c.setBounds(0, dim.height - sbHeight, dim.width, sbHeight);
            }
            else {
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