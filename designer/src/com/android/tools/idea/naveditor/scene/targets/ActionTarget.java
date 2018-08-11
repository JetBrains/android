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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.ArrowDirection;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawArrow;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.model.ActionType;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.NavColorSet;
import com.android.tools.idea.naveditor.scene.NavDrawHelperKt;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.naveditor.scene.draw.DrawAction;
import com.android.tools.idea.naveditor.scene.draw.DrawSelfAction;
import com.google.common.collect.ImmutableList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.*;
import static com.android.tools.idea.naveditor.scene.targets.ActionTarget.ConnectionDirection.*;

/**
 * An Action in the navigation editor
 */
public class ActionTarget extends BaseTarget {
  @SwingCoordinate private Rectangle mySourceRect;
  @SwingCoordinate private Rectangle myDestRect;
  private final NlComponent myNlComponent;
  private final SceneComponent myDestination;
  private boolean myHighlighted = false;

  @NavCoordinate private static final int ACTION_PADDING = JBUI.scale(8);
  @NavCoordinate private static final int CONTROL_POINT_THRESHOLD = JBUI.scale(120);

  private static final ConnectionDirection START_DIRECTION = RIGHT;

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

  public enum ConnectionType {NORMAL, EXIT}

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

  public String getId() {
    return myNlComponent.getId();
  }

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  public void setHighlighted(boolean highlighted) {
    myHighlighted = highlighted;
  }

  public boolean isHighlighted() {
    return myHighlighted;
  }

  @Override
  public void mouseRelease(int x, int y, @NotNull List<Target> closestTargets) {
    myComponent.getScene().getDesignSurface().getSelectionModel().setSelection(ImmutableList.of(myNlComponent));
  }

  // TODO: This should depend on selection
  @Override
  public int getPreferenceLevel() {
    return DRAG_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext context, int l, int t, int r, int b) {
    // TODO
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    Rectangle sourceRect = Coordinates.getSwingRect(sceneContext, getComponent().fillRect(null));

    String sourceId = getComponent().getId();
    if (sourceId == null) {
      return;
    }

    String targetId = NavComponentHelperKt.getEffectiveDestinationId(myNlComponent);
    if (targetId == null) {
      // TODO: error handling
      return;
    }

    Rectangle destRect = myDestination.fillRect(null);
    myDestination.getTargets().forEach(t -> {
      if (t instanceof NavBaseTarget) {
        destRect.add(((NavBaseTarget)t).getBounds());
      }
    });
    myDestRect = Coordinates.getSwingRect(sceneContext, destRect);
    mySourceRect = sourceRect;

    boolean selected = getComponent().getScene().getSelection().contains(myNlComponent);

    NavColorSet colorSet = (NavColorSet)sceneContext.getColorSet();
    Color color = selected ? colorSet.getSelectedActions()
                           : mIsOver || myHighlighted ? colorSet.getHighlightedActions() : colorSet.getActions();

    if (sourceId.equals(targetId)) {
      renderSelfAction(list, sceneContext, color);
      return;
    }

    ConnectionType connectionType = ConnectionType.NORMAL;
    if (NavComponentHelperKt.getActionType(myNlComponent) == ActionType.EXIT) {
      connectionType = ConnectionType.EXIT;
    }

    DrawAction.buildDisplayList(list, connectionType, sourceRect, myDestRect,
                                selected ? SELECTED : mIsOver || myHighlighted ? HOVER : NORMAL);

    ConnectionDirection direction = getDestinationDirection(sourceRect, myDestRect);
    Point arrowPoint = getArrowPoint(sceneContext, myDestRect, direction);

    ArrowDirection arrowDirection = getArrowDirection(direction);

    SceneView view = getComponent().getScene().getDesignSurface().getCurrentSceneView();

    RoundRectangle2D.Float arrowRectangle = getArrowRectangle(view, arrowPoint, direction);

    list.add(new DrawArrow(NavDrawHelperKt.DRAW_ACTION_LEVEL, arrowDirection, arrowRectangle, color));
  }

  @NotNull
  public NlComponent getActionComponent() {
    return myNlComponent;
  }

  private void renderSelfAction(@NotNull DisplayList list, @NotNull SceneContext sceneContext, Color color) {
    Point start = getStartPoint(mySourceRect);
    Point arrowPoint = getArrowPoint(sceneContext, myDestRect, BOTTOM);
    arrowPoint.x += myDestRect.width / 2
                    + sceneContext.getSwingDimension(NavDrawHelperKt.SELF_ACTION_LENGTHS[0]
                                                     - NavDrawHelperKt.SELF_ACTION_LENGTHS[2]);

    SceneView view = getComponent().getScene().getDesignSurface().getCurrentSceneView();

    RoundRectangle2D.Float arrowRectangle = getArrowRectangle(view, arrowPoint, BOTTOM);
    Point end = new Point((int)arrowRectangle.x + (int)arrowRectangle.width / 2, (int)arrowRectangle.y + (int)arrowRectangle.height - 1);

    list.add(new DrawArrow(NavDrawHelperKt.DRAW_ACTION_LEVEL, ArrowDirection.UP, arrowRectangle, color));
    list.add(new DrawSelfAction(start, end, color));
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

    String targetId = NavComponentHelperKt.getEffectiveDestinationId(myNlComponent);
    if (targetId == null) {
      return;
    }

    if (sourceId.equals(targetId)) {
      @SwingCoordinate Point[] points = getSelfActionPoints(mySourceRect, transform);
      for (int i = 1; i < points.length; i++) {
        picker.addLine(this, 0, points[i - 1].x, points[i - 1].y, points[i].x, points[i].y, 5);
      }

      return;
    }

    CurvePoints points = getCurvePoints(mySourceRect, myDestRect, transform);
    picker.addCurveTo(this, 0, points.p1.x, points.p1.y, points.p2.x, points.p2.y, points.p3.x, points.p3.y, points.p4.x, points.p4.y, 10);
  }

  @NotNull
  public static CurvePoints getCurvePoints(@SwingCoordinate @NotNull Rectangle source,
                                           @SwingCoordinate @NotNull Rectangle dest,
                                           SceneContext sceneContext) {
    ConnectionDirection destDirection = getDestinationDirection(source, dest);
    CurvePoints result = new CurvePoints();
    result.dir = destDirection;
    result.p1 = getStartPoint(source);
    result.p4 = getEndPoint(sceneContext, dest, destDirection);
    result.p2 = getControlPoint(sceneContext, result.p1, result.p4, START_DIRECTION);
    result.p3 = getControlPoint(sceneContext, result.p4, result.p1, destDirection);

    return result;
  }

  @NotNull
  public static Point[] getSelfActionPoints(@SwingCoordinate @NotNull Rectangle rect, @NotNull SceneContext sceneContext) {
    Point start = getStartPoint(rect);
    Point end = getSelfActionEndPoint(rect, sceneContext);

    return NavDrawHelperKt.selfActionPoints(start, end, sceneContext);
  }

  @NotNull
  public static Point getSelfActionEndPoint(@SwingCoordinate @NotNull Rectangle rect, @NotNull SceneContext sceneContext) {
    Point end = getEndPoint(sceneContext, rect, BOTTOM);
    end.x += rect.width / 2 + sceneContext.getSwingDimension(NavDrawHelperKt.SELF_ACTION_LENGTHS[0]
                                                             - NavDrawHelperKt.SELF_ACTION_LENGTHS[2]);

    return end;
  }

  @NotNull
  private static Point getArrowPoint(@NotNull SceneContext context, @NotNull Rectangle rectangle, @NotNull ConnectionDirection direction) {
    return shiftPoint(getConnectionPoint(rectangle, direction), direction, context.getSwingDimension(ACTION_PADDING));
  }

  @NotNull
  private static Point getStartPoint(@NotNull Rectangle rectangle) {
    return getConnectionPoint(rectangle, START_DIRECTION);
  }

  @NotNull
  private static Point getEndPoint(@NotNull SceneContext context, @NotNull Rectangle rectangle, @NotNull ConnectionDirection direction) {
    return shiftPoint(getConnectionPoint(rectangle, direction),
                      direction,
                      context.getSwingDimension((int)NavSceneManager.ACTION_ARROW_PARALLEL + ACTION_PADDING) - 1);
  }

  @NotNull
  private static Point getConnectionPoint(@NotNull Rectangle rectangle, @NotNull ConnectionDirection direction) {
    return shiftPoint(getCenterPoint(rectangle), direction, rectangle.width / 2, rectangle.height / 2);
  }

  @NotNull
  private static Point getControlPoint(@NotNull SceneContext context,
                                       @NotNull Point p1,
                                       @NotNull Point p2,
                                       @NotNull ConnectionDirection direction) {
    int shift = (int)Math.min(Math.hypot(p1.x - p2.x, p1.y - p2.y) / 2, context.getSwingDimension(CONTROL_POINT_THRESHOLD));
    return shiftPoint(p1, direction, shift);
  }

  @NotNull
  private static Point shiftPoint(@NotNull Point p, @NotNull ConnectionDirection direction, int shift) {
    return shiftPoint(p, direction, shift, shift);
  }

  @NotNull
  private static Point shiftPoint(@NotNull Point p, ConnectionDirection direction, int shiftX, int shiftY) {
    return new Point(p.x + shiftX * direction.getDeltaX(), p.y + shiftY * direction.getDeltaY());
  }

  /**
   * Determines which side of the destination the action should be attached to.
   * If the starting point of the action is:
   * Above the top-left to bottom-right diagonal of the destination, and higher than the center point of the destination: TOP
   * Below the top-right to bottom-left diagonal of the destination, and lower than the center point of the destination: BOTTOM
   * Otherwise: LEFT
   */
  @NotNull
  private static ConnectionDirection getDestinationDirection(@NotNull Rectangle source, @NotNull Rectangle destination) {
    Point start = getStartPoint(source);
    Point end = getCenterPoint(destination);

    float slope = (destination.width == 0) ? 1f : (float)destination.height / destination.width;
    float rise = (start.x - end.x) * slope;
    boolean higher = start.y < end.y;

    if (higher && start.y < end.y + rise) {
      return TOP;
    }

    if (!higher && start.y > end.y - rise) {
      return BOTTOM;
    }

    return LEFT;
  }

  @NotNull
  private static RoundRectangle2D.Float getArrowRectangle(@NotNull SceneView view, @NotNull Point p, @NotNull ConnectionDirection direction) {
    RoundRectangle2D.Float rectangle = new RoundRectangle2D.Float();
    float parallel = Coordinates.getSwingDimension(view, NavSceneManager.ACTION_ARROW_PARALLEL);
    float perpendicular = Coordinates.getSwingDimension(view, NavSceneManager.ACTION_ARROW_PERPENDICULAR);
    float deltaX = direction.getDeltaX();
    float deltaY = direction.getDeltaY();

    rectangle.x = p.x + (deltaX == 0 ? -perpendicular : (parallel * (deltaX - 1))) / 2;
    rectangle.y = p.y + (deltaY == 0 ? -perpendicular : (parallel * (deltaY - 1))) / 2;
    rectangle.width = Math.abs(deltaX * parallel) + Math.abs(deltaY * perpendicular);
    rectangle.height = Math.abs(deltaX * perpendicular) + Math.abs(deltaY * parallel);

    return rectangle;
  }

  @NotNull
  private static ArrowDirection getArrowDirection(@NotNull ConnectionDirection direction) {
    switch (direction) {
      case LEFT:
        return ArrowDirection.RIGHT;
      case RIGHT:
        return ArrowDirection.LEFT;
      case TOP:
        return ArrowDirection.DOWN;
      case BOTTOM:
        return ArrowDirection.UP;
    }

    throw new IllegalArgumentException();
  }

  @NotNull
  private static Point getCenterPoint(@NotNull Rectangle rectangle) {
    return new Point((int)rectangle.getCenterX(), (int)rectangle.getCenterY());
  }
}

