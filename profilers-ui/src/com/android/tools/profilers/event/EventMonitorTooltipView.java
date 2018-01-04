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
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.ActivityAction;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.StackedEventType;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerMonitorTooltipView;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.StudioMonitorStageView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class EventMonitorTooltipView extends ProfilerMonitorTooltipView<EventMonitor> {
  private JLabel myTimeRangeLabel;

  public EventMonitorTooltipView(StudioMonitorStageView parent, @NotNull EventMonitorTooltip tooltip) {
    super(tooltip.getMonitor());
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

    // Get the data range to determine how long an activity is alive.
    Range dataRange = timeline.getDataRange();

    // Get the tooltip range to figure out what activity is under our cursor.
    Range range = timeline.getTooltipRange();

    if (!range.isEmpty()) {
      ActivityAction activity = getActivityAt(range.getMin());
      if (activity != null) {
        // Set the label to [Activity] [Length of time activity was active]
        double endTime = activity.getEndUs() == 0 ? dataRange.getMax() : activity.getEndUs();
        String time = TimeAxisFormatter.DEFAULT
          .getFormattedString(dataRange.getLength(), endTime - activity.getStartUs(), true);
        setTimelineText(timeline.getDataRange(), activity, endTime);
        myHeadingLabel.setText(activity.getData() + " " + time);
        updateMaximumLabelDimensions();
      }
      else {
        super.timeChanged();
        myTimeRangeLabel.setText("");
      }
    }
    else {
      myHeadingLabel.setText("");
    }
  }

  private void setTimelineText(Range dataRange, ActivityAction activity, double endTime) {
    // Set the label to [Activity StartTime] - [Activity EndTime]
    String startTime = TimeAxisFormatter.DEFAULT
      .getFormattedString(dataRange.getLength(), activity.getStartUs() - dataRange.getMin(), true);
    String endTimeString = TimeAxisFormatter.DEFAULT
      .getFormattedString(dataRange.getLength(), endTime - dataRange.getMin(), true);
    myTimeRangeLabel.setText(String.format("%s - %s", startTime, endTimeString));
  }

  // Find the activity that overlaps a specific time, the activities are sorted so if there are multiple the first
  // one we encounter will be the one that is presented in the UI.
  @Nullable
  private ActivityAction getActivityAt(double time) {
    List<SeriesData<EventAction<StackedEventType>>> activitySeries =
      getMonitor().getActivityEvents().getRangedSeries().getSeries();
    for (SeriesData<EventAction<StackedEventType>> series : activitySeries) {
      if (series.value.getStartUs() <= time && (series.value.getEndUs() > time || series.value.getEndUs() == 0)) {
        return (ActivityAction)series.value;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public JComponent createTooltip() {
    JPanel panel = new JPanel(new TabularLayout("*"));
    panel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    myTimeRangeLabel = new JLabel();
    myTimeRangeLabel.setForeground(Color.GRAY);
    myTimeRangeLabel.setFont(myFont);
    panel.add(myTimeRangeLabel, new TabularLayout.Constraint(0, 0));
    // TODO: Add Fragment information for O devices.

    return panel;
  }
}
