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
package com.android.tools.adtui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ImageComponent is a Swing component that displays an image. The image is scaled to fit inside the component bounds, which are set
 * externally.
 */
public class ImageComponent extends OpaquePanel {
  protected Icon myIcon = null;

  public ImageComponent() {
  }

  public ImageComponent(@Nullable Icon icon) {
    setIcon(icon);
  }

  @Override
  protected void paintChildren(@NotNull Graphics g) {
    if (myIcon == null) return;
    Image image = IconLoader.toImage(myIcon, ScaleContext.create((Graphics2D)g));
    UIUtil.drawImage(g, image, new Rectangle(getWidth(), getHeight()), new Rectangle(image.getWidth(null), image.getHeight(null)), null);
  }

  public void setIcon(@Nullable Icon icon) {
    myIcon = icon;
    revalidate();
    repaint();
  }

  @Nullable
  public BufferedImage getImage() {
    return myIcon != null ? ImageUtil.toBufferedImage(IconUtil.toImage(myIcon)) : null;
  }
}
