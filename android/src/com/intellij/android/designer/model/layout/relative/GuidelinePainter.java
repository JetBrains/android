/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer.model.layout.relative;

import com.intellij.android.designer.designSurface.RootView;
import com.intellij.android.designer.designSurface.graphics.DesignerGraphics;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.android.designer.designSurface.graphics.DrawingStyle.*;
import static com.intellij.android.designer.model.layout.relative.DependencyGraph.Constraint;

/**
 * The {@link GuidelinePainter} is responsible for painting guidelines during an operation
 * which uses a {@link GuidelineHandler} such as a resize operation.
 */
public final class GuidelinePainter extends JComponent {
  private static final AlphaComposite ALPHA_COMPOSITE = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.5f);

  @NotNull private final GuidelineHandler myState;

  public GuidelinePainter(@NotNull GuidelineHandler state) {
    myState = state;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);

    DesignerGraphics g = new DesignerGraphics(graphics, this);
    paint(g);
  }

  private void paint(@NotNull DesignerGraphics g) {
    g.useStyle(DRAGGED);
    for (RadViewComponent dragged : myState.myDraggedNodes) {
      Rectangle bounds = dragged.getBounds(g.getTarget());
      if (!bounds.isEmpty()) {
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
      }
    }

    Set<RadViewComponent> horizontalDeps = myState.myHorizontalDeps;
    Set<RadViewComponent> verticalDeps = myState.myVerticalDeps;
    Set<RadViewComponent> deps = new HashSet<RadViewComponent>(horizontalDeps.size() + verticalDeps.size());
    deps.addAll(horizontalDeps);
    deps.addAll(verticalDeps);
    if (deps.size() > 0) {
      g.useStyle(DEPENDENCY);
      for (RadViewComponent n : deps) {
        // Don't highlight the selected nodes themselves
        if (myState.myDraggedNodes.contains(n)) {
          continue;
        }
        Rectangle bounds = n.getBounds(g.getTarget());
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
      }
    }

    // If the layout has padding applied, draw the padding bounds to make it obvious what the boundaries are
    if (!myState.layout.getPadding().isEmpty() || !myState.layout.getMargins().isEmpty()) {
      g.useStyle(DrawingStyle.PADDING_BOUNDS);
      JComponent target = myState.myContext.getArea().getFeedbackLayer();
      Rectangle bounds = myState.layout.getPaddedBounds(target);
      g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    if (myState.myBounds != null) {
      Rectangle bounds = myState.myBounds;
      if (myState instanceof MoveHandler) {
        List<RadComponent> myComponents = myState.getContext().getComponents();
        if (myComponents.size() == 1) {
          RadComponent component = myComponents.get(0);
          final Rectangle targetBounds = component.getBounds(g.getTarget());
          RootView nativeComponent = (RootView)((RadViewComponent)component.getRoot()).getNativeComponent();
          if (nativeComponent != null) {
            final BufferedImage image = nativeComponent.getImage();
            final Rectangle sourceBounds = component.getBounds();
            Graphics2D g2d = (Graphics2D)g.getGraphics();
            Composite prevComposite = g2d.getComposite();
            try {
              if (image != null) {
                g2d.setComposite(ALPHA_COMPOSITE);
                g2d.drawImage(image, bounds.x, bounds.y, bounds.x + targetBounds.width, bounds.y + targetBounds.height, sourceBounds.x,
                              sourceBounds.y, sourceBounds.x + sourceBounds.width, sourceBounds.y + sourceBounds.height, null);
              }
            } finally {
              g2d.setComposite(prevComposite);
            }
          }
        }

        g.useStyle(DROP_PREVIEW);
      }
      else {
        // Resizing
        if (myState.haveSuggestions()) {
          g.useStyle(RESIZE_PREVIEW);
        }
        else {
          g.useStyle(RESIZE_FAIL);
        }
      }
      g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

      // Draw baseline preview too
      // TODO: Implement when we have drag information from palette drag previews
      //if (myFeedback.dragBaseline != -1) {
      //  int y = myState.myBounds.y + myFeedback.dragBaseline;
      //  g.drawLine(myState.myBounds.x, y, myState.myBounds.x + myState.myBounds.width, y);
      //}
    }

    showMatch(g, myState.getCurrentLeftMatch(), myState);
    showMatch(g, myState.getCurrentRightMatch(), myState);
    showMatch(g, myState.getCurrentTopMatch(), myState);
    showMatch(g, myState.getCurrentBottomMatch(), myState);

    if (myState.myHorizontalCycle != null) {
      paintCycle(g, myState.myHorizontalCycle);
    }
    if (myState.myVerticalCycle != null) {
      paintCycle(g, myState.myVerticalCycle);
    }
  }

  /**
   * Paints a particular match constraint
   */
  private static void showMatch(DesignerGraphics g, Match m, GuidelineHandler state) {
    if (m == null) {
      return;
    }
    ConstraintPainter.paintConstraint(g, state, m);
  }

  private static Point center(Rectangle rectangle) {
    return new Point(rectangle.x + rectangle.width / 2, rectangle.y + rectangle.height / 2);
  }

  /**
   * Paints a constraint cycle
   */
  void paintCycle(DesignerGraphics g, List<Constraint> cycle) {
    assert cycle.size() > 0;

    RadViewComponent from = cycle.get(0).from.node;
    assert from != null;
    Rectangle fromBounds = from.getBounds(g.getTarget());
    if (myState.myDraggedNodes.contains(from)) {
      fromBounds = myState.myBounds;
    }
    Point fromCenter = center(fromBounds);
    List<Point> points = new ArrayList<Point>();
    points.add(fromCenter);

    for (Constraint constraint : cycle) {
      assert constraint.from.node == from;
      RadViewComponent to = constraint.to.node;
      assert to != null;

      Point toCenter = center(to.getBounds(g.getTarget()));
      points.add(toCenter);

      // Also go through the dragged node bounds
      boolean isDragged = myState.myDraggedNodes.contains(to);
      if (isDragged) {
        toCenter = center(myState.myBounds);
        points.add(toCenter);
      }

      from = to;
      fromCenter = toCenter;
    }

    points.add(fromCenter);
    points.add(points.get(0));

    g.useStyle(CYCLE);
    for (int i = 1, n = points.size(); i < n; i++) {
      Point a = points.get(i - 1);
      Point b = points.get(i);
      g.drawLine(a.x, a.y, b.x, b.y);
    }
  }
}
