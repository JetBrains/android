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
package com.android.tools.idea.naveditor.scene.decorator;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates {@link SceneComponent}s from {@link NlComponent}s for the navigation editor.
 */
public class NavSceneDecoratorFactory extends SceneDecoratorFactory {
  private static final Map<String, Constructor<? extends SceneDecorator>> ourConstructorMap = new HashMap<>();

  public NavSceneDecoratorFactory(@NotNull NavigationSchema schema) {
    for (NavigationSchema.DestinationType type : NavigationSchema.DestinationType.values()) {
      for (String tag : schema.getDestinationClassByTagMap(type).keySet()) {
        try {
          Constructor<? extends SceneDecorator> constructor = type == NavigationSchema.DestinationType.NAVIGATION
                                       ? NavigationDecorator.class.getConstructor()
                                       : NavScreenDecorator.class.getConstructor();
          ourConstructorMap.put(tag, constructor);
        }
        catch (NoSuchMethodException e) {
          // shouldn't happen, ignore
        }
      }
    }
  }

  @NotNull
  @Override
  public SceneDecorator get(@NotNull NlComponent component) {
    return get(component.getTagName()).orElse(BASIC_DECORATOR);
  }

  @NotNull
  @Override
  protected Map<String, Constructor<? extends SceneDecorator>> getConstructorMap() {
    return ourConstructorMap;
  }
}
