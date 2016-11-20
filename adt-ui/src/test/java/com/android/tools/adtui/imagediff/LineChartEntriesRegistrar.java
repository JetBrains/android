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

import com.android.tools.adtui.AnimatedRange;
import com.android.tools.adtui.chart.linechart.EventConfig;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.*;
import com.intellij.util.containers.ImmutableList;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class LineChartEntriesRegistrar extends ImageDiffEntriesRegistrar {

  public LineChartEntriesRegistrar() {
    registerStackedLineChart();
    registerSimpleLineChart();
    registerSteppedLineChart();
    registerSimpleEventLineChart();
    registerBlockingEventLineChart();
    registerFilledEventLineChart();
  }

  private void registerStackedLineChart() {
    register(new LineChartImageDiffEntry("stacked_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a filled, stacked line chart and register the components to the choreographer
        addLine(0.0, 100.0, "Left Series", new LineConfig(Color.BLUE).setFilled(true).setStacked(true));
        addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setFilled(true).setStacked(true));
      }
    });
  }

  private void registerSimpleLineChart() {
    register(new LineChartImageDiffEntry("simple_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
        addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
        addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)));
      }
    });
  }

  private void registerSteppedLineChart() {
    register(new LineChartImageDiffEntry("stepped_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a stepped line chart and register the components to the choreographer. Add thick lines to generate relevant images.
        addLine(0.0, 50.0, "First Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(10)).setStepped(true));
        addLine(0.0, 100.0, "Second Series", new LineConfig(Color.RED).setStroke(new BasicStroke(10)).setStepped(true));
        addLine(0.0, 200.0, "Third Series", new LineConfig(Color.GREEN).setStroke(new BasicStroke(10)).setStepped(true));
      }
    });
  }

  private void registerSimpleEventLineChart() {
    register(new LineChartImageDiffEntry("simple_event_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
        addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
        addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)));

        // Add a simple event to the line chart
        addEvent(Color.BLACK, false, false);
      }
    });
  }

  private void registerBlockingEventLineChart() {
    register(new LineChartImageDiffEntry("blocking_event_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
        addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
        addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)));

        // Add a blocking event to the line chart
        addEvent(Color.BLACK, false, true);
      }
    });
  }

  private void registerFilledEventLineChart() {
    register(new LineChartImageDiffEntry("filled_event_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
        addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
        addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)));

        // Add a filled event to the line chart
        addEvent(Color.GREEN, true, false);
      }
    });
  }

  private static abstract class LineChartImageDiffEntry extends AnimatedComponentImageDiffEntry {

    private static final int LINE_CHART_INITIAL_VALUE = 20;

    /**
     * The amount of time deltas (determined by {@link #TIME_DELTA_US}) an event will last.
     */
    private static final int EVENT_DURATION_MULTIPLIER = 8;

    /**
     * The amount of time deltas (determined by {@link #TIME_DELTA_US}) that will pass before an event start.
     */
    private static final int EVENT_START_MULTIPLIER = 5;

    /**
     * Array of integers used to represent the delta between the current and the new values of the line chart.
     */
    private static final int[] VARIANCE_ARRAY = {5, 4, -4, 7, -6, 1, 5, -4, 7, 5, 3, -10, -8};

    private int myVarianceArrayIndex;

    private LineChart myLineChart;

    private List<DefaultDataSeries<Long>> myData;

    private LineChartImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    @Override
    protected void setUp() {
      myLineChart = new LineChart();
      myLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
      myData = new ArrayList<>();
      myContentPane.add(myLineChart, BorderLayout.CENTER);
      myComponents.add(myLineChart);
      myVarianceArrayIndex = 0;
    }

    @Override
    protected void generateTestData() {
      for (int i = 0; i < TOTAL_VALUES; i++) {
        for (DefaultDataSeries<Long> series : myData) {
          ImmutableList<SeriesData<Long>> data = series.getAllData();
          long last = data.isEmpty() ? LINE_CHART_INITIAL_VALUE : data.get(data.size() - 1).value;
          long delta = VARIANCE_ARRAY[myVarianceArrayIndex++];
          // Make sure not to add negative numbers.
          long current = Math.max(last + delta, 0);
          series.add(myCurrentTimeUs, current);
          myVarianceArrayIndex %= VARIANCE_ARRAY.length;
        }
        myCurrentTimeUs += TIME_DELTA_US;
      }
    }

    protected void addLine(double rangeMin, double rangeMax, String seriesLabel, LineConfig lineConfig) {
      AnimatedRange yRange = new AnimatedRange(rangeMin, rangeMax);
      myComponents.add(yRange);
      DefaultDataSeries<Long> series = new DefaultDataSeries<>();
      RangedContinuousSeries rangedSeries = new RangedContinuousSeries(seriesLabel, myXRange, yRange, series);
      myData.add(series);
      myLineChart.addLine(rangedSeries, lineConfig);
    }

    protected void addEvent(Color eventColor, boolean isFilledEvent, boolean isBlockingEvent) {
      DefaultDataSeries<DurationData> eventData = new DefaultDataSeries<>();
      RangedSeries<DurationData> eventSeries = new RangedSeries<>(myXRange, eventData);
      EventConfig eventConfig = new EventConfig(eventColor).setText("Test Event").setIcon(UIManager.getIcon("Tree.leafIcon"));
      eventConfig.setFilled(isFilledEvent).setBlocking(isBlockingEvent);
      myLineChart.addEvent(eventSeries, eventConfig);

      // Set event duration and start time. Add it to eventData afterwards.
      long eventDuration = EVENT_DURATION_MULTIPLIER * TIME_DELTA_US;
      long eventStart = myCurrentTimeUs + EVENT_START_MULTIPLIER * TIME_DELTA_US;
      DurationData newEvent = new DurationData(eventDuration);
      eventData.add(eventStart, newEvent);
    }
  }
}
