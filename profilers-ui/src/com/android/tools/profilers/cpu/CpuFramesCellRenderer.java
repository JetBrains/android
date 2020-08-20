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
import com.android.tools.adtui.chart.statechart.StateChartTextConverter;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.FeatureConfig;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.cpu.systemtrace.CpuFramesModel;
import com.android.tools.profilers.cpu.systemtrace.SystemTraceFrame;
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

public class CpuFramesCellRenderer extends CpuCellRenderer<CpuFramesModel.FrameState, SystemTraceFrame> {
  private final boolean myDebugRenderingEnabled;

  /**
   * The current frame that the mouse is over.
   */
  private SystemTraceFrame myHighlightedFrame = SystemTraceFrame.EMPTY;

  private JList<CpuFramesModel.FrameState> myCpuStates;

  /**
   * Creates a new {@link CpuFramesCellRenderer}, this cell renderer creates a label, as well as a {@link StateChart} for each element
   * in the list. The {@link LazyDataSeries} returned by {@link CpuFramesModel.FrameState} is used to populate the {@link StateChart}.
   * All items with a process id matching the process id passed in are highlighted one color, while everything else is a different color.
   */
  public CpuFramesCellRenderer(@NotNull FeatureConfig featureConfig,
                               @NotNull JList<CpuFramesModel.FrameState> cpuStateList) {
    super(cpuStateList);
    myCpuStates = cpuStateList;
    myDebugRenderingEnabled = featureConfig.isPerformanceMonitoringEnabled();
  }

  @Override
  @NotNull
  StateChart<SystemTraceFrame> getChartForModel(@NotNull CpuFramesModel.FrameState model) {
    return myStateCharts.get(model.getModel().hashCode()).getChart();
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends CpuFramesModel.FrameState> list,
                                                CpuFramesModel.FrameState value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    JPanel panel = new JPanel(new TabularLayout("150px,*", "*"));
    panel.setBorder(ProfilerLayout.CPU_THREADS_BORDER);
    panel.setPreferredSize(JBDimension.create(panel.getPreferredSize()).withHeight(ProfilerLayout.CPU_STATE_LIST_ITEM_HEIGHT));
    panel.setBackground(list.getBackground());
    myLabel.setText(value.getThreadName());
    myLabel.setBackground(ProfilerColors.THREAD_LABEL_BACKGROUND);
    myLabel.setForeground(ProfilerColors.THREAD_LABEL_TEXT);
    // Offset the label to match the threads component.
    Border iconIndent = JBUI.Borders.emptyLeft(StudioIcons.LayoutEditor.Menu.MENU.getIconWidth() + myLabel.getIconTextGap());
    myLabel.setBorder(new CompoundBorder(iconIndent, ProfilerLayout.CPU_THREADS_RIGHT_BORDER));

    // Instead of using just one statechart for the cell renderer and set its model here, we cache the statecharts
    // corresponding to each cpu. StateChart#setModel is currently expensive and will make StateChart#render
    // to be called. As this method can be called by Swing more often than our update cycle, we cache the models to avoid
    // recalculating the render states. This causes the rendering time to be substantially improved.
    // TODO: Think about abstracting this to the base class as all renderers have this pattern.
    StateChartModel<SystemTraceFrame> model = value.getModel();
    StateChart<SystemTraceFrame> stateChart = getOrCreateStateChart(model);
    stateChart.setDrawDebugInfo(myDebugRenderingEnabled);
    stateChart.setOpaque(true);
    panel.add(myLabel, new TabularLayout.Constraint(0, 0));
    panel.add(stateChart, new TabularLayout.Constraint(0, 0, 2));
    return panel;
  }

  private void repaint() {
    myCpuStates.repaint();
  }

  void setHighlightedFrame(@NotNull SystemTraceFrame frame) {
    if (myHighlightedFrame != frame) {
      myHighlightedFrame = frame;
      // We force the whole list to be repainted as we need the associated frame to be repainted as well.
      repaint();
    }
  }

  /**
   * Checks if the frame should be highlighted or not.
   * A frame should be highlighted if the mouse is over it or over a frame that is associated with it.
   */
  private boolean isFrameHighlighted(@NotNull SystemTraceFrame frame) {
    if (myHighlightedFrame == SystemTraceFrame.EMPTY) {
      return false;
    }
    return frame == myHighlightedFrame || frame == myHighlightedFrame.getAssociatedFrame();
  }

  /**
   * Returns a {@link StateChart} corresponding to a given thread or create a new one if it doesn't exist.
   */
  private StateChart<SystemTraceFrame> getOrCreateStateChart(StateChartModel<SystemTraceFrame> model) {
    if (myStateCharts.containsKey(model.hashCode()) && myStateCharts.get(model.hashCode()).getModel().equals(model)) {
      // State chart is already saved on the map. Return it.
      return myStateCharts.get(model.hashCode()).getChart();
    }
    // The state chart corresponding to the thread is not stored on the map. Create a new one.
    StateChart<SystemTraceFrame> stateChart = new StateChart<>(model,
                                                               new StateChartColorProvider<SystemTraceFrame>() {
                                                            @NotNull
                                                            @Override
                                                            public Color getColor(boolean isMouseOver,
                                                                                  @NotNull SystemTraceFrame value) {
                                                              boolean isHighlighted = isFrameHighlighted(value);
                                                              switch (value.getTotalPerfClass()) {
                                                                case BAD:
                                                                  return isHighlighted
                                                                         ? ProfilerColors.SLOW_FRAME_COLOR_HIGHLIGHTED
                                                                         : ProfilerColors.SLOW_FRAME_COLOR;
                                                                case GOOD:
                                                                  return isHighlighted
                                                                         ? ProfilerColors.NORMAL_FRAME_COLOR_HIGHLIGHTED
                                                                         : ProfilerColors.NORMAL_FRAME_COLOR;
                                                                default:
                                                                  return ProfilerColors.DEFAULT_STAGE_BACKGROUND;
                                                              }
                                                            }
                                                          }, new StateChartTextConverter<SystemTraceFrame>() {
      @NotNull
      @Override
      public String convertToString(@NotNull SystemTraceFrame value) {
        // Show timings on bad frames.
        if (value.getTotalPerfClass() == SystemTraceFrame.PerfClass.BAD) {
          return TimeFormatter.getSingleUnitDurationString(value.getDurationUs());
        }
        return "";
      }
    });
    stateChart.setRenderMode(StateChart.RenderMode.TEXT);
    CpuCellRenderer.StateChartData<SystemTraceFrame> data = new CpuCellRenderer.StateChartData<>(stateChart, model);
    stateChart.setHeightGap(0.0f); // Default config sets this to 0.5f;
    myStateCharts.put(model.hashCode(), data);
    return stateChart;
  }
}
