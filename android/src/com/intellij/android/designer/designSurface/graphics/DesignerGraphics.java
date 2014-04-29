/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer.designSurface.graphics;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class DesignerGraphics {
  private final Graphics myGraphics;
  private final JComponent myTarget;

  public DesignerGraphics(Graphics graphics, JComponent target) {
    myGraphics = graphics;
    myTarget = target;
  }

  public Graphics getGraphics() {
    return myGraphics;
  }

  /**
   * Current style being used for drawing.
   */
  @NotNull
  private DrawingStyle myStyle = DrawingStyle.INVALID;

  /** Use the given style for subsequent drawing operations */
  public void useStyle(@NotNull DrawingStyle style) {
    myStyle = style;
  }

  public static void useStroke(DrawingStyle style, Graphics gc) {
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

  public static void useFill(DrawingStyle style, Graphics gc) {
    Color fillColor = style.getFillColor();
    if (fillColor != null) {
      if (fillColor != gc.getColor()) {
        gc.setColor(fillColor);
      }
    }
  }

  public void fillRect(int x, int y, int width, int height) {
    fillRect(myStyle, myGraphics, x, y, width, height);
  }

  public static void fillRect(DrawingStyle style, Graphics gc, int x, int y, int width, int height) {
    Color fillColor = style.getFillColor();
    if (fillColor != null) {
      useFill(style, gc);
      gc.fillRect(x + 1, y + 1, width - 1, height - 1);
    }
  }

  public static void drawFilledRect(DrawingStyle style, Graphics gc, int x, int y, int width, int height) {
    Color fillColor = style.getFillColor();
    if (fillColor != null) {
      useFill(style, gc);
      gc.fillRect(x + 1, y + 1, width - 2, height - 2);
    }
    useStroke(style, gc);
    if (style.getStrokeColor() != null) {
      gc.drawRect(x, y, width - 1, height - 1);
    }
  }

  public static void drawStrokeFilledRect(DrawingStyle style, Graphics gc, int x, int y, int width, int height) {
    useStroke(style, gc);
    if (style.getStrokeColor() != null) {
      gc.fillRect(x, y, width, height);
    }
  }

  public void drawRect(int x, int y, int width, int height) {
    drawRect(myStyle, myGraphics, x, y, width, height);
  }

  public static void drawRect(DrawingStyle style, Graphics gc, int x, int y, int width, int height) {
    useStroke(style, gc);
    gc.drawRect(x, y, width - 1, height - 1);
  }

  public void drawLine(int x1, int y1, int x2, int y2) {
    drawLine(myStyle, myGraphics, x1, y1, x2, y2);
  }

  public static void drawLine(DrawingStyle style, Graphics gc, int x1, int y1, int x2, int y2) {
    useStroke(style, gc);
    gc.drawLine(x1, y1, x2, y2);
  }


  // arrows

  private static final int MIN_LENGTH = 10;
  private static final int ARROW_SIZE = 5;

  public void drawArrow(int x1, int y1, int x2, int y2) {
    drawArrow(myStyle, myGraphics, x1, y1, x2, y2);
  }

  public static void drawArrow(DrawingStyle style, Graphics graphics, int x1, int y1, int x2, int y2) {
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
      } else {
        y1 += delta;
        y2-= delta;
      }

    } else if (y1 == y2 && Math.abs(x1 - x2) < MIN_LENGTH) {
      int delta = (MIN_LENGTH - Math.abs(x1 - x2)) / 2;
      if (x1 < x2) {
        x1 -= delta;
        x2 += delta;
      } else {
        x1 += delta;
        x2-= delta;
      }
    }

    graphics.drawLine(x1, y1, x2, y2);

    // Arrowhead:

    if (x1 == x2) {
      // Vertical
      if (y2 > y1) {
        graphics.drawLine(x2 - arrowWidth, y2 - arrowHeight, x2, y2);
        graphics.drawLine(x2 + arrowWidth, y2 - arrowHeight, x2, y2);
      } else {
        graphics.drawLine(x2 - arrowWidth, y2 + arrowHeight, x2, y2);
        graphics.drawLine(x2 + arrowWidth, y2 + arrowHeight, x2, y2);
      }
    } else if (y1 == y2) {
      // Horizontal
      if (x2 > x1) {
        graphics.drawLine(x2 - arrowHeight, y2 - arrowWidth, x2, y2);
        graphics.drawLine(x2 - arrowHeight, y2 + arrowWidth, x2, y2);
      } else {
        graphics.drawLine(x2 + arrowHeight, y2 - arrowWidth, x2, y2);
        graphics.drawLine(x2 + arrowHeight, y2 + arrowWidth, x2, y2);
      }
    } else {
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
      int rx = (int) (Math.cos(angle) * (ax-x1) - Math.sin(angle) * (ay-y1) + x1);
      int ry = (int) (Math.sin(angle) * (ax-x1) + Math.cos(angle) * (ay-y1) + y1);
      graphics.drawLine(x2, y2, rx, ry);

      ay = y1 + arrowWidth;
      rx = (int) (Math.cos(angle) * (ax-x1) - Math.sin(angle) * (ay-y1) + x1);
      ry = (int) (Math.sin(angle) * (ax-x1) + Math.cos(angle) * (ay-y1) + y1);
      graphics.drawLine(x2, y2, rx, ry);
    }
  }

  public static void drawCross(DrawingStyle style, Graphics g, int radius) {
    int size2 = (radius - 3) / 2;
    Color fillColor = style.getFillColor();
    if (fillColor != null) {
      fillRect(style, g, 0, size2, radius, 3);
      fillRect(style, g, size2, 0, 3, radius);
    } else {
      drawLine(style, g, 0, size2 + 1, radius, size2 + 1);
      drawLine(style, g, size2 + 1, 0, size2 + 1, radius);
    }
  }

  public JComponent getTarget() {
    return myTarget;
  }
}
