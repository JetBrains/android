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
package com.android.tools.idea.uibuilder.scout.test;

import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scout.RectangleGenerator;
import com.android.tools.idea.uibuilder.scout.ConstraintSetGenerator;
import com.android.tools.idea.uibuilder.scout.ScoutWidget;

import java.util.ArrayList;

import javax.swing.*;
import java.awt.*;

/**
 * Test interface to display a widget space randomly generated widgets and their
 * brute force generated constraint set
 */
public class ConstraintGeneratorTest extends JPanel {

  DisplayList list;
  int count = 0;
  long time = System.nanoTime();
  public static final Color BLUEPRINT_BG_COLOR = new Color(0x133572);
  private String mSceneList = "";

  public ConstraintGeneratorTest() {
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
    if (System.nanoTime() - time > 1000000000L) {
      double total = (System.nanoTime() - time) * 1E-9;
      System.out.println(" " + ((float)(count / total)) + " fps");
      count = 0;
      time = System.nanoTime();
    }
    repaint();
  }

  {

  }

  public void setSceneList(String sceneList) {
    mSceneList = sceneList;
    list = DisplayList.getDisplayList(mSceneList);
    repaint();
  }

  public static void main(String[] args) {
    JFrame f = new JFrame("Constraint Set");
    f.setBounds(new Rectangle(1100, 1100));
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    com.android.tools.idea.uibuilder.scout.test.ConstraintGeneratorTest p =
      new com.android.tools.idea.uibuilder.scout.test.ConstraintGeneratorTest();
    RectangleGenerator rectGenerator = new RectangleGenerator();
    ArrayList<ScoutWidget> recs = rectGenerator.random(3, 3, 1000, 900);
    ScoutWidget[] widgets = recs.toArray(new ScoutWidget[4]);
    ConstraintSetGenerator lists = new ConstraintSetGenerator(widgets);
    String list = rectGenerator.displayRecs(widgets);
    list += lists.findConstraintSet().displayString();
    p.setSceneList(list);
    f.setContentPane(p);
    f.validate();
    f.setVisible(true);
  }
}
