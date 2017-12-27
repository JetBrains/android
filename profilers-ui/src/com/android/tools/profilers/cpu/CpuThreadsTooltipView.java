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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.ProfilerTooltipView;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class CpuThreadsTooltipView extends ProfilerTooltipView {
  @NotNull private final CpuProfilerStage.ThreadsTooltip myTooltip;
  @NotNull private final JLabel myContent;
  @NotNull private final ProfilerTimeline myTimeline;
  private int myMaximumLabelWidth = 0;

  protected CpuThreadsTooltipView(@NotNull CpuProfilerStageView view, @NotNull CpuProfilerStage.ThreadsTooltip tooltip) {
    super(view.getTimeline(), "CPU");
    myLabel.setFont(AdtUiUtils.DEFAULT_FONT.deriveFont(ProfilerLayout.TOOLTIP_FONT_SIZE));
    myLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

    myTimeline = view.getTimeline();
    myTooltip = tooltip;
    myContent = new JLabel();
    myContent.setFont(AdtUiUtils.DEFAULT_FONT);
    myContent.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
    tooltip.addDependency(this).onChange(CpuProfilerStage.ThreadsTooltip.Aspect.THREAD_STATE, this::threadStateChanged);
  }

  @Override
  protected void timeChanged() {
    Range range = myTimeline.getTooltipRange();
    if (!range.isEmpty()) {
      String time = TimeAxisFormatter.DEFAULT
        .getFormattedString(myTimeline.getDataRange().getLength(), range.getMin() - myTimeline.getDataRange().getMin(), true);
      String title = myTooltip.getThreadName() != null ? myTooltip.getThreadName() : "CPU";
      myLabel.setText(String.format("<html>%s <span style='color:#%s'>%s</span></html",
                                    title,
                                    ColorUtil.toHex(ProfilerColors.CPU_THREADS_TOOLTIP_TIME_COLOR),
                                    time));
      myMaximumLabelWidth = Math.max(myMaximumLabelWidth, myLabel.getWidth());
      myLabel.setMinimumSize(new Dimension(myMaximumLabelWidth, 0));
    } else {
      myLabel.setText("");
    }
  }

  private void threadStateChanged() {
    String state = myTooltip.getThreadState() == null ? "" : threadStateToString(myTooltip.getThreadState());
    myContent.setText(state);
  }

  @Override
  protected Component createTooltip() {
    return myContent;
  }

  private String threadStateToString(@NotNull CpuProfilerStage.ThreadState state) {
    switch (state) {
      case RUNNING:
      case RUNNING_CAPTURED:
        return "Running";
      case SLEEPING:
      case SLEEPING_CAPTURED:
        return "Sleeping";
      case DEAD:
      case DEAD_CAPTURED:
        return "Dead";
      case WAITING:
      case WAITING_CAPTURED:
        return "Waiting";
      default:
        return "Unknown";
    }
  }
}
