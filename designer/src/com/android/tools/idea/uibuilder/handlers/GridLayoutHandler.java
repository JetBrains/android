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
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.uibuilder.model.NlComponent;

import java.util.List;

final class GridLayoutHandler extends ViewGroupHandler {
  @Override
  public DragHandler createDragHandler(@NonNull ViewEditor editor, @NonNull NlComponent layout, @NonNull List<NlComponent> components,
                                       @NonNull DragType type) {
    return new GridDragHandler(editor, this, layout, components, type);
  }

  private static final class GridDragHandler extends DragHandler {
    private final GridInfo info;

    private GridDragHandler(ViewEditor editor, ViewGroupHandler handler, NlComponent layout, List<NlComponent> components, DragType type) {
      super(editor, handler, layout, components, type);
      info = new GridInfo(layout);
    }

    @Override
    public void commit(int x, int y, int modifiers) {
    }

    @Override
    public void paint(@NonNull NlGraphics graphics) {
      Insets padding = layout.getPadding();

      int layoutX1 = layout.x + padding.left;
      int layoutY1 = layout.y + padding.top;
      int layoutX2 = layout.x + padding.left + layout.w - padding.width() - 1;
      int layoutY2 = layout.y + padding.top + layout.h - padding.height() - 1;

      graphics.useStyle(NlDrawingStyle.DROP_ZONE);

      for (int x : info.verticalLineLocations) {
        x += layoutX1;
        graphics.drawLine(x, layoutY1, x, layoutY2);
      }

      for (int y : info.horizontalLineLocations) {
        y += layoutY1;
        graphics.drawLine(layoutX1, y, layoutX2, y);
      }

      graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
      graphics.drawRect(layoutX1, layoutY1, layout.w - padding.width(), layout.h - padding.height());
    }
  }
}
