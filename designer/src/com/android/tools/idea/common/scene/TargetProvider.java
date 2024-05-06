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
package com.android.tools.idea.common.scene;

import com.android.tools.idea.common.scene.target.Target;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A mechanism for adding {@link Target}s to {@link SceneComponent}s.
 */
public interface TargetProvider {
  /**
   * Create the {@link Target}s for the given sceneComponent.
   * @param sceneComponent the sceneComponent which created {@link Target}s associate with
   * @return A list of {@link Target} which all associate to the given component.
   */
  @NotNull
  List<Target> createTargets(@NotNull SceneComponent sceneComponent);

  /**
   * Create the {@link Target}s for the child component.
   * @param parentComponent the parent of the given childComponent.
   * @param childComponent  the component which created {@link Target}s associate with
   * @return A list of {@link Target} which all associate to the given child component.
   */
  @NotNull
  default List<Target> createChildTargets(@NotNull SceneComponent parentComponent, @NotNull SceneComponent childComponent) {
    return Collections.emptyList();
  }

  /**
   * Returns whether the {@link SceneComponent} should have a drag target associated.
   */
  default boolean shouldAddCommonDragTarget(@NotNull SceneComponent component) {
    return false;
  }
}
