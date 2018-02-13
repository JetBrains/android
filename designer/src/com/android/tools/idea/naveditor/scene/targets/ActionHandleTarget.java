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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.LerpValue;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCircle;
import com.android.tools.idea.common.scene.draw.DrawFilledCircle;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.draw.DrawActionHandleDrag;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.DRAW_ACTION_HANDLE_BACKGROUND_LEVEL;
import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.DRAW_ACTION_HANDLE_LEVEL;

/**
 * {@linkplain ActionHandleTarget} is a target for handling drag-creation of actions.
 * It appears as a circular grab handle on the right side of the navigation screen.
 */
public class ActionHandleTarget extends NavBaseTarget {
  @NavCoordinate private static final int INNER_RADIUS_SMALL = 5;
  @NavCoordinate private static final int INNER_RADIUS_LARGE = 8;
  @NavCoordinate private static final int OUTER_RADIUS_SMALL = 7;
  @NavCoordinate private static final int OUTER_RADIUS_LARGE = 11;
  @NavCoordinate private static final int HORIZONTAL_OFFSET = 3;
  private static final int DURATION = 200;
  @SwingCoordinate private static final int STROKE_WIDTH = 2;

  private static final BasicStroke STROKE = new BasicStroke(STROKE_WIDTH);

  private enum HandleState {
    INVISIBLE(0, 0),
    SMALL(INNER_RADIUS_SMALL, OUTER_RADIUS_SMALL),
    LARGE(INNER_RADIUS_LARGE, OUTER_RADIUS_LARGE);

    HandleState(@NavCoordinate int innerRadius, @NavCoordinate int outerRadius) {
      myInnerRadius = innerRadius;
      myOuterRadius = outerRadius;
    }

    @NavCoordinate private final int myInnerRadius;
    @NavCoordinate private final int myOuterRadius;
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
    layoutCircle(r + HORIZONTAL_OFFSET, t + (b - t) / 2, myHandleState.myOuterRadius);
    return false;
  }

  @Override
  public void mouseDown(@NavCoordinate int x, @NavCoordinate int y) {
    myComponent.getScene().getDesignSurface().getSelectionModel().setSelection(ImmutableList.of(getComponent().getNlComponent()));
    myIsDragging = true;
    myComponent.getScene().needsRebuildList();
    getComponent().setDragging(true);
  }

  @Override
  public void mouseRelease(@NavCoordinate int x, @NavCoordinate int y, @NotNull List<Target> closestTargets) {
    myIsDragging = false;
    getComponent().setDragging(false);
  }

  public void createAction(@NotNull SceneComponent destination) {
    NlComponent destinationNlComponent = destination.getNlComponent();

    NavigationSchema schema = NavigationSchema.get(destinationNlComponent.getModel().getFacet());

    if (schema.getDestinationType(destinationNlComponent.getTagName()) == null) {
      return;
    }

    NlComponent myNlComponent = getComponent().getNlComponent();
    NlModel myModel = myNlComponent.getModel();

    new WriteCommandAction(myModel.getProject(), "Create Action", myModel.getFile()) {
      @Override
      protected void run(@NotNull Result result) {
        NlComponent action = NavComponentHelperKt.createAction(myNlComponent, destinationNlComponent.getId());
      }
    }.execute();
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (myIsDragging) {
      list.add(new DrawActionHandleDrag(getSwingCenterX(sceneContext), getSwingCenterY(sceneContext),
                                        sceneContext.getSwingDimension(myHandleState.myOuterRadius)));
      return;
    }

    HandleState newState = calculateState();

    if (newState == HandleState.INVISIBLE && myHandleState == HandleState.INVISIBLE) {
      return;
    }

    @SwingCoordinate Point center = new Point(getSwingCenterX(sceneContext), getSwingCenterY(sceneContext));
    @SwingCoordinate int initialRadius = sceneContext.getSwingDimension(myHandleState.myOuterRadius);
    @SwingCoordinate int finalRadius = sceneContext.getSwingDimension(newState.myOuterRadius);
    int duration = Math.abs(DURATION * (finalRadius - initialRadius) / OUTER_RADIUS_LARGE);

    ColorSet colorSet = sceneContext.getColorSet();
    list.add(new DrawFilledCircle(DRAW_ACTION_HANDLE_BACKGROUND_LEVEL, center, colorSet.getBackground(),
                                  new LerpValue(initialRadius, finalRadius, duration)));

    initialRadius = sceneContext.getSwingDimension(myHandleState.myInnerRadius);
    finalRadius = sceneContext.getSwingDimension(newState.myInnerRadius);
    Color color = getComponent().isSelected() ? colorSet.getSelectedFrames() : colorSet.getSubduedFrames();
    list.add(new DrawCircle(DRAW_ACTION_HANDLE_LEVEL, center, color, STROKE, new LerpValue(initialRadius, finalRadius, duration)));

    myHandleState = newState;
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    picker.addCircle(this, 0, getSwingCenterX(transform), getSwingCenterY(transform),
                     transform.getSwingDimension(OUTER_RADIUS_LARGE));
  }

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  private HandleState calculateState() {
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
}
