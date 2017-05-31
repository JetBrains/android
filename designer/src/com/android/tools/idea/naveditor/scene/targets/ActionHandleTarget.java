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

import com.android.tools.idea.naveditor.scene.draw.DrawActionHandle;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.ScenePicker;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.target.BaseTarget;
import com.android.tools.idea.uibuilder.scene.target.Target;
import com.android.tools.sherpa.drawing.ColorSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * {@linkplain ActionHandleTarget} is a target for handling drag-creation of actions.
 * It appears as a circular grab handle on the right side of the navigation screen.
 */
public class ActionHandleTarget extends BaseTarget {
  private int myCurrentRadius = 0;

  public ActionHandleTarget(@NotNull SceneComponent component) {
    setComponent(component);
  }

  @Override
  public int getPreferenceLevel() {
    return Target.ANCHOR_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    myLeft = r - DrawActionHandle.LARGE_RADIUS;
    myTop = t + (b - t) / 2 - DrawActionHandle.LARGE_RADIUS;
    myRight = myLeft + 2 * DrawActionHandle.LARGE_RADIUS;
    myBottom = myTop + 2 * DrawActionHandle.LARGE_RADIUS;

    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    int newRadius = 0;

    if (mIsOver) {
      newRadius = DrawActionHandle.LARGE_RADIUS;
    }
    else if (getComponent().getDrawState() == SceneComponent.DrawState.HOVER || getComponent().isSelected()) {
      newRadius = DrawActionHandle.SMALL_RADIUS;
    }

    ColorSet colorSet = sceneContext.getColorSet();
    Color borderColor = colorSet.getFrames();

    if (getComponent().isSelected()) {
      borderColor = colorSet.getSelectedFrames();
    }
    else if (getComponent().getDrawState() == SceneComponent.DrawState.HOVER) {
      borderColor = colorSet.getHighlightedFrames();
    }

    Color fillColor = colorSet.getBackground();

    list.add(new DrawActionHandle(sceneContext.getSwingX(getCenterX()), sceneContext.getSwingY(getCenterY()), myCurrentRadius, newRadius,
                                  borderColor, fillColor));

    myCurrentRadius = newRadius;
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    picker.addCircle(this, 0, transform.getSwingX(getCenterX()), transform.getSwingY(getCenterY()), DrawActionHandle.LARGE_RADIUS);
  }

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }
}
