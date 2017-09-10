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
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.scene.draw.FancyStroke;
import com.android.tools.sherpa.drawing.ColorSet;

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
  public static final int TYPE_CENTER = 4; // connected on both sides to the same anchor
  public static final int TYPE_BASELINE = 5;  // connected such that anchor connects back
  public static final int TYPE_CENTER_WIDGET = 6; // connected on both sides to same widget different anchors
  public static final int TYPE_ADJACENT = 7; // Anchors are very close to each other

  private static final long MILISECONDS = 1000000; // 1 mill in nano seconds
  // modes define the the Color potentially style of
  public static final int MODE_NORMAL = DecoratorUtilities.ViewStates.NORMAL_VALUE;
  public static final int MODE_SELECTED = DecoratorUtilities.ViewStates.SELECTED_VALUE;
  public static final int MODE_COMPUTED = DecoratorUtilities.ViewStates.INFERRED_VALUE;
  public static final int MODE_WILL_DESTROY = DecoratorUtilities.ViewStates.WILL_DESTROY_VALUE;
  public static final int MODE_WILL_HOVER = DecoratorUtilities.ViewStates.HOVER_VALUE;
  public static final int MODE_SUBDUED = DecoratorUtilities.ViewStates.SUBDUED_VALUE;

  public static final int TOTAL_MODES = 3;
  private static int[] ourModeLookup = null;

  public static final int DIR_LEFT = 0;
  public static final int DIR_RIGHT = 1;
  public static final int DIR_TOP = 2;
  public static final int DIR_BOTTOM = 3;
  private static final int OVER_HANG = 20;
  private static final long TRANSITION_TIME = 1000 * MILISECONDS;
  static GeneralPath ourPath = new GeneralPath();
  final static int[] dirDeltaX = {-1, +1, 0, 0};
  final static int[] dirDeltaY = {0, 0, -1, 1};
  final static int[] ourOppositeDirection = {1, 0, 3, 2};
  public static final int GAP = 10;
  int myConnectionType;
  @SwingCoordinate Rectangle mySource = new Rectangle();
  int mySourceDirection;
  @SwingCoordinate Rectangle myDest = new Rectangle();
  int myDestDirection;
  public final static int DEST_NORMAL = 0;
  public final static int DEST_PARENT = 1;
  public final static int DEST_GUIDELINE = 2;
  int myDestType;
  boolean myShift;
  int myMargin;
  @SwingCoordinate int myMarginDistance;
  boolean myIsMarginReference;
  float myBias;
  int myModeFrom; // use to describe various display modes 0=default 1 = Source selected
  int myModeTo;
  long myStateChangeTime;
  static Stroke myBackgroundStroke = new BasicStroke(8);
  static Stroke myDashStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{4, 6}, 0f);
  static Stroke mySpringStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{4, 4}, 0f);
  static Stroke myChainStroke1 = new FancyStroke(FancyStroke.Type.HALF_CHAIN1, 2.5f, 9, 1);
  static Stroke myChainStroke2 = new FancyStroke(FancyStroke.Type.HALF_CHAIN2, 2.5f, 9, 1);

  @Override
  public int getLevel() {
    switch(myModeTo) {
      case MODE_WILL_DESTROY:
        return CONNECTION_DELETE_LEVEL;
      case MODE_SELECTED:
        return CONNECTION_SELECTED_LEVEL;
      case MODE_WILL_HOVER:
        return CONNECTION_HOVER_LEVEL;
    }
    return CONNECTION_LEVEL;
  }

  @Override
  public String serialize() {
    return "DrawConnection," + myConnectionType + "," + rectToString(mySource) + "," +
           mySourceDirection + "," + rectToString(myDest) + "," + myDestDirection + "," +
           myDestType + "," + myShift + "," + myMargin + "," + myMarginDistance + "," +
           myIsMarginReference + "," + myBias + "," + myModeFrom + "," + myModeTo + "," + 0;
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
    myDestType = Integer.parseInt(sp[c++]);
    myShift = Boolean.parseBoolean(sp[c++]);
    myMargin = Integer.parseInt(sp[c++]);
    myMarginDistance = Integer.parseInt(sp[c++]);
    myIsMarginReference = Boolean.parseBoolean(sp[c++]);
    myBias = Float.parseFloat(sp[c++]);
    myModeFrom = Integer.parseInt(sp[c++]);
    myModeTo = Integer.parseInt(sp[c++]);
    myStateChangeTime = Long.parseLong(sp[c++]);
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet color = sceneContext.getColorSet();
    g.setColor(color.getConstraints());
    boolean animate = draw(g, color, myConnectionType, mySource, mySourceDirection, myDest, myDestDirection, myDestType, myMargin, myMarginDistance,
         myIsMarginReference, myModeFrom, myModeTo, myStateChangeTime);
    if (animate) {
      sceneContext.repaint();
    }
  }

  public DrawConnection(int connectionType,
                        @SwingCoordinate Rectangle source,
                        int sourceDirection,
                        @SwingCoordinate Rectangle dest,
                        int destDirection,
                        int destType,
                        boolean shift,
                        int margin,
                        @SwingCoordinate int marginDistance,
                        boolean isMarginReference,
                        Float bias,
                        int modeFrom, int modeTo, long nanoTime) {
    config(connectionType, source, sourceDirection, dest, destDirection, destType, shift, margin, marginDistance, isMarginReference, bias,
           modeFrom, modeTo, nanoTime);
  }

  public static void buildDisplayList(DisplayList list,
                                      int connectionType,
                                      @SwingCoordinate Rectangle source,
                                      int sourceDirection,
                                      @SwingCoordinate Rectangle dest,
                                      int destDirection,
                                      int destType,
                                      boolean shift,
                                      int margin,
                                      @SwingCoordinate int marginDistance,
                                      boolean isMarginReference,
                                      Float bias,
                                      int modeFrom, int modeTo, long nanoTime) {
    list
      .add(new DrawConnection(connectionType, source, sourceDirection, dest, destDirection, destType, shift, margin, marginDistance,
                              isMarginReference, bias, modeFrom, modeTo, nanoTime));
  }

  public void config(int connectionType,
                     @SwingCoordinate Rectangle source,
                     int sourceDirection,
                     @SwingCoordinate Rectangle dest,
                     int destDirection,
                     int destType,
                     boolean shift,
                     int margin,
                     @SwingCoordinate int marginDistance,
                     boolean isMarginReference,
                     Float bias,
                     int modeFrom,
                     int modeTo,
                     long stateChangeTime) {
    mySource.setBounds(source);
    myDest.setBounds(dest);
    myConnectionType = connectionType;
    mySource.setBounds(source);
    mySourceDirection = sourceDirection;
    myDest.setBounds(dest);
    myDestDirection = destDirection;
    myDestType = destType;
    myShift = shift;
    myMargin = margin;
    myMarginDistance = marginDistance;
    myIsMarginReference = isMarginReference;
    myBias = bias;
    myModeFrom = modeFrom;
    myModeTo = modeTo;
    myStateChangeTime = stateChangeTime;
  }

  public static Color modeGetConstraintsColor(int mode, ColorSet color) {
    switch (mode) {
      case MODE_NORMAL:
        return color.getConstraints();
      case MODE_SELECTED:
        return color.getSelectedConstraints();
      case MODE_COMPUTED:
        return  color.getCreatedConstraints();
      case MODE_WILL_DESTROY:
        return color.getAnchorDisconnectionCircle();
      case  MODE_SUBDUED:
        return color.getSubduedConstraints();
    }
    return color.getConstraints();
  }

  public static Color modeGetMarginColor(int mode, ColorSet color) {
    switch (mode) {
      case MODE_NORMAL:
        return color.getMargins();
      case MODE_SELECTED:
        return color.getConstraints();
      case MODE_COMPUTED:
        return color.getHighlightedConstraints();
      case  MODE_SUBDUED:
        return color.getSubduedConstraints();
    }
    return color.getMargins();
  }

  static Color interpolate(Color fromColor, Color toColor, float percent) {
    int col1 = fromColor.getRGB();
    int col2 = toColor.getRGB();
    int c1 = (int) (((col1>>0)&0xFF) * (1 - percent) + ((col2>>0)&0xFF) * percent);
    int c2 = (int) (((col1>>8)&0xFF) * (1 - percent) + ((col2>>8)&0xFF) * percent);
    int c3 = (int) (((col1>>16)&0xFF) * (1 - percent) + ((col2>>16)&0xFF) * percent);
    return new Color(c3,c2,c1);
  }

  public static boolean draw(Graphics2D g,
                             ColorSet color, int connectionType,
                             @SwingCoordinate Rectangle source,
                             int sourceDirection,
                             @SwingCoordinate Rectangle dest,
                             int destDirection,
                             int myDestType,
                             int margin,
                             @SwingCoordinate int marginDistance,
                             boolean isMarginReference,
                             int modeFrom,
                             int modeTo,
                             long stateChange) {
    boolean animate = false;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Color constraintColor = modeGetConstraintsColor(modeTo, color);
    Color marginColor = modeGetMarginColor(modeTo, color);
    long timeSince = System.nanoTime() - stateChange;
    if (timeSince < TRANSITION_TIME) {
      float t = (float)((timeSince) / (double)TRANSITION_TIME);
      Color fromColor = modeGetConstraintsColor(modeFrom, color);
      Color toColor = modeGetConstraintsColor(modeTo, color);

      constraintColor = interpolate(fromColor, toColor, t);
      animate = true;
    }

    if (connectionType == TYPE_BASELINE) {
      drawBaseLine(g, source, dest, constraintColor);
      return animate;
    }
    int startx = getConnectionX(sourceDirection, source);
    int starty = getConnectionY(sourceDirection, source);
    int endx = getConnectionX(destDirection, dest);
    int endy = getConnectionY(destDirection, dest);
    int dx = getDestinationDX(destDirection);
    int dy = getDestinationDY(destDirection);

    int manhattanDistance = Math.abs(startx - endx) + Math.abs(starty - endy);
    int scale_source = Math.min(90, manhattanDistance);
    int scale_dest = (myDestType == DEST_PARENT) ? -scale_source : scale_source;
    boolean flip_arrow = false;
    if (myDestType != DEST_NORMAL) {
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
            if (endx < startx) {
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
    int dir = ((myDestType == DEST_PARENT) ^ flip_arrow) ? ourOppositeDirection[destDirection] : destDirection;
    ourPath.reset();
    ourPath.moveTo(startx, starty);
    Stroke defaultStroke;
    if (manhattanDistance == 0) {
      g.setColor(constraintColor);
      DrawConnectionUtils.getArrow(dir, endx, endy, xPoints, yPoints);
      g.fillPolygon(xPoints, yPoints, 3);
      g.draw(ourPath);
    }

    switch (connectionType) {
      case TYPE_CHAIN:
        boolean flip_chain =  (endx +endy > startx+starty);
    if(flip_chain) {
      ourPath.moveTo(startx, starty);
      ourPath.curveTo(startx + scale_source * dirDeltaX[sourceDirection],
                      starty + scale_source * dirDeltaY[sourceDirection],
                      endx + scale_dest * dirDeltaX[destDirection],
                      endy + scale_dest * dirDeltaY[destDirection],
                      endx, endy);
    } else {
      ourPath.moveTo(endx, endy);
      ourPath.curveTo(endx + scale_source * dirDeltaX[destDirection],
                      endy + scale_source * dirDeltaY[destDirection],
                      startx + scale_dest * dirDeltaX[sourceDirection],
                      starty + scale_dest * dirDeltaY[sourceDirection],
                      startx, starty);
    }
        defaultStroke = g.getStroke();
        g.setColor(constraintColor);
        g.setStroke(flip_chain?myChainStroke1:myChainStroke2);
        g.draw(ourPath);
        g.setStroke(defaultStroke);
        if (modeTo == MODE_WILL_DESTROY) {
          DrawConnectionUtils.getArrow(dir, endx, endy, xPoints, yPoints);
          g.fillPolygon(xPoints, yPoints, 3);
        }
        break;
      case TYPE_ADJACENT:
        g.setColor(constraintColor);

        DrawConnectionUtils.getSmallArrow(dir, startx-dx/2, starty-dy/2, xPoints, yPoints);
        g.fillPolygon(xPoints, yPoints, 3);
        if (destDirection == DIR_LEFT || destDirection == DIR_RIGHT) {
          startx = (startx+endx)/2;
          endx = startx;
        } else {
          starty = (starty+endy)/2;
          endy = starty;
        }
        g.drawLine(startx, starty, endx, endy);
        break;
      case TYPE_SPRING:
        boolean drawArrow = true;
        int springEndX = endx;
        int springEndY = endy;
        if (myDestType != DEST_NORMAL) {
          if (margin != 0) {
            String marginString = Integer.toString(margin);
            if (destDirection == DIR_LEFT || destDirection == DIR_RIGHT) {
              int gap = Math.max(marginDistance, DrawConnectionUtils.getHorizontalMarginGap(g, marginString));
              if (Math.abs(startx - endx) > gap) {
                int marginX = endx - ((endx > startx) ? gap : -gap);
                int arrow = ((endx > startx) ? 1 : -1) * DrawConnectionUtils.ARROW_SIDE;
                g.setColor(marginColor);
                DrawConnectionUtils
                  .drawHorizontalMargin(g, marginString, isMarginReference, marginX, endx - arrow, endy);

                springEndX = marginX;
              }
            }
            else {
              int gap = Math.max(marginDistance, DrawConnectionUtils.getVerticalMarginGap(g));
              if (Math.abs(starty - endy) > gap) {
                int marginY = endy - ((endy > starty) ? gap : -gap);
                int arrow = ((endy > starty) ? 1 : -1) * DrawConnectionUtils.ARROW_SIDE;
                g.setColor(marginColor);

                DrawConnectionUtils
                  .drawVerticalMargin(g, marginString, isMarginReference, endx, marginY, endy - arrow);

                springEndY = marginY;
              }
            }
          }

          if (endx == startx) {
            g.setColor(constraintColor);
            DrawConnectionUtils.drawVerticalZigZagLine(ourPath, startx, starty, springEndY);
            g.fillRect(startx - 4, springEndY, 9, 1);
          }
          else {
            g.setColor(constraintColor);
            DrawConnectionUtils.drawHorizontalZigZagLine(ourPath, startx, springEndX, endy);
            g.fillRect(springEndX, endy - 4, 1, 9);
          }
        }
        else {
          g.setColor(constraintColor);
          if (destDirection == DIR_LEFT || destDirection == DIR_RIGHT) {
            g.setColor(constraintColor);
            DrawConnectionUtils.drawHorizontalZigZagLine(ourPath, startx, endx, starty);
            defaultStroke = g.getStroke();
            g.setStroke(mySpringStroke);
            drawArrow = false;
            g.drawLine(endx, starty, endx, endy);
            g.setStroke(defaultStroke);
            g.fillRoundRect(endx-2,endy-2,5,5,2,2);

          }
          else {
            g.setColor(constraintColor);
            DrawConnectionUtils.drawVerticalZigZagLine(ourPath, startx, starty, endy);
            defaultStroke = g.getStroke();
            g.setStroke(mySpringStroke);
            drawArrow = false;
            g.drawLine(startx, endy, endx, endy);
            g.setStroke(defaultStroke);
            g.fillRoundRect(endx-2,endy-2,5,5,2,2);
          }
        }
        g.setColor(constraintColor);
        g.draw(ourPath);
        if (drawArrow) {
          g.setColor(constraintColor);
          DrawConnectionUtils.getArrow(dir, endx, endy, xPoints, yPoints);
          g.fillPolygon(xPoints, yPoints, 3);
        }
        break;
      case TYPE_CENTER:
      case TYPE_CENTER_WIDGET:
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
          int vline_y1 = -1, vline_y2 = -1;
          if (source.y > dest.y + dest.height) {
            vline_y1 = dest.y + dest.height;
            vline_y2 = source.y;
          }
          if (source.y + source.height < dest.y) {
            vline_y1 = source.y + source.height;
            vline_y2 = dest.y;
          }
          if (vline_y1 != -1) {
            Stroke stroke = g.getStroke();
            g.setStroke(myDashStroke);
            int xpos = source.x + source.width / 2;
            g.setColor(constraintColor);
            g.drawLine(xpos, vline_y1, xpos, vline_y2);
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

          int vline_x1 = -1, vline_x2 = -1;
          if (source.x > dest.x + dest.width) {
            vline_x1 = dest.x + dest.width;
            vline_x2 = source.x;
          }
          if (source.x + source.width < dest.x) {
            vline_x1 = source.x + source.width;
            vline_x2 = dest.x;
          }
          if (vline_x1 != -1) {
            Stroke stroke = g.getStroke();
            g.setStroke(myDashStroke);
            int ypos = source.y + source.height / 2;
            g.setColor(constraintColor);
            g.drawLine(vline_x1, ypos, vline_x2, ypos);
            g.setStroke(stroke);
          }
        }
        int len = 6;
        int[] px = new int[len];
        int[] py = new int[len];
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

        g.setColor(constraintColor);
        if (TYPE_CENTER_WIDGET == connectionType) {
          len = DrawConnectionUtils.removeZigZag(px, py, len, 50);
        }
        DrawConnectionUtils.drawRound(ourPath, px, py, len, GAP);
        DrawConnectionUtils.getArrow(destDirection, endx, endy, xPoints, yPoints);
        g.fillPolygon(xPoints, yPoints, 3);
        g.draw(ourPath);
        break;
      case TYPE_NORMAL:
        if (margin > 0) {
          if (sourceDirection == DIR_RIGHT || sourceDirection == DIR_LEFT) {
            boolean above = starty < endy;
            int line_y = starty + (above ? -1 : 1) * source.height / 4;
            g.setColor(marginColor);
            DrawConnectionUtils.drawHorizontalMarginIndicator(g, String.valueOf(margin), isMarginReference, startx, endx, line_y);
            if (myDestType != DEST_PARENT || (line_y < dest.y || line_y > dest.y + dest.height)) {
              int constraintX = (destDirection == DIR_LEFT) ? dest.x : dest.x + dest.width;
              Stroke stroke = g.getStroke();
              g.setStroke(myDashStroke);
              int overlap = (above) ? -OVER_HANG : OVER_HANG;
              g.setColor(constraintColor);
              g.drawLine(constraintX, line_y + overlap, constraintX, above ? dest.y : dest.y + dest.height);
              g.setStroke(stroke);
            }
          }
          else {
            boolean left = startx < endx;
            int line_x = startx + (left ? -1 : 1) * source.width / 4;
            g.setColor(marginColor);
            DrawConnectionUtils.drawVerticalMarginIndicator(g, String.valueOf(margin), isMarginReference, line_x, starty, endy);

            if (myDestType != DEST_PARENT || (line_x < dest.x || line_x > dest.x + dest.width)) {
              int constraint_y = (destDirection == DIR_TOP) ? dest.y : dest.y + dest.height;
              Stroke stroke = g.getStroke();
              g.setStroke(myDashStroke);
              g.setColor(constraintColor);
              int overlap = (left) ? -OVER_HANG : OVER_HANG;
              g.drawLine(line_x + overlap, constraint_y,
                         left ? dest.x : dest.x + dest.width, constraint_y);
              g.setStroke(stroke);
            }
          }
        }
        if ((startx - endx == 0 ||  starty - endy == 0) &&  sourceDirection != destDirection ) {
          scale_source = 0;
          scale_dest = 0;
        }
        g.setColor(constraintColor);
        if (sourceDirection == destDirection && margin == 0) {
          scale_source /= 3;
          scale_dest /= 2;
          ourPath.curveTo(startx + scale_source * dirDeltaX[sourceDirection], starty + scale_source * dirDeltaY[sourceDirection],
                          endx + dx + scale_dest * dirDeltaX[destDirection], endy + dy + scale_dest * dirDeltaY[destDirection],
                          endx + dx, endy + dy);
        }
        else {
          int xgap = startx - endx;
          int ygap = starty - endy;
          if ((startx - endx) == 0 && dirDeltaX[sourceDirection] == 0) {
            scale_source = 0;
            scale_dest = 0;
          }
          else if ((starty - endy) == 0 && dirDeltaY[sourceDirection] == 0) {
            scale_dest = 0;
            scale_source = 0;
          }
          ourPath.curveTo(startx + scale_source * dirDeltaX[sourceDirection], starty + scale_source * dirDeltaY[sourceDirection],
                          endx + dx + scale_dest * dirDeltaX[destDirection], endy + dy + scale_dest * dirDeltaY[destDirection],
                          endx + dx, endy + dy);
        }
        defaultStroke = g.getStroke();
        g.setStroke(myBackgroundStroke);
        g.setColor(color.getBackground());
        DrawConnectionUtils.getArrow(dir, endx, endy, xPoints, yPoints);
        g.fillPolygon(xPoints, yPoints, 3);
        g.draw(ourPath);
        g.setStroke(defaultStroke);
        g.setColor(constraintColor);
        DrawConnectionUtils.getArrow(dir, endx, endy, xPoints, yPoints);
        g.fillPolygon(xPoints, yPoints, 3);
        g.draw(ourPath);
    }
    return animate;
  }

  private static void drawBaseLine(Graphics2D g,
                                   @SwingCoordinate Rectangle source,
                                   @SwingCoordinate Rectangle dest, Color color) {
    g.setColor(color);
    ourPath.reset();
    ourPath.moveTo(source.x + source.width / 2., source.y);
    ourPath.curveTo(source.x + source.width / 2., source.y - 40,
                    dest.x + dest.width / 2., dest.y + 40,
                    dest.x + dest.width / 2., dest.y);
    int[] xPoints = new int[3];
    int[] yPoints = new int[3];
    DrawConnectionUtils.getArrow(DIR_BOTTOM, dest.x + dest.width / 2, dest.y, xPoints, yPoints);
    int inset = source.width / 5;
    g.fillRect(source.x + inset, source.y, source.width - inset * 2, 1);
    inset = dest.width / 5;
    g.fillRect(dest.x + inset, dest.y, dest.width - inset * 2, 1);
    g.fillPolygon(xPoints, yPoints, 3);
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

  private static int getDestinationDX(int side) {
    switch (side) {
      case DIR_LEFT:
        return -DrawConnectionUtils.ARROW_SIDE;
      case DIR_RIGHT:
        return +DrawConnectionUtils.ARROW_SIDE;
    }
    return 0;
  }

  private static int getDestinationDY(int side) {
    switch (side) {
      case DIR_TOP:
        return -DrawConnectionUtils.ARROW_SIDE;
      case DIR_BOTTOM:
        return +DrawConnectionUtils.ARROW_SIDE;
    }
    return 0;
  }
}
