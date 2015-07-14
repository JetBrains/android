/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.model.NlModel;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Responsible for painting a screen view */
public class ScreenViewLayer extends Layer {
  private final ScreenView myScreenView;

  public ScreenViewLayer(@NonNull ScreenView screenView) {
    myScreenView = screenView;
  }

  @Override
  public void paint(@NonNull Graphics2D gc) {
    NlModel myModel = myScreenView.getModel();
    RenderResult renderResult = myModel.getRenderResult();
    if (renderResult != null && renderResult.getImage() != null) {
      BufferedImage originalImage = renderResult.getImage().getOriginalImage();
      double scale = myScreenView.getScale();
      BufferedImage scaled = ImageUtils.scale(originalImage, scale, scale);
      gc.drawImage(scaled, myScreenView.getX(), myScreenView.getY(), null); // TODO: Retina
    }
  }
}
