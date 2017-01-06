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

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.formatter.NetworkTrafficFormatter;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.intellij.util.containers.ImmutableList;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class LegendComponentRegistrar extends ImageDiffEntriesRegistrar {

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 2, 5);
  private static final BaseAxisFormatter NETWORK_AXIS_FORMATTER = new NetworkTrafficFormatter(1, 2, 5);

  public LegendComponentRegistrar() {
    registerSimpleLineChart();
  }
  private void registerSimpleLineChart() {
    register(new LegendComponentImageDiffEntry("simple_legend_component.png") {
      @Override
      protected void generateComponent() {
        // Create a simple line chart and register the components to the choreographer. Add thick lines to generate relevant images.
        addLine(0.0, 50.0, "Left Series", new LineConfig(Color.BLUE).setStroke(new BasicStroke(25)), MEMORY_AXIS_FORMATTER);
        addLine(0.0, 200.0, "Right Series", new LineConfig(Color.RED).setStroke(new BasicStroke(25)).setLegendIconType(
          LegendConfig.IconType.BOX), NETWORK_AXIS_FORMATTER);
      }
    });
  }

  private static abstract class LegendComponentImageDiffEntry extends AnimatedComponentImageDiffEntry {

    private static final int LINE_CHART_INITIAL_VALUE = 20;

    /**
     * Array of integers used to represent the delta between the current and the new values of the line chart.
     */
    private static final int[] VARIANCE_ARRAY = {5, 4, -4, 7, -6, 1, 5, -4, 7, 5, 3, -10, -8};

    private int myVarianceArrayIndex;

    private LineChart myLineChart;

    private LineChartModel myLineChartModel;

    private LegendComponent myLegend;

    private List<DefaultDataSeries<Long>> myData;

    private LegendComponentModel myLegendModel;

    private LegendComponentImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    @Override
    protected void setUp() {
      myLegendModel = new LegendComponentModel();
      myLegend = new LegendComponent(myLegendModel);
      myLineChartModel = new LineChartModel();
      myLineChart = new LineChart(myLineChartModel);
      myLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
      myData = new ArrayList<>();
      myContentPane.add(myLegend, BorderLayout.CENTER);
      myComponents.add(myLineChartModel);
      myComponents.add(myLegendModel);
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

    protected void addLine(double rangeMin, double rangeMax, String seriesLabel, LineConfig lineConfig, BaseAxisFormatter formatter) {
      Range yRange = new Range(rangeMin, rangeMax);
      DefaultDataSeries<Long> series = new DefaultDataSeries<>();
      RangedContinuousSeries rangedSeries = new RangedContinuousSeries(seriesLabel, myXRange, yRange, series);
      myData.add(series);
      myLineChartModel.add(rangedSeries);
      myLineChart.configure(rangedSeries, lineConfig);

      SeriesLegend legend = new SeriesLegend(rangedSeries, formatter, myXRange);
      myLegendModel.add(legend);
      myLegend.configure(legend, new LegendConfig(lineConfig));
    }
  }
}
