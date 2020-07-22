/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneView;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;

/**
 * The Overlay Layer to be displayed on top of the layout preview
 */
public class OverlayLayer extends Layer {
  private final SceneView mySceneView;
  private Dimension myScreenViewSize = new Dimension();

  /**
   * Creates the OverlayLayer
   *
   * @param sceneView
   */
  public OverlayLayer(SceneView sceneView) {
    //TODO: add check for placeholder & generate it according to screen size
    mySceneView = sceneView;
  }

  @Override
  public void paint(@NotNull Graphics2D g) {
    if (mySceneView.getSurface().getOverlayConfiguration().getOverlayVisibility()) {
      BufferedImage image = mySceneView.getSurface().getOverlayConfiguration().getOverlayImage();
      float alpha = mySceneView.getSurface().getOverlayConfiguration().getOverlayAlpha();

      if (image == null) {
        return;
      }

      myScreenViewSize = mySceneView.getScaledContentSize(myScreenViewSize);

      g.setComposite(AlphaComposite.SrcOver.derive(alpha));
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(image, mySceneView.getX(), mySceneView.getY(), myScreenViewSize.width, myScreenViewSize.height, null);
    }
  }
}
