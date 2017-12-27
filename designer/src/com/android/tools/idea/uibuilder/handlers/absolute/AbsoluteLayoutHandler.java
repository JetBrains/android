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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintDragHandler;
import com.android.tools.idea.common.scene.SceneInteraction;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for {@link com.android.SdkConstants#ABSOLUTE_LAYOUT}
 */
public class AbsoluteLayoutHandler extends ViewGroupHandler {
  @Override
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return new ConstraintDragHandler(editor, this, layout, components, type);
  }

  @Override
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent component) {
    return new SceneInteraction(screenView);
  }

  @Override
  public boolean handlesPainting() {
    return true;
  }

  @Override
  @NotNull
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent, boolean isParent) {
    List<Target> result = new ArrayList<>();
    if (!isParent) {
      result.add(new AbsoluteDragTarget());
      result.add(new AbsoluteResizeTarget(ResizeBaseTarget.Type.LEFT));
      result.add(new AbsoluteResizeTarget(ResizeBaseTarget.Type.LEFT_TOP));
      result.add(new AbsoluteResizeTarget(ResizeBaseTarget.Type.TOP));
      result.add(new AbsoluteResizeTarget(ResizeBaseTarget.Type.RIGHT_TOP));
      result.add(new AbsoluteResizeTarget(ResizeBaseTarget.Type.RIGHT));
      result.add(new AbsoluteResizeTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM));
      result.add(new AbsoluteResizeTarget(ResizeBaseTarget.Type.BOTTOM));
      result.add(new AbsoluteResizeTarget(ResizeBaseTarget.Type.LEFT_BOTTOM));
    }
    return result;
  }
}
