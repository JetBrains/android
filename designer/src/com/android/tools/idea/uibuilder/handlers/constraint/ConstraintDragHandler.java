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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.SdkConstants;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintDragDndTarget;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Handles drag'n drop on a Scene
 */
public class ConstraintDragHandler extends DragHandler {

  @Nullable protected SceneComponent myComponent;

  public ConstraintDragHandler(@NotNull ViewEditor editor,
                               @NotNull ViewGroupHandler handler,
                               @NotNull SceneComponent layout,
                               @NotNull List<NlComponent> components, DragType type) {
    super(editor, handler, layout, components, type);
    if (components.size() == 1) {
      NlComponent component = components.get(0);
      myComponent = new TemporarySceneComponent(layout.getScene(), component);
      myComponent.setSize(editor.pxToDp(NlComponentHelperKt.getW(component)), editor.pxToDp(NlComponentHelperKt.getH(component)), false);
      if (component.getTagName().equals(SdkConstants.CLASS_CONSTRAINT_LAYOUT_GUIDELINE.defaultName())) {
        if (SdkConstants.VALUE_VERTICAL.equals(component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION))) {
          myComponent.setSize(2, 200, false);
        } else {
          myComponent.setSize(200, 2,false);
        }
      }
      myComponent.setTargetProvider(sceneComponent -> ImmutableList.of(new ConstraintDragDndTarget()));
      myComponent.setDrawState(SceneComponent.DrawState.DRAG);
      layout.addChild(myComponent);
    }
  }

  @Override
  public void start(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    super.start(x, y, modifiers);
    if (myComponent == null) {
      return;
    }
    Scene scene = editor.getScene();
    scene.needsRebuildList();
    @AndroidDpCoordinate int dx = x - myComponent.getDrawWidth() / 2;
    @AndroidDpCoordinate int dy = y - myComponent.getDrawHeight() / 2;
    for (Target target : myComponent.getTargets()) {
      if (target instanceof ConstraintDragDndTarget) {
        target.mouseDown(dx, dy);
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
    Scene scene = editor.getScene();
    @AndroidDpCoordinate int dx = x - myComponent.getDrawWidth() / 2;
    @AndroidDpCoordinate int dy = y - myComponent.getDrawHeight() / 2;
    myComponent.setPosition(dx, dy);
    List<Target> targets = myComponent.getTargets();
    for (int i = 0; i < targets.size(); i++) {
      if (targets.get(i) instanceof ConstraintDragDndTarget) {
        ConstraintDragDndTarget target = (ConstraintDragDndTarget)targets.get(i);
        target.mouseDrag(dx, dy, targets);
        break;
      }
    }
    scene.checkRequestLayoutStatus();
    return result;
  }

  @Override
  public void cancel() {
    Scene scene = editor.getScene();
    if (myComponent != null) {
      scene.removeComponent(myComponent);
    }
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
    Scene scene = editor.getScene();
    if (myComponent != null) {
      NlComponent root = myComponent.getNlComponent().getRoot();
      NlWriteCommandAction.run(root, "Add App Namespace", () -> root.ensureNamespace(SdkConstants.SHERPA_PREFIX, SdkConstants.AUTO_URI));

      @AndroidDpCoordinate int dx = editor.pxToDp(x) - myComponent.getDrawWidth() / 2;
      @AndroidDpCoordinate int dy = editor.pxToDp(y) - myComponent.getDrawHeight() / 2;
      for (Target target : myComponent.getTargets()) {
        if (target instanceof ConstraintDragDndTarget) {
          ((ConstraintDragDndTarget)target).mouseRelease(dx, dy, components.get(0));
          break;
        }
      }
    }
    editor.insertChildren(layout.getNlComponent(), components, -1, insertType);
    scene.removeComponent(myComponent);
    scene.checkRequestLayoutStatus();
  }
}
