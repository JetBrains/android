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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.Segment;
import com.android.tools.idea.uibuilder.model.SegmentType;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Collections;
import java.util.Set;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.MAX_MATCH_DISTANCE;
import static com.android.tools.idea.uibuilder.model.MarginType.NO_MARGIN;
import static java.lang.Math.abs;

/**
 * A {@link RelativeResizeHandler} is a {@link GuidelineHandler} which handles resizing of individual
 * edges in a RelativeLayout.
 */
public class RelativeResizeHandler extends GuidelineHandler {
  @Nullable private final SegmentType myHorizontalEdgeType;
  @Nullable private final SegmentType myVerticalEdgeType;

  /**
   * Creates a new {@link RelativeResizeHandler}
   *
   * @param viewEditor         the editor performing this editing operation
   * @param layout             the layout containing the resized node
   * @param resized            the node being resized
   * @param horizontalEdgeType the type of horizontal edge being resized, or null
   * @param verticalEdgeType   the type of vertical edge being resized, or null
   */
  public RelativeResizeHandler(@NotNull ViewEditor viewEditor,
                               @NotNull NlComponent layout,
                               @NotNull NlComponent resized,
                               @Nullable SegmentType horizontalEdgeType,
                               @Nullable SegmentType verticalEdgeType) {
    super(viewEditor, layout);

    assert horizontalEdgeType != null || verticalEdgeType != null;
    assert horizontalEdgeType != SegmentType.BASELINE && verticalEdgeType != SegmentType.BASELINE;
    assert horizontalEdgeType != SegmentType.CENTER_HORIZONTAL && verticalEdgeType != SegmentType.CENTER_HORIZONTAL;
    assert horizontalEdgeType != SegmentType.CENTER_VERTICAL && verticalEdgeType != SegmentType.CENTER_VERTICAL;

    myHorizontalEdgeType = horizontalEdgeType;
    myVerticalEdgeType = verticalEdgeType;

    Set<NlComponent> nodes = Collections.singleton(resized);
    myDraggedNodes = nodes;

    myHorizontalDeps = myDependencyGraph.dependsOn(nodes, false /* vertical */);
    myVerticalDeps = myDependencyGraph.dependsOn(nodes, true /* vertical */);

    if (horizontalEdgeType != null) {
      if (horizontalEdgeType == SegmentType.TOP) {
        myMoveTop = true;
      }
      else if (horizontalEdgeType == SegmentType.BOTTOM) {
        myMoveBottom = true;
      }
    }
    if (verticalEdgeType != null) {
      if (myTextDirection.isLeftSegment(verticalEdgeType)) {
        myMoveLeft = true;
      }
      else if (myTextDirection.isRightSegment(verticalEdgeType)) {
        myMoveRight = true;
      }
    }

    for (NlComponent child : layout.getChildren()) {
      if (child != resized && child != null) {
        String id = child.getId();
        addBounds(child, id, !myHorizontalDeps.contains(child), !myVerticalDeps.contains(child), false /*includePadding*/);
      }
    }

    addBounds(layout, layout.getId(), true, true, true /*includePadding*/);
  }

  @Override
  protected void snapVertical(Segment vEdge, int x, Rectangle newBounds) {
    int maxDistance = MAX_MATCH_DISTANCE;
    if (vEdge.edgeType == SegmentType.LEFT) {
      int margin = mySnap ? 0 : abs(newBounds.x - x);
      if (margin > maxDistance) {
        myLeftMargin = margin;
      }
      else {
        newBounds.width += newBounds.x - x;
        newBounds.x = x;
      }
    }
    else if (vEdge.edgeType == SegmentType.RIGHT) {
      int margin = mySnap ? 0 : abs(newBounds.x - (x - newBounds.width));
      if (margin > maxDistance) {
        myRightMargin = margin;
      }
      else {
        newBounds.width = x - newBounds.x;
      }
    }
    else {
      assert false : vEdge;
    }
  }

  @Override
  protected void snapHorizontal(Segment hEdge, int y, Rectangle newBounds) {
    int maxDistance = MAX_MATCH_DISTANCE;
    if (hEdge.edgeType == SegmentType.TOP) {
      int margin = mySnap ? 0 : abs(newBounds.y - y);
      if (margin > maxDistance) {
        myTopMargin = margin;
      }
      else {
        newBounds.height += newBounds.y - y;
        newBounds.y = y;
      }
    }
    else if (hEdge.edgeType == SegmentType.BOTTOM) {
      int margin = mySnap ? 0 : abs(newBounds.y - (y - newBounds.height));
      if (margin > maxDistance) {
        myBottomMargin = margin;
      }
      else {
        newBounds.height = y - newBounds.y;
      }
    }
    else {
      assert false : hEdge;
    }
  }

  @Override
  protected boolean isEdgeTypeCompatible(SegmentType edge, SegmentType dragged, int delta) {
    boolean compatible = super.isEdgeTypeCompatible(edge, dragged, delta);

    // When resizing and not snapping (e.g. using margins to pick a specific pixel
    // width) we cannot use -negative- margins to jump back to a closer edge; we
    // must always use positive margins, so mark closer edges that result in a negative
    // margin as not compatible.
    if (compatible && !mySnap) {
      switch (dragged) {
        case LEFT:
        case TOP:
          return delta <= 0;
        default:
          return delta >= 0;
      }
    }

    return compatible;
  }

  /**
   * Updates the handler for the given mouse resize
   *
   * @param child        the node being resized
   * @param newBounds    the new bounds of the resize rectangle
   * @param modifierMask the keyboard modifiers pressed during the drag
   */
  public void updateResize(NlComponent child, Rectangle newBounds, int modifierMask) {
    mySnap = (modifierMask & InputEvent.SHIFT_MASK) == 0;
    myBounds = newBounds;
    clearSuggestions();

    @SuppressWarnings("UnnecessaryLocalVariable") // To make arithmetic with x/y/w/h below fit on one line
    Rectangle b = newBounds;
    Segment hEdge = null;
    Segment vEdge = null;
    String childId = child.getId();

    // TODO: MarginType=NO_MARGIN may not be right. Consider resizing a widget
    //   that has margins and how that should be handled.

    if (myHorizontalEdgeType == SegmentType.TOP) {
      hEdge = new Segment(b.y, b.x, x2(b), child, childId, myHorizontalEdgeType, NO_MARGIN);
    }
    else if (myHorizontalEdgeType == SegmentType.BOTTOM) {
      hEdge = new Segment(y2(b), b.x, x2(b), child, childId, myHorizontalEdgeType, NO_MARGIN);
    }
    else {
      assert myHorizontalEdgeType == null;
    }

    if (myVerticalEdgeType != null && myTextDirection.isLeftSegment(myVerticalEdgeType)) {
      vEdge = new Segment(b.x, b.y, y2(b), child, childId, myVerticalEdgeType, NO_MARGIN);
    }
    else if (myVerticalEdgeType != null && myTextDirection.isRightSegment(myVerticalEdgeType)) {
      vEdge = new Segment(x2(b), b.y, y2(b), child, childId, myVerticalEdgeType, NO_MARGIN);
    }
    else {
      assert myVerticalEdgeType == null;
    }

    myTopMargin = myBottomMargin = myLeftMargin = myRightMargin = 0;

    if (hEdge != null && myHorizontalEdges.size() > 0) {
      // Compute horizontal matches
      myHorizontalSuggestions = findClosest(hEdge, myHorizontalEdges);

      Match match = pickBestMatch(myHorizontalSuggestions);
      if (match != null && (!mySnap || abs(match.delta) < MAX_MATCH_DISTANCE)) {
        if (myHorizontalDeps.contains(match.edge.component)) {
          match.cycle = true;
        }

        snapHorizontal(hEdge, match.edge.at, newBounds);

        if (hEdge.edgeType == SegmentType.TOP) {
          myCurrentTopMatch = match;
        }
        else if (hEdge.edgeType == SegmentType.BOTTOM) {
          myCurrentBottomMatch = match;
        }
        else {
          assert hEdge.edgeType == SegmentType.CENTER_HORIZONTAL || hEdge.edgeType == SegmentType.BASELINE : hEdge;
          myCurrentTopMatch = match;
        }
      }
    }

    if (vEdge != null && myVerticalEdges.size() > 0) {
      myVerticalSuggestions = findClosest(vEdge, myVerticalEdges);

      Match match = pickBestMatch(myVerticalSuggestions);
      if (match != null && (!mySnap || abs(match.delta) < MAX_MATCH_DISTANCE)) {
        if (myVerticalDeps.contains(match.edge.component)) {
          match.cycle = true;
        }

        // Snap
        snapVertical(vEdge, match.edge.at, newBounds);

        if (myTextDirection.isLeftSegment(vEdge.edgeType)) {
          myCurrentLeftMatch = match;
        }
        else if (myTextDirection.isRightSegment(vEdge.edgeType)) {
          myCurrentRightMatch = match;
        }
        else {
          assert vEdge.edgeType == SegmentType.CENTER_VERTICAL;
          myCurrentLeftMatch = match;
        }
      }
    }

    checkCycles();
  }
}
