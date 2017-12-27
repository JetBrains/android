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

  String simpleList =  "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                       "Clip,0,0,1000,1000\n" +
                       "DrawComponentBackground,50,375,100,20,1\n" +
                       "DrawTextRegion,50,375,100,20,0,false,false,5,5,28,1.0,\"\"\n" +
                       "DrawComponentFrame,50,375,100,20,1,40,40\n" +
                       "DrawConnection,2,50x375x100x20,0,0x0x1000x1000,0,1,false,0,0,false,0.5,0,1,352680822097845\n" +
                       "DrawConnection,2,50x375x100x20,1,0x0x1000x1000,1,1,false,0,0,false,0.5,0,1,352680822123643\n" +
                       "DrawComponentBackground,200,525,100,15,1\n" +
                       "DrawTextRegion,200,525,100,15,0,false,false,5,5,28,1.0,\"\"\n" +
                       "DrawComponentFrame,200,525,100,15,1,30,30\n" +
                       "DrawConnection,2,200x525x100x15,0,0x0x1000x1000,0,1,false,0,0,false,0.5,0,1,352680822223779\n" +
                       "DrawConnection,2,200x525x100x15,1,0x0x1000x1000,1,1,false,0,0,false,0.5,0,1,352680822243725\n" +
                       "DrawConnection,1,200x525x100x15,3,50x375x100x20,3,0,false,0,0,false,0.5,0,1,352680882075149\n" +
                       "DrawComponentBackground,325,575,100,25,1\n" +
                       "DrawTextRegion,325,575,100,25,0,false,false,5,5,28,1.0,\"\"\n" +
                       "DrawComponentFrame,325,575,100,25,1,50,50\n" +
                       "DrawConnection,2,325x575x100x25,0,0x0x1000x1000,0,1,false,0,0,false,0.5,0,1,352680822326282\n" +
                       "DrawConnection,2,325x575x100x25,1,0x0x1000x1000,1,1,false,0,0,false,0.5,0,1,352680822351127\n" +
                       "DrawConnection,1,325x575x100x25,3,200x525x100x15,3,0,true,0,0,false,0.5,0,1,352680882145055\n" +
                       "UNClip";

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
