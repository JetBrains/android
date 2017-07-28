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
package com.android.tools.idea.uibuilder.handlers.common;

import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link DragHandler} for layouts with a linear layout of children.
 */
public class GenericLinearDragHandler extends DragHandler {
  /**
   * Vertical layout?
   */
  private final boolean myVertical;

  /**
   * Insert points (dp + index)
   */
  private final List<MatchPos> myIndices;

  /**
   * Number of insert positions in the target node
   */
  private final int myNumPositions;

  /**
   * Current marker X position
   */
  @AndroidDpCoordinate
  private Integer myCurrX;

  /**
   * Current marker Y position
   */
  @AndroidDpCoordinate
  private Integer myCurrY;

  /**
   * Position of the dragged element in this layout (or
   * -1 if the dragged element is from elsewhere)
   */
  private int mySelfPos;

  /**
   * Current drop insert index (-1 for "at the end")
   */
  private int myInsertPos = -1;

  /**
   * width of match line if it's a horizontal one
   */
  @AndroidDpCoordinate
  private Integer myWidth;

  /**
   * height of match line if it's a vertical one
   */
  @AndroidDpCoordinate
  private Integer myHeight;

  private SceneComponent myComponent;


  public GenericLinearDragHandler(@NotNull ViewEditor editor,
                                  @NotNull SceneComponent layout,
                                  @NotNull List<NlComponent> components,
                                  @NotNull DragType type,
                                  @NotNull ViewGroupHandler viewGroupHandler,
                                  boolean isVertical) {
    super(editor, viewGroupHandler, layout, components, type);
    assert !components.isEmpty();

    myVertical = isVertical;

    // Prepare a list of insertion points: X coordinates for horizontal, Y for
    // vertical.
    myIndices = new ArrayList<>();

    @AndroidDpCoordinate
    int last = myVertical
               ? layout.getDrawY() + editor.pxToDp(NlComponentHelperKt.getPadding(layout.getNlComponent()).top)
               : layout.getDrawX() + editor.pxToDp(NlComponentHelperKt.getPadding(layout.getNlComponent()).left);
    int pos = 0;
    boolean lastDragged = false;
    mySelfPos = -1;
    if (myVertical) {
      layout.getChildren().sort((c1, c2) -> c1.getDrawY() - c2.getDrawY());
    }
    else {
      layout.getChildren().sort((c1, c2) -> c1.getDrawX() - c2.getDrawX());
    }
    for (SceneComponent it : layout.getChildren()) {
      if (it.getDrawWidth() > 0 && it.getDrawHeight() > 0) {
        boolean isDragged = components.contains(it.getNlComponent());

        // We don't want to insert drag positions before or after the
        // element that is itself being dragged. However, we -do- want
        // to insert a match position here, at the center, such that
        // when you drag near its current position we show a match right
        // where it's already positioned.
        if (isDragged) {
          @AndroidDpCoordinate int v = myVertical ? it.getDrawY() + (it.getDrawHeight() / 2) : it.getDrawX() + (it.getDrawWidth() / 2);
          mySelfPos = pos;
          myIndices.add(new MatchPos(v, pos++));
        }
        else if (lastDragged) {
          // Even though we don't want to insert a match below, we
          // need to increment the index counter such that subsequent
          // lines know their correct index in the child list.
          pos++;
        }
        else {
          // Add an insertion point between the last point and the
          // start of this child
          @AndroidDpCoordinate int v = myVertical ? it.getDrawY() : it.getDrawX();
          v = (last + v) / 2;
          myIndices.add(new MatchPos(v, pos++));
        }

        last = myVertical ? (it.getDrawY() + it.getDrawHeight()) : (it.getDrawX() + it.getDrawWidth());
        lastDragged = isDragged;
      }
      else {
        // We still have to count this position even if it has no bounds, or
        // subsequent children will be inserted at the wrong place
        pos++;
      }
    }

    // Finally add an insert position after all the children - unless of
    // course we happened to be dragging the last element
    if (!lastDragged) {
      @AndroidDpCoordinate int v = last + 1;
      myIndices.add(new MatchPos(v, pos));
    }

    myNumPositions = layout.getChildCount() + 1;
    NlComponent component = components.get(0);
    myComponent = new TemporarySceneComponent(layout.getScene(), component);
    myComponent.setSize(editor.pxToDp(NlComponentHelperKt.getW(component)), editor.pxToDp(NlComponentHelperKt.getH(component)), false);
  }

  @Nullable
  @Override
  public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @JdkConstants.InputEventMask int modifiers) {
    super.update(x, y, modifiers);

    boolean isVertical = myVertical;

    @AndroidDpCoordinate int bestDist = Integer.MAX_VALUE;
    int bestIndex = Integer.MIN_VALUE;
    Integer bestPos = null;

    for (MatchPos index : myIndices) {
      @AndroidDpCoordinate int i = index.getDistance();
      int pos = index.getPosition();
      @AndroidDpCoordinate int dist = (isVertical ? y : x) - i;
      if (dist < 0) {
        dist = -dist;
      }
      if (dist < bestDist) {
        bestDist = dist;
        bestIndex = i;
        bestPos = pos;
        if (bestDist <= 0) {
          break;
        }
      }
    }

    if (bestIndex != Integer.MIN_VALUE) {
      if (isVertical) {
        myCurrX = layout.getDrawX() + layout.getDrawWidth() / 2;
        myCurrY = bestIndex;
        myWidth = layout.getDrawWidth();
        myHeight = null;
      }
      else {
        myCurrX = bestIndex;
        myCurrY = layout.getDrawY() + layout.getDrawHeight() / 2;
        myWidth = null;
        myHeight = layout.getDrawHeight();
      }

      myInsertPos = bestPos;
    }

    return null;
  }

  @Override
  public void paint(@NotNull NlGraphics gc) {
    @AndroidCoordinate Insets padding = NlComponentHelperKt.getPadding(layout.getNlComponent());
    @AndroidDpCoordinate int layoutX = layout.getDrawX() + editor.pxToDp(padding.left);
    @AndroidDpCoordinate int layoutW = layout.getDrawWidth() - editor.pxToDp(padding.width());
    @AndroidDpCoordinate int layoutY = layout.getDrawY() + editor.pxToDp(padding.top);
    @AndroidDpCoordinate int layoutH = layout.getDrawHeight() - editor.pxToDp(padding.height());

    // Highlight the receiver
    gc.useStyle(NlDrawingStyle.DROP_RECIPIENT);
    gc.drawRectDp(layoutX, layoutY, layoutW, layoutH);

    gc.useStyle(NlDrawingStyle.DROP_ZONE);

    boolean isVertical = myVertical;
    int selfPos = mySelfPos;

    for (MatchPos it : myIndices) {
      @AndroidDpCoordinate int i = it.getDistance();
      int pos = it.getPosition();
      // Don't show insert drop zones for "self"-index since that one goes
      // right through the center of the widget rather than in a sibling
      // position
      if (pos != selfPos) {
        if (isVertical) {
          // draw horizontal lines
          gc.drawLineDp(layoutX, i, layoutX + layoutW, i);
        }
        else {
          // draw vertical lines
          gc.drawLineDp(i, layoutY, i, layoutY + layoutH);
        }
      }
    }

    @AndroidDpCoordinate Integer currX = myCurrX;
    @AndroidDpCoordinate Integer currY = myCurrY;

    if (currX != null && currY != null) {
      gc.useStyle(NlDrawingStyle.DROP_ZONE_ACTIVE);

      @AndroidDpCoordinate int x = currX;
      @AndroidDpCoordinate int y = currY;

      // Draw a clear line at the closest drop zone (unless we're over the
      // dragged element itself)
      if (myInsertPos != selfPos || selfPos == -1) {
        gc.useStyle(NlDrawingStyle.DROP_PREVIEW);
        if (myWidth != null) {
          @AndroidDpCoordinate int width = myWidth;
          @AndroidDpCoordinate int fromX = x - width / 2;
          @AndroidDpCoordinate int toX = x + width / 2;
          gc.drawLineDp(fromX, y, toX, y);
        }
        else if (myHeight != null) {
          @AndroidDpCoordinate int height = myHeight;
          @AndroidDpCoordinate int fromY = y - height / 2;
          @AndroidDpCoordinate int toY = y + height / 2;
          gc.drawLineDp(x, fromY, x, toY);
        }
      }

      SceneComponent be = myComponent;
      if (be.getDrawWidth() > 0 && be.getDrawHeight() > 0) {
        boolean isLast = myInsertPos == myNumPositions - 1;

        // At least the first element has a bound. Draw rectangles for
        // all dropped elements with valid bounds, offset at the drop
        // point.
        @AndroidDpCoordinate int offsetX;
        @AndroidDpCoordinate int offsetY;
        if (isVertical) {
          offsetX = layoutX - be.getDrawX();
          offsetY = currY - be.getDrawY() - (isLast ? 0 : (be.getDrawHeight() / 2));
        }
        else {
          offsetX = currX - be.getDrawX() - (isLast ? 0 : (be.getDrawWidth() / 2));
          offsetY = layoutY - be.getDrawY();
        }

        gc.useStyle(NlDrawingStyle.DROP_ZONE_ACTIVE);
        for (NlComponent nlComponent : components) {
          SceneComponent element = layout.getSceneComponent(nlComponent);
          if (nlComponent == myComponent.getNlComponent()) {
            element = myComponent;
          }
          if (element == null) {
            continue;
          }
          if (element.getDrawWidth() > 0 && element.getDrawHeight() > 0 &&
              (element.getDrawWidth() > layoutW || element.getDrawHeight() > layoutH) &&
              layout.getChildCount() == 0) {
            // The bounds of the child does not fully fit inside the target.
            // Limit the bounds to the layout bounds (but only when there
            // are no children, since otherwise positioning around the existing
            // children gets difficult)
            @AndroidDpCoordinate final int px, py, pw, ph;
            if (element.getDrawWidth() > layoutW) {
              px = layoutX;
              pw = layoutW;
            }
            else {
              px = element.getDrawX() + offsetX;
              pw = element.getDrawWidth();
            }
            if (element.getDrawHeight() > layoutH) {
              py = layoutY;
              ph = layoutH;
            }
            else {
              py = element.getDrawY() + offsetY;
              ph = element.getDrawHeight();
            }
            gc.drawRectDp(px, py, pw, ph);
          }
          else {
            drawElement(gc, element, editor.dpToPx(offsetX), editor.dpToPx(offsetY));
          }
        }
      }
    }
  }

  /**
   * Draws the bounds of the given elements and all its children elements in the canvas
   * with the specified offset.
   *
   * @param gc        the graphics context
   * @param component the element to be drawn
   * @param offsetX   a horizontal delta to add to the current bounds of the element when
   *                  drawing it
   * @param offsetY   a vertical delta to add to the current bounds of the element when
   *                  drawing it
   */
  public void drawElement(NlGraphics gc, SceneComponent component, @AndroidCoordinate int offsetX, @AndroidCoordinate int offsetY) {
    int w = editor.dpToPx(component.getDrawWidth());
    int h = editor.dpToPx(component.getDrawHeight());
    if (w > 0 && h > 0) {
      gc.fillRect(offsetX, offsetY, w, h);
      gc.drawRect(offsetX, offsetY, w, h);
    }

    for (SceneComponent inner : component.getChildren()) {
      drawElement(gc, inner, offsetX, offsetY);
    }
  }

  @Override
  public void cancel() {
    Scene scene = ((ViewEditorImpl)editor).getSceneView().getScene();
    scene.removeComponent(myComponent);
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
    insertComponents(myInsertPos, insertType);
    Scene scene = ((ViewEditorImpl)editor).getSceneView().getScene();
    scene.removeComponent(myComponent);
  }
}
