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
package com.android.tools.idea.uibuilder.scene;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.target.DragDndTarget;
import com.android.tools.idea.uibuilder.scene.target.Target;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles drag'n drop on a Scene
 */
public class SceneDragHandler extends DragHandler {

  private SceneComponent myComponent;

  public SceneDragHandler(@NotNull ViewEditor editor,
                          @NotNull ViewGroupHandler handler,
                          @NotNull SceneComponent layout,
                          @NotNull List<SceneComponent> components, DragType type) {
    super(editor, handler, layout, components, type);
    if (components.size() == 1) {
      myComponent = components.get(0);
      Scene scene = ((ViewEditorImpl) editor).getSceneView().getScene();
      scene.setDnDComponent(myComponent);
    }
  }

  @Override
  public void start(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    super.start(x, y, modifiers);
    if (myComponent == null) {
      return;
    }
    Scene scene = ((ViewEditorImpl) editor).getSceneView().getScene();
    scene.needsRebuildList();
    ArrayList<Target> targets = myComponent.getTargets();
    @AndroidDpCoordinate int dx = x - myComponent.getDrawWidth() / 2;
    @AndroidDpCoordinate int dy = y - myComponent.getDrawHeight() / 2;
    for (Target target : targets) {
      if (target instanceof DragDndTarget) {
        target.mouseDown(scene.pxToDp(dx), scene.pxToDp(dy));
        break;
      }
    }
  }

  @Nullable
  @Override
  public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    String result = super.update(x, y, modifiers);
    if (myComponent == null) {
      return "undefined";
    }
    Scene scene = ((ViewEditorImpl) editor).getSceneView().getScene();
    @AndroidDpCoordinate int dx = x - myComponent.getDrawWidth() / 2;
    @AndroidDpCoordinate int dy = y - myComponent.getDrawHeight() / 2;
    myComponent.setPosition(dx, dy);
    if (myComponent != null) {
      ArrayList<Target> targets = myComponent.getTargets();
      for (int i = 0; i < targets.size(); i++) {
        if (targets.get(i) instanceof DragDndTarget) {
          DragDndTarget target = (DragDndTarget) targets.get(i);
          target.mouseDrag(dx, dy, target);
          break;
        }
      }
    }
    scene.checkRequestLayoutStatus();
    return result;
  }

  @Override
  public void cancel() {
    Scene scene = ((ViewEditorImpl) editor).getSceneView().getScene();
    scene.setDnDComponent(null);
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
    Scene scene = ((ViewEditorImpl) editor).getSceneView().getScene();
    if (myComponent != null) {
      NlComponent root = myComponent.getNlComponent().getRoot();
      root.ensureNamespace(SdkConstants.SHERPA_PREFIX, SdkConstants.AUTO_URI);
      if (myComponent != null) {
        ArrayList<Target> targets = myComponent.getTargets();
        @AndroidDpCoordinate int dx = scene.pxToDp(x) - myComponent.getDrawWidth() / 2;
        @AndroidDpCoordinate int dy = scene.pxToDp(y) - myComponent.getDrawHeight() / 2;
        for (Target target : targets) {
          if (target instanceof DragDndTarget) {
            ((DragDndTarget)target).mouseRelease(dx, dy, components.get(0).getNlComponent());
            break;
          }
        }
      }
    }
    insertComponents(-1, insertType);
    scene.setDnDComponent(null);
    scene.checkRequestLayoutStatus();
  }

  @Override
  public void paint(@NotNull NlGraphics graphics) {
    // Do nothing for now
  }

}
