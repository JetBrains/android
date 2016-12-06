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
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  }

  @Nullable
  @Override
  public String update(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    String result = super.update(x, y, modifiers);
    Scene scene = ((ViewEditorImpl) editor).getScreenView().getScene();
    SceneComponent component = scene.getSceneComponent(myComponent);
    myComponent.x = x - myComponent.w / 2;
    myComponent.y = y - myComponent.h / 2;
    if (component != null) {
      scene.setAnimate(false);
      component.updateFrom(myComponent);
      scene.setAnimate(true);
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
      NlComponent component = components.get(0);
      NlComponent root = component.getRoot();
      root.ensureNamespace(SdkConstants.SHERPA_PREFIX, SdkConstants.AUTO_URI);
      AttributesTransaction attributes = component.startAttributeTransaction();
      SceneComponent parent = scene.getSceneComponent(layout);
      int ax = scene.pxToDp(x - component.w / 2) - parent.getDrawX();
      int ay = scene.pxToDp(y - component.h / 2) - parent.getDrawY();
      String valueX = String.format(SdkConstants.VALUE_N_DP, ax);
      String valueY = String.format(SdkConstants.VALUE_N_DP, ay);
      attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, valueX);
      attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, valueY);
      attributes.commit();
    }
    scene.setDnDComponent(null);
    insertComponents(-1, insertType);
  }

  @Override
  public void paint(@NotNull NlGraphics graphics) {
    // Do nothing for now
  }
}
