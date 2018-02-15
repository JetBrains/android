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


import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ThreadCellRenderer extends CpuCellRenderer<CpuThreadsModel.RangedCpuThread> {
  /**
   * Maps a {@link StateChart} to a {@link EnumColors} helper class to return the proper color object for the {@link StateChart}
   */
  @NotNull
  private final Map<StateChart<CpuProfilerStage.ThreadState>, EnumColors<CpuProfilerStage.ThreadState>> myColors;

  public ThreadCellRenderer(JList<CpuThreadsModel.RangedCpuThread> list, UpdatableManager updatableManager) {
    super(list, updatableManager);
    myColors = new HashMap<>();
  }

  @Override
  public Component getListCellRendererComponent(JList list,
                                                CpuThreadsModel.RangedCpuThread value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    JPanel panel = new JPanel(new TabularLayout("150px,*", "*"));
    panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, JBUI.scale(15)));
    panel.setBackground(list.getBackground());

    myLabel.setText(value.getName());
    myLabel.setBackground(ProfilerColors.THREAD_LABEL_BACKGROUND);
    myLabel.setForeground(ProfilerColors.THREAD_LABEL_TEXT);

    // Instead of using just one statechart for the cell renderer and set its model here, we cache the statecharts
    // corresponding to each thread and their models. StateChart#setModel is currently expensive and will make StateChart#render
    // to be called. As this method can be called by Swing more often than our update cycle, we cache the models to avoid
    // recalculating the render states. This causes the rendering time to be substantially improved.
    int tid = value.getThreadId();
    StateChartModel<CpuProfilerStage.ThreadState> model = value.getModel();
    if (myStateCharts.containsKey(tid) && !model.equals(myStateCharts.get(tid).getModel())) {
      // The model associated to the tid has changed. That might have happened because the tid was recycled and
      // assigned to another thread. The current model needs to be unregistered.
      myUpdatableManager.unregister(myStateCharts.get(tid).getModel());
    }
    StateChart<CpuProfilerStage.ThreadState> stateChart = getOrCreateStateChart(tid, model);
    stateChart.setOpaque(true);
    // 1 is index of the selected color, 0 is of the non-selected
    // See more: {@link ProfilerColors#THREAD_STATES}
    myColors.get(stateChart).setColorIndex(isSelected ? 1 : 0);

    if (isSelected) {
      // Cell is selected. Update its background accordingly.
      panel.setBackground(ProfilerColors.THREAD_SELECTED_BACKGROUND);
      myLabel.setBackground(ProfilerColors.THREAD_SELECTED_BACKGROUND);
      myLabel.setForeground(ProfilerColors.SELECTED_THREAD_LABEL_TEXT);
      // As the state chart is opaque the selected background wouldn't be visible
      // if we didn't set the opaqueness to false if the cell is selected.
      stateChart.setOpaque(false);
    }
    else if (myHoveredIndex == index) {
      // Cell is hovered. Draw the hover overlay over it.
      JPanel overlay = new JPanel();
      overlay.setBackground(ProfilerColors.DEFAULT_HOVER_COLOR);
      panel.add(overlay, new TabularLayout.Constraint(0, 0, 2));
    }

    panel.add(myLabel, new TabularLayout.Constraint(0, 0));
    panel.add(stateChart, new TabularLayout.Constraint(0, 0, 2));
    return panel;
  }

  /**
   * Returns a {@link StateChart} corresponding to a given thread or create a new one if it doesn't exist.
   */
  private StateChart<CpuProfilerStage.ThreadState> getOrCreateStateChart(int tid, StateChartModel<CpuProfilerStage.ThreadState> model) {
    if (myStateCharts.containsKey(tid) && myStateCharts.get(tid).getModel().equals(model)) {
      // State chart is already saved on the map. Return it.
      return myStateCharts.get(tid).getChart();
    }
    // The state chart corresponding to the thread is not stored on the map. Create a new one.
    EnumColors<CpuProfilerStage.ThreadState> enumColors = ProfilerColors.THREAD_STATES.build();
    StateChart<CpuProfilerStage.ThreadState> stateChart = new StateChart<>(model, enumColors::getColor);
    StateChartData<CpuProfilerStage.ThreadState> data = new StateChartData<>(stateChart, model);
    stateChart.setHeightGap(0.40f);
    myStateCharts.put(tid, data);
    myColors.put(stateChart, enumColors);
    myUpdatableManager.register(model);
    return stateChart;
  }
}