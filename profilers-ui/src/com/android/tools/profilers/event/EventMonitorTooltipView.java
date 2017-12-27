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
import com.android.tools.profilers.ProfilerTooltipView;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.ActivityAction;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.StackedEventType;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.StudioMonitorStageView;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.List;

public class EventMonitorTooltipView extends ProfilerTooltipView {

  private JLabel myTimeRange;
  private final EventMonitor myMonitor;

  public EventMonitorTooltipView(StudioMonitorStageView parent, EventMonitor monitor) {
    super(monitor.getTimeline(), monitor.getName());
    myMonitor = monitor;
    // Callback on the data range so the active event time gets updated properly.
    monitor.getProfilers().getTimeline().getDataRange().addDependency(this).onChange(Range.Aspect.RANGE, this::dataRangeChanged);
  }

  @Override
  protected void timeChanged() {
    updateTimelineText();
  }

  private void updateTimelineText() {
    ProfilerTimeline timeline = myMonitor.getProfilers().getTimeline();

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
        myLabel.setText(activity.getData() + " " + time);
      } else {
        super.timeChanged();
        myTimeRange.setText("");
      }
    } else {
      myLabel.setText("");
    }

  }

  // Handle logic for displaying time and fragment information in tooltip.
  protected void dataRangeChanged() {
    updateTimelineText();
  }

  private void setTimelineText(Range dataRange, ActivityAction activity, double endTime) {
    // Set the label to [Activity StartTime] - [Activity EndTime]
    String startTime = TimeAxisFormatter.DEFAULT
      .getFormattedString(dataRange.getLength(), activity.getStartUs() - dataRange.getMin(), true);
    String endTimeString = TimeAxisFormatter.DEFAULT
      .getFormattedString(dataRange.getLength(), endTime - dataRange.getMin(), true);
    myTimeRange.setText(String.format("%s - %s", startTime, endTimeString));
  }

  // Find the activity that overlaps a specific time, the activities are sorted so if there are multiple the first
  // one we encounter will be the one that is presented in the UI.
  private ActivityAction getActivityAt(double time) {
    List<SeriesData<EventAction<StackedEventType>>> activitySeries = myMonitor.getActivityEvents().getRangedSeries().getSeries();
    for (SeriesData<EventAction<StackedEventType>> series : activitySeries) {
      if (series.value.getStartUs() <= time && (series.value.getEndUs() > time || series.value.getEndUs() == 0)) {
        return (ActivityAction)series.value;
      }
    }
    return null;
  }

  @Override
  public Component createTooltip() {
    TabularLayout grid = new TabularLayout("*", "*");
    myTimeRange = new JLabel();
    myTimeRange.setForeground(Color.GRAY);
    JPanel panel = new JPanel(new BorderLayout());
    myLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    myTimeRange.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
    panel.add(myTimeRange, BorderLayout.NORTH);
    // TODO: Add Fragment information for O devices.
    panel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    return panel;
  }
}
