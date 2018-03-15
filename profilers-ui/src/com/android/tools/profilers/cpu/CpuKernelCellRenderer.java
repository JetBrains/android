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
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * This class is responsible for the layout of each row of the cpu process rendering.
 */
public class CpuKernelCellRenderer extends CpuCellRenderer<CpuKernelModel.CpuState, CpuThreadInfo> {
  /**
   * Maintain threads list so we can match selection state between the two lists.
   */
  private final JList<CpuThreadsModel.RangedCpuThread> myThreadsList;

  /**
   * Current process id so we can highlight user process threads as a different color.
   */
  private final int myProcessId;

  /**
   * Creates a new {@link CpuKernelCellRenderer}, this cell renderer creates a label, as well as a {@link StateChart} for each element
   * in the list. The {@link AtraceDataSeries} returned by {@link CpuKernelModel.CpuState} is used to populate the {@link StateChart}.
   * All items with a process id matching the process id passed in are highlighted one color, while everything else is a different color.
   *
   * @param processId        Id of the process to stand out as the user process.
   * @param updatableManager updatable manager to trigger model updates.
   * @param cpuStateList     list to be passed to the base cell renderer.
   * @param threadsList      list containing thread elements to keep selection in sync between the two list.
   */
  public CpuKernelCellRenderer(int processId,
                               @NotNull UpdatableManager updatableManager,
                               @NotNull JList<CpuKernelModel.CpuState> cpuStateList,
                               @NotNull JList<CpuThreadsModel.RangedCpuThread> threadsList) {
    super(cpuStateList, updatableManager);
    myProcessId = processId;
    myThreadsList = threadsList;
  }

  @Override
  @NotNull
  StateChart<CpuThreadInfo> getChartForModel(@NotNull CpuKernelModel.CpuState model) {
    return myStateCharts.get(model.getCpuId()).getChart();
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends CpuKernelModel.CpuState> list,
                                                CpuKernelModel.CpuState value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    JPanel panel = new JPanel(new TabularLayout("150px,*", "30px"));
    panel.setBackground(list.getBackground());
    myLabel.setText(String.format("CPU %d", value.getCpuId()));
    myLabel.setBackground(ProfilerColors.THREAD_LABEL_BACKGROUND);
    myLabel.setForeground(ProfilerColors.THREAD_LABEL_TEXT);

    // Instead of using just one statechart for the cell renderer and set its model here, we cache the statecharts
    // corresponding to each cpu. StateChart#setModel is currently expensive and will make StateChart#render
    // to be called. As this method can be called by Swing more often than our update cycle, we cache the models to avoid
    // recalculating the render states. This causes the rendering time to be substantially improved.
    int cpuId = value.getCpuId();
    StateChartModel<CpuThreadInfo> model = value.getModel();
    if (myStateCharts.containsKey(cpuId) && !model.equals(myStateCharts.get(cpuId).getModel())) {
      // The model associated to the tid has changed. That might have happened because the tid was recycled and
      // assigned to another thread. The current model needs to be unregistered.
      myUpdatableManager.unregister(myStateCharts.get(cpuId).getModel());
    }
    StateChart<CpuThreadInfo> stateChart = getOrCreateStateChart(cpuId, model);
    stateChart.setOpaque(true);

    if (myHoveredIndex == index) {
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
  private StateChart<CpuThreadInfo> getOrCreateStateChart(int cpuId, StateChartModel<CpuThreadInfo> model) {
    if (myStateCharts.containsKey(cpuId) && myStateCharts.get(cpuId).getModel().equals(model)) {
      // State chart is already saved on the map. Return it.
      return myStateCharts.get(cpuId).getChart();
    }
    // The state chart corresponding to the thread is not stored on the map. Create a new one.
    StateChart<CpuThreadInfo> stateChart = new StateChart<>(model, (threadInfo) -> {
      CpuThreadsModel.RangedCpuThread selectedThread = myThreadsList.getSelectedValue();
      Color color = ProfilerColors.DEFAULT_BACKGROUND;

      // If the thread data we are about to render is part of our process set the color to match the CPU chart.
      if (threadInfo.getProcessId() == myProcessId) {
        color = ProfilerColors.CPU_USAGE_CAPTURED;
      }
      // Otherwise if we have thread info that is not empty use the other processes CPU color.
      else if (threadInfo != CpuThreadInfo.NULL_THREAD) {
        color = ProfilerColors.CPU_OTHER_USAGE_CAPTURED;
      }

      // If we have a selected thread and its thread id does not match our thread info id fade it, making our selected thread elements pop.
      if (selectedThread != null && threadInfo != CpuThreadInfo.NULL_THREAD) {
        if (selectedThread.getThreadId() != threadInfo.getId()) {
          color = ColorUtil.withAlpha(color, 0.4);
        }
      }
      return color;
    }, (threadInfo) -> threadInfo.getName());
    stateChart.setRenderMode(StateChart.RenderMode.TEXT);
    CpuCellRenderer.StateChartData<CpuThreadInfo> data = new CpuCellRenderer.StateChartData<>(stateChart, model);
    stateChart.setHeightGap(0.10f);
    myStateCharts.put(cpuId, data);
    myUpdatableManager.register(model);
    return stateChart;
  }
}
