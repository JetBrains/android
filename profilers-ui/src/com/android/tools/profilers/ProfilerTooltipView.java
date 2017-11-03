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

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.*;


public abstract class ProfilerTooltipView extends AspectObserver {
  @NotNull
  private final ProfilerTimeline myTimeline;

  @NotNull
  protected final String myTitle;

  @NotNull
  protected final JLabel myLabel;

  private int myMaximumLabelWidth;

  protected ProfilerTooltipView(@NotNull ProfilerTimeline timeline, @NotNull String title) {
    myTimeline = timeline;
    myTitle = title;

    myLabel = new JLabel();
    myLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    myLabel.setFont(myLabel.getFont().deriveFont(ProfilerLayout.TOOLTIP_FONT_SIZE));
    myLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 12, 0));
    timeline.getTooltipRange().addDependency(this).onChange(Range.Aspect.RANGE, this::timeChanged);
    myMaximumLabelWidth = 0;
  }

  protected void timeChanged() {
    Range range = myTimeline.getTooltipRange();
    if (!range.isEmpty()) {
      String time = TimeAxisFormatter.DEFAULT
        .getFormattedString(myTimeline.getDataRange().getLength(), range.getMin() - myTimeline.getDataRange().getMin(), true);
      myLabel.setText(String.format("%s at %s", myTitle, time));
      myMaximumLabelWidth = Math.max(myMaximumLabelWidth, myLabel.getWidth());
      myLabel.setMinimumSize(new Dimension(myMaximumLabelWidth, 0));
    } else {
      myLabel.setText("");
    }
  }

  protected abstract Component createTooltip();

  final public Component createComponent() {

    Component tooltip = createTooltip();

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myLabel, BorderLayout.NORTH);
    panel.add(tooltip, BorderLayout.CENTER);
    panel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    panel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    timeChanged();
    return panel;
  }

  public void dispose() {
    myTimeline.getTooltipRange().removeDependencies(this);
  }
}
