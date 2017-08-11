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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.SegmentType;
import com.android.tools.idea.common.scene.SceneComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Handler for the {@code <FrameLayout>} layout
 */
public class FrameLayoutHandler extends ViewGroupHandler {
  @Override
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return new FrameDragHandler(editor, this, layout, components, type) {
    };
  }

  protected static class FrameDragHandler extends DragHandler {

    protected FrameDragHandler(@NotNull ViewEditor editor,
                               @NotNull ViewGroupHandler handler,
                               @NotNull SceneComponent layout,
                               @NotNull List<NlComponent> components,
                               @NotNull DragType type) {
      super(editor, handler, layout, components, type);
    }

    @Override
    public void paint(@NotNull NlGraphics graphics) {
      graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
      graphics.drawRectDp(layout.getDrawX(), layout.getDrawY(), layout.getDrawWidth(), layout.getDrawHeight());


      for (NlComponent nlComponent : components) {
        // Place all elements at (0,0) in the FrameLayout
        SceneComponent component = layout.getSceneComponent(nlComponent);
        if (component == null) {
          continue;
        }

        int x = layout.getDrawX();
        int y = layout.getDrawY();
        int w = NlComponentHelperKt.getW(nlComponent);
        int h = NlComponentHelperKt.getH(nlComponent);

        graphics.useStyle(NlDrawingStyle.DROP_PREVIEW);
        graphics.drawRectDp(x, y, w, h);
      }
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
