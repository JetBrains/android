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
package com.android.tools.idea.uibuilder.surface;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.*;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.uibuilder.model.SelectionHandle.PIXEL_RADIUS;

public class SelectionLayer extends Layer {
  private final ScreenView myScreenView;

  public SelectionLayer(@NotNull ScreenView screenView) {
    myScreenView = screenView;
  }

  @Override
  public boolean paint(@NotNull Graphics2D gc) {
    SelectionModel model = myScreenView.getSelectionModel();
    ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(
      myScreenView.getModel().getFacet());
    for (NlComponent component : model.getSelection()) {
      if (component.isRoot() || !component.isShowing()) {
        continue;
      }
      if (parentHandlingSelection(component, viewHandlerManager)) {
        continue;
      }
      int x = Coordinates.getSwingX(myScreenView, component.x);
      int y = Coordinates.getSwingY(myScreenView, component.y);
      int w = Coordinates.getSwingDimension(myScreenView, component.w);
      int h = Coordinates.getSwingDimension(myScreenView, component.h);
      NlGraphics.drawRect(NlDrawingStyle.SELECTION, gc, x, y, w + 1, h + 1);

      SelectionHandles handles = model.getHandles(component);
      for (SelectionHandle handle : handles) {
        int sx = Coordinates.getSwingX(myScreenView, handle.getCenterX());
        int sy = Coordinates.getSwingY(myScreenView, handle.getCenterY());
        NlGraphics.drawFilledRect(NlDrawingStyle.SELECTION, gc, sx - PIXEL_RADIUS / 2, sy - PIXEL_RADIUS / 2,
                                  PIXEL_RADIUS, PIXEL_RADIUS);
      }
    }
    return false;
  }

  /**
   * Utility function that checks if the component is a child of a view group that
   * handles painting
   *
   * @param component          the component we are looking at
   * @param viewHandlerManager the current view handler manager
   * @return true if the parent container handles painting
   */
  private static boolean parentHandlingSelection(@NotNull NlComponent component,
                                                 @NotNull ViewHandlerManager viewHandlerManager) {
    NlComponent parent = component.getParent();

    if (parent == null) {
      return false;
    }

    ViewInfo view = parent.viewInfo;

    if (view == null) {
      return false;
    }

    String className = view.getClassName();
    ViewHandler handler = viewHandlerManager.getHandler(className);
    if (handler != null && handler instanceof ViewGroupHandler) {
      ViewGroupHandler viewGroupHandler = (ViewGroupHandler)handler;
      if (viewGroupHandler.handlesPainting()) {
        return true;
      }
    }
    return false;
  }
}
