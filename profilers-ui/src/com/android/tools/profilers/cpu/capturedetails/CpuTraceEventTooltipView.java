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
package com.android.tools.profilers.cpu.capturedetails;

import static com.android.tools.adtui.TooltipView.TOOLTIP_BODY_FONT;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultDurationData;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.model.legend.EventLegend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.profilers.ChartTooltipViewBase;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.cpu.CaptureNode;
import java.awt.Color;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;

/**
 * Tooltip view for the Trace Event chart in Systrace.
 */
class CpuTraceEventTooltipView extends ChartTooltipViewBase<CaptureNode> {
  @NotNull private final TraceEventTooltipLegends myLegends;
  @NotNull private final Color myRunningColor;
  @NotNull private final Color myIdleColor;

  protected CpuTraceEventTooltipView(@NotNull HTreeChart<CaptureNode> chart,
                                     @NotNull JComponent tooltipRoot,
                                     @NotNull Color runningColor,
                                     @NotNull Color idleColor) {
    super(chart, tooltipRoot);
    myLegends = new TraceEventTooltipLegends();
    myRunningColor = runningColor;
    myIdleColor = idleColor;
  }

  @Override
  public void showTooltip(@NotNull CaptureNode node) {
    long totalDuration = node.getDuration();
    long threadDuration = Math.min(totalDuration, Math.max(0, node.getEndThread() - node.getStartThread()));
    long idleDuration = totalDuration - threadDuration;

    getTooltipContainer().removeAll();
    JLabel nameLabel = new JLabel(node.getData().getFullName());
    nameLabel.setFont(TOOLTIP_BODY_FONT);
    nameLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);

    myLegends.getRunningDurationLegend().setPickData(new DefaultDurationData(threadDuration));
    myLegends.getIdleDurationLegend().setPickData(new DefaultDurationData(idleDuration));
    LegendComponent legend = new LegendComponent.Builder(myLegends).setOrientation(LegendComponent.Orientation.VERTICAL).build();
    legend.setForeground(ProfilerColors.TOOLTIP_TEXT);
    legend
      .configure(myLegends.getRunningDurationLegend(), new LegendConfig(LegendConfig.IconType.BOX, myRunningColor));
    legend.configure(myLegends.getIdleDurationLegend(), new LegendConfig(LegendConfig.IconType.BOX, myIdleColor));

    JLabel totalLabel = new JLabel(String.format("Total: %s", TimeFormatter.getSingleUnitDurationString(totalDuration)));

    getTooltipContainer().add(nameLabel, new TabularLayout.Constraint(0, 0));
    getTooltipContainer().add(legend, new TabularLayout.Constraint(1, 0));
    getTooltipContainer().add(AdtUiUtils.createHorizontalSeparator(), new TabularLayout.Constraint(2, 0));
    getTooltipContainer().add(totalLabel, new TabularLayout.Constraint(3, 0));
  }

  private static class TraceEventTooltipLegends extends LegendComponentModel {
    @NotNull private final EventLegend<DefaultDurationData> myRunningDurationLegend;
    @NotNull private final EventLegend<DefaultDurationData> myIdleDurationLegend;

    TraceEventTooltipLegends() {
      super();
      myRunningDurationLegend = new EventLegend<>("Running", e -> TimeFormatter.getSingleUnitDurationString(e.getDurationUs()));
      myIdleDurationLegend = new EventLegend<>("Idle", e -> TimeFormatter.getSingleUnitDurationString(e.getDurationUs()));

      add(myRunningDurationLegend);
      add(myIdleDurationLegend);
    }

    @NotNull
    EventLegend<DefaultDurationData> getRunningDurationLegend() {
      return myRunningDurationLegend;
    }

    @NotNull
    EventLegend<DefaultDurationData> getIdleDurationLegend() {
      return myIdleDurationLegend;
    }
  }
}
