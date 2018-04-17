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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
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

  @Nullable private Action myAction;
  @NotNull private final NlIcon myIcon;
  protected boolean myIsVisible = true;

  public interface Action {
    void apply(SceneComponent component);
  }

  public ActionTarget(@NotNull NlIcon icon, @Nullable Action action) {
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

  public float getRight() {
    return myRight;
  }

  public boolean isVisible() {
    return myComponent.isSelected() && myIsVisible;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    myLeft = l;
    myRight = r;
    myTop = t;
    myBottom = b;
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (!isRenderable()) {
      return;
    }

    if (myIsVisible) {
      Rectangle src = new Rectangle();
      myComponent.fillRect(src);
      DrawAction.add(list, sceneContext, myLeft, myTop, myRight, myBottom, myIcon, mIsOver);
    }
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    if (isRenderable()) {
      picker.addRect(this, 0, transform.getSwingXDip(myLeft), transform.getSwingYDip(myTop),
                     transform.getSwingXDip(myRight), transform.getSwingYDip(myBottom));
    }
  }

  private boolean isRenderable() {
    SceneComponent component = getComponent();
    if (component.isSelected()) {
      if (component.canShowBaseline()) {
        return true;
      }
      return !component.isDragging();
    }
    return false;
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    if (myAction != null && closestTargets.contains(this)) {
      myAction.apply(myComponent);
      myComponent.getScene().needsRebuildList();
    }
  }
}
