/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.layout;

import com.android.tools.idea.common.scene.SceneComponent;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * {@link NavSceneLayoutAlgorithm} that lays out one component at a time
 */
abstract public class SingleComponentLayoutAlgorithm implements NavSceneLayoutAlgorithm {
  @Override
  @NotNull
  public List<SceneComponent> layout(@NotNull List<SceneComponent> components) {
    return components.stream().filter(c -> !doLayout(c)).collect(Collectors.toList());
  }

  abstract protected boolean doLayout(@NotNull SceneComponent component);
}