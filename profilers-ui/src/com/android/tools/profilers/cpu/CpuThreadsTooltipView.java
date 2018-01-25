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
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.ProfilerTooltipView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CpuThreadsTooltipView extends ProfilerTooltipView {
  @NotNull private final CpuThreadsTooltip myTooltip;
  @NotNull private final ProfilerTimeline myTimeline;

  @VisibleForTesting
  @NotNull protected final JLabel myContent;

  protected CpuThreadsTooltipView(@NotNull CpuProfilerStageView view, @NotNull CpuThreadsTooltip tooltip) {
    super(view.getTimeline(), "CPU");
    myTimeline = view.getTimeline();
    myTooltip = tooltip;
    myContent = new JLabel();
    myContent.setFont(AdtUiUtils.DEFAULT_FONT);
    myContent.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
    tooltip.addDependency(this).onChange(CpuThreadsTooltip.Aspect.THREAD_STATE, this::timeChanged);
  }

  @Override
  public void dispose() {
    super.dispose();
    myTooltip.removeDependencies(this);
  }

  @Override
  protected void timeChanged() {
    Range range = myTimeline.getTooltipRange();
    if (!range.isEmpty()) {
      String time = TimeAxisFormatter.DEFAULT
        .getFormattedString(myTimeline.getDataRange().getLength(), range.getMin() - myTimeline.getDataRange().getMin(), true);
      String title = myTooltip.getThreadName() != null ? myTooltip.getThreadName() : "CPU";
      myHeadingLabel.setText(String.format("<html>%s <span style='color:#%s'>%s</span></html",
                                           title,
                                           ColorUtil.toHex(ProfilerColors.TOOLTIP_TIME_COLOR),
                                           time));
      myContent.setText(myTooltip.getThreadState() == null ? "" : threadStateToString(myTooltip.getThreadState()));
      updateMaximumLabelDimensions();
    } else {
      myHeadingLabel.setText("");
      myContent.setText("");
    }
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    return myContent;
  }

  private static String threadStateToString(@NotNull CpuProfilerStage.ThreadState state) {
    switch (state) {
      case RUNNING:
      case RUNNING_CAPTURED:
        return "Running";
      case RUNNABLE_CAPTURED:
        return "Runnable";
      case SLEEPING:
      case SLEEPING_CAPTURED:
        return "Sleeping";
      case DEAD:
      case DEAD_CAPTURED:
        return "Dead";
      case WAITING:
      case WAITING_CAPTURED:
        return "Waiting";
      case WAITING_IO_CAPTURED:
        return "Waiting on IO";
      default:
        return "Unknown";
    }
  }
}
