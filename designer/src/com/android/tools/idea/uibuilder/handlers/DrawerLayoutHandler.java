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
package com.android.tools.idea.uibuilder.handlers;

import com.android.AndroidXConstants;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.*;

public class DrawerLayoutHandler extends ViewGroupHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_CONTEXT,
      ATTR_OPEN_DRAWER,
      ATTR_FITS_SYSTEM_WINDOWS);
  }

  @NotNull
  @Override
  public List<Target> createChildTargets(@NotNull SceneComponent parentComponent, @NotNull SceneComponent childComponent) {
    if (AndroidXConstants.NAVIGATION_VIEW.isEquals(childComponent.getNlComponent().getTagName())) {
      NavigationViewSelectionTarget target = new NavigationViewSelectionTarget();
      target.setComponent(childComponent);
      return ImmutableList.of(target);
    }
    return Collections.emptyList();
  }

  private static class NavigationViewSelectionTarget extends BaseTarget {
    @Override
    public int getPreferenceLevel() {
      return 0;
    }

    @Override
    public boolean layout(@NotNull SceneContext context, int l, int t, int r, int b) {
      myLeft = myComponent.getDrawX();
      myRight = myComponent.getDrawX() + myComponent.getDrawWidth();
      myTop = myComponent.getDrawY();
      myBottom = myComponent.getDrawY() + myComponent.getDrawHeight();
      return false;
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {

    }

    @Nullable
    @Override
    public List<SceneComponent> newSelection() {
      return ImmutableList.of(myComponent);
    }

    @Override
    protected boolean isHittable() {
      return true;
    }
  }
}
