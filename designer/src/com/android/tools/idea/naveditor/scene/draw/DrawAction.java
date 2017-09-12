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
package com.android.tools.idea.naveditor.scene.draw;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.naveditor.scene.targets.ActionTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils;
import com.android.tools.sherpa.drawing.ColorSet;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.util.Map;

import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.SELECTED;
import static java.awt.BasicStroke.CAP_BUTT;
import static java.awt.BasicStroke.JOIN_MITER;

/**
 * {@link DrawCommand} that draw a nav editor action (an arrow between two screens).
 */
public class DrawAction extends NavBaseDrawCommand {
  private final static Map<RenderingHints.Key, Object> HQ_RENDERING_HITS = ImmutableMap.of(
    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR
  );

  private static final GeneralPath PATH = new GeneralPath();
  private final ActionTarget.ConnectionType myConnectionType;
  @SwingCoordinate private Rectangle mySource = new Rectangle();
  @SwingCoordinate private Rectangle myDest = new Rectangle();
  private static final Stroke BACKGROUND_STROKE = new BasicStroke(8.0f, CAP_BUTT, JOIN_MITER);
  private static final Stroke REGULAR_ACTION_STROKE = new BasicStroke(3.0f);
  private static final Stroke SELF_ACTION_STROKE = new BasicStroke(3.0f,
                                                                   BasicStroke.CAP_BUTT,
                                                                   BasicStroke.JOIN_ROUND,
                                                                   10.0f, new float[]{6.0f, 3.0f}, 0.0f);
  private static final int ARCHLEN = 10;

  private final DrawMode myMode;

  public enum DrawMode {NORMAL, SELECTED}

  @Override
  @NotNull
  protected Object[] getProperties() {
    return new Object[]{myConnectionType, rectToString(mySource), rectToString(myDest), myMode};
  }

  private static String rectToString(@NotNull Rectangle r) {
    return r.x + "x" + r.y + "x" + r.width + "x" + r.height;
  }

  private static Rectangle stringToRect(@NotNull String s) {
    String[] sp = s.split("x");
    int c = -1;
    Rectangle r = new Rectangle();
    r.x = Integer.parseInt(sp[++c]);
    r.y = Integer.parseInt(sp[++c]);
    r.width = Integer.parseInt(sp[++c]);
    r.height = Integer.parseInt(sp[++c]);
    return r;
  }

  public DrawAction(@NotNull String s) {
    String[] sp = s.split(",");
    int c = -1;
    myConnectionType = ActionTarget.ConnectionType.valueOf(sp[++c]);
    mySource = stringToRect(sp[++c]);
    myDest = stringToRect(sp[++c]);
    myMode = DrawMode.valueOf(sp[++c]);
  }

  @Override
  public int getLevel() {
    return DRAW_ACTION;
  }

  @Override
  public void paint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    g.setRenderingHints(HQ_RENDERING_HITS);
    Color previousColor = g.getColor();
    Stroke previousStroke = g.getStroke();
    ColorSet color = sceneContext.getColorSet();
    draw(g, color, myConnectionType, mySource, myDest, myMode, sceneContext);
    g.setColor(previousColor);
    g.setStroke(previousStroke);
  }

  private DrawAction(@NotNull ActionTarget.ConnectionType connectionType,
                     @SwingCoordinate Rectangle source,
                     @SwingCoordinate Rectangle dest,
                     @NotNull DrawMode mode) {
    mySource.setBounds(source);
    myDest.setBounds(dest);
    myConnectionType = connectionType;
    mySource.setBounds(source);
    myDest.setBounds(dest);
    myMode = mode;
  }

  public static void buildDisplayList(@NotNull DisplayList list,
                                      @NotNull ActionTarget.ConnectionType connectionType,
                                      @SwingCoordinate Rectangle source,
                                      @SwingCoordinate Rectangle dest,
                                      @NotNull DrawMode mode) {
    list.add(new DrawAction(connectionType, source, dest, mode));
  }

  private static void draw(@NotNull Graphics2D g,
                           @NotNull ColorSet color,
                           @NotNull ActionTarget.ConnectionType connectionType,
                           @SwingCoordinate Rectangle source,
                           @SwingCoordinate Rectangle dest,
                           @NotNull DrawMode mode,
                           @NotNull SceneContext sceneContext) {
    Color actionColor = (mode == SELECTED) ? color.getSelectedFrames() : color.getFrames();

    @SwingCoordinate int endX;
    @SwingCoordinate int endY;
    Stroke actionStroke;
    ActionTarget.ConnectionDirection direction;

    PATH.reset();
    g.setStroke(BACKGROUND_STROKE);
    g.setColor(color.getBackground());

    switch (connectionType) {
      case SELF:
        ActionTarget.SelfActionPoints selfActionPoints = ActionTarget.getSelfActionPoints(source, sceneContext);
        PATH.moveTo(selfActionPoints.x[0], selfActionPoints.y[0]);
        DrawConnectionUtils
          .drawRound(PATH, selfActionPoints.x, selfActionPoints.y, selfActionPoints.x.length, sceneContext.getSwingDimension(ARCHLEN));

        endX = selfActionPoints.x[selfActionPoints.y.length - 1];
        endY = selfActionPoints.y[selfActionPoints.y.length - 1];
        actionStroke = SELF_ACTION_STROKE;
        direction = selfActionPoints.dir;

        break;
      case NORMAL:
        ActionTarget.CurvePoints points = ActionTarget.getCurvePoints(source, dest);
        PATH.moveTo(points.p1.x, points.p1.y);
        PATH.curveTo(points.p2.x, points.p2.y, points.p3.x, points.p3.y, points.p4.x, points.p4.y);

        endX = points.p4.x;
        endY = points.p4.y;
        actionStroke = REGULAR_ACTION_STROKE;
        direction = points.dir;

        break;
      default:
        return;
    }

    @SwingCoordinate int arrowX = endX - ActionTarget.getDestinationDx(direction);
    @SwingCoordinate int arrowY = endY - ActionTarget.getDestinationDy(direction);
    @SwingCoordinate int[] xPoints = new int[3];
    @SwingCoordinate int[] yPoints = new int[3];
    DrawConnectionUtils
      .getArrow(direction.ordinal(), arrowX, arrowY, xPoints, yPoints);
    g.fillPolygon(xPoints, yPoints, 3);
    g.draw(PATH);
    g.setStroke(actionStroke);
    g.setColor(actionColor);
    g.fillPolygon(xPoints, yPoints, 3);
    g.draw(PATH);
  }
}
