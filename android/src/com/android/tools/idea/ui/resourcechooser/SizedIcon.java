/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser;

import javax.swing.*;
import java.awt.*;

class SizedIcon implements Icon {
  private final int mySize;
  private final Image myImage;

  public SizedIcon(int size, Image image) {
    mySize = size;
    myImage = image;
  }

  public SizedIcon(int size, ImageIcon icon) {
    this(size, icon.getImage());
  }

  @Override
  public void paintIcon(Component c, Graphics g, int i, int j) {
    double scale = Math.min(getIconHeight() / (double)myImage.getHeight(c), getIconWidth() / (double)myImage.getWidth(c));
    int x = (int)(getIconWidth() - (myImage.getWidth(c) * scale)) / 2;
    int y = (int)(getIconHeight() - (myImage.getHeight(c) * scale)) / 2;
    ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(myImage, i + x, j + y, (int)(myImage.getWidth(c) * scale), (int)(myImage.getHeight(c) * scale), null);
  }

  @Override
  public int getIconWidth() {
    return mySize;
  }

  @Override
  public int getIconHeight() {
    return mySize;
  }
}
