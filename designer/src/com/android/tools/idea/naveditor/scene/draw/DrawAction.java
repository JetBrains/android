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
import com.android.tools.idea.common.scene.draw.DrawCommandBase;
import com.android.tools.idea.common.scene.draw.DrawCommandSerializationHelperKt;
import com.android.tools.idea.naveditor.model.ActionType;
import com.android.tools.idea.naveditor.scene.NavColorSet;
import com.android.tools.idea.naveditor.scene.NavDrawHelperKt;
import com.android.tools.idea.naveditor.scene.targets.ActionTarget;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.DRAW_ACTION_LEVEL;
import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.setRenderingHints;
import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.HOVER;
import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.SELECTED;

/**
 * {@link DrawCommand} that draw a nav editor action (an arrow between two screens).
 */
public class DrawAction extends DrawCommandBase {
  private static final GeneralPath PATH = new GeneralPath();
  private final ActionType myActionType;
  @SwingCoordinate private final Rectangle2D.Float mySource = new Rectangle2D.Float();
  @SwingCoordinate private final Rectangle2D.Float myDest = new Rectangle2D.Float();

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
      .buildString(getClass().getSimpleName(), myActionType, DrawCommandSerializationHelperKt.rect2DToString(mySource),
                   DrawCommandSerializationHelperKt.rect2DToString(myDest), myMode);
  }

  @Override
  protected void onPaint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    setRenderingHints(g);
    NavColorSet color = (NavColorSet)sceneContext.getColorSet();
    draw(g, color, myActionType, mySource, myDest, myMode, sceneContext);
  }

  public DrawAction(@NotNull ActionType actionType,
                    @SwingCoordinate Rectangle2D.Float source,
                    @SwingCoordinate Rectangle2D.Float dest,
                    @NotNull DrawMode mode) {
    mySource.setRect(source);
    myDest.setRect(dest);
    myActionType = actionType;
    myMode = mode;
  }

  private DrawAction(@NotNull String[] s) {
    this(ActionType.valueOf(s[0]), DrawCommandSerializationHelperKt.stringToRect2D(s[1]),
         DrawCommandSerializationHelperKt.stringToRect2D(s[2]), DrawMode.valueOf(s[3]));
  }

  public static void buildDisplayList(@NotNull DisplayList list,
                                      @NotNull ActionType connectionType,
                                      @SwingCoordinate Rectangle2D.Float source,
                                      @SwingCoordinate Rectangle2D.Float dest,
                                      @NotNull DrawMode mode) {
    list.add(new DrawAction(connectionType, source, dest, mode));
  }

  private static void draw(@NotNull Graphics2D g,
                           @NotNull NavColorSet color,
                           @NotNull ActionType connectionType,
                           @SwingCoordinate Rectangle2D.Float source,
                           @SwingCoordinate Rectangle2D.Float dest,
                           @NotNull DrawMode mode,
                           @NotNull SceneContext sceneContext) {
    Color actionColor = (mode == SELECTED) ? color.getSelectedActions()
                                           : (mode == HOVER) ? color.getHighlightedActions() : color.getActions();
    PATH.reset();

    ActionTarget.CurvePoints points = ActionTarget.getCurvePoints(source, dest, sceneContext);
    PATH.moveTo(points.p1.x, points.p1.y);
    PATH.curveTo(points.p2.x, points.p2.y, points.p3.x, points.p3.y, points.p4.x, points.p4.y);

    BasicStroke actionStroke = (connectionType == ActionType.EXIT_DESTINATION)
                               ? NavDrawHelperKt.DASHED_ACTION_STROKE
                               : NavDrawHelperKt.ACTION_STROKE;

    g.setStroke(actionStroke);
    g.setColor(actionColor);
    g.draw(PATH);
  }
}
