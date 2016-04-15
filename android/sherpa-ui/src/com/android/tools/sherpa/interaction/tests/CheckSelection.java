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
package com.android.tools.sherpa.interaction.tests;

import com.android.tools.sherpa.interaction.DrawPicker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Random;

/**
 * Test code for DrawPicker
 * This demonstrates how DrawPicker is used and test the performance
 */
public class CheckSelection extends JPanel {
  DrawPicker selector = new DrawPicker();
  Random mRandom = new Random();
  int mMaxX = 512;
  int mMaxY = 512;
  ArrayList<Drawable> list = new ArrayList<Drawable>();
  Drawable[] mOver = new Drawable[10];
  int count;
  int mouseX;
  int mouseY;

  CheckSelection() {
    buildMarks(25);

    selector.setSelectListener(new DrawPicker.HitElementListener() {
      @Override
      public void over(Object over, double dist) {
        if (count == mOver.length) {
          return;
        }
        mOver[count] = (Drawable)over;
        mOver[count].hover = true;
        count++;
        repaint();
      }
    });

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        list.clear();
        selector.reset();
        mMaxX = getWidth();
        mMaxY = getHeight();
        long time = System.nanoTime();
        buildMarks(mMaxX / 8);
        System.out.println("add " + 13 * (mMaxX / 8) + " objects = " + (System.nanoTime() - time) * 1E-6f + "ms");
        repaint();
      }
    });

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        select(mouseX, mouseY);
      }
    });

    Timer t = new Timer(16, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        selector.reset();
        for (Drawable drawable : list) {
          drawable.move();
          repaint();
        }
        select(mouseX, mouseY);
      }
    });
    t.start();
  }

  /**
   * perform the selection
   * @param x
   * @param y
   */
  void select(int x, int y) {
    long time = System.nanoTime();
    for (int i = 0; i < mOver.length; i++) {
      if (mOver[i] == null) {
        break;
      }
      mOver[i].hover = false;
    }
    count = 0;
    selector.find(x, y);
    System.out.println("selection  " + ((System.nanoTime() - time) * 1E-6f) + "ms");
  }

  void buildMarks(int size) {
    for (int i = 0; i < size * 8; i++) {
      list.add(new Point());
    }
    for (int i = 0; i < size * 2; i++) {
      list.add(new Line());
    }
    for (int i = 0; i < size; i++) {
      list.add(new Rect());
    }
    for (int i = 0; i < size * 2; i++) {
      list.add(new Curve());
    }
  }

  abstract class Drawable {
    boolean hover;
    int dx, dy;

    {
      int speed = 4;
      dx = mRandom.nextInt(speed) - speed / 2;
      dy = mRandom.nextInt(speed) - speed / 2;
    }

    public abstract void draw(Graphics2D g);

    public abstract void move();
  }

  class Point extends Drawable {
    int x1, y1;

    @Override
    public void move() {
      if (x1 > mMaxX) dx = -Math.abs(dx);
      if (y1 > mMaxY) dy = -Math.abs(dy);
      if (x1 < 0) dx = Math.abs(dx);
      if (y1 < 0) dy = Math.abs(dy);
      x1 += dx;
      y1 += dy;
      selector.addPoint(this, 10, x1, y1);
    }

    Point() {
      x1 = mRandom.nextInt(mMaxX);
      y1 = mRandom.nextInt(mMaxY);
      selector.addPoint(this, 10, x1, y1);
    }

    @Override
    public void draw(Graphics2D g) {
      g.setColor(hover ? Color.WHITE : Color.RED);
      g.drawRoundRect(x1 - 3, y1 - 3, 6, 6, 6, 6);
    }
  }

  class Line extends Drawable {
    int x1, y1;
    int x2, y2;

    @Override
    public void move() {
      if (x1 > mMaxX) dx = -Math.abs(dx);
      if (y1 > mMaxY) dy = -Math.abs(dy);
      if (x1 < 0) dx = Math.abs(dx);
      if (y1 < 0) dy = Math.abs(dy);
      x1 += dx;
      y1 += dy;
      x2 += dx;
      y2 += dy;
      selector.addLine(this, 10, x1, y1, x2, y2);
    }

    Line() {
      int size = mMaxX / 2;
      x1 = mRandom.nextInt(mMaxX);
      y1 = mRandom.nextInt(mMaxY);
      x2 = x1 + mRandom.nextInt(size) - size / 2;
      y2 = y1 + mRandom.nextInt(size) - size / 2;
      selector.addLine(this, 10, x1, y1, x2, y2);
    }

    @Override
    public void draw(Graphics2D g) {
      g.setColor(hover ? Color.WHITE : Color.RED);
      g.drawLine(x1, y1, x2, y2);
    }
  }

  class Rect extends Drawable {
    int x1, y1;
    int x2, y2;

    @Override
    public void move() {
      if (x1 > mMaxX) dx = -Math.abs(dx);
      if (y1 > mMaxY) dy = -Math.abs(dy);
      if (x1 < 0) dx = Math.abs(dx);
      if (y1 < 0) dy = Math.abs(dy);
      x1 += dx;
      y1 += dy;
      x2 += dx;
      y2 += dy;
      selector.addRect(this, 10, x1, y1, x2, y2);
    }

    Rect() {
      x1 = mRandom.nextInt(mMaxX);
      y1 = mRandom.nextInt(mMaxY);
      x2 = x1 + 70;
      y2 = y1 + 32;
      selector.addRect(this, 10, x1, y1, x2, y2);
    }

    @Override
    public void draw(Graphics2D g) {
      g.setColor(hover ? Color.WHITE : Color.RED);
      g.drawRect(x1, y1, x2 - x1, y2 - y1);
    }
  }

  class Curve extends Drawable {
    int x1, y1;
    int x2, y2;
    int x3, y3;
    int x4, y4;

    @Override
    public void move() {
      if (x1 > mMaxX) dx = -Math.abs(dx);
      if (y1 > mMaxY) dy = -Math.abs(dy);
      if (x1 < 0) dx = Math.abs(dx);
      if (y1 < 0) dy = Math.abs(dy);
      x1 += dx;
      y1 += dy;
      x2 += dx;
      y2 += dy;
      x3 += dx;
      y3 += dy;
      x4 += dx;
      y4 += dy;
      selector.addCurveTo(this, 10, x1, y1, x2, y2, x3, y3, x4, y4);
    }

    Curve() {
      x1 = mRandom.nextInt(mMaxX);
      y1 = mRandom.nextInt(mMaxY);
      int size = 200;
      int half = size / 2;
      x2 = x1 + mRandom.nextInt(size) - half;
      y2 = y1 + mRandom.nextInt(size) - half;
      x3 = x1 + mRandom.nextInt(size) - half;
      y3 = y1 + mRandom.nextInt(size) - half;
      x4 = x1 + mRandom.nextInt(size) - half;
      y4 = y1 + mRandom.nextInt(size) - half;
      selector.addCurveTo(this, 10, x1, y1, x2, y2, x3, y3, x4, y4);
    }

    @Override
    public void draw(Graphics2D g) {
      g.setColor(hover ? Color.WHITE : Color.RED);
      GeneralPath path = new GeneralPath();

      path.moveTo(x1, y1);
      path.curveTo(x2, y2, x3, y3, x4, y4);
      g.draw(path);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    int width = getWidth();
    int height = getHeight();
    g.setColor(Color.BLACK);
    g.fillRect(0, 0, width, height);
    Graphics2D g2d = (Graphics2D)g;
    for (Drawable drawable : list) {
      drawable.draw(g2d);
    }
  }

  public static void main(String[] args) {
    JFrame f = new JFrame("CurveToDistance");
    f.setBounds(new Rectangle(623, 660));
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    CheckSelection p = new CheckSelection();
    f.setContentPane(p);
    f.validate();
    f.setVisible(true);
  }
}
