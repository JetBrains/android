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
package com.android.tools.idea.uibuilder.scene.draw;

import com.android.tools.idea.uibuilder.scene.SceneContext;

import javax.swing.*;
import java.awt.*;

/**
 * Simple application to render display list
 */
public class CheckDisplayList extends JPanel {
  DisplayList list;
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
  }

  String simpleList = "Rect,0,0,1000,1000,ffff0000\n" +
                      "Clip,0,0,1000,1000\n" +
                      "Rect,450,490,100,20,ff00ffff\n" +
                      "Rect,450,490,100,20,ff00ff00\n" +
                      "Line,450,490,550,510,ffff0000\n" +
                      "Line,450,510,550,490,ffff0000\n" +
                      "Rect,450,490,100,20,ff00ff00\n" +
                      "Line,450,490,550,510,ffff0000\n" +
                      "Line,450,510,550,490,ffff0000\n" +
                      "DrawConnection,2,450x490x100x20,0,0x0x1000x1000,0,true,false,0,0.5\n" +
                      "DrawConnection,2,450x490x100x20,1,0x0x1000x1000,1,true,false,0,0.5\n" +
                      "DrawConnection,2,450x490x100x20,2,0x0x1000x1000,2,true,false,0,0.5\n" +
                      "DrawConnection,2,450x490x100x20,3,0x0x1000x1000,3,true,false,0,0.5\n" +
                      "Rect,450,490,100,20,ff00ffff\n" +
                      "Rect,450,490,100,20,ff00ff00\n" +
                      "Line,450,490,550,510,ffff0000\n" +
                      "Line,450,510,550,490,ffff0000\n" +
                      "Rect,450,490,100,20,ff00ff00\n" +
                      "Line,450,490,550,510,ffff0000\n" +
                      "Line,450,510,550,490,ffff0000\n" +
                      "UNClip\n";

  {
    list = DisplayList.getDisplayList(simpleList);
  }

  public static void main(String[] args) {
    JFrame f = new JFrame("Spring");
    f.setBounds(new Rectangle(900, 900));
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    CheckDisplayList p = new CheckDisplayList();
    f.setContentPane(p);
    f.validate();
    f.setVisible(true);
  }
}
