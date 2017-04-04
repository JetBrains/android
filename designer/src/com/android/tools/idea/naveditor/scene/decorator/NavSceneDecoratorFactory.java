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

import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecorator;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecoratorFactory;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates {@link SceneComponent}s from {@link NlComponent}s for the navigation editor.
 */
public class NavSceneDecoratorFactory extends SceneDecoratorFactory {
  private static final Map<String, Constructor<? extends SceneDecorator>> ourConstructorMap = new HashMap<>();

  static {
    try {
      ourConstructorMap.put(NavSceneManager.TAG_FRAGMENT, NavScreenDecorator.class.getConstructor());
      ourConstructorMap.put(NavSceneManager.TAG_NAVIGATION, NavigationDecorator.class.getConstructor());
    }
    catch (NoSuchMethodException e) {
      // shouldn't happen
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
