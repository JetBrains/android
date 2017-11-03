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


import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.Range;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class RangeEntriesRegistrar extends ImageDiffEntriesRegistrar {

  public RangeEntriesRegistrar() {
    registerSimpleHorizontalRange();
    registerMultipleSeriesHorizontalRange();
    registerSimpleVerticalRange();
    registerMultipleSeriesVerticalRange();
    registerAnimatedTimeRange();
    registerAnimatedTimeNoAnimationRange();
  }

  private void registerSimpleHorizontalRange() {
    register(new RangeImageDiffEntry("simple_horizontal_range_baseline.png") {
      @Override
      protected void generateComponent() {
        addLine(new LineConfig(Color.RED).setFilled(true));
      }

      @Override
      protected void generateTestData() {
        assert myData.size() == 1; // Only one series was added to the chart
        DefaultDataSeries<Long> series = myData.get(0);

        // Fill the line chart almost entirely
        long constantLineChartValue = (long) (0.9 * LINE_CHART_RANGE_MAX);
        series.add((long) myXRange.getMin(), constantLineChartValue);
        series.add((long) myXRange.getMax(), constantLineChartValue);
      }
    });
  }

  private void registerMultipleSeriesHorizontalRange() {
    register(new RangeImageDiffEntry("multiple_series_horizontal_range_baseline.png") {
      @Override
      protected void generateComponent() {
        addLine(new LineConfig(Color.RED).setFilled(true));
        addLine(new LineConfig(Color.BLUE).setFilled(true));
      }

      @Override
      protected void generateTestData() {
        assert myData.size() == 2; // Two series were added to the chart
        DefaultDataSeries<Long> series1 = myData.get(0);
        DefaultDataSeries<Long> series2 = myData.get(1);

        // Fill the line chart almost entirely
        long constantLineChartValue = (long) (0.9 * LINE_CHART_RANGE_MAX);
        long rangeMid = (long) (myXRange.getMax() + myXRange.getMin()) / 2;

        // Let the first series occupy the first horizontal half of the range
        series1.add((long) myXRange.getMin(), constantLineChartValue);
        series1.add(rangeMid, constantLineChartValue);

        // Let the second series occupy the second horizontal half of the range
        series2.add(rangeMid + 1, constantLineChartValue);
        series2.add((long) myXRange.getMax(), constantLineChartValue);
      }
    });
  }

  private void registerSimpleVerticalRange() {
    register(new RangeImageDiffEntry("simple_vertical_range_baseline.png") {
      @Override
      protected void generateComponent() {
        addLine(new LineConfig(Color.RED).setFilled(true));
      }

      @Override
      protected void generateTestData() {
        assert myData.size() == 1; // Only one series was added to the chart
        DefaultDataSeries<Long> series = myData.get(0);

        // Add the max range to the chart. That should occupy the chart entirely.
        long constantLineChartValue = (long) LINE_CHART_RANGE_MAX;
        series.add((long) myXRange.getMin(), constantLineChartValue);
        series.add((long) myXRange.getMax(), constantLineChartValue);
      }
    });
  }

  private void registerMultipleSeriesVerticalRange() {
    register(new RangeImageDiffEntry("multiple_series_vertical_range_baseline.png") {
      @Override
      protected void generateComponent() {
        addLine(new LineConfig(Color.RED).setFilled(true));
        addLine(new LineConfig(Color.BLUE).setStroke(new BasicStroke(50)));
      }

      @Override
      protected void generateTestData() {
        assert myData.size() == 2; // Two series were added to the chart
        DefaultDataSeries<Long> series1 = myData.get(0);
        DefaultDataSeries<Long> series2 = myData.get(1);

        // Add the max range to the chart. That should occupy the chart entirely.
        long constantLineChartValue = (long) LINE_CHART_RANGE_MAX;
        series1.add((long) myXRange.getMin(), constantLineChartValue);
        series1.add((long) myXRange.getMax(), constantLineChartValue);

        // Add a value higher than the previous max. The range is expected to increase its max value to fit this new line.
        // Consequently, the line above won't occupy the entire chart anymore.
        long series2ConstantValue = (long) LINE_CHART_RANGE_MAX + 15;
        series2.add((long) myXRange.getMin(), series2ConstantValue);
        series2.add((long) myXRange.getMax(), series2ConstantValue);

        // When adding a value that is higher than current range's max, the choreographer increases the range gently in each animation step,
        // providing a better visual experience if compared to increasing the range to the new max all of a sudden.
        // Therefore, step the choreographer multiple times to simulate this behavior.
        int extraSteps = 60;
        for (int i = 0; i < extraSteps; i++) {
          myTimer.step();
        }
      }
    });
  }

  private void registerAnimatedTimeRange() {
    register(new RangeImageDiffEntry("animated_time_range_baseline.png") {

      private Range myMockTimeRange;

      @Override
      protected void generateComponent() {
        long nanoTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        // Set a range duration in us
        long rangeDurationUs = myRangeEndUs - myRangeStartUs;

        // Use a mock start time in the past to use as range start time.
        long mockRangeStartUs = nanoTimeUs - rangeDurationUs;

        // Set range's min to start time and max to (start time + duration/2).
        // If a normal range was used, it would occupy, horizontally, half of the screen
        myMockTimeRange = new Range(mockRangeStartUs, mockRangeStartUs + rangeDurationUs / 2);

        addLine(new LineConfig(Color.RED).setFilled(true), myMockTimeRange);

        AnimatedTimeRange animatedTimeRange = new AnimatedTimeRange(myMockTimeRange, 0);
        myComponents.add(animatedTimeRange);
      }

      @Override
      protected void generateTestData() {
        assert myData.size() == 1; // Only one series was added to the chart
        DefaultDataSeries<Long> series = myData.get(0);

        // Calling step() will animate the time range and, as a result, it will set the max to the current time.
        // That will make the range to occupy the entire screen.
        myTimer.step();

        // Add the max range to the chart. That should occupy the chart entirely.
        long constantLineChartValue = (long) LINE_CHART_RANGE_MAX;
        series.add((long) myMockTimeRange.getMin(), constantLineChartValue);
        series.add((long) myMockTimeRange.getMax(), constantLineChartValue);
      }
    });
  }

  private void registerAnimatedTimeNoAnimationRange() {
    register(new RangeImageDiffEntry("animated_time_range_no_animation_baseline.png") {

      private Range myMockTimeRange;

      @Override
      protected void generateComponent() {
        long nanoTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        // Set a range duration in us
        long rangeDurationUs = myRangeEndUs - myRangeStartUs;

        // Use a mock start time in the past to use as range start time.
        long mockRangeStartUs = nanoTimeUs - rangeDurationUs;

        // Set range's min to start time and max to (start time + duration/2).
        // If a normal range was used, it would occupy, horizontally, half of the screen
        myMockTimeRange = new Range(mockRangeStartUs, mockRangeStartUs + rangeDurationUs / 2);

        addLine(new LineConfig(Color.RED).setFilled(true), myMockTimeRange);

        AnimatedTimeRange animatedTimeRange = new AnimatedTimeRange(myMockTimeRange, 0);
        myComponents.add(animatedTimeRange);
      }

      @Override
      protected void generateTestData() {
        assert myData.size() == 1; // Only one series was added to the chart
        DefaultDataSeries<Long> series = myData.get(0);

        // Not calling step() will make the time range not to animate.
        // As a result, it will behave like a normal range, not setting the current time as max.
        // That will make the range to occupy half of the screen.

        // Add the max range to the chart. That should occupy the chart entirely.
        long constantLineChartValue = (long) LINE_CHART_RANGE_MAX;
        series.add((long) myMockTimeRange.getMin(), constantLineChartValue);
        series.add((long) myMockTimeRange.getMax(), constantLineChartValue);
      }
    });
  }

  private static abstract class RangeImageDiffEntry extends AnimatedComponentImageDiffEntry {

    private static final double LINE_CHART_RANGE_MIN = 0;

    protected static final double LINE_CHART_RANGE_MAX = 100;

    protected List<DefaultDataSeries<Long>> myData;

    private LineChart myLineChart;

    private LineChartModel myLineChartModel;

    private Range myYRange;

    private RangeImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    @Override
    protected void setUp() {
      myLineChartModel = new LineChartModel();
      myLineChart = new LineChart(myLineChartModel);
      myLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
      myData = new ArrayList<>();
      myContentPane.add(myLineChart, BorderLayout.CENTER);
      myComponents.add(myLineChartModel);
      myYRange = new Range(LINE_CHART_RANGE_MIN, LINE_CHART_RANGE_MAX);
    }

    protected void addLine(LineConfig lineConfig, Range xRange) {
      DefaultDataSeries<Long> series = new DefaultDataSeries<>();
      RangedContinuousSeries rangedSeries = new RangedContinuousSeries("Series #" + myData.size(), xRange, myYRange, series);
      myData.add(series);
      myLineChartModel.add(rangedSeries);
      myLineChart.configure(rangedSeries, lineConfig);
    }

    protected void addLine(LineConfig lineConfig) {
      addLine(lineConfig, myXRange);
    }
  }
}
