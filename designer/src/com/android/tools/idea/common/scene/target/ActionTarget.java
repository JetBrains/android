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
package com.android.tools.idea.common.scene.target;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawAction;
import com.android.tools.idea.uibuilder.graphics.NlIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Implements an Action target
 */
public class ActionTarget extends BaseTarget {

  final static int mySize = 12;
  final static int myGap = 4;
  final ActionTarget myPreviousActionTarget;
  @Nullable private Action myAction;
  @NotNull private final NlIcon myIcon;
  protected boolean myIsVisible = true;

  public interface Action {
    void apply(SceneComponent component);
  }

  public ActionTarget(@Nullable ActionTarget previous, @NotNull NlIcon icon, @Nullable Action action) {
    myPreviousActionTarget = previous;
    myIcon = icon;
    myAction = action;
  }

  public void setAction(@Nullable Action action) {
    myAction = action;
  }

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  @Override
  public int getPreferenceLevel() {
    return Target.ACTION_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    float ratio = 1f / (float)sceneTransform.getScale();
    if (ratio > 2) {
      ratio = 2;
    }
    if (myPreviousActionTarget == null) {
      myLeft = l;
    }
    else {
      if (!myPreviousActionTarget.myIsVisible) {
        myLeft = myPreviousActionTarget.myRight;
      }
      else {
        myLeft = myPreviousActionTarget.myRight + (myGap * ratio);
      }
    }
    float size = (mySize * ratio);
    myTop = b + (myGap * ratio);
    myRight = myLeft + size;
    myBottom = myTop + size;
    if (!myIsVisible) {
      myLeft = (myPreviousActionTarget == null) ? myLeft : myPreviousActionTarget.myRight;
      myRight = myLeft;
    }
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }

    if (myIsVisible) {
      Rectangle src = new Rectangle();
      myComponent.fillRect(src);
      DrawAction.add(list, sceneContext, myLeft, myTop, myRight, myBottom, src, myIcon, mIsOver);
    }
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    // Do nothing
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTarget) {
    // Do nothing
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTargets) {
    if (myAction != null && closestTargets.contains(this)) {
      myAction.apply(myComponent);
      myComponent.getScene().needsRebuildList();
    }
  }
}
