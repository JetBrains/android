/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.layout3d;

import com.android.tools.idea.gradle.structure.configurables.ui.UiUtil;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.util.ui.UIUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;

/**
 * This is a simple test driver for the 3d engine
 */
public class CheckLayout3d extends JPanel {
  private static final boolean DEBUG_WITH_FILE = true;
  Display3D myDisplay3D = new Display3D();

  public CheckLayout3d() {
    super(new BorderLayout());
    add(myDisplay3D);
    myDisplay3D.addViewChangeListener(e -> savePref());
  }

  public static BufferedImage getTestImage() {
    BufferedImage img = null;
   if (DEBUG_WITH_FILE) {
     try {
       img = ImageIO.read(new File("/usr/local/google/home/hoford/phone.png"));
       return img;
     }
     catch (IOException e) {
       e.printStackTrace();
     }
   }
    //Typically the image would be loaded from a file using image = ImageIO.read...
    // but for testing we just build a pattern
    int width = 1024;
    int height = 1920;
    img = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_RGB);
    int[] data = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
    for (int i = 0; i < data.length; i++) {
      data[i] = ((((i % width) * 255) / width) << 16) | ((((i / width) * 255) / height) << 8) | (i >> 10);
    }

    return img;
  }

  public static Layout.View getTestViews(BufferedImage img) {
    float w = img.getWidth();
    float h = img.getHeight();
    float block = w / 10;
    float bottom = 398;
    float button_w = 124;

    float button_h = 108;
    float gap = (w - button_w * 5) / 6;
    Layout.View v = new Layout.View(0, 0, w, h)
      .addChild(
        new Layout.View(0, 0, w, 300)
          .addChild(
            new Layout.View(0, 94, w, 182)
          ),

        new Layout.View(gap * 1 + button_w * 0, h - bottom, button_w, button_h),
        new Layout.View(gap * 2 + button_w * 1, h - bottom, button_w, button_h),
        new Layout.View(gap * 3 + button_w * 2, h - bottom, button_w, button_h),
        new Layout.View(gap * 4 + button_w * 3, h - bottom, button_w, button_h),
        new Layout.View(gap * 5 + button_w * 4, h - bottom, button_w, button_h)
      );
    return v;
  }

  private void savePref() {
    Preferences prefs = Preferences.userNodeForPackage(CheckLayout3d.class);
    final String ORIENTATION = "name_of_preference";
    JFrame topFrame = (JFrame)SwingUtilities.getWindowAncestor(this);
    Rectangle rect = topFrame.getBounds();
    prefs.put(ORIENTATION, myDisplay3D.getOrientationString(rect));
  }

  public void loadPref() {
    Preferences prefs = Preferences.userNodeForPackage(CheckLayout3d.class);
    final String ORIENTATION = "name_of_preference";
    String pref = prefs.get(ORIENTATION, null);
    if (pref != null) {
      myDisplay3D.setup();
      Rectangle rect = myDisplay3D.parseOrientationString(pref);
      Frame topFrame = (JFrame)SwingUtilities.getWindowAncestor(this);
      topFrame.setBounds(rect);
    }
  }


  public static void create(Layout.View views, BufferedImage img) {
    JFrame f = new JFrame("CheckTriangles");
    CheckLayout3d p = new CheckLayout3d();
    f.setContentPane(p);
    f.setBounds(100, 100, 512, 512);

    p.myDisplay3D.setTriData(new Layout(img, views));

    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    f.setVisible(true);
  }
  public static void main(String[] args) {
    JFrame f = new JFrame("CheckTriangles");
    CheckLayout3d p = new CheckLayout3d();
    f.setContentPane(p);
    f.setBounds(100, 100, 512, 512);
    BufferedImage img = getTestImage();
    Layout.View views = getTestViews(img);
    p.myDisplay3D.setTriData(new Layout(img, views));
    if (args.length < 1 || !args[0].equals("-r")) {
      p.loadPref();
    }
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    f.setVisible(true);
  }
}
