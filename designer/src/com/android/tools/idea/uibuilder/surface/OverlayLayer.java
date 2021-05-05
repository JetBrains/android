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
import com.android.tools.idea.ui.designer.overlays.OverlayConfiguration;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * The Overlay Layer to be displayed on top of the layout preview
 */
public class OverlayLayer extends Layer {
  private static final String PLACEHOLDER_TEXT = "Loading Overlay...";
  private static final float PLACEHOLDER_ALPHA = 0.7f;
  private final SceneView mySceneView;
  private Dimension myScreenViewSize = new Dimension();

  /**
   * Creates the OverlayLayer
   *
   * @param sceneView
   */
  public OverlayLayer(SceneView sceneView) {
    mySceneView = sceneView;
  }

  @VisibleForTesting
  static float getPlaceholderAlpha() {
    return PLACEHOLDER_ALPHA;
  }

  @VisibleForTesting
  static String getPlaceholderText() {
    return PLACEHOLDER_TEXT;
  }

  private void paintPlaceholder(Graphics2D g) {
    g.setComposite(AlphaComposite.SrcOver.derive(PLACEHOLDER_ALPHA));
    g.setPaint(JBColor.WHITE);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                       RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    if (mySceneView.getScreenShape() != null) {
      g.fill(mySceneView.getScreenShape());
    }
    else {
      g.fillRect(mySceneView.getX(),
                 mySceneView.getY(),
                 myScreenViewSize.width,
                 myScreenViewSize.height);
    }

    g.setFont(UIUtil.getFont(UIUtil.FontSize.NORMAL, null));
    g.setPaint(JBColor.BLACK);

    TextLayout textLayout = new TextLayout(PLACEHOLDER_TEXT,
                                           g.getFont(),
                                           g.getFontRenderContext());
    double textHeight = textLayout.getBounds().getHeight();
    double textWidth = textLayout.getBounds().getWidth();

    g.drawString(PLACEHOLDER_TEXT,
                 mySceneView.getX() + myScreenViewSize.width / 2 - (int)textWidth / 2,
                 mySceneView.getY() + myScreenViewSize.height / 2 + (int)textHeight / 2);
  }

  private void paintOverlay(Graphics2D g, BufferedImage image) {
    OverlayConfiguration overlayConfiguration = mySceneView.getSurface().getOverlayConfiguration();
    if (image == null) {
      return;
    }

    g.setComposite(AlphaComposite.SrcOver.derive(overlayConfiguration.getOverlayAlpha()));
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                       RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(image,
                mySceneView.getX(),
                mySceneView.getY(),
                myScreenViewSize.width,
                myScreenViewSize.height,
                null);
  }


  @Override
  public void paint(@NotNull Graphics2D g) {
    OverlayConfiguration overlayConfiguration = mySceneView.getSurface().getOverlayConfiguration();
    if (overlayConfiguration.getOverlayVisibility()) {
      myScreenViewSize = mySceneView.getScaledContentSize(myScreenViewSize);

      if (overlayConfiguration.isPlaceholderVisible()) {
        paintPlaceholder(g);
      }
      else {
        paintOverlay(g, overlayConfiguration.getOverlayImage());
      }
    }
  }
}
