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
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.profilers.FeatureConfig;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.cpu.systemtrace.CpuKernelModel;
import com.android.tools.profilers.cpu.systemtrace.CpuThreadSliceInfo;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import org.jetbrains.annotations.NotNull;

/**
 * This class is responsible for the layout of each row of the cpu process rendering.
 */
public class CpuKernelCellRenderer extends CpuCellRenderer<CpuKernelModel.CpuState, CpuThreadSliceInfo> {
  @NotNull private final CpuProfilerStage myStage;

  private final boolean myDebugRenderingEnabled;

  /**
   * Current process id so we can highlight user process threads as a different color.
   */
  @VisibleForTesting final int myProcessId;

  /**
   * Creates a new {@link CpuKernelCellRenderer}, this cell renderer creates a label, as well as a {@link StateChart} for each element
   * in the list. The {@link LazyDataSeries} returned by {@link CpuKernelModel.CpuState} is used to populate the {@link StateChart}.
   * All items with a process id matching the process id passed in are highlighted one color, while everything else is a different color.
   *
   * @param processId    Id of the process to stand out as the user process.
   * @param cpuStateList list to be passed to the base cell renderer.
   */
  public CpuKernelCellRenderer(@NotNull CpuProfilerStage stage,
                               @NotNull FeatureConfig featureConfig,
                               int processId,
                               @NotNull JList<CpuKernelModel.CpuState> cpuStateList) {
    super(cpuStateList);
    myStage = stage;
    myProcessId = processId;
    myDebugRenderingEnabled = featureConfig.isPerformanceMonitoringEnabled();
  }

  @Override
  @NotNull
  StateChart<CpuThreadSliceInfo> getChartForModel(@NotNull CpuKernelModel.CpuState model) {
    return myStateCharts.get(model.getCpuId()).getChart();
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends CpuKernelModel.CpuState> list,
                                                CpuKernelModel.CpuState value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    JPanel panel = new JPanel(new TabularLayout("150px,*", "*"));
    panel.setBorder(ProfilerLayout.CPU_THREADS_BORDER);
    panel.setPreferredSize(JBDimension.create(panel.getPreferredSize()).withHeight(ProfilerLayout.CPU_STATE_LIST_ITEM_HEIGHT));
    panel.setBackground(list.getBackground());
    myLabel.setText(String.format("CPU %d", value.getCpuId()));
    myLabel.setBackground(ProfilerColors.THREAD_LABEL_BACKGROUND);
    myLabel.setForeground(ProfilerColors.THREAD_LABEL_TEXT);
    // Offset the label to match the threads component.
    Border iconIndent = JBUI.Borders.emptyLeft(StudioIcons.LayoutEditor.Menu.MENU.getIconWidth() + myLabel.getIconTextGap());
    myLabel.setBorder(new CompoundBorder(iconIndent, ProfilerLayout.CPU_THREADS_RIGHT_BORDER));

    // Instead of using just one statechart for the cell renderer and set its model here, we cache the statecharts
    // corresponding to each cpu. StateChart#setModel is currently expensive and will make StateChart#render
    // to be called. As this method can be called by Swing more often than our update cycle, we cache the models to avoid
    // recalculating the render states. This causes the rendering time to be substantially improved.
    int cpuId = value.getCpuId();
    StateChartModel<CpuThreadSliceInfo> model = value.getModel();
    StateChart<CpuThreadSliceInfo> stateChart = getOrCreateStateChart(cpuId, model);
    stateChart.setDrawDebugInfo(myDebugRenderingEnabled);
    stateChart.setOpaque(true);
    panel.add(myLabel, new TabularLayout.Constraint(0, 0));
    panel.add(stateChart, new TabularLayout.Constraint(0, 0, 2));
    return panel;
  }

  /**
   * Returns a {@link StateChart} corresponding to a given thread or create a new one if it doesn't exist.
   */
  private StateChart<CpuThreadSliceInfo> getOrCreateStateChart(int cpuId, StateChartModel<CpuThreadSliceInfo> model) {
    if (myStateCharts.containsKey(cpuId) && myStateCharts.get(cpuId).getModel().equals(model)) {
      // State chart is already saved on the map. Return it.
      return myStateCharts.get(cpuId).getChart();
    }
    // The state chart corresponding to the thread is not stored on the map. Create a new one.
    StateChart<CpuThreadSliceInfo> stateChart = new StateChart<>(model, new StateChartColorProvider<CpuThreadSliceInfo>() {
      @NotNull
      @Override
      public Color getColor(boolean isMouseOver, @NotNull CpuThreadSliceInfo value) {
        // On the null thread return the background color.
        if (value == CpuThreadSliceInfo.NULL_THREAD) {
          return ProfilerColors.DEFAULT_BACKGROUND;
        }
        // Return other process colors.
        if (value.getProcessId() != myProcessId) {
          return isMouseOver ? ProfilerColors.CPU_KERNEL_OTHER_HOVER : ProfilerColors.CPU_KERNEL_OTHER;
        }
        // Test and return our process color.
        boolean isSelected = myStage.getSelectedThread() == value.getId();
        if (isMouseOver) {
          return ProfilerColors.CPU_KERNEL_APP_HOVER;
        }
        else if (isSelected) {
          return ProfilerColors.CPU_KERNEL_APP_SELECTED;
        }
        else {
          return ProfilerColors.CPU_KERNEL_APP;
        }
      }

      @NotNull
      @Override
      public Color getFontColor(boolean isMouseOver, @NotNull CpuThreadSliceInfo value) {
        // On the null thread return the background color.
        if (value == CpuThreadSliceInfo.NULL_THREAD) {
          return AdtUiUtils.DEFAULT_FONT_COLOR;
        }
        // Return other process color.
        if (value.getProcessId() != myProcessId) {
          return isMouseOver ? ProfilerColors.CPU_KERNEL_OTHER_TEXT_HOVER : ProfilerColors.CPU_KERNEL_OTHER_TEXT;
        }
        // Test and return our process color.
        boolean isSelected = myStage.getSelectedThread() == value.getId();
        if (isMouseOver) {
          return ProfilerColors.CPU_KERNEL_APP_TEXT_HOVER;
        }
        else if (isSelected) {
          return ProfilerColors.CPU_KERNEL_APP_TEXT_SELECTED;
        }
        else {
          return ProfilerColors.CPU_KERNEL_APP_TEXT;
        }
      }
    }, (threadInfo) -> threadInfo.getName());
    stateChart.setRenderMode(StateChart.RenderMode.TEXT);
    CpuCellRenderer.StateChartData<CpuThreadSliceInfo> data = new CpuCellRenderer.StateChartData<>(stateChart, model);
    stateChart.setHeightGap(0.0f); // Default config sets this to 0.5f;
    myStateCharts.put(cpuId, data);
    return stateChart;
  }
}
