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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Responsible for painting a screen view */
public class ScreenViewLayer extends Layer {
  private final ScreenView myScreenView;
  /** The source image we scaled from */
  @Nullable private BufferedImage myImage;
  /** Cached scaled image */
  @Nullable private BufferedImage myScaledImage;
  /** The scale at which we cached the scaled image  */
  private double myCachedScale;

  public ScreenViewLayer(@NotNull ScreenView screenView) {
    myScreenView = screenView;
  }

  @Override
  public boolean paint(@NotNull Graphics2D g) {
    NlModel myModel = myScreenView.getModel();
    RenderResult renderResult = myModel.getRenderResult();
    if (renderResult != null && renderResult.getImage() != null) {
      BufferedImage originalImage = renderResult.getImage().getOriginalImage();
      if (UIUtil.isRetina() && paintHiDpi(g, originalImage)) {
        return false;
      }
      paintLoDpi(g, originalImage);
    }
    return false;
  }

  public void paintLoDpi(@NotNull Graphics g, @NotNull BufferedImage originalImage) {
    double scale = myScreenView.getScale();
    int x = myScreenView.getX();
    int y = myScreenView.getY();

    if (myScaledImage == null || myImage != originalImage || myCachedScale != scale) {
      myImage = originalImage;
      myCachedScale = scale;
      myScaledImage = ImageUtils.scale(originalImage, scale, scale);
    }
    g.drawImage(myScaledImage, x, y, null);
  }

  public boolean paintHiDpi(@NotNull Graphics g, @NotNull BufferedImage originalImage) {
    if (!ImageUtils.supportsRetina()) {
      return false;
    }

    /* No longer need to support custom tweaking of this
    AndroidEditorSettings.GlobalState settings = AndroidEditorSettings.getInstance().getGlobalState();
    if (!settings.isRetina()) {
      return false;
    }
    */

    double scale = myScreenView.getScale();
    if (scale > 1.01) {
      // When scaling up significantly, use normal painting logic; no need to pixel double into a
      // double res image buffer!
      return false;
    }
    int x = myScreenView.getX();
    int y = myScreenView.getY();

    if (myScaledImage == null || myImage != originalImage || myCachedScale != scale) {
      myImage = originalImage;
      myCachedScale = scale;

      BufferedImage image = myImage;

      /* TODO: Not supporting wear yet
      Device device = myScreenView.getModel().getConfiguration().getDevice();
      if (HardwareConfigHelper.isRound(device)) {
        int imageType = image.getType();
        if (imageType == BufferedImage.TYPE_CUSTOM) {
          imageType = BufferedImage.TYPE_INT_ARGB;
        }
        @SuppressWarnings("UndesirableClassUsage") // layoutlib doesn't create retina images
          BufferedImage clipped = new BufferedImage(image.getWidth(), image.getHeight(), imageType);
        Graphics2D g2 = clipped.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        //noinspection UseJBColor
        g2.setColor(new Color(0, true));
        g2.fillRect(0, 0, clipped.getWidth(), clipped.getHeight());
        paintClipped(g2, image, device, 0, 0, true);
        g2.dispose();
        image = clipped;
      }
      */

      // No scaling if very close to 1.0
      double retinaScale = 2 * scale;
      if (Math.abs(scale - 1.0) > 0.01) {
        image = ImageUtils.scale(image, retinaScale, retinaScale);
      }

      myScaledImage = ImageUtils.convertToRetina(image);
      if (myScaledImage == null) {
        return false;
      }
    }

    //noinspection ConstantConditions
    UIUtil.drawImage(g, myScaledImage, x, y, null);
    return true;
  }
}
