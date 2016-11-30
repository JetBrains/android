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

import com.android.tools.idea.uibuilder.scene.SceneTransform;

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

  public static final int DIR_LEFT = 0;
  public static final int DIR_RIGHT = 1;
  public static final int DIR_TOP = 2;
  public static final int DIR_BOTTOM = 3;
  static GeneralPath s = new GeneralPath();
  final static int[] dirDeltaX = {-1, +1, 0, 0};
  final static int[] dirDeltaY = {0, 0, -1, +1};

  int myConnectionType;
  Rectangle mySource = new Rectangle();
  int mySourceDirection;
  Rectangle myDest = new Rectangle();
  int myDestDirection;
  boolean myToParent;
  boolean myShift;
  int myMargin;
  float myBias;
  static Stroke mySpringStroke = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{2, 2}, 0f);
  static Stroke myChainStroke1 = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{3, 3}, 0f);
  static Stroke myChainStroke2 = new BasicStroke(4, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{3, 3}, 3f);

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
  public void paint(Graphics2D g, SceneTransform sceneTransform) {
    g.setColor(Color.GREEN);
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
    int startx = getConnectionX(sourceDirection, source);
    int starty = getConnectionY(sourceDirection, source);
    int endx = getConnectionX(destDirection, dest);
    int endy = getConnectionY(destDirection, dest);

    s.reset();
    s.moveTo(startx, starty);
    int scale_source = 30;
    int scale_dest = (toParent) ? -40 : 40;
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
            }
            break;
          case DIR_TOP:
            if (endy - 1 < starty) {
              scale_dest *= -1;
            }
            break;
          case DIR_LEFT:
            if (endx - 1 < startx) {
              scale_dest *= -1;
            }
            break;
          case DIR_RIGHT:
            if (endx - 1 > startx) {
              scale_dest *= -1;
            }
            break;
        }
      }
    }
    switch (connectionType) {
      case TYPE_CHAIN:
        s.curveTo(startx + scale_source * dirDeltaX[sourceDirection], starty + scale_source * dirDeltaY[sourceDirection],
                  endx + scale_dest * dirDeltaX[destDirection], endy + scale_dest * dirDeltaY[destDirection],
                  endx, endy);
        Stroke defaultStroke = g.getStroke();
        g.setStroke(myChainStroke1);
        g.draw(s);
        g.setStroke(myChainStroke2);
        g.draw(s);
        g.setStroke(defaultStroke);
        break;
      case TYPE_SPRING:

        s.curveTo(startx + scale_source * dirDeltaX[sourceDirection], starty + scale_source * dirDeltaY[sourceDirection],
                  endx + scale_dest * dirDeltaX[destDirection], endy + scale_dest * dirDeltaY[destDirection],
                  endx, endy);
        defaultStroke = g.getStroke();
        g.setStroke(mySpringStroke);
        g.draw(s);
        g.setStroke(defaultStroke);
        break;
      case TYPE_CENTER:
        int mid_x, mid_y;
        int delta_x = 0;
        int delta_y = 0;
        if (destDirection == DIR_LEFT || destDirection == DIR_RIGHT) {
          mid_x = endx;
          mid_y = (source.y + source.height / 2 + endy) / 2;
          delta_y = (endy > starty) ? 10 : -10;
        }
        else {
          mid_y = endy;
          mid_x = (source.x + source.width / 2 + endx) / 2;
          delta_x = (endx > startx) ? 10 : -10;
        }

        s.curveTo(startx + scale_source * dirDeltaX[sourceDirection], starty + scale_source * dirDeltaY[sourceDirection],
                  mid_x - delta_x, mid_y - delta_y,
                  mid_x, mid_y);

        s.curveTo(mid_x + delta_x, mid_y + delta_y,
                  endx + scale_dest * dirDeltaX[destDirection], endy + scale_dest * dirDeltaY[destDirection],
                  endx, endy);
        g.draw(s);
        break;
      case TYPE_NORMAL:
        s.curveTo(startx + scale_source * dirDeltaX[sourceDirection], starty + scale_source * dirDeltaY[sourceDirection],
                  endx + scale_dest * dirDeltaX[destDirection], endy + scale_dest * dirDeltaY[destDirection],
                  endx, endy);
        g.draw(s);
    }
  }

  private static int getConnectionX(int side, Rectangle rect) {
    switch (side) {
      case 0:
        return rect.x;
      case 1:
        return rect.x + rect.width;
      case 2:
      case 3:
        return rect.x + rect.width / 2;
    }
    return 0;
  }

  private static int getConnectionY(int side, Rectangle rect) {
    switch (side) {
      case 0:
      case 1:
        return rect.y + rect.height / 2;
      case 2:
        return rect.y;
      case 3:
        return rect.y + rect.height;
    }
    return 0;
  }
}
