/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.timeline;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.text.DecimalFormat;

/**
 * Class to compute the the appropriate number and spacing of tick marks
 */
public class TickMarkCalculator {

  private float actual_minx, actual_maxx;
  private float minx, maxx;
  private float mTickX;
  int ins_left = MEUI.scale(10), ins_right = MEUI.scale(15);
  int ins_top = 0, ins_botom = 0;
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

  public void calcRangeTicks(int canvasWidth) {
    mCanvasWidth = canvasWidth;
    double dx = actual_maxx - actual_minx;
    int sw = canvasWidth;
    double border = 1.09345; // small fudge factor

    mTickX = (float) calcTick(sw, dx);
    dx = mTickX * Math.ceil(border * dx / mTickX);
    double tx = (actual_minx + actual_maxx - dx) / 2;
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
    return 1 + (int) (0.5 + (maxx - minx) / mTickX);
  }

  public void calcTicks(int[] ticks) {
    int draw_width = mCanvasWidth - ins_left - ins_right;
    float e = 0.0001f * (maxx - minx);
    int tcount = 0;
    int count = getCount();
    for (int i = 0; i < count; i++) {
      float fx = mTickX * i;
      int ix = (int) (draw_width * fx / (maxx - minx) + ins_left);
      ticks[tcount++] = ix;
    }
  }

  public int floatToPosition(float value) {
    int draw_width = mCanvasWidth - ins_left - ins_right;
    int ix = (int) (draw_width * (value + minx) / (maxx - minx) + ins_left);
    return ix;
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
    int count = getCount();
    for (int x = 0; x < count; x++) {
      float fx = mTickX * x;
      int ix = (int) (draw_width * fx / (maxx - minx) + ins_left);
      ticks[tcount++] = ix;
      g.drawLine(ix, top + text_height, ix, h - ins_botom);
      String str = df.format(fx + minx);
      int sw = fm.stringWidth(str) / 2;
      g.drawString(str, ix - sw, ascent + top);
    }
    return tcount;
  }

  static double frac(double x) {
    return x - Math.floor(x);
  }
}
