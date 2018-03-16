/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for handing CpuCellRendering, this class is used by Renderers that create state charts within
 * a list in the {@link CpuProfilerStageView}
 * @param <T> The first type is the type used for the {@link ListCellRenderer}, this type should match the model used for the List.
 * @param <K> The second type is the type used for the {@link StateChartData}, this should match the model used for the {@link StateChart}
 */
public abstract class CpuCellRenderer<T, K> implements ListCellRenderer<T> {
  /**
   * Label to display the thread name on a cell.
   */
  protected final JLabel myLabel;

  /**
   * Keep the index of the item currently hovered.
   */
  protected int myHoveredIndex = -1;

  /**
   * Maps an id to a {@link StateChartData} containing the chart info that should be rendered
   * on the cell corresponding to that thread.
   */
  protected final Map<Integer, StateChartData<K>> myStateCharts;

  /**
   * {@link UpdatableManager} responsible for managing the threads state charts.
   */
  protected final UpdatableManager myUpdatableManager;

  public CpuCellRenderer(JList<T> list, UpdatableManager updatableManager) {
    myLabel = new JLabel();
    myLabel.setFont(AdtUiUtils.DEFAULT_FONT);
    Border rightSeparator = BorderFactory.createMatteBorder(0, 0, 0, 1, ProfilerColors.THREAD_LABEL_BORDER);
    Border marginLeft = JBUI.Borders.emptyLeft(10);
    myLabel.setBorder(new CompoundBorder(rightSeparator, marginLeft));
    myLabel.setOpaque(true);
    myUpdatableManager = updatableManager;
    myStateCharts = new HashMap<>();
    list.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        Point p = new Point(e.getX(), e.getY());
        int oldHoveredIndex = myHoveredIndex;
        myHoveredIndex = list.locationToIndex(p);
        // Pass the mouse moved event onto the child statecharts.
        // First we pass a mouse exited to reset positioning.
        if (oldHoveredIndex != myHoveredIndex && list.getModel().getSize() > oldHoveredIndex && oldHoveredIndex >= 0) {
          StateChart<K> chart = getChartForModel(list.getModel().getElementAt(myHoveredIndex));
          chart.mouseExited(e);
        }
        // Second we pass the updated position of the mouse.
        if (myHoveredIndex >= 0) {
          StateChart<K> chart = getChartForModel(list.getModel().getElementAt(myHoveredIndex));
          chart.mouseMoved(e);
        }
      }
    });
  }

  /**
   * Function to translate a model to {@link StateChart}.
   */
  @NotNull
  abstract StateChart<K> getChartForModel(@NotNull T model);

  /**
   * Contains a state chart and its corresponding model.
   */
  protected static class StateChartData<T> {
    private final StateChart<T> myChart;
    private final StateChartModel<T> myModel;

    public StateChartData(StateChart<T> chart, StateChartModel<T> model) {
      myChart = chart;
      myModel = model;
    }

    public StateChart<T> getChart() {
      return myChart;
    }

    public StateChartModel<T> getModel() {
      return myModel;
    }
  }
}
