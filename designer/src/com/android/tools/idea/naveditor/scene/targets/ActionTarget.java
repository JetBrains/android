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
package com.android.tools.idea.naveditor.scene.targets;

import com.android.SdkConstants;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.naveditor.scene.draw.DrawAction;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.NORMAL;
import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.SELECTED;
import static com.android.tools.idea.naveditor.scene.targets.ActionTarget.ConnectionDirection.*;

/**
 * An Action in the navigation editor
 */
public class ActionTarget extends BaseTarget {
  @SwingCoordinate private Rectangle mySourceRect;
  @SwingCoordinate private Rectangle myDestRect;
  private final NlComponent myNlComponent;
  private final SceneComponent myDestination;

  private static final int SPACING = 15;

  public static class CurvePoints {
    @SwingCoordinate public Point p1;
    @SwingCoordinate public Point p2;
    @SwingCoordinate public Point p3;
    @SwingCoordinate public Point p4;
    public ConnectionDirection dir;
  }

  public static class SelfActionPoints {
    @SwingCoordinate public final int[] x = new int[5];
    @SwingCoordinate public final int[] y = new int[5];
    public ConnectionDirection dir;
  }

  public enum ConnectionType {NORMAL, SELF}

  public enum ConnectionDirection {
    LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1);

    static {
      LEFT.myOpposite = RIGHT;
      RIGHT.myOpposite = LEFT;
      TOP.myOpposite = BOTTOM;
      BOTTOM.myOpposite = TOP;
    }

    private ConnectionDirection myOpposite;
    private final int myDeltaX;
    private final int myDeltaY;

    ConnectionDirection(int deltaX, int deltaY) {
      myDeltaX = deltaX;
      myDeltaY = deltaY;
    }

    public int getDeltaX() {
      return myDeltaX;
    }

    public int getDeltaY() {
      return myDeltaY;
    }

    public ConnectionDirection getOpposite() {
      return myOpposite;
    }
  }

  public ActionTarget(@NotNull SceneComponent component, @NotNull SceneComponent destination, @NotNull NlComponent actionComponent) {
    setComponent(component);
    myNlComponent = actionComponent;
    myDestination = destination;
  }

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable List<Target> closestTargets) {
    myComponent.getScene().getDesignSurface().getSelectionModel().setSelection(ImmutableList.of(myNlComponent));
  }

  @Override
  public int getPreferenceLevel() {
    return 0;
  }

  @Override
  public boolean layout(@NotNull SceneContext context, int l, int t, int r, int b) {
    // TODO
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    Rectangle sourceRect = Coordinates.getSwingRectDip(sceneContext, getComponent().fillRect(null));

    String sourceId = getComponent().getId();
    if (sourceId == null) {
      return;
    }

    String targetId = NlComponent.stripId(myNlComponent.getAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION));
    if (targetId == null) {
      // TODO: error handling
      return;
    }

    myDestRect = Coordinates.getSwingRectDip(sceneContext, myDestination.fillRect(null));
    mySourceRect = sourceRect;
    boolean selected = getComponent().getScene().getSelection().contains(myNlComponent);
    DrawAction.buildDisplayList(list, sourceId.equals(targetId) ? ConnectionType.SELF : ConnectionType.NORMAL, sourceRect, myDestRect,
                                selected ? SELECTED : NORMAL);
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    if (mySourceRect == null || myDestRect == null) {
      return;
    }

    String sourceId = getComponent().getId();
    if (sourceId == null) {
      return;
    }

    String targetId = NlComponent.stripId(myNlComponent.getAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION));
    if (targetId == null) {
      return;
    }

    if (sourceId.equals(targetId)) {
      @SwingCoordinate SelfActionPoints points = getSelfActionPoints(mySourceRect, transform);
      for (int i = 1; i < points.x.length; i++) {
        picker.addLine(this, 5, points.x[i - 1], points.y[i - 1], points.x[i], points.y[i]);
      }

      return;
    }

    CurvePoints points = getCurvePoints(mySourceRect, myDestRect);
    picker.addCurveTo(this, 5, points.p1.x, points.p1.y, points.p2.x, points.p2.y, points.p3.x, points.p3.y, points.p4.x, points.p4.y);
  }

  @NotNull
  public static CurvePoints getCurvePoints(@SwingCoordinate @NotNull Rectangle source,
                                           @SwingCoordinate @NotNull Rectangle dest) {
    ConnectionDirection sourceDirection = RIGHT;
    ConnectionDirection destDirection = LEFT;
    int startx = getConnectionX(sourceDirection, source);
    int starty = getConnectionY(sourceDirection, source);
    int endx = getConnectionX(destDirection, dest);
    int endy = getConnectionY(destDirection, dest);
    int dx = getDestinationDx(destDirection);
    int dy = getDestinationDy(destDirection);
    int scale_source = 100;
    int scale_dest = 100;
    CurvePoints result = new CurvePoints();
    result.dir = destDirection;
    result.p1 = new Point(startx, starty);
    result.p2 = new Point(startx + scale_source * sourceDirection.getDeltaX(), starty + scale_source * sourceDirection.getDeltaY());
    result.p3 = new Point(endx + dx + scale_dest * destDirection.getDeltaX(), endy + dy + scale_dest * destDirection.getDeltaY());
    result.p4 = new Point(endx + dx, endy + dy);
    return result;
  }

  @NotNull
  public static SelfActionPoints getSelfActionPoints(@SwingCoordinate @NotNull Rectangle rect, @NotNull SceneContext sceneContext) {
    ConnectionDirection sourceDirection = RIGHT;
    ConnectionDirection destDirection = BOTTOM;

    SelfActionPoints selfActionPoints = new SelfActionPoints();
    selfActionPoints.dir = destDirection;

    @SwingCoordinate int spacing = sceneContext.getSwingDimensionDip(SPACING);

    selfActionPoints.x[0] = getConnectionX(sourceDirection, rect);
    selfActionPoints.x[1] = selfActionPoints.x[0] + spacing;
    selfActionPoints.x[2] = selfActionPoints.x[1];
    selfActionPoints.x[3] = selfActionPoints.x[2] - rect.width / 2;
    selfActionPoints.x[4] = selfActionPoints.x[3];

    selfActionPoints.y[0] = getConnectionY(sourceDirection, rect);
    selfActionPoints.y[1] = selfActionPoints.y[0];
    selfActionPoints.y[2] = selfActionPoints.y[1] + rect.height / 2 + spacing;
    selfActionPoints.y[3] = selfActionPoints.y[2];
    selfActionPoints.y[4] = selfActionPoints.y[3] - spacing + getDestinationDy(destDirection);

    return selfActionPoints;
  }

  private static int getConnectionX(@NotNull ConnectionDirection side, @NotNull Rectangle rect) {
    return rect.x + side.getDeltaX() + (1 + side.getDeltaX()) * rect.width / 2;
  }

  private static int getConnectionY(@NotNull ConnectionDirection side, @NotNull Rectangle rect) {
    return rect.y + side.getDeltaY() + (1 + side.getDeltaY()) * rect.height / 2;
  }

  public static int getDestinationDx(@NotNull ConnectionDirection side) {
    return side.getDeltaX() * DrawConnectionUtils.ARROW_SIDE;
  }

  public static int getDestinationDy(@NotNull ConnectionDirection side) {
    return side.getDeltaY() * DrawConnectionUtils.ARROW_SIDE;
  }
}

