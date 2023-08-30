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

import com.android.SdkConstants;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.scene.Placeholder;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.handlers.common.CommonDragHandler;
import com.android.tools.idea.common.scene.SceneInteraction;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
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
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return new CommonDragHandler(editor, this, layout, components, type);
  }

  @Override
  @Nullable
  public Interaction createInteraction(@NotNull ScreenView screenView,
                                       @SwingCoordinate int x,
                                       @SwingCoordinate int y,
                                       @NotNull NlComponent component) {
    return new SceneInteraction(screenView);
  }

  @Override
  public boolean handlesPainting() {
    return true;
  }

  @Override
  public void onChildRemoved(@NotNull NlComponent layout,
                             @NotNull NlComponent newChild,
                             @NotNull InsertType insertType) {
    newChild.removeAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_X);
    newChild.removeAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_Y);
  }

  @NotNull
  @Override
  public List<Target> createChildTargets(@NotNull SceneComponent parentComponent, @NotNull SceneComponent childComponent) {
    return ImmutableList.of(
      new AbsoluteResizeTarget(ResizeBaseTarget.Type.LEFT),
      new AbsoluteResizeTarget(ResizeBaseTarget.Type.LEFT_TOP),
      new AbsoluteResizeTarget(ResizeBaseTarget.Type.TOP),
      new AbsoluteResizeTarget(ResizeBaseTarget.Type.RIGHT_TOP),
      new AbsoluteResizeTarget(ResizeBaseTarget.Type.RIGHT),
      new AbsoluteResizeTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM),
      new AbsoluteResizeTarget(ResizeBaseTarget.Type.BOTTOM),
      new AbsoluteResizeTarget(ResizeBaseTarget.Type.LEFT_BOTTOM)
    );
  }

  @Override
  public List<Placeholder> getPlaceholders(@NotNull SceneComponent component, @NotNull List<SceneComponent> draggedComponents) {
    return ImmutableList.of(new AbsolutePlaceholder(component));
  }

  @Override
  public boolean shouldAddCommonDragTarget(@NotNull SceneComponent component) {
    return true;
  }
}
