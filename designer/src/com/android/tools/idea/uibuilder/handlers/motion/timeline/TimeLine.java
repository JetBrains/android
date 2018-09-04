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

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;

/**
 * This provides the tick marks on the top of the graph
 */
public class TimeLine extends JPanel {
  private static float TIMELINE_MIN = 0.0f;
  private static float TIMELINE_MAX = 100.0f;
  private final Chart myChart;
  private Tick tick = new Tick();

  TimeLine(Chart chart) {
    myChart = chart;
    tick.setRange(TIMELINE_MIN, TIMELINE_MAX);
  }

  public void setGraphWidth(int w) {
    tick.calcRangeTicks(w);
  }

  @Override
  protected void paintComponent(Graphics g) {
    // tick.calcRangeTicks(getWidth());
    int w = getWidth();
    int h = getHeight();
    Graphics2D g2d = (Graphics2D)g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                         RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(getBackground());
    g.fillRect(0, 0, w, h);
    g.setColor(getForeground());
    int n = tick.getCount();
    if (myChart.myXTicksPixels.length <= n) {
      myChart.myXTicksPixels = new int[n * 2];
    }
    myChart.myXTickCount = tick.paint(g2d, w, h, myChart.myXTicksPixels);
  }

  public void setInserts(int left, int right) {
    tick.setInsets(left, right, 0, 0);
  }

  static class Tick {

    private float actual_minx, actual_maxx;
    private float minx, maxx;
    private float mTickX;
    int ins_left, ins_right;
    int ins_top = 0, ins_botom = 0;
    int mTextGap = 2;
    int mCanvasWidth;
    DecimalFormat df = new DecimalFormat("###.##");
    int mGraphWidth;

    public void setInsets(int l, int r, int t, int b) {
      ins_botom = b;
      ins_left = l;
      ins_right = r;
      ins_top = t;
    }

    public void setRange(float min, float max) {
      actual_minx = min;
      actual_maxx = max;
    }

    void calcRangeTicks(int canvasWidth) {
      mCanvasWidth = canvasWidth;
      double dx = actual_maxx - actual_minx;
      int sw = canvasWidth;
      double border = 1.09345; // small fudge factor

      mTickX = (float)calcTick(sw, dx);
      dx = mTickX * Math.ceil(border * dx / mTickX);
      double tx = (actual_minx + actual_maxx - dx) / 2;
      //tx = myTickX * Math.floor(tx / myTickX);
      minx = actual_minx;
      tx = (actual_minx + actual_maxx + dx) / 2;
      tx = mTickX * Math.ceil(tx / mTickX);
      maxx = actual_maxx;
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

    public int getCount() {
      float e = 0.0001f * (maxx - minx);
      return (int)(0.5 + (maxx - minx) / mTickX);
    }

    public int paint(Graphics2D g, int w, int h, int[] ticks) {
      int draw_width = mCanvasWidth - ins_left - ins_right;
      float e = 0.0001f * (maxx - minx);
      FontMetrics fm = g.getFontMetrics();
      int ascent = fm.getAscent();
      int descent = fm.getDescent();
      int text_height = fm.getHeight();
      int top = text_height / 2;
      int tcount = 0;
      for (float i = minx; i <= maxx + e; i += mTickX) {
        int ix = (int)(draw_width * (i - minx) / (maxx - minx) + ins_left);
        ticks[tcount++] = ix;
        g.drawLine(ix, top + text_height, ix, h - ins_botom);
        String str = df.format(i);
        int sw = fm.stringWidth(str) / 2;

        g.drawString(str, ix - sw, ascent + top);
      }
      return tcount;
    }

    static double frac(double x) {
      return x - Math.floor(x);
    }
  }

  /**
   * Kept to test the graphing engine
   * @param arg
   */
  public static void main(String[] arg) {
    File dir = new File("");
    TimeLine p = new TimeLine(null);
    JFrame f = new JFrame("timeline");
    f.setContentPane(p);
    f.setBounds(100, 100, 1200, 800);
    f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    f.setVisible(true);
  }
}
