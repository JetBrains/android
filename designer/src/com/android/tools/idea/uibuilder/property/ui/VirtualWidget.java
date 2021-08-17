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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Implements a basic 3d line render of a widget
 */
public class VirtualWidget extends JComponent {
  double myRotate = 0;  // radians
  double myRotateX = 0; // radians
  double myRotateY = 0; // radians
  private static double ourFillFactor = 0.666;
  private static double ourThick = 0.1;
  private static double ourHalfWidth = 1;
  private static double ourHalfHeight = 0.8;
  double ourTextPercent = 0.6666666;
  String myString = "View";
  private double[][] myTextPath;
  private double[][] myTransformedTextPath;
  GeneralPath mGeneralPath = new GeneralPath();

  Matrix myMatrix = new Matrix();
  double[][] myCubeVertex = {
      {-ourHalfWidth, -ourHalfHeight, -ourThick},
      {ourHalfWidth, -ourHalfHeight, -ourThick},
      {ourHalfWidth, ourHalfHeight, -ourThick},
      {-ourHalfWidth, ourHalfHeight, -ourThick},
      {-ourHalfWidth, -ourHalfHeight, ourThick},
      {ourHalfWidth, -ourHalfHeight, ourThick},
      {ourHalfWidth, ourHalfHeight, ourThick},
      {-ourHalfWidth, ourHalfHeight, ourThick}
  };
  double[][] myTransformedVertex = {
      {-ourHalfWidth, -ourHalfHeight, -ourThick},
      {ourHalfWidth, -ourHalfHeight, -ourThick},
      {ourHalfWidth, ourHalfHeight, -ourThick},
      {-ourHalfWidth, ourHalfHeight, -ourThick},
      {-ourHalfWidth, -ourHalfHeight, ourThick},
      {ourHalfWidth, -ourHalfHeight, ourThick},
      {ourHalfWidth, ourHalfHeight, ourThick},
      {-ourHalfWidth, ourHalfHeight, ourThick}
  };

  int[][] myCubeEdges = {
      {0, 1}, {1, 2}, {2, 3}, {3, 0},
      {4, 5}, {5, 6}, {6, 7}, {7, 4},
      {0, 4}, {1, 5}, {2, 6}, {3, 7},
  };

  VirtualWidget() {
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        setText(myString);
      }
    });
  }

  public void setText(String text) {
    myString = text;
    myTextPath = getPoints((Graphics2D) getGraphics(), text);
    myTransformedTextPath = new double[myTextPath.length][];
    for (int i = 0; i < myTextPath.length; i++) {
      double[] point = myTextPath[i];
      myTransformedTextPath[i] = new double[point.length];
    }
    update();
  }

  double[][] getPoints(Graphics2D g, String str) {
    double[] point = new double[6];
    Font font = g.getFont();
    if (font == null) {
      return new double[0][0];
    }
    FontRenderContext frc = g.getFontRenderContext();
    Rectangle2D bounds = g.getFontMetrics().getStringBounds(str, g);
    double offx = -bounds.getWidth() / 2;
    double offy = g.getFontMetrics().getDescent();
    double scale = ourTextPercent / (bounds.getWidth() / 2);
    if (frc == null) {
      return new double[0][0];
    }
    GlyphVector gv = font.createGlyphVector(frc, str);
    ArrayList<double[]> points = new ArrayList<>();
    double last_move_x = 0, last_move_y = 0;
    int length = gv.getNumGlyphs();
    for (int i = 0; i < length; i++) {
      Shape outline = gv.getGlyphOutline(i);
      PathIterator iter = outline.getPathIterator(null, 0.0001);
      for (; !iter.isDone(); iter.next()) {
        int type = iter.currentSegment(point);
        if (type == PathIterator.SEG_LINETO) {
          points.add(new double[]{(point[0] + offx) * scale, (point[1] + offy) * scale, -ourThick});
        } else if (type == PathIterator.SEG_MOVETO) {
          points.add(new double[0]);
          points.add(new double[]{(point[0] + offx) * scale, (point[1] + offy) * scale, -ourThick});
          last_move_x = (point[0] + offx) * scale;
          last_move_y = (point[1] + offy) * scale;
        } else if (type == PathIterator.SEG_CLOSE) {
          points.add(new double[0]);
          points.add(new double[]{(point[0] + offx) * scale, (point[1] + offy) * scale, -ourThick});
          points.add(new double[]{last_move_x, last_move_y, -ourThick});
        }
      }
    }
    return points.toArray(new double[0][]);
  }

  static class Matrix {
    double[] m = new double[16];
    double[] scratch = new double[16];

    void unit() {
      for (int i = 0; i < m.length; i++) {
        m[i] = ((i % 4) == (i / 4)) ? 1 : 0;
      }
    }

    void mult(double[] m2) {
      for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
          scratch[i + 4 * j] = 0;
          for (int k = 0; k < 4; k++) {
            scratch[i + 4 * j] += m[i + 4 * k] * m2[k + 4 * j];
          }
        }
      }
      double[] swap = m;
      m = scratch;
      scratch = swap;
    }

    void addRotZ(double angle) {
      double s = Math.sin(angle);
      double c = Math.cos(angle);
      double[] yaw = {
          c, -s, 0, 0,
          s, c, 0, 0,
          0, 0, 1, 0,
          0, 0, 0, 1,
      };
      mult(yaw);
    }

    void addRotY(double angle) {
      double s = Math.sin(angle);
      double c = Math.cos(angle);
      double[] pitch = {
          c, 0, s, 0,
          0, 1, 0, 0,
          -s, 0, c, 0,
          0, 0, 0, 1,
      };
      mult(pitch);
    }

    void addRotX(double angle) {
      double s = Math.sin(angle);
      double c = Math.cos(angle);
      double[] roll = {
          1, 0, 0, 0,
          0, c, -s, 0,
          0, s, c, 0,
          0, 0, 0, 1,
      };
      mult(roll);
    }

    void setRots(double x, double y, double z) {
      unit();
      addRotZ(z);
      addRotY(y);
      addRotX(x);
    }

    void transform(double[][] src, double[][] dest) {
      for (int k = 0; k < src.length; k++) {
        if (src[k].length == 3) {
          for (int i = 0; i < 3; i++) {
            int col = i * 4;
            double sum = 0;
            for (int j = 0; j < 3; j++) {
              sum += m[col + j] * src[k][j];
            }
            dest[k][i] = sum + m[col + 3];
          }

        }
      }
    }
  }

  private void update() {
    myMatrix.setRots(myRotateX, myRotateY, myRotate);
    myMatrix.transform(myCubeVertex, myTransformedVertex);
    if (myTextPath != null) {
      myMatrix.transform(myTextPath, myTransformedTextPath);
    }
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    int w = getWidth();
    int h = getHeight();
    double ss =  2 * Math.min(w, h)* ourFillFactor;

    Color c = g.getColor();
    g.setColor(getBackground());
    g.fillRect(0, 0, w, h);
    g.setColor(c);
    AffineTransform af = new AffineTransform();
    Graphics2D g2d = (Graphics2D) g.create();
    AffineTransform tx = new AffineTransform();

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
    g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
        RenderingHints.VALUE_FRACTIONALMETRICS_ON);

    for (int i = 0; i < myCubeEdges.length; i++) {
      int[] index = myCubeEdges[i];
      double[] p1 = myTransformedVertex[index[0]];
      double[] p2 = myTransformedVertex[index[1]];
      double s1 = ss / (4 + p1[2]);
      int p1x = w / 2 + (int) (p1[0] * s1);
      int p1y = h / 2 + (int) (p1[1] * s1);

      double s2 = ss / (4 + p2[2]);
      int p2x = w / 2 + (int) (p2[0] * s2);
      int p2y = h / 2 + (int) (p2[1] * s2);
      g2d.drawLine(p1x, p1y, p2x, p2y);
    }

    mGeneralPath.reset();
    boolean moveto = true;
    if (myTransformedTextPath != null) {
      for (int i = 0; i < myTransformedTextPath.length; i++) {
        double[] p = myTransformedTextPath[i];
        if (p.length != 3) {
          moveto = true;
          continue;
        }
        double s2 = ss / (4 + p[2]);
        double p2x = w / 2f + (p[0] * s2);
        double p2y = h / 2f + (p[1] * s2);

        if (moveto) {
          mGeneralPath.moveTo(p2x, p2y);
        }
        else {
          mGeneralPath.lineTo(p2x, p2y);
        }
        moveto = false;
      }
      g2d.draw(mGeneralPath);
    }
  }

  public void setRotate(double value) {
    myRotate = Math.toRadians(value);
    update();
  }

  public void setRotateX(double value) {
    myRotateX = -Math.toRadians(value);
    update();
  }

  public void setRotateY(double value) {
    myRotateY = -Math.toRadians(value);
    update();
  }

  public static void main(String[] args) {
    JFrame f = new JFrame("test Trackball");
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    JPanel base = new JPanel(new BorderLayout());
    JPanel control = new JPanel();
    control.setLayout(new BoxLayout(control, BoxLayout.PAGE_AXIS));
    base.add(control, BorderLayout.SOUTH);
    final VirtualWidget virtualWidget = new VirtualWidget();
    base.add(virtualWidget);
    JSlider rot = new JSlider(-360, 360, 0);
    JSlider rotX = new JSlider(-360, 360, 0);
    JSlider rotY = new JSlider(-360, 360, 0);

    JLabel rotLabel = new JLabel("0");
    JLabel rotYLabel = new JLabel("0");
    JLabel rotXLabel = new JLabel("0");
    rotLabel.setPreferredSize(new Dimension(40, 20));
    rotYLabel.setPreferredSize(new Dimension(40, 20));
    rotXLabel.setPreferredSize(new Dimension(40, 20));

    control.add(new JLabel("Rotation"));

    JPanel controlRotationX = new JPanel();
    controlRotationX.setLayout(new BoxLayout(controlRotationX, BoxLayout.LINE_AXIS));
    controlRotationX.add(new JLabel("   X"));
    controlRotationX.add(rotX);
    controlRotationX.add(rotXLabel);
    control.add(controlRotationX);

    JPanel controlRotationY = new JPanel();
    controlRotationY.setLayout(new BoxLayout(controlRotationY, BoxLayout.LINE_AXIS));
    controlRotationY.add(new JLabel("   Y"));
    controlRotationY.add(rotY);
    controlRotationY.add(rotYLabel);
    control.add(controlRotationY);

    JPanel controlRotationZ = new JPanel();
    controlRotationZ.setLayout(new BoxLayout(controlRotationZ, BoxLayout.LINE_AXIS));
    controlRotationZ.add(new JLabel("   Z"));
    controlRotationZ.add(rot);
    controlRotationZ.add(rotLabel);
    control.add(controlRotationZ);

    virtualWidget.setBackground(Color.BLACK);
    virtualWidget.setForeground(Color.GREEN);

    rot.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        rotLabel.setText(rot.getValue()+"");
        virtualWidget.setRotate(rot.getValue());
      }
    });
    rotX.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        rotXLabel.setText(rotX.getValue()+"");
        virtualWidget.setRotateX(rotX.getValue());
      }
    });
    rotY.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        rotYLabel.setText(rotY.getValue()+"");
        virtualWidget.setRotateY(rotY.getValue());
      }
    });
    f.setContentPane(base);
    f.setBounds(100, 100, 280, 400);
    f.setVisible(true);
  }

}