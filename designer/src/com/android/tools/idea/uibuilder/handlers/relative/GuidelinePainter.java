/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.relative;

import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.relative.DependencyGraph.Constraint;
import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.uibuilder.graphics.NlDrawingStyle.*;

/**
 * The {@link GuidelinePainter} is responsible for painting guidelines during an operation
 * which uses a {@link GuidelineHandler} such as a resize operation.
 */
public final class GuidelinePainter {
  private static final AlphaComposite ALPHA_COMPOSITE = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.5f);

  public static void paint(@NotNull NlGraphics g, GuidelineHandler myState) {
    g.useStyle(DRAGGED);
    for (NlComponent dragged : myState.myDraggedNodes) {
      if (NlComponentHelperKt.getW(dragged) > 0 && NlComponentHelperKt.getH(dragged) > 0) {
        g.fillRect(NlComponentHelperKt.getX(dragged), NlComponentHelperKt.getY(dragged), NlComponentHelperKt.getW(dragged),
                   NlComponentHelperKt.getH(dragged));
      }
    }

    Set<NlComponent> horizontalDeps = myState.myHorizontalDeps;
    Set<NlComponent> verticalDeps = myState.myVerticalDeps;
    Set<NlComponent> deps = new HashSet<NlComponent>(horizontalDeps.size() + verticalDeps.size());
    deps.addAll(horizontalDeps);
    deps.addAll(verticalDeps);
    if (!deps.isEmpty()) {
      g.useStyle(DEPENDENCY);
      for (NlComponent n : deps) {
        // Don't highlight the selected nodes themselves
        if (myState.myDraggedNodes.contains(n)) {
          continue;
        }
        g.fillRect(NlComponentHelperKt.getX(n), NlComponentHelperKt.getY(n), NlComponentHelperKt.getW(n), NlComponentHelperKt.getH(n));
      }
    }

    // If the layout has padding applied, draw the padding bounds to make it obvious what the boundaries are
    if (!NlComponentHelperKt.getPadding(myState.layout).isEmpty() || !NlComponentHelperKt.getMargins(myState.layout).isEmpty()) {
      g.useStyle(NlDrawingStyle.PADDING_BOUNDS);
      NlComponent layout = myState.layout;
      Insets padding = NlComponentHelperKt.getPadding(layout);
      g.drawRect(NlComponentHelperKt.getX(layout) + padding.left,
                           NlComponentHelperKt.getY(layout) + padding.top,
                           Math.max(0, NlComponentHelperKt.getW(layout) - padding.left - padding.right),
                           Math.max(0, NlComponentHelperKt.getH(layout) - padding.top - padding.bottom));
    }

    if (myState.myBounds != null) {
      Rectangle bounds = myState.myBounds;
      if (myState instanceof RelativeDragHandler) {
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
      paintCycle(myState, g, myState.myHorizontalCycle);
    }
    if (myState.myVerticalCycle != null) {
      paintCycle(myState, g, myState.myVerticalCycle);
    }
  }

  /**
   * Paints a particular match constraint
   */
  private static void showMatch(NlGraphics g, Match m, GuidelineHandler state) {
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
  private static void paintCycle(GuidelineHandler myState, NlGraphics g, List<Constraint> cycle) {
    assert !cycle.isEmpty();

    NlComponent from = cycle.get(0).from.node;
    assert from != null;
    Rectangle fromBounds = new Rectangle(NlComponentHelperKt.getX(from), NlComponentHelperKt.getY(from), NlComponentHelperKt.getW(from),
                                         NlComponentHelperKt.getH(from));
    if (myState.myDraggedNodes.contains(from)) {
      fromBounds = myState.myBounds;
    }
    Point fromCenter = center(fromBounds);
    List<Point> points = new ArrayList<Point>();
    points.add(fromCenter);

    for (Constraint constraint : cycle) {
      assert constraint.from.node == from;
      NlComponent to = constraint.to.node;
      assert to != null;

      Point toCenter = new Point(NlComponentHelperKt.getX(to) + NlComponentHelperKt.getW(to) / 2,
                                 NlComponentHelperKt.getY(to) + NlComponentHelperKt.getH(to) / 2);
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
