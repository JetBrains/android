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

import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.naveditor.scene.draw.DrawAction;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Target responsible for adding the "Initial Destination" arrow.
 *
 * TODO: add support for editing as a pseudo-property on destinations
 * TODO: 63031461 repurpose this class for global actions
 */
public class StartDestinationTarget extends BaseTarget {

  private static final int LENGTH = 34;

  public StartDestinationTarget(@NotNull SceneComponent component) {
    setComponent(component);
  }

  @Override
  public int getPreferenceLevel() {
    return Target.ANCHOR_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext context, int l, int t, int r, int b) {
    // TODO
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    Rectangle dest = Coordinates.getSwingRect(sceneContext, getComponent().fillRect(null));
    Rectangle src = new Rectangle();
    src.setBounds(dest);
    src.translate(-1 * (dest.width + LENGTH), 0);
    // TODO: make separate connection type?
    DrawAction.buildDisplayList(list, ActionTarget.ConnectionType.NORMAL, src, dest, DrawAction.DrawMode.NORMAL);
  }
}
