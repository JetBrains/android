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
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.target.DragDndTarget;
import com.android.tools.idea.uibuilder.scene.target.DragTarget;
import com.android.tools.idea.uibuilder.scene.target.Target;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles drag'n drop on a Scene
 */
public class SceneDragHandler extends DragHandler {

  private NlComponent myComponent;

  public SceneDragHandler(@NotNull ViewEditor editor,
                          @NotNull ConstraintLayoutHandler constraintLayoutHandler,
                          @NotNull NlComponent layout,
                          @NotNull List<NlComponent> components, DragType type) {
    super(editor, constraintLayoutHandler, layout, components, type);
    if (components.size() == 1) {
      myComponent = components.get(0);
      Scene scene = ((ViewEditorImpl) editor).getScreenView().getScene();
      scene.setDnDComponent(myComponent);
    }
  }

  @Override
  public void start(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    super.start(x, y, modifiers);
    Scene scene = ((ViewEditorImpl) editor).getScreenView().getScene();
    SceneComponent component = scene.getSceneComponent(myComponent);
    ArrayList<Target> targets = component.getTargets();
    int dx = x - myComponent.w / 2;
    int dy = y - myComponent.h / 2;
    for (int i = 0; i < targets.size(); i++) {
      if (targets.get(i) instanceof DragTarget) {
        DragTarget target = (DragTarget) targets.get(i);
        target.mouseDown(scene.pxToDp(dx), scene.pxToDp(dy));
        break;
      }
    }
  }

  @Nullable
  @Override
  public String update(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    String result = super.update(x, y, modifiers);
    Scene scene = ((ViewEditorImpl) editor).getScreenView().getScene();
    SceneComponent component = scene.getSceneComponent(myComponent);
    int dx = x - myComponent.w / 2;
    int dy = y - myComponent.h / 2;
    myComponent.x = dx;
    myComponent.y = dy;
    if (component != null) {
      ArrayList<Target> targets = component.getTargets();
      for (int i = 0; i < targets.size(); i++) {
        if (targets.get(i) instanceof DragTarget) {
          DragTarget target = (DragTarget) targets.get(i);
          target.mouseDrag(scene.pxToDp(dx), scene.pxToDp(dy), null);
          break;
        }
      }
    }
    scene.needsRebuildList();
    return result;
  }

  @Override
  public void cancel() {
    Scene scene = ((ViewEditorImpl) editor).getScreenView().getScene();
    scene.setDnDComponent(null);
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
    Scene scene = ((ViewEditorImpl) editor).getScreenView().getScene();
    if (myComponent != null) {
      NlComponent nlComponent = components.get(0);
      NlComponent root = nlComponent.getRoot();
      root.ensureNamespace(SdkConstants.SHERPA_PREFIX, SdkConstants.AUTO_URI);
      SceneComponent component = scene.getSceneComponent(myComponent);
      if (component != null) {
        ArrayList<Target> targets = component.getTargets();
        int dx = x - myComponent.w / 2;
        int dy = y - myComponent.h / 2;
        for (int i = 0; i < targets.size(); i++) {
          if (targets.get(i) instanceof DragTarget) {
            DragDndTarget target = (DragDndTarget) targets.get(i);
            target.mouseRelease(scene.pxToDp(dx), scene.pxToDp(dy), nlComponent);
            break;
          }
        }
      }
    }
    insertComponents(-1, insertType);
    scene.setDnDComponent(null);
  }

  @Override
  public void paint(@NotNull NlGraphics graphics) {
    // Do nothing for now
  }

}
