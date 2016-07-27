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

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.linechart.EventConfig;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.*;
import com.intellij.util.containers.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LineChartImageDiffTest {

  /**
   * Total number of values added to the line chart.
   * TODO: consider moving these to the base class if other components use something similar
   */
  private static final int TOTAL_VALUES = 50;

  /**
   * Simulated time delta, in microseconds, between each value added to the line chart.
   * TODO: consider moving these to the base class if other components use something similar
   */
  private static final long TIME_DELTA_US = TimeUnit.MILLISECONDS.toMicros(50);

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

  private long myCurrentTimeUs;

  private Range myXRange;

  private JPanel myContentPane;

  private Choreographer myChoreographer;

  private List<Animatable> myComponents;

  @Before
  public void setUp() {
    myLineChart = new LineChart();
    myLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
    myData = new ArrayList<>();
    myComponents = new ArrayList<>();

    // TODO: consider moving the lines below to the base class if other components use something similar
    myCurrentTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    myXRange = new Range(myCurrentTimeUs, myCurrentTimeUs + TOTAL_VALUES * TIME_DELTA_US);
    myContentPane = new JPanel(new BorderLayout());

    // We don't need to set a proper FPS to the choreographer, as we're interested in the final image only, not the animation.
    myChoreographer = new Choreographer(-1, myContentPane);
    myChoreographer.setUpdate(false);

    myContentPane.add(myLineChart, BorderLayout.CENTER);
    myComponents.add(myLineChart);
    myComponents.add(myXRange);
  }

  @Test
  public void testStackedLineChart() {
    doTestLineChart("stacked_line_chart_baseline.png", () -> {
      // Create a filled, stacked line chart and register the components to the choreographer
      addLine(0.0, 100.0, "Left Series", new LineConfig(Color.BLUE).setFilled(true).setStacked(true));
      addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setFilled(true).setStacked(true));
    });
  }

  @Test
  public void testSimpleLineChart() {
    // TODO: investigate if there's a need for having a different image for each OS
    doTestLineChart("simple_line_chart_baseline.png", () -> {
      // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
      addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
      addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)));
    });
  }

  @Test
  public void testSteppedLineChart() {
    doTestLineChart("stepped_line_chart_baseline.png", () -> {
      // Create a stepped line chart and register the components to the choreographer. Add thick lines to generate relevant images.
      addLine(0.0, 50.0, "First Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(10)).setStepped(true));
      addLine(0.0, 100.0, "Second Series", new LineConfig(Color.RED).setStroke(new BasicStroke(10)).setStepped(true));
      addLine(0.0, 200.0, "Third Series", new LineConfig(Color.GREEN).setStroke(new BasicStroke(10)).setStepped(true));
    });
  }

  @Test
  public void testSimpleEventLineChart() {
    doTestLineChart("simple_event_line_chart_baseline.png", () -> {
      // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
      addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
      addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)));

      // Add a simple event to the line chart
      addEvent(Color.BLACK, false, false);
    });
  }

  @Test
  public void testBlockingEventLineChart() {
    doTestLineChart("blocking_event_line_chart_baseline.png", () -> {
      // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
      addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
      addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)));

      // Add a blocking event to the line chart
      addEvent(Color.BLACK, false, true);
    });
  }

  @Test
  public void testFilledEventLineChart() {
    doTestLineChart("filled_event_line_chart_baseline.png", () -> {
      // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
      addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
      addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)));

      // Add a filled event to the line chart
      addEvent(Color.GREEN, true, false);
    });
  }

  /**
   * Test that generated main component is similar enough to baseline image.
   *
   * @param baselineFilename filename of baseline image
   * @param lineChartGenerator code to generate the line chart corresponding to the test
   */
  private void doTestLineChart(String baselineFilename, Runnable lineChartGenerator) {
    // Generate the line chart corresponding to the current test
    lineChartGenerator.run();

    // Register the chart components in the choreographer
    myChoreographer.register(myComponents);

    // Add data to line chart
    generateTestData();

    // Compare baseline image with the one generated from main component
    ImageDiffUtil.assertImagesSimilar(baselineFilename, myContentPane);
  }

  private void addEvent(Color eventColor, boolean isFilledEvent, boolean isBlockingEvent) {
    DefaultDataSeries<DurationData> eventData = new DefaultDataSeries<>();
    RangedSeries<DurationData> eventSeries = new RangedSeries<>(myXRange, eventData);
    EventConfig eventConfig = new EventConfig(eventColor).setText("Test Event").setIcon(UIManager.getIcon("Menu.arrowIcon"));
    eventConfig.setFilled(isFilledEvent).setBlocking(isBlockingEvent);
    myLineChart.addEvent(eventSeries, eventConfig);

    // Set event duration and start time. Add it to eventData afterwards.
    long eventDuration = EVENT_DURATION_MULTIPLIER * TIME_DELTA_US;
    long eventStart = myCurrentTimeUs + EVENT_START_MULTIPLIER * TIME_DELTA_US;
    DurationData newEvent = new DurationData(eventDuration);
    eventData.add(eventStart, newEvent);
  }

  private void addLine(double rangeMin, double rangeMax, String seriesLabel, LineConfig lineConfig) {
    Range yRange = new Range(rangeMin, rangeMax);
    myComponents.add(yRange);
    DefaultDataSeries<Long> series = new DefaultDataSeries<>();
    RangedContinuousSeries rangedSeries = new RangedContinuousSeries(seriesLabel, myXRange, yRange, series);
    myData.add(series);
    myLineChart.addLine(rangedSeries, lineConfig);
  }

  private void generateTestData() {
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
    myChoreographer.step();
  }
}
