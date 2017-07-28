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
package com.android.tools.idea.uibuilder.handlers.linear;

import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.linear.targets.LinearDragTarget;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.target.Target;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class LinearDragHandler extends DragHandler {

  private final SceneComponent myComponent;
  private final LinearDragTarget myDragTarget;
  private static final List<Target> ourEmptyTargetList = ImmutableList.of();

  public LinearDragHandler(@NotNull ViewEditor editor,
                           @NotNull LinearLayoutHandler handler,
                           @NotNull SceneComponent layout,
                           @NotNull List<NlComponent> components,
                           @NotNull DragType type) {
    super(editor, handler, layout, components, type);

    assert !components.isEmpty();
    NlComponent dragged = components.get(0);
    myComponent = new TemporarySceneComponent(layout.getScene(), components.get(0));
    myDragTarget = new LinearDragTarget(handler, type.equals(DragType.CREATE));
    myComponent.setSize(editor.pxToDp(NlComponentHelperKt.getW(dragged)), editor.pxToDp(NlComponentHelperKt.getH(dragged)), false);
    myComponent.setTargetProvider((sceneComponent, isParent) -> ImmutableList.of(myDragTarget), false);
    myComponent.setDrawState(SceneComponent.DrawState.DRAG);
    layout.addChild(myComponent);
  }

  @Override
  public void start(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    super.start(x, y, modifiers);
    myDragTarget.mouseDown(x, y);
  }

  @Override
  public void cancel() {
    Scene scene = ((ViewEditorImpl)editor).getSceneView().getScene();
    scene.removeComponent(myComponent);
    myDragTarget.cancel();
  }

  @Nullable
  @Override
  public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    String result = super.update(x, y, modifiers);
    @AndroidDpCoordinate int dx = x + startX - myComponent.getDrawWidth() / 2;
    @AndroidDpCoordinate int dy = y + startY - myComponent.getDrawHeight() / 2;
    myDragTarget.mouseDrag(dx, dy, ourEmptyTargetList);
    return result;
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
    Scene scene = ((ViewEditorImpl)editor).getSceneView().getScene();
    if (myComponent != null) {
      @AndroidDpCoordinate int dx = editor.pxToDp(x) - myComponent.getDrawWidth() / 2;
      @AndroidDpCoordinate int dy = editor.pxToDp(y) - myComponent.getDrawHeight() / 2;
      myDragTarget.mouseRelease(dx, dy, ourEmptyTargetList);
      scene.removeComponent(myComponent);
      if (!myDragTarget.isDragHandled()) {
        // If the target didn't handled the insertion, we delegate
        // the insertion to the drag handler
        super.commit(x, y, modifiers, insertType);
      }
      scene.checkRequestLayoutStatus();
    }
  }
}
