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
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.ProfilerTooltipView;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.profilers.ProfilerFonts.TOOLTIP_BODY_FONT;

public class CpuThreadsTooltipView extends ProfilerTooltipView {
  @NotNull private final CpuThreadsTooltip myTooltip;
  @NotNull private final ProfilerTimeline myTimeline;
  @NotNull private final JPanel myContent;
  @NotNull private final JLabel myLabel;
  @NotNull private final JLabel myState;
  @NotNull private final JLabel myDuration;
  @NotNull private final JPanel myUnavailableDetails;

  protected CpuThreadsTooltipView(@NotNull CpuProfilerStageView view, @NotNull CpuThreadsTooltip tooltip) {
    super(view.getTimeline());
    myTimeline = view.getTimeline();
    myTooltip = tooltip;
    myContent = new JPanel();
    myLabel = createTooltipLabel();
    myState = createTooltipLabel();
    myDuration = createTooltipLabel();
    myUnavailableDetails = new JPanel(new TabularLayout("*", "Fit,Fit"));
    tooltip.addDependency(this).onChange(CpuThreadsTooltip.Aspect.THREAD_STATE, this::stateChanged);
  }

  @Override
  public void dispose() {
    super.dispose();
    myTooltip.removeDependencies(this);
  }

  private void stateChanged() {
    Range range = myTimeline.getTooltipRange();
    myContent.removeAll();
    if (range.isEmpty()) {
      myContent.add(myUnavailableDetails, new TabularLayout.Constraint(0, 0));
      return;
    }
    String title = myTooltip.getThreadName() != null ? myTooltip.getThreadName() : "CPU";
    myLabel.setText(String.format("Thread: %s", title));
    myContent.add(myLabel, new TabularLayout.Constraint(0, 0));

    if (myTooltip.getThreadState() != null) {
      myState.setText(threadStateToString(myTooltip.getThreadState()));
      myContent.add(myState, new TabularLayout.Constraint(2, 0));

      if (myTooltip.getDurationUs() > 0) {
        myDuration.setText(TimeFormatter.getSingleUnitDurationString(myTooltip.getDurationUs()));
        myContent.add(myDuration, new TabularLayout.Constraint(4, 0));
      }

      if (!threadStateIsCaptured(myTooltip.getThreadState())) {
        myContent.add(myUnavailableDetails, new TabularLayout.Constraint(5, 0));
      }
    }
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    myContent.setLayout(new TabularLayout("*", "Fit-,8px,Fit-,8px,Fit"));
    JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    separator.setBorder(JBUI.Borders.empty(8, 0));
    myUnavailableDetails.add(separator, new TabularLayout.Constraint(0, 0));
    JLabel unavailableLabel = new JLabel("Details Unavailable");
    unavailableLabel.setFont(TOOLTIP_BODY_FONT);
    unavailableLabel.setForeground(ProfilerColors.TOOLTIP_LOW_CONTRAST);
    myUnavailableDetails.add(unavailableLabel, new TabularLayout.Constraint(1, 0));
    myContent.add(myUnavailableDetails, new TabularLayout.Constraint(0, 0));
    return myContent;
  }

  private static boolean threadStateIsCaptured(@NotNull CpuProfilerStage.ThreadState state) {
    switch (state) {
      case RUNNING_CAPTURED:
      case RUNNABLE_CAPTURED:
      case SLEEPING_CAPTURED:
      case DEAD_CAPTURED:
      case WAITING_CAPTURED:
      case WAITING_IO_CAPTURED:
      case HAS_ACTIVITY:
        return true;
      default:
        return false;
    }
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
      case HAS_ACTIVITY:
        return "Thread activity";
      case NO_ACTIVITY:
        return "No thread activity";
      default:
        return "Unknown";
    }
  }
}
