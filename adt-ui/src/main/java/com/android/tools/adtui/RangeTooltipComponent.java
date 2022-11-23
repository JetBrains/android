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
package com.android.tools.adtui;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.Timeline;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A tooltip to be shown over some ranged chart with a vertical line to mark its horizontal position.
 */
public final class RangeTooltipComponent extends AnimatedComponent {
  public static final Color HIGHLIGHT_COLOR = new JBColor(0x4A81FF, 0x78B2FF);

  private static final float HIGHLIGHT_WIDTH = 2.0f;

  private static final float INVALID_HIGHLIGHT_X = -Float.MAX_VALUE;

  // We want to "erase" the line at the old X position, and we already have that X position in the draw method.
  // Therefore, we just stash the old X position here.
  private float myOldHighlightX = INVALID_HIGHLIGHT_X;

  @NotNull
  private final Timeline myTimeline;

  @NotNull
  private final TooltipComponent myTooltipComponent;

  /**
   * Supplier that determines if our seek component is visible or not. This is determined in addition to
   * other requirements of showing if the tooltip is visible or not see {@link #draw(Graphics2D, Dimension)}
   */
  @NotNull
  private final Supplier<Boolean> myShowSeekComponent;

  @Nullable
  private Point myLastPoint;

  public RangeTooltipComponent(@NotNull Timeline timeline,
                               @NotNull JComponent component,
                               @NotNull JLayeredPane parent,
                               @NotNull Supplier<Boolean> showSeekComponent) {
    myTimeline = timeline;
    myShowSeekComponent = showSeekComponent;
    myTooltipComponent =
      new TooltipComponent.Builder(component, this, parent)
        .setDefaultVisibilityOverride(() -> isHighlightRangeVisible())
        // Flapping usually happens when the timeline is constantly changing.
        .setEnableAntiFlap(timeline instanceof StreamingTimeline)
        .build();
    myTimeline.getViewRange().addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::refreshRanges);
    myTimeline.getTooltipRange().addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::tooltipRangeChanged);
  }

  @VisibleForTesting
  public RangeTooltipComponent(Timeline timeline, @NotNull JComponent component) {
    this(timeline, component, new JLayeredPane(), () -> true);
  }

  public void registerListenersOn(@NotNull JComponent component) {
    MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        handleMove(e);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        handleMove(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myLastPoint = null;
        myTimeline.getTooltipRange().clear();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        handleMove(e);
      }

      private void handleMove(MouseEvent e) {
        Point nextPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), RangeTooltipComponent.this);
        if (myLastPoint != null && myLastPoint.equals(nextPoint)) {
          return; // Mouse detected a movement, but not enough to cross pixel boundaries.
        }

        myLastPoint = nextPoint;
        refreshRanges();
      }
    };
    component.addMouseMotionListener(adapter);
    component.addMouseListener(adapter);

    // Add the TooltipComponent listeners after, since they need to be fired after the contents' listeners are fired.
    myTooltipComponent.registerListenersOn(component);
  }

  private void refreshRanges() {
    if (isShowing()) {
      if (myLastPoint != null) {
        double current = xToRange(myLastPoint.x);
        myTimeline.getTooltipRange().set(current, current);
      }
      else {
        myTimeline.getTooltipRange().clear();
      }
    }
  }

  @SuppressWarnings("FloatingPointEquality")
  private void tooltipRangeChanged() {
    if (myTimeline.getTooltipRange().isEmpty() && myOldHighlightX == INVALID_HIGHLIGHT_X) {
      return;
    }

    if (myOldHighlightX != INVALID_HIGHLIGHT_X) {
      int minX = (int)Math.floor(myOldHighlightX - HIGHLIGHT_WIDTH / 2.0);
      int width = (int)Math.ceil(myOldHighlightX + HIGHLIGHT_WIDTH / 2.0) - minX;
      opaqueRepaint(minX, 0, width, getHeight());
      myOldHighlightX = INVALID_HIGHLIGHT_X;
    }

    // Repaint the area where the highlight/seek line will be only when the range is not empty and the highlight/seek line is turned on.
    if (isHighlightRangeVisible() && myShowSeekComponent.get()) {
      float x = rangeToX(myTimeline.getTooltipRange().getMin());
      int minX = (int)Math.floor(x - HIGHLIGHT_WIDTH / 2.0);
      int width = (int)Math.ceil(x + HIGHLIGHT_WIDTH / 2.0) - minX;
      opaqueRepaint(minX, 0, width, getHeight());
    }

    myTooltipComponent.setVisible(isHighlightRangeVisible());
  }

  @VisibleForTesting
  double xToRange(int x) {
    return x / (double)getWidth() * myTimeline.getViewRange().getLength() + myTimeline.getViewRange().getMin();
  }

  @VisibleForTesting
  float rangeToX(double value) {
    return (float)(getWidth() * (value - myTimeline.getViewRange().getMin()) /
                   (myTimeline.getViewRange().getMax() - myTimeline.getViewRange().getMin()));
  }

  private boolean isHighlightRangeVisible() {
    return myLastPoint != null &&
           !myTimeline.getTooltipRange().isEmpty() &&
           myTimeline.getTooltipRange().getMin() >= myTimeline.getDataRange().getMin();
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (!isHighlightRangeVisible()) {
      return;
    }

    if (myShowSeekComponent.get()) {
      float x = rangeToX(myTimeline.getTooltipRange().getMin());
      myOldHighlightX = x;

      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(HIGHLIGHT_COLOR);
      g.setStroke(new BasicStroke(HIGHLIGHT_WIDTH));
      Path2D.Float path = new Path2D.Float();
      path.moveTo(x, 0);
      path.lineTo(x, getHeight());
      g.draw(path);
    }
  }
}