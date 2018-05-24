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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.ProfilerTooltipView;
import com.android.tools.profilers.cpu.atrace.CpuKernelTooltip;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.profilers.ProfilerFonts.TOOLTIP_BODY_FONT;

/**
 * This class manages the elements shown in the tooltip when the tooltip is visible.
 * The data is populated by listening to changes from the {@link CpuKernelTooltip.Aspect} as well as
 * listening to when the timeline range changes.
 */
public class CpuKernelTooltipView extends ProfilerTooltipView {
  @NotNull private final CpuKernelTooltip myTooltip;
  @NotNull private final ProfilerTimeline myTimeline;
  private final int myProcessId;
  @NotNull private final JPanel myContent;
  @NotNull private final JLabel myThread;
  @NotNull private final JLabel myProcess;
  @NotNull private final JLabel myCpu;
  @NotNull private final JPanel myUnavailableDetails;

  protected CpuKernelTooltipView(@NotNull CpuProfilerStageView view, @NotNull CpuKernelTooltip tooltip) {
    super(view.getTimeline());
    myProcessId = view.getStage().getStudioProfilers().getSession().getPid();
    myTimeline = view.getTimeline();
    myTooltip = tooltip;
    myContent = new JPanel();
    myThread = createTooltipLabel();
    myProcess = createTooltipLabel();
    myCpu = createTooltipLabel();
    myUnavailableDetails = new JPanel(new TabularLayout("*", "Fit,Fit"));
    tooltip.addDependency(this).onChange(CpuKernelTooltip.Aspect.CPU_KERNEL_THREAD_INFO, this::threadInfoChanged);
  }

  @Override
  public void dispose() {
    super.dispose();
    myTooltip.removeDependencies(this);
  }

  private void threadInfoChanged() {
    Range range = myTimeline.getTooltipRange();
    CpuThreadInfo threadInfo = myTooltip.getCpuThreadInfo();
    myContent.removeAll();
    if (range.isEmpty() || threadInfo == null) {
      return;
    }
    myThread.setText(String.format("Thread: %s", threadInfo.getName()));
    myContent.add(myThread, new TabularLayout.Constraint(0, 0));
    myProcess.setText(String.format("Process: %s", threadInfo.getProcessName()));
    myContent.add(myProcess, new TabularLayout.Constraint(2, 0));
    myCpu.setText(String.format("CPU: %d", myTooltip.getCpuId()));
    myContent.add(myCpu, new TabularLayout.Constraint(4, 0));
    if (myProcessId != threadInfo.getProcessId()) {
      myContent.add(myUnavailableDetails, new TabularLayout.Constraint(5, 0));
    }
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    myContent.setLayout(new TabularLayout("*", "Fit-,8px,Fit-,8px,Fit"));
    JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    separator.setBorder(JBUI.Borders.empty(8, 0));
    myUnavailableDetails.add(separator, new TabularLayout.Constraint(0, 0));
    JLabel unavailableLabel = new JLabel("Other (not selectable)");
    unavailableLabel.setFont(TOOLTIP_BODY_FONT);
    unavailableLabel.setForeground(ProfilerColors.TOOLTIP_LOW_CONTRAST);
    myUnavailableDetails.add(unavailableLabel, new TabularLayout.Constraint(1, 0));
    myContent.add(myUnavailableDetails, new TabularLayout.Constraint(0, 0));
    return myContent;
  }
}
