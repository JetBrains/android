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

import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.DragBaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.MultiComponentTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Implements a target allowing dragging a nav editor screen
 */
public class ScreenDragTarget extends DragBaseTarget implements MultiComponentTarget {

  private final ManualLayoutAlgorithm myAlgorithm;


  public ScreenDragTarget(@NotNull SceneComponent component, @NotNull ManualLayoutAlgorithm algorithm) {
    super();
    setComponent(component);
    myAlgorithm = algorithm;
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  protected void updateAttributes(@NotNull AttributesTransaction attributes, int x, int y) {
    // Nothing
  }

  @Override
  public void mouseDrag(@NavCoordinate int x, @NavCoordinate int y, @Nullable List<Target> closestTarget) {
    // TODO: Support growing the scrollable area when dragging a control off the screen
    SceneComponent parent = myComponent.getParent();

    if (parent == null) {
      return;
    }

    myComponent.setDragging(true);
    int dx = x - myOffsetX;
    int dy = y - myOffsetY;

    if (dx < parent.getDrawX() || dx + myComponent.getDrawWidth() > parent.getDrawX() + parent.getDrawWidth()) {
      return;
    }

    if (dy < parent.getDrawY() || dy + myComponent.getDrawHeight() > parent.getDrawY() + parent.getDrawHeight()) {
      return;
    }

    myComponent.setPosition(dx, dy, false);
    myChangedComponent = true;
  }

  @Override
  public void mouseRelease(@NavCoordinate int x, @NavCoordinate int y, @Nullable List<Target> closestTargets) {
    if (!myComponent.isDragging()) {
      return;
    }
    myComponent.setDragging(false);
    if (myComponent.getParent() != null) {
      if (Math.abs(x - myFirstMouseX) <= 1 && Math.abs(y - myFirstMouseY) <= 1) {
        return;
      }
      myAlgorithm.save(myComponent);
    }
    if (myChangedComponent) {
      myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
