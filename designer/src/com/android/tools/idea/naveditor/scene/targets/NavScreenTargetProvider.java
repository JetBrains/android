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

import com.android.SdkConstants;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.NavSceneLayoutAlgorithm;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.TargetProvider;
import com.android.tools.idea.common.scene.target.Target;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_START_DESTINATION;

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
    SceneComponent parent = sceneComponent.getParent();
    if (mySchema.getDestinationType(sceneComponent.getNlComponent().getTagName()) == null || parent == null) {
      return result;
    }

    Map<String, SceneComponent> groupMap = new HashMap<>();
    for (SceneComponent sibling : parent.getChildren()) {
      if (sibling == sceneComponent) {
        continue;
      }
      sibling.flatten().forEach(component -> groupMap.put(component.getId(), sibling));
    }
    sceneComponent.getNlComponent().flatten()
      .filter(component -> component.getTagName().equals(NavigationSchema.TAG_ACTION))
      .forEach(nlChild -> {
        String destinationId = NlComponent.stripId(nlChild.getAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION));
        SceneComponent destination = groupMap.get(destinationId);
        if (destination != null) {
          result.add(new ActionTarget(sceneComponent, destination, nlChild));
        }
      });
    if (myLayoutAlgorithm instanceof ManualLayoutAlgorithm) {
      result.add(new ScreenDragTarget(sceneComponent, (ManualLayoutAlgorithm)myLayoutAlgorithm));
    }
    result.add(new ActionHandleTarget(sceneComponent));
    result.add(new ScreenLabelTarget(sceneComponent));
    NlComponent parentNlComponent = parent.getNlComponent();
    String startDestination = parentNlComponent.getAttribute(SdkConstants.AUTO_URI, ATTR_START_DESTINATION);
    startDestination = NlComponent.stripId(startDestination);
    if (startDestination != null && startDestination.equals(sceneComponent.getId())) {
      result.add(new StartDestinationTarget(sceneComponent));
    }
    return result;
  }
}
