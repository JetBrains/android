/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.draw;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.ScenePicker;
import com.intellij.ui.scale.JBUIScale;
import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for creating various graphics
 */
public final class DrawConnectionUtils {
  static final int ZIGZAG = JBUIScale.scale(2);
  static final int CENTER_ZIGZAG = JBUIScale.scale(3);
  public static final int MARGIN_SPACING = JBUIScale.scale(3);

  private static final boolean DEBUG = false;
  private static final boolean DRAW_ARROW = false;

  private static Polygon sLeftArrow;
  private static Polygon sTopArrow;
  private static Polygon sRightArrow;
  private static Polygon sBottomArrow;

  static Font sFont = new Font("Helvetica", Font.PLAIN, JBUIScale.scale(12));
  static Font sFontReference = new Font("Helvetica", Font.ITALIC | Font.BOLD, JBUIScale.scale(12));

  private static Font sSmallFont = new Font("Helvetica", Font.PLAIN, JBUIScale.scale(8));

  public static Stroke
    sSpreadDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
                                          BasicStroke.JOIN_BEVEL, 0, new float[]{1, JBUIScale.scale(4)}, 0);

  public static Stroke
    sDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
                                    BasicStroke.JOIN_BEVEL, 0, new float[]{JBUIScale.scale(2)}, 0);

  public static final int ARROW_SIDE = JBUIScale.scale(6);
  public static final int CONNECTION_ARROW_SIZE = JBUIScale.scale(5);
  public static final int SMALL_ARROW_SIDE = JBUIScale.scale(4);
  public static final int SMALL_ARROW_SIZE = JBUIScale.scale(3);

  /**
   * Utility function to draw a circle text centered at coordinates (x, y)
   *
   * @param g    graphics context
   * @param font the font we use to draw the text
   * @param text the text to display
   * @param x    x coordinate
   * @param y    y coordinate
   */
  public static void drawCircledText(Graphics2D g, Font font, String text, @SwingCoordinate int x, @SwingCoordinate int y) {
    Graphics2D g2 = (Graphics2D)g.create();
    g2.setFont(font);
    FontMetrics fm = g2.getFontMetrics();
    int padding = 4;
    Rectangle2D bounds = fm.getStringBounds(text, g2);
    double th = bounds.getHeight();
    double tw = bounds.getWidth();
    float radius = (float)(Math.max(th, tw) / 2f + padding);
    Ellipse2D.Float circle =
      new Ellipse2D.Float(x - radius, y - radius, 2 * radius + 1, 2 * radius + 1);
    g2.fill(circle);
    g2.setColor(Color.BLACK);
    g2.drawString(text, (int)(x - tw / 2), (y + fm.getAscent() / 2));
    if (DEBUG) {
      g2.setColor(Color.RED);
      g2.drawLine(x - 50, y, x + 50, y);
      g2.drawLine(x, y - 50, x, y + 50);
    }
    g2.dispose();
  }

  /**
   * Utility function to draw a circle text centered at coordinates (x, y)
   *
   * @param g         graphics context
   * @param font      the font we use to draw the text
   * @param textColor
   * @param text      the text to display
   * @param x         x coordinate
   * @param y         y coordinate
   */
  public static void drawRoundRectText(Graphics2D g,
                                       Font font,
                                       Color textColor,
                                       String text,
                                       @SwingCoordinate int x,
                                       @SwingCoordinate int y) {
    Graphics2D g2 = (Graphics2D)g.create();
    g2.setFont(font);
    FontMetrics fm = g2.getFontMetrics();
    int padding = JBUIScale.scale(4);
    Rectangle2D bounds = fm.getStringBounds(text, g2);
    double th = bounds.getHeight() + padding * 2;
    double tw = bounds.getWidth() + padding * 2;
    int radius = (int)(Math.min(th, tw) / 3);

    g2.fillRoundRect((int)(x - tw / 2), (int)(y - th / 2), (int)tw, (int)th, radius, radius);
    g2.setColor(textColor);
    g2.drawString(text, (int)(x - tw / 2 + padding), (int)(y - th / 2 + fm.getAscent() + padding));
    if (DEBUG) {
      g2.setColor(Color.RED);
      g2.drawLine(x - 50, y, x + 50, y);
      g2.drawLine(x, y - 50, x, y + 50);
    }
    g2.dispose();
  }

  /**
   * Utility function to draw a circle text centered at coordinates (x, y)
   *
   * @param g    graphics context
   * @param text the text to display
   * @param x    x coordinate
   * @param y    y coordinate
   */
  public static void drawCircledText(Graphics2D g, String text, @SwingCoordinate int x, @SwingCoordinate int y) {
    drawCircledText(g, sSmallFont, text, x, y);
  }

  /**
   * Utility function to draw an horizontal margin indicator
   *
   * @param g    graphics context
   * @param text the text to display
   * @param x1   x1 coordinate
   * @param x2   x2 coordinate
   * @param y    y coordinate
   */
  public static void drawHorizontalMarginIndicator(Graphics2D g,
                                                   String text,
                                                   boolean isMarginReference,
                                                   @SwingCoordinate int x1,
                                                   @SwingCoordinate int x2,
                                                   @SwingCoordinate int y) {
    if (x1 > x2) {
      int temp = x1;
      x1 = x2;
      x2 = temp;
    }

    if (text == null) {
      g.drawLine(x1, y, x2, y);
      g.drawLine(x1, y, x1 + CONNECTION_ARROW_SIZE, y - CONNECTION_ARROW_SIZE);
      g.drawLine(x1, y, x1 + CONNECTION_ARROW_SIZE, y + CONNECTION_ARROW_SIZE);
      g.drawLine(x2, y, x2 - CONNECTION_ARROW_SIZE, y - CONNECTION_ARROW_SIZE);
      g.drawLine(x2, y, x2 - CONNECTION_ARROW_SIZE, y + CONNECTION_ARROW_SIZE);
      return;
    }

    Canvas c = new Canvas();
    Font previousFont = g.getFont();
    Font font = isMarginReference ? sFontReference : sFont;
    FontMetrics fm = c.getFontMetrics(font);
    g.setFont(font);
    int padding = JBUIScale.scale(4);
    Rectangle2D bounds = fm.getStringBounds(text, g);
    int th = (int)bounds.getHeight();
    int tw = (int)bounds.getWidth();

    int offset = 3 * CONNECTION_ARROW_SIZE;

    int w = ((x2 - x1) - (tw + 2 * padding)) / 2;
    if (w <= padding) {
      g.drawLine(x1, y, x2, y);
      g.drawString(text, x1 + w + padding, y + offset);
      g.drawLine(x1, y, x1, y);
      g.drawLine(x2, y, x2, y);
    }
    else {
      g.drawLine(x1, y, x1 + w, y);
      g.drawLine(x2 - w, y, x2, y);
      g.drawString(text, x1 + w + padding, (int)(y + (bounds.getHeight() / 2)));
      if (DRAW_ARROW) {
        g.drawLine(x1, y, x1 + CONNECTION_ARROW_SIZE, y - CONNECTION_ARROW_SIZE);
        g.drawLine(x1, y, x1 + CONNECTION_ARROW_SIZE, y + CONNECTION_ARROW_SIZE);
        g.drawLine(x2, y, x2 - CONNECTION_ARROW_SIZE, y - CONNECTION_ARROW_SIZE);
        g.drawLine(x2, y, x2 - CONNECTION_ARROW_SIZE, y + CONNECTION_ARROW_SIZE);
      }
      else {
        g.drawLine(x1 + 1, y, x1 + 1, y - CONNECTION_ARROW_SIZE);
        g.drawLine(x1 + 1, y, x1 + 1, y + CONNECTION_ARROW_SIZE);
        g.drawLine(x2 - 1, y, x2 - 1, y - CONNECTION_ARROW_SIZE);
        g.drawLine(x2 - 1, y, x2 - 1, y + CONNECTION_ARROW_SIZE);
      }
    }
    g.setFont(previousFont);
  }

  /**
   * Utility function to draw a vertical margin indicator
   *
   * @param g    graphics context
   * @param text the text to display
   * @param x    x coordinate
   * @param y1   y1 coordinate
   * @param y2   y2 coordinate
   */
  public static void drawVerticalMarginIndicator(Graphics2D g,
                                                 String text,
                                                 boolean isMarginReference,
                                                 @SwingCoordinate int x,
                                                 @SwingCoordinate int y1,
                                                 @SwingCoordinate int y2) {
    if (y1 > y2) {
      int temp = y1;
      y1 = y2;
      y2 = temp;
    }
    if (text == null) {
      g.drawLine(x, y1, x, y2);
      if (DRAW_ARROW) {
        g.drawLine(x, y1, x - CONNECTION_ARROW_SIZE, y1 + CONNECTION_ARROW_SIZE);
        g.drawLine(x, y1, x + CONNECTION_ARROW_SIZE, y1 + CONNECTION_ARROW_SIZE);
        g.drawLine(x, y2, x - CONNECTION_ARROW_SIZE, y2 - CONNECTION_ARROW_SIZE);
        g.drawLine(x, y2, x + CONNECTION_ARROW_SIZE, y2 - CONNECTION_ARROW_SIZE);
      }
      else {
        g.drawLine(x, y1, x - CONNECTION_ARROW_SIZE, y1);
        g.drawLine(x, y1, x + CONNECTION_ARROW_SIZE, y1);
        g.drawLine(x, y2, x - CONNECTION_ARROW_SIZE, y2);
        g.drawLine(x, y2, x + CONNECTION_ARROW_SIZE, y2);
      }
      return;
    }
    Canvas c = new Canvas();
    Font previousFont = g.getFont();
    Font font = isMarginReference ? sFontReference : sFont;
    FontMetrics fm = c.getFontMetrics(font);
    g.setFont(font);
    int padding = JBUIScale.scale(4);
    Rectangle2D bounds = fm.getStringBounds(text, g);
    int th = (int)bounds.getHeight();

    int offset = 3 * CONNECTION_ARROW_SIZE;

    int h = ((y2 - y1) - (th + 2 * padding)) / 2;
    if (h <= padding) {
      g.drawLine(x, y1, x, y2);
      g.drawString(text, (int)(x - bounds.getWidth() / 2) + offset, y2 - h - padding);
      g.drawLine(x - CONNECTION_ARROW_SIZE, y1, x + CONNECTION_ARROW_SIZE, y1);
      g.drawLine(x - CONNECTION_ARROW_SIZE, y2, x + CONNECTION_ARROW_SIZE, y2);
    }
    else {
      g.drawLine(x, y1, x, y1 + h);
      g.drawLine(x, y2 - h, x, y2);
      g.drawString(text, (int)(x - bounds.getWidth() / 2), y2 - h - padding);
      if (DRAW_ARROW) {
        g.drawLine(x, y1, x - CONNECTION_ARROW_SIZE, y1 + CONNECTION_ARROW_SIZE);
        g.drawLine(x, y1, x + CONNECTION_ARROW_SIZE, y1 + CONNECTION_ARROW_SIZE);
        g.drawLine(x, y2, x - CONNECTION_ARROW_SIZE, y2 - CONNECTION_ARROW_SIZE);
        g.drawLine(x, y2, x + CONNECTION_ARROW_SIZE, y2 - CONNECTION_ARROW_SIZE);
      }
      else {
        g.drawLine(x, y1 + 1, x - CONNECTION_ARROW_SIZE, y1 + 1);
        g.drawLine(x, y1 + 1, x + CONNECTION_ARROW_SIZE, y1 + 1);
        g.drawLine(x, y2 - 1, x - CONNECTION_ARROW_SIZE, y2 - 1);
        g.drawLine(x, y2 - 1, x + CONNECTION_ARROW_SIZE, y2 - 1);
      }
    }
    g.setFont(previousFont);
  }

  /**
   * Utility function to draw in (x, y) one of the Polygon used for the arrows
   *
   * @param g     Graphics context
   * @param arrow the polygon representing the arrow we want to draw
   * @param x     x coordinate
   * @param y     y coordinate
   */
  public static void drawArrow(Graphics2D g, Polygon arrow, @SwingCoordinate int x, @SwingCoordinate int y) {
    arrow.translate(x, y);
    g.draw(arrow);
    g.fill(arrow);
    arrow.translate(-x, -y);
  }

  public static void getArrow(int direction,
                              @SwingCoordinate int x,
                              @SwingCoordinate int y,
                              @SwingCoordinate int[] xPoints,
                              @SwingCoordinate int[] yPoints) {
    xPoints[0] = x;
    yPoints[0] = y;
    switch (direction) {
      case DrawConnection.DIR_BOTTOM:
        xPoints[1] = x - CONNECTION_ARROW_SIZE;
        xPoints[2] = x + CONNECTION_ARROW_SIZE;
        yPoints[1] = y + ARROW_SIDE;
        yPoints[2] = y + ARROW_SIDE;
        break;
      case DrawConnection.DIR_TOP:
        xPoints[1] = x - CONNECTION_ARROW_SIZE;
        xPoints[2] = x + CONNECTION_ARROW_SIZE;
        yPoints[1] = y - ARROW_SIDE;
        yPoints[2] = y - ARROW_SIDE;
        break;
      case DrawConnection.DIR_LEFT:
        xPoints[1] = x - ARROW_SIDE;
        xPoints[2] = x - ARROW_SIDE;
        yPoints[1] = y - CONNECTION_ARROW_SIZE;
        yPoints[2] = y + CONNECTION_ARROW_SIZE;
        break;
      case DrawConnection.DIR_RIGHT:
        xPoints[1] = x + ARROW_SIDE;
        xPoints[2] = x + ARROW_SIDE;
        yPoints[1] = y - CONNECTION_ARROW_SIZE;
        yPoints[2] = y + CONNECTION_ARROW_SIZE;
        break;
    }
  }

  public static void getSmallArrow(int direction,
                                   @SwingCoordinate int x,
                                   @SwingCoordinate int y,
                                   @SwingCoordinate int[] xPoints,
                                   @SwingCoordinate int[] yPoints) {
    xPoints[0] = x;
    yPoints[0] = y;
    switch (direction) {
      case DrawConnection.DIR_BOTTOM:
        xPoints[1] = x - SMALL_ARROW_SIZE;
        xPoints[2] = x + SMALL_ARROW_SIZE;
        yPoints[1] = y + SMALL_ARROW_SIDE;
        yPoints[2] = y + SMALL_ARROW_SIDE;
        break;
      case DrawConnection.DIR_TOP:
        xPoints[1] = x - SMALL_ARROW_SIZE;
        xPoints[2] = x + SMALL_ARROW_SIZE;
        yPoints[1] = y - SMALL_ARROW_SIDE;
        yPoints[2] = y - SMALL_ARROW_SIDE;
        break;
      case DrawConnection.DIR_LEFT:
        xPoints[1] = x - SMALL_ARROW_SIDE;
        xPoints[2] = x - SMALL_ARROW_SIDE;
        yPoints[1] = y - SMALL_ARROW_SIZE;
        yPoints[2] = y + SMALL_ARROW_SIZE;
        break;
      case DrawConnection.DIR_RIGHT:
        xPoints[1] = x + SMALL_ARROW_SIDE;
        xPoints[2] = x + SMALL_ARROW_SIDE;
        yPoints[1] = y - SMALL_ARROW_SIZE;
        yPoints[2] = y + SMALL_ARROW_SIZE;
        break;
    }
  }

  /**
   * This is used to draw ageneral path
   *
   * @param path
   * @param xPoints
   * @param yPoints
   * @param length
   * @param archLen
   */
  static void drawLines(GeneralPath path, @SwingCoordinate int[] xPoints, @SwingCoordinate int[] yPoints, int length, int archLen) {
    for (int i = 1; i < length; i++) {
      path.lineTo(xPoints[i], yPoints[i]);
    }
  }

  /**
   * removes a zigzag (veering to right and left alternately.) from the list of points.
   * the point are modified and the shortened length is returned
   *
   * @param xPoints  the x coordinates
   * @param yPoints  the y coordinates
   * @param length   the number of element in xPoints and yPoints
   * @param distance maximum distance to be considered a zigzag
   * @return the new length
   */
  static int removeZigZag(@SwingCoordinate int[] xPoints, @SwingCoordinate int[] yPoints, int length, @SwingCoordinate int distance) {
    int dir1 = -1;
    int dir2 = -1;
    int dir3 = -1;
    int dir4 = -1;
    int len2 = distance;
    int len3 = distance;
    int remove = -1;
    for (int i = 0; i < length - 1; i++) {
      int dx = xPoints[i + 1] - xPoints[i];
      int dy = yPoints[i + 1] - yPoints[i];
      if (dx == 0) {
        dir4 = (dy > 0) ? 0 : 2;
      }
      else {
        dir4 = (dx > 0) ? 1 : 3;
      }
      int len4 = Math.abs((dx == 0) ? dy : dx);
      if (dir1 >= 0) {
        if (dir1 == dir3 && dir2 == dir4 && distance > len2) { // if we move in the same direction
          remove = i - 2;
          if (dir1 == 0 || dir1 == 2) {
            yPoints[i - 2] = yPoints[i];
          }
          else {
            xPoints[i - 2] = xPoints[i];
          }
          for (int j = i + 1; j < length; j++) {
            xPoints[j - 2] = xPoints[j];
            yPoints[j - 2] = yPoints[j];
          }
          return length - 2;
        }
      }

      dir1 = dir2;
      dir2 = dir3;
      dir3 = dir4;
      len2 = len3;
      len3 = len4;
    }
    return length;
  }

  /**
   * This will generate a rounded path for a path described by xPoints and yPoints.
   * This will only work if the path consist of only vertical or horizontal lines.
   * it assumes the path has already been moved to the start point
   *
   * @param path    path that will be filled
   * @param xPoints
   * @param yPoints
   * @param length
   * @param archLen
   */
  public static void drawRound(Path2D.Float path, @SwingCoordinate int[] xPoints, @SwingCoordinate int[] yPoints, int length, int archLen) {
    int[] arches = new int[xPoints.length - 1];
    for (int i = 0; i < arches.length; i++) {
      arches[i] = archLen;
    }

    drawRound(path, xPoints, yPoints, length, arches);
  }

  /**
   * This will generate a rounded path for a path described by xPoints and yPoints.
   * This will only work if the path consist of only vertical or horizontal lines.
   * it assumes the path has already been moved to the start point
   *
   * @param path              path that will be filled
   * @param secondarySelector
   * @param startx
   * @param xPoints
   * @param yPoints
   * @param length
   * @param archLen
   */
  public static void drawPick(ScenePicker pick,
                              Object obj,
                              @SwingCoordinate int[] xPoints,
                              @SwingCoordinate int[] yPoints,
                              int length,
                              int archLen) {
    int[] arches = new int[xPoints.length - 1];
    for (int i = 0; i < arches.length; i++) {
      arches[i] = archLen;
    }

    pickRound(pick, obj, xPoints, yPoints, length, arches);
  }

  /**
   * This will generate a rounded path for a path described by xPoints and yPoints.
   * This will only work if the path consist of only vertical or horizontal lines.
   * it assumes the path has already been moved to the start point
   * arches should contain length - 1 elements
   * The ith element in arches represents the radius of the curve between
   * the ith and ith + 1 points in xPoints and yPoints
   *
   * @param path    path that will be filled
   * @param o
   * @param startx
   * @param xPoints
   * @param yPoints
   * @param length
   * @param arches
   */
  public static void pickRound(ScenePicker pick,
                               Object obj,
                               @SwingCoordinate int[] xPoints,
                               @SwingCoordinate int[] yPoints,
                               int length,
                               int[] arches) {
    int lastx = xPoints[0];
    int lasty = yPoints[0];
    int p = 1;
    while (p < length - 1) {
      int d0x = xPoints[p] - lastx;
      int d0y = yPoints[p] - lasty;
      int d1x = xPoints[p + 1] - xPoints[p];
      int d1y = yPoints[p + 1] - yPoints[p];
      int len0 = Math.abs(d0x) + Math.abs(d0y);
      int len1 = Math.abs(d1x) + Math.abs(d1y);

      int d0xs = Integer.signum(d0x);
      int d0ys = Integer.signum(d0y);
      int d1xs = Integer.signum(d1x);
      int d1ys = Integer.signum(d1y);
      int useArch = Math.min(len0 - 2, Math.min(len1 / 2 - 2, arches[p - 1]));
      if (useArch < 2) {
        pick.addLine(obj, 4, lastx, lasty, xPoints[p], yPoints[p], 4);
        lastx = xPoints[p];
        lasty = yPoints[p];
      }
      else {
        pick.addLine(obj, 4, lastx, lasty, xPoints[p] - useArch * d0xs, yPoints[p] - useArch * d0ys, 4);

        lastx = xPoints[p] + useArch * d1xs;
        lasty = yPoints[p] + useArch * d1ys;

        // map direction to degrees / 90
        int dir0 = (d0xs == 0) ? ((d0ys < 1) ? 0 : 2) : (d0xs > 0) ? 1 : 3;
        int dir1 = (d1xs == 0) ? ((d1ys < 1) ? 0 : 2) : (d1xs > 0) ? 1 : 3;
        int rot = (4 + dir1 - dir0) % 4;
        boolean dir = rot == 1;

        pickArc(pick, obj, xPoints[p] - useArch * d0xs, yPoints[p] - useArch * d0ys, lastx, lasty,
                useArch, useArch, 0, false, dir);
        pick.addLine(obj, 4, lastx, lasty, xPoints[p] - useArch * d0xs, yPoints[p] - useArch * d0ys, 4);
      }
      p++;
    }
    pick.addLine(obj, 4, lastx, lasty, xPoints[p], yPoints[p], 4);
  }

  private static void pickArc(ScenePicker pick,
                              Object obj, float x0, float y0, float x1, float y1, float a, float b,
                              float theta, boolean isMoreThanHalf, boolean isPositiveArc) {

    /* Convert rotation angle from degrees to radians */
    double thetaD = theta * Math.PI / 180.0f;
    /* Pre-compute rotation matrix entries */
    double cosTheta = Math.cos(thetaD);
    double sinTheta = Math.sin(thetaD);
    /* Transform (x0, y0) and (x1, y1) into unit space */
    /* using (inverse) rotation, followed by (inverse) scale */
    double x0p = (x0 * cosTheta + y0 * sinTheta) / a;
    double y0p = (-x0 * sinTheta + y0 * cosTheta) / b;
    double x1p = (x1 * cosTheta + y1 * sinTheta) / a;
    double y1p = (-x1 * sinTheta + y1 * cosTheta) / b;

    /* Compute differences and averages */
    double dx = x0p - x1p;
    double dy = y0p - y1p;
    double xm = (x0p + x1p) / 2;
    double ym = (y0p + y1p) / 2;
    /* Solve for intersecting unit circles */
    double dsq = dx * dx + dy * dy;
    if (dsq == 0.0) {

      return; /* Points are coincident */
    }
    double disc = 1.0 / dsq - 1.0 / 4.0;
    if (disc < 0.0) {

      float adjust = (float)(Math.sqrt(dsq) / 1.99999);
      pickArc(pick, obj, x0, y0, x1, y1, a * adjust, b * adjust, theta, isMoreThanHalf, isPositiveArc);
      return; /* Points are too far apart */
    }
    double s = Math.sqrt(disc);
    double sdx = s * dx;
    double sdy = s * dy;
    double cx;
    double cy;
    if (isMoreThanHalf == isPositiveArc) {
      cx = xm - sdy;
      cy = ym + sdx;
    }
    else {
      cx = xm + sdy;
      cy = ym - sdx;
    }

    double eta0 = Math.atan2((y0p - cy), (x0p - cx));
    double eta1 = Math.atan2((y1p - cy), (x1p - cx));
    double sweep = (eta1 - eta0);
    if (isPositiveArc != (sweep >= 0)) {
      if (sweep > 0) {
        sweep -= 2 * Math.PI;
      }
      else {
        sweep += 2 * Math.PI;
      }
    }

    cx *= a;
    cy *= b;
    double tcx = cx;
    cx = cx * cosTheta - cy * sinTheta;
    cy = tcx * sinTheta + cy * cosTheta;

    pickArcToBezier(pick, obj, cx, cy, a, b, x0, y0, thetaD, eta0, sweep);
  }

  /**
   * Converts an arc to cubic Bezier segments and records them in p.
   *
   * @param pick  The target for the cubic Bezier segments
   * @param cx    The x coordinate center of the ellipse
   * @param cy    The y coordinate center of the ellipse
   * @param a     The radius of the ellipse in the horizontal direction
   * @param b     The radius of the ellipse in the vertical direction
   * @param e1x   E(eta1) x coordinate of the starting point of the arc
   * @param e1y   E(eta2) y coordinate of the starting point of the arc
   * @param theta The angle that the ellipse bounding rectangle makes with the horizontal plane
   * @param start The start angle of the arc on the ellipse
   * @param sweep The angle (positive or negative) of the sweep of the arc on the ellipse
   */
  private static void pickArcToBezier(ScenePicker pick,
                                      Object obj, double cx, double cy, double a, double b, double e1x,
                                      double e1y, double theta, double start, double sweep) {
    // Taken from equations at:
    // http://spaceroots.org/documents/ellipse/node8.html
    // and http://www.spaceroots.org/documents/ellipse/node22.html

    // Maximum of 45 degrees per cubic Bezier segment
    int numSegments = Math.abs((int)Math.ceil(sweep * 4 / Math.PI));

    double eta1 = start;
    double cosTheta = Math.cos(theta);
    double sinTheta = Math.sin(theta);
    double cosEta1 = Math.cos(eta1);
    double sinEta1 = Math.sin(eta1);
    double ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1);
    double ep1y = (-a * sinTheta * sinEta1) + (b * cosTheta * cosEta1);

    double anglePerSegment = sweep / numSegments;
    for (int i = 0; i < numSegments; i++) {
      double eta2 = eta1 + anglePerSegment;
      double sinEta2 = Math.sin(eta2);
      double cosEta2 = Math.cos(eta2);
      double e2x = cx + (a * cosTheta * cosEta2) - (b * sinTheta * sinEta2);
      double e2y = cy + (a * sinTheta * cosEta2) + (b * cosTheta * sinEta2);
      double ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2;
      double ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2;
      double tanDiff2 = Math.tan((eta2 - eta1) / 2);
      double alpha = Math.sin(eta2 - eta1) * (Math.sqrt(4 + (3 * tanDiff2 * tanDiff2)) - 1) / 3;
      double q1x = e1x + alpha * ep1x;
      double q1y = e1y + alpha * ep1y;
      double q2x = e2x - alpha * ep2x;
      double q2y = e2y - alpha * ep2y;
      pick.addCurveTo(obj, 4, (int)e1x, (int)e1y, (int)q1x, (int)q1y, (int)q2x, (int)q2y, (int)e2x, (int)e2y, 4);
      eta1 = eta2;
      e1x = e2x;
      e1y = e2y;
      ep1x = ep2x;
      ep1y = ep2y;
    }
  }

  /**
   * This will generate a rounded path for a path described by xPoints and yPoints.
   * This will only work if the path consist of only vertical or horizontal lines.
   * it assumes the path has already been moved to the start point
   * arches should contain length - 1 elements
   * The ith element in arches represents the radius of the curve between
   * the ith and ith + 1 points in xPoints and yPoints
   *
   * @param path    path that will be filled
   * @param xPoints
   * @param yPoints
   * @param length
   * @param arches
   */
  public static void drawRound(Path2D.Float path,
                               @SwingCoordinate int[] xPoints, @SwingCoordinate int[] yPoints,
                               int length, int[] arches) {
    int lastx = xPoints[0];
    int lasty = yPoints[0];
    int p = 1;
    while (p < length - 1) {
      int d0x = xPoints[p] - lastx;
      int d0y = yPoints[p] - lasty;
      int d1x = xPoints[p + 1] - xPoints[p];
      int d1y = yPoints[p + 1] - yPoints[p];
      int len0 = Math.abs(d0x) + Math.abs(d0y);
      int len1 = Math.abs(d1x) + Math.abs(d1y);

      int d0xs = Integer.signum(d0x);
      int d0ys = Integer.signum(d0y);
      int d1xs = Integer.signum(d1x);
      int d1ys = Integer.signum(d1y);
      int useArch = Math.min(len0 - 2, Math.min(len1 / 2 - 2, arches[p - 1]));
      if (useArch < 2) {
        path.lineTo(xPoints[p], yPoints[p]);
        lastx = xPoints[p];
        lasty = yPoints[p];
      }
      else {
        path.lineTo(xPoints[p] - useArch * d0xs, yPoints[p] - useArch * d0ys);
        lastx = xPoints[p] + useArch * d1xs;
        lasty = yPoints[p] + useArch * d1ys;

        // map direction to degrees / 90
        int dir0 = (d0xs == 0) ? ((d0ys < 1) ? 0 : 2) : (d0xs > 0) ? 1 : 3;
        int dir1 = (d1xs == 0) ? ((d1ys < 1) ? 0 : 2) : (d1xs > 0) ? 1 : 3;
        int rot = (4 + dir1 - dir0) % 4;
        boolean dir = rot == 1;

        drawArc(path, xPoints[p] - useArch * d0xs, yPoints[p] - useArch * d0ys, lastx, lasty,
                useArch, useArch, 0, false, dir);
        path.lineTo(lastx, lasty);
      }
      p++;
    }
    path.lineTo(xPoints[p], yPoints[p]);
  }

  private static void drawArc(Path2D p, float x0, float y0, float x1, float y1, float a, float b,
                              float theta, boolean isMoreThanHalf, boolean isPositiveArc) {

    /* Convert rotation angle from degrees to radians */
    double thetaD = theta * Math.PI / 180.0f;
    /* Pre-compute rotation matrix entries */
    double cosTheta = Math.cos(thetaD);
    double sinTheta = Math.sin(thetaD);
    /* Transform (x0, y0) and (x1, y1) into unit space */
    /* using (inverse) rotation, followed by (inverse) scale */
    double x0p = (x0 * cosTheta + y0 * sinTheta) / a;
    double y0p = (-x0 * sinTheta + y0 * cosTheta) / b;
    double x1p = (x1 * cosTheta + y1 * sinTheta) / a;
    double y1p = (-x1 * sinTheta + y1 * cosTheta) / b;

    /* Compute differences and averages */
    double dx = x0p - x1p;
    double dy = y0p - y1p;
    double xm = (x0p + x1p) / 2;
    double ym = (y0p + y1p) / 2;
    /* Solve for intersecting unit circles */
    double dsq = dx * dx + dy * dy;
    if (dsq == 0.0) {

      return; /* Points are coincident */
    }
    double disc = 1.0 / dsq - 1.0 / 4.0;
    if (disc < 0.0) {

      float adjust = (float)(Math.sqrt(dsq) / 1.99999);
      drawArc(p, x0, y0, x1, y1, a * adjust, b * adjust, theta, isMoreThanHalf, isPositiveArc);
      return; /* Points are too far apart */
    }
    double s = Math.sqrt(disc);
    double sdx = s * dx;
    double sdy = s * dy;
    double cx;
    double cy;
    if (isMoreThanHalf == isPositiveArc) {
      cx = xm - sdy;
      cy = ym + sdx;
    }
    else {
      cx = xm + sdy;
      cy = ym - sdx;
    }

    double eta0 = Math.atan2((y0p - cy), (x0p - cx));
    double eta1 = Math.atan2((y1p - cy), (x1p - cx));
    double sweep = (eta1 - eta0);
    if (isPositiveArc != (sweep >= 0)) {
      if (sweep > 0) {
        sweep -= 2 * Math.PI;
      }
      else {
        sweep += 2 * Math.PI;
      }
    }

    cx *= a;
    cy *= b;
    double tcx = cx;
    cx = cx * cosTheta - cy * sinTheta;
    cy = tcx * sinTheta + cy * cosTheta;

    arcToBezier(p, cx, cy, a, b, x0, y0, thetaD, eta0, sweep);
  }

  /**
   * Converts an arc to cubic Bezier segments and records them in p.
   *
   * @param p     The target for the cubic Bezier segments
   * @param cx    The x coordinate center of the ellipse
   * @param cy    The y coordinate center of the ellipse
   * @param a     The radius of the ellipse in the horizontal direction
   * @param b     The radius of the ellipse in the vertical direction
   * @param e1x   E(eta1) x coordinate of the starting point of the arc
   * @param e1y   E(eta2) y coordinate of the starting point of the arc
   * @param theta The angle that the ellipse bounding rectangle makes with the horizontal plane
   * @param start The start angle of the arc on the ellipse
   * @param sweep The angle (positive or negative) of the sweep of the arc on the ellipse
   */
  private static void arcToBezier(Path2D p, double cx, double cy, double a, double b, double e1x,
                                  double e1y, double theta, double start, double sweep) {
    // Taken from equations at:
    // http://spaceroots.org/documents/ellipse/node8.html
    // and http://www.spaceroots.org/documents/ellipse/node22.html

    // Maximum of 45 degrees per cubic Bezier segment
    int numSegments = Math.abs((int)Math.ceil(sweep * 4 / Math.PI));

    double eta1 = start;
    double cosTheta = Math.cos(theta);
    double sinTheta = Math.sin(theta);
    double cosEta1 = Math.cos(eta1);
    double sinEta1 = Math.sin(eta1);
    double ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1);
    double ep1y = (-a * sinTheta * sinEta1) + (b * cosTheta * cosEta1);

    double anglePerSegment = sweep / numSegments;
    for (int i = 0; i < numSegments; i++) {
      double eta2 = eta1 + anglePerSegment;
      double sinEta2 = Math.sin(eta2);
      double cosEta2 = Math.cos(eta2);
      double e2x = cx + (a * cosTheta * cosEta2) - (b * sinTheta * sinEta2);
      double e2y = cy + (a * sinTheta * cosEta2) + (b * cosTheta * sinEta2);
      double ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2;
      double ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2;
      double tanDiff2 = Math.tan((eta2 - eta1) / 2);
      double alpha = Math.sin(eta2 - eta1) * (Math.sqrt(4 + (3 * tanDiff2 * tanDiff2)) - 1) / 3;
      double q1x = e1x + alpha * ep1x;
      double q1y = e1y + alpha * ep1y;
      double q2x = e2x - alpha * ep2x;
      double q2y = e2y - alpha * ep2y;

      p.curveTo((float)q1x, (float)q1y, (float)q2x, (float)q2y, (float)e2x, (float)e2y);
      eta1 = eta2;
      e1x = e2x;
      e1y = e2y;
      ep1x = ep2x;
      ep1y = ep2y;
    }
  }

  /**
   * Add an vertical spring between (x0, y1) and (x0, y1) to the given path object
   *
   * @param path the path object we'll add the spring to
   * @param x0   the x coordinate of the spring
   * @param y1   the y start coordinate
   * @param y2   the y end coordiante
   */
  public static void addVerticalSmallSpring(Path2D.Float path, @SwingCoordinate int x0, @SwingCoordinate int y1, @SwingCoordinate int y2) {
    int springHeight = JBUIScale.scale(2);
    int springWidth = JBUIScale.scale(2);
    int distance = Math.abs(y2 - y1);
    int numSprings = (distance / (springHeight));
    int leftOver = (distance - (numSprings * springHeight)) / 2;
    path.lineTo(x0, y1);
    path.lineTo(x0, y1 - leftOver);
    int count = 0;
    if (y1 > y2) {
      for (int y = y1 - leftOver; y > y2 + leftOver; y -= springHeight) {
        int x = (count % 2 == 0) ? x0 - springWidth : x0 + springWidth;
        path.lineTo(x, y);
        count++;
      }
    }
    else {
      for (int y = y1 + leftOver; y < y2 - leftOver; y += springHeight) {
        int x = (count % 2 == 0) ? x0 - springWidth : x0 + springWidth;
        path.lineTo(x, y);
        count++;
      }
    }
    path.lineTo(x0, y2 + leftOver);
    path.lineTo(x0, y2);
  }

  /**
   * Add an horizontal spring between (x1, y0) and (x2, y0) to the given path object
   *
   * @param path the path object we'll add the spring to
   * @param y0   the y coordinate of the spring
   * @param x1   the x start coordinate
   * @param x2   the x end coordiante
   */
  public static void addHorizontalSmallSpring(Path2D.Float path,
                                              @SwingCoordinate int y0,
                                              @SwingCoordinate int x1,
                                              @SwingCoordinate int x2) {
    int springHeight = JBUIScale.scale(2);
    int springWidth = JBUIScale.scale(2);
    int distance = Math.abs(x2 - x1);
    int numSprings = (distance / (springHeight));
    int leftOver = (distance - (numSprings * springHeight)) / 2;
    path.lineTo(x1, y0);
    path.lineTo(x1 - leftOver, y0 - leftOver);
    int count = 0;
    if (x1 > x2) {
      for (int x = x1 - leftOver; x > x2 + leftOver; x -= springHeight) {
        int y = (count % 2 == 0) ? y0 - springWidth : y0 + springWidth;
        path.lineTo(x, y);
        count++;
      }
    }
    else {
      for (int x = x1 + leftOver; x < x2 - leftOver; x += springHeight) {
        int y = (count % 2 == 0) ? y0 - springWidth : y0 + springWidth;
        path.lineTo(x, y);
        count++;
      }
    }
    path.lineTo(x2 + leftOver, y0);
    path.lineTo(x2, y0);
  }

  /**
   * Draw an horizontal, centered zig-zag line.
   * The color and style used for the drawing will be the current ones in the graphics context.
   *
   * @param path path that will be added to
   * @param x1   start x point
   * @param x2   end x point
   * @param y    y point
   */
  public static void drawHorizontalZigZagLine(Path2D.Float path, @SwingCoordinate int x1, @SwingCoordinate int x2, @SwingCoordinate int y) {
    drawHorizontalZigZagLine(path, x1, x2, y, CENTER_ZIGZAG, CENTER_ZIGZAG);
  }

  /**
   * Draw an horizontal zig-zag line.
   * The color and style used for the drawing will be the current ones in the graphics context.
   *
   * @param path path that will be added to
   * @param x1   start x point
   * @param x2   end x point
   * @param y    y point
   * @param dY1  positive height of the zig-zag
   * @param dY2  negative height of the zig-zag
   */
  static void drawHorizontalZigZagLine(Path2D.Float path,
                                       @SwingCoordinate int x1,
                                       @SwingCoordinate int x2,
                                       @SwingCoordinate int y,
                                       @SwingCoordinate int dY1,
                                       @SwingCoordinate int dY2) {
    if (x2 < x1) {
      int temp = x1;
      x1 = x2;
      x2 = temp;
    }
    int distance = x2 - x1;
    int step = ZIGZAG * 2 + (dY2 > 0 ? ZIGZAG : 0);
    int count = (distance / step) - 2;
    int remainings = distance - (count * step);
    int x = x1 + remainings / 2;
    path.moveTo(x1, y);
    path.lineTo(x, y);
    for (int i = 0; i < count; i++) {
      path.lineTo(x + ZIGZAG, y + dY1);
      path.lineTo(x + 2 * ZIGZAG, y - dY2);
      if (dY2 != 0) {
        path.lineTo(x + 3 * ZIGZAG, y);
      }
      x += step;
    }
    path.lineTo(x2, y);
  }

  /**
   * Draw a vertical, centered zig-zag line.
   * The color and style used for the drawing will be the current ones in the graphics context.
   *
   * @param path path that will be added to
   * @param x    x point
   * @param y1   start y point
   * @param y2   end y point
   */
  public static void drawVerticalZigZagLine(Path2D.Float path, @SwingCoordinate int x, @SwingCoordinate int y1, @SwingCoordinate int y2) {
    drawVerticalZigZagLine(path, x, y1, y2, CENTER_ZIGZAG, CENTER_ZIGZAG);
  }

  /**
   * Draw a vertical zig-zag line.
   * The color and style used for the drawing will be the current ones in the graphics context.
   *
   * @param path path that will be added to
   * @param x    x point
   * @param y1   start y point
   * @param y2   end y point
   * @param dX1  positive width of the zig-zag
   * @param dX2  negative width of the zig-zag
   */
  static void drawVerticalZigZagLine(Path2D.Float path,
                                     @SwingCoordinate int x,
                                     @SwingCoordinate int y1,
                                     @SwingCoordinate int y2,
                                     @SwingCoordinate int dX1,
                                     @SwingCoordinate int dX2) {
    if (y2 < y1) {
      int temp = y1;
      y1 = y2;
      y2 = temp;
    }
    int distance = y2 - y1;
    int step = ZIGZAG * 2 + (dX2 > 0 ? ZIGZAG : 0);
    int count = (distance / step) - 2;
    int remainings = distance - (count * step);
    int y = y1 + remainings / 2;
    path.moveTo(x, y1);
    path.lineTo(x, y);
    for (int i = 0; i < count; i++) {
      path.lineTo(x + dX1, y + ZIGZAG);
      path.lineTo(x - dX2, y + 2 * ZIGZAG);
      if (dX2 != 0) {
        path.lineTo(x, y + 3 * ZIGZAG);
      }
      y += step;
    }
    path.lineTo(x, y2);
  }

  public static int getVerticalMarginGap(Graphics2D g) {
    g.setFont(sFont);
    return g.getFontMetrics().getHeight() + 2 * MARGIN_SPACING;
  }

  /**
   * Get horizontal gap needed to draw the margin
   *
   * @param g
   * @param string
   * @return
   */
  public static int getHorizontalMarginGap(Graphics2D g, String string) {
    g.setFont(sFont);
    return (int)(g.getFontMetrics().getStringBounds(string, g).getWidth() + 2 * MARGIN_SPACING);
  }

  /**
   * draw the horizontal margin text and line.
   *
   * @param string The margin value to display. May be null.
   */
  public static void drawHorizontalMargin(@NotNull Graphics2D g,
                                          @Nullable String string,
                                          boolean isReference,
                                          @SwingCoordinate int x1,
                                          @SwingCoordinate int x2,
                                          @SwingCoordinate int y) {
    g.drawLine(x1, y, x2, y);

    drawHorizontalMarginString(g, /* stringColor= */ null, string, isReference, x1, x2, y);
  }

  /**
   * Draw the text of an horizontal margin line.
   *
   * @param stringColor The color of the text, may be null and use current color.
   * @param string      The margin value to display. May be null.
   */
  public static void drawHorizontalMarginString(@NotNull Graphics2D g,
                                                @Nullable Color stringColor,
                                                @Nullable String string,
                                                boolean isReference,
                                                @SwingCoordinate int x1,
                                                @SwingCoordinate int x2,
                                                @SwingCoordinate int y) {
    if (stringColor != null) {
      g.setColor(stringColor);
    }

    if (string != null) {
      Font previousFont = g.getFont();
      g.setFont(sFont);
      FontMetrics metrics = g.getFontMetrics();
      Rectangle2D rect = metrics.getStringBounds(string, g);
      float sx = (float)((x1 + x2) / 2 - rect.getWidth() / 2);
      float sy = (float)(y - MARGIN_SPACING - metrics.getDescent());
      if (isReference) {
        g.setFont(sFontReference);
      }
      g.drawString(string, sx, sy);
      g.setFont(previousFont);
    }
  }

  /**
   * draw the vertical margin text and line.
   *
   * @param string The margin value to display. May be null.
   */
  public static void drawVerticalMargin(@NotNull Graphics2D g,
                                        @Nullable String string,
                                        boolean isReference,
                                        @SwingCoordinate int x,
                                        @SwingCoordinate int y1,
                                        @SwingCoordinate int y2) {
    g.drawLine(x, y1, x, y2);

    drawVerticalMarginString(g, /* stringColor= */ null, string, isReference, x, y1, y2);
  }

  /**
   * Draw the text of a vertical margin line.
   *
   * @param stringColor The color of the text, may be null and use current color.
   * @param string      The margin value to display. May be null.
   */
  public static void drawVerticalMarginString(@NotNull Graphics2D g,
                                                @Nullable Color stringColor,
                                                @Nullable String string,
                                                boolean isReference,
                                                @SwingCoordinate int x,
                                                @SwingCoordinate int y1,
                                                @SwingCoordinate int y2) {
    if (stringColor != null) {
      g.setColor(stringColor);
    }

    if (string != null) {
      Font previousFont = g.getFont();
      g.setFont(sFont);
      FontMetrics metrics = g.getFontMetrics();
      Rectangle2D rect = metrics.getStringBounds(string, g);
      float sx = (float)(x + MARGIN_SPACING);
      float sy = (float)((y2 + y1) / 2 + rect.getHeight() / 2 - metrics.getDescent());
      if (isReference) {
        g.setFont(sFontReference);
      }
      g.drawString(string, sx, sy);
      g.setFont(previousFont);
    }
  }
}
