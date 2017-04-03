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
package com.android.tools.idea.uibuilder.handlers.constraint.targets;

import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.TemporarySceneComponent;
import com.android.tools.idea.uibuilder.scene.target.Target;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Implements a target managing dragging on a dnd temporary widget
 */
public class ConstraintDragDndTarget extends ConstraintDragTarget {

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    if (myComponent instanceof TemporarySceneComponent) {
      getTargetNotchConnector().gatherNotches(myComponent);
    }
    else {
      super.mouseDown(x, y);
    }
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTarget) {
    if (myComponent instanceof TemporarySceneComponent) {
      Scene scene = myComponent.getScene();
      int dx = getTargetNotchConnector().trySnap(x);
      int dy = getTargetNotchConnector().trySnapY(y);
      myComponent.setPosition(dx, dy);
      scene.needsRebuildList();
    }
    else {
      super.mouseDrag(x, y, closestTarget);
    }
  }

  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull NlComponent component) {
    myComponent.setDragging(false);
    if (myComponent.getParent() != null) {
      AttributesTransaction attributes = component.startAttributeTransaction();
      int dx = x - myOffsetX;
      int dy = y - myOffsetY;
      Point snappedCoordinates = getTargetNotchConnector().applyNotches(myComponent, attributes, dx, dy);
      updateAttributes(attributes, snappedCoordinates.x, snappedCoordinates.y);
      attributes.apply();
      attributes.commit();
    }
    if (myChangedComponent) {
      myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }
}
