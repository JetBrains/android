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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SegmentType;

import java.util.List;

/** Handler for the {@code <FrameLayout>} layout */
public class FrameLayoutHandler extends ViewGroupHandler {
  @Override
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull NlComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return new FrameDragHandler(editor, this, layout, components, type) {
    };
  }

  protected class FrameDragHandler extends DragHandler {

    protected FrameDragHandler(@NotNull ViewEditor editor,
                               @NotNull ViewGroupHandler handler,
                               @NotNull NlComponent layout,
                               @NotNull List<NlComponent> components,
                               @NotNull DragType type) {
      super(editor, handler, layout, components, type);
    }

    @Override
    public void paint(@NotNull NlGraphics graphics) {
      graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
      graphics.drawRect(layout.x, layout.y, layout.w, layout.h);


      for (NlComponent component : components) {
        // Place all elements at (0,0) in the FrameLayout
        int x = layout.x;
        int y = layout.y;
        int w = component.w;
        int h = component.h;

        graphics.useStyle(NlDrawingStyle.DROP_PREVIEW);
        graphics.drawRect(x, y, w, h);
      }
    }

    @Override
    public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    }
  }

  @Override
  @Nullable
  public ResizeHandler createResizeHandler(@NotNull ViewEditor editor,
                                           @NotNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    return new DefaultResizeHandler(editor, this, component, horizontalEdgeType, verticalEdgeType);
  }
}