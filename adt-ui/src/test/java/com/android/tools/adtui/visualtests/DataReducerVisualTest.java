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
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.util.Arrays;
import java.util.List;

// TODO: Add scrolling support to LineCharts
// TODO: As SelectionComponent used only for zooming mechanism, consider replacing it with only zooming without selection
public class DataReducerVisualTest extends VisualTest {
  private static final int AXIS_SIZE = 80;

  private Range myGlobalXRange;
  private AnimatedRange myViewXRange;
  private AnimatedRange mySelectionXRange;
  private Range myYRange;

  private LineChart myLineChart;
  private LineChart myOptimizedLineChart;
  private AxisComponent myXAxis;

  private DefaultDataSeries<Long> myData;
  private RangedContinuousSeries mySeries;
  private int myVariance = 10;
  private int mySampleSize = 10;
  private SelectionComponent mySelection;

  @Override
  protected List<Updatable> createComponentsList() {
    myGlobalXRange = new Range(0, 0);
    myViewXRange = new AnimatedRange();
    mySelectionXRange = new AnimatedRange();
    myYRange = new Range(0, 0);

    myLineChart = new LineChart((shape, config) -> shape);
    myOptimizedLineChart = new LineChart();

    myXAxis = new AxisComponent.Builder(myViewXRange, new SingleUnitAxisFormatter(1, 5, 1, ""), AxisComponent.AxisOrientation.BOTTOM).build();
    mySelection = new SelectionComponent(mySelectionXRange, myViewXRange);

    myData = new DefaultDataSeries<>();
    mySeries = new RangedContinuousSeries("Straight", myViewXRange, myYRange, myData);

    myLineChart.addLine(mySeries, new LineConfig(JBColor.BLUE));
    myOptimizedLineChart.addLine(mySeries, new LineConfig(JBColor.RED));

    return Arrays.asList(myViewXRange, mySelectionXRange, myLineChart, myOptimizedLineChart, myXAxis, mySelection);
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(myLineChart, myOptimizedLineChart);
  }

  @Override
  public String getName() {
    return "DataReducer";
  }

  private void addData(int variance, int count) {
    for (int i = 0; i < count; ++i) {
      ImmutableList<SeriesData<Long>> data = myData.getAllData();
      long x = data.isEmpty() ? 0 : data.get(data.size() - 1).x + 1;
      long last = data.isEmpty() ? 0 : data.get(data.size() - 1).value;
      float delta = ((float)Math.random() - 0.5f) * variance;
      // Make sure not to add negative numbers.
      long current = Math.max(last + (long)delta, 0);
      myData.add(x, current);
      myViewXRange.setMax(x + 1);
      myGlobalXRange.setMax(x + 1);
    }
  }

  private JLayeredPane createLayeredSelection(JComponent host) {
    JBLayeredPane layeredPane = new JBLayeredPane();
    layeredPane.add(host);
    layeredPane.add(mySelection);
    layeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          for (Component c : host.getComponents()) {
              c.setBounds(0, 0, dim.width, dim.height);
          }
        }
      }
    });
    return layeredPane;
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    JPanel chartPane = new JPanel();
    chartPane.setLayout(new BoxLayout(chartPane, BoxLayout.Y_AXIS));
    myXAxis.setMaximumSize(new Dimension(Integer.MAX_VALUE, AXIS_SIZE));
    chartPane.add(myLineChart);
    chartPane.add(createLayeredSelection(myOptimizedLineChart));
    chartPane.add(myXAxis);

    JPanel controls = VisualTest.createControlledPane(panel, chartPane);

    myLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
    myOptimizedLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));

    controls.add(VisualTest.createVariableSlider("Variance", 0, 100, new VisualTests.Value() {
      @Override
      public void set(int v) {
        myVariance = v;
      }

      @Override
      public int get() {
        return myVariance;
      }
    }));

    controls.add(VisualTest.createVariableSlider("Sample Size", 10, 10000, new VisualTests.Value() {
      @Override
      public void set(int v) {
        mySampleSize = v;
      }

      @Override
      public int get() {
        return mySampleSize;
      }
    }));

    controls.add(VisualTest.createCheckbox("Filled line", itemEvent -> {
      boolean isFilled = itemEvent.getStateChange() == ItemEvent.SELECTED;
      myLineChart.getLineConfig(mySeries).setFilled(isFilled);
      myOptimizedLineChart.getLineConfig(mySeries).setFilled(isFilled);
    }));

    controls.add(VisualTest.createCheckbox("Stepped line", itemEvent -> {
      boolean isStepped = itemEvent.getStateChange() == ItemEvent.SELECTED;
      myLineChart.getLineConfig(mySeries).setStepped(isStepped);
      myOptimizedLineChart.getLineConfig(mySeries).setStepped(isStepped);
    }));


    controls.add(VisualTest.createButton("Add samples", e -> addData(myVariance, mySampleSize)));
    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }
}
