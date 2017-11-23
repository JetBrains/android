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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class ProfilerTooltipView extends AspectObserver {
  @NotNull
  private final ProfilerTimeline myTimeline;

  @NotNull
  private final String myTitle;

  @NotNull
  protected final JLabel myHeadingLabel;

  @Nullable
  private JComponent myTooltipComponent;

  protected final Font myFont;

  private final int myMaximumLabelHeight;

  private int myMaximumWidth = 0;

  protected ProfilerTooltipView(@NotNull ProfilerTimeline timeline, @NotNull String title) {
    myTimeline = timeline;
    myTitle = title;

    myHeadingLabel = new JLabel();
    myHeadingLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    myFont = myHeadingLabel.getFont().deriveFont(ProfilerLayout.TOOLTIP_FONT_SIZE);
    myMaximumLabelHeight = myHeadingLabel.getFontMetrics(myFont).getHeight();
    myHeadingLabel.setFont(myFont);
    timeline.getTooltipRange().addDependency(this).onChange(Range.Aspect.RANGE, this::timeChanged);
  }

  protected void timeChanged() {
    Range range = myTimeline.getTooltipRange();
    if (!range.isEmpty()) {
      String time = TimeAxisFormatter.DEFAULT
        .getFormattedString(myTimeline.getDataRange().getLength(), range.getMin() - myTimeline.getDataRange().getMin(), true);
      myHeadingLabel.setText(String.format("%s at %s", myTitle, time));
      updateMaximumLabelDimensions();
    }
    else {
      myHeadingLabel.setText("");
    }
  }

  protected final void updateMaximumLabelDimensions() {
    int oldMaxWidth = myMaximumWidth;
    myMaximumWidth = Math.max(myMaximumWidth, myHeadingLabel.getPreferredSize().width);
    myMaximumWidth = Math.max(myMaximumWidth, myTooltipComponent == null ? 0 : myTooltipComponent.getPreferredSize().width);
    if (oldMaxWidth != myMaximumWidth) {
      // Set the minimum size so that the tooltip width doesn't flap.
      myHeadingLabel.setMinimumSize(new Dimension(myMaximumWidth, myMaximumLabelHeight));
    }
  }

  @NotNull
  protected abstract JComponent createTooltip();

  public final JComponent createComponent() {
    myTooltipComponent = createTooltip();

    // Reset label widths when the component changes.
    myMaximumWidth = 0;
    myHeadingLabel.setMinimumSize(new Dimension(myMaximumWidth, myMaximumLabelHeight));
    updateMaximumLabelDimensions();

    JPanel panel = new JPanel(new TabularLayout("*", "Fit,10px,*"));
    panel.add(myHeadingLabel, new TabularLayout.Constraint(0, 0));
    panel.add(myTooltipComponent, new TabularLayout.Constraint(2, 0));
    panel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    panel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    panel.setBorder(new JBEmptyBorder(10, 10, 10, 10));
    timeChanged();

    return panel;
  }

  public void dispose() {
    myTimeline.getTooltipRange().removeDependencies(this);
  }
}
