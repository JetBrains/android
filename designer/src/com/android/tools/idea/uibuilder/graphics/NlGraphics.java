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
package com.android.tools.idea.uibuilder.graphics;

import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.surface.ScreenView;

import java.awt.*;

public class NlGraphics {
  private final ScreenView myScreenView;
  private final Graphics2D myGraphics;

  public NlGraphics(@NotNull Graphics2D graphics, @NotNull ScreenView screenView) {
    myGraphics = graphics;
    myScreenView = screenView;
  }

  /**
   * Current style being used for drawing.
   */
  @NotNull
  private NlDrawingStyle myStyle = NlDrawingStyle.INVALID;

  /**
   * Use the given style for subsequent drawing operations
   */
  public void useStyle(@NotNull NlDrawingStyle style) {
    myStyle = style;
  }

  public void fillRect(@AndroidCoordinate int x,
                       @AndroidCoordinate int y,
                       @AndroidCoordinate int width,
                       @AndroidCoordinate int height) {
    Color fillColor = myStyle.getFillColor();
    if (fillColor != null) {
      useFill(myStyle, myGraphics);

      x = Coordinates.getSwingX(myScreenView, x);
      y = Coordinates.getSwingY(myScreenView, y);
      width = Coordinates.getSwingDimension(myScreenView, width);
      height = Coordinates.getSwingDimension(myScreenView, height);

      fillRect(myStyle, myGraphics, x, y, width, height);
    }
  }

  public void drawTop(@NotNull NlComponent component) {
    drawLine(component.x, component.y, component.x + component.w, component.y);
  }

  public void drawTop(@NotNull Rectangle rectangle) {
    drawLine(rectangle.x, rectangle.y, rectangle.x + rectangle.width, rectangle.y);
  }

  public void drawLeft(@NotNull NlComponent component) {
    drawLine(component.x, component.y, component.x, component.y + component.h);
  }

  public void drawLeft(@NotNull Rectangle rectangle) {
    drawLine(rectangle.x, rectangle.y, rectangle.x, rectangle.y + rectangle.height);
  }

  public void drawRight(@NotNull NlComponent component) {
    drawLine(component.x + component.w, component.y, component.x + component.w, component.y + component.h);
  }

  public void drawRight(@NotNull Rectangle rectangle) {
    drawLine(rectangle.x + rectangle.width, rectangle.y, rectangle.x + rectangle.width, rectangle.y + rectangle.height);
  }

  public void drawBottom(@NotNull NlComponent component) {
    drawLine(component.x, component.y + component.h, component.x + component.w, component.y + component.h);
  }

  public void drawBottom(@NotNull Rectangle rectangle) {
    drawLine(rectangle.x, rectangle.y + rectangle.height, rectangle.x + rectangle.width, rectangle.y + rectangle.height);
  }

  public void drawLine(@AndroidCoordinate int x1,
                       @AndroidCoordinate int y1,
                       @AndroidCoordinate int x2,
                       @AndroidCoordinate int y2) {
    x1 = Coordinates.getSwingX(myScreenView, x1);
    x2 = Coordinates.getSwingX(myScreenView, x2);
    y1 = Coordinates.getSwingY(myScreenView, y1);
    y2 = Coordinates.getSwingY(myScreenView, y2);

    drawLine(myStyle, myGraphics, x1, y1, x2, y2);
  }

  public void drawRect(@AndroidCoordinate int x,
                       @AndroidCoordinate int y,
                       @AndroidCoordinate int width,
                       @AndroidCoordinate int height) {
    x = Coordinates.getSwingX(myScreenView, x);
    y = Coordinates.getSwingY(myScreenView, y);
    width = Coordinates.getSwingDimension(myScreenView, width);
    height = Coordinates.getSwingDimension(myScreenView, height);

    drawRect(myStyle, myGraphics, x, y, width, height);
  }

  public void drawArrow(@AndroidCoordinate int x1,
                        @AndroidCoordinate int y1,
                        @AndroidCoordinate int x2,
                        @AndroidCoordinate int y2) {
    x1 = Coordinates.getSwingX(myScreenView, x1);
    x2 = Coordinates.getSwingX(myScreenView, x2);
    y1 = Coordinates.getSwingY(myScreenView, y1);
    y2 = Coordinates.getSwingY(myScreenView, y2);

    drawArrow(myStyle, myGraphics, x1, y1, x2, y2);
  }

  @SuppressWarnings("unused")
  public void drawCross(@AndroidCoordinate int radius) {
    radius = Coordinates.getSwingDimension(myScreenView, radius);
    drawCross(myStyle, myGraphics, radius);
  }

  // Swing coordinate system

  public static void fillRect(@NotNull NlDrawingStyle style,
                              @NotNull Graphics gc,
                              @SwingCoordinate int x,
                              @SwingCoordinate int y,
                              @SwingCoordinate int width,
                              @SwingCoordinate int height) {
    Color fillColor = style.getFillColor();
    if (fillColor != null) {
      useFill(style, gc);
      gc.fillRect(x + 1, y + 1, width - 1, height - 1);
    }
  }

  public static void drawFilledRect(@NotNull NlDrawingStyle style,
                                    @NotNull Graphics gc,
                                    @SwingCoordinate int x,
                                    @SwingCoordinate int y,
                                    @SwingCoordinate int width,
                                    @SwingCoordinate int height) {
    Color fillColor = style.getFillColor();
    if (fillColor != null) {
      useFill(style, gc);
      gc.setColor(fillColor);
      gc.fillRect(x + 1, y + 1, width - 2, height - 2);
    }
    useStroke(style, gc);
    if (style.getStrokeColor() != null) {
      gc.drawRect(x, y, width - 1, height - 1);
    }
  }

  @SuppressWarnings("unused")
  public static void drawStrokeFilledRect(@NotNull NlDrawingStyle style,
                                          @NotNull Graphics gc,
                                          @SwingCoordinate int x,
                                          @SwingCoordinate int y,
                                          @SwingCoordinate int width,
                                          @SwingCoordinate int height) {
    useStroke(style, gc);
    if (style.getStrokeColor() != null) {
      gc.fillRect(x, y, width, height);
    }
  }

  public static void drawRect(@NotNull NlDrawingStyle style,
                              @NotNull Graphics gc,
                              @SwingCoordinate int x,
                              @SwingCoordinate int y,
                              @SwingCoordinate int width,
                              @SwingCoordinate int height) {
    useStroke(style, gc);
    gc.drawRect(x, y, width - 1, height - 1);
  }

  public static void drawLine(@NotNull NlDrawingStyle style,
                              @NotNull Graphics gc,
                              @SwingCoordinate int x1,
                              @SwingCoordinate int y1,
                              @SwingCoordinate int x2,
                              @SwingCoordinate int y2) {
    useStroke(style, gc);
    gc.drawLine(x1, y1, x2, y2);
  }

  private static final int MIN_LENGTH = 10;
  private static final int ARROW_SIZE = 5;

  public static void drawArrow(@NotNull NlDrawingStyle style,
                               @NotNull Graphics graphics,
                               @SwingCoordinate int x1,
                               @SwingCoordinate int y1,
                               @SwingCoordinate int x2,
                               @SwingCoordinate int y2) {
    Color strokeColor = style.getStrokeColor();
    if (strokeColor != graphics.getColor()) {
      graphics.setColor(strokeColor);
    }
    if (graphics instanceof Graphics2D) {
      Graphics2D gc2 = (Graphics2D)graphics;
      Stroke stroke = style.getStroke();
      if (gc2.getStroke() != stroke) {
        gc2.setStroke(stroke);
      }
    }

    int arrowWidth = ARROW_SIZE;
    int arrowHeight = ARROW_SIZE;

    // Make ARROW_SIZE adjustments to ensure that the arrow has enough width to be visible
    if (x1 == x2 && Math.abs(y1 - y2) < MIN_LENGTH) {
      int delta = (MIN_LENGTH - Math.abs(y1 - y2)) / 2;
      if (y1 < y2) {
        y1 -= delta;
        y2 += delta;
      }
      else {
        y1 += delta;
        y2 -= delta;
      }

    }
    else if (y1 == y2 && Math.abs(x1 - x2) < MIN_LENGTH) {
      int delta = (MIN_LENGTH - Math.abs(x1 - x2)) / 2;
      if (x1 < x2) {
        x1 -= delta;
        x2 += delta;
      }
      else {
        x1 += delta;
        x2 -= delta;
      }
    }

    graphics.drawLine(x1, y1, x2, y2);

    // Arrowhead:

    if (x1 == x2) {
      // Vertical
      if (y2 > y1) {
        graphics.drawLine(x2 - arrowWidth, y2 - arrowHeight, x2, y2);
        graphics.drawLine(x2 + arrowWidth, y2 - arrowHeight, x2, y2);
      }
      else {
        graphics.drawLine(x2 - arrowWidth, y2 + arrowHeight, x2, y2);
        graphics.drawLine(x2 + arrowWidth, y2 + arrowHeight, x2, y2);
      }
    }
    else if (y1 == y2) {
      // Horizontal
      if (x2 > x1) {
        graphics.drawLine(x2 - arrowHeight, y2 - arrowWidth, x2, y2);
        graphics.drawLine(x2 - arrowHeight, y2 + arrowWidth, x2, y2);
      }
      else {
        graphics.drawLine(x2 + arrowHeight, y2 - arrowWidth, x2, y2);
        graphics.drawLine(x2 + arrowHeight, y2 + arrowWidth, x2, y2);
      }
    }
    else {
      // Compute angle:
      int dy = y2 - y1;
      int dx = x2 - x1;
      double angle = Math.atan2(dy, dx);
      double lineLength = Math.sqrt(dy * dy + dx * dx);

      // Imagine a line of the same length as the arrow, but with angle 0.
      // Its two arrow lines are at (-arrowWidth, -arrowHeight) relative
      // to the endpoint (x1 + lineLength, y1) stretching up to (x2,y2).
      // We compute the positions of (ax,ay) for the point above and
      // below this line and paint the lines to it:
      double ax = x1 + lineLength - arrowHeight;
      double ay = y1 - arrowWidth;
      int rx = (int)(Math.cos(angle) * (ax - x1) - Math.sin(angle) * (ay - y1) + x1);
      int ry = (int)(Math.sin(angle) * (ax - x1) + Math.cos(angle) * (ay - y1) + y1);
      graphics.drawLine(x2, y2, rx, ry);

      ay = y1 + arrowWidth;
      rx = (int)(Math.cos(angle) * (ax - x1) - Math.sin(angle) * (ay - y1) + x1);
      ry = (int)(Math.sin(angle) * (ax - x1) + Math.cos(angle) * (ay - y1) + y1);
      graphics.drawLine(x2, y2, rx, ry);
    }
  }

  public static void drawCross(@NotNull NlDrawingStyle style, @NotNull Graphics g, @SwingCoordinate int radius) {
    int size2 = (radius - 3) / 2;
    Color fillColor = style.getFillColor();
    if (fillColor != null) {
      fillRect(style, g, 0, size2, radius, 3);
      fillRect(style, g, size2, 0, 3, radius);
    }
    else {
      drawLine(style, g, 0, size2 + 1, radius, size2 + 1);
      drawLine(style, g, size2 + 1, 0, size2 + 1, radius);
    }
  }

  public static void useStroke(@NotNull NlDrawingStyle style, @NotNull Graphics gc) {
    Color strokeColor = style.getStrokeColor();
    if (strokeColor != gc.getColor()) {
      gc.setColor(strokeColor);
    }
    if (gc instanceof Graphics2D) {
      Graphics2D gc2 = (Graphics2D)gc;
      Stroke stroke = style.getStroke();
      if (gc2.getStroke() != stroke) {
        gc2.setStroke(stroke);
      }
    }
  }

  public static void useFill(@NotNull NlDrawingStyle style, @NotNull Graphics gc) {
    Color fillColor = style.getFillColor();
    if (fillColor != null) {
      if (fillColor != gc.getColor()) {
        gc.setColor(fillColor);
      }
    }
  }
}
