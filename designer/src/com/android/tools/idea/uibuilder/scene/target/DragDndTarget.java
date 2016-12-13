/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.TemporarySceneComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implements a target managing dragging on a dnd temporary widget
 */
public class DragDndTarget extends DragTarget {

  @Override
  public void mouseDown(int x, int y) {
    if (myComponent instanceof TemporarySceneComponent) {
      gatherNotches();
    } else {
      super.mouseDown(x, y);
    }
  }

  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {
    if (myComponent instanceof TemporarySceneComponent) {
      Scene scene = myComponent.getScene();
      int dx = snapX(x);
      int dy = snapY(y);
      myComponent.setPosition(dx, dy);
      scene.needsRebuildList();
    } else {
      super.mouseDrag(x, y, closestTarget);
    }
  }

  public void mouseRelease(int x, int y, @NotNull NlComponent component) {
    if (myComponent.getParent() != null) {
      AttributesTransaction attributes = component.startAttributeTransaction();
      int dx = x - myOffsetX;
      int dy = y - myOffsetY;
      if (myCurrentNotchX != null) {
        dx = myCurrentNotchX.apply(dx);
        if (myComponent.allowsAutoConnect()) {
          myCurrentNotchX.apply(attributes);
        }
        myCurrentNotchX = null;
      }
      if (myCurrentNotchY != null) {
        dy = myCurrentNotchY.apply(dy);
        if (myComponent.allowsAutoConnect()) {
          myCurrentNotchY.apply(attributes);
        }
        myCurrentNotchY = null;
      }
      updateAttributes(attributes, dx, dy);
      cleanup(attributes);
      attributes.apply();
      attributes.commit();
    }
    if (myChangedComponent) {
      myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }
}
