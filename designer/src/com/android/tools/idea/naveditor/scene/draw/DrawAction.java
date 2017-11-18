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
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.GeneralPath;

import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.SELECTED;
import static java.awt.BasicStroke.*;

/**
 * {@link DrawCommand} that draw a nav editor action (an arrow between two screens).
 */
public class DrawAction extends NavBaseDrawCommand {
  private static final GeneralPath PATH = new GeneralPath();
  private final ActionTarget.ConnectionType myConnectionType;
  @SwingCoordinate private final Rectangle mySource = new Rectangle();
  @SwingCoordinate private final Rectangle myDest = new Rectangle();
  private static final Stroke BACKGROUND_STROKE = new BasicStroke(8.0f, CAP_BUTT, JOIN_MITER);
  private static final Stroke REGULAR_ACTION_STROKE = new BasicStroke(3.0f);
  private static final Stroke SELF_ACTION_STROKE = new BasicStroke(3.0f, CAP_BUTT, JOIN_ROUND, 10.0f, new float[]{6.0f, 3.0f}, 0.0f);
  private static final int ARCH_LEN = 10;

  private final DrawMode myMode;

  public enum DrawMode {NORMAL, SELECTED}

  @Override
  @NotNull
  protected Object[] getProperties() {
    return new Object[]{myConnectionType, rectToString(mySource), rectToString(myDest), myMode};
  }

  public DrawAction(@NotNull String s) {
    this(parse(s, 4));
  }

  @Override
  public int getLevel() {
    return DRAW_ACTION;
  }

  @Override
  protected void onPaint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    setRenderingHints(g);
    ColorSet color = sceneContext.getColorSet();
    draw(g, color, myConnectionType, mySource, myDest, myMode, sceneContext);
  }

  public DrawAction(@NotNull ActionTarget.ConnectionType connectionType,
                    @SwingCoordinate Rectangle source,
                    @SwingCoordinate Rectangle dest,
                    @NotNull DrawMode mode) {
    mySource.setBounds(source);
    myDest.setBounds(dest);
    myConnectionType = connectionType;
    myMode = mode;
  }

  private DrawAction(@NotNull String[] s) {
    this(ActionTarget.ConnectionType.valueOf(s[0]), stringToRect(s[1]), stringToRect(s[2]), DrawMode.valueOf(s[3]));
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
          .drawRound(PATH, selfActionPoints.x, selfActionPoints.y, selfActionPoints.x.length, sceneContext.getSwingDimensionDip(ARCH_LEN));

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
