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
import com.android.tools.idea.common.scene.draw.DrawCommandSerializationHelperKt;
import com.android.tools.idea.naveditor.scene.NavColorSet;
import com.android.tools.idea.naveditor.scene.NavDrawHelperKt;
import com.android.tools.idea.naveditor.scene.targets.ActionTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.GeneralPath;

import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.DRAW_ACTION_LEVEL;
import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.setRenderingHints;
import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.HOVER;
import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.SELECTED;

/**
 * {@link DrawCommand} that draw a nav editor action (an arrow between two screens).
 */
public class DrawAction extends NavBaseDrawCommand {
  private static final GeneralPath PATH = new GeneralPath();
  private final ActionTarget.ConnectionType myConnectionType;
  @SwingCoordinate private final Rectangle mySource = new Rectangle();
  @SwingCoordinate private final Rectangle myDest = new Rectangle();
  private static final int ARCH_LEN = 10;

  private final DrawMode myMode;

  public enum DrawMode {NORMAL, SELECTED, HOVER}

  public DrawAction(@NotNull String s) {
    this(DrawCommandSerializationHelperKt.parse(s, 4));
  }

  @Override
  public int getLevel() {
    return DRAW_ACTION_LEVEL;
  }

  @Override
  public String serialize() {
    return DrawCommandSerializationHelperKt
      .buildString(getClass().getSimpleName(), myConnectionType, DrawCommandSerializationHelperKt.rectToString(mySource),
                   DrawCommandSerializationHelperKt.rectToString(myDest), myMode);
  }

  @Override
  protected void onPaint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    setRenderingHints(g);
    NavColorSet color = (NavColorSet)sceneContext.getColorSet();
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
    this(ActionTarget.ConnectionType.valueOf(s[0]), DrawCommandSerializationHelperKt.stringToRect(s[1]),
         DrawCommandSerializationHelperKt.stringToRect(s[2]), DrawMode.valueOf(s[3]));
  }

  public static void buildDisplayList(@NotNull DisplayList list,
                                      @NotNull ActionTarget.ConnectionType connectionType,
                                      @SwingCoordinate Rectangle source,
                                      @SwingCoordinate Rectangle dest,
                                      @NotNull DrawMode mode) {
    list.add(new DrawAction(connectionType, source, dest, mode));
  }

  private static void draw(@NotNull Graphics2D g,
                           @NotNull NavColorSet color,
                           @NotNull ActionTarget.ConnectionType connectionType,
                           @SwingCoordinate Rectangle source,
                           @SwingCoordinate Rectangle dest,
                           @NotNull DrawMode mode,
                           @NotNull SceneContext sceneContext) {
    Color actionColor = (mode == SELECTED) ? color.getSelectedActions()
                                           : (mode == HOVER) ? color.getHighlightedActions() : color.getActions();
    PATH.reset();

    switch (connectionType) {
      case SELF:
        ActionTarget.SelfActionPoints selfActionPoints = ActionTarget.getSelfActionPoints(source, sceneContext);
        PATH.moveTo(selfActionPoints.x[0], selfActionPoints.y[0]);
        DrawConnectionUtils
          .drawRound(PATH, selfActionPoints.x, selfActionPoints.y, selfActionPoints.x.length, sceneContext.getSwingDimension(ARCH_LEN));

        break;
      case NORMAL:
      case EXIT:
        ActionTarget.CurvePoints points = ActionTarget.getCurvePoints(source, dest, sceneContext);
        PATH.moveTo(points.p1.x, points.p1.y);
        PATH.curveTo(points.p2.x, points.p2.y, points.p3.x, points.p3.y, points.p4.x, points.p4.y);

        break;
      default:
        return;
    }

    BasicStroke actionStroke = (connectionType == ActionTarget.ConnectionType.EXIT)
                               ? NavDrawHelperKt.DASHED_ACTION_STROKE
                               : NavDrawHelperKt.ACTION_STROKE;

    g.setStroke(actionStroke);
    g.setColor(actionColor);
    g.draw(PATH);
  }
}
