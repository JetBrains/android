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
package com.android.tools.idea.monitor.ui.network.view;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class NetworkDetailedView extends JComponent {
  private static final int PADDING = 100;

  @NotNull
  private BufferedImage myImage;

  private String myAppId;

  public NetworkDetailedView() {
    setPreferredSize(new Dimension(350, 0));
    setBorder(BorderFactory.createLineBorder(JBColor.black));
    myImage = UIUtil.createImage(180, 150, BufferedImage.TYPE_BYTE_GRAY);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    g.drawImage(myImage, (getWidth() - myImage.getWidth()) / 2, PADDING, null);
  }

  public void showConnectionDetails(@NotNull File imageFile) {
    try {
      myImage = ImageIO.read(imageFile);
    } catch (IOException e) {
      return;
    }
    repaint();
  }
}
