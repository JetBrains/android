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

import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.*;
import static com.android.tools.idea.uibuilder.model.Coordinates.*;

public class BlueprintLayer extends Layer {
  private static final int BACKGROUND_LINE_SPACE_PX = 2;

  private static final float[] COMPONENT_BACKGROUND_GRADIENT_FRACTIONS = {0, .1f, .1001f};
  private static final Color[] COMPONENT_BACKGROUND_GRADIENT_COLORS =
    {BLUEPRINT_COMPONENT_FG_COLOR, BLUEPRINT_COMPONENT_FG_COLOR, BLUEPRINT_COMPONENT_BG_COLOR};

  private final ScreenView myScreenView;
  private Dimension myScreenViewSize = new Dimension();
  private Rectangle mySizeRectangle = new Rectangle();

  public BlueprintLayer(@NotNull ScreenView screenView) {
    myScreenView = screenView;
  }

  /**
   * Base paint method. Draw the blueprint background.
   * TODO: We might want to simplify the stack of layers and not keep this one.
   *
   * @param gc The Graphics object to draw into
   */
  @Override
  public void paint(@NotNull Graphics2D gc) {
    myScreenView.getSize(myScreenViewSize);

    mySizeRectangle.setBounds(myScreenView.getX(), myScreenView.getY(), myScreenViewSize.width, myScreenViewSize.height);
    Rectangle2D.intersect(mySizeRectangle, gc.getClipBounds(), mySizeRectangle);
    if (mySizeRectangle.isEmpty()) {
      return;
    }

    // Draw the background
    Graphics2D g = (Graphics2D) gc.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(BLUEPRINT_BG_COLOR);
    g.fillRect(mySizeRectangle.x, mySizeRectangle.y, mySizeRectangle.width, mySizeRectangle.height);

    // Draw the components
    NlModel model = myScreenView.getModel();
    if (model.getComponents().size() == 0) {
      return;
    }

    NlComponent component = model.getComponents().get(0);
    component = component.getRoot();

    ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(model.getFacet());
    drawComponent(g, component, viewHandlerManager);

    g.dispose();
  }

  /**
   * Draw the given component and its children
   *
   * @param gc                 the graphics context
   * @param component          the component we want to draw
   * @param viewHandlerManager the view handler
   */
  private void drawComponent(@NotNull Graphics2D gc, @NotNull NlComponent component,
                             @NotNull ViewHandlerManager viewHandlerManager) {
    if (component.viewInfo == null) {
      return;
    }

    ViewHandler handler = component.getViewHandler();

    // Check if the view handler handles the painting
    if (handler != null && handler instanceof ViewGroupHandler) {
      ViewGroupHandler viewGroupHandler = (ViewGroupHandler)handler;
      if (viewGroupHandler.handlesPainting()) {
        if (handler.paintConstraints(myScreenView, gc, component)) {
          return;
        }
      }
    }

    // If not, paint the component ourselves
    Graphics2D g = (Graphics2D)gc.create();

    int x = getSwingX(myScreenView, component.x);
    int y = getSwingY(myScreenView, component.y);
    int w = getSwingDimension(myScreenView, component.w);
    int h = getSwingDimension(myScreenView, component.h);

    drawComponentBackground(g, component);
    String name = component.getTagName();
    name = name.substring(name.lastIndexOf('.') + 1);

    Font font = BLUEPRINT_TEXT_FONT;
    g.setFont(font);
    String id = component.getId();
    int lineHeight = g.getFontMetrics().getHeight();
    FontRenderContext fontRenderContext = g.getFontRenderContext();
    if (id != null && h > lineHeight * 2) {
      // Can fit both
      Rectangle2D classBounds = font.getStringBounds(name, fontRenderContext);
      Rectangle2D idBounds = font.getStringBounds(id, fontRenderContext);
      int textY = y + h / 2;
      int textX = x + w / 2 - ((int)classBounds.getWidth()) / 2;
      if (component.isRoot()) {
        textX = x + lineHeight;
        textY = y - (int)(classBounds.getHeight() + idBounds.getHeight());
      }
      g.drawString(name, textX, textY);

      if (component.isRoot()) {
        textX = x + lineHeight;
        textY = y - (int)(idBounds.getHeight());
      }
      else {
        textX = x + w / 2 - ((int)idBounds.getWidth()) / 2;
        textY += (int)(idBounds.getHeight());
      }
      g.drawString(id, textX, textY);
    }
    else {
      // Only room for a single line: prioritize the id if it's available, otherwise the class name
      String text = id != null ? id : name;
      Rectangle2D stringBounds = font.getStringBounds(text, fontRenderContext);
      int textX = x + w / 2 - ((int)stringBounds.getWidth()) / 2;
      int textY = y + h / 2 + ((int)stringBounds.getHeight()) / 2;
      g.drawString(text, textX, textY);
    }

    g.dispose();

    if (handler != null) {
      if (handler.paintConstraints(myScreenView, gc, component)) {
        return;
      }
    }

    // Draw the children of the component...
    for (NlComponent child : component.getChildren()) {
      drawComponent(gc, child, viewHandlerManager);
    }
  }

  /**
   * Utility function to draw a component's background
   *
   * @param gc        the graphics context
   * @param component the component
   */
  private void drawComponentBackground(@NotNull Graphics2D gc, @NotNull NlComponent component) {
    if (component.viewInfo != null) {
      int x = getSwingX(myScreenView, component.x);
      int y = getSwingY(myScreenView, component.y);
      int w = getSwingDimension(myScreenView, component.w);
      int h = getSwingDimension(myScreenView, component.h);

      Graphics2D g = (Graphics2D)gc.create();

      if (!component.isRoot()) {
        g.setPaint(new LinearGradientPaint((float)x, (float)y, (float)(x + BACKGROUND_LINE_SPACE_PX),
                                           (float)(y + BACKGROUND_LINE_SPACE_PX),
                                           COMPONENT_BACKGROUND_GRADIENT_FRACTIONS, COMPONENT_BACKGROUND_GRADIENT_COLORS,
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
