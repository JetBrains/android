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
package com.android.tools.idea.common.scene.draw.test;

import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;

import javax.swing.*;
import java.awt.*;

/**
 * Simple application to render display list
 */
public class CheckDisplayList extends JPanel {
  DisplayList list;
  int count = 0;
  long time = System.nanoTime();
  public static final Color BLUEPRINT_BG_COLOR = new Color(0x133572);

  public CheckDisplayList() {
    setBackground(Color.BLUE);
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(BLUEPRINT_BG_COLOR);
    g.fillRect(0, 0, getWidth(), getHeight());

    if (list != null) {
      list.paint((Graphics2D)g, SceneContext.get());
    }
    count++;
    if (System.nanoTime()-time > 1000000000L) {
      double total = (System.nanoTime()-time)*1E-9;
      System.out.println(" "+((float)(count/total))+" fps");
      count = 0;
      time = System.nanoTime();
    }
    repaint();
  }

  String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                      "Clip,0,0,1000,1000\n" +
                      "DrawComponentBackground,450,490,100,20,1,false\n" +
                      "DrawTextRegion,450,490,100,20,0,0,false,false,5,5,28,1.0,\"\"\n" +
                      "DrawNlComponentFrame,450,490,100,20,1,20,20\n" +
                      "DrawConnection,2,450x490x100x20,0,0x0x1000x1000,0,1,false,0,0,false,0.5,0,0,0\n" +
                      "DrawConnection,2,450x490x100x20,1,0x0x1000x1000,1,1,false,0,0,false,0.5,0,0,0\n" +
                      "DrawConnection,2,450x490x100x20,2,0x0x1000x1000,2,1,false,0,0,false,0.5,0,0,0\n" +
                      "DrawConnection,2,450x490x100x20,3,0x0x1000x1000,3,1,false,0,0,false,0.5,0,0,0\n" +
                      "UNClip\n";

  {
    list = DisplayList.getDisplayList(simpleList);
  }

  public static void main(String[] args) {
    JFrame f = new JFrame("Spring");
    f.setBounds(new Rectangle(1100, 1100));
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    CheckDisplayList p = new CheckDisplayList();
    f.setContentPane(p);
    f.validate();
    f.setVisible(true);
  }
}
