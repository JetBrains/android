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
package com.android.tools.idea.uibuilder.handlers.absolute;

import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneDragHandler;
import com.android.tools.idea.uibuilder.scene.SceneInteraction;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.android.tools.idea.uibuilder.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Handler for {@link com.android.SdkConstants#ABSOLUTE_LAYOUT}
 */
public class AbsoluteLayoutHandler extends ViewGroupHandler {
  @Override
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull NlComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return new SceneDragHandler(editor, this, layout, components, type);
  }

  @Override
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent component) {
    return new SceneInteraction(screenView);
  }

  @Override
  public void addTargets(@NotNull SceneComponent component, boolean isParent) {
    if (!isParent) {
      component.addTarget(new AbsoluteDragTarget());
      component.addTarget(new AbsoluteResizeTarget(ResizeBaseTarget.Type.LEFT));
      component.addTarget(new AbsoluteResizeTarget(ResizeBaseTarget.Type.LEFT_TOP));
      component.addTarget(new AbsoluteResizeTarget(ResizeBaseTarget.Type.TOP));
      component.addTarget(new AbsoluteResizeTarget(ResizeBaseTarget.Type.RIGHT_TOP));
      component.addTarget(new AbsoluteResizeTarget(ResizeBaseTarget.Type.RIGHT));
      component.addTarget(new AbsoluteResizeTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM));
      component.addTarget(new AbsoluteResizeTarget(ResizeBaseTarget.Type.BOTTOM));
      component.addTarget(new AbsoluteResizeTarget(ResizeBaseTarget.Type.LEFT_BOTTOM));
    }
  }
}
