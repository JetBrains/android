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
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.target.Target;
import java.awt.Cursor;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Guideline anchors
 */
public class GuidelineAnchorTarget extends ConstraintAnchorTarget {
  boolean myIsHorizontal;

  @Override
  public Cursor getMouseCursor(@JdkConstants.InputEventMask int modifiersEx) {
    if (myIsHorizontal) {
      return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
    }
    return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
  }

  @Override
  public int getPreferenceLevel() {
    return Target.GUIDELINE_ANCHOR_LEVEL;
  }

  public GuidelineAnchorTarget(@NotNull Type type, boolean isHorizontal) {
    super(type, true);
    myIsHorizontal = isHorizontal;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    int dist = 4;
    SceneComponent parent = myComponent.getParent();
    if (myIsHorizontal) {
      myLeft = parent.getDrawX();
      myTop = t - dist;
      myRight = myLeft + parent.getDrawWidth();
      myBottom = t + dist;
    } else {
      myLeft = l - dist;
      myTop = parent.getDrawY();
      myRight = l + dist;
      myBottom = myTop + parent.getDrawHeight();
    }
    return false;
  }

  @Override
  public void addHit(@NotNull SceneContext transform,
                     @NotNull ScenePicker.Writer picker,
                     @JdkConstants.InputEventMask int modifiersEx) {
    picker.addRect(this, 0, transform.getSwingXDip(myLeft), transform.getSwingYDip(myTop),
                   transform.getSwingXDip(myRight), transform.getSwingYDip(myBottom));
  }

  @Override
  public float getCenterX() {
    return (myLeft + myRight) / 2;
  }

  @Override
  public float getCenterY() {
    return (myTop + myBottom) / 2;
  }
}
