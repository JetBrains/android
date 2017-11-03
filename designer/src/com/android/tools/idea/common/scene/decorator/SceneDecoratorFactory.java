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
package com.android.tools.idea.common.scene.decorator;

import com.android.tools.idea.common.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class SceneDecoratorFactory {
  static Map<String, SceneDecorator> ourSceneMap = new HashMap<>();
  protected static final SceneDecorator BASIC_DECORATOR = new SceneDecorator();

  @NotNull
  public abstract SceneDecorator get(@NotNull NlComponent component);

  @NotNull
  protected Optional<SceneDecorator> get(@NotNull String key) {
    if (getConstructorMap().containsKey(key)) {
      if (!ourSceneMap.containsKey(key)) {
        try {
          ourSceneMap.put(key, getConstructorMap().get(key).newInstance());
        }
        catch (Exception e) {
          ourSceneMap.put(key, null);
        }
      }
      return Optional.of(ourSceneMap.get(key));
    }
    return Optional.empty();
  }

  @NotNull
  protected abstract Map<String, Constructor<? extends SceneDecorator>> getConstructorMap();
}
