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
import com.android.tools.adtui.TooltipView;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.cpu.systemtrace.CpuKernelTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuThreadSliceInfo;
import com.intellij.util.ui.JBUI;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

/**
 * This class manages the elements shown in the tooltip when the tooltip is visible.
 * The data is populated by listening to changes from the {@link CpuKernelTooltip.Aspect} as well as
 * listening to when the timeline range changes.
 */
public class CpuKernelTooltipView extends TooltipView {
  @NotNull private final CpuKernelTooltip myTooltip;
  @NotNull private final JPanel myContent;
  @NotNull private final JLabel myThread;
  @NotNull private final JLabel myProcess;
  @NotNull private final JLabel myDuration;
  @NotNull private final JLabel myCpu;
  @NotNull private final JPanel myUnavailableDetails;

  protected CpuKernelTooltipView(@NotNull JComponent parent, @NotNull CpuKernelTooltip tooltip) {
    super(tooltip.getTimeline());
    myTooltip = tooltip;
    // TODO(b/109661512): Move vgap scale into TabularLayout
    myContent = new JPanel(new TabularLayout("*").setVGap(JBUI.scale(8)));
    myThread = createTooltipLabel();
    myProcess = createTooltipLabel();
    myDuration = createTooltipLabel();
    myCpu = createTooltipLabel();
    // TODO(b/109661512): Move vgap scale into TabularLayout
    myUnavailableDetails = new JPanel(new TabularLayout("*").setVGap(JBUI.scale(1)));
    tooltip.addDependency(this).onChange(CpuKernelTooltip.Aspect.CPU_KERNEL_THREAD_SLICE_INFO, this::threadSliceInfoChanged);
  }

  @Override
  public void dispose() {
    super.dispose();
    myTooltip.removeDependencies(this);
  }

  private static void addRow(JPanel parent, JComponent c) {
    int nextRow = parent.getComponentCount();
    parent.add(c, new TabularLayout.Constraint(nextRow, 0));
  }

  private void threadSliceInfoChanged() {
    Range range = getTimeline().getTooltipRange();
    CpuThreadSliceInfo threadSlice = myTooltip.getCpuThreadSliceInfo();
    myContent.removeAll();
    if (range.isEmpty() || threadSlice == null) {
      return;
    }
    myThread.setText(String.format("Thread: %s", threadSlice.getName()));
    addRow(myContent, myThread);
    myProcess.setText(String.format("Process: %s", threadSlice.getProcessName()));
    addRow(myContent, myProcess);
    myDuration.setText(String.format("Duration: %s", TimeFormatter.getSingleUnitDurationString(threadSlice.getDurationUs())));
    addRow(myContent, myDuration);
    myCpu.setText(String.format("CPU: %d", myTooltip.getCpuId()));
    addRow(myContent, myCpu);
    if (myTooltip.getProcessId() != threadSlice.getProcessId()) {
      addRow(myContent, myUnavailableDetails);
    }
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    //TODO (b/77491599): Remove workaround after tabular layout no longer defaults to min size:
    separator.setMinimumSize(separator.getPreferredSize());
    addRow(myUnavailableDetails, separator);
    JLabel unavailableLabel = new JLabel("Other (not selectable)");
    unavailableLabel.setFont(TOOLTIP_BODY_FONT);
    unavailableLabel.setForeground(ProfilerColors.TOOLTIP_LOW_CONTRAST);
    addRow(myUnavailableDetails, unavailableLabel);
    addRow(myContent, myUnavailableDetails);
    return myContent;
  }
}
