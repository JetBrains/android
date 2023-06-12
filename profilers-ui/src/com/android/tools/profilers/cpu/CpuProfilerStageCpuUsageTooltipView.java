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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipView;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;
import com.intellij.util.ui.JBUI;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Tooltip view for {@link CpuCaptureStageCpuUsageTooltip}.
 */
class CpuProfilerStageCpuUsageTooltipView extends TooltipView {
  @NotNull private final CpuProfilerStageCpuUsageTooltip myTooltip;
  @NotNull private final JLabel mySelectionLabel;

  CpuProfilerStageCpuUsageTooltipView(@NotNull StageView view, @NotNull CpuProfilerStageCpuUsageTooltip tooltip) {
    super(view.getStage().getTimeline());
    myTooltip = tooltip;
    mySelectionLabel = new JLabel();
  }

  @Override
  protected void updateTooltip() {
    boolean canSelect = myTooltip.getRangeSelectionModel().canSelectRange(getTimeline().getTooltipRange());
    if (canSelect) {
      List<SeriesData<CpuTraceInfo>> traceSeries =
        myTooltip.getTraceDurations().getSeries().getSeriesForRange(getTimeline().getTooltipRange());
      if (traceSeries.isEmpty()) {
        return;
      }
      SeriesData<CpuTraceInfo> trace = traceSeries.get(0);
      String name =
        ProfilingTechnology.fromTraceConfiguration(trace.value.getTraceInfo().getConfiguration()).getName();
      mySelectionLabel.setText(name);
    }
    else {
      mySelectionLabel.setText("Selection Unavailable");
    }
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    JPanel panel = new JPanel(new TabularLayout("*", "*,Fit"));
    JPanel detailsPanel = new JPanel(new TabularLayout("*", "Fit,Fit-"));
    CpuProfilerStage.CpuStageLegends legends = myTooltip.getLegends();

    LegendComponent legend =
      new LegendComponent.Builder(legends).setVerticalPadding(0).setOrientation(LegendComponent.Orientation.VERTICAL).build();
    legend.setForeground(ProfilerColors.TOOLTIP_TEXT);
    legend.configure(legends.getCpuLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.CPU_USAGE_CAPTURED));
    legend.configure(legends.getOthersLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.CPU_OTHER_USAGE_CAPTURED));
    legend.configure(legends.getThreadsLegend(),
                     new LegendConfig(LegendConfig.IconType.DASHED_LINE, ProfilerColors.THREADS_COUNT_CAPTURED));
    panel.add(legend, new TabularLayout.Constraint(0, 0));
    // Build detail panel.
    JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    separator.setBorder(JBUI.Borders.empty(8, 0));
    detailsPanel.add(separator, new TabularLayout.Constraint(0, 0));
    mySelectionLabel.setFont(TOOLTIP_BODY_FONT);
    mySelectionLabel.setForeground(ProfilerColors.TOOLTIP_LOW_CONTRAST);
    detailsPanel.add(mySelectionLabel, new TabularLayout.Constraint(1, 0));
    panel.add(detailsPanel, new TabularLayout.Constraint(1, 0));
    return panel;
  }
}
