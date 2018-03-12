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
import com.android.tools.profilers.cpu.atrace.CpuKernelTooltip;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * This class manages the elements shown in the tooltip when the tooltip is visible.
 * The data is populated by listening to changes from the {@link CpuKernelTooltip.Aspect} as well as
 * listening to when the timeline range changes.
 */
public class CpuKernelTooltipView extends ProfilerTooltipView {
  @NotNull private final CpuKernelTooltip myTooltip;
  @NotNull private final ProfilerTimeline myTimeline;
  @NotNull private final JLabel myContent;

  protected CpuKernelTooltipView(@NotNull CpuProfilerStageView view, @NotNull CpuKernelTooltip tooltip) {
    super(view.getTimeline(), "CPU");
    myTimeline = view.getTimeline();
    myTooltip = tooltip;
    myContent = new JLabel();
    myContent.setFont(AdtUiUtils.DEFAULT_FONT);
    myContent.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
    tooltip.addDependency(this).onChange(CpuKernelTooltip.Aspect.CPU_KERNEL_THREAD_INFO, this::timeChanged);
  }

  @Override
  public void dispose() {
    super.dispose();
    myTooltip.removeDependencies(this);
  }

  @Override
  protected void timeChanged() {
    Range range = myTimeline.getTooltipRange();
    if (range.isEmpty()) {
      myHeadingLabel.setText("");
      myContent.setText("");
      return;
    }

    CpuThreadInfo threadInfo = myTooltip.getCpuThreadInfo();
    if (threadInfo == null) {
      // If we don't have a kernel thread set default tooltip data.
      String time = TimeAxisFormatter.DEFAULT
        .getFormattedString(myTimeline.getDataRange().getLength(),
                            range.getMin() - myTimeline.getDataRange().getMin(),
                            true);
      myHeadingLabel.setText(String.format("<html>IDLE at <span style='color:#%s'>%s</span></html",
                                           ColorUtil.toHex(ProfilerColors.TOOLTIP_TIME_COLOR),
                                           time));
      myContent.setText("");
    }
    else {
      String time = TimeAxisFormatter.DEFAULT
        .getFormattedString(myTimeline.getDataRange().getLength(),
                            range.getMin() - myTimeline.getDataRange().getMin(),
                            true);
      myHeadingLabel.setText(String.format("<html>%s at <span style='color:#%s'>%s</span></html",
                                           threadInfo.getProcessName(),
                                           ColorUtil.toHex(ProfilerColors.TOOLTIP_TIME_COLOR),
                                           time));
      myContent.setText(threadInfo.getName());
    }
    updateMaximumLabelDimensions();
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    return myContent;
  }
}
