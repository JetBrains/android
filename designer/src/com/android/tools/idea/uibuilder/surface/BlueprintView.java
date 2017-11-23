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

import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.BlueprintColorSet;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class BlueprintView extends ScreenView {
  private final ColorSet myColorSet = new BlueprintColorSet();

  public BlueprintView(@NotNull NlDesignSurface surface, @NotNull NlModel model) {
    super(surface, model);
  }

  @NotNull
  @Override
  protected ImmutableList<Layer> createLayers() {
    ImmutableList.Builder<Layer> builder = ImmutableList.builder();
    builder.add(
      new MyBottomLayer(this),
      new SelectionLayer(this),
      new MockupLayer(this));
    if (!myIsSecondary) {
      builder.add(new CanvasResizeLayer((NlDesignSurface) mySurface, this));
    }
    builder.add(new SceneLayer(mySurface, this, true));
    return builder.build();
  }

  @NotNull
  @Override
  public ColorSet getColorSet() {
    return myColorSet;
  }

  @Override
  public void paintBorder(@NotNull Graphics2D g) {
    ScreenView.BorderPainter.paint(g, this);
  }

  private static class MyBottomLayer extends Layer {

    private final ScreenViewBase myScreenView;

    public MyBottomLayer(@NotNull ScreenViewBase screenView) {
      myScreenView = screenView;
    }

    @Override
    public void paint(@NotNull Graphics2D g2d) {
      Shape screenShape = myScreenView.getScreenShape();
      if (screenShape != null) {
        g2d.draw(screenShape);
        return;
      }
      myScreenView.paintBorder(g2d);
    }
  }
}
