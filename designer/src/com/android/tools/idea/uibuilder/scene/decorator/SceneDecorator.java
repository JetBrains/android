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


import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
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
      ourConstructorMap.put(SdkConstants.CLASS_CONSTRAINT_LAYOUT, ConstraintLayoutDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.PROGRESS_BAR, ProgressBarDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.BUTTON, ButtonDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.TEXT_VIEW, TextViewDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.IMAGE_VIEW, ImageViewDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.CHECK_BOX, CheckBoxDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.SEEK_BAR, SeekBarDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.SWITCH, SwitchDecorator.class.getConstructor());
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
   * <ol>
   *   <li>It adds a rectangle</li>
   *   <li>adds targets</li>
   *   <li>add children (If children they are wrapped in a clip)</li>
   * </ol>
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
    buildListComponent(list,time, sceneContext,component);
    buildListTargets(list,time, sceneContext,component);
    buildListChildren(list,time, sceneContext,component);
  }

  public void buildListComponent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
     // default empty for now
    // TODO add draw the component
  }

  /**
   * This is responsible for setting the clip and building the list for this component's children
   *
   * @param list
   * @param time
   * @param sceneContext
   * @param component
   */
  protected void buildListChildren(@NotNull DisplayList list,
                                long time,
                                @NotNull SceneContext sceneContext,
                                @NotNull SceneComponent component) {
    ArrayList<SceneComponent> children = component.getChildren();
    if (children.size() > 0) {
      Rectangle rect = new Rectangle();
      component.fillRect(rect);
      DisplayList.UNClip unClip = list.addClip(sceneContext, rect);
      for (SceneComponent child : children) {
        child.buildDisplayList(time, list, sceneContext);
      }
      list.add(unClip);
    }
  }

  /**
   * This is responsible for building the targets of this component
   *
   * @param list
   * @param time
   * @param sceneContext
   * @param component
   */
  protected void buildListTargets(@NotNull DisplayList list,
                               long time,
                               @NotNull SceneContext sceneContext,
                               @NotNull SceneComponent component) {
    ArrayList<Target> targets = component.getTargets();
    for (Target target : targets) {
      target.render(list, sceneContext);
    }
  }

}
