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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.*;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LineChartEntriesRegistrar extends ImageDiffEntriesRegistrar {

  public LineChartEntriesRegistrar() {
    registerStackedLineChart();
    registerStackedLineChartWithNonZeroMinValue();
    registerSimpleLineChart();
    registerRepeatedValuesLineChart();
    registerSteppedLineChart();
    registerSimpleEventLineChart();
    registerAttachedEventLineChart();
    registerEventWithCustomConfigLineChart();
    // WIP DurationDataRenderer revamp - will re-enable after pixels are finalized.
    //registerFilledEventLineChart();
  }

  private void registerStackedLineChart() {
    register(new LineChartImageDiffEntry("stacked_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a filled, stacked line chart and register the components to the choreographer
        addLine(0.0, 100.0, "Left Series", new LineConfig(Color.BLUE).setFilled(true).setStacked(true));
        addLine(0.0, 100.0, "Right Series", new LineConfig(Color.RED).setFilled(true).setStacked(true));
      }
    });
  }

  // Ensures that the stacked lines works correctly with a y range that's min value is not zero.
  private void registerStackedLineChartWithNonZeroMinValue() {
    register(new LineChartImageDiffEntry("stacked_line_chart_with_non_zero_min_value_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a filled, stacked line chart and register the components to the choreographer
        addLine(50.0, 100.0, "Left Series", new LineConfig(Color.BLUE).setFilled(true).setStacked(true));
        addLine(50.0, 100.0, "Right Series", new LineConfig(Color.RED).setFilled(true).setStacked(true));
      }

      @Override
      protected void generateTestData() {
        myXRange.set(0, 6);
        myData.get(0).add(1, 51L);
        myData.get(0).add(5, 61L);

        myData.get(1).add(1, 20L);
        myData.get(1).add(5, 5L);
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

  private void registerRepeatedValuesLineChart() {
    register(new LineChartImageDiffEntry("repeated_values_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
      }

      @Override
      protected void generateTestData() {
        myXRange.set(0, 12);
        myData.get(0).add(0, 0L);
        myData.get(0).add(1, 51L);
        myData.get(0).add(2, 51L);
        myData.get(0).add(3, 51L);
        myData.get(0).add(4, 51L);
        myData.get(0).add(5, 61L);
        myData.get(0).add(6, 61L);
        myData.get(0).add(7, 61L);
        myData.get(0).add(8, 0L);
        myData.get(0).add(9, 0L);
        myData.get(0).add(10, 0L);
        myData.get(0).add(11, 32L);
      }
    });
  }

  private void registerSteppedLineChart() {
    register(new LineChartImageDiffEntry("stepped_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a stepped line chart and register the components to the choreographer. Add thick lines to generate relevant images.
        BasicStroke dashedStroke = new BasicStroke(10.0f,
                                                   BasicStroke.CAP_SQUARE,
                                                   BasicStroke.JOIN_MITER,
                                                   10.0f,  // Miter limit, Swing's default
                                                   new float[]{4.0f, 20.0f},  // Dash pattern in pixel
                                                   0.0f);  // Dash phase - just starts at zero.
        addLine(0.0, 50.0, "First Series", new LineConfig(Color.BLUE).setStroke(dashedStroke).setStepped(true));
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
        addEvent(Color.BLACK, null, null);
      }
    });
  }

  private void registerAttachedEventLineChart() {
    register(new LineChartImageDiffEntry("attached_event_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
        addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
        addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)));

        // Add an attached event to the line chart
        addEvent(Color.BLACK, myLineChartModel.getSeries().get(0), null);
      }
    });
  }

  private void registerEventWithCustomConfigLineChart() {
    register(new LineChartImageDiffEntry("event_with_custom_config_line_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
        RangedContinuousSeries firstLine =
          addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)));
        RangedContinuousSeries secondLine =
          addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)));

        Map<RangedContinuousSeries, LineConfig> configs = new HashMap<>();
        configs.put(firstLine, LineConfig.copyOf(myLineChart.getLineConfig(firstLine)).setColor(Color.GREEN));
        configs.put(secondLine, LineConfig.copyOf(myLineChart.getLineConfig(secondLine)).setColor(Color.ORANGE));
        // Add a blocking event to the line chart
        addEvent(Color.BLACK, null, configs);
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
        addEvent(Color.GREEN, null, null);
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

    LineChart myLineChart;

    LineChartModel myLineChartModel;

    List<DefaultDataSeries<Long>> myData;

    OverlayComponent myOverlayComponent;

    private LineChartImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    @Override
    protected void setUp() {
      TabularLayout layout = new TabularLayout("*", "*");
      myContentPane.setLayout(layout);
      myLineChartModel = new LineChartModel();
      myLineChart = new LineChart(myLineChartModel);
      myLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
      myOverlayComponent = new OverlayComponent(myLineChart);
      myData = new ArrayList<>();
      JPanel lineChartPanel = new JPanel(new BorderLayout());
      lineChartPanel.setOpaque(false);
      JPanel overlayPanel = new JPanel(new BorderLayout());
      overlayPanel.setOpaque(false);
      lineChartPanel.add(myLineChart, BorderLayout.CENTER);
      overlayPanel.add(myOverlayComponent, BorderLayout.CENTER);
      myContentPane.add(overlayPanel, new TabularLayout.Constraint(0, 0));
      myContentPane.add(lineChartPanel, new TabularLayout.Constraint(0, 0));
      myComponents.add(myLineChartModel);
      myVarianceArrayIndex = 0;
    }

    @Override
    protected void generateTestData() {
      for (int i = 0; i < TOTAL_VALUES; i++) {
        for (DefaultDataSeries<Long> series : myData) {
          List<SeriesData<Long>> data = series.getAllData();
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

    protected RangedContinuousSeries addLine(double rangeMin, double rangeMax, String seriesLabel, LineConfig lineConfig) {
      Range yRange = new Range(rangeMin, rangeMax);
      DefaultDataSeries<Long> series = new DefaultDataSeries<>();
      RangedContinuousSeries rangedSeries = new RangedContinuousSeries(seriesLabel, myXRange, yRange, series);
      myData.add(series);
      myLineChartModel.add(rangedSeries);
      myLineChart.configure(rangedSeries, lineConfig);
      return rangedSeries;
    }

    protected void addEvent(Color eventColor,
                            RangedContinuousSeries attachedSeries,
                            @Nullable Map<RangedContinuousSeries, LineConfig> customConfigs) {
      DefaultDataSeries<DefaultDurationData> eventData = new DefaultDataSeries<>();
      RangedSeries<DefaultDurationData> eventSeries = new RangedSeries<>(myXRange, eventData);
      DurationDataModel<DefaultDurationData> durationModel = new DurationDataModel<>(eventSeries);
      if (attachedSeries != null) {
        durationModel.setAttachedSeries(attachedSeries, Interpolatable.SegmentInterpolator);
      }
      DurationDataRenderer<DefaultDurationData> durationRenderer =
        new DurationDataRenderer.Builder<>(durationModel, eventColor)
          .setLabelProvider(durationData -> "Test Event")
          .setStroke(new BasicStroke(5))
          .setLabelColors(Color.BLACK, Color.GRAY, Color.GRAY, Color.WHITE).build();

      if (customConfigs != null) {
        for (Map.Entry<RangedContinuousSeries, LineConfig> entry : customConfigs.entrySet()) {
          durationRenderer.addCustomLineConfig(entry.getKey(), entry.getValue());
        }
      }

      myLineChart.addCustomRenderer(durationRenderer);
      myOverlayComponent.addDurationDataRenderer(durationRenderer);
      myComponents.add(durationModel);

      // Set event duration and start time. Add it to eventData afterwards.
      long eventDuration = EVENT_DURATION_MULTIPLIER * TIME_DELTA_US;
      long eventStart = myCurrentTimeUs + EVENT_START_MULTIPLIER * TIME_DELTA_US;
      DefaultDurationData newEvent = new DefaultDurationData(eventDuration);
      eventData.add(eventStart, newEvent);
    }
  }
}
