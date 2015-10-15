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

import com.android.annotations.NonNull;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.*;
import static com.android.tools.idea.uibuilder.model.Coordinates.*;

public class BlueprintLayer extends Layer {
  private final ScreenView myScreenView;

  public BlueprintLayer(@NonNull ScreenView screenView) {
    myScreenView = screenView;
  }

  @Override
  public void paint(@NonNull Graphics2D gc) {
    int tlx = myScreenView.getX();
    int tly = myScreenView.getY();

    NlModel myModel = myScreenView.getModel();
    ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(myModel.getFacet());

    RenderResult renderResult = myModel.getRenderResult();
    if (renderResult == null || renderResult.getImage() == null) {
      return;
    }
    BufferedImage originalImage = renderResult.getImage().getOriginalImage();
    gc.setColor(BLUEPRINT_BG_COLOR);
    double scale = myScreenView.getScale();
    int width = (int)(originalImage.getWidth() * scale);
    int height = (int)(originalImage.getHeight() * scale);
    gc.fillRect(tlx, tly, width, height);

    gc.setColor(BLUEPRINT_GRID_COLOR);
    int gridSize = 16;
    for (int x = gridSize; x < width - 1; x += gridSize) {
      gc.drawLine(tlx + x, tly, tlx + x, tly + height - 1);

    }
    for (int y = gridSize; y < height - 1; y += gridSize) {
      gc.drawLine(tlx, tly + y, tlx + width - 1, tly + y);
    }

    for (NlComponent component : myScreenView.getModel().getComponents()) {
      drawComponent(gc, component, viewHandlerManager);
    }
  }

  private void drawComponent(@NonNull Graphics2D gc, @NonNull NlComponent component, @NonNull ViewHandlerManager viewHandlerManager) {
    if (component.viewInfo != null) {
      String className = component.viewInfo.getClassName();

      int x = getSwingX(myScreenView, component.x);
      int y = getSwingY(myScreenView, component.y);
      int w = getSwingDimension(myScreenView, component.w);
      int h = getSwingDimension(myScreenView, component.h);

      gc.setColor(BLUEPRINT_FG_COLOR);
      Stroke prevStroke = gc.getStroke();
      gc.setStroke(BLUEPRINT_COMPONENT_STROKE);
      gc.drawRect(x, y, w - 1, h - 1);
      gc.setStroke(prevStroke);
      className = className.substring(className.lastIndexOf('.') + 1);
      if (className.equals("FloatingActionButton")) {
        className = "FAB";
      }
      Font font = BLUEPRINT_TEXT_FONT;
      gc.setFont(font);
      String id = component.getId();
      int lineHeight = gc.getFontMetrics().getHeight();
      FontRenderContext fontRenderContext = gc.getFontRenderContext();
      if (id != null && h > lineHeight * 2) {
        // Can fit both
        Rectangle2D classBounds = font.getStringBounds(className, fontRenderContext);
        Rectangle2D idBounds = font.getStringBounds(id, fontRenderContext);
        int textY = y + h / 2;
        int textX = x + w / 2 - ((int)classBounds.getWidth()) / 2;
        gc.drawString(className, textX, textY);

        textX = x + w / 2 - ((int)idBounds.getWidth()) / 2;
        textY += (int)(idBounds.getHeight());
        gc.drawString(id, textX, textY);
      }
      else {
        // Only room for a single line: prioritize the id if it's available, otherwise the class name
        String text = id != null ? id : className;
        Rectangle2D stringBounds = font.getStringBounds(text, fontRenderContext);
        int textX = x + w / 2 - ((int)stringBounds.getWidth()) / 2;
        int textY = y + h / 2 + ((int)stringBounds.getHeight()) / 2;
        gc.drawString(text, textX, textY);
      }

      ViewHandler handler = viewHandlerManager.getHandler(className);
      if (handler != null) {
        if (handler.paintConstraints(myScreenView, gc, component)) {
          return;
        }
      }
    }

    for (NlComponent child : component.getChildren()) {
      drawComponent(gc, child, viewHandlerManager);
    }
  }
}
