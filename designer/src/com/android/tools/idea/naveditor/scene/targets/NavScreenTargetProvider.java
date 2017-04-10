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

import org.jetbrains.android.dom.navigation.NavigationSchema;
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.NavSceneLayoutAlgorithm;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.TargetProvider;
import com.android.tools.idea.uibuilder.scene.target.Target;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link TargetProvider} for navigation screens.
 *
 * Notably, adds the actions going from this screen to others.
 */
public class NavScreenTargetProvider implements TargetProvider {
  private final NavSceneLayoutAlgorithm myLayoutAlgorithm;
  private final NavigationSchema mySchema;

  public NavScreenTargetProvider(@NotNull NavSceneLayoutAlgorithm algorithm, @NotNull NavigationSchema schema) {
    myLayoutAlgorithm = algorithm;
    mySchema = schema;
  }

  @NotNull
  @Override
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent, boolean isParent) {
    List<Target> result = new ArrayList<>();
    for (NlComponent nlChild : sceneComponent.getNlComponent().getChildren()) {
      if (nlChild.getTagName().equals(NavigationSchema.TAG_ACTION)) {
        result.add(new ActionTarget(sceneComponent, nlChild));
      }
    }
    if (mySchema.getDestinationType(sceneComponent.getNlComponent().getTagName()) == NavigationSchema.DestinationType.FRAGMENT) {
      if (myLayoutAlgorithm instanceof ManualLayoutAlgorithm) {
        result.add(new ScreenDragTarget(sceneComponent, (ManualLayoutAlgorithm)myLayoutAlgorithm));
      }
    }
    return result;
  }
}
