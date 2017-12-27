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

import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils;
import com.android.tools.idea.common.scene.draw.FancyStroke;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.util.Random;

public class CheckFancyStroke extends JPanel {
  public static final Color BLUEPRINT_BG_COLOR = new Color(0x112572);
  public static final Color BLUEPRINT_FG_COLOR = new Color(0xFFF5C2);
  int[] xp = new int[100];
  int[] yp = new int[xp.length];
  public static final int CURVE = 30;

  public CheckFancyStroke() {
    fill();
    setBackground(Color.BLUE);
    addMouseListener(new MouseAdapter() {

      @Override
      public void mousePressed(MouseEvent e) {
        fill();
        repaint();
      }

      @Override
      public void mouseReleased(MouseEvent e) {

      }
    });
  }

  @Override
  protected void paintComponent(Graphics g) {
    int w = getWidth();
    int h = getHeight();
    g.setColor(BLUEPRINT_BG_COLOR);
    Graphics2D g2d = ((Graphics2D)g);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.fillRect(0, 0, w, h);
    GeneralPath p = new GeneralPath();
    float cx = w / 2.f;
    float cy = h / 2.f;
    g.setColor(BLUEPRINT_FG_COLOR);

    if (false) {
      p.moveTo(cx, cy);
      for (int i = 0; i < 10000; i++) {
        double f = i / 100.;
        p.lineTo(cx + 4 * f * Math.sin(f), cy + 4 * f * Math.cos(f));
      }

      g2d.setStroke(new FancyStroke(FancyStroke.Type.SPRING, 2, 2, 1));
      g2d.draw(p);
    }
    if (false){
      p.reset();
      p.moveTo(cx, cy);
      for (int i = 0; i < 10000; i++) {
        double f = i / 100.;
        p.lineTo(cx - (4 * f) * Math.sin(f), cy - (4 * f) * Math.cos(f));
      }
      g2d.setStroke(new FancyStroke(FancyStroke.Type.SPRING, 5, 6, 2));
      g2d.draw(p);
    }
    if (false) {
      g2d.setStroke(new FancyStroke(FancyStroke.Type.SPRING, 10, 20, 2));
      g.drawRoundRect(100, 100, w - 200, h - 200, 0, 0);
    }
    Random r = new Random();
    for(int n = 0; n < 7; n++){
      fill();
      float size = 1 + (float) Math.random() * 4;
      int spacing = 2 +r.nextInt(20);
      float width = 1;

      int type = +r.nextInt(4);;
      switch (type) {
        case 0:
          g2d.setStroke(new FancyStroke(FancyStroke.Type.SINE, size, spacing, width));
        break;
        case 1:
          g2d.setStroke(new FancyStroke(FancyStroke.Type.SPRING, size, spacing, width));
          break;
        case 2:
          g2d.setStroke(new FancyStroke(FancyStroke.Type.ROPE, size, spacing, width));
          break;
        case 3:
          g2d.setStroke(new FancyStroke(FancyStroke.Type.CHAIN, size, spacing, width));
          break;

      }
      p.reset();
      p.moveTo(xp[0], yp[0]);
      DrawConnectionUtils.drawRound(p, xp, yp, CURVE, 4 + r.nextInt(100));
      g2d.draw(p);
    }
  }

  private void fill() {
    if (getWidth() < CURVE * 2 || getHeight() < CURVE * 2) {
      return;
    }

    Random r = new Random();
    int seed = r.nextInt();//-1452007195;//
    System.out.println("seed =" + seed);
    r.setSeed(seed);
    int rand;
    xp[0] = r.nextInt(getWidth());
    yp[0] = r.nextInt(getHeight());
    for (int i = 1; i < xp.length; i++) {
      if ((i & 1) == 0) {
        do {
          rand = r.nextInt(getWidth()) - getWidth() / 2;
        }
        while (Math.abs(rand) <= CURVE);

        xp[i] = xp[i - 1] + rand;
        yp[i] = yp[i - 1];
      }
      else {
        do {
          rand = r.nextInt(getHeight()) - getHeight() / 2;
        }
        while (Math.abs(rand) <= CURVE);
        yp[i] = yp[i - 1] + rand;
        xp[i] = xp[i - 1];
      }
      if (xp[i] >= getWidth() || xp[i] < 0 || yp[i] >= getHeight() || yp[i] < 0) {
        i--;
      }
    }
  }

  public static void main(String[] args) {
    JFrame f = new JFrame("CheckFancyStroke");
    f.setBounds(new Rectangle(1400, 900));
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    CheckFancyStroke p = new CheckFancyStroke();
    f.setContentPane(p);
    f.validate();
    f.setVisible(true);
  }
}
