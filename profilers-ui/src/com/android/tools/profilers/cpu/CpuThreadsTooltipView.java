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
import com.intellij.util.ui.JBUI;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

public class CpuThreadsTooltipView extends TooltipView {
  @NotNull private final CpuThreadsTooltip myTooltip;
  @NotNull private final JPanel myContent;
  @NotNull private final JLabel myLabel;
  @NotNull private final JLabel myState;
  @NotNull private final JLabel myDuration;
  @NotNull private final JPanel myUnavailableDetails;

  protected CpuThreadsTooltipView(@NotNull JComponent parent, @NotNull CpuThreadsTooltip tooltip) {
    super(tooltip.getTimeline());
    myTooltip = tooltip;
    // TODO(b/109661512): Move vgap scale into TabularLayout
    myContent = new JPanel(new TabularLayout("*").setVGap(JBUI.scale(8)));
    myLabel = createTooltipLabel();
    myState = createTooltipLabel();
    myDuration = createTooltipLabel();
    // TODO(b/109661512): Move vgap scale into TabularLayout
    myUnavailableDetails = new JPanel(new TabularLayout("*").setVGap(JBUI.scale(1)));
    tooltip.addDependency(this).onChange(CpuThreadsTooltip.Aspect.THREAD_STATE, this::stateChanged);
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

  private void stateChanged() {
    Range range = getTimeline().getTooltipRange();
    myContent.removeAll();
    if (range.isEmpty()) {
      addRow(myContent, myUnavailableDetails);
      return;
    }
    String title = myTooltip.getThreadName() != null ? myTooltip.getThreadName() : "CPU";
    myLabel.setText(String.format("Thread: %s", title));
    addRow(myContent, myLabel);

    if (myTooltip.getThreadState() != null) {
      myState.setText(myTooltip.getThreadState().getDisplayName());
      addRow(myContent, myState);

      if (myTooltip.getDurationUs() > 0) {
        myDuration.setText(TimeFormatter.getSingleUnitDurationString(myTooltip.getDurationUs()));
        addRow(myContent, myDuration);
      }

      if (!myTooltip.getThreadState().isCaptured()) {
        addRow(myContent, myUnavailableDetails);
      }
    }
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    //TODO (b/77491599): Remove workaround after tabular layout no longer defaults to min size:
    separator.setMinimumSize(separator.getPreferredSize());
    addRow(myUnavailableDetails, separator);
    JLabel unavailableLabel = new JLabel("Details Unavailable");
    unavailableLabel.setFont(TOOLTIP_BODY_FONT);
    unavailableLabel.setForeground(ProfilerColors.TOOLTIP_LOW_CONTRAST);
    addRow(myUnavailableDetails, unavailableLabel);
    addRow(myContent, myUnavailableDetails);
    return myContent;
  }
}
