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
package com.android.tools.idea.uibuilder.handlers.linear.targets;

import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.DragBaseTarget;
import com.android.tools.idea.uibuilder.scene.target.Notch;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.scene.target.TargetSnapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Target to handle the drag of LinearLayout's children
 */
public class LinearDragTarget extends DragBaseTarget {

  private final LinearLayoutHandler myHandler;
  private final boolean myIsDragFromPalette;
  private LinearSeparatorTarget myClosest;
  private boolean myDragHandled;

  public LinearDragTarget(@NotNull LinearLayoutHandler handler) {
    this(handler, false);
  }

  /**
   * @param fromPalette set to true if the drag is coming from the palette
   */
  public LinearDragTarget(@NotNull LinearLayoutHandler handler, boolean fromPalette) {
    myHandler = handler;
    myIsDragFromPalette = fromPalette;
  }

  @Override
  protected void updateAttributes(@NotNull AttributesTransaction attributes, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    //Do nothing
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    SceneComponent parent = myComponent.getParent();
    assert parent != null;
    myHandler.setDragging(myComponent, true);
    // Need to call this to update the targetsProvider when moving from one layout to another during a drag
    // but we should have a better scenario to recreate the targets
    ((LayoutlibSceneManager)parent.getScene().getSceneManager()).addTargets(myComponent);
    parent.updateTargets(true);
    myDragHandled = false;
    super.mouseDown(x, y);
    myComponent.setModelUpdateAuthorized(false);
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTargets) {
    SceneComponent sceneParent = myComponent.getParent();
    assert sceneParent != null;
    TargetSnapper snapper = getTargetNotchSnapper();
    Notch snappedNotch;
    Target closestTarget = null;
    myComponent.setDragging(true);

    x -= myOffsetX;
    y -= myOffsetY;
    if (myHandler.isVertical(sceneParent.getNlComponent())) {
      int middle = myComponent.getDrawHeight() / 2;
      int parentHeight = sceneParent.getDrawHeight();
      int nx = myIsDragFromPalette ? x : myComponent.getDrawX();
      int ny = snapper.trySnapY(min(max(y, -middle), parentHeight + middle));
      myComponent.setPosition(nx, ny, false);
      snappedNotch = snapper.getSnappedNotchY();
    }
    else {
      int middle = myComponent.getDrawWidth() / 2;
      int parentWidth = sceneParent.getDrawWidth();
      int nx = snapper.trySnapX(min(max(x, -middle), parentWidth + middle));
      int ny = myIsDragFromPalette ? y : myComponent.getDrawY();
      myComponent.setPosition(nx, ny, false);
      snappedNotch = snapper.getSnappedNotchX();
    }

    // We get the snapped Notch
    if (snappedNotch != null) {
      closestTarget = snappedNotch.getTarget();
    }

    if (myClosest != closestTarget) {
      // Reset previous closest Target
      if (myClosest != null) {
        myClosest.setHighlight(false);
      }

      if (closestTarget != null && closestTarget instanceof LinearSeparatorTarget) {
        myClosest = (LinearSeparatorTarget)closestTarget;
        myClosest.setHighlight(true, myComponent.getDrawWidth(), myComponent.getDrawHeight());
      }
      else {
        myClosest = null;
      }
    }

    myComponent.getScene().repaint();
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTarget) {
    super.mouseRelease(x, y, closestTarget);
    myComponent.setModelUpdateAuthorized(true);
    myHandler.setDragging(myComponent, false);
    SceneComponent parent = myComponent.getParent();
    assert parent != null;
    parent.updateTargets(true);
    if (myClosest != null) {
      myClosest.setHighlight(false);
      if (!LinearLayoutHandler.insertComponentAtTarget(myComponent, myClosest, myIsDragFromPalette)) {
        myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
        return;
      }
      myDragHandled = true;
    }
    else {
      myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
    }
  }

  public boolean isDragHandled() {
    return myDragHandled;
  }

  public void cancel() {
    myHandler.setDragging(myComponent, false);
    if (myClosest != null) {
      myClosest.setHighlight(false);
    }
  }
}
