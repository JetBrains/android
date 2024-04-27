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
import com.android.tools.adtui.chart.statechart.StateChartColorProvider;
import com.android.tools.adtui.common.ColoredIconGenerator;
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayout;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import org.jetbrains.annotations.NotNull;

public class ThreadCellRenderer extends CpuCellRenderer<CpuThreadsModel.RangedCpuThread, ThreadState> {
  /**
   * Maps a {@link StateChart} to a {@link EnumColors} helper class to return the proper color object for the {@link StateChart}
   */
  @NotNull
  private final Map<StateChart<ThreadState>, EnumColors<ThreadState>> myColors;

  public ThreadCellRenderer(JList<CpuThreadsModel.RangedCpuThread> list, UpdatableManager updatableManager) {
    super(list);
    myColors = new HashMap<>();
  }

  @Override
  @NotNull
  StateChart<ThreadState> getChartForModel(@NotNull CpuThreadsModel.RangedCpuThread model) {
    return myStateCharts.get(model.getId()).getChart();
  }

  @Override
  public Component getListCellRendererComponent(JList list,
                                                CpuThreadsModel.RangedCpuThread value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    Icon reorderIcon = StudioIcons.Common.REORDER;
    JPanel panel = new JPanel(new TabularLayout("150px,*", "*"));
    panel.setBorder(ProfilerLayout.CPU_THREADS_BORDER);
    panel.setPreferredSize(JBDimension.create(panel.getPreferredSize()).withHeight(ProfilerLayout.CPU_STATE_LIST_ITEM_HEIGHT));
    panel.setBackground(list.getBackground());

    myLabel.setText(value.getName());
    myLabel.setIcon(null);
    Border iconIndent = JBUI.Borders.emptyLeft(reorderIcon.getIconWidth() + myLabel.getIconTextGap());
    myLabel.setBorder(new CompoundBorder(iconIndent, ProfilerLayout.CPU_THREADS_RIGHT_BORDER));
    myLabel.setBackground(ProfilerColors.THREAD_LABEL_BACKGROUND);
    myLabel.setForeground(ProfilerColors.THREAD_LABEL_TEXT);

    // Instead of using just one statechart for the cell renderer and set its model here, we cache the statecharts
    // corresponding to each thread and their models. StateChart#setModel is currently expensive and will make StateChart#render
    // to be called. As this method can be called by Swing more often than our update cycle, we cache the models to avoid
    // recalculating the render states. This causes the rendering time to be substantially improved.
    int tid = value.getThreadId();
    StateChartModel<ThreadState> model = value.getModel();
    StateChart<ThreadState> stateChart = getOrCreateStateChart(tid, model);
    stateChart.setOpaque(true);
    stateChart.setBorder(null);
    // 1 is index of the selected color, 0 is of the non-selected
    // See more: {@link ProfilerColors#THREAD_STATES}
    // For now we always use the non-selected color.
    myColors.get(stateChart).setColorIndex(0);

    if (isSelected) {
      // Cell is selected. Update its background accordingly.
      myLabel.setBackground(ProfilerColors.CPU_THREAD_SELECTED_BACKGROUND);
      myLabel.setForeground(ProfilerColors.SELECTED_THREAD_LABEL_TEXT);
      stateChart.setBorder(JBUI.Borders.customLine(ProfilerColors.CPU_THREAD_SELECTED_BACKGROUND, 2));
    }
    if (myHoveredIndex == index) {
      // Draw drag icon next to label
      myLabel.setBorder(ProfilerLayout.CPU_THREADS_RIGHT_BORDER);
      myLabel.setIcon(isSelected && !ExperimentalUI.isNewUI() ? ColoredIconGenerator.INSTANCE.generateWhiteIcon(reorderIcon) : reorderIcon);
    }

    panel.add(myLabel, new TabularLayout.Constraint(0, 0));
    panel.add(stateChart, new TabularLayout.Constraint(0, 0, 2));
    return panel;
  }

  /**
   * Returns a {@link StateChart} corresponding to a given thread or create a new one if it doesn't exist.
   */
  private StateChart<ThreadState> getOrCreateStateChart(int tid, StateChartModel<ThreadState> model) {
    if (myStateCharts.containsKey(tid) && myStateCharts.get(tid).getModel().equals(model)) {
      // State chart is already saved on the map. Return it.
      return myStateCharts.get(tid).getChart();
    }
    // The state chart corresponding to the thread is not stored on the map. Create a new one.
    EnumColors<ThreadState> enumColors = ProfilerColors.THREAD_STATES.build();
    StateChart<ThreadState> stateChart =
      new StateChart<>(model,
                       new StateChartColorProvider<ThreadState>() {
                         @NotNull
                         @Override
                         public Color getColor(boolean isMouseOver, @NotNull ThreadState value) {
                           enumColors.setColorIndex(isMouseOver ? 1 : 0);
                           return enumColors.getColor(value);
                         }
                       });
    StateChartData<ThreadState> data = new StateChartData<>(stateChart, model);
    stateChart.setHeightGap(0.0f); // Default config sets this to 0.5f;
    myStateCharts.put(tid, data);
    myColors.put(stateChart, enumColors);
    return stateChart;
  }
}