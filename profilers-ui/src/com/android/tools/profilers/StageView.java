/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.tools.profilers.ProfilerFonts.STANDARD_FONT;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.TooltipView;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.ViewBinder;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.intellij.ui.components.JBPanel;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public abstract class StageView<T extends Stage> extends AspectObserver {
  private final T myStage;
  private final JPanel myComponent;
  private final StudioProfilersView myProfilersView;

  /**
   * Container for the tooltip.
   */
  private final JPanel myTooltipPanel;

  /**
   * View of the active tooltip for stages that contain more than one tooltips.
   */
  private TooltipView myActiveTooltipView;

  /**
   * Binder to bind a tooltip to its view.
   */
  private final ViewBinder<StageView, TooltipModel, TooltipView> myTooltipBinder;

  /**
   * A common component for showing the current selection range.
   */
  @NotNull private final JLabel mySelectionTimeLabel;

  // TODO (b/77709239): All Stages currently have a Panel that defines a tabular layout, and a tooltip.
  // we should refactor this so common functionality is in the base class to avoid more duplication.
  public StageView(@NotNull StudioProfilersView profilersView, @NotNull T stage) {
    myProfilersView = profilersView;
    myStage = stage;
    myComponent = new JBPanel(new BorderLayout());
    myComponent.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    // Use FlowLayout instead of the usual BorderLayout since BorderLayout doesn't respect min/preferred sizes.
    myTooltipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    myTooltipPanel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    myTooltipBinder = new ViewBinder<>();

    mySelectionTimeLabel = createSelectionTimeLabel();
    stage.getStudioProfilers().addDependency(this).onChange(ProfilerAspect.TOOLTIP, this::tooltipChanged);
    stage.getTimeline().getSelectionRange().addDependency(this).onChange(Range.Aspect.RANGE, this::selectionChanged);
    selectionChanged();
  }

  @NotNull
  public T getStage() {
    return myStage;
  }

  @NotNull
  public StudioProfilersView getProfilersView() {
    return myProfilersView;
  }

  @NotNull
  public IdeProfilerComponents getIdeComponents() {
    return myProfilersView.getIdeProfilerComponents();
  }

  @NotNull
  public final JComponent getComponent() {
    return myComponent;
  }

  public ViewBinder<StageView, TooltipModel, TooltipView> getTooltipBinder() {
    return myTooltipBinder;
  }

  public JPanel getTooltipPanel() {
    return myTooltipPanel;
  }

  @NotNull
  public JLabel getSelectionTimeLabel() {
    return mySelectionTimeLabel;
  }

  @NotNull
  protected JComponent buildTimeAxis(StudioProfilers profilers) {
    JPanel axisPanel = new JPanel(new BorderLayout());
    axisPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    AxisComponent timeAxis = new AxisComponent(profilers.getViewAxis(), AxisComponent.AxisOrientation.BOTTOM, true);
    timeAxis.setShowAxisLine(false);
    timeAxis.setMinimumSize(new Dimension(0, ProfilerLayout.TIME_AXIS_HEIGHT));
    timeAxis.setPreferredSize(new Dimension(Integer.MAX_VALUE, ProfilerLayout.TIME_AXIS_HEIGHT));
    axisPanel.add(timeAxis, BorderLayout.CENTER);
    return axisPanel;
  }

  abstract public JComponent getToolbar();

  public boolean isToolbarVisible() {
    return true;
  }

  /**
   * @return whether the current stage supports streaming. Useful for toggling the streaming controls (e.g. Go Live button).
   */
  public boolean supportsStreaming() {
    return getStage().getTimeline() instanceof StreamingTimeline;
  }

  /**
   * @return whether user can navigate to other stages from here. Useful for toggling the stage navigation controls (e.g. Go Back button,
   * profiler dropdown list).
   */
  public boolean supportsStageNavigation() {
    return getStage().getStudioProfilers().getSession().getPid() != 0;
  }

  /**
   * A purely visual concept as to whether this stage wants the "process and devices" selection being shown to the user.
   * It is not possible to assume processes won't change while a stage is running. For example: a process dying.
   */
  public boolean needsProcessSelection() {
    return false;
  }

  protected void tooltipChanged() {
    if (myActiveTooltipView != null) {
      myActiveTooltipView.dispose();
      myActiveTooltipView = null;
    }
    myTooltipPanel.removeAll();
    myTooltipPanel.setVisible(false);

    if (myStage.getTooltip() != null) {
      myActiveTooltipView = myTooltipBinder.build(this, myStage.getTooltip());
      myTooltipPanel.add(myActiveTooltipView.createComponent());
      myTooltipPanel.setVisible(true);
    }
    myTooltipPanel.invalidate();
    myTooltipPanel.repaint();
  }

  private void selectionChanged() {
    StreamingTimeline timeline = myStage.getStudioProfilers().getTimeline();
    Range selectionRange = timeline.getSelectionRange();
    if (selectionRange.isEmpty()) {
      mySelectionTimeLabel.setIcon(null);
      mySelectionTimeLabel.setText("");
      return;
    }

    // Note - relative time conversion happens in nanoseconds
    long selectionMinUs = timeline.convertToRelativeTimeUs(TimeUnit.MICROSECONDS.toNanos((long)selectionRange.getMin()));
    long selectionMaxUs = timeline.convertToRelativeTimeUs(TimeUnit.MICROSECONDS.toNanos((long)selectionRange.getMax()));
    mySelectionTimeLabel.setIcon(StudioIcons.Profiler.Toolbar.CLOCK);
    if (selectionRange.isPoint()) {
      mySelectionTimeLabel.setText(TimeFormatter.getSimplifiedClockString(selectionMinUs));
    }
    else {
      mySelectionTimeLabel.setText(String.format("%s - %s",
                                                 TimeFormatter.getSimplifiedClockString(selectionMinUs),
                                                 TimeFormatter.getSimplifiedClockString(selectionMaxUs)));
    }
  }

  @NotNull
  private JLabel createSelectionTimeLabel() {
    JLabel selectionTimeLabel = new JLabel("");
    selectionTimeLabel.setFont(STANDARD_FONT);
    selectionTimeLabel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
    selectionTimeLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        Timeline timeline = getStage().getTimeline();
        timeline.frameViewToRange(timeline.getSelectionRange());
      }
    });
    selectionTimeLabel.setToolTipText("Selected range");
    selectionTimeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return selectionTimeLabel;
  }
}
