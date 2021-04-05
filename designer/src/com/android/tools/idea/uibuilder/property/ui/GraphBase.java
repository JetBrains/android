/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.ui;

import com.android.tools.adtui.common.StudioColorsKt;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Vector;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class GraphBase extends JPanel {
  int myInsTop = 30;
  int myInsLeft = 30;
  int myInsBottom = 30;
  int myInsRight = 30;
  float[][] myXPoints = new float[0][];
  float[][] myYPoints = new float[0][];
  Color[] myPointColor = new Color[0];
  float myActualMinx, myActualMiny, myActualMaxx, myActualMaxy;
  float myLastMinx, myLastMiny, myLastMaxx, myLastMaxy;
  float myMinx, myMiny, myMaxx, myMaxy;
  float myTickX;
  float myTickY;
  int myTextGap = 2;
  int myFrameCount = 0;
  private static Color ourGridColor = StudioColorsKt.getSecondaryGraphLines();
  private static Color ourTextGridColor = StudioColorsKt.getGraphLabel();
  private static Color ourEndPointColor  = StudioColorsKt.getContentSelectionBackground();

  private static Stroke ourStroke = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  Vector<DrawItem> myDrawItems = new Vector<DrawItem>();
  private int[] pointMode = new int[0];

  void waveGen(int base) {
    float fb = base / 100f;
    float sc = ((base) % 10000) / 10000f + 1;
    float[] xPoints = new float[1024];
    float[] yPoints = new float[xPoints.length];
    for (int i = 0; i < xPoints.length; i++) {
      float x = (float)((i + fb) * 10 * Math.PI / xPoints.length);
      xPoints[i] = x;
      yPoints[i] = (float)Math.sin(x) * sc;
    }
    addGraph(0, xPoints, yPoints, Color.WHITE, 0);
  }

  public GraphBase() {
    myDrawItems.addElement(mGrid);
    myDrawItems.addElement(mAxis);
    myDrawItems.addElement(mAxisVLabel);
    myDrawItems.addElement(mAxisHLabel);

    myDrawItems.addElement(mDrawGraph);
    waveGen(0);
    calcRange();
  }

  public void addGraph(int n, double[] x, double[] y, Color c, int mode) {
    float[] xf = new float[x.length];
    float[] yf = new float[y.length];
    for (int i = 0; i < yf.length; i++) {
      xf[i] = (float)x[i];
      yf[i] = (float)y[i];
    }
    addGraph(n, xf, yf, c, mode);
    calcRange();
    repaint();
  }


  public void addGraph(int n, double[][] p, Color c, int mode) {
    float[] xf = new float[p.length];
    float[] yf = new float[p.length];
    for (int i = 0; i < yf.length; i++) {
      xf[i] = (float)p[i][0];
      yf[i] = (float)p[i][1];
    }
    addGraph(n, xf, yf, c, mode);
    calcRange();
    repaint();
  }

  public void addGraph(int n, float[] x, float[] y, Color c, int mode) {
    if (myXPoints.length <= n) {
      float[][] yp = new float[n + 1][];
      float[][] xp = new float[n + 1][];
      Color[] ncol = new Color[n + 1];
      int[] m = new int[n + 1];
      for (int i = 0; i < myXPoints.length; i++) {
        xp[i] = myXPoints[i];
        yp[i] = myYPoints[i];
        ncol[i] = myPointColor[i];
        m[i] = pointMode[i];
      }
      myPointColor = ncol;
      myXPoints = xp;
      myYPoints = yp;
      pointMode = m;
    }
    myXPoints[n] = x;
    myYPoints[n] = y;
    myPointColor[n] = c;

    pointMode[n] = mode;
    calcRange();
    repaint();
  }

  public void calcRange() {
    myActualMinx = Float.MAX_VALUE;
    myActualMiny = Float.MAX_VALUE;
    myActualMaxx = -Float.MAX_VALUE;
    myActualMaxy = -Float.MAX_VALUE;
    for (int g = 0; g < myXPoints.length; g++) {
      if (myXPoints[g] == null | myYPoints[g] == null) {
        continue;
      }
      for (int i = 0; i < myXPoints[g].length; i++) {
        float x = myXPoints[g][i];
        float y = myYPoints[g][i];
        myActualMinx = Math.min(myActualMinx, x);
        myActualMiny = Math.min(myActualMiny, y);
        myActualMaxx = Math.max(myActualMaxx, x);
        myActualMaxy = Math.max(myActualMaxy, y);
      }
    }
  }

  void calcRangeTicks() {
    double dx = myActualMaxx - myActualMinx;
    double dy = myActualMaxy - myActualMiny;
    int sw = getWidth();
    int sh = getHeight();

    double border = 1.09345;

    if (Math.abs(myLastMinx - myActualMinx)
        + Math.abs(myLastMaxx - myActualMaxx) > 0.1 * (myActualMaxx - myActualMinx)) {
      myTickX = (float)calcTick(sw, dx);
      dx = myTickX * Math.ceil(border * dx / myTickX);
      double tx = (myActualMinx + myActualMaxx - dx) / 2;
      tx = myTickX * Math.floor(tx / myTickX);
      myMinx = (float)tx;
      tx = (myActualMinx + myActualMaxx + dx) / 2;
      tx = myTickX * Math.ceil(tx / myTickX);
      myMaxx = (float)tx;

      myLastMinx = myActualMinx;
      myLastMaxx = myActualMaxx;
    }
    if (Math.abs(myLastMiny - myActualMiny)
        + Math.abs(myLastMaxy - myActualMaxy) > 0.1 * (myActualMaxy - myActualMiny)) {
      myTickY = (float)calcTick(sh, dy);
      dy = myTickY * Math.ceil(border * dy / myTickY);
      double ty = (myActualMiny + myActualMaxy - dy) / 2;
      ty = myTickY * Math.floor(ty / myTickY);
      myMiny = (float)ty;
      ty = (myActualMiny + myActualMaxy + dy) / 2;
      ty = myTickY * Math.ceil(ty / myTickY);
      myMaxy = (float)ty;

      myLastMiny = myActualMiny;
      myLastMaxy = myActualMaxy;
    }

    // TODO: cleanup
    myMinx = 0;
    myMiny = 0;
    myMaxx = 1;
    myMaxy = 1;
  }

  static public double calcTick(int scr, double range) {
    int aprox_x_ticks = scr / 50;
    int type = 1;
    double best = Math.log10(range / ((double)aprox_x_ticks));
    double n = Math.log10(range / ((double)aprox_x_ticks * 2));
    if (frac(n) < frac(best)) {
      best = n;
      type = 2;
    }
    n = Math.log10(range / (aprox_x_ticks * 5));
    if (frac(n) < frac(best)) {
      best = n;
      type = 5;
    }
    return type * Math.pow(10, Math.floor(best));
  }

  static double frac(double x) {
    return x - Math.floor(x);
  }

  interface DrawItem {
    public void paint(Graphics2D g, int w, int h);
  }

  @Override
  protected void paintComponent(Graphics g) {
    myFrameCount++;
    calcRangeTicks();
    int w = getWidth();
    int h = getHeight();
    Graphics2D g2d = (Graphics2D)g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
    Paint paint = new GradientPaint(0, 0, Color.BLACK, 0,
        (h * 2) / 3f, new Color(
        0x182323), true);
    g2d.setPaint(paint);
    g2d.setColor(getBackground());
    g2d.fillRect(0, 0, w, h);
    g2d.setColor(ourGridColor);
    for (DrawItem drawItem : myDrawItems) {
      drawItem.paint(g2d, w, h);
    }
  }

  public void screenToGraph(Point scr, Point2D graph) {
    int draw_width = getWidth() - myInsLeft - myInsRight;
    int draw_height = getHeight() - myInsTop - myInsLeft;
    float x = myMinx + (myMaxx - myMinx) * ((scr.x - myInsLeft) / (float)draw_width);
    float y = myMiny + (myMaxy - myMiny) * ((myInsBottom + draw_height - scr.y) / (float)draw_height);
    graph.setLocation(x, y);
  }

  DrawItem mAxis = new DrawItem() {
    @Override
    public void paint(Graphics2D g, int w, int h) {
      g.setColor(ourGridColor);

      g.drawLine(myInsLeft, myInsTop, myInsLeft, h - myInsBottom);
      g.drawLine(myInsLeft, h - myInsBottom, w - myInsRight, h - myInsBottom);
    }
  };

  DrawItem mGrid = new DrawItem() {
    DecimalFormat df = new DecimalFormat("###.#");

    @Override
    public void paint(Graphics2D g, int w, int h) {
      g.setColor(ourTextGridColor);
      int draw_width = w - myInsLeft - myInsRight;
      float e = 0.0001f * (myMaxx - myMinx);
      FontMetrics fm = g.getFontMetrics();
      int ascent = fm.getAscent();

      for (float i = myMinx; i <= myMaxx + e; i += myTickX) {
        int ix = (int)(draw_width * (i - myMinx) / (myMaxx - myMinx) + myInsLeft);
        g.drawLine(ix, myInsTop, ix, h - myInsBottom);
        String str = df.format(i);
        int sw = fm.stringWidth(str) / 2;

        g.drawString(str, ix - sw, h - myInsBottom + ascent + myTextGap);
      }
      int draw_height = h - myInsTop - myInsLeft;
      e = 0.0001f * (myMaxy - myMiny);
      int hightoff = -fm.getHeight() / 2 + ascent;
      for (float i = myMiny; i <= myMaxy + e; i += myTickY) {
        int iy = (int)(draw_height * (1 - (i - myMiny) / (myMaxy - myMiny)) + myInsTop);
        g.drawLine(myInsLeft, iy, w - myInsRight, iy);
        String str = df.format(i);
        int sw = fm.stringWidth(str);

        g.drawString(str, myInsLeft - sw - myTextGap, iy + hightoff);
      }
    }
  };

  DrawItem mAxisVLabel = new DrawItem() {

    @Override
    public void paint(Graphics2D g, int w, int h) {

    }
  };

  DrawItem mAxisHLabel = new DrawItem() {
    @Override
    public void paint(Graphics2D g, int w, int h) {
    }
  };

  DrawItem mDrawGraph = new DrawItem() {
    int[] xp = new int[0];
    int[] yp = new int[0];

    @Override
    public void paint(Graphics2D g, int w, int h) {
      g.setColor(ourGridColor);
      if (myXPoints.length == 0) return;
      if (xp.length != myXPoints[0].length) {
        xp = new int[myXPoints[0].length];
        yp = new int[myXPoints[0].length];
      }
      int draw_width = w - myInsLeft - myInsRight;
      int draw_height = h - myInsTop - myInsLeft;

      for (int k = 0; k < myXPoints.length; k++) {
        if (myXPoints[k] == null || myYPoints[k] == null) {
          continue;
        }
        for (int i = 0; i < myXPoints[k].length; i++) {
          float x = draw_width * (myXPoints[k][i] - myMinx)
              / (myMaxx - myMinx) + myInsLeft;
          float y = draw_height
              * (1 - (myYPoints[k][i] - myMiny) / (myMaxy - myMiny))
              + myInsTop;
          xp[i] = (int)x;
          yp[i] = (int)y;
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(myPointColor[k]);
        g.setStroke(ourStroke);
        if (pointMode[k] == 0) {
          g.drawPolyline(xp, yp, myXPoints[k].length);
        }
        else if (pointMode[k] == 2) { // ticks
          final int TICK_SIZE = 2;
          for (int i = 0; i < myXPoints[k].length; i++) {
            int pointX = xp[i];
            int pointY = yp[i];
            g.fillRoundRect(pointX - TICK_SIZE, pointY - TICK_SIZE * 4, TICK_SIZE * 2, TICK_SIZE * 8, TICK_SIZE * 2, TICK_SIZE * 8);
          }
        }
        else {
          final int CIRCLE_SIZE = 4;
          for (int i = 0; i < myXPoints[k].length; i++) { // CIRCLE
            if (i == 0 || i == 3) {
              g.setColor(ourEndPointColor);
            }
            else {
              g.setColor(myPointColor[k]);
            }
            int pointX = xp[i];
            int pointY = yp[i];
            g.fillRoundRect(pointX - CIRCLE_SIZE, pointY - CIRCLE_SIZE, CIRCLE_SIZE * 2, CIRCLE_SIZE * 2, CIRCLE_SIZE * 2, CIRCLE_SIZE * 2);
          }
        }
      }
    }
  };

  public static GraphBase create(String title) {

    JFrame f = new JFrame(title);
    f.setBounds(new Rectangle(200, 200));
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JPanel base = new JPanel(new BorderLayout());
    JPanel ctl = new JPanel();
    base.add(ctl, BorderLayout.SOUTH);
    JButton button = new JButton("Save...");
    GraphBase p = new GraphBase();
    button.addActionListener(e -> save(e, p));
    ctl.add(button);
    base.add(p);
    f.setContentPane(base);
    f.validate();
    f.setVisible(true);
    return p;
  }

  public static void save(ActionEvent e, GraphBase graph) {
    int w = graph.getWidth();
    int h = graph.getHeight();
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    graph.paint(img.createGraphics());
    JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.home")));
    int c = chooser.showSaveDialog(graph);
    if (c == JFileChooser.CANCEL_OPTION) {
      System.out.println("cancel");
      return;
    }
    try {
      File f = chooser.getSelectedFile();
      ImageIO.write(img, "png", f);
      System.out.println(f.getAbsolutePath());

      Desktop.getDesktop().open(f.getParentFile());
    }
    catch (IOException e1) {
      e1.printStackTrace();
    }
  }
}
