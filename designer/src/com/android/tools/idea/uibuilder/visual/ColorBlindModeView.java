/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual;

import static com.android.tools.idea.flags.StudioFlags.NELE_RENDER_DIAGNOSTICS;

import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.BorderLayer;
import com.android.tools.idea.uibuilder.surface.DiagnosticsLayer;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * View for drawing color blind modes.
 */
public class ColorBlindModeView extends ScreenView {

  public ColorBlindModeView(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
    super(surface, manager);
  }

  @NotNull
  @Override
  protected ImmutableList<Layer> createLayers() {

    ImmutableList.Builder<Layer> builder = ImmutableList.builder();

    // Always has border in visualization tool.
    builder.add(new BorderLayer(this));
    ColorBlindMode mode = getMode();
    if (mode != null) {
      builder.add(new ColorBlindModeScreenViewLayer(this, mode));
    } else {
      // ERROR - at least show the original.
      builder.add(new ScreenViewLayer(this));
    }

    if (NELE_RENDER_DIAGNOSTICS.get()) {
      builder.add(new DiagnosticsLayer(getSurface()));
    }
    return builder.build();
  }

  @Override
  public boolean hasBorderLayer() {
    return false;
  }

  /**
   * Returns appropriate mode based on model display name.
   */
  @Nullable
  private ColorBlindMode getMode() {
    NlModel model = getSceneManager().getModel();

    for (ColorBlindMode mode : ColorBlindMode.values()) {
      if (mode.getDisplayName().equals(model.getModelDisplayName())) {
        return mode;
      }
    }
    return null;
  }
}
