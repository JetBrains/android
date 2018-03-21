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
package com.android.tools.profilers.network;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.ProfilerTooltipView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class NetworkRadioTooltipView extends ProfilerTooltipView {
  @NotNull private final NetworkRadioTooltip myTooltip;
  @NotNull private final ProfilerTimeline myTimeline;

  private JLabel myTimeRangeLabel;

  NetworkRadioTooltipView(@NotNull NetworkProfilerStageView view, @NotNull NetworkRadioTooltip tooltip) {
    super(view.getTimeline(), "Network Radio");
    myTimeline = view.getTimeline();
    myTooltip = tooltip;

    tooltip.addDependency(this).onChange(NetworkRadioTooltip.Aspect.RADIO_STATE, this::timeChanged);
  }

  @Override
  public void dispose() {
    super.dispose();
    myTooltip.removeDependencies(this);
  }

  @Override
  protected void timeChanged() {
    NetworkRadioTooltip.RadioStateData radioStateData = myTooltip.getRadioStateData();
    if (radioStateData != null) {
      String min = TimeAxisFormatter.DEFAULT.getClockFormattedString(
        (long)(radioStateData.getRadioStateRange().getMin() - myTimeline.getDataRange().getMin()));
      String max = TimeAxisFormatter.DEFAULT.getClockFormattedString(
        (long)(radioStateData.getRadioStateRange().getMax() - myTimeline.getDataRange().getMin()));
      myHeadingLabel.setText(radioStateData.getRadioState().name());
      myTimeRangeLabel.setText(String.format("%s - %s", min, max));
      updateMaximumLabelDimensions();
    }
    else {
      myHeadingLabel.setText("");
      myTimeRangeLabel.setText("");
    }
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    JPanel panel = new JPanel(new TabularLayout("*"));
    panel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    myTimeRangeLabel = new JLabel();
    myTimeRangeLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    myTimeRangeLabel.setFont(myFont);
    panel.add(myTimeRangeLabel, new TabularLayout.Constraint(0, 0));
    return panel;
  }
}
