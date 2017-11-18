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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.BaseTarget;
import org.jetbrains.annotations.NotNull;

/**
 * {@linkplain NavBaseTarget} Contains helper functions common to navigation editor targets.
 */
public abstract class NavBaseTarget extends BaseTarget {
  protected NavBaseTarget(@NotNull SceneComponent component) {
    setComponent(component);
  }

  protected void layoutRectangle(@AndroidDpCoordinate int l,
                                 @AndroidDpCoordinate int t,
                                 @AndroidDpCoordinate int r,
                                 @AndroidDpCoordinate int b) {
    myLeft = l;
    myTop = t;
    myRight = r;
    myBottom = b;
  }

  protected void layoutCircle(@AndroidDpCoordinate int x,
                              @AndroidDpCoordinate int y,
                              @AndroidDpCoordinate int r) {
    layoutRectangle(x - r, y - r, x + r, y + r);
  }

  @SwingCoordinate
  protected int getSwingLeft(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingXDip(myLeft);
  }

  @SwingCoordinate
  protected int getSwingTop(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingYDip(myTop);
  }

  @SwingCoordinate
  protected int getSwingRight(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingXDip(myRight);
  }

  @SwingCoordinate
  protected int getSwingBottom(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingYDip(myBottom);
  }

  @SwingCoordinate
  protected int getSwingCenterX(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingXDip(getCenterX());
  }

  @SwingCoordinate
  protected int getSwingCenterY(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingYDip(getCenterY());
  }
}
