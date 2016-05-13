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

package com.android.tools.adtui.visual;

import com.android.annotations.NonNull;
import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.model.RangedContinuousSeries;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class AccordionVisualTest extends VisualTest {

  private static final int DOUBLE_CLICK = 2;
  private static final int LINECHART_DATA_DELAY = 100; // milliseconds.

  private static final int MIN_SIZE = 50;
  private static final int PREFERRED_SIZE = 100;
  private static final int MAX_SIZE = 200;

  private int mChartCountX;

  private int mChartCountY;

  private long mStartTimeMs;

  @NonNull
  private AccordionLayout mAccordionX;

  @NonNull
  private AccordionLayout mAccordionY;

  @NonNull
  private JPanel mPanelX;

  @NonNull
  private JPanel mPanelY;

  @NonNull
  private AnimatedTimeRange mAnimatedTimeRange;

  private ArrayList<RangedContinuousSeries> mData;

  @Override
  protected List<Animatable> createComponentsList() {
    mStartTimeMs = System.currentTimeMillis();
    Range xRange = new Range();
    mAnimatedTimeRange = new AnimatedTimeRange(xRange, mStartTimeMs);

    mPanelX = new JPanel();
    mPanelY = new JPanel();
    mAccordionX = new AccordionLayout(mPanelX, AccordionLayout.Orientation.HORIZONTAL);
    mAccordionY = new AccordionLayout(mPanelY, AccordionLayout.Orientation.VERTICAL);
    mPanelX.setLayout(mAccordionX);
    mPanelY.setLayout(mAccordionY);
    List<Animatable> componentsList = new ArrayList<>();

    // Add the scene components to the list
    componentsList.add(mAccordionX);
    componentsList.add(mAccordionY);
    componentsList.add(mAnimatedTimeRange);
    componentsList.add(xRange);

    mData = new ArrayList<>();
    Range mYRange = new Range(0.0, 100.0);
    for (int i = 0; i < 4; i++) {
      if (i % 2 == 0) {
        mYRange = new Range(0.0, 100.0);
        componentsList.add(mYRange);
      }
      RangedContinuousSeries ranged = new RangedContinuousSeries("Widgets", xRange, mYRange);
      mData.add(ranged);
    }
    return componentsList;
  }

  @Override
  protected void registerComponents(List<AnimatedComponent> components) {
  }

  @Override
  protected void reset() {
    super.reset();

    mChartCountY = 0;
    mChartCountX = 0;
  }

  @Override
  public String getName() {
    return "Accordion";
  }

  @Override
  protected void populateUi(@NonNull JPanel panel) {
    panel.setLayout(new GridLayout(0, 1));

    mUpdateDataThread = new Thread() {
      @Override
      public void run() {
        super.run();
        try {
          while (true) {
            long now = System.currentTimeMillis() - mStartTimeMs;
            for (RangedContinuousSeries rangedSeries : mData) {
              int size = rangedSeries.getSeries().size();
              long last = size > 0 ? rangedSeries.getSeries().getY(size - 1) : 0;
              float delta = 10 * ((float)Math.random() - 0.45f);
              rangedSeries.getSeries().add(now, last + (long)delta);
            }
            Thread.sleep(LINECHART_DATA_DELAY);
          }
        }
        catch (InterruptedException e) {
        }
      }
    };
    mUpdateDataThread.start();

    // Creates the vertical accordion at the top half.
    JPanel yPanel = new JPanel();
    panel.add(yPanel);
    yPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    final JPanel controlsY = VisualTests.createControlledPane(yPanel, mPanelY);

    controlsY.add(VisualTests.createButton("Reset Weights", listener -> {
      mAccordionY.resetComponents();
    }));
    controlsY.add(VisualTests.createButton("Add Chart", listener -> {
      final LineChart chart = generateChart(mAccordionY, AccordionLayout.Orientation.VERTICAL,
                                            0, PREFERRED_SIZE, Integer.MAX_VALUE);
      mPanelY.add(chart);
      mChartCountY++;
    }));
    controlsY.add(VisualTests.createButton("Add Chart With Min", listener -> {
      final LineChart chart = generateChart(mAccordionY, AccordionLayout.Orientation.VERTICAL,
                                            MIN_SIZE, PREFERRED_SIZE, Integer.MAX_VALUE);
      mPanelY.add(chart);
      mChartCountY++;
    }));
    controlsY.add(VisualTests.createButton("Add Chart With Small Max", listener -> {
      final LineChart chart = generateChart(mAccordionY, AccordionLayout.Orientation.VERTICAL,
                                            0, PREFERRED_SIZE, MAX_SIZE);
      mPanelY.add(chart);
      mChartCountY++;
    }));
    controlsY.add(VisualTests.createButton("Remove Last Chart", listener -> {
      mPanelY.remove(--mChartCountY);
    }));

    controlsY.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));

    // Creates the horizontal accordion at the bottom half.
    JPanel xPanel = new JPanel();
    panel.add(xPanel);
    xPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    final JPanel controlsX = VisualTests.createControlledPane(xPanel, mPanelX);
    controlsX.add(VisualTests.createButton("Reset Weights", listener -> {
      mAccordionX.resetComponents();
    }));
    controlsX.add(VisualTests.createButton("Add Chart", listener -> {
      final LineChart chart = generateChart(mAccordionX, AccordionLayout.Orientation.HORIZONTAL,
                                            0, PREFERRED_SIZE, Integer.MAX_VALUE);
      mPanelX.add(chart);
      mChartCountX++;
    }));
    controlsX.add(VisualTests.createButton("Add Chart With Min", listener -> {
      final LineChart chart = generateChart(mAccordionX, AccordionLayout.Orientation.HORIZONTAL,
                                            MIN_SIZE, PREFERRED_SIZE, Integer.MAX_VALUE);
      mPanelX.add(chart);
      mChartCountX++;
    }));
    controlsX.add(VisualTests.createButton("Add Chart With Small Max", listener -> {
      final LineChart chart = generateChart(mAccordionX, AccordionLayout.Orientation.HORIZONTAL,
                                            0, PREFERRED_SIZE, MAX_SIZE);
      mPanelX.add(chart);
      mChartCountX++;
    }));
    controlsX.add(VisualTests.createButton("Remove Last Chart", listener -> {
      mPanelX.remove(--mChartCountX);
    }));

    controlsX.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  @NonNull
  private LineChart generateChart(AccordionLayout layout, AccordionLayout.Orientation direction,
                                  int minSize, int preferredSize, int maxSize) {
    LineChart chart = new LineChart(getName(), mData);
    if (direction == AccordionLayout.Orientation.VERTICAL) {
      chart.setMinimumSize(new Dimension(0, minSize));
      chart.setPreferredSize(new Dimension(0, preferredSize));
      chart.setMaximumSize(new Dimension(0, maxSize));
    }
    else {
      chart.setMinimumSize(new Dimension(minSize, 0));
      chart.setPreferredSize(new Dimension(preferredSize, 0));
      chart.setMaximumSize(new Dimension(maxSize, 0));
    }
    chart.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    chart.setToolTipText("Double-click to expand/collapse.");
    chart.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == DOUBLE_CLICK) {
          switch (layout.getState(chart)) {
            case MINIMIZE:
              layout.setState(chart, AccordionLayout.AccordionState.PREFERRED);
              break;
            case PREFERRED:
              layout.setState(chart, AccordionLayout.AccordionState.MAXIMIZE);
              break;
            case MAXIMIZE:
              layout.setState(chart, AccordionLayout.AccordionState.MINIMIZE);
              break;
          }
        }
      }
    });
    addToChoreographer(chart);
    return chart;
  }
}
