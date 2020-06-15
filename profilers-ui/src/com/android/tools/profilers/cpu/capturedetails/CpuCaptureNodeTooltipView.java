/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipView;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.cpu.CaptureNode;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Tooltip view for displaying a node in a call chart or trace event chart.
 */
public class CpuCaptureNodeTooltipView extends TooltipView {
  @NotNull private final CaptureNode myCaptureNode;

  public CpuCaptureNodeTooltipView(@NotNull JComponent parent, @NotNull CpuCaptureNodeTooltip tooltip) {
    super(tooltip.getTimeline());
    myCaptureNode = tooltip.getCaptureNode();
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    long totalDuration = myCaptureNode.getDuration();
    long threadDuration = Math.min(totalDuration, Math.max(0, myCaptureNode.getEndThread() - myCaptureNode.getStartThread()));
    long idleDuration = totalDuration - threadDuration;

    JLabel nameLabel = new JLabel(myCaptureNode.getData().getFullName());
    JLabel runningLabel = new JLabel(String.format("Running: %s", TimeFormatter.getSingleUnitDurationString(threadDuration)));
    JLabel idleLabel = new JLabel(String.format("Idle: %s", TimeFormatter.getSingleUnitDurationString(idleDuration)));
    JLabel totalLabel = new JLabel(String.format("Total: %s", TimeFormatter.getSingleUnitDurationString(totalDuration)));
    JLabel contextHelpLabel = new JLabel("Click to inspect");
    contextHelpLabel.setForeground(ProfilerColors.TOOLTIP_LOW_CONTRAST);

    JPanel content = new JPanel(new TabularLayout("*").setVGap(12));
    content.add(nameLabel, new TabularLayout.Constraint(0, 0));
    content.add(runningLabel, new TabularLayout.Constraint(1, 0));
    content.add(idleLabel, new TabularLayout.Constraint(2, 0));
    content.add(totalLabel, new TabularLayout.Constraint(3, 0));
    content.add(AdtUiUtils.createHorizontalSeparator(), new TabularLayout.Constraint(4, 0));
    content.add(contextHelpLabel, new TabularLayout.Constraint(5, 0));
    return content;
  }
}
