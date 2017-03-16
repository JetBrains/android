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

import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawBarrier;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.target.Target;
import org.jetbrains.annotations.NotNull;

/**
 * Implements the drag behaviour for ConstraintLayout Guideline
 */
public class BarrierTarget extends DragTarget {
  int myDirection;
  public static final int TOP = 1;
  public static final int BOTTOM = 2;
  public static final int LEFT = 3;
  public static final int RIGHT = 4;

  public static int parseDirection(String dir) {
    if ("TOP".equalsIgnoreCase(dir)) {
      return TOP;
    }
    if ("BOTTOM".equalsIgnoreCase(dir)) {
      return BOTTOM;
    }
    if ("LEFT".equalsIgnoreCase(dir)) {
      return LEFT;
    }
    if ("RIGHT".equalsIgnoreCase(dir)) {
      return RIGHT;
    }
    if ("START".equalsIgnoreCase(dir)) {
      return LEFT;
    }
    if ("END".equalsIgnoreCase(dir)) {
      return RIGHT;
    }
    return TOP;
  }

  private boolean isHorizontal() {
    return myDirection == DrawBarrier.TOP || myDirection == DrawBarrier.BOTTOM;
  }

  @Override
  public int getPreferenceLevel() {
    return Target.GUIDELINE_LEVEL;
  }

  public BarrierTarget(int direction) {
    myDirection = direction;
  }


  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    DrawBarrier.add(list, sceneContext, myLeft, myTop, isHorizontal() ? (myRight - myLeft) : (myBottom - myTop), myDirection);
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform, int l, int t, int r, int b) {

    SceneComponent parent = myComponent.getParent();
    if (parent != null) {
      if (isHorizontal()) {
        myLeft = parent.getDrawX();
        myRight = parent.getDrawX() + parent.getDrawWidth();
        myTop = t;
        myBottom = t;
      }
      else {
        myLeft = l;
        myRight = l;
        myTop = parent.getDrawY();
        myBottom = parent.getDrawY() + parent.getDrawHeight();
      }
    }

    return false;
  }

  @Override
  protected void updateAttributes(AttributesTransaction attributes, int x, int y) {


  }
}
