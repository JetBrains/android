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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.BlueprintColorSet;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class BlueprintView extends ScreenView {
  private final ColorSet myColorSet = new BlueprintColorSet();

  public BlueprintView(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
    super(surface, manager);
  }

  @NotNull
  @Override
  protected ImmutableList<Layer> createLayers() {
    ImmutableList.Builder<Layer> builder = ImmutableList.builder();
    if (myShowBorder) {
      builder.add(new BorderLayer(this));
    }
    builder.add(new MockupLayer(this));
    if (!myIsSecondary) {
      builder.add(new CanvasResizeLayer(getSurface(), this));
    }
    builder.add(new SceneLayer(getSurface(), this, true));
    return builder.build();
  }

  @NotNull
  @Override
  public ColorSet getColorSet() {
    return myColorSet;
  }
}
