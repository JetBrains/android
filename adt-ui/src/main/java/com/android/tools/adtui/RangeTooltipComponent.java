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
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;

/**
 * A tooltip to be shown over some ranged chart with a vertical line to mark its horizontal position.
 */
public final class RangeTooltipComponent extends AnimatedComponent {
  public static final Color HIGHLIGHT_COLOR = new JBColor(0x4A81FF, 0x78B2FF);

  @NotNull
  private final Range myHighlightRange;

  @NotNull
  private final Range myViewRange;

  @NotNull
  private final Range myDataRange;

  @NotNull
  private final TooltipComponent myTooltipComponent;

  @Nullable
  private Point myLastPoint;

  public RangeTooltipComponent(@NotNull Range hightlight, @NotNull Range view, @NotNull Range data, Component component) {
    myHighlightRange = hightlight;
    myViewRange = view;
    myDataRange = data;

    myTooltipComponent = new TooltipComponent(component);
    add(myTooltipComponent);

    myViewRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::viewRangeChanged);
    myHighlightRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::highlightRangeChanged);
  }

  public void registerListenersOn(Component component) {
    myTooltipComponent.registerListenersOn(component);

    MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        handleMove(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myLastPoint = null;
        opaqueRepaint();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        handleMove(e);
      }

      private void handleMove(MouseEvent e) {
        myLastPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), RangeTooltipComponent.this);
        viewRangeChanged();
        opaqueRepaint();
      }
    };
    component.addMouseMotionListener(adapter);
    component.addMouseListener(adapter);
  }

  private void viewRangeChanged() {
    if (isShowing()) {
      if (myLastPoint != null) {
        double current = xToRange(myLastPoint.x);
        myHighlightRange.set(current, current);
      }
      else {
        myHighlightRange.clear();
      }
    }
  }

  private void highlightRangeChanged() {
    opaqueRepaint();
  }

  private double xToRange(int x) {
    return x / getSize().getWidth() * myViewRange.getLength() + myViewRange.getMin();
  }

  private float rangeToX(double value) {
    return (float)((value - myViewRange.getMin()) / (myViewRange.getMax() - myViewRange.getMin()));
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (myLastPoint == null || myHighlightRange.isEmpty() || myHighlightRange.getMin() < myDataRange.getMin()) {
      myTooltipComponent.setVisible(false);
      return;
    }
    myTooltipComponent.setVisible(true);

    float x = rangeToX(myHighlightRange.getMin());
    float pos = (float)(x * dim.getWidth());

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(HIGHLIGHT_COLOR);
    g.setStroke(new BasicStroke(2.0f));
    Path2D.Float path = new Path2D.Float();
    path.moveTo(pos, 0);
    path.lineTo(pos, dim.getHeight());
    g.draw(path);

    myTooltipComponent.setBounds(0, 0, getWidth(), getHeight());
    myTooltipComponent.repaint();
  }
}