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

import com.android.tools.idea.uibuilder.model.ModelListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/** Responsible for painting a screen view */
public class ScreenViewLayer extends Layer {
  private final ScreenView myScreenView;
  /** The source image we scaled from */
  @Nullable private BufferedImage myImage;
  /** Cached scaled image */
  @Nullable private BufferedImage myScaledImage;
  /** Cached last render result */
  @Nullable private RenderResult myLastRenderResult;
  /** The scale at which we cached the scaled image  */
  private double myCachedScale;

  private Rectangle mySizeRectangle = new Rectangle();
  private Dimension myScreenViewSize = new Dimension();

  public ScreenViewLayer(@NotNull ScreenView screenView) {
    myScreenView = screenView;
  }

  @Nullable
  private static BufferedImage getRetinaScaledImage(@NotNull BufferedImage original, double scale) {
    if (scale > 1.01) {
      // When scaling up significantly, use normal painting logic; no need to pixel double into a
      // double res image buffer!
      return null;
    }

    // No scaling if very close to 1.0 (we check for 0.5 since we're doubling the output)
    if (Math.abs(scale - 0.5) > 0.01) {
      double retinaScale = 2 * scale;
      original = ImageUtils.scale(original, retinaScale, retinaScale);
    }

    return ImageUtils.convertToRetina(original);
  }

  private void setNewImage(@NotNull BufferedImage newImage, double newScale) {
    myCachedScale = newScale;
    myImage = newImage;
    myScaledImage = null;

    if (UIUtil.isRetina() && ImageUtils.supportsRetina()) {
      myScaledImage = getRetinaScaledImage(newImage, newScale);
    }
    if (myScaledImage == null) {
      // Fallback to normal scaling
      myScaledImage = ImageUtils.scale(newImage, newScale, newScale);
    }
  }

  @Override
  public void paint(@NotNull Graphics2D g) {
    myScreenViewSize = myScreenView.getSize(myScreenViewSize);

    mySizeRectangle.setBounds(myScreenView.getX(), myScreenView.getY(), myScreenViewSize.width, myScreenViewSize.height);
    Rectangle2D.intersect(mySizeRectangle, g.getClipBounds(), mySizeRectangle);
    if (mySizeRectangle.isEmpty()) {
      return;
    }

    RenderResult renderResult = myScreenView.getModel().getRenderResult();
    if (renderResult != null && renderResult.getImage() != null && renderResult != myLastRenderResult) {
      myLastRenderResult = renderResult;
      myImage = renderResult.getImage().getOriginalImage();
      myScaledImage = null;
    }

    if (myImage == null) {
      return;
    }

    double scale = myScreenView.getScale();
    if (myScaledImage == null || myCachedScale != scale) {
      setNewImage(myImage, scale);
    }

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

    UIUtil.drawImage(g, myScaledImage, myScreenView.getX(), myScreenView.getY(), null);
  }
}
