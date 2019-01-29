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

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.chart.statechart.StateChartColorProvider;
import com.android.tools.adtui.chart.statechart.StateChartConfig;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.updater.Updatable;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.*;
import java.util.List;

public class StateChartReducerVisualTest extends VisualTest {
  private enum ColorState {
    YELLOW,
    RED,
    BLACK
  }

  @NotNull private static final StateChartColorProvider<ColorState> COLOR_STATE_COLORS = new StateChartColorProvider<ColorState>() {
    @NotNull
    @Override
    public Color getColor(boolean isMouseOver, @NotNull ColorState value) {
      switch (value) {
        case RED:
          return JBColor.RED;
        case BLACK:
          return JBColor.BLACK;
        case YELLOW:
          return JBColor.YELLOW;
        default:
          return JBColor.BLUE;
      }
    }
  };

  private Range myViewRange;
  private StateChartModel<ColorState> myOptimizedColorChartModel;
  private StateChartModel<ColorState> myColorChartModel;
  private StateChart<ColorState> myOptimizedColorChart;
  private StateChart<ColorState> myColorChart;

  private DefaultDataSeries<ColorState> myData;

  private int mySampleSize;

  @Override
  protected List<Updatable> createModelList() {
    myViewRange = new Range(0, 0);
    myData = new DefaultDataSeries<>();
    RangedSeries<ColorState> series = new RangedSeries<>(myViewRange, myData);
    myColorChartModel = new StateChartModel<>();
    myColorChart = new StateChart<>(myColorChartModel, new StateChartConfig<>((rectangles, values) -> {}), COLOR_STATE_COLORS);
    myColorChartModel.addSeries(series);

    myOptimizedColorChartModel = new StateChartModel<>();
    myOptimizedColorChart = new StateChart<>(myOptimizedColorChartModel, COLOR_STATE_COLORS);
    myOptimizedColorChartModel.addSeries(series);
    return Collections.emptyList();
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(myColorChart, myOptimizedColorChart);
  }

  @Override
  public String getName() {
    return "StateChartReducer";
  }

  private void addData(int size) {
    ColorState[] values = ColorState.values();
    Random random = new Random();
    for (int i = 0; i < size; ++i) {
      long delta = random.nextInt(100) + 1;
      int mutateDeltaChance = random.nextInt(100);

      // We want to make some stages much larger, to ensure that our state reducer logic works
      // well even on large and unpredictable stage lengths.
      if (mutateDeltaChance == 0) {
        delta *= 100;
      }
      else if (mutateDeltaChance <= 10) {
        delta *= 10;
      }

      long x = (myData.size() > 0) ? myData.getX(myData.size() - 1) + delta : 0;
      myData.add(x, values[random.nextInt(values.length)]);
      myViewRange.setMax(x + 1);
    }
    myOptimizedColorChartModel.changed(StateChartModel.Aspect.MODEL_CHANGED);
    myColorChartModel.changed(StateChartModel.Aspect.MODEL_CHANGED);
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    myColorChart.setPreferredSize(new Dimension(0, 20));
    myOptimizedColorChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
    myColorChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));

    JPanel chartPane = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.weighty = 0.25;
    gbc.fill = GridBagConstraints.BOTH;
    chartPane.add(myColorChart, gbc);
    gbc.gridy = 1;
    chartPane.add(myOptimizedColorChart, gbc);
    gbc.gridy = 2;
    gbc.weighty = 0.5;
    chartPane.add(new JPanel(), gbc);

    final JPanel controls = VisualTest.createControlledPane(panel, chartPane);

    controls.add(VisualTest.createVariableSlider("Sample Size", 0, 10000, new VisualTests.ValueAdapter() {
      @Override
      protected void onSet(int v) {
        mySampleSize = v;
      }
    }));

    controls.add(VisualTest.createButton("Add samples", e -> addData(mySampleSize)));

    controls.add(VisualTest.createCheckbox("Text Mode", itemEvent -> {
      StateChart.RenderMode mode = itemEvent.getStateChange() == ItemEvent.SELECTED ?
                                   StateChart.RenderMode.TEXT : StateChart.RenderMode.BAR;
      myColorChart.setRenderMode(mode);
      myOptimizedColorChart.setRenderMode(mode);
    }));

    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }
}
