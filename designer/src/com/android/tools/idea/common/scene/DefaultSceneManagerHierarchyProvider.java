/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.scene;

import com.android.tools.idea.common.model.NlComponent;
import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Default {@link SceneManager.SceneComponentHierarchyProvider}. It will create one {@link SceneComponent} per
 * every given {@link NlComponent}. It will move the existing components in the {@link SceneManager} to their correct
 * position if they already existed.
 */
public class DefaultSceneManagerHierarchyProvider implements SceneManager.SceneComponentHierarchyProvider {
  @Override
  @NotNull
  public List<SceneComponent> createHierarchy(@NotNull SceneManager manager, @NotNull NlComponent component) {
    SceneComponent sceneComponent = manager.getScene().getSceneComponent(component);
    if (sceneComponent == null) {
      sceneComponent = new SceneComponent(manager.getScene(), component, manager.getHitProvider(component));
    }
    Set<SceneComponent> oldChildren = new HashSet<>(sceneComponent.getChildren());
    for (NlComponent nlChild : component.getChildren()) {
      List<SceneComponent> children = createHierarchy(manager, nlChild);
      oldChildren.removeAll(children);
      for (SceneComponent child : children) {
        // Even the parent of child is the same, re-add it to make the order same as NlComponent.
        child.removeFromParent();
        sceneComponent.addChild(child);
      }
    }
    for (SceneComponent child : oldChildren) {
      if (child instanceof TemporarySceneComponent && child.getParent() == sceneComponent) {
        // ignore TemporarySceneComponent since its associated NlComponent has not been added to the hierarchy.
        continue;
      }
      if (child.getParent() == sceneComponent) {
        child.removeFromParent();
      }
    }
    return ImmutableList.of(sceneComponent);
  }

  @Override
  public void syncFromNlComponent(@NotNull SceneComponent sceneComponent) {}
}
