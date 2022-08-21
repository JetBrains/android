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

import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.RangeSelectionComponent;
import com.android.tools.adtui.RangeTimeScrollBar;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.adtui.model.updater.Updatable;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class AxisLineChartVisualTest extends VisualTest {

  private static final int AXIS_SIZE = 100;

  private static final String SERIES1_LABEL = "Memory1";
  private static final String SERIES2_LABEL = "Memory2";

  private long mStartTimeUs;

  private Range mTimeGlobalRangeUs;

  private Range mTimeViewRangeUs;

  private LineChart mLineChart;

  private AnimatedTimeRange mAnimatedTimeRange;

  private List<RangedContinuousSeries> mRangedData;

  private List<DefaultDataSeries<Long>> mData;

  private AxisComponent mMemoryAxis1;

  private AxisComponent mMemoryAxis2;

  private AxisComponent mTimeAxis;

  private RangeSelectionComponent mSelection;

  private RangeTimeScrollBar mScrollbar;

  private LegendComponent mLegendComponent;
  private LineChartModel mLineChartModel;
  private ResizingAxisComponentModel mTimeAxisModel;
  private ResizingAxisComponentModel mMemoryAxisModel1;
  private ClampedAxisComponentModel mMemoryAxisModel2;
  private LegendComponentModel mLegendComponentModel;

  private AxisComponentModel mTimeAxisGuideModel;
  private AxisComponent mTimeAxisGuide;

  @Override
  protected List<Updatable> createModelList() {
    mRangedData = new ArrayList<>();
    mData = new ArrayList<>();
    mLineChartModel = new LineChartModel(newDirectExecutorService());
    mLineChart = new LineChart(mLineChartModel);

    mStartTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    mTimeViewRangeUs = new Range(0, TimeUnit.SECONDS.toMicros(15));
    mTimeGlobalRangeUs = new Range(0, 0);
    mAnimatedTimeRange = new AnimatedTimeRange(mTimeGlobalRangeUs, mStartTimeUs);
    mScrollbar = new RangeTimeScrollBar(mTimeGlobalRangeUs, mTimeViewRangeUs, TimeUnit.MICROSECONDS);

    // add horizontal time axis
    mTimeAxisModel =
      new ResizingAxisComponentModel.Builder(mTimeViewRangeUs, TimeAxisFormatter.DEFAULT).setGlobalRange(mTimeGlobalRangeUs).build();

    mTimeAxis = new AxisComponent(mTimeAxisModel, AxisComponent.AxisOrientation.BOTTOM);
    mTimeAxis.setMargins(AXIS_SIZE, AXIS_SIZE);

    // add axis guide to time axis
    mTimeAxisGuideModel = new ResizingAxisComponentModel.Builder(mTimeViewRangeUs, TimeAxisFormatter.DEFAULT_WITHOUT_MINOR_TICKS)
      .setGlobalRange(mTimeGlobalRangeUs).build();

    mTimeAxisGuide = new AxisComponent(mTimeAxisGuideModel, AxisComponent.AxisOrientation.BOTTOM);
    mTimeAxisGuide.setMargins(AXIS_SIZE, AXIS_SIZE);
    mTimeAxisGuide.setMarkerColor(Gray._100);
    mTimeAxisGuide.setShowAxisLine(false);
    mTimeAxisGuide.setShowLabels(false);
    mLineChart.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        mTimeAxisGuide.setMarkerLengths(mLineChart.getHeight(), 0);
      }
    });

    // left memory data + axis
    Range yRange1Animatable = new Range(0, 100);
    mMemoryAxisModel1 =
      new ResizingAxisComponentModel.Builder(yRange1Animatable, MemoryAxisFormatter.DEFAULT).setLabel(SERIES1_LABEL).build();
    mMemoryAxis1 = new AxisComponent(mMemoryAxisModel1, AxisComponent.AxisOrientation.LEFT);
    mMemoryAxis1.setShowMax(true);
    mMemoryAxis1.setOnlyShowUnitAtMax(true);
    mMemoryAxis1.setMargins(AXIS_SIZE, AXIS_SIZE);

    DefaultDataSeries<Long> series1 = new DefaultDataSeries<>();
    RangedContinuousSeries ranged1 = new RangedContinuousSeries(SERIES1_LABEL, mTimeViewRangeUs, yRange1Animatable, series1);
    mRangedData.add(ranged1);
    mData.add(series1);

    // right memory data + axis
    Range yRange2Animatable = new Range(0, 100);
    mMemoryAxisModel2 =
      new ClampedAxisComponentModel.Builder(yRange2Animatable, MemoryAxisFormatter.DEFAULT).setLabel(SERIES2_LABEL).build();
    mMemoryAxis2 = new AxisComponent(mMemoryAxisModel2, AxisComponent.AxisOrientation.RIGHT);
    mMemoryAxis2.setShowMax(true);
    mMemoryAxis2.setOnlyShowUnitAtMax(true);
    mMemoryAxis2.setMargins(AXIS_SIZE, AXIS_SIZE);

    DefaultDataSeries<Long> series2 = new DefaultDataSeries<>();
    RangedContinuousSeries ranged2 = new RangedContinuousSeries(SERIES2_LABEL, mTimeViewRangeUs, yRange2Animatable, series2);
    mRangedData.add(ranged2);
    mData.add(series2);

    mLineChartModel.addAll(mRangedData);

    mLegendComponentModel = new LegendComponentModel(mTimeViewRangeUs);
    SeriesLegend legend = new SeriesLegend(mRangedData.get(0), MemoryAxisFormatter.DEFAULT, mTimeGlobalRangeUs);
    mLegendComponentModel.add(legend);
    mLegendComponent = new LegendComponent(mLegendComponentModel);
    mLegendComponent.configure(legend, new LegendConfig(LegendConfig.IconType.LINE, LineConfig.getColor(1)));

    final Range timeSelectionRangeUs = new Range();
    RangeSelectionModel selection = new RangeSelectionModel(timeSelectionRangeUs, mTimeViewRangeUs);
    mSelection = new RangeSelectionComponent(selection);

    // Note: the order below is important as some components depend on
    // others to be updated first. e.g. the ranges need to be updated before the axes.
    // The comment on each line highlights why the component needs to be in that position.
    return Arrays.asList(mAnimatedTimeRange, // Update global time range immediate.
                         mLineChartModel, // Set y's interpolation values.
                         mMemoryAxisModel2); // Sync with mMemoryAxis1 if enabled.
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(mSelection, mLineChart, mMemoryAxis1, mMemoryAxis2, mTimeAxis, mLegendComponent);
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

    Thread mUpdateDataThread = new Thread(() -> {
      try {
        while (true) {
          long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime()) - mStartTimeUs;
          for (DefaultDataSeries<Long> series : mData) {
            List<SeriesData<Long>> data = series.getAllData();
            long last = data.isEmpty() ? 0 : data.get(data.size() - 1).value;
            float delta = 10 * ((float)Math.random() - 0.45f);
            series.add(nowUs, last + (long)delta);
          }
          Thread.sleep(delay.get());
        }
      }
      catch (InterruptedException ignored) {
      }
    }, "AxisLineChartVisualTest.populateUi");
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
    controls.add(VisualTest.createVariableSlider("View length in seconds", 0, 50, new VisualTests.Value() {
      @Override
      public void set(int v) {
        mTimeViewRangeUs.setMax(mTimeViewRangeUs.getMin() + TimeUnit.SECONDS.toMicros(v));
      }

      @Override
      public int get() {
        return (int)TimeUnit.MICROSECONDS.toSeconds((long)mTimeViewRangeUs.getLength());
      }
    }));

    controls.add(VisualTest.createCheckbox("Show Axis Guide",
                                           itemEvent -> mTimeAxisGuide.setVisible(itemEvent.getStateChange() == ItemEvent.SELECTED), true));

    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  private JLayeredPane createMockTimeline() {
    JBLayeredPane timelinePane = new JBLayeredPane();
    timelinePane.add(mMemoryAxis1);
    timelinePane.add(mMemoryAxis2);
    timelinePane.add(mTimeAxis);
    timelinePane.add(mTimeAxisGuide);
    timelinePane.add(mLineChart);
    timelinePane.add(mSelection);
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
            if (c == mTimeAxisGuide) {
              // Axis Guide should located on top of the linechart
              c.setBounds(0, AXIS_SIZE, dim.width,
                          dim.height);
            }
            else if (c instanceof AxisComponent) {
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
            else if (c instanceof RangeTimeScrollBar) {
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