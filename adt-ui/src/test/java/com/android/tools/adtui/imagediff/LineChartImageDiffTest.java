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
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.intellij.util.containers.ImmutableList;
import org.junit.After;
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
   * Array of integers used to represent the delta between the current and the new values of the line chart.
   */
  private static final int[] VARIANCE_ARRAY = {5, 4, -4, 7, -6, 1, 5, -4, 7, 5, 3, -10, -8};

  private int myVarianceArrayIndex;

  private LineChart myLineChart;

  private List<DefaultDataSeries<Long>> myData;

  private long myCurrentTimeUs;

  private Range myXRange;

  protected JPanel myContentPane;

  protected Choreographer myChoreographer;

  @Before
  public void setUp() {
    myLineChart = new LineChart();
    myData = new ArrayList<>();

    // TODO: consider moving the lines below to the base class if other components use something similar
    myCurrentTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    myXRange = new Range(myCurrentTimeUs, myCurrentTimeUs + TOTAL_VALUES * TIME_DELTA_US);
    myContentPane = new JPanel(new BorderLayout());
    myContentPane.setPreferredSize(ImageDiffUtil.TEST_IMAGE_DIMENSION);

    // We don't need to set a proper FPS to the choreographer, as we're interested in the final image only, not the animation.
    myChoreographer = new Choreographer(-1, myContentPane);
    myChoreographer.setUpdate(false);
  }

  @Test
  public void testStackedLineChart() {
    // Load target image file
    String targetFilename = "stacked_line_chart_target.png";

    // Create stacked line chart component
    List<Animatable> components = new ArrayList<>();
    components.add(myLineChart);
    components.add(myXRange);

    addStackedLine(0.0, 100.0, components, "Left Series", Color.BLUE);
    addStackedLine(0.0, 200.0, components, "Right Series", Color.RED);

    myLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
    myContentPane.add(myLineChart, BorderLayout.CENTER);
    myChoreographer.register(components);

    // Add data to line chart
    generateTestData();

    // Compare expected with image generated from main component
    ImageDiffUtil.assertImagesSimilar(targetFilename, myContentPane);
  }

  private void addLine(double rangeMin, double rangeMax, List<Animatable> components, String seriesLabel, LineConfig lineConfig) {
    Range yRange = new Range(rangeMin, rangeMax);
    components.add(yRange);
    DefaultDataSeries<Long> series = new DefaultDataSeries<>();
    RangedContinuousSeries rangedSeries = new RangedContinuousSeries(seriesLabel, myXRange, yRange, series);
    myData.add(series);
    myLineChart.addLine(rangedSeries, lineConfig);
  }

  private void addStackedLine(double rangeMin, double rangeMax, List<Animatable> components, String seriesLabel, Color lineColor) {
    addLine(rangeMin, rangeMax, components, seriesLabel, new LineConfig(lineColor).setFilled(true).setStacked(true));
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

  @After
  public void tearDown() {
    myLineChart = null;
    myData = null;
    myXRange = null;
    myContentPane = null;
    myChoreographer = null;
  }
}
