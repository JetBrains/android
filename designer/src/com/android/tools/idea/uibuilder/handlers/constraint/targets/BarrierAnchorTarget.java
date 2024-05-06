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
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;


/**
 * Guideline anchors
 */
public class BarrierAnchorTarget extends ConstraintAnchorTarget {
  int myDirection;

  @Override
  public int getPreferenceLevel() {
    return Target.GUIDELINE_ANCHOR_LEVEL;
  }

  public BarrierAnchorTarget(@NotNull Type type, int dir) {
    super(type, true);
    myDirection = dir;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    int dist = 8;
    SceneComponent parent = myComponent.getParent();
    myLeft = parent.getDrawX();
    myRight = parent.getDrawX() + parent.getDrawWidth();
    myTop = parent.getDrawY();
    myBottom = parent.getDrawY() + parent.getDrawHeight();
    if (parent != null) {
      switch (myDirection) {
        case BarrierTarget.TOP:
          myTop = t - dist;
          myBottom = t;
          break;
        case BarrierTarget.BOTTOM:
          myTop = t;
          myBottom = t + dist;
          break;
        case BarrierTarget.LEFT:
          myLeft = l- dist;
          myRight = l ;
          break;
        case BarrierTarget.RIGHT:
          myLeft = l;
          myRight = l+ dist;
          break;
      }
    }
    return false;
  }

  @Override
  public void addHit(@NotNull SceneContext transform,
                     @NotNull ScenePicker picker,
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
    return (myLeft + myRight) / 2;
  }
}
