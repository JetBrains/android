/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * ImageComponent is a Swing component that displays an image. The image is scaled to fit inside the component bounds, which are set
 * externally.
 */
public class ImageComponent extends OpaquePanel {
  protected BufferedImage myImage = null;

  public ImageComponent() {
  }

  public ImageComponent(@Nullable Icon icon) {
    setIcon(icon);
  }

  @Override
  protected void paintChildren(@NotNull Graphics g) {
    if (myImage == null) return;
    Graphics2D g2 = (Graphics2D) g.create();
    setRenderingHints(g2);
    g2.drawImage(myImage, 0, 0, getWidth(), getHeight(), 0, 0, myImage.getWidth(), myImage.getHeight(), null);
  }

  public void setIcon(@Nullable Icon icon) {
    if (icon != null) {
      myImage = UIUtil.createImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      final Graphics2D gg = myImage.createGraphics();
      setRenderingHints(gg);
      icon.paintIcon(this, gg, 0, 0);
      gg.dispose();
    } else {
      myImage = null;
    }
    revalidate();
    repaint();
  }

  private static void setRenderingHints(Graphics2D gg) {
    gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
  }
}
