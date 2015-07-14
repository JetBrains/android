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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.graphics.NlDrawingStyle.*;


/**
 * The {@link RelativeLayoutConstraintPainter} is responsible for painting relative layout constraints -
 * such as a source node having its top edge constrained to a target node with a given margin.
 * This painter is used both to show static constraints, as well as visualizing proposed
 * constraints during a move or resize operation.
 */
public class RelativeLayoutConstraintPainter {
  /**
   * The size of the arrow head
   */
  private static final int ARROW_SIZE = 5;
  /**
   * Size (height for horizontal, and width for vertical) parent feedback rectangles
   */
  private static final int PARENT_RECT_SIZE = 12;

  /**
   * Paints a constraint.
   * <p/>
   * TODO: when there are multiple links originating in the same direction from
   * center, maybe offset them slightly from each other?
   *
   * @param graphics   the graphics context to draw into
   * @param constraint The constraint to be drawn
   */
  private static void paintConstraint(ScreenView screenView,
                                      NlGraphics graphics,
                                      DependencyGraph.Constraint constraint,
                                      Set<DependencyGraph.Constraint> allConstraints,
                                      TextDirection textDirection) {
    DependencyGraph.ViewData source = constraint.from;
    DependencyGraph.ViewData target = constraint.to;

    NlComponent sourceNode = source.node;
    NlComponent targetNode = target.node;
    if (sourceNode == targetNode) {
      // Self reference - don't visualize
      return;
    }

    Rectangle sourceBounds = getBounds(screenView, sourceNode);
    Rectangle targetBounds = getBounds(screenView, targetNode);
    paintConstraint(screenView, graphics, constraint.type, sourceNode, sourceBounds, targetNode, targetBounds, allConstraints,
                    false /* highlightTargetEdge */, textDirection);
  }

  // TODO: Make more efficient
  private static Rectangle getBounds(ScreenView screenView, NlComponent component) {
    return new Rectangle(Coordinates.getSwingX(screenView, component.x), Coordinates.getSwingY(screenView, component.y),
                         Coordinates.getSwingDimension(screenView, component.w), Coordinates.getSwingDimension(screenView, component.h));
  }

  // TODO: Make more efficient
  private static Insets getInsets(ScreenView screenView, Insets insets) {
    if (insets == Insets.NONE) {
      return insets;
    }
    int left = insets.left;
    int top = insets.top;
    int right = insets.right;
    int bottom = insets.bottom;
    if (left != 0) {
      left = Coordinates.getAndroidDimension(screenView, left);
    }
    if (right != 0) {
      right = Coordinates.getAndroidDimension(screenView, right);
    }
    if (top != 0) {
      left = Coordinates.getAndroidDimension(screenView, top);
    }
    if (bottom != 0) {
      left = Coordinates.getAndroidDimension(screenView, bottom);
    }
    return new Insets(left, top, right, bottom);
  }


  /**
   * Paint selection feedback by painting constraints for the selected nodes
   *
   * @param graphics       the graphics context
   * @param parentNode     the parent relative layout
   * @param childNodes     the nodes whose constraints should be painted
   * @param showDependents whether incoming constraints should be shown as well
   */
  public static void paintSelectionFeedback(ScreenView screenView,
                                            NlGraphics graphics,
                                            NlComponent parentNode,
                                            List<NlComponent> childNodes,
                                            boolean showDependents,
                                            TextDirection textDirection) {

    DependencyGraph dependencyGraph = DependencyGraph.get(parentNode);
    Set<NlComponent> horizontalDeps = dependencyGraph.dependsOn(childNodes, false /* vertical */);
    Set<NlComponent> verticalDeps = dependencyGraph.dependsOn(childNodes, true /* vertical */);
    Set<NlComponent> deps = new HashSet<NlComponent>(horizontalDeps.size() + verticalDeps.size());
    deps.addAll(horizontalDeps);
    deps.addAll(verticalDeps);
    if (deps.size() > 0) {
      for (NlComponent node : deps) {
        // Don't highlight the selected nodes themselves
        if (childNodes.contains(node)) {
          continue;
        }
        Rectangle bounds = getBounds(screenView, node);
        graphics.useStyle(DEPENDENCY);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
      }
    }

    for (NlComponent childNode : childNodes) {
      DependencyGraph.ViewData view = dependencyGraph.getView(childNode);
      if (view == null) {
        continue;
      }

      // Paint all incoming constraints
      if (showDependents) {
        paintConstraints(screenView, graphics, view.dependedOnBy, textDirection);
      }

      // Paint all outgoing constraints
      paintConstraints(screenView, graphics, view.dependsOn, textDirection);
    }
  }

  /**
   * Paints a set of constraints.
   */
  private static void paintConstraints(ScreenView screenView, NlGraphics graphics, java.util.List<DependencyGraph.Constraint> constraints, TextDirection textDirection) {
    Set<DependencyGraph.Constraint> mutableConstraintSet = new HashSet<DependencyGraph.Constraint>(constraints);

    // WORKAROUND! Hide alignBottom attachments if we also have a alignBaseline
    // constraint; this is because we also *add* alignBottom attachments when you add
    // alignBaseline constraints to work around a surprising behavior of baseline
    // constraints.
    for (DependencyGraph.Constraint constraint : constraints) {
      if (constraint.type == ConstraintType.ALIGN_BASELINE) {
        // Remove any baseline
        for (DependencyGraph.Constraint c : constraints) {
          if (c.type == ConstraintType.ALIGN_BOTTOM && c.to.node == constraint.to.node) {
            mutableConstraintSet.remove(c);
          }
        }
      }
    }

    for (DependencyGraph.Constraint constraint : constraints) {
      // paintConstraint can digest more than one constraint, so we need to keep
      // checking to see if the given constraint is still relevant.
      if (mutableConstraintSet.contains(constraint)) {
        paintConstraint(screenView, graphics, constraint, mutableConstraintSet, textDirection);
      }
    }
  }

  /**
   * Paints a constraint of the given type from the given source node, to the
   * given target node, with the specified bounds.
   */
  private static void paintConstraint(ScreenView screenView,
                                      NlGraphics graphics,
                                      ConstraintType type,
                                      NlComponent sourceNode,
                                      Rectangle sourceBounds,
                                      NlComponent targetNode,
                                      Rectangle targetBounds,
                                      @Nullable Set<DependencyGraph.Constraint> allConstraints,
                                      boolean highlightTargetEdge,
                                      TextDirection textDirection) {

    SegmentType sourceSegmentTypeX = type.sourceSegmentTypeX;
    SegmentType sourceSegmentTypeY = type.sourceSegmentTypeY;
    SegmentType targetSegmentTypeX = type.targetSegmentTypeX;
    SegmentType targetSegmentTypeY = type.targetSegmentTypeY;

    // Horizontal center constraint?
    if (sourceSegmentTypeX == SegmentType.CENTER_VERTICAL && targetSegmentTypeX == SegmentType.CENTER_VERTICAL) {
      paintHorizontalCenterConstraint(graphics, sourceBounds, targetBounds);
      return;
    }

    // Vertical center constraint?
    if (sourceSegmentTypeY == SegmentType.CENTER_HORIZONTAL && targetSegmentTypeY == SegmentType.CENTER_HORIZONTAL) {
      paintVerticalCenterConstraint(graphics, sourceBounds, targetBounds);
      return;
    }

    // Corner constraint?
    if (allConstraints != null && (type == ConstraintType.LAYOUT_ABOVE || type == ConstraintType.LAYOUT_BELOW || type == ConstraintType.LAYOUT_LEFT_OF || type == ConstraintType.LAYOUT_RIGHT_OF)) {
      if (paintCornerConstraint(graphics, type, sourceNode, sourceBounds, targetNode, targetBounds, allConstraints, textDirection)) {
        return;
      }
    }

    // Vertical constraint?
    if (sourceSegmentTypeX == SegmentType.UNKNOWN) {
      paintVerticalConstraint(screenView, graphics, type, sourceNode, sourceBounds, targetNode, targetBounds, highlightTargetEdge);
      return;
    }

    // Horizontal constraint?
    if (sourceSegmentTypeY == SegmentType.UNKNOWN) {
      paintHorizontalConstraint(screenView, graphics, type, sourceNode, sourceBounds, targetNode, targetBounds, highlightTargetEdge, textDirection);
      return;
    }

    // This shouldn't happen - it means we have a constraint that defines all sides
    // and is not a centering constraint
    assert false;
  }

  /**
   * Paints a corner constraint, or returns false if this constraint is not a corner.
   * A corner is one where there are two constraints from this source node to the
   * same target node, one horizontal and one vertical, to the closest edges on
   * the target node.
   * <p/>
   * Corners are a common occurrence. If we treat the horizontal and vertical
   * constraints separately (below & toRightOf), then we end up with a lot of
   * extra lines and arrows -- e.g. two shared edges and arrows pointing to these
   * shared edges:
   * <p/>
   * <pre>
   *  +--------+ |
   *  | Target -->
   *  +----|---+ |
   *       v
   *  - - - - - -|- - - - - -
   *                   ^
   *             | +---|----+
   *             <-- Source |
   *             | +--------+
   *
   * Instead, we can simply draw a diagonal arrow here to represent BOTH constraints and
   * reduce clutter:
   *
   *  +---------+
   *  | Target _|
   *  +-------|\+
   *            \
   *             \--------+
   *             | Source |
   *             +--------+
   * </pre>
   *
   * @param graphics       the graphics context to draw
   * @param type           the constraint to be drawn
   * @param sourceNode     the source node
   * @param sourceBounds   the bounds of the source node
   * @param targetNode     the target node
   * @param targetBounds   the bounds of the target node
   * @param allConstraints the set of all constraints; if a corner is found and painted the
   *                       matching corner constraint is removed from the set
   * @return true if the constraint was handled and painted as a corner, false otherwise
   */
  @SuppressWarnings("PointlessArithmeticExpression")
  private static boolean paintCornerConstraint(NlGraphics graphics,
                                               ConstraintType type,
                                               NlComponent sourceNode,
                                               Rectangle sourceBounds,
                                               NlComponent targetNode,
                                               Rectangle targetBounds,
                                               Set<DependencyGraph.Constraint> allConstraints,
                                               TextDirection textDirection) {

    SegmentType sourceSegmentTypeX = type.sourceSegmentTypeX;
    SegmentType sourceSegmentTypeY = type.sourceSegmentTypeY;
    SegmentType targetSegmentTypeX = type.targetSegmentTypeX;
    SegmentType targetSegmentTypeY = type.targetSegmentTypeY;

    ConstraintType opposite1, opposite2;
    switch (type) {
      case LAYOUT_BELOW:
      case LAYOUT_ABOVE:
        opposite1 = ConstraintType.LAYOUT_LEFT_OF;
        opposite2 = ConstraintType.LAYOUT_RIGHT_OF;
        break;
      case LAYOUT_LEFT_OF:
      case LAYOUT_RIGHT_OF:
        opposite1 = ConstraintType.LAYOUT_ABOVE;
        opposite2 = ConstraintType.LAYOUT_BELOW;
        break;
      default:
        return false;
    }
    DependencyGraph.Constraint pair = null;
    for (DependencyGraph.Constraint constraint : allConstraints) {
      if ((constraint.type == opposite1 || constraint.type == opposite2) &&
          constraint.to.node == targetNode && constraint.from.node == sourceNode) {
        pair = constraint;
        break;
      }
    }

    // TODO -- ensure that the nodes are adjacent! In other words, that
    // their bounds are within N pixels.

    if (pair != null) {
      // Visualize the corner constraint
      if (sourceSegmentTypeX == SegmentType.UNKNOWN) {
        sourceSegmentTypeX = pair.type.sourceSegmentTypeX;
      }
      if (sourceSegmentTypeY == SegmentType.UNKNOWN) {
        sourceSegmentTypeY = pair.type.sourceSegmentTypeY;
      }
      if (targetSegmentTypeX == SegmentType.UNKNOWN) {
        targetSegmentTypeX = pair.type.targetSegmentTypeX;
      }
      if (targetSegmentTypeY == SegmentType.UNKNOWN) {
        targetSegmentTypeY = pair.type.targetSegmentTypeY;
      }

      int x1, y1, x2, y2;
      if (textDirection.isLeftSegment(sourceSegmentTypeX)) {
        x1 = sourceBounds.x + 1 * sourceBounds.width / 4;
      }
      else {
        x1 = sourceBounds.x + 3 * sourceBounds.width / 4;
      }
      if (sourceSegmentTypeY == SegmentType.TOP) {
        y1 = sourceBounds.y + 1 * sourceBounds.height / 4;
      }
      else {
        y1 = sourceBounds.y + 3 * sourceBounds.height / 4;
      }
      if (textDirection.isLeftSegment(targetSegmentTypeX)) {
        x2 = targetBounds.x + 1 * targetBounds.width / 4;
      }
      else {
        x2 = targetBounds.x + 3 * targetBounds.width / 4;
      }
      if (targetSegmentTypeY == SegmentType.TOP) {
        y2 = targetBounds.y + 1 * targetBounds.height / 4;
      }
      else {
        y2 = targetBounds.y + 3 * targetBounds.height / 4;
      }

      graphics.useStyle(GUIDELINE);
      graphics.drawArrow(x1, y1, x2, y2);

      // Don't process this constraint on its own later.
      allConstraints.remove(pair);

      return true;
    }

    return false;
  }

  /**
   * Paints a vertical constraint, handling the various scenarios where there are
   * margins, or where the two nodes overlap horizontally and where they don't, etc.
   * <p/>
   * Here's an example of what will be shown for a "below" constraint where the
   * nodes do not overlap horizontally and the target node has a bottom margin:
   * <pre>
   *  +--------+
   *  | Target |
   *  +--------+
   *       |
   *       v
   *   - - - - - - - - - - - - - -
   *                         ^
   *                         |
   *                    +--------+
   *                    | Source |
   *                    +--------+
   * </pre>
   */
  private static void paintVerticalConstraint(ScreenView screenView,
                                              NlGraphics graphics,
                                              ConstraintType type,
                                              NlComponent sourceNode,
                                              Rectangle sourceBounds,
                                              NlComponent targetNode,
                                              Rectangle targetBounds,
                                              boolean highlightTargetEdge) {
    SegmentType sourceSegmentTypeY = type.sourceSegmentTypeY;
    SegmentType targetSegmentTypeY = type.targetSegmentTypeY;
    Insets targetMargins = getInsets(screenView, targetNode.getMargins());

    assert sourceSegmentTypeY != SegmentType.UNKNOWN;
    assert targetBounds != null;

    int sourceY = sourceSegmentTypeY.getY(sourceNode, sourceBounds);
    int targetY = targetSegmentTypeY == SegmentType.UNKNOWN ? sourceY : targetSegmentTypeY.getY(targetNode, targetBounds);

    if (highlightTargetEdge && type.isRelativeToParentEdge()) {
      graphics.useStyle(DROP_ZONE_ACTIVE);
      graphics.fillRect(targetBounds.x, targetY - PARENT_RECT_SIZE / 2, targetBounds.width, PARENT_RECT_SIZE);
    }

    // First see if the two views overlap horizontally. If so, we can just draw a direct
    // arrow from the source up to (or down to) the target.
    //
    //  +--------+
    //  | Target |
    //  +--------+
    //         ^
    //         |
    //         |
    //       +--------+
    //       | Source |
    //       +--------+
    //
    int maxLeft = Math.max(sourceBounds.x, targetBounds.x);
    int minRight = Math.min(x2(sourceBounds), x2(targetBounds));

    int center = (maxLeft + minRight) / 2;
    if (center > sourceBounds.x && center < x2(sourceBounds)) {
      // Yes, the lines overlap -- just draw a straight arrow
      //
      //
      // If however there is a margin on the target edge, it should be drawn like this:
      //
      //  +--------+
      //  | Target |
      //  +--------+
      //         |
      //         |
      //         v
      //   - - - - - - -
      //         ^
      //         |
      //         |
      //       +--------+
      //       | Source |
      //       +--------+
      //
      // Use a minimum threshold for this visualization since it doesn't look good
      // for small margins
      if (targetSegmentTypeY == SegmentType.BOTTOM && targetMargins.bottom > 5) {
        int sharedY = targetY + targetMargins.bottom;
        if (sourceY > sharedY + 2) { // Skip when source falls on the margin line
          graphics.useStyle(GUIDELINE_DASHED);
          graphics.drawLine(targetBounds.x, sharedY, x2(targetBounds), sharedY);
          graphics.useStyle(GUIDELINE);
          graphics.drawArrow(center, sourceY, center, sharedY + 2);
          graphics.drawArrow(center, targetY, center, sharedY - 3);
        }
        else {
          graphics.useStyle(GUIDELINE);
          // Draw reverse arrow to make it clear the node is as close
          // at it can be
          graphics.drawArrow(center, targetY, center, sourceY);
        }
        return;
      }
      else if (targetSegmentTypeY == SegmentType.TOP && targetMargins.top > 5) {
        int sharedY = targetY - targetMargins.top;
        if (sourceY < sharedY - 2) {
          graphics.useStyle(GUIDELINE_DASHED);
          graphics.drawLine(targetBounds.x, sharedY, x2(targetBounds), sharedY);
          graphics.useStyle(GUIDELINE);
          graphics.drawArrow(center, sourceY, center, sharedY - 3);
          graphics.drawArrow(center, targetY, center, sharedY + 3);
        }
        else {
          graphics.useStyle(GUIDELINE);
          graphics.drawArrow(center, targetY, center, sourceY);
        }
        return;
      }

      // TODO: If the center falls smack in the center of the sourceBounds,
      // AND the source node is part of the selection, then adjust the
      // center location such that it is off to the side, let's say 1/4 or 3/4 of
      // the overlap region, to ensure that it does not overlap the center selection
      // handle

      // When the constraint is for two immediately adjacent edges, we
      // need to make some adjustments to make sure the arrow points in the right
      // direction
      if (sourceY == targetY) {
        if (sourceSegmentTypeY == SegmentType.BOTTOM || sourceSegmentTypeY == SegmentType.BASELINE) {
          sourceY -= 2 * ARROW_SIZE;
        }
        else if (sourceSegmentTypeY == SegmentType.TOP) {
          sourceY += 2 * ARROW_SIZE;
        }
        else {
          assert sourceSegmentTypeY == SegmentType.CENTER_HORIZONTAL : sourceSegmentTypeY;
          sourceY += sourceBounds.height / 2 - 2 * ARROW_SIZE;
        }
      }
      else if (sourceSegmentTypeY == SegmentType.BASELINE) {
        sourceY = targetY - 2 * ARROW_SIZE;
      }

      // Center the vertical line in the overlap region
      graphics.useStyle(GUIDELINE);
      graphics.drawArrow(center, sourceY, center, targetY);

      return;
    }

    // If there is no horizontal overlap in the vertical constraints, then we
    // will show the attachment relative to a dashed line that extends beyond
    // the target bounds, like this:
    //
    //  +--------+
    //  | Target |
    //  +--------+ - - - - - - - - -
    //                         ^
    //                         |
    //                    +--------+
    //                    | Source |
    //                    +--------+
    //
    // However, if the target node has a vertical margin, we may need to offset
    // the line:
    //
    //  +--------+
    //  | Target |
    //  +--------+
    //       |
    //       v
    //   - - - - - - - - - - - - - -
    //                         ^
    //                         |
    //                    +--------+
    //                    | Source |
    //                    +--------+
    //
    // If not, we'll need to indicate a shared edge. This is the edge that separate
    // them (but this will require me to evaluate margins!)

    // Compute overlap region and pick the middle
    int sharedY = targetSegmentTypeY == SegmentType.UNKNOWN ? sourceY : targetSegmentTypeY.getY(targetNode, targetBounds);
    if (type.relativeToMargin) {
      if (targetSegmentTypeY == SegmentType.TOP) {
        sharedY -= targetMargins.top;
      }
      else if (targetSegmentTypeY == SegmentType.BOTTOM) {
        sharedY += targetMargins.bottom;
      }
    }

    int startX;
    int endX;
    if (center <= sourceBounds.x) {
      startX = targetBounds.x + targetBounds.width / 4;
      endX = x2(sourceBounds);
    }
    else {
      assert (center >= x2(sourceBounds));
      startX = sourceBounds.x;
      endX = targetBounds.x + 3 * targetBounds.width / 4;
    }
    // Must draw segmented line instead
    // Place the arrow 1/4 instead of 1/2 in the source to avoid overlapping with the
    // selection handles
    graphics.useStyle(GUIDELINE_DASHED);
    graphics.drawLine(startX, sharedY, endX, sharedY);

    // Adjust position of source arrow such that it does not sit across edge; it
    // should point directly at the edge
    if (Math.abs(sharedY - sourceY) < 2 * ARROW_SIZE) {
      if (sourceSegmentTypeY == SegmentType.BASELINE) {
        sourceY = sharedY - 2 * ARROW_SIZE;
      }
      else if (sourceSegmentTypeY == SegmentType.TOP) {
        sharedY = sourceY;
        sourceY = sharedY + 2 * ARROW_SIZE;
      }
      else {
        sharedY = sourceY;
        sourceY = sharedY - 2 * ARROW_SIZE;
      }
    }

    graphics.useStyle(GUIDELINE);

    // Draw the line from the source anchor to the shared edge
    int x = sourceBounds.x + ((sourceSegmentTypeY == SegmentType.BASELINE) ? sourceBounds.width / 2 : sourceBounds.width / 4);
    graphics.drawArrow(x, sourceY, x, sharedY);

    // Draw the line from the target to the horizontal shared edge
    int tx = centerX(targetBounds);
    if (targetSegmentTypeY == SegmentType.TOP) {
      int ty = targetBounds.y;
      int margin = targetMargins.top;
      if (margin == 0 || !type.relativeToMargin) {
        graphics.drawArrow(tx, ty + 2 * ARROW_SIZE, tx, ty);
      }
      else {
        graphics.drawArrow(tx, ty, tx, ty - margin);
      }
    }
    else if (targetSegmentTypeY == SegmentType.BOTTOM) {
      int ty = y2(targetBounds);
      int margin = targetMargins.bottom;
      if (margin == 0 || !type.relativeToMargin) {
        graphics.drawArrow(tx, ty - 2 * ARROW_SIZE, tx, ty);
      }
      else {
        graphics.drawArrow(tx, ty, tx, ty + margin);
      }
    }
    else {
      assert targetSegmentTypeY == SegmentType.BASELINE : targetSegmentTypeY;
      int ty = targetSegmentTypeY.getY(targetNode, targetBounds);
      graphics.drawArrow(tx, ty - 2 * ARROW_SIZE, tx, ty);
    }
  }

  /**
   * Paints a horizontal constraint, handling the various scenarios where there are margins,
   * or where the two nodes overlap horizontally and where they don't, etc.
   */
  private static void paintHorizontalConstraint(ScreenView screenView,
                                                NlGraphics graphics,
                                                ConstraintType type,
                                                NlComponent sourceNode,
                                                Rectangle sourceBounds,
                                                NlComponent targetNode,
                                                Rectangle targetBounds,
                                                boolean highlightTargetEdge,
                                                TextDirection textDirection) {
    SegmentType sourceSegmentTypeX = type.sourceSegmentTypeX;
    SegmentType targetSegmentTypeX = type.targetSegmentTypeX;
    Insets targetMargins = getInsets(screenView, targetNode.getMargins());

    assert sourceSegmentTypeX != SegmentType.UNKNOWN;
    assert targetBounds != null;

    // See paintVerticalConstraint for explanations of the various cases.

    int sourceX = sourceSegmentTypeX.getX(textDirection, sourceNode, sourceBounds);
    int targetX = targetSegmentTypeX == SegmentType.UNKNOWN ? sourceX : targetSegmentTypeX.getX(textDirection, targetNode, targetBounds);

    if (highlightTargetEdge && type.isRelativeToParentEdge()) {
      graphics.useStyle(DROP_ZONE_ACTIVE);
      graphics.fillRect(targetX - PARENT_RECT_SIZE / 2, targetBounds.y, PARENT_RECT_SIZE, targetBounds.height);
    }

    int maxTop = Math.max(sourceBounds.y, targetBounds.y);
    int minBottom = Math.min(y2(sourceBounds), y2(targetBounds));

    // First see if the two views overlap vertically. If so, we can just draw a direct
    // arrow from the source over to the target.
    int center = (maxTop + minBottom) / 2;
    if (center > sourceBounds.y && center < y2(sourceBounds)) {
      // See if we should draw a margin line
      if (textDirection.isRightSegment(targetSegmentTypeX) && targetMargins.right > 5) {
        int sharedX = targetX + targetMargins.right;
        if (sourceX > sharedX + 2) { // Skip when source falls on the margin line
          graphics.useStyle(GUIDELINE_DASHED);
          graphics.drawLine(sharedX, targetBounds.y, sharedX, y2(targetBounds));
          graphics.useStyle(GUIDELINE);
          graphics.drawArrow(sourceX, center, sharedX + 2, center);
          graphics.drawArrow(targetX, center, sharedX - 3, center);
        }
        else {
          graphics.useStyle(GUIDELINE);
          // Draw reverse arrow to make it clear the node is as close
          // at it can be
          graphics.drawArrow(targetX, center, sourceX, center);
        }
        return;
      }
      else if (textDirection.isLeftSegment(targetSegmentTypeX) && targetMargins.left > 5) {
        int sharedX = targetX - targetMargins.left;
        if (sourceX < sharedX - 2) {
          graphics.useStyle(GUIDELINE_DASHED);
          graphics.drawLine(sharedX, targetBounds.y, sharedX, y2(targetBounds));
          graphics.useStyle(GUIDELINE);
          graphics.drawArrow(sourceX, center, sharedX - 3, center);
          graphics.drawArrow(targetX, center, sharedX + 3, center);
        }
        else {
          graphics.useStyle(GUIDELINE);
          graphics.drawArrow(targetX, center, sourceX, center);
        }
        return;
      }

      if (sourceX == targetX) {
        if (textDirection.isRightSegment(sourceSegmentTypeX)) {
          sourceX -= 2 * ARROW_SIZE;
        }
        else if (textDirection.isLeftSegment(sourceSegmentTypeX)) {
          sourceX += 2 * ARROW_SIZE;
        }
        else {
          assert sourceSegmentTypeX == SegmentType.CENTER_VERTICAL : sourceSegmentTypeX;
          sourceX += sourceBounds.width / 2 - 2 * ARROW_SIZE;
        }
      }

      graphics.useStyle(GUIDELINE);
      graphics.drawArrow(sourceX, center, targetX, center);
      return;
    }

    // Segment line

    // Compute overlap region and pick the middle
    int sharedX = targetSegmentTypeX == SegmentType.UNKNOWN ? sourceX : targetSegmentTypeX.getX(textDirection, targetNode, targetBounds);
    if (type.relativeToMargin) {
      if (textDirection.isLeftSegment(targetSegmentTypeX)) {
        sharedX -= targetMargins.left;
      }
      else if (textDirection.isRightSegment(targetSegmentTypeX)) {
        sharedX += targetMargins.right;
      }
    }

    int startY, endY;
    if (center <= sourceBounds.y) {
      startY = targetBounds.y + targetBounds.height / 4;
      endY = y2(sourceBounds);
    }
    else {
      assert (center >= y2(sourceBounds));
      startY = sourceBounds.y;
      endY = targetBounds.y + 3 * targetBounds.height / 2;
    }

    // Must draw segmented line instead
    // Place at 1/4 instead of 1/2 to avoid overlapping with selection handles
    int y = sourceBounds.y + sourceBounds.height / 4;
    graphics.useStyle(GUIDELINE_DASHED);
    graphics.drawLine(sharedX, startY, sharedX, endY);

    // Adjust position of source arrow such that it does not sit across edge; it
    // should point directly at the edge
    if (Math.abs(sharedX - sourceX) < 2 * ARROW_SIZE) {
      if (textDirection.isLeftSegment(sourceSegmentTypeX)) {
        sharedX = sourceX;
        sourceX = sharedX + 2 * ARROW_SIZE;
      }
      else {
        sharedX = sourceX;
        sourceX = sharedX - 2 * ARROW_SIZE;
      }
    }

    graphics.useStyle(GUIDELINE);

    // Draw the line from the source anchor to the shared edge
    graphics.drawArrow(sourceX, y, sharedX, y);

    // Draw the line from the target to the horizontal shared edge
    int ty = centerY(targetBounds);
    if (textDirection.isLeftSegment(targetSegmentTypeX)) {
      int tx = targetBounds.x;
      int margin = targetMargins.left;
      if (margin == 0 || !type.relativeToMargin) {
        graphics.drawArrow(tx + 2 * ARROW_SIZE, ty, tx, ty);
      }
      else {
        graphics.drawArrow(tx, ty, tx - margin, ty);
      }
    }
    else {
      assert textDirection.isRightSegment(targetSegmentTypeX);
      int tx = x2(targetBounds);
      int margin = targetMargins.right;
      if (margin == 0 || !type.relativeToMargin) {
        graphics.drawArrow(tx - 2 * ARROW_SIZE, ty, tx, ty);
      }
      else {
        graphics.drawArrow(tx, ty, tx + margin, ty);
      }
    }
  }

  /**
   * Paints a vertical center constraint. The constraint is shown as a dashed line
   * through the vertical view, and a solid line over the node bounds.
   */
  private static void paintVerticalCenterConstraint(NlGraphics graphics, Rectangle sourceBounds, Rectangle targetBounds) {
    graphics.useStyle(GUIDELINE_DASHED);
    graphics.drawLine(targetBounds.x, centerY(targetBounds), x2(targetBounds), centerY(targetBounds));
    graphics.useStyle(GUIDELINE);
    graphics.drawLine(sourceBounds.x, centerY(sourceBounds), x2(sourceBounds), centerY(sourceBounds));
  }

  /**
   * Paints a horizontal center constraint. The constraint is shown as a dashed line
   * through the horizontal view, and a solid line over the node bounds.
   */
  private static void paintHorizontalCenterConstraint(NlGraphics graphics, Rectangle sourceBounds, Rectangle targetBounds) {
    graphics.useStyle(GUIDELINE_DASHED);
    graphics.drawLine(
      centerX(targetBounds), targetBounds.y, centerX(targetBounds), y2(targetBounds));
    graphics.useStyle(GUIDELINE);
    graphics.drawLine(centerX(sourceBounds), sourceBounds.y, centerX(sourceBounds),
                      y2(sourceBounds));
  }

  /**
   * Data structure about relative layout relationships which makes it possible to:
   * <ul>
   * <li> Quickly determine not just the dependencies on other nodes, but which nodes
   * depend on this node such that they can be visualized for the selection
   * <li> Determine if there are cyclic dependencies, and whether a potential move
   * would result in a cycle
   * <li> Determine the "depth" of a given node (in terms of how many connections it
   * is away from a parent edge) such that we can prioritize connections which
   * minimizes the depth
   * </ul>
   */
  public static class DependencyGraph {
    @NonNls private static final String KEY = "DependencyGraph";

    private final Map<NlComponent, ViewData> myNodeToView = new HashMap<NlComponent, ViewData>();

    /**
     * Returns the {@link DependencyGraph} for the given relative layout widget
     *
     * @param layout the relative layout
     * @return a {@link DependencyGraph} for the layout
     */
    @NotNull
    public static DependencyGraph get(@NotNull NlComponent layout) {
      // TODO: Cache
      //DependencyGraph graph = layout.getClientProperty(KEY);
      //if (graph == null) {
      //  graph = new DependencyGraph(layout);
      //  layout.setClientProperty(KEY, graph);
      //}
      //return graph;
      return new DependencyGraph(layout);
    }

    /**
     * Constructs a new {@link DependencyGraph} for the given relative layout
     */
    private DependencyGraph(NlComponent layout) {
      // Parent view:
      String parentId = layout.getId();
      if (parentId != null) {
        parentId = LintUtils.stripIdPrefix(parentId);
      }
      else {
        parentId = "RelativeLayout"; // For display purposes; we never reference
        // the parent id from a constraint, only via parent-relative params
        // like centerInParent
      }
      ViewData parentView = new ViewData(layout, parentId);
      myNodeToView.put(layout, parentView);
      Map<String, ViewData> idToView = new HashMap<String, ViewData>();
      idToView.put(parentId, parentView);

      for (NlComponent child : layout.getChildren()) {
        String id = child.getId();
        if (id != null) {
          id = LintUtils.stripIdPrefix(id);
        }
        ViewData view = new ViewData(child, id);
        myNodeToView.put(child, view);
        if (id != null) {
          idToView.put(id, view);
        }
      }

      for (ViewData view : myNodeToView.values()) {
        for (XmlAttribute attribute : view.node.tag.getAttributes()) {
          String name = attribute.getLocalName();
          ConstraintType type = ConstraintType.fromAttribute(name);
          if (type != null) {
            String value = attribute.getValue();

            if (type.targetParent) {
              if (VALUE_TRUE.equals(value)) {
                Constraint constraint = new Constraint(type, view, parentView);
                view.dependsOn.add(constraint);
                parentView.dependedOnBy.add(constraint);
              }
            }
            else {
              // id-based constraint.
              // NOTE: The id could refer to some widget that is NOT a sibling!
              String targetId = LintUtils.stripIdPrefix(value);
              ViewData target = idToView.get(targetId);
              //noinspection StatementWithEmptyBody
              if (target != view) {
                if (target != null) {
                  Constraint constraint = new Constraint(type, view, target);
                  view.dependsOn.add(constraint);
                  target.dependedOnBy.add(constraint);
                }
              }
              else {
                // Self-reference. RelativeLayout ignores these so it's
                // not an error like a deeper cycle (where RelativeLayout
                // will throw an exception), but we might as well warn
                // the user about it.
                // TODO: Where do we emit this error?
              }
            }
          }
        }
      }
    }

    public ViewData getView(NlComponent node) {
      return myNodeToView.get(node);
    }

    /**
     * Returns the set of views that depend on the given node in either the horizontal or
     * vertical direction
     *
     * @param nodes    the set of nodes that we want to compute the transitive dependencies
     *                 for
     * @param vertical if true, look for vertical edge dependencies, otherwise look for
     *                 horizontal edge dependencies
     * @return the set of nodes that directly or indirectly depend on the given nodes in
     * the given direction
     */
    public Set<NlComponent> dependsOn(Collection<? extends NlComponent> nodes, boolean vertical) {
      java.util.List<ViewData> reachable = new ArrayList<ViewData>();

      // Traverse the graph of constraints and determine all nodes affected by
      // this node
      Set<ViewData> visiting = new HashSet<ViewData>();
      for (NlComponent node : nodes) {
        ViewData view = myNodeToView.get(node);
        if (view != null) {
          findBackwards(view, visiting, reachable, vertical);
        }
      }

      Set<NlComponent> dependents = new HashSet<NlComponent>(reachable.size());

      for (ViewData v : reachable) {
        dependents.add(v.node);
      }

      return dependents;
    }

    private static void findBackwards(ViewData view, Set<ViewData> visiting, List<ViewData> reachable, boolean vertical) {
      visiting.add(view);
      reachable.add(view);

      for (Constraint constraint : view.dependedOnBy) {
        if (vertical && !constraint.type.verticalEdge || !vertical && !constraint.type.horizontalEdge) {
          continue;
        }

        assert constraint.to == view;
        ViewData from = constraint.from;
        if (!visiting.contains(from)) {
          findBackwards(from, visiting, reachable, vertical);
        }
      }

      visiting.remove(view);
    }

    /**
     * Info about a specific widget child of a relative layout and its constraints. This
     * is a node in the dependency graph.
     */
    static class ViewData {
      @NotNull public final NlComponent node;
      @Nullable public final String id;
      @NotNull public final java.util.List<Constraint> dependsOn = new ArrayList<Constraint>(4);
      @NotNull public final java.util.List<Constraint> dependedOnBy = new ArrayList<Constraint>(8);

      ViewData(@NotNull NlComponent node, @Nullable String id) {
        this.node = node;
        this.id = id;
      }
    }

    /**
     * Info about a specific constraint between two widgets in a relative layout. This is
     * an edge in the dependency graph.
     */
    static class Constraint {
      @NotNull public final ConstraintType type;
      public final ViewData from;
      public final ViewData to;

      Constraint(@NotNull ConstraintType type, @NotNull ViewData from, @NotNull ViewData to) {
        this.type = type;
        this.from = from;
        this.to = to;
      }
    }
  }

  /**
   * Each constraint type corresponds to a type of constraint available for the
   * RelativeLayout; for example, {@link #LAYOUT_ABOVE} corresponds to the layout_above constraint.
   */
  private enum ConstraintType {
    LAYOUT_ABOVE(ATTR_LAYOUT_ABOVE, null /* sourceX */, SegmentType.BOTTOM, null /* targetX */, SegmentType.TOP, false /* targetParent */,
                 true /* horizontalEdge */, false /* verticalEdge */, true /* relativeToMargin */),

    LAYOUT_BELOW(ATTR_LAYOUT_BELOW, null, SegmentType.TOP, null, SegmentType.BOTTOM, false, true, false, true),
    ALIGN_TOP(ATTR_LAYOUT_ALIGN_TOP, null, SegmentType.TOP, null, SegmentType.TOP, false, true, false, false),
    ALIGN_BOTTOM(ATTR_LAYOUT_ALIGN_BOTTOM, null, SegmentType.BOTTOM, null, SegmentType.BOTTOM, false, true, false, false),
    ALIGN_LEFT(ATTR_LAYOUT_ALIGN_LEFT, SegmentType.LEFT, null, SegmentType.LEFT, null, false, false, true, false),
    ALIGN_RIGHT(ATTR_LAYOUT_ALIGN_RIGHT, SegmentType.RIGHT, null, SegmentType.RIGHT, null, false, false, true, false),
    LAYOUT_ALIGN_START(ATTR_LAYOUT_ALIGN_START, SegmentType.START, null, SegmentType.START, null, false, false, true, false),
    LAYOUT_ALIGN_END(ATTR_LAYOUT_ALIGN_END, SegmentType.END, null, SegmentType.END, null, false, false, true, false),
    LAYOUT_LEFT_OF(ATTR_LAYOUT_TO_LEFT_OF, SegmentType.RIGHT, null, SegmentType.LEFT, null, false, false, true, true),
    LAYOUT_RIGHT_OF(ATTR_LAYOUT_TO_RIGHT_OF, SegmentType.LEFT, null, SegmentType.RIGHT, null, false, false, true, true),
    LAYOUT_ALIGN_START_OF(ATTR_LAYOUT_TO_START_OF, SegmentType.START, null, SegmentType.START, null, false, false, true, false),
    LAYOUT_ALIGN_END_OF(ATTR_LAYOUT_TO_END_OF, SegmentType.END, null, SegmentType.END, null, false, false, true, false),
    ALIGN_PARENT_TOP(ATTR_LAYOUT_ALIGN_PARENT_TOP, null, SegmentType.TOP, null, SegmentType.TOP, true, true, false, false),
    ALIGN_BASELINE(ATTR_LAYOUT_ALIGN_BASELINE, null, SegmentType.BASELINE, null, SegmentType.BASELINE, false, true, false, false),
    ALIGN_PARENT_LEFT(ATTR_LAYOUT_ALIGN_PARENT_LEFT, SegmentType.LEFT, null, SegmentType.LEFT, null, true, false, true, false),
    ALIGN_PARENT_RIGHT(ATTR_LAYOUT_ALIGN_PARENT_RIGHT, SegmentType.RIGHT, null, SegmentType.RIGHT, null, true, false, true, false),
    ALIGN_PARENT_BOTTOM(ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, null, SegmentType.BOTTOM, null, SegmentType.BOTTOM, true, true, false, false),
    LAYOUT_CENTER_HORIZONTAL(ATTR_LAYOUT_CENTER_HORIZONTAL, SegmentType.CENTER_VERTICAL, null, SegmentType.CENTER_VERTICAL, null, true, true, false, false),
    LAYOUT_CENTER_VERTICAL(ATTR_LAYOUT_CENTER_VERTICAL, null, SegmentType.CENTER_HORIZONTAL, null, SegmentType.CENTER_HORIZONTAL, true,
                           false, true, false),
    LAYOUT_CENTER_IN_PARENT(ATTR_LAYOUT_CENTER_IN_PARENT, SegmentType.CENTER_VERTICAL, SegmentType.CENTER_HORIZONTAL, SegmentType.CENTER_VERTICAL, SegmentType.CENTER_HORIZONTAL, true, true,
                            true, false);

    ConstraintType(String name,
                   SegmentType sourceSegmentTypeX,
                   SegmentType sourceSegmentTypeY,
                   SegmentType targetSegmentTypeX,
                   SegmentType targetSegmentTypeY,
                   boolean targetParent,
                   boolean horizontalEdge,
                   boolean verticalEdge,
                   boolean relativeToMargin) {
      assert horizontalEdge || verticalEdge;

      this.name = name;
      this.sourceSegmentTypeX = sourceSegmentTypeX != null ? sourceSegmentTypeX : SegmentType.UNKNOWN;
      this.sourceSegmentTypeY = sourceSegmentTypeY != null ? sourceSegmentTypeY : SegmentType.UNKNOWN;
      this.targetSegmentTypeX = targetSegmentTypeX != null ? targetSegmentTypeX : SegmentType.UNKNOWN;
      this.targetSegmentTypeY = targetSegmentTypeY != null ? targetSegmentTypeY : SegmentType.UNKNOWN;
      this.targetParent = targetParent;
      this.horizontalEdge = horizontalEdge;
      this.verticalEdge = verticalEdge;
      this.relativeToMargin = relativeToMargin;
    }

    /**
     * The attribute name of the constraint
     */
    public final String name;

    /**
     * The horizontal position of the source of the constraint
     */
    public final SegmentType sourceSegmentTypeX;

    /**
     * The vertical position of the source of the constraint
     */
    public final SegmentType sourceSegmentTypeY;

    /**
     * The horizontal position of the target of the constraint
     */
    public final SegmentType targetSegmentTypeX;

    /**
     * The vertical position of the target of the constraint
     */
    public final SegmentType targetSegmentTypeY;

    /**
     * If true, the constraint targets the parent layout, otherwise it targets another
     * view
     */
    public final boolean targetParent;

    /**
     * If true, this constraint affects the horizontal dimension
     */
    public final boolean horizontalEdge;

    /**
     * If true, this constraint affects the vertical dimension
     */
    public final boolean verticalEdge;

    /**
     * Whether this constraint is relative to the margin bounds of the node rather than
     * the node's actual bounds
     */
    public final boolean relativeToMargin;

    /**
     * Map from attribute name to constraint type
     */
    private static Map<String, ConstraintType> ourSNameToType;

    /**
     * Returns the {@link ConstraintType} corresponding to the given attribute name, or
     * null if not found.
     *
     * @param attribute the name of the attribute to look up
     * @return the corresponding {@link ConstraintType}
     */
    @Nullable
    public static ConstraintType fromAttribute(@NotNull String attribute) {
      if (ourSNameToType == null) {
        ConstraintType[] types = ConstraintType.values();
        Map<String, ConstraintType> map = new HashMap<String, ConstraintType>(types.length);
        for (ConstraintType type : types) {
          map.put(type.name, type);
        }
        ourSNameToType = map;
      }
      return ourSNameToType.get(attribute);
    }

    /**
     * Returns true if this constraint type represents a constraint where the target edge
     * is one of the parent edges (actual edge, not center/baseline segments)
     *
     * @return true if the target segment is a parent edge
     */
    public boolean isRelativeToParentEdge() {
      return this == ALIGN_PARENT_LEFT || this == ALIGN_PARENT_RIGHT || this == ALIGN_PARENT_TOP || this == ALIGN_PARENT_BOTTOM;
    }
  }

  private static int centerX(Rectangle rectangle) {
    return rectangle.x + rectangle.width / 2;
  }

  private static int centerY(Rectangle rectangle) {
    return rectangle.y + rectangle.height / 2;
  }

  private static int x2(Rectangle rectangle) {
    return rectangle.x + rectangle.width;
  }

  private static int y2(Rectangle rectangle) {
    return rectangle.y + rectangle.height;
  }
}
