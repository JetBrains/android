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
import com.android.tools.idea.common.scene.*;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCircle;
import com.android.tools.idea.common.scene.draw.DrawFilledCircle;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.draw.DrawActionHandleDrag;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;

import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.*;

/**
 * {@linkplain ActionHandleTarget} is a target for handling drag-creation of actions.
 * It appears as a circular grab handle on the right side of the navigation screen.
 */
public class ActionHandleTarget extends NavBaseTarget {
  private static final int DURATION = 200;
  @SwingCoordinate private static final int STROKE_WIDTH = 2;
  private static String DRAG_CREATE_IN_PROGRESS = "DRAG_CREATE_IN_PROGRESS";

  private static final BasicStroke STROKE = new BasicStroke(STROKE_WIDTH);

  private enum HandleState {
    INVISIBLE(0, 0),
    SMALL(INNER_RADIUS_SMALL, OUTER_RADIUS_SMALL),
    LARGE(INNER_RADIUS_LARGE, OUTER_RADIUS_LARGE);

    HandleState(@NavCoordinate float innerRadius, @NavCoordinate float outerRadius) {
      myInnerRadius = innerRadius;
      myOuterRadius = outerRadius;
    }

    @NavCoordinate private final float myInnerRadius;
    @NavCoordinate private final float myOuterRadius;
  }

  private HandleState myHandleState;
  private boolean myIsDragging = false;

  public ActionHandleTarget(@NotNull SceneComponent component) {
    super(component);
    myHandleState = calculateState();
  }

  @Override
  public int getPreferenceLevel() {
    return Target.ANCHOR_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @NavCoordinate int l,
                        @NavCoordinate int t,
                        @NavCoordinate int r,
                        @NavCoordinate int b) {
    @NavCoordinate int x = r;
    if (NavComponentHelperKt.isFragment(getComponent().getNlComponent())) {
      x += ACTION_HANDLE_OFFSET;
    }
    layoutCircle(x, t + (b - t) / 2, (int)myHandleState.myOuterRadius);
    return false;
  }

  @Override
  public void mouseDown(@NavCoordinate int x, @NavCoordinate int y) {
    Scene scene = myComponent.getScene();
    scene.getDesignSurface().getSelectionModel().setSelection(ImmutableList.of(getComponent().getNlComponent()));
    myIsDragging = true;
    scene.needsRebuildList();
    SceneComponent parent = myComponent.getParent();
    assert parent != null;
    parent.getNlComponent().putClientProperty(DRAG_CREATE_IN_PROGRESS, true);
    getComponent().setDragging(true);
    scene.repaint();
  }

  @Override
  public void mouseRelease(@NavCoordinate int x,
                           @NavCoordinate int y,
                           @NotNull List<Target> closestTargets) {
    myIsDragging = false;
    Scene scene = myComponent.getScene();
    SceneComponent parent = myComponent.getParent();
    assert parent != null;
    parent.getNlComponent().removeClientProperty(DRAG_CREATE_IN_PROGRESS);
    getComponent().setDragging(false);
    SceneComponent closestComponent =
      scene.findComponent(SceneContext.get(getComponent().getScene().getSceneManager().getSceneView()), x, y);
    if (closestComponent != null &&
        closestComponent != getComponent().getScene().getRoot() &&
        StringUtil.isNotEmpty(closestComponent.getId())) {
      NlComponent action = createAction(closestComponent);
      if (action != null) {
        getComponent().getScene().getDesignSurface().getSelectionModel().setSelection(ImmutableList.of(action));
      }
    }
    scene.needsRebuildList();
  }

  /* When true, this causes Scene.mouseRelease to change the selection to the associated SceneComponent.
  We don't have SceneComponents for actions so we need to return false here and set the selection to the
  correct NlComponent in Scene.mouseRelease */
  @Override
  public boolean canChangeSelection() {
    return false;
  }

  @Nullable
  public NlComponent createAction(@NotNull SceneComponent destination) {
    if (mIsOver) {
      return null;
    }

    NlComponent destinationNlComponent = destination.getNlComponent();

    if (!NavComponentHelperKt.isDestination(destinationNlComponent)) {
      return null;
    }

    NlComponent myNlComponent = getComponent().getNlComponent();
    return WriteCommandAction.runWriteCommandAction(myNlComponent.getModel().getProject(),
                                                    (Computable<NlComponent>)() -> NavComponentHelperKt
                                                      .createAction(myNlComponent, destinationNlComponent.getId()));
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    HandleState newState = calculateState();

    if (newState == HandleState.INVISIBLE && myHandleState == HandleState.INVISIBLE) {
      return;
    }

    SceneView view = myComponent.getScene().getDesignSurface().getCurrentSceneView();

    @SwingCoordinate float centerX = Coordinates.getSwingXDip(view, getCenterX());
    @SwingCoordinate float centerY = Coordinates.getSwingYDip(view, getCenterY());
    @SwingCoordinate Point2D.Float center = new Point2D.Float(centerX, centerY);

    @SwingCoordinate float initialRadius = Coordinates.getSwingDimension(view, myHandleState.myOuterRadius);
    @SwingCoordinate float finalRadius = Coordinates.getSwingDimension(view, newState.myOuterRadius);
    int duration = (int)Math.abs(DURATION * (finalRadius - initialRadius) / OUTER_RADIUS_LARGE);

    ColorSet colorSet = sceneContext.getColorSet();
    list.add(new DrawFilledCircle(DRAW_ACTION_HANDLE_BACKGROUND_LEVEL, center, colorSet.getBackground(),
                                  new LerpFloat(initialRadius, finalRadius, duration)));

    initialRadius = Coordinates.getSwingDimension(view, myHandleState.myInnerRadius);
    finalRadius = Coordinates.getSwingDimension(view, newState.myInnerRadius);
    Color color = getComponent().isSelected() ? colorSet.getSelectedFrames() : colorSet.getSubduedFrames();

    if (myIsDragging) {
      list.add(new DrawFilledCircle(DRAW_ACTION_HANDLE_LEVEL, center, color, finalRadius));
      list.add(new DrawActionHandleDrag(getSwingCenterX(sceneContext), getSwingCenterY(sceneContext)));
    }
    else {
      list.add(new DrawCircle(DRAW_ACTION_HANDLE_LEVEL, center, color, STROKE, new LerpFloat(initialRadius, finalRadius, duration)));
    }

    myHandleState = newState;
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    picker.addCircle(this, 0, getSwingCenterX(transform), getSwingCenterY(transform),
                     transform.getSwingDimension((int)OUTER_RADIUS_LARGE));
  }

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  private HandleState calculateState() {
    if (myIsDragging) {
      return HandleState.SMALL;
    }

    if (myComponent.getScene().getDesignSurface().getInteractionManager().isInteractionInProgress()) {
      return HandleState.INVISIBLE;
    }

    if (mIsOver) {
      return HandleState.LARGE;
    }

    if (getComponent().getDrawState() == SceneComponent.DrawState.HOVER) {
      return HandleState.SMALL;
    }

    if (getComponent().isSelected() && myComponent.getScene().getSelection().size() == 1) {
      return HandleState.SMALL;
    }

    return HandleState.INVISIBLE;
  }

  public static boolean isDragCreateInProgress(@NotNull NlComponent component) {
    NlComponent parent = component.getParent();
    return parent != null && parent.getClientProperty(DRAG_CREATE_IN_PROGRESS) != null;
  }
}
