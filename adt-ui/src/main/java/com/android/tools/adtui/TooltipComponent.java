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
import java.awt.geom.RoundRectangle2D;

/**
 * A vertical marker with a tooltip.
 */
public final class TooltipComponent extends AnimatedComponent {

  public static final Color HIGHLIGHT_COLOR = new JBColor(0x4A81FF, 0x4A81FF);

  @NotNull
  private final Range myHighlightRange;
  @NotNull
  private final Range myViewRange;
  @NotNull
  private final Range myDataRange;
  @NotNull
  private final Component myComponent;
  @Nullable
  private Point myLastPoint;

  public TooltipComponent(@NotNull Range hightlight, @NotNull Range view, Range data, Component component) {
    myHighlightRange = hightlight;
    myViewRange = view;
    myDataRange = data;
    myComponent = component;
    add(component);

    myViewRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::viewRangeChanged);
    myHighlightRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::highlightRangeChanged);
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

  public void registerListenersOn(Component component) {
    MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        myLastPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TooltipComponent.this);
        viewRangeChanged();
        opaqueRepaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myLastPoint = null;
        opaqueRepaint();
      }
    };
    component.addMouseMotionListener(adapter);
    component.addMouseListener(adapter);
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
      myComponent.setVisible(false);
      return;
    }
    myComponent.setVisible(true);
    float x = rangeToX(myHighlightRange.getMin());
    float pos = (float)(x * dim.getWidth());

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(HIGHLIGHT_COLOR);
    g.setStroke(new BasicStroke(2.0f));
    Path2D.Float path = new Path2D.Float();
    path.moveTo(pos, 0);
    path.lineTo(pos, dim.getHeight());
    g.draw(path);

    Dimension size = myComponent.getPreferredSize();
    Dimension minSize = myComponent.getMinimumSize();
    size = new Dimension(Math.max(size.width, minSize.width), Math.max(size.height, minSize.height));

    g.setColor(Color.WHITE);
    int gap = 10;
    int x1 = Math.max(Math.min((int)(pos + gap), dim.width - size.width - gap), 0);
    int y1 = Math.max(Math.min(myLastPoint.y + gap, dim.height - size.height - gap), gap);
    int width = size.width;
    int height = size.height;

    g.fillRect(x1, y1, width, height);

    g.setStroke(new BasicStroke(1.0f));

    int lines = 4;
    int[] alphas = new int[]{40, 30, 20, 10};
    RoundRectangle2D.Float rect = new RoundRectangle2D.Float();
    for (int i = 0; i < lines; i++) {
      g.setColor(new Color(0, 0, 0, alphas[i]));
      rect.setRoundRect(x1 - 1 - i, y1 - 1 - i, width + 1 + i * 2, height + 1 + i * 2, i * 2 + 2, i * 2 + 2);
      g.draw(rect);
    }
    myComponent.setBounds(x1, y1, width, height);
    myComponent.repaint();
  }
}