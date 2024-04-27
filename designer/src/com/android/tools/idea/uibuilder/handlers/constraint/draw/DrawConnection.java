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

import static com.intellij.util.ui.JBUI.scale;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.scene.draw.FancyStroke;
import com.android.tools.idea.uibuilder.handlers.constraint.SecondarySelector;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
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
  public static final int MODE_COMPUTED = DecoratorUtilities.ViewStates.INFERRED_VALUE;
  public static final int MODE_SUBDUED = DecoratorUtilities.ViewStates.SUBDUED_VALUE;
  public static final int MODE_NORMAL = DecoratorUtilities.ViewStates.NORMAL_VALUE;
  public static final int MODE_WILL_HOVER = DecoratorUtilities.ViewStates.HOVER_VALUE;
  public static final int MODE_VIEW_SELECTED = DecoratorUtilities.ViewStates.SELECTED_VALUE;
  public static final int MODE_CONSTRAINT_SELECTED = DecoratorUtilities.ViewStates.SECONDARY_VALUE;
  public static final int MODE_DELETING = DecoratorUtilities.ViewStates.WILL_DESTROY_VALUE;
  public static final int HOVER_FLAG = 0x100;
  public static final int HOVER_MASK = ~0x100;

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
  public static final int GAP = scale(10);
  int myConnectionType;
  SecondarySelector mySecondarySelector;
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
  static Stroke myBackgroundStroke = new BasicStroke(scale(8));
  static Stroke myDashStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{scale(4), scale(6)}, 0f);
  static Stroke mySpringStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{scale(4), scale(4)}, 0f);
  static Stroke myChainStroke1 = new FancyStroke(FancyStroke.Type.HALF_CHAIN1, scale(2.5f), scale(9), 1);
  static Stroke myChainStroke2 = new FancyStroke(FancyStroke.Type.HALF_CHAIN2, scale(2.5f), scale(9), 1);
  static Stroke myNormalStroke = new BasicStroke(scale(1));
  static Stroke myHoverStroke = new BasicStroke(scale(12), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);

  static Stroke myThickDashStroke =
    new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{scale(4), scale(6)}, 0f);
  static Stroke myThickSpringStroke =
    new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{scale(4), scale(4)}, 0f);
  static Stroke myThickChainStroke1 = new FancyStroke(FancyStroke.Type.HALF_CHAIN1, scale(2.5f), scale(9), 2);
  static Stroke myThickChainStroke2 = new FancyStroke(FancyStroke.Type.HALF_CHAIN2, scale(2.5f), scale(9), 2);
  static Stroke myThickNormalStroke = new BasicStroke(scale(2));

  @Override
  public int getLevel() {
    switch (myModeTo) {
      case MODE_DELETING:
        return CONNECTION_DELETE_LEVEL;
      case MODE_VIEW_SELECTED:
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
    ScenePicker picker = sceneContext.getScenePicker();
    SecondarySelector secondarySelector = mySecondarySelector;
    g.setColor(color.getConstraints());

    boolean animate =
      draw(g, color, picker, secondarySelector, myConnectionType, mySource, mySourceDirection, myDest, myDestDirection, myDestType,
           myMargin, myMarginDistance,
           myIsMarginReference, myModeFrom, myModeTo, myStateChangeTime);
    if (animate) {
      sceneContext.repaint();
    }
  }

  public DrawConnection(SecondarySelector selector, int connectionType,
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

    config(selector, connectionType, source, sourceDirection, dest, destDirection, destType, shift, margin, marginDistance,
           isMarginReference, bias,
           modeFrom, modeTo, nanoTime);
  }

  public static void buildDisplayList(DisplayList list,
                                      SecondarySelector selector, int connectionType,
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

    list.add(
      new DrawConnection(selector, connectionType, source, sourceDirection, dest, destDirection, destType, shift, margin, marginDistance,
                         isMarginReference, bias, modeFrom, modeTo, nanoTime));
  }

  public void config(SecondarySelector selector, int connectionType,
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
    mySecondarySelector = selector;
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
    mode &= HOVER_MASK;
    switch (mode) {
      case MODE_NORMAL:
        return color.getConstraints();
      case MODE_VIEW_SELECTED:
        return color.getSelectedConstraints();
      case MODE_COMPUTED:
        return color.getCreatedConstraints();
      case MODE_DELETING:
        return color.getAnchorDisconnectionCircle();
      case MODE_SUBDUED:
        return color.getSubduedConstraints();
      case MODE_CONSTRAINT_SELECTED:
        return color.getSelectedConstraints();
      case MODE_WILL_HOVER:
        return color.getLassoSelectionFill();
    }
    return color.getConstraints();
  }

  enum StrokeType {
    DASH,
    CHAIN,
    SPRING,
    NORMAL,
    BACKGROUND
  }

  static Stroke getStroke(StrokeType strokeType, boolean flip_chain, int mode) {
    boolean thick = (mode == MODE_DELETING || mode == MODE_CONSTRAINT_SELECTED || mode == MODE_COMPUTED);
    switch (strokeType) {
      case CHAIN:
        if (thick) {
          return flip_chain ? myThickChainStroke1 : myThickChainStroke2;
        }
        return flip_chain ? myChainStroke1 : myChainStroke2;
      case SPRING:
        return thick ? myThickSpringStroke : mySpringStroke;
      case DASH:
        return thick ? myThickDashStroke : myDashStroke;
      case NORMAL:
        return thick ? myThickNormalStroke : myNormalStroke;
    }
    return thick ? myThickNormalStroke : myNormalStroke;
  }

  public static Color modeGetMarginColor(int mode, ColorSet color) {
    switch (mode) {
      case MODE_NORMAL:
        return color.getMargins();
      case MODE_VIEW_SELECTED:
        return color.getConstraints();
      case MODE_COMPUTED:
        return color.getHighlightedConstraints();
      case MODE_SUBDUED:
        return color.getSubduedConstraints();
    }
    return color.getMargins();
  }

  static Color interpolate(Color fromColor, Color toColor, float percent) {
    int col1 = fromColor.getRGB();
    int col2 = toColor.getRGB();
    int c1 = (int)(((col1 >> 0) & 0xFF) * (1 - percent) + ((col2 >> 0) & 0xFF) * percent);
    int c2 = (int)(((col1 >> 8) & 0xFF) * (1 - percent) + ((col2 >> 8) & 0xFF) * percent);
    int c3 = (int)(((col1 >> 16) & 0xFF) * (1 - percent) + ((col2 >> 16) & 0xFF) * percent);
    return new Color(c3, c2, c1);
  }

  public static boolean draw(Graphics2D g,
                             ColorSet color,
                             ScenePicker picker,
                             SecondarySelector secondarySelector,
                             int connectionType,
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

    Shape originalClip = null;

    boolean animate = false;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Color constraintColor = modeGetConstraintsColor(modeTo, color);
    Color marginColor = modeGetMarginColor(modeTo, color);
    long timeSince = System.nanoTime() - stateChange;
    boolean hover = (HOVER_FLAG & modeTo) > 0 || modeTo == MODE_CONSTRAINT_SELECTED;
    modeTo &= HOVER_MASK;
    if (timeSince < TRANSITION_TIME) {
      float t = (float)((timeSince) / (double)TRANSITION_TIME);
      Color fromColor = modeGetConstraintsColor(modeFrom, color);
      Color toColor = modeGetConstraintsColor(modeTo, color);

      constraintColor = interpolate(fromColor, toColor, t);
      animate = true;
    }

    if (connectionType == TYPE_BASELINE) {
      Color hoverColor = modeGetConstraintsColor(MODE_WILL_HOVER, color);
      drawBaseLine(g, source, dest, constraintColor, hoverColor, picker, secondarySelector, hover);
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
    int scale_dest = scale_source;
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
    if (sourceDirection == destDirection) {
      switch (myDestType) {
        case DEST_PARENT:
          scale_dest *= -1;
          flip_arrow = true;
          switch (destDirection) {
            case DIR_BOTTOM:
            case DIR_TOP:
              dy *= -1;
              break;
            case DIR_LEFT:
            case DIR_RIGHT:
              dx *= -1;
              break;
          }
          break;
        case DEST_NORMAL:
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
          break;
      }
    }

    int[] xPoints = new int[3];
    int[] yPoints = new int[3];
    int dir = flip_arrow ? ourOppositeDirection[destDirection] : destDirection;
    ourPath.reset();
    ourPath.moveTo(startx, starty);
    Stroke defaultStroke;
    if (manhattanDistance == 0) {
      g.setColor(constraintColor);
      DrawConnectionUtils.getArrow(dir, endx, endy, xPoints, yPoints);
      g.fillPolygon(xPoints, yPoints, 3);
      g.draw(ourPath);
      ourPath.reset();
      ourPath.moveTo(startx, starty);
    }
    defaultStroke = g.getStroke();
    g.setStroke(getStroke(StrokeType.NORMAL, false, modeTo));
    switch (connectionType) {
      case TYPE_CHAIN:
        boolean flip_chain = (endx + endy > startx + starty);
        if (flip_chain) {
          float x1, y1, x2, y2, x3, y3, x4, y4;
          if (hover) {
            GeneralPath hoverPath = new GeneralPath(ourPath);
            g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
            Stroke tmpStroke = g.getStroke();
            g.setStroke(myHoverStroke);
            hoverPath.moveTo(x1 = startx, y1 = starty);
            hoverPath.curveTo(x2 = startx + scale_source * dirDeltaX[sourceDirection],
                              y2 = starty + scale_source * dirDeltaY[sourceDirection],
                              x3 = endx + scale_dest * dirDeltaX[destDirection],
                              y3 = endy + scale_dest * dirDeltaY[destDirection],
                              x4 = endx, y4 = endy);
            g.draw(hoverPath);
            hoverPath.reset();
            g.setStroke(tmpStroke);
          }
          ourPath.moveTo(x1 = startx, y1 = starty);
          ourPath.curveTo(x2 = startx + scale_source * dirDeltaX[sourceDirection],
                          y2 = starty + scale_source * dirDeltaY[sourceDirection],
                          x3 = endx + scale_dest * dirDeltaX[destDirection],
                          y3 = endy + scale_dest * dirDeltaY[destDirection],
                          x4 = endx, y4 = endy);
          if (picker != null && secondarySelector != null) {
            picker.addCurveTo(secondarySelector, 4, (int)x1, (int)y1, (int)x2, (int)y2, (int)x3, (int)y3, (int)x4, (int)y4, 4);
          }
        }
        else {
          float x1, y1, x2, y2, x3, y3, x4, y4;
          if (hover) {
            GeneralPath hoverPath = new GeneralPath(ourPath);
            g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
            Stroke tmpStroke = g.getStroke();
            g.setStroke(myHoverStroke);
            hoverPath.moveTo(x1 = endx, y1 = endy);
            hoverPath.curveTo(x2 = endx + scale_source * dirDeltaX[destDirection],
                              y2 = endy + scale_source * dirDeltaY[destDirection],
                              x3 = startx + scale_dest * dirDeltaX[sourceDirection],
                              y3 = starty + scale_dest * dirDeltaY[sourceDirection],
                              x4 = startx, y4 = starty);
            g.draw(hoverPath);
            hoverPath.reset();
            g.setStroke(tmpStroke);
          }
          ourPath.moveTo(x1 = endx, y1 = endy);
          ourPath.curveTo(x2 = endx + scale_source * dirDeltaX[destDirection],
                          y2 = endy + scale_source * dirDeltaY[destDirection],
                          x3 = startx + scale_dest * dirDeltaX[sourceDirection],
                          y3 = starty + scale_dest * dirDeltaY[sourceDirection],
                          x4 = startx, y4 = starty);
          if (picker != null && secondarySelector != null) {
            picker.addCurveTo(secondarySelector, 4, (int)x1, (int)y1, (int)x2, (int)y2, (int)x3, (int)y3, (int)x4, (int)y4, 4);
          }
        }
        g.setColor(constraintColor);
        g.setStroke(getStroke(StrokeType.CHAIN, flip_chain, modeTo));
        g.draw(ourPath);
        if (modeTo == MODE_DELETING) {
          DrawConnectionUtils.getArrow(dir, endx, endy, xPoints, yPoints);
          g.fillPolygon(xPoints, yPoints, 3);
        }
        break;
      case TYPE_ADJACENT:
        g.setColor(constraintColor);

        DrawConnectionUtils.getSmallArrow(dir, startx - dx / 2, starty - dy / 2, xPoints, yPoints);
        g.fillPolygon(xPoints, yPoints, 3);
        if (destDirection == DIR_LEFT || destDirection == DIR_RIGHT) {
          startx = (startx + endx) / 2;
          endx = startx;
        }
        else {
          starty = (starty + endy) / 2;
          endy = starty;
        }
        g.drawLine(startx, starty, endx, endy);
        if (picker != null && secondarySelector != null) {
          picker.addLine(secondarySelector, 4, startx, starty, endx, endy, 4);
        }
        break;
      case TYPE_SPRING:
        boolean drawArrow = true;
        int springEndX = endx;
        int springEndY = endy;
        if (myDestType != DEST_NORMAL) {
          int rectGap = scale(4);
          int rectDim = scale(9);
          if (margin != 0) {
            String marginString = Integer.toString(margin);
            if (destDirection == DIR_LEFT || destDirection == DIR_RIGHT) {
              int gap = Math.max(marginDistance, DrawConnectionUtils.getHorizontalMarginGap(g, marginString));
              if (Math.abs(startx - endx) < gap) {
                // Doesn't have enough space to paint margin string.
                marginString = null;
              }

              int marginX = endx - ((endx > startx) ? gap : -gap);
              int arrow = ((endx > startx) ? 1 : -1) * DrawConnectionUtils.ARROW_SIDE;
              if (hover) {
                g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
                Stroke tmpStroke = g.getStroke();
                g.setStroke(myHoverStroke);
                g.drawLine(marginX, endy, endx - arrow, endy);
                g.setStroke(tmpStroke);
              }
              if (picker != null && secondarySelector != null) {
                picker.addLine(secondarySelector, 8, marginX, endy, endx - arrow, endy, 4);
              }
              g.setColor(constraintColor);
              g.drawLine(marginX, endy, endx - arrow, endy);
              DrawConnectionUtils.drawHorizontalMarginString(g, marginColor, marginString, isMarginReference, marginX, endx - arrow, endy);

              springEndX = marginX;
            }
            else {
              int gap = Math.max(marginDistance, DrawConnectionUtils.getVerticalMarginGap(g));
              if (Math.abs(starty - endy) < gap) {
                // Doesn't have enough space to paint margin string.
                marginString = null;
              }

              int marginY = endy - ((endy > starty) ? gap : -gap);
              int arrow = ((endy > starty) ? 1 : -1) * DrawConnectionUtils.ARROW_SIDE;
              if (hover) {
                g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
                Stroke tmpStroke = g.getStroke();
                g.setStroke(myHoverStroke);
                g.drawLine(endx, marginY, endx, endy - arrow);
                g.setStroke(tmpStroke);
              }
              if (picker != null && secondarySelector != null) {
                picker.addLine(secondarySelector, 8, endx, marginY, endx, endy - arrow, 4);
              }
              g.setColor(constraintColor);
              g.drawLine(endx, marginY, endx, endy - arrow);
              DrawConnectionUtils.drawVerticalMarginString(g, marginColor, marginString, isMarginReference, endx, marginY, endy - arrow);
              springEndY = marginY;
            }
          }

          if (endx == startx) {
            if (hover) {
              g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
              Stroke tmpStroke = g.getStroke();
              g.setStroke(myHoverStroke);
              g.drawLine(startx, starty, startx, springEndY);
              g.setStroke(tmpStroke);
            }

            g.setColor(constraintColor);
            DrawConnectionUtils.drawVerticalZigZagLine(ourPath, startx, starty, springEndY);
            if (picker != null && secondarySelector != null) {
              picker.addLine(secondarySelector, 8, startx, starty, startx, springEndY, 4);
            }
            g.fillRect(startx - rectGap, springEndY, rectDim, 1);
          }
          else {
            if (hover) {
              g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
              Stroke tmpStroke = g.getStroke();
              g.setStroke(myHoverStroke);
              g.drawLine(startx, endy, springEndX, endy);
              g.setStroke(tmpStroke);
            }
            g.setColor(constraintColor);
            DrawConnectionUtils.drawHorizontalZigZagLine(ourPath, startx, springEndX, endy);
            if (picker != null && secondarySelector != null) {
              picker.addLine(secondarySelector, 8, startx, endy, springEndX, endy, 4);
            }

            g.fillRect(springEndX, endy - rectGap, 1, rectDim);
          }
        }
        else {
          g.setColor(constraintColor);
          int rectGap = scale(2);
          int rectDim = scale(5);
          if (destDirection == DIR_LEFT || destDirection == DIR_RIGHT) {
            if (hover) {
              g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
              Stroke tmpStroke = g.getStroke();
              g.setStroke(myHoverStroke);
              GeneralPath hoverPath = new GeneralPath(ourPath);
              hoverPath.moveTo(startx, starty);
              hoverPath.lineTo(endx, starty);
              hoverPath.lineTo(endx, endy);
              g.draw(hoverPath);
              g.setStroke(tmpStroke);
            }
            g.setColor(constraintColor);
            DrawConnectionUtils.drawHorizontalZigZagLine(ourPath, startx, endx, starty);
            if (picker != null && secondarySelector != null) {
              picker.addLine(secondarySelector, 8, startx, starty, endx, starty, 4);
              picker.addLine(secondarySelector, 8, endx, starty, endx, endy, 4);
            }

            g.setStroke(getStroke(StrokeType.SPRING, false, modeTo));
            drawArrow = false;
            g.drawLine(endx, starty, endx, endy);
            g.fillRoundRect(endx - rectGap, endy - rectGap, rectDim, rectDim, rectGap, rectGap);
          }
          else {

            if (hover) {
              g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
              Stroke tmpStroke = g.getStroke();
              g.setStroke(myHoverStroke);
              GeneralPath hoverPath = new GeneralPath(ourPath);
              hoverPath.moveTo(startx, starty);
              hoverPath.lineTo(startx, endy);
              hoverPath.lineTo(endx, endy);
              g.draw(hoverPath);
              g.setStroke(tmpStroke);
            }

            g.setColor(constraintColor);
            DrawConnectionUtils.drawVerticalZigZagLine(ourPath, startx, starty, endy);
            if (picker != null && secondarySelector != null) {
              picker.addLine(secondarySelector, 8, startx, starty, startx, endy, 4);
              picker.addLine(secondarySelector, 8, startx, endy, endx, endy, 4);
            }
            g.setStroke(getStroke(StrokeType.SPRING, false, modeTo));
            drawArrow = false;
            g.drawLine(startx, endy, endx, endy);
            g.fillRoundRect(endx - rectGap, endy - rectGap, rectDim, rectDim, rectGap, rectGap);
          }
        }
        g.setStroke(getStroke(StrokeType.NORMAL, false, modeTo));
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
            if (hover) {
              g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
              Stroke tmpStroke = g.getStroke();
              g.setStroke(myHoverStroke);
              int xpos = source.x + source.width / 2;
              g.drawLine(xpos, vline_y1, xpos, vline_y2);
              g.setStroke(tmpStroke);
            }
            g.setStroke(getStroke(StrokeType.DASH, false, modeTo));
            int xpos = source.x + source.width / 2;
            g.setColor(constraintColor);
            g.drawLine(xpos, vline_y1, xpos, vline_y2);
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
            if (hover) {
              g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
              Stroke tmpStroke = g.getStroke();
              g.setStroke(myHoverStroke);
              int ypos = source.y + source.height / 2;
              g.drawLine(vline_x1, ypos, vline_x2, ypos);
              g.setStroke(tmpStroke);
            }
            g.setStroke(getStroke(StrokeType.DASH, false, modeTo));
            int ypos = source.y + source.height / 2;
            g.setColor(constraintColor);
            g.drawLine(vline_x1, ypos, vline_x2, ypos);
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

        if (TYPE_CENTER_WIDGET == connectionType) {
          len = DrawConnectionUtils.removeZigZag(px, py, len, 50);
        }
        DrawConnectionUtils.drawRound(ourPath, px, py, len, GAP);
        if (hover) {
          g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
          Stroke tmpStroke = g.getStroke();
          g.setStroke(myHoverStroke);
          g.draw(ourPath);
          g.setStroke(tmpStroke);
        }
        if (picker != null && secondarySelector != null) {
          DrawConnectionUtils.drawPick(picker, secondarySelector, px, py, len, GAP);
        }
        g.setStroke(getStroke(StrokeType.NORMAL, false, modeTo));
        g.setColor(constraintColor);
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
              g.setStroke(getStroke(StrokeType.DASH, false, modeTo));
              int overlap = (above) ? -OVER_HANG : OVER_HANG;
              if (hover) {
                g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
                Stroke tmpStroke = g.getStroke();
                g.setStroke(myHoverStroke);
                g.drawLine(constraintX, line_y + overlap, constraintX, above ? dest.y : dest.y + dest.height);
                g.setStroke(tmpStroke);
              }
              g.setColor(constraintColor);
              g.drawLine(constraintX, line_y + overlap, constraintX, above ? dest.y : dest.y + dest.height);
            }
          }
          else {
            boolean left = startx < endx;
            int line_x = startx + (left ? -1 : 1) * source.width / 4;
            g.setColor(marginColor);
            DrawConnectionUtils.drawVerticalMarginIndicator(g, String.valueOf(margin), isMarginReference, line_x, starty, endy);

            if (myDestType != DEST_PARENT || (line_x < dest.x || line_x > dest.x + dest.width)) {
              int constraint_y = (destDirection == DIR_TOP) ? dest.y : dest.y + dest.height;
              g.setStroke(getStroke(StrokeType.DASH, false, modeTo));
              int overlap = (left) ? -OVER_HANG : OVER_HANG;
              if (hover) {
                g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
                Stroke tmpStroke = g.getStroke();
                g.setStroke(myHoverStroke);
                g.drawLine(line_x + overlap, constraint_y,
                           left ? dest.x : dest.x + dest.width, constraint_y);
                g.setStroke(tmpStroke);
              }
              g.setColor(constraintColor);
              g.drawLine(line_x + overlap, constraint_y,
                         left ? dest.x : dest.x + dest.width, constraint_y);
            }
          }
        }
        g.setStroke(getStroke(StrokeType.NORMAL, false, modeTo));
        if (startx - endx == 0 && (sourceDirection != destDirection || dirDeltaX[sourceDirection] == 0)) {
          // Case for straight vertical lines or adjacent widgets connecting opposite horizontal anchors
          scale_source = 0;
          scale_dest = 0;
        }
        else if (starty - endy == 0 && (sourceDirection != destDirection || dirDeltaY[sourceDirection] == 0)) {
          // Case for straight horizontal lines or adjacent widgets connecting opposite vertical anchors
          scale_source = 0;
          scale_dest = 0;
        }
        if (sourceDirection == destDirection && margin == 0) {
          // For adjacent widgets connecting the same anchor
          scale_source /= 3;
          scale_dest /= 2;
        }
        float x1 = startx, y1 = starty, x2, y2, x3, y3, x4, y4;
        if (hover) {
          g.setColor(modeGetConstraintsColor(MODE_WILL_HOVER, color));
          Stroke tmpStroke = g.getStroke();
          g.setStroke(myHoverStroke);
          GeneralPath hoverPath = new GeneralPath(ourPath);
          hoverPath
            .curveTo(x2 = startx + scale_source * dirDeltaX[sourceDirection], y2 = starty + scale_source * dirDeltaY[sourceDirection],
                     x3 = endx + dx + scale_dest * dirDeltaX[destDirection], y3 = endy + dy + scale_dest * dirDeltaY[destDirection],
                     x4 = endx + dx, y4 = endy + dy);
          g.draw(hoverPath);
          g.setStroke(tmpStroke);
        }
        g.setColor(constraintColor);
        ourPath.curveTo(x2 = startx + scale_source * dirDeltaX[sourceDirection], y2 = starty + scale_source * dirDeltaY[sourceDirection],
                        x3 = endx + dx + scale_dest * dirDeltaX[destDirection], y3 = endy + dy + scale_dest * dirDeltaY[destDirection],
                        x4 = endx + dx, y4 = endy + dy);
        if (picker != null && secondarySelector != null) {
          picker.addCurveTo(secondarySelector, 4, (int)x1, (int)y1, (int)x2, (int)y2, (int)x3, (int)y3, (int)x4, (int)y4, 4);
        }
        g.setStroke(getStroke(StrokeType.BACKGROUND, false, modeTo));
        g.setColor(color.getBackground());
        DrawConnectionUtils.getArrow(dir, endx, endy, xPoints, yPoints);
        g.fillPolygon(xPoints, yPoints, 3);
        g.draw(ourPath);
        g.setColor(constraintColor);
        DrawConnectionUtils.getArrow(dir, endx, endy, xPoints, yPoints);
        g.fillPolygon(xPoints, yPoints, 3);
        g.draw(ourPath);
    }
    g.setStroke(defaultStroke);
    return animate;
  }

  private static void drawBaseLine(Graphics2D g,
                                   @SwingCoordinate Rectangle source,
                                   @SwingCoordinate Rectangle dest,
                                   Color color,
                                   Color hoverColor, ScenePicker picker,
                                   SecondarySelector secondarySelector, boolean hover) {

    if (hover) {
      GeneralPath hoverPath = new GeneralPath();
      g.setColor(hoverColor);
      Stroke tmpStroke = g.getStroke();
      g.setStroke(myHoverStroke);
      hoverPath.moveTo(source.x + source.width / 2., source.y);
      hoverPath.curveTo(source.x + source.width / 2., source.y - 40,
                        dest.x + dest.width / 2., dest.y + 40,
                        dest.x + dest.width / 2., dest.y);
      g.draw(hoverPath);
      g.setStroke(tmpStroke);
    }
    g.setColor(color);
    ourPath.reset();
    ourPath.moveTo(source.x + source.width / 2., source.y);
    ourPath.curveTo(source.x + source.width / 2., source.y - 40,
                    dest.x + dest.width / 2., dest.y + 40,
                    dest.x + dest.width / 2., dest.y);
    if (picker != null && secondarySelector != null) {
      picker.addCurveTo(secondarySelector, 4, (int)(source.x + source.width / 2.), (int)(source.y),
                        (int)(source.x + source.width / 2.), (int)(source.y - 40),
                        (int)(dest.x + dest.width / 2.), (int)(dest.y + 40),
                        (int)(dest.x + dest.width / 2.), (int)(dest.y), 4);
    }
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
