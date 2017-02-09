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
package com.android.tools.profilers;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class MonitorTooltipView<M extends ProfilerMonitor> extends AspectObserver {

  @NotNull
  protected final M myMonitor;

  @NotNull
  private JLabel myLabel;

  protected MonitorTooltipView(@NotNull M monitor) {
    myMonitor = monitor;
    myLabel = new JLabel();
    myLabel.setFont(myLabel.getFont().deriveFont(ProfilerLayout.TOOLTIP_FONT_SIZE));
    monitor.getProfilers().getTimeline().getTooltipRange().addDependency(this).onChange(Range.Aspect.RANGE, this::timeChanged);
  }

  protected void timeChanged() {
    ProfilerTimeline timeline = myMonitor.getProfilers().getTimeline();
    Range range = timeline.getTooltipRange();
    if (!range.isEmpty()) {
      String time = TimeAxisFormatter.DEFAULT
        .getFormattedString(timeline.getDataRange().getLength(), range.getMin() - timeline.getDataRange().getMin(), true);
      myLabel.setText(myMonitor.getName() + " at " + time);
    }
  }

  public abstract Component createTooltip();

  final protected Component createComponent() {

    Component tooltip = createTooltip();

    JPanel panel = new JPanel(new BorderLayout());
    myLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
    panel.add(myLabel, BorderLayout.NORTH);
    panel.add(tooltip, BorderLayout.CENTER);
    panel.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    timeChanged();
    return panel;
  }

  public void dispose() {
    myMonitor.getProfilers().getTimeline().getTooltipRange().removeDependencies(this);
  }
}
