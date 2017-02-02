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

import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SegmentType;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Locale;

import static com.android.SdkConstants.*;

/** Handler for the {@code <AbsoluteLayout>} layout */
public class AbsoluteLayoutHandler extends ViewGroupHandler {
  @Override
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<SceneComponent> components,
                                       @NotNull DragType type) {
    return new DragHandler(editor, this, layout, components, type) {
      @Override
      public void paint(@NotNull NlGraphics graphics) {
        @AndroidDpCoordinate int deltaX = lastX - startX;
        @AndroidDpCoordinate int deltaY = lastY - startY;
        for (SceneComponent component : components) {
          int x = editor.dpToPx(component.getDrawX() + deltaX);
          int y = editor.dpToPx(component.getDrawY() + deltaY);
          int w = editor.dpToPx(component.getDrawWidth());
          int h = editor.dpToPx(component.getDrawHeight());

          graphics.useStyle(NlDrawingStyle.DROP_PREVIEW);
          graphics.drawRect(x, y, w, h);
        }
      }

      @Override
      public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
        // TODO: Remove all existing layout parameters; if you're dragging from one layout type to another, you don't
        // want stale layout parameters (e.g. layout_alignLeft from a previous RelativeLayout in a new GridLayout, and so on.)

        @AndroidDpCoordinate int deltaX = editor.pxToDp(x) - startX;
        @AndroidDpCoordinate int deltaY = editor.pxToDp(y) - startY;

        for (SceneComponent component : components) {
          component.getNlComponent().setAttribute(ANDROID_URI, ATTR_LAYOUT_X,
                                                  String.format(Locale.US, VALUE_N_DP, component.getDrawX() - layout.getDrawX() + deltaX));
          component.getNlComponent().setAttribute(ANDROID_URI, ATTR_LAYOUT_Y,
                                                  String.format(Locale.US, VALUE_N_DP, component.getDrawY() - layout.getDrawY() + deltaY));
        }
        insertComponents(-1, insertType);
      }
    };
  }

  @Override
  @Nullable
  public ResizeHandler createResizeHandler(@NotNull ViewEditor editor,
                                           @NotNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    return new DefaultResizeHandler(editor, this, component, horizontalEdgeType, verticalEdgeType) {
      /**
       * {@inheritDoc}
       * <p>
       * Overridden in this layout in order to let the top left coordinate be affected by
       * the resize operation too. In other words, dragging the top left corner to resize a
       * widget will not only change the size of the widget, it will also move it (though in
       * this case, the bottom right corner will stay fixed).
       */
      @Override
      protected void setNewSizeBounds(@NotNull NlComponent component,
                                      @NotNull NlComponent layout,
                                      @NotNull Rectangle oldBounds,
                                      @NotNull Rectangle newBounds,
                                      @Nullable SegmentType horizontalEdge,
                                      @Nullable SegmentType verticalEdge) {
        Rectangle previousBounds = new Rectangle(component.x, component.y, component.w, component.h);
        super.setNewSizeBounds(component, layout, previousBounds, newBounds,
                               horizontalEdge, verticalEdge);
        if (verticalEdge != null && newBounds.x != previousBounds.x) {
          component.setAttribute(ANDROID_URI, ATTR_LAYOUT_X, String.format(VALUE_N_DP, editor.pxToDp(newBounds.x - layout.x)));
        }
        if (horizontalEdge != null && newBounds.y != previousBounds.y) {
          component.setAttribute(ANDROID_URI, ATTR_LAYOUT_Y, String.format(VALUE_N_DP, editor.pxToDp(newBounds.y - layout.y)));
        }
      }

      @Override
      protected String getResizeUpdateMessage(@NotNull NlComponent child,
                                              @NotNull NlComponent parent,
                                              @NotNull Rectangle newBounds,
                                              @Nullable SegmentType horizontalEdge,
                                              @Nullable SegmentType verticalEdge) {
        Rectangle parentBounds = new Rectangle(layout.x, layout.y, layout.w, layout.h);
        if (horizontalEdge == SegmentType.BOTTOM && verticalEdge == SegmentType.RIGHT) {
          return super.getResizeUpdateMessage(child, parent, newBounds, horizontalEdge, verticalEdge);
        }
        return String.format("x=%d, y=%d\nwidth=%s, height=%s", editor.pxToDp(newBounds.x - parentBounds.x),
                             editor.pxToDp(newBounds.y - parentBounds.y), getWidthAttribute(), getHeightAttribute());
      }
    };
  }
}
