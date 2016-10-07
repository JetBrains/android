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
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;

import java.util.List;

/** Handler for subclasses of the {@code AdapterView} ViewGroup. */
public class AdapterViewHandler extends ViewGroupHandler {
  @Nullable
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull NlComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return new DragHandler(editor, this, layout, components, type) {
      @Nullable
      @Override
      public String update(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
        super.update(x, y, modifiers);

        return String.format(
          "%1$s cannot be configured via XML; add content to the AdapterView using Java code",
          layout.getTagName());
      }

      @Override
      public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
      }

      @Override
      public void paint(@NotNull NlGraphics graphics) {
        graphics.useStyle(NlDrawingStyle.INVALID);
        graphics.drawRect(layout.x, layout.y, layout.w, layout.h);
      }
    };
  }

  @Override
  @Nullable
  public ScrollHandler createScrollHandler(@NonNull ViewEditor editor, @NonNull NlComponent component) {
    return null;
  }
}
