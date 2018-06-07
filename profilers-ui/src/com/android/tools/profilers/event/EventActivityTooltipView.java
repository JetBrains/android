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
package com.android.tools.profilers.event;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.event.ActivityAction;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerMonitorTooltipView;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.StageView;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class EventActivityTooltipView extends ProfilerMonitorTooltipView<EventMonitor> {

  @VisibleForTesting
  protected JLabel myDurationLabel;

  @NotNull
  private final EventActivityTooltip myActivityTooltip;


  public EventActivityTooltipView(StageView parent, @NotNull EventActivityTooltip tooltip) {
    super(tooltip.getMonitor());
    myActivityTooltip = tooltip;

    // Callback on the data range so the active event time gets updated properly.
    getMonitor().getProfilers().getTimeline().getDataRange().addDependency(this).onChange(Range.Aspect.RANGE, this::timeChanged);
  }

  @Override
  public void dispose() {
    super.dispose();
    getMonitor().getProfilers().getTimeline().getDataRange().removeDependencies(this);
  }

  @Override
  protected void timeChanged() {
    ProfilerTimeline timeline = getMonitor().getProfilers().getTimeline();
    Range dataRange = timeline.getDataRange();
    Range range = timeline.getTooltipRange();

    if (!range.isEmpty()) {
      showStackedEventInfo(timeline, dataRange, range);
    }
    else {
      myHeadingLabel.setText("");
    }
    updateMaximumLabelDimensions();
  }

  private void clearTooltipInfo() {
    super.timeChanged();
    myDurationLabel.setText("");
  }

  private void showStackedEventInfo(ProfilerTimeline timeline, Range dataRange, Range range) {
    ActivityAction activity = myActivityTooltip.getActivityAt(range.getMin());
    if (activity != null) {
      // Set the label to [Activity] [Length of time activity was active]
      double endTime = activity.getEndUs() == 0 ? dataRange.getMax() : activity.getEndUs();
      setTimelineText(timeline.getDataRange(), activity.getStartUs(), endTime);
      myHeadingLabel.setText(activity.getData());
    }
    else {
      clearTooltipInfo();
    }
  }


  private void setTimelineText(Range dataRange, double startTime, double endTime) {
    // Set the label to [Activity StartTime] - [Activity EndTime]
    String startTimeString = TimeAxisFormatter.DEFAULT
      .getFormattedString(dataRange.getLength(), startTime - dataRange.getMin(), true);
    String endTimeString = TimeAxisFormatter.DEFAULT
      .getFormattedString(dataRange.getLength(), endTime, true);
    myDurationLabel.setText(String.format("%s - %s", startTimeString, endTimeString));
  }

  @NotNull
  @Override
  public JComponent createTooltip() {
    JPanel panel = new JPanel(new TabularLayout("*"));
    panel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    myDurationLabel = new JLabel();
    myDurationLabel.setForeground(Color.GRAY);
    myDurationLabel.setFont(myFont);
    panel.add(myDurationLabel, new TabularLayout.Constraint(0, 0));
    return panel;
  }
}
