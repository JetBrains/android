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
package com.android.tools.idea.uibuilder.scene.decorator;


import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.draw.DrawComponent;
import com.android.tools.idea.uibuilder.scene.target.Target;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The generic Scene Decorator
 */
public class SceneDecorator {
  static SceneDecorator basicDecorator = new SceneDecorator();
  static Map<String, Constructor<? extends SceneDecorator>> ourConstructorMap = new HashMap<>();
  static Map<String, SceneDecorator> ourSceneMap = new HashMap<>();

  static {
    try {
      ourConstructorMap.put("android.support.constraint.ConstraintLayout", ConstraintLayoutDecorator.class.getConstructor());
    }
    catch (NoSuchMethodException e) {
    }
  }

  /**
   * Simple factory for providing decorators
   *
   * @param component
   * @return
   */
  public static SceneDecorator get(NlComponent component) {
    String tag = component.getTagName();
    if (ourConstructorMap.containsKey(tag)) {
      if (!ourSceneMap.containsKey(tag)) {
        try {
          ourSceneMap.put(tag, ourConstructorMap.get(tag).newInstance());
        }
        catch (Exception e) {
          ourSceneMap.put(tag, basicDecorator);
        }
      }
      return ourSceneMap.get(tag);
    }
    return basicDecorator;
  }

  /**
   * The basic implementation of building a Display List
   * This should be called after layout
   * The Display list will contain a collection of commands that in screen space
   * It is also responsible to draw its targets (but not creating or placing targets
   *
   * @param list
   * @param time
   * @param screenView
   * @param component
   */
  public void buildList(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    Rectangle rect = new Rectangle();
    Color color = Color.CYAN;
    if (component.getDrawState() == SceneComponent.DrawState.HOVER) {
      color = Color.yellow;
    }
    component.fillRect(rect); // get the rectangle from the component
    list.addRect(sceneContext, rect, color); // add to the list

    ArrayList<Target> targets = component.getTargets();

    int num = targets.size();
    for (int i = 0; i < num; i++) {
      targets.get(i).render(list, sceneContext);
    }
  }
}
