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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.draw.DrawActionHandle;
import com.android.tools.idea.naveditor.scene.draw.DrawActionHandleDrag;
import com.android.tools.idea.naveditor.scene.draw.DrawColor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * {@linkplain ActionHandleTarget} is a target for handling drag-creation of actions.
 * It appears as a circular grab handle on the right side of the navigation screen.
 */
public class ActionHandleTarget extends NavBaseTarget {
  @NavCoordinate public static final int SMALL_RADIUS = 12;
  @NavCoordinate public static final int LARGE_RADIUS = 16;
  public static final int MAX_DURATION = 200;

  @NavCoordinate private int myCurrentRadius;
  private boolean myIsDragging = false;

  public ActionHandleTarget(@NotNull SceneComponent component) {
    super(component);
    myCurrentRadius = (component.isSelected() ? SMALL_RADIUS : 0);
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
    layoutCircle(r, t + (b - t) / 2, myCurrentRadius);
    return false;
  }

  @Override
  public void mouseDown(@NavCoordinate int x, @NavCoordinate int y) {
    myIsDragging = true;
    myComponent.getScene().needsRebuildList();
    getComponent().setDragging(true);
  }

  @Override
  public void mouseRelease(@NavCoordinate int x, @NavCoordinate int y, @Nullable List<Target> closestTargets) {
    myIsDragging = false;
    getComponent().setDragging(false);
  }

  public void createAction(@NotNull SceneComponent destination) {
    NlComponent destinationNlComponent = destination.getNlComponent();

    NavigationSchema schema = NavigationSchema.getOrCreateSchema(destinationNlComponent.getModel().getFacet());

    if (schema.getDestinationType(destinationNlComponent.getTagName()) == null) {
      return;
    }

    NlComponent myNlComponent = getComponent().getNlComponent();
    NlModel myModel = myNlComponent.getModel();

    new WriteCommandAction(myModel.getProject(), "Create Action", myModel.getFile()) {
      @Override
      protected void run(@NotNull Result result) {
        XmlTag tag = myNlComponent.getTag().createChildTag(NavigationSchema.TAG_ACTION, null, null, false);
        NlComponent newComponent = myModel.createComponent(tag, myNlComponent, null);
        newComponent.ensureId();
        newComponent.setAttribute(
          SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, SdkConstants.ID_PREFIX + destinationNlComponent.getId());
      }
    }.execute();
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    DrawCommand drawCommand = myIsDragging
                              ? createDrawActionHandleDrag(sceneContext)
                              : createDrawActionHandle(sceneContext);

    list.add(drawCommand);
  }

  private DrawCommand createDrawActionHandleDrag(@NotNull SceneContext sceneContext) {
    return new DrawActionHandleDrag(getSwingCenterX(sceneContext), getSwingCenterY(sceneContext),
                                    sceneContext.getSwingDimension(myCurrentRadius));
  }

  private DrawCommand createDrawActionHandle(@NotNull SceneContext sceneContext) {
    int newRadius = 0;

    if (mIsOver) {
      newRadius = LARGE_RADIUS;
    }
    else if (getComponent().getDrawState() == SceneComponent.DrawState.HOVER || getComponent().isSelected()) {
      newRadius = SMALL_RADIUS;
    }

    DrawColor borderColor = getComponent().isSelected() ? DrawColor.SELECTED_FRAMES : DrawColor.FRAMES;
    int duration = Math.abs(MAX_DURATION * (myCurrentRadius - newRadius) / SMALL_RADIUS);

    DrawActionHandle drawCommand =
      new DrawActionHandle(getSwingCenterX(sceneContext), getSwingCenterY(sceneContext),
                           sceneContext.getSwingDimension(myCurrentRadius), sceneContext.getSwingDimension(newRadius),
                           borderColor, duration);

    myCurrentRadius = newRadius;
    return drawCommand;
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    picker.addCircle(this, 0, getSwingCenterX(transform), getSwingCenterY(transform),
                     transform.getSwingDimension(LARGE_RADIUS));
  }

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }
}
