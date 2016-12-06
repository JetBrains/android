/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene.draw;

import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.sherpa.drawing.ColorSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * This class is the display list entry for drawing a connection
 * it also has the method which assembles the DrawConnection display list element
 * given a SceneComponent
 */
public class DrawConnection implements DrawCommand {
  public static final int TYPE_NORMAL = 1; // normal connections
  public static final int TYPE_SPRING = 2; // connected on both sides
  public static final int TYPE_CHAIN = 3;  // connected such that anchor connects back
  public static final int TYPE_CENTER = 4; // connected on both sides to the same point
  public static final int TYPE_BASELINE = 5;  // connected such that anchor connects back

  public static final int DIR_LEFT = 0;
  public static final int DIR_RIGHT = 1;
  public static final int DIR_TOP = 2;
  public static final int DIR_BOTTOM = 3;
  private static final int OVER_HANG = 20;
  static GeneralPath ourPath = new GeneralPath();
  final static int[] dirDeltaX = {-1, +1, 0, 0};
  final static int[] dirDeltaY = {0, 0, -1, 1};
  final static int[] ourOppositeDirection = {1, 0, 3, 2};
  public static final int GAP = 10;
  int myConnectionType;
  Rectangle mySource = new Rectangle();
  int mySourceDirection;
  Rectangle myDest = new Rectangle();
  int myDestDirection;
  boolean myToParent;
  boolean myShift;
  int myMargin;
  float myBias;
  static Stroke myDashStroke = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{4, 6}, 0f);
  static Stroke mySpringStroke = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{4, 4}, 0f);
  static Stroke myChainStroke1 = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{5, 5}, 0f);
  static Stroke myChainStroke2 = new BasicStroke(4, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{5, 5}, 5f);


  @Override
  public int getLevel() {
    return CONNECTION_LEVEL;
  }

  @Override
  public int compareTo(@NotNull Object o) {
    return Integer.compare(getLevel(), ((DrawCommand)o).getLevel());
  }

  @Override
  public String serialize() {
    return "DrawConnection," + myConnectionType + "," + rectToString(mySource) + "," +
           mySourceDirection + "," + rectToString(myDest) + "," + myDestDirection + "," +
           myToParent + "," + myShift + "," + myMargin + "," + myBias;
  }

  private static String rectToString(Rectangle r) {
    return r.x + "x" + r.y + "x" + r.width + "x" + r.height;
  }

  private static Rectangle stringToRect(String s) {
    String[] sp = s.split("x");
    int c = 0;
    Rectangle r = new Rectangle();
    r.x = Integer.parseInt(sp[c++]);
    r.y = Integer.parseInt(sp[c++]);
    r.width = Integer.parseInt(sp[c++]);
    r.height = Integer.parseInt(sp[c++]);
    return r;
  }

  public DrawConnection(String s) {
    String[] sp = s.split(",");
    int c = 0;
    myConnectionType = Integer.parseInt(sp[c++]);
    mySource = stringToRect(sp[c++]);
    mySourceDirection = Integer.parseInt(sp[c++]);
    myDest = stringToRect(sp[c++]);
    myDestDirection = Integer.parseInt(sp[c++]);
    myToParent = Boolean.parseBoolean(sp[c++]);
    myShift = Boolean.parseBoolean(sp[c++]);
    myMargin = Integer.parseInt(sp[c++]);
    myBias = Float.parseFloat(sp[c++]);
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet color = sceneContext.getColorSet();
    g.setColor(color.getConstraints());
    draw(g, myConnectionType, mySource, mySourceDirection, myDest, myDestDirection, myToParent, myMargin, myBias);
  }


  public DrawConnection(int connectionType,
                        Rectangle source,
                        int sourceDirection,
                        Rectangle dest,
                        int destDirection,
                        boolean toParent,
                        boolean shift,
                        int margin,
                        Float bias) {
    config(connectionType, source, sourceDirection, dest, destDirection, toParent, shift, margin, bias);
  }

  public static void buildDisplayList(DisplayList list,
                                      int connectionType,
                                      Rectangle source,
                                      int sourceDirection,
                                      Rectangle dest,
                                      int destDirection,
                                      boolean toParent,
                                      boolean shift,
                                      int margin,
                                      Float bias) {
    list.add(new DrawConnection(connectionType, source, sourceDirection, dest, destDirection, toParent, shift, margin, bias));
  }

  public void config(int connectionType,
                     Rectangle source,
                     int sourceDirection,
                     Rectangle dest,
                     int destDirection,
                     boolean toParent,
                     boolean shift,
                     int margin,
                     Float bias) {
    mySource.setBounds(source);
    myDest.setBounds(dest);
    myConnectionType = connectionType;
    mySource.setBounds(source);
    mySourceDirection = sourceDirection;
    myDest.setBounds(dest);
    myDestDirection = destDirection;
    myToParent = toParent;
    myShift = shift;
    myMargin = margin;
    myBias = bias;
  }

  public static void draw(Graphics2D g,
                          int connectionType,
                          Rectangle source,
                          int sourceDirection,
                          Rectangle dest,
                          int destDirection,
                          boolean toParent,
                          int margin, float bias) {
    if (connectionType==TYPE_BASELINE) {
      drawBaseLine( g, source, dest);
    }
    int startx = getConnectionX(sourceDirection, source);
    int starty = getConnectionY(sourceDirection, source);
    int endx = getConnectionX(destDirection, dest);
    int endy = getConnectionY(destDirection, dest);
    int dx = getDestinationDX(destDirection, dest, toParent, 0);
    int dy = getDestinationDY(destDirection, dest, toParent, 0);
    int x1 = startx;
    int y1 = starty;

    int scale_source = 40;
    int scale_dest = (toParent) ? -40 : 40;
    boolean flip_arrow = false;
    if (toParent) {
      switch (destDirection) {
        case DIR_BOTTOM:
        case DIR_TOP:
          endx = startx;
          break;
        case DIR_LEFT:
        case DIR_RIGHT:
          endy = starty;
          break;
      }
    }
    else {
      if (sourceDirection == destDirection) {
        switch (destDirection) {
          case DIR_BOTTOM:
            if (endy - 1 > starty) {
              scale_dest *= -1;
              dy *= -1;
              flip_arrow = true;
            }
            break;
          case DIR_TOP:
            if (endy < starty) {
              scale_dest *= -1;
              dy *= -1;
              flip_arrow = true;
            }
            break;
          case DIR_LEFT:
            if (endx  < startx) {
              scale_dest *= -1;
              dx *= -1;
              flip_arrow = true;
            }
            break;
          case DIR_RIGHT:
            if (endx - 1 > startx) {
              scale_dest *= -1;
              dx *= -1;
              flip_arrow = true;
            }
            break;
        }
      }
    }

    int[] xPoints = new int[3];
    int[] yPoints = new int[3];

    int dir = (toParent  ^ flip_arrow) ? ourOppositeDirection[destDirection] : destDirection;
    DrawConnectionUtils.getArrow(dir, endx, endy, xPoints, yPoints);
    g.fillPolygon(xPoints, yPoints, 3);

    ourPath.reset();
    ourPath.moveTo(startx, starty);
    switch (connectionType) {
      case TYPE_CHAIN:
        DrawConnectionUtils.getArrow(sourceDirection, startx, starty, xPoints, yPoints);
        g.fillPolygon(xPoints, yPoints, 3);
        ourPath.moveTo(startx - dx, starty - dy);
        ourPath.curveTo(startx - dx + scale_source * dirDeltaX[sourceDirection], starty - dy + scale_source * dirDeltaY[sourceDirection],
                        endx + dx + scale_dest * dirDeltaX[destDirection], endy + dy + scale_dest * dirDeltaY[destDirection],
                        endx + dx, endy + dy);
        Stroke defaultStroke = g.getStroke();
        g.setStroke(myChainStroke1);
        g.draw(ourPath);
        g.setStroke(myChainStroke2);
        g.draw(ourPath);
        g.setStroke(defaultStroke);
        break;
      case TYPE_SPRING:
        if (toParent) {
          ourPath.lineTo(endx, endy);
        }
        else {
          ourPath.curveTo(startx + scale_source * dirDeltaX[sourceDirection],
                          starty + scale_source * dirDeltaY[sourceDirection],
                          endx + dx + scale_dest * dirDeltaX[destDirection],
                          endy + dy + scale_dest * dirDeltaY[destDirection],
                          endx + dx, endy + dy);
        }
        defaultStroke = g.getStroke();
        g.setStroke(mySpringStroke);
        g.draw(ourPath);
        g.setStroke(defaultStroke);
        break;
      case TYPE_CENTER:

        int dir0_x = 0, dir0_y = 0; // direction of the start
        int dir1_x = 0, dir1_y = 0; // direction the arch must go
        int dir2_x = 0, dir2_y = 0;
        int p6x, p6y; // position of the 6'th point on the curve
        if (destDirection == DIR_LEFT || destDirection == DIR_RIGHT) {

          dir0_x = (sourceDirection == DIR_LEFT) ? -1 : 1;
          dir1_y = (endy > starty) ? 1 : -1;
          dir2_x = (destDirection == DIR_LEFT) ? -1 : 1;
          p6x = (destDirection == DIR_LEFT)
                ? endx - GAP * 2
                : endx + GAP * 2;
          p6y = starty + dir0_y * GAP + (source.height / 2 + GAP) * dir1_y;
          int vline_y1 = -1,vline_y2 = -1;
             if (source.y > dest.y+dest.height) {
              vline_y1 = dest.y+dest.height;
              vline_y2 = source.y;
            }
          if (source.y +source.height < dest.y) {
            vline_y1 = source.y +source.height;
            vline_y2 = dest.y;
          }
          if (vline_y1!=-1) {
            Stroke stroke = g.getStroke();
            g.setStroke(myDashStroke);
            int xpos = source.x+source.width/2;
            g.drawLine(xpos,vline_y1,xpos,vline_y2);
            g.setStroke(stroke);
          }

        }
        else {

          dir1_x = (endx > startx) ? 1 : -1;
          dir0_y = (sourceDirection == DIR_TOP) ? -1 : 1;
          dir2_y = (destDirection == DIR_TOP) ? -1 : 1;
          p6y = (destDirection == DIR_TOP)
                ? endy - GAP * 2
                : endy + GAP * 2;
          p6x = startx + dir0_x * GAP + (source.width / 2 + GAP) * dir1_x;

          int vline_x1 = -1,vline_x2 = -1;
          if (source.x > dest.x+dest.width) {
            vline_x1 = dest.x+dest.width;
            vline_x2 = source.x;
          }
          if (source.x +source.width < dest.x) {
            vline_x1 = source.x +source.width;
            vline_x2 = dest.x;
          }
          if (vline_x1!=-1) {
            Stroke stroke = g.getStroke();
            g.setStroke(myDashStroke);
            int ypos = source.y+source.height/2;
            g.drawLine(vline_x1,ypos,vline_x2,ypos);
            g.setStroke(stroke);
          }

        }
        int[] px = new int[6];
        int[] py = new int[6];
        px[0] = startx;
        py[0] = starty;
        px[1] = startx + dir0_x * GAP;
        py[1] = starty + dir0_y * GAP;
        px[2] = px[1] + (source.width / 2 + GAP) * dir1_x;
        py[2] = py[1] + (source.height / 2 + GAP) * dir1_y;
        px[3] = p6x;
        py[3] = p6y;
        px[4] = endx + 2 * dir2_x * GAP;
        py[4] = endy + 2 * dir2_y * GAP;
        px[5] = endx;
        py[5] = endy;

        DrawConnectionUtils.drawRound(ourPath, px, py, 6, GAP);

        g.draw(ourPath);
        break;
      case TYPE_NORMAL:
        if (margin > 0) {
          if (sourceDirection == DIR_RIGHT || sourceDirection == DIR_LEFT) {
            boolean above = starty < endy;
            int line_y = starty + ((above) ? -(source.height) / 4 : (source.height) / 4);
            DrawConnectionUtils.drawHorizontalMarginIndicator(g, "" + margin, startx, endx, line_y);
            if (!toParent || (line_y < dest.y || line_y > dest.y + dest.height)) {
              int constraintX = (destDirection == DIR_LEFT) ? dest.x : dest.x + dest.width;
              Stroke stroke = g.getStroke();
              g.setStroke(myDashStroke);
              int overlap = (above) ? -OVER_HANG : OVER_HANG;
              g.drawLine(constraintX, line_y + overlap, constraintX, above ? dest.y : dest.y + dest.height);
              g.setStroke(stroke);
            }
          }
          else {
            boolean left = startx < endx;
            int line_x = startx + ((left) ? -(source.width) / 4 : (source.width) / 4);
            DrawConnectionUtils.drawVerticalMarginIndicator(g, "" + margin, line_x, starty, endy);
            if (!toParent || (line_x < dest.x || line_x > dest.x + dest.width)) {
              int constraint_y = (destDirection == DIR_TOP) ? dest.y : dest.y + dest.height;
              Stroke stroke = g.getStroke();
              g.setStroke(myDashStroke);
              int overlap = (left) ? -OVER_HANG : OVER_HANG;
              g.drawLine(line_x + overlap, constraint_y,
                         left ? dest.x : dest.x + dest.width, constraint_y);
              g.setStroke(stroke);
            }
          }
        }
        ourPath.curveTo(startx + scale_source * dirDeltaX[sourceDirection], starty + scale_source * dirDeltaY[sourceDirection],
                        endx + dx + scale_dest * dirDeltaX[destDirection], endy + dy + scale_dest * dirDeltaY[destDirection],
                        endx + dx, endy + dy);

        g.draw(ourPath);
    }
  }

  private static void drawBaseLine(Graphics2D g,
                                   Rectangle source,
                                   Rectangle dest){
    ourPath.reset();
    ourPath.moveTo(source.x+source.width/2,source.y);
    ourPath.curveTo(  source.x+source.width/2,source.y-40,
                      dest.x+dest.width/2,dest.y+40,
                      dest.x+dest.width/2,dest.y);
    g.draw(ourPath);

  }

  private static int getConnectionX(int side, Rectangle rect) {
    switch (side) {
      case DIR_LEFT:
        return rect.x;
      case DIR_RIGHT:
        return rect.x + rect.width;
      case DIR_TOP:
      case DIR_BOTTOM:
        return rect.x + rect.width / 2;
    }
    return 0;
  }

  private static int getConnectionY(int side, Rectangle rect) {
    switch (side) {
      case DIR_LEFT:
      case DIR_RIGHT:
        return rect.y + rect.height / 2;
      case DIR_TOP:
        return rect.y;
      case DIR_BOTTOM:
        return rect.y + rect.height;
    }
    return 0;
  }

  private static int getDestinationDX(int side, Rectangle rect, boolean toParent, int shift) {
    switch (side) {
      case DIR_LEFT:
        return -DrawConnectionUtils.ARROW_SIDE;
      case DIR_RIGHT:
        return +DrawConnectionUtils.ARROW_SIDE;
    }
    return 0;
  }

  private static int getDestinationDY(int side, Rectangle rect, boolean toParent, int shift) {
    switch (side) {
      case DIR_TOP:
        return -DrawConnectionUtils.ARROW_SIDE;
      case DIR_BOTTOM:
        return +DrawConnectionUtils.ARROW_SIDE;
    }
    return 0;
  }
}
