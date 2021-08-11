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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Draw and interact with the easing curve
 */
public class EasingCurve extends GraphBase {

  final double[] param = {0.2, 0.2, 0.8, 0.8};
  private boolean mDown;
  private boolean myOnUpdateNotify = true;
  private static Color ourEasingCurveColor = StudioColorsKt.getGraphLines();
  private static Color ourEasingControlColor = StudioColorsKt.getSelectionBackground();

  public void setOnUpdateNotify(boolean notify) {
    myOnUpdateNotify = notify;
  }

  public EasingCurve() {
    addMouseMotionListener(new MouseMotionAdapter() {
      Point2D click = new Point2D.Float();

      @Override
      public void mouseDragged(MouseEvent e) {
        screenToGraph(e.getPoint(), click);
        if (mDown) {
          param[2] = Math.min(1, Math.max(0, click.getX()));
          param[3] = Math.min(1, Math.max(0, click.getY()));
        }
        else {
          param[0] = Math.min(1, Math.max(0, click.getX()));
          param[1] = Math.min(1, Math.max(0, click.getY()));
        }
        setUp(param);
        repaint();
      }
    });
    addMouseListener(new MouseAdapter() {
      Point2D click = new Point2D.Float();

      @Override
      public void mousePressed(MouseEvent e) {
        screenToGraph(e.getPoint(), click);
        if (click.distance(param[0], param[1]) > click.distance(param[2], param[3])) {
          mDown = true;
        }
        else {
          mDown = false;
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        notifyListeners();
      }
    });
    setUp(param);
  }

  public void setControlPoints(double x1, double y1, double x2, double y2) {
    param[0] = x1;
    param[1] = y1;
    param[2] = x2;
    param[3] = y2;
    if (myOnUpdateNotify) {
      notifyListeners();
    }
    setUp(param);
    repaint();
  }

  public int getComboIndex() {
    // None
    if ((param[0] == 0.2)
        && (param[1] == 0.2)
        && (param[2] == 0.8)
        && (param[3] == 0.8)) {
      return 0;
    }
    // Standard
    if ((param[0] == 0.4)
        && (param[1] == 0.0)
        && (param[2] == 0.2)
        && (param[3] == 1)) {
      return 1;
    }
    // Acceleration
    if ((param[0] == 0.4)
        && (param[1] == 0.0)
        && (param[2] == 1)
        && (param[3] == 1)) {
      return 2;
    }
    // Deceleration
    if ((param[0] == 0.0)
        && (param[1] == 0.0)
        && (param[2] == 0.2)
        && (param[3] == 1)) {
      return 3;
    }
    // Sharp
    if ((param[0] == 0.4)
        && (param[1] == 0.0)
        && (param[2] == 0.6)
        && (param[3] == 1)) {
      return 4;
    }
    // Custom
    return 5;
  }

  public boolean isNone() {
    return (param[0] == 0.2)
           && (param[1] == 0.2)
           && (param[2] == 0.8)
           && (param[3] == 0.8);
  }

  public void setControlPoints(String curve) {
    if (curve == null) {
      setControlPoints(0.2, 0.2, 0.8, 0.8);
    }
    else {
      int start = curve.indexOf('(');
      int off1 = curve.indexOf(',', start);
      double x1 = Double.parseDouble(curve.substring(start + 1, off1).trim());
      int off2 = curve.indexOf(',', off1 + 1);
      double y1 = Double.parseDouble(curve.substring(off1 + 1, off2).trim());
      int off3 = curve.indexOf(',', off2 + 1);
      double x2 = Double.parseDouble(curve.substring(off2 + 1, off3).trim());
      int end = curve.indexOf(')', off3 + 1);
      double y2 = Double.parseDouble(curve.substring(off3 + 1, end).trim());
      setControlPoints(x1, y1, x2, y2);
    }
  }

  public String getControlPoints() {
    DecimalFormat df = new DecimalFormat("##.##");
    return "cubic(" + df.format(param[0]) + "," + df.format(param[1]) + "," + df.format(param[2]) + "," + df.format(param[3]) + ")";
  }

  static class CubicInterpolator {

    private static double error = 0.01;
    double x1, y1, x2, y2;

    public CubicInterpolator(double x1, double y1, double x2, double y2) {
      this.x1 = x1;
      this.y1 = y1;
      this.x2 = x2;
      this.y2 = y2;
    }

    private double getX(double t) {
      double t1 = 1 - t;
      // no need for because start at 0,0 double f0 = (1 - t) * (1 - t) * (1 - t);
      double f1 = 3 * t1 * t1 * t;
      double f2 = 3 * t1 * t * t;
      double f3 = t * t * t;
      return x1 * f1 + x2 * f2 + f3;
    }

    private double getY(double t) {
      double t1 = 1 - t;
      // no need for because start at 0,0 double f0 = (1 - t) * (1 - t) * (1 - t);
      double f1 = 3 * t1 * t1 * t;
      double f2 = 3 * t1 * t * t;
      double f3 = t * t * t;
      return y1 * f1 + y2 * f2 + f3;
    }

    private double getDiffX(double t) {
      double t1 = 1 - t;
      return 3 * t1 * t1 * x1 + 6 * t1 * t * (x2 - x1) + 3 * t * t * (1 - x2);
    }

    private double getDiffY(double t) {
      double t1 = 1 - t;
      return 3 * t1 * t1 * y1 + 6 * t1 * t * (y2 - y1) + 3 * t * t * (1 - y2);
    }

    /**
     * binary search for the region
     * and linear interpolate the answer
     */
    public double getDiff(double x) {
      if (x <= 0.0) {
        return 0;
      }
      if (x >= 1.0) {
        return 1.0;
      }
      double t = 0.5;
      double range = 0.5;
      while (range > error) {
        double tx = getX(t);
        range *= 0.5;
        if (tx < x) {
          t += range;
        }
        else {
          t -= range;
        }
      }

      double x1 = getX(t - range);
      double x2 = getX(t + range);
      double y1 = getDiffY(t - range);
      double y2 = getDiffY(t + range);

      return (y2 - y1) * (x - x1) / (x2 - x1) + y1;
    }

    /**
     * binary search for the region
     * and linear interpolate the answer
     */
    public double get(double x) {
      if (x <= 0.0) {
        return 0;
      }
      if (x >= 1.0) {
        return 1.0;
      }
      double t = 0.5;
      double range = 0.5;
      while (range > error) {
        double tx = getX(t);
        range *= 0.5;
        if (tx < x) {
          t += range;
        }
        else {
          t -= range;
        }
      }

      double x1 = getX(t - range);
      double x2 = getX(t + range);
      double y1 = getY(t - range);
      double y2 = getY(t + range);

      return (y2 - y1) * (x - x1) / (x2 - x1) + y1;
    }
  }

  ArrayList<ActionListener> listeners = new ArrayList<>();

  public void addActionListener(ActionListener actionListener) {
    listeners.add(actionListener);
  }

  public void removeActionListener(ActionListener actionListener) {
    listeners.remove(actionListener);
  }

  protected void notifyListeners() {
    DecimalFormat df = new DecimalFormat("#.##");
    String cubic =
      "cubic(" + df.format(param[0]) + "," + df.format(param[1]) + "," + df.format(param[2]) + "," + df.format(param[3]) + ")";
    ActionEvent actionEvent = new ActionEvent(this, 0, cubic);
    for (ActionListener listener : listeners) {
      listener.actionPerformed(actionEvent);
    }
  }

  static double[][] cubic(double x1, double y1, double x2, double y2) {
    double[][] ret = new double[100][2];
    for (int i = 0; i < ret.length; i++) {
      double t = i / (double)(ret.length - 1);
      double f0 = (1 - t) * (1 - t) * (1 - t);
      double f1 = 3 * (1 - t) * (1 - t) * t;
      double f2 = 3 * (1 - t) * t * t;
      double f3 = t * t * t;
      double x = x1 * f1 + x2 * f2 + f3;
      double y = y1 * f1 + y2 * f2 + f3;
      ret[i][0] = x;
      ret[i][1] = y;
    }
    return ret;
  }

  public void setUp(double[] param) {

    double[][] points = {{0, 0}, {param[0], param[1]}, {param[2], param[3]}, {1, 1}};
    double[][] curve = cubic(param[0], param[1], param[2], param[3]);
    double[] fitX = new double[curve.length];
    double[] fitY = new double[curve.length];
    for (int i = 0; i < fitY.length; i++) {
      fitX[i] = curve[i][0];
      fitY[i] = curve[i][1];
    }

    addGraph(1, points, ourEasingControlColor, 1);

    CubicInterpolator interpolator = new CubicInterpolator(param[0], param[1], param[2], param[3]);
    double[][] fit2 = new double[100][2];
    long time = System.nanoTime();
    for (int i = 0; i < fit2.length; i++) {
      fit2[i][0] = (i / (double)(fit2.length - 1));
      fit2[i][1] = interpolator.get(fit2[i][0]);
    }
    time = System.nanoTime() - time;
    addGraph(0, fit2, ourEasingCurveColor, 0);
  }

  public static void main(String[] arg) {

    JFrame f = new JFrame("test panel");
    f.setBounds(new Rectangle(200, 200));
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JPanel base = new JPanel(new BorderLayout());
    JPanel ctl = new JPanel();
    base.add(ctl, BorderLayout.SOUTH);
    JLabel button = new JLabel("Save...");
    EasingCurve p = new EasingCurve();
    ctl.add(button);
    base.add(p);
    p.addActionListener((e -> button.setText(e.getActionCommand())));
    f.setContentPane(base);
    f.validate();
    f.setVisible(true);
  }
}