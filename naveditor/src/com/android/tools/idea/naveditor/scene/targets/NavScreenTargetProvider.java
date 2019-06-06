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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.TargetProvider;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link TargetProvider} for navigation screens.
 *
 * Notably, adds the actions going from this screen to others.
 */
public class NavScreenTargetProvider implements TargetProvider {

  @NotNull
  @Override
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    List<Target> result = new ArrayList<>();
    SceneComponent parent = sceneComponent.getParent();
    NlComponent nlComponent = sceneComponent.getNlComponent();

    if (!NavComponentHelperKt.isDestination(nlComponent) || parent == null) {
      return result;
    }

    result.add(new ScreenDragTarget(sceneComponent));
    if (NavComponentHelperKt.getSupportsActions(nlComponent)) {
      result.add(new ActionHandleTarget(sceneComponent));
    }

    return result;
  }
}
