/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.chart.hchart;

import com.android.tools.adtui.RangeScrollBarUI;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.intellij.ui.components.JBScrollBar;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * A vertical scroll bar for {@link HTreeChart} that synchronizes with {@link HTreeChart#getYRange()}.
 */
public class HTreeChartVerticalScrollBar<T> extends JBScrollBar {
  private boolean myUpdating;
  private final AspectObserver myObserver;
  @NotNull private final HTreeChart<T> myChart;

  public HTreeChartVerticalScrollBar(@NotNull HTreeChart<T> chart) {
    super(VERTICAL);
    myChart = chart;
    setPreferredSize(new Dimension(10, getPreferredSize().height));

    setUI(new RangeScrollBarUI());
    addAdjustmentListener(e -> updateYRange());
    myObserver = new AspectObserver();
    chart.getYRange().addDependency(myObserver).onChange(Range.Aspect.RANGE, this::updateValues);
    updateValues();

    chart.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        updateValues();
      }
    });
  }

  private void updateValues() {
    myUpdating = true;
    double value;

    if (myChart.getOrientation() == HTreeChart.Orientation.TOP_DOWN) {
      value = (int)myChart.getYRange().getMin();
    } else {
      value = myChart.getMaximumHeight() - myChart.getYRange().getMin() - myChart.getHeight();
    }

    setValues((int)value, myChart.getHeight(), 0, myChart.getMaximumHeight());
    myUpdating = false;
  }

  private void updateYRange() {
    if (myUpdating) {
      return;
    }
    int offset;
    if (myChart.getOrientation() == HTreeChart.Orientation.BOTTOM_UP) {
      // HTreeChart rendered bottom up, so is scrollBar.
      offset = getMaximum() - (getValue() + getVisibleAmount());
    }
    else {
      offset = getValue();
    }

    myChart.getYRange().set(offset, offset);
  }
}