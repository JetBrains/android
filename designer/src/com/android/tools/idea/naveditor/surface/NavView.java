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
package com.android.tools.idea.naveditor.surface;

import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.SceneView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * View of a navigation editor {@link Scene}, as part of a {@link NavDesignSurface}.
 */
public class NavView extends SceneView {
  private NavDesignSurface mySurface;

  public NavView(@NotNull NavDesignSurface surface, @NotNull NlModel model) {
    super(surface, model);
    mySurface = surface;
  }

  @NotNull
  @Override
  public Dimension getPreferredSize(@Nullable Dimension dimension) {
    return mySurface.getSize(dimension);
  }

  @NotNull
  @Override
  public Color getBgColor() {
    return Color.WHITE;
  }
}
