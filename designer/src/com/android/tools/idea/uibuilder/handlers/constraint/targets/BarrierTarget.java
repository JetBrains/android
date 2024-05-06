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

import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawBarrier;
import com.google.common.collect.ImmutableList;
import java.awt.Cursor;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Implements the drag behaviour for ConstraintLayout Barrier
 */
public class BarrierTarget extends BaseTarget {
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

  @Nullable
  @Override
  public List<SceneComponent> newSelection() {
    return ImmutableList.of(getComponent());
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
    if (isHorizontal()) {
      DrawBarrier.add(list, sceneContext, myLeft, myTop, (myRight - myLeft), myDirection, myComponent.isSelected());
    }
    else {
      DrawBarrier.add(list, sceneContext, myLeft, myTop, (myBottom - myTop), myDirection, myComponent.isSelected());
    }
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    int dist = 4;
    SceneComponent parent = myComponent.getParent();
    if (parent != null) {
      myLeft = parent.getDrawX();
      myRight = parent.getDrawX() + parent.getDrawWidth();
      myTop = parent.getDrawY();
      myBottom = parent.getDrawY() + parent.getDrawHeight();
      switch (myDirection) {
        case TOP:
          myTop = t;
          myBottom = t + dist;
          break;
        case BOTTOM:
          myTop = t;
          myBottom = t + dist;
          break;
        case LEFT:
          myLeft = l;
          myRight = l + dist;
          break;
        case RIGHT:
          myLeft = l;
          myRight = l+ dist;
          break;
      }
    }
    return false;
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myComponent.setSelected(true);
  }

  @Override
  public Cursor getMouseCursor(@JdkConstants.InputEventMask int modifiersEx) {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  @Override
  protected boolean isHittable() {
    return true;
  }

  @Override
  public String getToolTipText() {
      switch (myDirection) {
        case TOP: return "Barrier Top";
        case BOTTOM:return "Barrier Bottom";
        case LEFT:return "Barrier Left";
        case RIGHT:return "Barrier Right";
      }
      return "Barrier Unknown";
  }
}
