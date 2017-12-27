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

import com.android.tools.idea.naveditor.scene.targets.ActionTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.sherpa.drawing.ColorSet;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.util.Map;

import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.SELECTED;

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
  private static final Stroke BACKGROUND_STROKE = new BasicStroke(8);

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
    int c = 0;
    Rectangle r = new Rectangle();
    r.x = Integer.parseInt(sp[c++]);
    r.y = Integer.parseInt(sp[c++]);
    r.width = Integer.parseInt(sp[c++]);
    r.height = Integer.parseInt(sp[c++]);
    return r;
  }

  public DrawAction(@NotNull String s) {
    String[] sp = s.split(",");
    int c = 0;
    myConnectionType = ActionTarget.ConnectionType.valueOf(sp[c++]);
    mySource = stringToRect(sp[c++]);
    myDest = stringToRect(sp[c++]);
    myMode = DrawMode.valueOf(sp[c++]);
  }

  @Override
  public int getLevel() {
    return DRAW_ACTION;
  }

  @Override
  public void paint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    g.setRenderingHints(HQ_RENDERING_HITS);
    Color previousColor = g.getColor();
    ColorSet color = sceneContext.getColorSet();
    g.setColor(color.getConstraints());
    draw(g, color, myConnectionType, mySource, myDest, myMode);
    g.setColor(previousColor);
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
                          @NotNull DrawMode mode) {
    Color actionColor = (mode == SELECTED) ? color.getSelectedFrames() : color.getFrames();

    ActionTarget.CurvePoints points = ActionTarget.getCurvePoints(source, dest);
    PATH.reset();
    PATH.moveTo(points.p1.x, points.p1.y);
    Stroke defaultStroke;
    switch (connectionType) {
      case SELF:
        // TODO
        break;
      case NORMAL:
        g.setColor(actionColor);
        PATH.curveTo(points.p2.x, points.p2.y, points.p3.x, points.p3.y, points.p4.x, points.p4.y);
        defaultStroke = g.getStroke();
        g.setStroke(BACKGROUND_STROKE);
        g.setColor(color.getBackground());
        int[] xPoints = new int[3];
        int[] yPoints = new int[3];
        int arrowX = points.p4.x - ActionTarget.getDestinationDx(points.dir);
        int arrowY = points.p4.y - ActionTarget.getDestinationDy(points.dir);
        DrawConnectionUtils
          .getArrow(points.dir.ordinal(), arrowX, arrowY, xPoints, yPoints);
        g.fillPolygon(xPoints, yPoints, 3);
        g.draw(PATH);
        g.setStroke(defaultStroke);
        g.setColor(actionColor);
        DrawConnectionUtils.getArrow(points.dir.ordinal(), arrowX, arrowY, xPoints, yPoints);
        g.fillPolygon(xPoints, yPoints, 3);
        g.draw(PATH);
    }
  }
}
