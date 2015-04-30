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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SegmentType;
import com.android.tools.idea.uibuilder.surface.ScreenView;

import java.awt.*;
import java.util.List;

import static com.android.SdkConstants.*;

public class AbsoluteLayoutHandler extends ViewGroupHandler {
  @Override
  @Nullable
  public DragHandler createDragHandler(@NonNull ViewEditor editor,
                                       @NonNull NlComponent layout,
                                       @NonNull List<NlComponent> components,
                                       @NonNull DragType type) {
    return new DragHandler(editor, this, layout, components, type) {
      @Override
      public void start(@AndroidCoordinate int x, @AndroidCoordinate int y) {
        super.start(x, y);
      }

      @Override
      public void paint(@NonNull ScreenView view, @NonNull Graphics2D gc) {
        int deltaX = lastX - startX;
        int deltaY = lastY - startY;
        for (NlComponent component : components) {
          int x = component.x + deltaX;
          int y = component.y + deltaY;
          int w = component.w;
          int h = component.h;

          int sx = Coordinates.getSwingX(view, x);
          int sy = Coordinates.getSwingY(view, y);
          int sw = Coordinates.getSwingDimension(view, w);
          int sh = Coordinates.getSwingDimension(view, h);

          NlGraphics.drawRect(NlDrawingStyle.DROP_PREVIEW, gc, sx, sy, sw, sh);
        }
      }

      @Override
      public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y) {
        // TODO: Remove all existing layout parameters; if you're dragging from one layout type to another, you don't
        // want stale layout parameters (e.g. layout_alignLeft from a previous RelativeLayout in a new GridLayout, and so on.)

        int deltaX = x - startX;
        int deltaY = y - startY;

        for (NlComponent component : components) {
          // TODO: Property API for manipulating properties
          component.tag.setAttribute(ATTR_LAYOUT_X, ANDROID_URI, editor.pxToDpWithUnits(component.x - layout.x + deltaX));
          component.tag.setAttribute(ATTR_LAYOUT_Y, ANDROID_URI, editor.pxToDpWithUnits(component.y - layout.y + deltaY));
        }
      }
    };
  }

  @Override
  @Nullable
  public ResizeHandler createResizeHandler(@NonNull ViewEditor editor,
                                           @NonNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    return new ResizeHandler(editor, this, component, horizontalEdgeType, verticalEdgeType) {
      @Override
      public void commit(@AndroidCoordinate int px, @AndroidCoordinate int py) {
        int deltaX = px - startX;
        int deltaY = py - startY;
        int x = component.x;
        int y = component.y;
        NlComponent parent = component.getParent();
        if (parent != null) {
          // layout_x and layout_y are relative to the parent, not absolute screen coordinates
          x -= parent.x;
          y -= parent.y;
        }
        int w = component.w;
        int h = component.h;
        if (horizontalEdgeType == SegmentType.TOP) {
          y += deltaY;
          component.tag.setAttribute(ATTR_LAYOUT_Y, ANDROID_URI, editor.pxToDpWithUnits(y));
          h -= deltaY;
        } else {
          h += deltaY;
        }
        if (verticalEdgeType == SegmentType.LEFT || verticalEdgeType == SegmentType.START) {
          x += deltaX;
          component.tag.setAttribute(ATTR_LAYOUT_X, ANDROID_URI, editor.pxToDpWithUnits(x));
          w -= deltaX;
        } else {
          w += deltaX;
        }

        // TODO: Snap to wrap_content or match_parent
        component.tag.setAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI, editor.pxToDpWithUnits(w));
        component.tag.setAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI, editor.pxToDpWithUnits(h));
      }

      @Override
      public void paint(@NonNull ScreenView view, @NonNull Graphics2D gc) {
        int deltaX = lastX - startX;
        int deltaY = lastY - startY;
        int x = component.x;
        int y = component.y;
        int w = component.w;
        int h = component.h;
        if (horizontalEdgeType == SegmentType.TOP) {
          y += deltaY;
          h -= deltaY;
        } else {
          h += deltaY;
        }
        if (verticalEdgeType == SegmentType.LEFT || verticalEdgeType == SegmentType.START) {
          x += deltaX;
          w -= deltaX;
        } else {
          w += deltaX;
        }

        int sx = Coordinates.getSwingX(view, x);
        int sy = Coordinates.getSwingY(view, y);
        int sw = Coordinates.getSwingDimension(view, w);
        int sh = Coordinates.getSwingDimension(view, h);

        NlGraphics.drawRect(NlDrawingStyle.RESIZE_PREVIEW, gc, sx, sy, sw, sh);
      }
    };
  }
}