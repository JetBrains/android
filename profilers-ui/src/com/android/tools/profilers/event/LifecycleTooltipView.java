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

import com.android.tools.adtui.ActivityComponent;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.event.LifecycleAction;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerMonitorTooltipView;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.StageView;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.util.ui.JBEmptyBorder;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import org.jetbrains.annotations.TestOnly;

public class LifecycleTooltipView extends ProfilerMonitorTooltipView<EventMonitor> {

  private static final int HOVER_OVER_WIDTH_PX = ActivityComponent.EVENT_LINE_WIDTH_PX + ActivityComponent.EVENT_LINE_GAP_WIDTH_PX * 2;

  @NotNull
  private final LifecycleTooltip myLifecycleTooltip;

  @NotNull
  private JLabel myActivityNameLabel;

  @NotNull
  private JLabel myDurationLabel;

  @NotNull
  private JPanel myFragmentsPanel;

  @NotNull
  private JComponent myComponent;

  public LifecycleTooltipView(@NotNull StageView parent, @NotNull LifecycleTooltip tooltip) {
    super(tooltip.getMonitor());
    myLifecycleTooltip = tooltip;
    myComponent = parent.getComponent();

    // Callback on the data range so the active event time gets updated properly.
    getMonitor().getProfilers().getTimeline().getDataRange().addDependency(this).onChange(Range.Aspect.RANGE, this::timeChanged);
    getMonitor().getProfilers().getTimeline().getTooltipRange().addDependency(this).onChange(Range.Aspect.RANGE, this::timeChanged);

    myActivityNameLabel = new JLabel();
    myDurationLabel = new JLabel();
    myFragmentsPanel = new JPanel(new VerticalFlowLayout(0, 0));
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
      showStackedEventInfo(timeline, dataRange, range.getMin());
    }
  }

  private void showStackedEventInfo(ProfilerTimeline timeline, Range dataRange, double tooltipX) {
    LifecycleAction activity = myLifecycleTooltip.getActivityAt(tooltipX);

    if (activity != null) {
      // Set the label to [Activity] [Length of time activity was active]
      double endTime = activity.getEndUs() == 0 ? dataRange.getMax() : activity.getEndUs();
      setTimelineText(timeline.getDataRange(), activity.getStartUs(), endTime);
      myActivityNameLabel.setText(activity.getName());
    } else {
      myDurationLabel.setText("");
      myActivityNameLabel.setText("");
    }

    if (getMonitor().getProfilers().getIdeServices().getFeatureConfig().isFragmentsEnabled()) {
      myFragmentsPanel.removeAll();
      double timePerPixel = getMonitor().getProfilers().getTimeline().getViewRange().getLength() / myComponent.getWidth();
      Range hoverRange = new Range(tooltipX - timePerPixel * HOVER_OVER_WIDTH_PX / 2, tooltipX + timePerPixel * HOVER_OVER_WIDTH_PX / 2);
      List<LifecycleAction> fragments = myLifecycleTooltip.getFragmentsAt(hoverRange);
      List<JLabel> labels = new ArrayList<>();
      fragments.forEach(fragment -> {
        String text = fragment.getName();
        boolean justAdded = fragment.getStartUs() > hoverRange.getMin() && fragment.getStartUs() <= hoverRange.getMax();
        boolean justRemoved = fragment.getEndUs() != 0 && fragment.getEndUs() > hoverRange.getMin() && fragment.getEndUs() <= hoverRange.getMax();
        if (justAdded) {
          text += " - Resumed";
        }
        else if (justRemoved) {
          text += " - Paused";
        }
        labels.add(new JLabel(text));
      });
      labels.sort((o1, o2) -> o1.getText().compareToIgnoreCase(o2.getText()));
      labels.forEach(label -> myFragmentsPanel.add(label));
    }
  }

  private void setTimelineText(Range dataRange, double startTime, double endTime) {
    // Set the label to [Activity StartTime] - [Activity EndTime]
    String startTimeString = TimeFormatter.getSemiSimplifiedClockString((long)(startTime - dataRange.getMin()));
    String endTimeString = TimeFormatter.getSemiSimplifiedClockString((long)(endTime - dataRange.getMin()));
    myDurationLabel.setText(String.format("%s - %s", startTimeString, endTimeString));
  }

  @TestOnly
  @NotNull
  public JLabel getActivityNameLabel() {
    return myActivityNameLabel;
  }

  @TestOnly
  @NotNull
  public JLabel getDurationLabel() {
    return myDurationLabel;
  }

  @TestOnly
  @NotNull
  public JPanel getFragmentsPanel() {
    return myFragmentsPanel;
  }

  @NotNull
  @Override
  public JComponent createTooltip() {
    JPanel panel = new JPanel(new VerticalFlowLayout(0, 0));
    panel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    panel.add(myActivityNameLabel);
    myDurationLabel.setForeground(Color.GRAY);
    myDurationLabel.setFont(myFont);
    panel.add(myDurationLabel);
    if (getMonitor().getProfilers().getIdeServices().getFeatureConfig().isFragmentsEnabled()) {
      myFragmentsPanel.setBorder(new JBEmptyBorder(8, 0, 0, 0));
      panel.add(myFragmentsPanel);
    }
    return panel;
  }
}
