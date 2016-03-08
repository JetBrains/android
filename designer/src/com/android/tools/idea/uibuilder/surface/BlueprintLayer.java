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
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
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
  private int myWidth;
  private int myHeight;

  public BlueprintLayer(@NonNull ScreenView screenView) {
    myScreenView = screenView;
  }

  /**
   * Base paint method. Draw the layer's background and call drawComponent() on the root component.
   *
   * @param gc The Graphics object to draw into
   *
   * @return true if we need to repaint
   */
  @Override
  public boolean paint(@NonNull Graphics2D gc) {
    int tlx = myScreenView.getX();
    int tly = myScreenView.getY();
    gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

    NlModel myModel = myScreenView.getModel();
    RenderResult renderResult = myModel.getRenderResult();
    if (renderResult == null || renderResult.getImage() == null) {
      return false;
    }

    BufferedImage originalImage = renderResult.getImage().getOriginalImage();
    double scale = myScreenView.getScale();
    int width = (int)(originalImage.getWidth() * scale);
    int height = (int)(originalImage.getHeight() * scale);
    myWidth = width;
    myHeight = height;

    if (myModel.getComponents().size() == 0) {
      return false;
    }

    NlComponent component = myModel.getComponents().get(0);
    component = component.getRoot();

    // Draw the background
    Graphics2D g = (Graphics2D) gc.create();
    g.setColor(BLUEPRINT_BG_COLOR);
    g.fillRect(tlx, tly, width, height);
    g.dispose();

    // Draw the components
    ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(myModel.getFacet());
    return drawComponent(gc, component, viewHandlerManager);
  }

  /**
   * Draw the given component and its children
   *
   * @param gc the graphics context
   * @param component the component we want to draw
   * @param viewHandlerManager the view handler
   *
   * @return true if we need to
   */
  private boolean drawComponent(@NonNull Graphics2D gc, @NonNull NlComponent component,
                                @NonNull ViewHandlerManager viewHandlerManager) {
    boolean needsRepaint = false;

    if (component.viewInfo != null) {
      String className = component.viewInfo.getClassName();
      ViewHandler handler = viewHandlerManager.getHandler(className);

      className = className.substring(className.lastIndexOf('.') + 1);
      if (handler == null) {
        handler = viewHandlerManager.getHandler(className);
      }

      // Check if the view handler handles the painting
      if (handler != null && handler instanceof ViewGroupHandler) {
        ViewGroupHandler viewGroupHandler = (ViewGroupHandler)handler;
        if (viewGroupHandler.handlesPainting()) {
          needsRepaint |= viewGroupHandler.drawGroup(gc, myScreenView, myWidth, myHeight, component);
          return needsRepaint;
        }
      }

      // If not, paint the component ourself

      Graphics2D g = (Graphics2D) gc.create();

      int x = getSwingX(myScreenView, component.x);
      int y = getSwingY(myScreenView, component.y);
      int w = getSwingDimension(myScreenView, component.w);
      int h = getSwingDimension(myScreenView, component.h);

      drawComponentBackground(g, component);

      Font font = BLUEPRINT_TEXT_FONT;
      g.setFont(font);
      String id = component.getId();
      int lineHeight = g.getFontMetrics().getHeight();
      FontRenderContext fontRenderContext = g.getFontRenderContext();
      if (id != null && h > lineHeight * 2) {
        // Can fit both
        Rectangle2D classBounds = font.getStringBounds(className, fontRenderContext);
        Rectangle2D idBounds = font.getStringBounds(id, fontRenderContext);
        int textY = y + h / 2;
        int textX = x + w / 2 - ((int)classBounds.getWidth()) / 2;
        if (component.isRoot()) {
          textX = x + lineHeight;
          textY = y - (int) (classBounds.getHeight() + idBounds.getHeight());
        }
        g.drawString(className, textX, textY);

        if (component.isRoot()) {
          textX = x + lineHeight;
          textY = y - (int) (idBounds.getHeight());
        } else {
          textX = x + w / 2 - ((int)idBounds.getWidth()) / 2;
          textY += (int)(idBounds.getHeight());
        }
        g.drawString(id, textX, textY);
      }
      else {
        // Only room for a single line: prioritize the id if it's available, otherwise the class name
        String text = id != null ? id : className;
        Rectangle2D stringBounds = font.getStringBounds(text, fontRenderContext);
        int textX = x + w / 2 - ((int)stringBounds.getWidth()) / 2;
        int textY = y + h / 2 + ((int)stringBounds.getHeight()) / 2;
        g.drawString(text, textX, textY);
      }

      g.dispose();

      if (handler != null) {
        if (handler.paintConstraints(myScreenView, gc, component)) {
          return false;
        }
      }

      // Draw the children of the component...
      for (NlComponent child : component.getChildren()) {
        needsRepaint |= drawComponent(gc, child, viewHandlerManager);
      }
    }

    return needsRepaint;
  }

  /**
   * Utility function to draw a component's background
   *
   * @param gc the graphics context
   * @param component the component
   */
  private void drawComponentBackground(@NonNull Graphics2D gc, @NonNull NlComponent component) {
    if (component.viewInfo != null) {
      int x = getSwingX(myScreenView, component.x);
      int y = getSwingY(myScreenView, component.y);
      int w = getSwingDimension(myScreenView, component.w);
      int h = getSwingDimension(myScreenView, component.h);

      Graphics2D g = (Graphics2D) gc.create();

      if (!component.isRoot()) {
        int spaceBetweenLines = 2;
        Color bg = new Color(0, 0, 0, 0);
        Color fg = new Color(81, 103, 163, 100);
        g.setPaint(new LinearGradientPaint((float)x, (float)y, (float)(x + spaceBetweenLines),
                                            (float)(y + spaceBetweenLines),
                                            new float[]{0, .1f, .1001f}, new Color[]{fg, fg, bg},
                                            MultipleGradientPaint.CycleMethod.REFLECT));
        g.fillRect(x, y, w, h);
      }
      g.setColor(BLUEPRINT_FG_COLOR);
      Stroke prevStroke = g.getStroke();
      g.setStroke(BLUEPRINT_COMPONENT_STROKE);
      g.drawRect(x, y, w, h);
      g.setStroke(prevStroke);

      g.dispose();
    }
  }
}
