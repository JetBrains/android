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

import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawComponentBackground;
import com.android.tools.idea.common.scene.draw.DrawComponentFrame;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;

/**
 * The generic Scene Decorator
 */
public class SceneDecorator {
  private SceneFrameFactory myFrameFactory = (list, component, sceneContext) -> {
    Rectangle rect = new Rectangle();
    component.fillRect(rect); // get the rectangle from the component

    SceneComponent.DrawState mode = component.getDrawState();
    DrawComponentFrame.add(list, sceneContext, rect, mode.ordinal()); // add to the list
  };

  public void setFrameFactory(@NotNull SceneFrameFactory sceneFrameFactory) {
    myFrameFactory = sceneFrameFactory;
  }

  /**
   * The basic implementation of building a Display List
   * This should be called after layout
   * The Display list will contain a collection of commands that in screen space
   * It is also responsible to draw its targets (but not creating or placing targets
   * <ol>
   * <li>It adds a rectangle</li>
   * <li>adds targets</li>
   * <li>add children (If children they are wrapped in a clip)</li>
   * </ol>
   */
  public void buildList(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    if (sceneContext.showOnlySelection()) {
      addFrame(list, sceneContext, component);
      buildListChildren(list, time, sceneContext, component);
      return;
    }
    buildListComponent(list, time, sceneContext, component);
    buildListTargets(list, time, sceneContext, component);
    buildListChildren(list, time, sceneContext, component);
  }

  public void buildListComponent(@NotNull DisplayList list,
                                 long time,
                                 @NotNull SceneContext sceneContext,
                                 @NotNull SceneComponent component) {
    addBackground(list, sceneContext, component);
    addContent(list, time, sceneContext, component);
    addFrame(list, sceneContext, component);
  }

  protected void addContent(@NotNull DisplayList list,
                            long time,
                            @NotNull SceneContext sceneContext,
                            @NotNull SceneComponent component) {
    // Nothing here...
  }

  protected void addBackground(@NotNull DisplayList list,
                               @NotNull SceneContext sceneContext,
                               @NotNull SceneComponent component) {
    if (sceneContext.getColorSet().drawBackground()) {
      Rectangle rect = new Rectangle();
      component.fillRect(rect); // get the rectangle from the component
      SceneComponent.DrawState state = component.getDrawState();
      if (component.isToolLocked()) {
        state = SceneComponent.DrawState.SUBDUED;
      }
      DrawComponentBackground.add(list, sceneContext, rect, state.ordinal()); // add to the list
    }
  }

  protected void addFrame(@NotNull DisplayList list,
                          @NotNull SceneContext sceneContext,
                          @NotNull SceneComponent component) {
    myFrameFactory.addFrame(list, component, sceneContext);
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
    if (!children.isEmpty()) {
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
    component.getTargets().forEach(target -> target.render(list, sceneContext));
  }
}
