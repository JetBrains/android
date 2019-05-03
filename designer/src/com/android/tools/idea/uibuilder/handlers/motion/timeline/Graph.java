/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.timeline;

import com.android.tools.idea.uibuilder.handlers.motion.timeline.utils.CurveFit;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.utils.Oscillator;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Vector;

import static com.android.tools.idea.uibuilder.handlers.motion.timeline.utils.CurveFit.LINEAR;
import static com.android.tools.idea.uibuilder.handlers.motion.timeline.utils.CurveFit.SPLINE;

/**
 * Provides the graphing system used to display Cycles and Attributes
 */
public class Graph extends JPanel {

  private final Chart myChart;
  int myInsTop = 1;
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
  int myFCount = 0;
  Color myBackgroundColor = Chart.ourPrimaryPanelBackground;
  Color myBackgroundColor2 = Chart.ourPrimaryPanelBackground;
  Color myDrawing = new Color(0xAAAAAA);
  Color myGridColor = new Color(0x999999);
  public static final byte LINE_STYLE = 0;
  public static final byte TICK_STYLE = 1;
  public static final byte CIRCLE_STYLE = 2;

  Stroke myStroke = new BasicStroke(1f);
  Vector<DrawItem> myDrawItems = new Vector<>();
  private int[] myStyle = new int[0];

  @Override
  public void setBackground(Color color) {
    super.setBackground(color);
    myBackgroundColor = color;
    myBackgroundColor2 = color;//new Color((color.getRed()*19)/20,color.getGreen(),(color.getBlue()*19)/20);
  }

  void waveGen(int base) {
    float fb = base / 100f;
    float sc = ((base) % 10000) / 10000f + 1;
    float[] xPoints = new float[1024];
    float[] yPoints = new float[xPoints.length];
    for (int i = 0; i < xPoints.length; i++) {
      float p = 100 * (float)i / (float)(xPoints.length - 1);
      xPoints[i] = p;
      yPoints[i] = p / 100;
    }
    addGraph(0, xPoints, yPoints, new Color(0xff3d81e1), LINE_STYLE);
  }

  public Graph(Chart chart) {
    myChart = chart;
    myDrawItems.addElement(mGrid);
    myDrawItems.addElement(mAxis);
    myDrawItems.addElement(mAxisVLabel);
    myDrawItems.addElement(mAxisHLabel);

    myDrawItems.addElement(mDrawGraph);
    waveGen(0);
    calcRange();
  }

  public void addGraph(int n, double[] x, double[] y, Color c, byte style) {
    float[] xf = new float[x.length];
    float[] yf = new float[y.length];
    for (int i = 0; i < yf.length; i++) {
      xf[i] = (float)x[i];
      yf[i] = (float)y[i];
    }
    addGraph(n, xf, yf, c, style);
    calcRange();
    repaint();
  }

  public void addGraph(int n, float[] x, float[] y, Color c, byte style) {
    if (myXPoints.length <= n) {
      float[][] yp = new float[n + 1][];
      float[][] xp = new float[n + 1][];
      Color[] ncol = new Color[n + 1];
      int[] m = new int[n + 1];
      for (int i = 0; i < myXPoints.length; i++) {
        xp[i] = myXPoints[i];
        yp[i] = myYPoints[i];
        ncol[i] = myPointColor[i];
        m[i] = myStyle[i];
      }
      myPointColor = ncol;
      myXPoints = xp;
      myYPoints = yp;
      myStyle = m;
    }
    myXPoints[n] = x;
    myYPoints[n] = y;
    myPointColor[n] = c;

    myStyle[n] = style;
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
      myMinx = myActualMinx;//(float) tx;
      tx = (myActualMinx + myActualMaxx + dx) / 2;
      tx = myTickX * Math.ceil(tx / myTickX);
      myMaxx = myActualMaxx;//(float) tx;

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
  }

  public void draw(Chart.GraphElements graph) {
    switch (graph.myType) {
      case CYCLES:
        ArrayList<MotionSceneModel.KeyCycle> cycleList = new ArrayList<>();
        for (Gantt.ViewElement element : myChart.myViewElements) {
          if (element.myName.equals(graph.myViewId)) {
            for (MotionSceneModel.KeyCycle keyCycle : element.mKeyFrames.myKeyCycles) {
              if (keyCycle.myAttributes.containsKey(graph.myElement)) {
                cycleList.add(keyCycle);
              }
            }
          }
        }
        graphKeyCycle(cycleList, graph.myElement);
        break;
      case ATTRIBUTES:
        ArrayList<MotionSceneModel.KeyAttributes> attrList = new ArrayList<>();
        for (Gantt.ViewElement element : myChart.myViewElements) {
          if (element.myName.equals(graph.myViewId)) {
            for (MotionSceneModel.KeyAttributes keyAttr : element.mKeyFrames.myKeyAttributes) {
              if (keyAttr.myAttributes.containsKey(graph.myElement)) {
                attrList.add(keyAttr);
              }
            }
          }
        }
        graphKeyAttributes(attrList, graph.myElement);
    }
  }

  private void graphKeyAttributes(ArrayList<MotionSceneModel.KeyAttributes> list, String element) {
    if (list.isEmpty()) {
      return;
    }
    list.sort(new Comparator<MotionSceneModel.KeyAttributes>() {
      @Override
      public int compare(MotionSceneModel.KeyAttributes k1, MotionSceneModel.KeyAttributes k2) {
        return Integer.compare(k1.framePosition, k2.framePosition);
      }
    });
    int first = 0;
    int last = 0;
    if (list.get(0).framePosition != 0) {
      first++;
    }
    if (list.get(list.size() - 1).framePosition != 100) {
      last++;
    }
    double[] x = new double[list.size() + first + last];
    double[] y = new double[list.size() + first + last];
    int i = first;
    for (MotionSceneModel.KeyAttributes attributes : list) {
      x[i] = attributes.framePosition;
      y[i] = attributes.getFloat(element);
      i++;
    }
    if (first == 1) {
      x[0] = x[1];
      y[0] = y[1];
    }
    if (last == 1) {
      int n = x.length - 1;
      x[n] = x[n - 1];
      y[n] = y[n - 1];
    }
    addGraph(0, x, y, Chart.myUnSelectedLineColor, TICK_STYLE);
    double[][] curves = new double[][]{y};
    CurveFit fit = CurveFit.get((y.length > 2) ? SPLINE : LINEAR, x, curves);
    int CURVE_POINTS = 200;
    double[] xline = new double[CURVE_POINTS];
    double[] yline = new double[CURVE_POINTS];
    for (int j = 0; j < xline.length; j++) {
      double xp = 100 * j / (double)(xline.length - 1);
      xline[j] = xp;
      yline[j] = fit.getPos(xp, 0);
    }
    addGraph(0, xline, yline, Chart.myUnSelectedLineColor, LINE_STYLE);
  }

  private void graphKeyCycle(ArrayList<MotionSceneModel.KeyCycle> list, String element) {
    if (list.isEmpty()) {
      return;
    }
    list.sort(new Comparator<MotionSceneModel.KeyCycle>() {
      @Override
      public int compare(MotionSceneModel.KeyCycle k1, MotionSceneModel.KeyCycle k2) {
        return Integer.compare(k1.framePosition, k2.framePosition);
      }
    });

    Oscillator oscillator = new Oscillator();
    float waveOffset = Float.NaN;
    float wavePeriod = Float.NaN;
    String waveShape;

    for (MotionSceneModel.KeyCycle cycle : list) {

    }
  }

  static public double calcTick(int scr, double range) {
    int aprox_x_ticks = scr / 100;
    int type = 1;
    double best = Math.log10(range / (aprox_x_ticks));
    double n = Math.log10(range / (aprox_x_ticks * 2));
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

  private void paintBorder(Graphics g, int width, int height) {
    g.setColor(getBackground());
    g.fillRect(0, 0, width, height);
    g.setColor(Chart.ourBorderLight);
    g.fillRect(0, 0, myChart.myChartLeftInset, height);
    g.fillRect(width - myChart.myChartRightInset, 0, myChart.myChartRightInset, height);
    g.fillRect(width - myChart.myChartRightInset, 0, myChart.myChartRightInset, height);
    g.fillRect(myChart.myChartLeftInset, height - myChart.myBottomInsert,
               width - myChart.myChartLeftInset - myChart.myChartRightInset, myChart.myBottomInsert);
  }

  @Override
  protected void paintComponent(Graphics g) {
    myFCount++;
    calcRangeTicks();
    int w = getWidth();
    int h = getHeight();
    paintBorder(g, w, h);
    Graphics2D g2d = (Graphics2D)g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Paint paint = new GradientPaint(0, 0, getBackground(), 0, (float)(int)(h * 2) / 3, myBackgroundColor2, true);

    g.setColor(myDrawing);
    for (DrawItem drawItem : myDrawItems) {
      drawItem.paint(g2d, w, h);
    }
  }

  //================================================================//
  DrawItem mAxis = new DrawItem() {
    @Override
    public void paint(Graphics2D g, int w, int h) {
      g.setColor(myDrawing);

      g.drawLine(myChart.myChartLeftInset, myInsTop, myInsLeft, h - myInsBottom);
      g.drawLine(myInsLeft, h - myInsBottom, w - myInsRight, h - myInsBottom);
    }
  };

  //==================================================================//
  DrawItem mGrid = new DrawItem() {
    DecimalFormat df = new DecimalFormat("###.#");

    @Override
    public void paint(Graphics2D g, int w, int h) {
      g.setColor(myGridColor);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

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

  //========================================================//
  DrawItem mAxisVLabel = new DrawItem() {

    @Override
    public void paint(Graphics2D g, int w, int h) {

    }
  };
  //=======================================================//
  DrawItem mAxisHLabel = new DrawItem() {

    @Override
    public void paint(Graphics2D g, int w, int h) {

    }
  };
  //======================================================= //
  DrawItem mDrawGraph = new DrawItem() {
    int[] xp = new int[0];
    int[] yp = new int[0];

    @Override
    public void paint(Graphics2D g, int w, int h) {
      g.setColor(myDrawing);
      if (myXPoints.length == 0) {
        return;
      }
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
          xp[i] = (int)(x + 0.5);
          yp[i] = (int)(y + 0.5);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(myPointColor[k]);
        g.setStroke(myStroke);
        if (myStyle[k] == LINE_STYLE) {
          g.drawPolyline(xp, yp, myXPoints[k].length);
        }
        else if (myStyle[k] == TICK_STYLE) { // ticks
          final int TICK_SIZE = 2;
          for (int i = 0; i < myXPoints[k].length; i++) {
            int pointX = xp[i];
            int pointY = yp[i];
            g.fillRoundRect(pointX - TICK_SIZE, pointY - TICK_SIZE * 4, TICK_SIZE * 2,
                            TICK_SIZE * 8, TICK_SIZE * 2, TICK_SIZE * 8);
          }
        }
        else {
          final int CIRCLE_SIZE = 16;
          for (int i = 0; i < myXPoints[k].length; i++) { // CIRCLE
            int pointX = xp[i];
            int pointY = yp[i];
            g.fillRoundRect(pointX - CIRCLE_SIZE, pointY - CIRCLE_SIZE, CIRCLE_SIZE * 2,
                            CIRCLE_SIZE * 2, CIRCLE_SIZE * 2, CIRCLE_SIZE * 2);
          }
        }
      }
    }
  };

  public static Graph create() {

    JFrame f = new JFrame("enclosing_type");
    f.setBounds(new Rectangle(623, 660));
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JPanel base = new JPanel(new BorderLayout());
    JPanel ctl = new JPanel();
    base.add(ctl, BorderLayout.SOUTH);
    JButton button = new JButton("Save...");
    Graph p = new Graph(new Chart(null));
    button.addActionListener(e -> save(e, p));
    ctl.add(button);
    base.add(p);
    f.setContentPane(base);
    f.validate();
    f.setVisible(true);
    return p;
  }

  public static void save(ActionEvent e, Graph graph) {
    int w = graph.getWidth();
    int h = graph.getHeight();
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    graph.paint(img.createGraphics());
    JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.home")));
    int c = chooser.showSaveDialog(graph);
    if (c == JFileChooser.CANCEL_OPTION) {
      return;
    }
    try {
      File f = chooser.getSelectedFile();
      ImageIO.write(img, "png", f);
      Desktop.getDesktop().open(f.getParentFile());
    }
    catch (IOException e1) {
      e1.printStackTrace();
    }
  }
}