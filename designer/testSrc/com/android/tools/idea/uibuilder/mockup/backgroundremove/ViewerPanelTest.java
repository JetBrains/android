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
package com.android.tools.idea.uibuilder.mockup.backgroundremove;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 *
 */
public class ViewerPanelTest {

  @SuppressWarnings("UndesirableClassUsage")
  public static void main(String[] arg) {
    JFrame frame = new JFrame();
    RemoveBackgroundPanel contentPane = new RemoveBackgroundPanel();
    try {
      //BufferedImage image = ImageIO.read(new File("/usr/local/google/home/caen/testfiles/nexus_number26.png"));
      //BufferedImage image = ImageIO.read(new File("/usr/local/google/home/caen/testfiles/extract_test.png"));
      //BufferedImage image = ImageIO.read(new File("/usr/local/google/home/caen/Wallpaper/26805481343_00ca18bd49_k.jpg"));
      //BufferedImage image = ImageIO.read(new File("/usr/local/google/home/caen/testfiles/nexus_fit.png"));
      BufferedImage image = ImageIO.read(new File("/usr/local/google/home/caen/testfiles/icons_big.png"));
      if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
        BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        newImage.getGraphics().drawImage(image, 0, 0, image.getWidth(), image.getHeight(), null);
        image = newImage;
      }
      contentPane.setImage(image);
      contentPane.setPreferredSize(new Dimension(300, 400));
      frame.setContentPane(contentPane);
      frame.pack();
      frame.setVisible(true);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}