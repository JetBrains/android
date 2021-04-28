/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.ui.spring;

import static java.lang.Math.sqrt;

import com.android.tools.adtui.common.StudioColorsKt;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the graph to reflect the motion of onSwipe
 */
public class SpringWidget extends JPanel {
  private static final Color GRID_COLOR = StudioColorsKt.getSecondaryGraphLines();
  private static final Color GRID_TEXT_COLOR = StudioColorsKt.getGraphLabel();
  private static final Color MAIN_PLOT_COLOR = StudioColorsKt.getGraphLines();
  private static final Color CRITICAL_PLOT_COLOR = StudioColorsKt.getGraphLines();
  private static final Color ENVELOPE_PLOT_COLOR = StudioColorsKt.getGraphLines();
  private static final Color OVERLAY_TEXT_COLOR = StudioColorsKt.getGraphLines();
  private static final Color CONTROL_POINT_COLOR = StudioColorsKt.getGraphLines();
  private static final Color BALL_PLOT_COLOR = StudioColorsKt.getLinkForeground();

  float mDestination = 1;
  float myStiffness = 400;
  float myDamping = 0.5f;
  float myMass = 1f;
  float myThreshold = 0.001f;
  float myCriticallyDamping;
  float mInitialVelocity = 0;
  float mInitialPosition = 0f;
  float mApproxDuration = 0.f;
  float mFrequency = 0.f;
  boolean mBounce = false;
  float mInitialVelocityRange = 4;
  boolean mUseSpring = true;

  float mDuration = 2; // in Seconds
  float mMaxAcceleration = 2;
  float mMaxVelocity = 2;

  StopLogicEngine stopLogicEngine = new StopLogicEngine();
  plotInfo plotInfo = new plotInfo();
  ArrayList<ActionListener> listeners = new ArrayList<>();
  final static boolean ADVANCED = false;
  private float mSamplingInterval = 16 / 1000f;
  private BasicPlot mMainPlot;
  boolean animate = false;

  public void addActionListener(ActionListener actionListener) {
    listeners.add(actionListener);
  }

  interface DrawItem {
    public void paint(Graphics2D g, int w, int h);
  }

  ArrayList<DrawItem> baseDraw = new ArrayList<>();
  ArrayList<PlotItem> plotDraw = new ArrayList<>();

  void recalc() {
    plotDraw.clear();
    plotWave();
  }

  DrawItem stats = new DrawItem() {
    int x;
    int y;
    DecimalFormat df = new DecimalFormat("###.##");

    @Override
    public void paint(Graphics2D g, int w, int h) {
      y = plotInfo.ins_top + 30;
      x = w - 100;
      g.setColor(OVERLAY_TEXT_COLOR);
      right("Base freq: ", df.format(mFrequency) + "hz", g);
      right("Duration: ", df.format(mApproxDuration * 1000) + "ms", g);
      right("Critically Damping: ", df.format(myCriticallyDamping) + "  ", g);
    }

    void right(String str1, String str2, Graphics2D g) {
      Rectangle2D bounds = g.getFontMetrics().getStringBounds(str1, g);
      g.drawString(str1, x - (float)bounds.getWidth(), y);
      g.drawString(str2, x + 2, y);
      y += bounds.getHeight() + 7;
    }
  };

  void plotWave() {
    if (mUseSpring) {
      plotSpring();
    }
    else {
      plotDecay();
    }
  }

  void plotDecay() {
    float duration = mDuration;
    stopLogicEngine.config(mInitialPosition, mDestination, mInitialVelocity, duration, mMaxAcceleration, mMaxVelocity);
    float dt = 16 / 1000f; // seconds
    int len = (int)(duration / dt);
    float[] ya = new float[len];
    float[] xa = new float[len];
    float x = 0;
    for (int i = 0; i < xa.length; i++) {
      xa[i] = (x += dt);
      ya[i] = stopLogicEngine.getInterpolation(x);
    }
    plotDraw.add(mMainPlot = new BasicPlot(xa, ya, MAIN_PLOT_COLOR));
    plotInfo.calcRange(plotDraw);
    repaint();
  }

  void plotSpring() {
    double k = myStiffness;
    double c = myDamping;
    double m = myMass;
    mFrequency = (float)(sqrt(k / m) / (2 * Math.PI));

    double f = sqrt(k / m);
    //   addFunction(0, 20, Color.orange, (x) -> 0.5 * Math.sin(f * x));
    myCriticallyDamping = (float)(2 * sqrt(myMass * myStiffness));
    mApproxDuration = calcThreshold(mDestination, myStiffness, myDamping, myMass, myThreshold, 20f);
    if (ADVANCED) {
      plotWave(CRITICAL_PLOT_COLOR, mDestination, myStiffness, myCriticallyDamping, myMass, mApproxDuration);
      plotMax(ENVELOPE_PLOT_COLOR, mDestination, myStiffness, myDamping, myMass, mApproxDuration);
    }
    plotWave(MAIN_PLOT_COLOR, mDestination, myStiffness, myDamping, myMass, mApproxDuration);
  }

  float calcThreshold(double destination, double stiffness, double damping, double mass, double threashold, float maxDuration) {
    double k = stiffness;
    double c = damping;
    double m = mass;
    float dt = 16 / 1000f; // seconds
    float v0 = mInitialVelocity; // in one second it would go from 0 to 1;\
    float x0 = mInitialPosition;
    boolean positive = (destination > mInitialPosition);
    int len = (int)(maxDuration / dt);
    double pos;
    double x = x0;
    double v = v0;
    float t = 0;
    for (int i = 0; i < len; i++) {
      double dx = x - destination;
      double a = (-dx * k - v * c) / m;
      double vavg = v + a * dt / 2;
      double xavg = x + dt * (vavg) / 2 - destination;
      a = (-xavg * k - vavg * c) / m;
      x += dt * (v + a * dt / 2);
      v += a * dt;
      pos = (float)x;
      if (mBounce) {
        if (positive) {
          if (pos > destination) {
            pos = 2 * destination - pos;
            v = -v;
            x = 2 * destination - x;
          }
        }
        else {
          if (pos < destination) {
            pos = 2 * destination - pos;
            v = -v;
            x = 2 * destination - x;
          }
        }
      }
      t += dt;
      dx = x - destination;
      double energy_x_2 = dx * dx * k + m * v * v;
      double max_p = sqrt(energy_x_2 / k);
      if (max_p < threashold) {
        return t;
      }
    }
    return maxDuration;
  }

  void plotMax(Color color, double destination, double stiffness, double damping, double mass, float duration) {
    double k = stiffness;
    double c = damping;
    double m = mass;
    float dt = 4 / 1000f; // seconds
    float v0 = mInitialVelocity; // in one second it would go from 0 to 1;\
    float x0 = mInitialPosition;
    boolean positive = (destination > mInitialPosition);

    int len = (int)(duration / dt);
    float[] ya = new float[len];
    float[] xa = new float[len];

    double x = x0;
    double v = v0;
    float t = 0;
    for (int i = 0; i < xa.length; i++) {
      double dx = x - destination;
      double a = (-dx * k - v * c) / m;
      double vavg = v + a * dt / 2;
      double xavg = x + dt * (vavg) / 2 - destination;
      a = (-xavg * k - vavg * c) / m;
      x += dt * (v + a * dt / 2);
      v += a * dt;
      if (mBounce && x < 0) {
        v = -v;
        x = -x;
      }
      dx = x - destination;
      double energy_x_2 = dx * dx * k + m * v * v;
      double max_p = sqrt(energy_x_2 / k);
      ya[i] = (float)max_p;
      xa[i] = (t += dt);
    }

    plotDraw.add(new BasicPlot(xa, ya, color));
    plotInfo.calcRange(plotDraw);
    repaint();
  }

  void plotWave(Color color, double destination, double stiffness, double damping, double mass, float duration) {
    double k = stiffness;
    double c = damping;
    double m = mass;
    float dt = mSamplingInterval; // seconds
    float v0 = mInitialVelocity; // in one second it would go from 0 to 1;\
    float x0 = mInitialPosition;
    boolean positive = (destination > mInitialPosition);
    int len = (int)(duration / dt);
    float[] ya = new float[len];
    float[] xa = new float[len];
    double critical = Math.PI / (sqrt(myStiffness / myMass) * mSamplingInterval * 4);

    int n = (int)(1 + critical * 4);
    dt /= n;
    double x = x0;
    double v = v0;
    float t = 0;
    for (int i = 0; i < xa.length; i++) {
      for (int j = 0; j < n; j++) {

        double dx = x - destination;
        double a = (-dx * k - v * c) / m;
        double vavg = v + a * dt / 2;
        double xavg = x + dt * (vavg) / 2 - destination;
        a = (-xavg * k - vavg * c) / m;
        x += dt * (v + a * dt / 2);
        v += a * dt;
        if (mBounce) {
          if (positive) {
            if (x > destination) {
              x = 2 * destination - x;
              v = -v;
              if (i + 1 < xa.length) {
                ya[i] = (float)destination;
                xa[i] = t + dt / 2;
                i++;
              }
            }
          }
          else {
            if (x < destination) {
              x = 2 * destination - x;
              v = -v;
              if (i + 1 < xa.length) {
                ya[i] = (float)destination;
                xa[i] = t + dt / 2;
                i++;
              }
            }
          }
        }
      }
      ya[i] = (float)x;
      xa[i] = (t += dt * n);
    }

    plotDraw.add(mMainPlot = new BasicPlot(xa, ya, color));

    plotInfo.calcRange(plotDraw);
    repaint();
  }

  public SpringWidget() {

    baseDraw.add(clear);
    baseDraw.add(mAxis);
    baseDraw.add(mGrid);
    if (ADVANCED) {
      baseDraw.add(stats);
      baseDraw.add(mInitialVelocityCtl);

      MouseAdapter mouseAdapter = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {

        }

        @Override
        public void mouseReleased(MouseEvent e) {

        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          super.mouseWheelMoved(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
          float y = (e.getY() - plotInfo.ins_top) / (float)(getHeight() - plotInfo.ins_top - plotInfo.ins_bottom);
          y = (0.5f - y) * mInitialVelocityRange * 2;
          mInitialVelocity = Math.max(Math.min(y, mInitialVelocityRange), -mInitialVelocityRange);

          recalc();
        }
      };
      addMouseListener(mouseAdapter);
      addMouseMotionListener(mouseAdapter);
      addMouseWheelListener(mouseAdapter);
    }
    else {
      baseDraw.add(mAnimation);
      animate = true;
    }
  }

  DrawItem mAnimation = new DrawItem() {
    float pos;
    int stop;
    final int delay = 16;
    final int DECAY_FRAMES = 30;
    final Timer animate = new Timer(delay, (e) -> animate());

    void animate() {
      if (stop == 0) {
        animate.stop();
      }
      else if (stop < DECAY_FRAMES) {
        stop--;
      }
      pos += delay / (mDuration * 1000);
      if (pos > 1) {
        pos = 0;
      }
      repaint();
    }

    final MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        animate.start();
        stop = DECAY_FRAMES;
      }

      @Override
      public void mouseExited(MouseEvent e) {
        stop = DECAY_FRAMES - 1;
      }
    };

    {
      animate.setRepeats(true);
      addMouseListener(mouseAdapter);
    }

    @Override
    public void paint(Graphics2D g, int w, int h) {
      if (stop == 0 || mMainPlot == null) {
        return;
      }
      float[] xplot = mMainPlot.getX();
      float[] yplot = mMainPlot.getY();
      int index = (int)(xplot.length * pos);
      int y = (int)plotInfo.getY(h, yplot[index]);
      int x = (int)plotInfo.getX(w, xplot[index]);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Color color = BALL_PLOT_COLOR;
      if (stop < DECAY_FRAMES) {
        int alpha = (stop * 255) / DECAY_FRAMES;
        color = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
      }
      g.setColor(color);

      g.fillRoundRect(x - 3, y - 3, 6, 6, 6, 6);
      g.fillRoundRect(w - plotInfo.ins_right / 2 - 5, y - 5, 10, 10, 10, 10);
    }
  };
  /* ===========================================================*/

  DrawItem mInitialVelocityCtl = new DrawItem() {
    int half_size = 5;
    DecimalFormat df = new DecimalFormat("0.0");

    @Override
    public void paint(Graphics2D g, int w, int h) {
      g.setColor(CONTROL_POINT_COLOR);
      float ctl_range = h - plotInfo.ins_bottom - plotInfo.ins_top;
      int center = (int)(ctl_range / 2 + plotInfo.ins_top);
      float y = 1 - mInitialVelocity / mInitialVelocityRange;

      y *= ctl_range / 2;

      int ypoint = (int)(plotInfo.ins_top + y);
      g.fillRect(half_size + 1, center, 1, (int)(ypoint - center));
      int px = 1 + half_size;
      int py = ypoint;
      if (mInitialVelocity == 0) {
        g.fillRoundRect(1, ypoint - half_size, half_size * 2, half_size * 2, half_size * 2, half_size * 2);
      }
      else if (mInitialVelocity < 0) {
        g.drawLine(px, py, px - 5, py - 5);
        g.drawLine(px, py, px + 5, py - 5);
      }
      else {
        g.drawLine(px, py, px - 5, py + 5);
        g.drawLine(px, py, px + 5, py + 5);
      }
      String str = "v0:";
      Rectangle2D rec = g.getFontMetrics().getStringBounds(str, g);
      int lineHeight = (int)rec.getHeight();
      int yoff = (mInitialVelocity >= 0) ? -lineHeight * 2 + half_size : +half_size * 4;

      g.drawString(str, 1, ypoint + yoff);
      str = df.format(mInitialVelocity);
      yoff += lineHeight;
      g.drawString(str, 1, ypoint + yoff);
      // log(" mouse " + mInitialVelocity);

    }
  };

  /* ===========================================================*/

  public double getGraphX(float x) {
    int draw_width = getWidth() - plotInfo.ins_left - plotInfo.ins_right;
    return plotInfo.minx + (plotInfo.maxx - plotInfo.minx) * (x - plotInfo.ins_left) / draw_width;
  }

  public double getGraphY(float y) {
    int draw_height = getHeight() - plotInfo.ins_top - plotInfo.ins_bottom;
    return plotInfo.miny + 1 - (plotInfo.maxy - plotInfo.miny) * (y - plotInfo.ins_top) / draw_height;
  }

  static class plotInfo {
    boolean draw_axis = true;
    boolean draw_grid = true;
    int mTextGap = 2;
    int ins_top = 20;
    int ins_left = 25;
    int ins_bottom = 25;
    int ins_right = 20;
    float actual_miny;
    float actual_maxx;
    float actual_maxy;
    float actual_minx;
    private float last_minx;
    private float mTickY;
    private float mTickX;
    private float minx;
    private float last_maxx;
    private float maxx;
    private float maxy;
    private float miny;

    private float last_miny;
    private float last_maxy;

    float getY(int h, float y) {
      int draw_height = h - ins_top - ins_bottom;

      return draw_height
             * (1 - (y - miny) / (maxy - miny))
             + ins_top;
    }

    float getX(int w, float x) {
      int draw_width = w - ins_left - ins_right;

      return draw_width * (x - minx)
             / (maxx - minx) + ins_left;
    }

    void calcRangeTicks(int width, int height) {
      double dx = actual_maxx - actual_minx;
      double dy = actual_maxy - actual_miny;
      double border = 1.09345;

      if (Double.isInfinite(dx) || Double.isInfinite(dy)) {
        return;
      }
      mTickX = (float)calcTick(width, dx);
      dx = mTickX * Math.ceil(border * dx / mTickX);
      double tx = (actual_minx + actual_maxx - dx) / 2;
      tx = mTickX * Math.floor(tx / mTickX);
      minx = 0;//(float) tx;
      tx = (actual_minx + actual_maxx + dx) / 2;
      tx = mTickX * Math.ceil(tx / mTickX);
      maxx = (float)tx;

      last_minx = actual_minx;
      last_maxx = actual_maxx;

      mTickY = (float)calcTick(height, dy);
      dy = mTickY * Math.ceil(border * dy / mTickY);
      double ty = (actual_miny + actual_maxy - dy) / 2;
      ty = mTickY * Math.floor(ty / mTickY);
      miny = 0;//(float) ty;
      ty = (actual_miny + actual_maxy + dy) / 2;
      ty = mTickY * Math.ceil(ty / mTickY);
      maxy = (float)ty;

      last_miny = actual_miny;
      last_maxy = actual_maxy;
    }

    private static double frac(double x) {
      return x - Math.floor(x);
    }

    static public double calcTick(int scr, double range) {

      int aprox_x_ticks = scr / 200;
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

    public void setRange(float minx, float maxx, float miny, float maxy) {
      actual_maxx = maxx;
      actual_maxy = maxy;
      actual_minx = minx;
      actual_miny = miny;
    }

    public void calcRange(ArrayList<PlotItem> plotDraw) {
      resetRange();

      for (PlotItem plotItem : plotDraw) {
        measure(plotItem.getX(), plotItem.getY());
      }
      setRange(minx, maxx, miny, maxy);
    }

    public void resetRange() {
      minx = Float.MAX_VALUE;
      miny = Float.MAX_VALUE;
      maxx = -Float.MAX_VALUE;
      maxy = -Float.MAX_VALUE;
    }

    void measure(float[] xPoints, float[] yPoints) {
      if (xPoints == null | yPoints == null) {
        return;
      }
      for (int i = 0; i < xPoints.length; i++) {
        float x = xPoints[i];
        float y = yPoints[i];
        minx = Math.min(minx, x);
        miny = Math.min(miny, y);
        maxx = Math.max(maxx, x);
        maxy = Math.max(maxy, y);
      }
      if (Float.isInfinite(miny)) {
        miny = -0.2f;
        log("!");
      }
      if (Float.isInfinite(maxy)) {
        maxy = 1.2f;
        log("!");
      }
    }
  }

  DrawItem mAxis = new DrawItem() {
    Color drawing = new Color(0xAAAAAA);
    Color mGridColor = new Color(0x49A087);

    @Override
    public void paint(Graphics2D g, int w, int h) {
      if (!plotInfo.draw_axis) {
        return;
      }
      g.setColor(drawing);
      g.setColor(mGridColor);

      g.drawLine(plotInfo.ins_left, plotInfo.ins_top, plotInfo.ins_left, h - plotInfo.ins_bottom);
      g.drawLine(plotInfo.ins_left, h - plotInfo.ins_bottom, w - plotInfo.ins_right, h - plotInfo.ins_bottom);
    }
  };

  DrawItem mGrid = new DrawItem() {
    Color mGridColor = GRID_COLOR;
    Color mGridTextColor = GRID_TEXT_COLOR;
    private float mPeriodMultiplier = 1;

    DecimalFormat df = new DecimalFormat("##0.0");

    @Override
    public void paint(Graphics2D g, int w, int h) {
      if (!plotInfo.draw_grid) {
        return;
      }
      g.setColor(mGridColor);
      int draw_width = w - plotInfo.ins_left - plotInfo.ins_right;
      float e = 0.0001f * (plotInfo.maxx - plotInfo.minx);
      FontMetrics fm = g.getFontMetrics();
      int ascent = fm.getAscent();
      if (plotInfo.mTickX > 0) {
        for (float i = plotInfo.minx + plotInfo.mTickX; i <= plotInfo.maxx - plotInfo.mTickX + e; i += plotInfo.mTickX) {
          int ix = (int)(draw_width * (i - plotInfo.minx) / (plotInfo.maxx - plotInfo.minx) + plotInfo.ins_left);
          String str = df.format(i);
          int sw = fm.stringWidth(str) / 2;
          g.setColor(mGridTextColor);

          g.drawString(str, ix - sw, h - plotInfo.ins_bottom + ascent + plotInfo.mTextGap);
        }
      }
      g.setColor(mGridColor);

      for (float i = plotInfo.minx; i <= plotInfo.maxx + e; i += plotInfo.mTickX) {
        int ix = (int)(draw_width * (i - plotInfo.minx) / (plotInfo.maxx - plotInfo.minx) + plotInfo.ins_left);
        g.setColor(mGridColor);

        g.drawLine(ix, plotInfo.ins_top, ix, h - plotInfo.ins_bottom);
      }
      int draw_height = h - plotInfo.ins_top - plotInfo.ins_bottom;
      e = 0.0001f * (plotInfo.maxy - plotInfo.miny);
      int hightoff = -fm.getHeight() / 2 + ascent;
      int y0 = (int)(draw_height * (1 - (0 - plotInfo.miny) / (plotInfo.maxy - plotInfo.miny)) + plotInfo.ins_top);

      for (float i = plotInfo.miny; i <= plotInfo.maxy + e; i += plotInfo.mTickY) {
        int iy = (int)(draw_height * (1 - (i - plotInfo.miny) / (plotInfo.maxy - plotInfo.miny)) + plotInfo.ins_top);
        g.setColor(mGridColor);

        g.drawLine(plotInfo.ins_left, iy, w - plotInfo.ins_right, iy);
        String str = df.format(i);

        int sw = fm.stringWidth(str);

        g.setColor(mGridTextColor);
        g.drawString(str, plotInfo.ins_left - sw - plotInfo.mTextGap, iy + hightoff);
      }
    }
  };
  DrawItem clear = new DrawItem() {
    @Override
    public void paint(Graphics2D g, int w, int h) {
      g.setColor(getBackground());
      g.fillRect(0, 0, w, h);
    }
  };

  @Override
  protected void paintComponent(Graphics g) {
    int w = getWidth();
    int h = getHeight();
    plotInfo.calcRangeTicks(w, h);
    Graphics2D g2d = (Graphics2D)g;
    for (DrawItem drawItem : baseDraw) {
      drawItem.paint(g2d, w, h);
    }
    for (DrawItem drawItem : plotDraw) {
      drawItem.paint(g2d, w, h);
    }
  }

  public void addBasicPlot(float[] x, float[] y, Color c) {
    plotDraw.add(new BasicPlot(x, y, c));
    plotInfo.calcRange(plotDraw);
    repaint();
  }

  interface Function {
    double f(double x);
  }

  public void setFunction(double minx, double maxx, Color c, Function f) {
    float[] x = new float[100];
    float[] y = new float[x.length];
    double last = 0;
    for (int i = 0; i < x.length; i++) {
      double in = minx + maxx * (i / (double)(x.length - 1));
      x[i] = (float)in;
      double value = (float)f.f(in);
      if (Double.isNaN(value) || Double.isInfinite(value)) {
        value = (float)f.f(in + 0.000001);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
          value = last;
        }
      }
      y[i] = (float)value;
      last = value;
    }
    plotDraw.clear();
    plotDraw.add(new BasicPlot(x, y, c));
    plotInfo.calcRange(plotDraw);
    repaint();
  }

  public void addFunction(double minx, double maxx, Color c, Function f) {
    float[] x = new float[512];
    float[] y = new float[512];
    double last = 0;
    for (int i = 0; i < x.length; i++) {
      double in = minx + maxx * (i / (double)(x.length - 1));
      x[i] = (float)in;
      double value = (float)f.f(in);
      if (Double.isNaN(value) || Double.isInfinite(value)) {
        value = (float)f.f(in + 0.000001);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
          value = last;
        }
      }
      y[i] = (float)value;
      last = value;
    }
    plotDraw.add(new BasicPlot(x, y, c));
    plotInfo.calcRange(plotDraw);
    repaint();
  }

  interface PlotItem extends DrawItem {
    float[] getX();

    float[] getY();
  }

  class BasicPlot implements PlotItem {
    private float old_min_draw_y;
    private int[] tmpX = new int[0];
    private int[] tmpY = new int[0];
    int[] xp = new int[0];
    int[] yp = new int[0];
    Color color = Color.BLACK;
    Stroke stroke = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    float[] xPoints;
    float[] yPoints;

    public float[] getX() {
      return xPoints;
    }

    public float[] getY() {
      return yPoints;
    }

    BasicPlot(float[] x, float[] y, Color c) {
      color = c;
      xPoints = x;
      yPoints = y;
    }

    @Override
    public void paint(Graphics2D g, int w, int h) {

      if (xPoints.length == 0) {
        return;
      }
      int draw_width = w - plotInfo.ins_left - plotInfo.ins_right;
      int draw_height = h - plotInfo.ins_top - plotInfo.ins_bottom;

      if (xp.length < xPoints.length * 2) {
        xp = new int[xPoints.length * 2];
        yp = new int[xPoints.length * 2];
        tmpX = new int[xPoints.length * 2 + 2];
        tmpY = new int[xPoints.length * 2 + 2];
      }
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(color);
      g.setStroke(stroke);
      float y0 = draw_height
                 * (1 - (0 - plotInfo.miny) / (plotInfo.maxy - plotInfo.miny))
                 + plotInfo.ins_top;

      for (int i = 0; i < xPoints.length; i++) {
        float x = draw_width * (xPoints[i] - plotInfo.minx)
                  / (plotInfo.maxx - plotInfo.minx) + plotInfo.ins_left;
        float y = draw_height
                  * (1 - (yPoints[i] - plotInfo.miny) / (plotInfo.maxy - plotInfo.miny))
                  + plotInfo.ins_top;

        xp[i] = (int)x;
        yp[i] = (int)y;
      }

      g.drawPolyline(xp, yp, xPoints.length);
    }
  }

  static SpringWidget setupFrame(JFrame frame) {
    SpringWidget p = new SpringWidget();
    frame.setContentPane(p);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setBounds(100, 100, 800, 600);

    return p;
  }

  static SpringWidget plot(double minx, double maxx, Color c, Function f) {
    JFrame frame;
    SpringWidget p = setupFrame(frame = new JFrame("Graph"));
    p.addFunction(minx, maxx, c, f);
    frame.setVisible(true);
    return p;
  }

  static SpringWidget plot(double minx, double maxx, Function... f) {
    JFrame frame;
    SpringWidget p = setupFrame(frame = new JFrame("Graph"));
    ColorGen colorGen = new ColorGen();
    for (Function function : f) {
      p.addFunction(minx, maxx, colorGen.getColor(), function);
    }
    frame.setVisible(true);
    return p;
  }

  static class ColorGen {
    double sx, sy, count = 0;

    Color getColor() {
      double angle;
      float hue;
      if (count < 1) {
        hue = (float)Math.random();
        angle = hue * Math.PI * 2;
      }
      else {
        angle = Math.toRadians(180) + Math.atan2(sy, sx);
        hue = (float)(angle / (Math.PI * 2));
      }
      sx += Math.cos(angle);
      sy += Math.sin(angle);
      count++;
      return Color.getHSBColor(hue, 0.9f, 0.8f);
    }
  }

  public float getStiffness() {
    return myStiffness;
  }

  public void setStiffness(float stiffness) {
    myStiffness = stiffness;
    recalc();
  }

  public float getDamping() {
    return myDamping;
  }

  public void setDamping(float damping) {
    myDamping = damping;
    recalc();
  }

  public float getMass() {
    return myMass;
  }

  public void setMass(float mass) {
    myMass = mass;
    recalc();
  }

  public float getThreshold() {
    return myThreshold;
  }

  public void setThreshold(float threshold) {
    myThreshold = threshold;
    recalc();
  }

  public boolean isBounce() {
    return mBounce;
  }

  public void setBounce(boolean bounce) {
    mBounce = bounce;
    recalc();
  }

  public boolean isUseSpring() {
    return mUseSpring;
  }

  public void setUseSpring(boolean useSpring) {
    mUseSpring = useSpring;
    recalc();
  }

  public void setMode(SpringMode mode) {
    switch (mode) {
      case NORMAL:
        mUseSpring = false;
        break;
      case SPRING_WITH_DAMP_CONSTANT:
        mUseSpring = true;
        break;
      case SPRING_WITH_DAMP_RATIO:
        mUseSpring = true;
        myMass = 1f;
        break;
    }
    recalc();
  }

  public float getDuration() {
    return mDuration;
  }

  public void setDuration(float duration) {
    mDuration = duration;
    recalc();
  }

  public float getMaxAcceleration() {
    return mMaxAcceleration;
  }

  public void setMaxAcceleration(float maxAcceleration) {
    mMaxAcceleration = maxAcceleration;
    recalc();
  }

  public float getMaxVelocity() {
    return mMaxVelocity;
  }

  public void setMaxVelocity(float maxVelocity) {
    mMaxVelocity = maxVelocity;
    recalc();
  }

  static public class StopLogicEngine {
    private float mStage1Velocity, mStage2Velocity, mStage3Velocity; // the velocity at the start of each period
    private float mStage1Duration, mStage2Duration, mStage3Duration; // the time for each period
    private float mStage1EndPosition, mStage2EndPosition, mStage3EndPosition; // ending position
    private int mNumberOfStages;
    private String mType;
    private boolean mBackwards = false;
    private float mStartPosition;
    private float mLastPosition;
    private boolean mDone = false;
    private static final float EPSILON = 0.00001f;

    /**
     * Debugging logic to log the state.
     *
     * @param desc Description to pre append
     * @param time Time during animation
     * @return string useful for debugging the state of the StopLogic
     */
    public String debug(String desc, float time) {
      String ret = desc + " ===== " + mType + "\n";
      ret += desc + (mBackwards ? "backwards" : "forward ") + " time = " + time + "  stages " + mNumberOfStages + "\n";
      ret += desc + " dur " + mStage1Duration + " vel " + mStage1Velocity + " pos " + mStage1EndPosition + "\n";

      if (mNumberOfStages > 1) {
        ret += desc + " dur " + mStage2Duration + " vel " + mStage2Velocity + " pos " + mStage2EndPosition + "\n";
      }
      if (mNumberOfStages > 2) {
        ret += desc + " dur " + mStage3Duration + " vel " + mStage3Velocity + " pos " + mStage3EndPosition + "\n";
      }

      if (time <= mStage1Duration) {
        ret += desc + "stage 0" + "\n";
        return ret;
      }
      if (mNumberOfStages == 1) {
        ret += desc + "end stage 0" + "\n";
        return ret;
      }
      time -= mStage1Duration;
      if (time < mStage2Duration) {

        ret += desc + " stage 1" + "\n";
        return ret;
      }
      if (mNumberOfStages == 2) {
        ret += desc + "end stage 1" + "\n";
        return ret;
      }
      time -= mStage2Duration;
      if (time < mStage3Duration) {

        ret += desc + " stage 2" + "\n";
        return ret;
      }
      ret += desc + " end stage 2" + "\n";
      return ret;
    }

    public float getVelocity(float x) {
      if (x <= mStage1Duration) {
        return mStage1Velocity + (mStage2Velocity - mStage1Velocity) * x / (mStage1Duration);
      }
      if (mNumberOfStages == 1) {
        return 0;
      }
      x -= mStage1Duration;
      if (x < mStage2Duration) {

        return mStage2Velocity + (mStage3Velocity - mStage2Velocity) * x / (mStage2Duration);
      }
      if (mNumberOfStages == 2) {
        return mStage2EndPosition;
      }
      x -= mStage2Duration;
      if (x < mStage3Duration) {

        return mStage3Velocity - mStage3Velocity * x / (mStage3Duration);
      }
      return mStage3EndPosition;
    }

    private float calcY(float time) {
      mDone = false;
      if (time <= mStage1Duration) {
        return mStage1Velocity * time + (mStage2Velocity - mStage1Velocity) * time * time / (2 * mStage1Duration);
      }
      if (mNumberOfStages == 1) {
        return mStage1EndPosition;
      }
      time -= mStage1Duration;
      if (time < mStage2Duration) {

        return mStage1EndPosition + mStage2Velocity * time + (mStage3Velocity - mStage2Velocity) * time * time / (2 * mStage2Duration);
      }
      if (mNumberOfStages == 2) {
        return mStage2EndPosition;
      }
      time -= mStage2Duration;
      if (time <= mStage3Duration) {

        return mStage2EndPosition + mStage3Velocity * time - mStage3Velocity * time * time / (2 * mStage3Duration);
      }
      mDone = true;
      return mStage3EndPosition;
    }

    public void config(float currentPos, float destination, float currentVelocity,
                       float maxTime, float maxAcceleration, float maxVelocity) {
      mDone = false;
      mStartPosition = currentPos;
      mBackwards = (currentPos > destination);
      if (mBackwards) {
        setup(-currentVelocity, currentPos - destination, maxAcceleration, maxVelocity, maxTime);
      }
      else {
        setup(currentVelocity, destination - currentPos, maxAcceleration, maxVelocity, maxTime);
      }
    }

    public float getInterpolation(float v) {
      float y = calcY(v);
      mLastPosition = v;
      return (mBackwards) ? mStartPosition - y : mStartPosition + y;
    }

    public float getVelocity() {
      return (mBackwards) ? -getVelocity(mLastPosition) : getVelocity(mLastPosition);
    }

    public boolean isStopped() {
      return getVelocity() < EPSILON && Math.abs(mStage3EndPosition - mLastPosition) < EPSILON;
    }

    private void setup(float velocity, float distance, float maxAcceleration, float maxVelocity,
                       float maxTime) {
      mDone = false;
      if (velocity == 0) {
        velocity = 0.0001f;
      }
      this.mStage1Velocity = velocity;
      float min_time_to_stop = velocity / maxAcceleration;
      float stopDistance = min_time_to_stop * velocity / 2;

      if (velocity < 0) { // backward
        float timeToZeroVelocity = (-velocity) / maxAcceleration;
        float reversDistanceTraveled = timeToZeroVelocity * velocity / 2;
        float totalDistance = distance - reversDistanceTraveled;
        float peak_v = (float)sqrt(maxAcceleration * totalDistance);
        if (peak_v < maxVelocity) { // accelerate then decelerate
          mType = "backward accelerate, decelerate";
          this.mNumberOfStages = 2;
          this.mStage1Velocity = velocity;
          this.mStage2Velocity = peak_v;
          this.mStage3Velocity = 0;
          this.mStage1Duration = (peak_v - velocity) / maxAcceleration;
          this.mStage2Duration = peak_v / maxAcceleration;
          this.mStage1EndPosition = (velocity + peak_v) * this.mStage1Duration / 2;
          this.mStage2EndPosition = distance;
          this.mStage3EndPosition = distance;
          return;
        }
        mType = "backward accelerate cruse decelerate";
        this.mNumberOfStages = 3;
        this.mStage1Velocity = velocity;
        this.mStage2Velocity = maxVelocity;
        this.mStage3Velocity = maxVelocity;

        this.mStage1Duration = (maxVelocity - velocity) / maxAcceleration;
        this.mStage3Duration = maxVelocity / maxAcceleration;
        float accDist = (velocity + maxVelocity) * this.mStage1Duration / 2;
        float decDist = (maxVelocity * this.mStage3Duration) / 2;
        this.mStage2Duration = (distance - accDist - decDist) / maxVelocity;
        this.mStage1EndPosition = accDist;
        this.mStage2EndPosition = (distance - decDist);
        this.mStage3EndPosition = distance;
        return;
      }

      if (stopDistance >= distance) { // we cannot make it hit the breaks.
        // we do a force hard stop
        mType = "hard stop";
        float time = 2 * distance / velocity;
        this.mNumberOfStages = 1;
        this.mStage1Velocity = velocity;
        this.mStage2Velocity = 0;
        this.mStage1EndPosition = distance;
        this.mStage1Duration = time;
        return;
      }

      float distance_before_break = distance - stopDistance;
      float cruseTime = distance_before_break / velocity; // do we just Cruse then stop?
      if (cruseTime + min_time_to_stop < maxTime) { // close enough maintain v then break
        mType = "cruse decelerate";
        this.mNumberOfStages = 2;
        this.mStage1Velocity = velocity;
        this.mStage2Velocity = velocity;
        this.mStage3Velocity = 0;
        this.mStage1EndPosition = distance_before_break;
        this.mStage2EndPosition = distance;
        this.mStage1Duration = cruseTime;
        this.mStage2Duration = velocity / maxAcceleration;
        return;
      }

      float peak_v = (float)sqrt(maxAcceleration * distance + velocity * velocity / 2);
      this.mStage1Duration = (peak_v - velocity) / maxAcceleration;
      this.mStage2Duration = peak_v / maxAcceleration;
      if (peak_v < maxVelocity) { // accelerate then decelerate
        mType = "accelerate decelerate";
        this.mNumberOfStages = 2;
        this.mStage1Velocity = velocity;
        this.mStage2Velocity = peak_v;
        this.mStage3Velocity = 0;
        this.mStage1Duration = (peak_v - velocity) / maxAcceleration;
        this.mStage2Duration = peak_v / maxAcceleration;
        this.mStage1EndPosition = (velocity + peak_v) * this.mStage1Duration / 2;
        this.mStage2EndPosition = distance;

        return;
      }
      mType = "accelerate cruse decelerate";
      // accelerate, cruse then decelerate
      this.mNumberOfStages = 3;
      this.mStage1Velocity = velocity;
      this.mStage2Velocity = maxVelocity;
      this.mStage3Velocity = maxVelocity;

      this.mStage1Duration = (maxVelocity - velocity) / maxAcceleration;
      this.mStage3Duration = maxVelocity / maxAcceleration;
      float accDist = (velocity + maxVelocity) * this.mStage1Duration / 2;
      float decDist = (maxVelocity * this.mStage3Duration) / 2;

      this.mStage2Duration = (distance - accDist - decDist) / maxVelocity;
      this.mStage1EndPosition = accDist;
      this.mStage2EndPosition = (distance - decDist);
      this.mStage3EndPosition = distance;
    }
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setBackground(StudioColorsKt.getSecondaryPanelBackground());
  }

  /**
   * This is an overall ui panel which contains the graph, sliders etc.
   * The sliders are configured below.
   * Because swing sliders are integer based a factor (mScale) is used to rescale to a reasonable range.
   * <p>
   * The values read form the MotionScene Transition are loaded into the inner class Parameters
   * Float.NaN is used to indicate the attribute is not in the array
   */
  static class mainUI extends JPanel {
    ArrayList<JComponent> components = new ArrayList<>();
    SpringWidget myGraph = new SpringWidget();
    SpringParameter[] allParameters;
    String spacing = "XXXXXXXXX";
    JPanel mainControlsPanel = new JPanel(new BorderLayout());
    JPanel topPanel = new JPanel(new BorderLayout());
    ButtonGroup group = new ButtonGroup();
    JPanel modeSelectorPanel = new JPanel();
    CardLayout cardLayout = new CardLayout();
    JPanel ctlCards = new JPanel(cardLayout);
    HashMap<SpringMode, JPanel> modeToControls = new HashMap<>();
    HashMap<SpringParameter, JSlider> parameterToSlider = new HashMap<>();
    Boolean isUpdatingFromModel;

    mainUI(SpringWidgetModel model) {
      super(new BorderLayout());
      allParameters = getAllParameters(model);
      isUpdatingFromModel = true;
      initializeGraph(model);
      initializeControls(model);
      isUpdatingFromModel = false;
      model.addListener(new MyChangeListener(() -> updateWidget(model)));
    }

    @SuppressWarnings("UnstableApiUsage")
    private void updateModel(SpringParameter parameter, SpringWidgetModel model, float value) {
      Application application = ApplicationManager.getApplication();
      String valueString = Float.toString(value);
      application.invokeLaterOnWriteThread(() -> model.setValue(parameter, valueString));
    }

    @Override
    public void updateUI() {
      super.updateUI();
      if (components != null) {
        for (JComponent component : components) {
          component.setBackground(StudioColorsKt.getSecondaryPanelBackground());
        }
      }
      setBackground(StudioColorsKt.getSecondaryPanelBackground());
    }

    private void initializeGraph(SpringWidgetModel model) {
      HashMap<SpringParameter, Float> valuesInModel = new HashMap<>();
      for (SpringParameter parameter: allParameters) {
        Float parameterValue = StringsKt.toFloatOrNull(model.getValue(parameter));
        valuesInModel.put(parameter, parameterValue != null ? parameterValue : 1f);
      }
      updateAllGraph(valuesInModel);
    }

    private void initializeControls(SpringWidgetModel model) {
      SpringMode initialMode = model.getStartingMode() != null ? model.getStartingMode() : model.getSupportedModes()[0];
      JPanel panel = this;
      panel.add(myGraph);
      components.add(mainControlsPanel);
      components.add(topPanel);
      components.add(modeSelectorPanel);
      topPanel.add(modeSelectorPanel);
      mainControlsPanel.add(topPanel, BorderLayout.NORTH);
      panel.add(mainControlsPanel, BorderLayout.SOUTH);
      components.add(ctlCards);
      mainControlsPanel.add(ctlCards);
      myGraph.setMode(initialMode);
      addModeSelector(model.getSupportedModes(), initialMode);
      for (SpringMode mode: model.getSupportedModes()) {
        addControlsForMode(mode, model);
      }
      cardLayout.show(ctlCards, initialMode.displayName);
      myGraph.plotWave();
      panel.setPreferredSize(new Dimension(300, 430));
      panel.setMinimumSize(new Dimension(300, 430));
      panel.setSize(new Dimension(300, 430));
      panel.setBackground(StudioColorsKt.getSecondaryPanelBackground());
      for (JComponent component : components) {
        component.setBackground(StudioColorsKt.getSecondaryPanelBackground());
      }
    }

    private void addModeSelector(SpringMode[] supportedModes, SpringMode defaultMode) {
      if (supportedModes.length <= 1) return;
      ActionListener actionListener = (e) -> {
        Object source = e.getSource();
        if (!(source instanceof JRadioButton)) {
          return;
        }
        if (!((JRadioButton)source).isSelected()) {
          return;
        }
        String selectedModeName = ((JRadioButton)source).getName();
        SpringMode selectedMode = getMatchingMode(selectedModeName);
        myGraph.setMode(selectedMode);
        cardLayout.show(ctlCards, selectedModeName);
        modeToControls.forEach((mode, panel) -> {
          boolean isModeSelected = mode.equals(selectedMode);
          for (Component component: panel.getComponents()) {
            component.setEnabled(isModeSelected);
          }
        });
      };
      for (SpringMode mode: supportedModes) {
        JRadioButton radioButton = new JRadioButton(mode.displayName);
        radioButton.setName(mode.displayName);
        if (mode.equals(defaultMode)) {
          radioButton.setSelected(true);
        }
        radioButton.addActionListener(actionListener);
        group.add(radioButton);
        modeSelectorPanel.add(radioButton);
        components.add(radioButton);
      }
      components.add(modeSelectorPanel);
    }

    private void addControlsForMode(SpringMode mode, SpringWidgetModel model) {
      Collection<SpringParameter> parametersToAdd = mode.parameters;
      JPanel controlsPanel = new JPanel(new GridLayout(parametersToAdd.size() + 1, 1));
      for (SpringParameter parameter: parametersToAdd) {
        if (isUseSlider(parameter)) {
          JPanel line = new JPanel(new BorderLayout());
          JLabel title = new JLabel(spacing);
          title.setPreferredSize(title.getPreferredSize());
          title.setText(parameter.displayName);
          title.setHorizontalAlignment(JLabel.RIGHT);
          SliderSpec sliderSpec = getSliderSpec(parameter);
          if (sliderSpec == null) {
            continue;
          }
          int initialValue = (int)(getGraphValue(parameter) * sliderSpec.scale);
          JSlider slider = new JSlider(sliderSpec.sliderMin, sliderSpec.sliderMax);
          slider.setOpaque(false);
          JLabel value = new JLabel("XXXXXXX");
          value.setOpaque(false);
          value.setPreferredSize(value.getPreferredSize());
          value.setText("" + (initialValue * sliderSpec.scale));
          line.add(title, BorderLayout.WEST);
          line.add(value, BorderLayout.EAST);
          float rescale = sliderSpec.scale;
          slider.addChangeListener(event -> {
            float f = slider.getValue() / rescale;
            f = ((int)(f * 10000)) / 10000f;
            value.setText(Float.toString(f));
            updateGraph(parameter, f, mode.equals(SpringMode.SPRING_WITH_DAMP_RATIO));
            if (!slider.getValueIsAdjusting() && !isUpdatingFromModel) {
              //if (parameter.equals(SpringParameter.DURATION)) {
              //  f *= 1000;
              //}
              updateModel(parameter, model, f);
            }
          });
          line.add(slider);
          slider.setValue(initialValue);
          parameterToSlider.put(parameter, slider);
          components.add(slider);
          components.add(value);
          components.add(line);
          components.add(title);
          controlsPanel.add(line);
        }
        else {
          // TODO: Add controls for Boundary
        }
      }
      if (!mode.equals(SpringMode.NORMAL)) {
        JPanel line = new JPanel(new BorderLayout());
        JLabel title = new JLabel(spacing);
        title.setPreferredSize(title.getPreferredSize());
        title.setText("Bounce");
        title.setHorizontalAlignment(JLabel.RIGHT);
        JCheckBox checkBox = new JCheckBox();
        components.add(title);
        components.add(checkBox);
        components.add(line);
        checkBox.addActionListener(e -> myGraph.setBounce(checkBox.isSelected()));
        line.add(title, BorderLayout.WEST);
        line.add(checkBox);
        controlsPanel.add(line);
      }
      components.add(controlsPanel);
      ctlCards.add(controlsPanel, mode.displayName);
    }

    private float getGraphValue(SpringParameter parameter) {
      switch (parameter) {
        case MAX_ACC:
          return myGraph.getMaxAcceleration();
        case MAX_VEL:
          return myGraph.getMaxVelocity();
        case DURATION:
          return myGraph.getDuration() * 1000f;
        case MASS:
          return myGraph.getMass();
        case DAMPING:
          return myGraph.getDamping();
        case STIFFNESS:
          return myGraph.getStiffness();
        case THRESHOLD:
          return myGraph.getThreshold();
        case DAMPING_RATIO:
          float stiffness = myGraph.getStiffness();
          float damping = myGraph.getDamping();
          return damping / (2f * (float)sqrt(stiffness));
        case BOUNDARY:
      }
      return 1f;
    }

    private void updateGraph(SpringParameter parameter, float value, boolean usesRatio) {
      switch (parameter) {
        case MAX_ACC:
          myGraph.setMaxAcceleration(value);
          break;
        case MAX_VEL:
          myGraph.setMaxVelocity(value);
          break;
        case DURATION:
          myGraph.setDuration(value / 1000f);
          break;
        case MASS:
          myGraph.setMass(value);
          break;
        case DAMPING:
          myGraph.setDamping(value);
          break;
        case STIFFNESS:
          if (!usesRatio) {
            myGraph.setStiffness(value);
          }
          else {
            float oldStiffness = myGraph.getStiffness();
            float oldDamping = myGraph.getDamping();
            float dampingRatio = oldDamping / ( 2 * (float)sqrt(oldStiffness) );
            float newDamping = 2f * (float)sqrt(value) * dampingRatio;
            myGraph.setStiffness(value);
            myGraph.setDamping(newDamping);
          }
          break;
        case THRESHOLD:
          myGraph.setThreshold(value);
          break;
        case DAMPING_RATIO:
          float stiffness = myGraph.getStiffness();
          myGraph.setMass(1f);
          myGraph.setDamping(2f * (float)sqrt(stiffness) * value);
          break;
        case BOUNDARY:
      }
    }

    private void updateAllGraph(HashMap<SpringParameter, Float> parametersValues) {
      for (SpringParameter parameter: parametersValues.keySet()) {
        switch (parameter) {
          case MAX_ACC:
            myGraph.setMaxAcceleration(parametersValues.get(parameter));
            continue;
          case MAX_VEL:
            myGraph.setMaxVelocity(parametersValues.get(parameter));
            continue;
          case DURATION:
            myGraph.setDuration(parametersValues.get(parameter) / 1000f);
            continue;
          case MASS:
            myGraph.setMass(parametersValues.get(parameter));
            continue;
          case DAMPING:
            myGraph.setDamping(parametersValues.get(parameter));
            continue;
          case STIFFNESS:
            if (!parametersValues.containsKey(SpringParameter.DAMPING_RATIO)) {
              float stiffness = parametersValues.get(SpringParameter.STIFFNESS);
              myGraph.setStiffness(stiffness);
            }
            else {
              float stiffness = parametersValues.get(SpringParameter.STIFFNESS);
              float dampingRatio = parametersValues.get(SpringParameter.DAMPING_RATIO);
              myGraph.setMass(1f);
              myGraph.setDamping(2f * (float)sqrt(stiffness) * dampingRatio);
              myGraph.setStiffness(stiffness);
            }
            continue;
          case THRESHOLD:
            myGraph.setThreshold(parametersValues.get(parameter));
            continue;
          case DAMPING_RATIO:
            float stiffness = parametersValues.get(SpringParameter.STIFFNESS);
            float dampingRatio = parametersValues.get(SpringParameter.DAMPING_RATIO);
            myGraph.setMass(1f);
            myGraph.setDamping(2f * (float)sqrt(stiffness) * dampingRatio);
            myGraph.setStiffness(stiffness);
            continue;
          case BOUNDARY:
        }
      }
    }

    private void updateWidget(SpringWidgetModel model) {
      isUpdatingFromModel = true;
      for (SpringParameter parameter: allParameters) {
        String valueString = model.getValue(parameter);
        Float value = StringsKt.toFloatOrNull(valueString);
        if (value != null) {
          JSlider slider = parameterToSlider.get(parameter);
          if (slider != null) {
            SliderSpec sliderSpec = getSliderSpec(parameter);
            if (sliderSpec != null) {
              slider.setValue((int)(value * sliderSpec.scale));
            }
          }
        }
      }
      isUpdatingFromModel = false;
    }
  }

  /**
   * @param model
   * @return
   */
  public static JPanel panelWithUI(SpringWidgetModel model) {
    return new mainUI(model);
  }

  private static class MyChangeListener implements SpringModelChangeListener {
    Runnable modelChangedCallback;

    public MyChangeListener(Runnable callback) {
      modelChangedCallback = callback;
    }

    @Override
    public void onModelChanged() {
      modelChangedCallback.run();
    }
  }

  public static void log(String str) {
    Throwable t = new Throwable();
    StackTraceElement s = t.getStackTrace()[1];
    String line = ".(" + s.getFileName() + ":" + s.getLineNumber() + ") " + s.getMethodName() + "() ";
    System.out.println(line + str);
  }

  public static void log(String str, int n) {
    Throwable t = new Throwable();
    StackTraceElement[] st = t.getStackTrace();
    String line = "";
    for (int i = 1; i < n; i++) {
      StackTraceElement s = st[i];
      line += ".(" + s.getFileName() + ":" + s.getLineNumber() + ") " + s.getMethodName() + "() " + str + "\n";
    }
    System.out.println(line);
  }

  public static void main(String[] arg) {
    JFrame frame = new JFrame("OnSwipe");
    JPanel panel = panelWithUI(null);
    frame.setContentPane(panel);
    frame.setVisible(true);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.pack();
    panel.addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent e) {
        System.out.println(">>>>>" + panel.getWidth() + " , " + panel.getHeight());
      }

      @Override
      public void componentMoved(ComponentEvent e) {

      }

      @Override
      public void componentShown(ComponentEvent e) {

      }

      @Override
      public void componentHidden(ComponentEvent e) {

      }
    });
  }

  private static class SliderSpec{
    public final int sliderMin;
    public final int sliderMax;
    public final int scale;

    public SliderSpec(int min, int max, int decimals) {
      this.scale = (int)Math.pow(10, decimals);
      this.sliderMin = min * scale;
      this.sliderMax = max * scale;
    }

    public SliderSpec(float min, float max, int decimals) {
      this.scale = (int)Math.pow(10, decimals);
      this.sliderMin = (int)(min * scale);
      this.sliderMax = (int)(max * scale);
    }
  }

  @Nullable
  private static SliderSpec getSliderSpec(SpringParameter parameter) {
    switch (parameter) {
      case MAX_ACC:
        return new SliderSpec(0.1f, 100, 2);
      case MAX_VEL:
        return new SliderSpec(0.1f, 10, 2);
      case DURATION:
        return new SliderSpec(100f, 10000f, 1);
      case MASS:
        return new SliderSpec(0, 10, 1);
      case DAMPING:
        return new SliderSpec(0, 100, 3);
      case STIFFNESS:
        return new SliderSpec(40, 2000, 2);
      case THRESHOLD:
        return new SliderSpec(0.0001f, 0.9f, 4);
      case DAMPING_RATIO:
        return new SliderSpec(0.01f, 2, 2);
      case BOUNDARY:
    }
    return null;
  }

  private static boolean isUseSlider(SpringParameter parameter) {
    return !parameter.equals(SpringParameter.BOUNDARY);
  }

  private static SpringMode getMatchingMode(String modeName) {
    for (SpringMode mode: SpringMode.values()) {
      if (mode.displayName.equals(modeName)) {
        return mode;
      }
    }
    return SpringMode.NORMAL;
  }

  @NotNull
  private static SpringParameter[] getAllParameters(SpringWidgetModel model) {
    SpringMode[] supportedModes = model.getSupportedModes();
    return Arrays.stream(supportedModes).flatMap(mode -> mode.parameters.stream()).toArray(SpringParameter[]::new);
  }
}