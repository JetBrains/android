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

package com.android.tools.adtui.visualtests;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.LongDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AxisLineChartVisualTest extends VisualTest {

  private static final int AXIS_SIZE = 100;

  private static final int LABEL_UPDATE_MILLIS = 100;

  private static final String SERIES1_LABEL = "Memory1";
  private static final String SERIES2_LABEL = "Memory2";

  private long mStartTimeUs;

  private Range mTimeGlobalRangeUs;

  private LineChart mLineChart;

  private AnimatedTimeRange mAnimatedTimeRange;

  private List<RangedContinuousSeries> mRangedData;

  private List<LongDataSeries> mData;

  private AxisComponent mMemoryAxis1;

  private AxisComponent mMemoryAxis2;

  private AxisComponent mTimeAxis;

  private GridComponent mGrid;

  private SelectionComponent mSelection;

  private RangeScrollbar mScrollbar;

  private LegendComponent mLegendComponent;

  @Override
  protected List<Animatable> createComponentsList() {
    mRangedData = new ArrayList<>();
    mData = new ArrayList<>();
    mLineChart = new LineChart();

    mStartTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    final Range timeCurrentRangeUs = new Range();
    mTimeGlobalRangeUs = new Range();
    mAnimatedTimeRange = new AnimatedTimeRange(mTimeGlobalRangeUs, mStartTimeUs);
    mScrollbar = new RangeScrollbar(mTimeGlobalRangeUs, timeCurrentRangeUs);

    // add horizontal time axis
    AxisComponent.Builder builder = new AxisComponent.Builder(timeCurrentRangeUs, TimeAxisFormatter.DEFAULT, AxisComponent.AxisOrientation.BOTTOM)
      .setGlobalRange(mTimeGlobalRangeUs).setMargins(AXIS_SIZE, AXIS_SIZE);
    mTimeAxis = builder.build();

    // left memory data + axis
    Range yRange1Animatable = new Range(0, 100);
    builder = new AxisComponent.Builder(yRange1Animatable, MemoryAxisFormatter.DEFAULT, AxisComponent.AxisOrientation.LEFT)
      .setLabel(SERIES1_LABEL)
      .showMax(true)
      .showUnitAtMax(true)
      .setMargins(AXIS_SIZE, AXIS_SIZE);
    mMemoryAxis1 = builder.build();

    LongDataSeries series1 = new LongDataSeries();
    RangedContinuousSeries ranged1 = new RangedContinuousSeries(SERIES1_LABEL, timeCurrentRangeUs, yRange1Animatable, series1);
    mRangedData.add(ranged1);
    mData.add(series1);

    // right memory data + axis
    Range yRange2Animatable = new Range(0, 100);
    builder = new AxisComponent.Builder(yRange2Animatable, MemoryAxisFormatter.DEFAULT, AxisComponent.AxisOrientation.RIGHT)
      .setLabel(SERIES2_LABEL)
      .showMax(true)
      .showUnitAtMax(true)
      .setMargins(AXIS_SIZE, AXIS_SIZE);
    mMemoryAxis2 = builder.build();

    LongDataSeries series2 = new LongDataSeries();
    RangedContinuousSeries ranged2 = new RangedContinuousSeries(SERIES2_LABEL, timeCurrentRangeUs, yRange2Animatable, series2);
    mRangedData.add(ranged2);
    mData.add(series2);

    mLineChart.addLines(mRangedData);
    List<LegendRenderData> legendRenderInfo = new ArrayList<>();

    //Test the populated series case
    legendRenderInfo.add(mLineChart.createLegendRenderData(mRangedData.get(0), MemoryAxisFormatter.DEFAULT, mTimeGlobalRangeUs));
    //Test the null series case
    legendRenderInfo.add(new LegendRenderData(LegendRenderData.IconType.LINE, LineConfig.getColor(1), SERIES2_LABEL));

    mLegendComponent = new LegendComponent(LegendComponent.Orientation.VERTICAL, LABEL_UPDATE_MILLIS);
    mLegendComponent.setLegendData(legendRenderInfo);

    mGrid = new GridComponent();
    mGrid.addAxis(mTimeAxis);
    mGrid.addAxis(mMemoryAxis1);

    final AnimatedRange timeSelectionRangeUs = new AnimatedRange();
    mSelection = new SelectionComponent(timeSelectionRangeUs, timeCurrentRangeUs);

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
                         mGrid, // No-op.
                         timeSelectionRangeUs,
                         mLegendComponent); // Reset flags.
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(mSelection, mLineChart, mMemoryAxis1, mMemoryAxis2, mTimeAxis, mGrid, mLegendComponent);
  }

  @Override
  public String getName() {
    return "Axis+Scroll+Zoom";
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
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
            long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime()) - mStartTimeUs;
            for (LongDataSeries series : mData) {
              ImmutableList<SeriesData<Long>> data = series.getAllData();
              long last = data.isEmpty() ? 0 : data.get(data.size() - 1).value;
              float delta = 10 * ((float)Math.random() - 0.45f);
              series.add(nowUs, last + (long)delta);
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