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
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerMonitorTooltipView;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.StageView;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class UserEventTooltipView extends ProfilerMonitorTooltipView<EventMonitor> {

  private static final int HOVER_OVER_WIDTH_PX = 16;

  @VisibleForTesting
  protected JLabel myStartTimeLabel;

  @VisibleForTesting
  protected JLabel myDurationLabel;

  @VisibleForTesting
  protected JLabel myContentLabel;

  protected JComponent myComponent;

  public UserEventTooltipView(StageView parent, @NotNull UserEventTooltip tooltip) {
    super(tooltip.getMonitor());

    myComponent = parent.getComponent();

    // Callback on the data range so the active event time gets updated properly.
    getMonitor().getProfilers().getTimeline().getDataRange().addDependency(this).onChange(Range.Aspect.RANGE, this::timeChanged);
  }

  @Override
  public void dispose() {
    super.dispose();
    getMonitor().getProfilers().getTimeline().getDataRange().removeDependencies(this);
  }

  private void timeChanged() {
    ProfilerTimeline timeline = getMonitor().getProfilers().getTimeline();
    Range dataRange = timeline.getDataRange();
    Range range = timeline.getTooltipRange();
    if (!range.isEmpty()) {
      showSimpleEventInfo(timeline, dataRange, range);
    }
  }

  private void clearTooltipInfo() {
    myContentLabel.setText("");
    myStartTimeLabel.setText("");
    myDurationLabel.setText("");
  }

  private void showSimpleEventInfo(ProfilerTimeline timeline, Range dataRange, Range range) {
    EventAction event = getEventAt(range.getMin());
    if (event == null) {
      clearTooltipInfo();
      return;
    }
    double endTime = event.getEndUs() == 0 ? dataRange.getMax() : event.getEndUs();
    setTimelineText(timeline.getDataRange(), event.getStartUs(), endTime);
    String label = "";
    if (event.getType() == UserEvent.KEYBOARD) {
      label = "Key Event - Press";
    }
    else if (event.getType() == UserEvent.TOUCH) {
      label = "Touch Event - Press";
    }
    else if (event.getType() == UserEvent.ROTATION) {
      label = "Rotation Event";
    }
    myContentLabel.setText(label);
  }

  private void setTimelineText(Range dataRange, double startTime, double endTime) {
    // Set the label to Start [StartTime] \n Duration: [Duration]
    String startTimeString = TimeFormatter.getSemiSimplifiedClockString((long)(startTime - dataRange.getMin()));
    String durationString = TimeFormatter.getSingleUnitDurationString((long)(endTime - startTime));
    myStartTimeLabel.setText(String.format("Start: %s", startTimeString));
    myDurationLabel.setText(String.format("Duration: %s", durationString));
  }

  // Find and event that overlaps with the specific time. If the event is a keyboard event we ignore the length and only return the event
  // for the time over the event icon. Otherwise we check the event time and return the first event that we encounter.
  @Nullable
  private EventAction getEventAt(double time) {
    double timePerPixel = getMonitor().getProfilers().getTimeline().getViewRange().getLength() / myComponent.getWidth();
    long hoverWidthAsTime = (long)timePerPixel * HOVER_OVER_WIDTH_PX;
    List<SeriesData<EventAction<UserEvent>>> userEventSeries = getMonitor().getUserEvents().getRangedSeries().getSeries();
    for (SeriesData<EventAction<UserEvent>> series : userEventSeries) {
      // If the series has a really small length it might be impossible to mouse over, so we add a range to make it
      // easier to mouse over.
      // If the event is a key event, because we don't draw a duration for key events we ignore the duration and use the
      // hover width to show the tooltip.
      if (series.value.getEndUs() - series.value.getStartUs() <= hoverWidthAsTime / 2 ||
          series.value.getType() == UserEvent.KEYBOARD) {
        if (series.value.getStartUs() - hoverWidthAsTime / 2 <= time && series.value.getStartUs() + hoverWidthAsTime / 2 >= time) {
          return series.value;
        }
      }
      else if (series.value.getStartUs() <= time && (series.value.getEndUs() > time || series.value.getEndUs() == 0)) {
        return series.value;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public JComponent createTooltip() {
    JPanel panel = new JPanel(new TabularLayout("*", "Fit-,5px,Fit-,5px,Fit-"));
    panel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    myContentLabel = new JLabel();
    myContentLabel.setForeground(Color.GRAY);
    myContentLabel.setFont(myFont);
    myStartTimeLabel = new JLabel();
    myStartTimeLabel.setForeground(Color.GRAY);
    myStartTimeLabel.setFont(myFont);
    myDurationLabel = new JLabel();
    myDurationLabel.setForeground(Color.GRAY);
    myDurationLabel.setFont(myFont);
    panel.add(myContentLabel, new TabularLayout.Constraint(0, 0));
    panel.add(myDurationLabel, new TabularLayout.Constraint(2, 0));
    panel.add(myStartTimeLabel, new TabularLayout.Constraint(4, 0));
    return panel;
  }
}
