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
import com.android.tools.adtui.TooltipComponent;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.profilers.ProfilerFonts.TOOLTIP_BODY_FONT;
import static com.android.tools.profilers.ProfilerFonts.TOOLTIP_HEADER_FONT;

public abstract class ProfilerTooltipView extends AspectObserver {
  @NotNull
  private final ProfilerTimeline myTimeline;

  @NotNull
  private final JLabel myHeadingLabel;

  protected final Font myFont;

  protected ProfilerTooltipView(@NotNull ProfilerTimeline timeline) {
    myTimeline = timeline;
    myHeadingLabel = new JLabel();
    myHeadingLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    myFont = TOOLTIP_HEADER_FONT;
    myHeadingLabel.setFont(myFont);
    timeline.getTooltipRange().addDependency(this).onChange(Range.Aspect.RANGE, this::timeChanged);
  }

  @VisibleForTesting
  public String getHeadingText() {
    return myHeadingLabel.getText();
  }

  private void timeChanged() {
    updateHeader();
    updateTooltip();
  }

  private void updateHeader() {
    Range range = myTimeline.getTooltipRange();
    if (!range.isEmpty() && range.getMin() >= myTimeline.getDataRange().getMin()) {
      String time = TimeFormatter.getSemiSimplifiedClockString((long)(range.getMin() - myTimeline.getDataRange().getMin()));
      myHeadingLabel.setText(time);
    }
    else {
      myHeadingLabel.setText("");
    }
  }

  /**
   * Function for tooltip views to override if they want to react on tooltip range change events.
   */
  protected void updateTooltip() {

  }

  @NotNull
  protected abstract JComponent createTooltip();

  protected static JLabel createTooltipLabel() {
    JLabel label = new JLabel();
    label.setFont(TOOLTIP_BODY_FONT);
    label.setForeground(ProfilerColors.TOOLTIP_TEXT);
    return label;
  }

  public final JComponent createComponent() {
    TooltipPanel tooltipPanel = new TooltipPanel(new TabularLayout("Fit", "Fit-,8px,Fit"));
    tooltipPanel.add(myHeadingLabel, new TabularLayout.Constraint(0, 0));
    tooltipPanel.add(createTooltip(), new TabularLayout.Constraint(2, 0));
    tooltipPanel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    tooltipPanel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    Border visibleBorder = JBUI.Borders.customLine(StudioColorsKt.getBorderLight());
    tooltipPanel.setBorder(JBUI.Borders.merge(new JBEmptyBorder(9, 9, 9, 9), visibleBorder, true));
    updateHeader();

    // Loop all the child components and set the background color so each tooltip doesn't need to do this individually.
    new TreeWalker(tooltipPanel).descendantStream().forEach((component -> component.setBackground(tooltipPanel.getBackground())));

    return tooltipPanel;
  }

  public void dispose() {
    myTimeline.getTooltipRange().removeDependencies(this);
  }

  /**
   * Special {@link JPanel} derived class to be able to synchronize its bounds with the tooltip renderer.
   */
  private static class TooltipPanel extends JPanel {
    public TooltipPanel(LayoutManager layoutManager) {
      super(layoutManager);
    }

    /**
     * The bounds of this panel is getting set in {@link TooltipComponent#draw(Graphics2D, Dimension)}. This usually
     * means when this component's children are updated, it is working with old bounds, which causes contents of this component to get
     * clipped. In order to avoid this, separate the logic of what's needed within {@link TooltipComponent#draw(Graphics2D, Dimension)}
     * (which is just to modify the (x, y) coordinates just prior to drawing, and update the width/height here (with the stale (x, y)
     * coordinates).
     */
    @Override
    public void doLayout() {
      // Recompute the width/height, but leave (x, y) intact.
      Rectangle oldBounds = getBounds();
      Dimension preferredSize = getPreferredSize();
      setBounds(oldBounds.x, oldBounds.y, preferredSize.width, preferredSize.height);

      super.doLayout();
    }
  }
}
