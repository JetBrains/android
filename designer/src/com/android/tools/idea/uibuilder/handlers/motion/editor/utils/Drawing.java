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
package com.android.tools.idea.uibuilder.handlers.motion.editor.utils;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEScenePicker;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import java.awt.BasicStroke;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;

/**
 * Provides utilities used in creating the Overview Drawing
 */
public final class Drawing {

  private static final boolean DEBUG = false;
  
  public static Stroke
    sDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
    BasicStroke.JOIN_BEVEL, 0, new float[]{MEUI.scale(2)}, 0);


  /**
   * This will generate a rounded path for a path described by xPoints and yPoints. This will only
   * work if the path consist of only vertical or horizontal lines. it assumes the path has already
   * been moved to the start point
   *
   * @param path path that will be filled
   */
  public static void drawRound(GeneralPath path, int[] xPoints, int[] yPoints, int length,
                               int archLen) {
    int[] arches = new int[xPoints.length - 1];
    for (int i = 0; i < arches.length; i++) {
      arches[i] = archLen;
    }

    drawRound(path, xPoints, yPoints, length, arches);
  }

  /**
   * This will generate a rounded path for a path described by xPoints and yPoints. This will only
   * work if the path consist of only vertical or horizontal lines. it assumes the path has already
   * been moved to the start point
   */
  public static void drawPick(MEScenePicker pick,
                              Object obj,
                              int[] xPoints,
                              int[] yPoints,
                              int length,
                              int archLen) {
    int[] arches = new int[xPoints.length - 1];
    for (int i = 0; i < arches.length; i++) {
      arches[i] = archLen;
    }

    pickRound(pick, obj, xPoints, yPoints, length, arches);
  }

  /**
   * This will generate a rounded path for a path described by xPoints and yPoints. This will only
   * work if the path consist of only vertical or horizontal lines. it assumes the path has already
   * been moved to the start point arches should contain length - 1 elements The ith element in
   * arches represents the radius of the curve between the ith and ith + 1 points in xPoints and
   * yPoints
   */
  public static void pickRound(MEScenePicker pick,
                               Object obj,
                               int[] xPoints,
                               int[] yPoints,
                               int length,
                               int[] arches) {
    int lastx = xPoints[0];
    int lasty = yPoints[0];
    int p = 1;
    while (p < length - 1) {
      int d0x = xPoints[p] - lastx;
      int d0y = yPoints[p] - lasty;
      int d1x = xPoints[p + 1] - xPoints[p];
      int d1y = yPoints[p + 1] - yPoints[p];
      int len0 = Math.abs(d0x) + Math.abs(d0y);
      int len1 = Math.abs(d1x) + Math.abs(d1y);

      int d0xs = Integer.signum(d0x);
      int d0ys = Integer.signum(d0y);
      int d1xs = Integer.signum(d1x);
      int d1ys = Integer.signum(d1y);
      int useArch = Math.min(len0 - 2, Math.min(len1 / 2 - 2, arches[p - 1]));
      if (useArch < 2) {
        pick.addLine(obj, 4, lastx, lasty, xPoints[p], yPoints[p], 4);
        lastx = xPoints[p];
        lasty = yPoints[p];
      } else {
        pick.addLine(obj, 4, lastx, lasty, xPoints[p] - useArch * d0xs, yPoints[p] - useArch * d0ys,
          4);

        lastx = xPoints[p] + useArch * d1xs;
        lasty = yPoints[p] + useArch * d1ys;

        // map direction to degrees / 90
        int dir0 = (d0xs == 0) ? ((d0ys < 1) ? 0 : 2) : (d0xs > 0) ? 1 : 3;
        int dir1 = (d1xs == 0) ? ((d1ys < 1) ? 0 : 2) : (d1xs > 0) ? 1 : 3;
        int rot = (4 + dir1 - dir0) % 4;
        boolean dir = rot == 1;

        pickArc(pick, obj, xPoints[p] - useArch * d0xs, yPoints[p] - useArch * d0ys, lastx, lasty,
          useArch, useArch, 0, false, dir);
        pick.addLine(obj, 4, lastx, lasty, xPoints[p] - useArch * d0xs, yPoints[p] - useArch * d0ys,
          4);
      }
      p++;
    }
    pick.addLine(obj, 4, lastx, lasty, xPoints[p], yPoints[p], 4);
  }

  private static void pickArc(MEScenePicker pick,
                              Object obj, float x0, float y0, float x1, float y1, float a, float b,
                              float theta, boolean isMoreThanHalf, boolean isPositiveArc) {

    /* Convert rotation angle from degrees to radians */
    double thetaD = theta * Math.PI / 180.0f;
    /* Pre-compute rotation matrix entries */
    double cosTheta = Math.cos(thetaD);
    double sinTheta = Math.sin(thetaD);
    /* Transform (x0, y0) and (x1, y1) into unit space */
    /* using (inverse) rotation, followed by (inverse) scale */
    double x0p = (x0 * cosTheta + y0 * sinTheta) / a;
    double y0p = (-x0 * sinTheta + y0 * cosTheta) / b;
    double x1p = (x1 * cosTheta + y1 * sinTheta) / a;
    double y1p = (-x1 * sinTheta + y1 * cosTheta) / b;

    /* Compute differences and averages */
    double dx = x0p - x1p;
    double dy = y0p - y1p;
    double xm = (x0p + x1p) / 2;
    double ym = (y0p + y1p) / 2;
    /* Solve for intersecting unit circles */
    double dsq = dx * dx + dy * dy;
    if (dsq == 0.0) {

      return; /* Points are coincident */
    }
    double disc = 1.0 / dsq - 1.0 / 4.0;
    if (disc < 0.0) {

      float adjust = (float) (Math.sqrt(dsq) / 1.99999);
      pickArc(pick, obj, x0, y0, x1, y1, a * adjust, b * adjust, theta, isMoreThanHalf,
        isPositiveArc);
      return; /* Points are too far apart */
    }
    double s = Math.sqrt(disc);
    double sdx = s * dx;
    double sdy = s * dy;
    double cx;
    double cy;
    if (isMoreThanHalf == isPositiveArc) {
      cx = xm - sdy;
      cy = ym + sdx;
    } else {
      cx = xm + sdy;
      cy = ym - sdx;
    }

    double eta0 = Math.atan2((y0p - cy), (x0p - cx));
    double eta1 = Math.atan2((y1p - cy), (x1p - cx));
    double sweep = (eta1 - eta0);
    if (isPositiveArc != (sweep >= 0)) {
      if (sweep > 0) {
        sweep -= 2 * Math.PI;
      } else {
        sweep += 2 * Math.PI;
      }
    }

    cx *= a;
    cy *= b;
    double tcx = cx;
    cx = cx * cosTheta - cy * sinTheta;
    cy = tcx * sinTheta + cy * cosTheta;

    pickArcToBezier(pick, obj, cx, cy, a, b, x0, y0, thetaD, eta0, sweep);
  }

  /**
   * Converts an arc to cubic Bezier segments and records them in p.
   *
   * @param pick  The target for the cubic Bezier segments
   * @param cx    The x coordinate center of the ellipse
   * @param cy    The y coordinate center of the ellipse
   * @param a     The radius of the ellipse in the horizontal direction
   * @param b     The radius of the ellipse in the vertical direction
   * @param e1x   E(eta1) x coordinate of the starting point of the arc
   * @param e1y   E(eta2) y coordinate of the starting point of the arc
   * @param theta The angle that the ellipse bounding rectangle makes with the horizontal plane
   * @param start The start angle of the arc on the ellipse
   * @param sweep The angle (positive or negative) of the sweep of the arc on the ellipse
   */
  private static void pickArcToBezier(MEScenePicker pick,
                                      Object obj, double cx, double cy, double a, double b, double e1x,
                                      double e1y, double theta, double start, double sweep) {
    // Taken from equations at:
    // http://spaceroots.org/documents/ellipse/node8.html
    // and http://www.spaceroots.org/documents/ellipse/node22.html

    // Maximum of 45 degrees per cubic Bezier segment
    int numSegments = Math.abs((int) Math.ceil(sweep * 4 / Math.PI));

    double eta1 = start;
    double cosTheta = Math.cos(theta);
    double sinTheta = Math.sin(theta);
    double cosEta1 = Math.cos(eta1);
    double sinEta1 = Math.sin(eta1);
    double ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1);
    double ep1y = (-a * sinTheta * sinEta1) + (b * cosTheta * cosEta1);

    double anglePerSegment = sweep / numSegments;
    for (int i = 0; i < numSegments; i++) {
      double eta2 = eta1 + anglePerSegment;
      double sinEta2 = Math.sin(eta2);
      double cosEta2 = Math.cos(eta2);
      double e2x = cx + (a * cosTheta * cosEta2) - (b * sinTheta * sinEta2);
      double e2y = cy + (a * sinTheta * cosEta2) + (b * cosTheta * sinEta2);
      double ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2;
      double ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2;
      double tanDiff2 = Math.tan((eta2 - eta1) / 2);
      double alpha = Math.sin(eta2 - eta1) * (Math.sqrt(4 + (3 * tanDiff2 * tanDiff2)) - 1) / 3;
      double q1x = e1x + alpha * ep1x;
      double q1y = e1y + alpha * ep1y;
      double q2x = e2x - alpha * ep2x;
      double q2y = e2y - alpha * ep2y;
      pick.addCurveTo(obj, 4, (int) e1x, (int) e1y, (int) q1x, (int) q1y, (int) q2x, (int) q2y,
        (int) e2x, (int) e2y, 4);
      eta1 = eta2;
      e1x = e2x;
      e1y = e2y;
      ep1x = ep2x;
      ep1y = ep2y;
    }
  }

  /**
   * This will generate a rounded path for a path described by xPoints and yPoints. This will only
   * work if the path consist of only vertical or horizontal lines. it assumes the path has already
   * been moved to the start point arches should contain length - 1 elements The ith element in
   * arches represents the radius of the curve between the ith and ith + 1 points in xPoints and
   * yPoints
   *
   * @param path path that will be filled
   */
  public static void drawRound(GeneralPath path, int[] xPoints, int[] yPoints, int length,
                               int[] arches) {
    int lastx = xPoints[0];
    int lasty = yPoints[0];
    int p = 1;
    while (p < length - 1) {
      int d0x = xPoints[p] - lastx;
      int d0y = yPoints[p] - lasty;
      int d1x = xPoints[p + 1] - xPoints[p];
      int d1y = yPoints[p + 1] - yPoints[p];
      int len0 = Math.abs(d0x) + Math.abs(d0y);
      int len1 = Math.abs(d1x) + Math.abs(d1y);

      int d0xs = Integer.signum(d0x);
      int d0ys = Integer.signum(d0y);
      int d1xs = Integer.signum(d1x);
      int d1ys = Integer.signum(d1y);
      int useArch = Math.min(len0 - 2, Math.min(len1 / 2 - 2, arches[p - 1]));
      if (useArch < 2) {
        path.lineTo(xPoints[p], yPoints[p]);
        lastx = xPoints[p];
        lasty = yPoints[p];
      } else {
        path.lineTo(xPoints[p] - useArch * d0xs, yPoints[p] - useArch * d0ys);
        lastx = xPoints[p] + useArch * d1xs;
        lasty = yPoints[p] + useArch * d1ys;

        // map direction to degrees / 90
        int dir0 = (d0xs == 0) ? ((d0ys < 1) ? 0 : 2) : (d0xs > 0) ? 1 : 3;
        int dir1 = (d1xs == 0) ? ((d1ys < 1) ? 0 : 2) : (d1xs > 0) ? 1 : 3;
        int rot = (4 + dir1 - dir0) % 4;
        boolean dir = rot == 1;

        drawArc(path, xPoints[p] - useArch * d0xs, yPoints[p] - useArch * d0ys, lastx, lasty,
          useArch, useArch, 0, false, dir);
        path.lineTo(lastx, lasty);
      }
      p++;
    }
    path.lineTo(xPoints[p], yPoints[p]);
  }

  private static void drawArc(Path2D p, float x0, float y0, float x1, float y1, float a, float b,
                              float theta, boolean isMoreThanHalf, boolean isPositiveArc) {

    /* Convert rotation angle from degrees to radians */
    double thetaD = theta * Math.PI / 180.0f;
    /* Pre-compute rotation matrix entries */
    double cosTheta = Math.cos(thetaD);
    double sinTheta = Math.sin(thetaD);
    /* Transform (x0, y0) and (x1, y1) into unit space */
    /* using (inverse) rotation, followed by (inverse) scale */
    double x0p = (x0 * cosTheta + y0 * sinTheta) / a;
    double y0p = (-x0 * sinTheta + y0 * cosTheta) / b;
    double x1p = (x1 * cosTheta + y1 * sinTheta) / a;
    double y1p = (-x1 * sinTheta + y1 * cosTheta) / b;

    /* Compute differences and averages */
    double dx = x0p - x1p;
    double dy = y0p - y1p;
    double xm = (x0p + x1p) / 2;
    double ym = (y0p + y1p) / 2;
    /* Solve for intersecting unit circles */
    double dsq = dx * dx + dy * dy;
    if (dsq == 0.0) {

      return; /* Points are coincident */
    }
    double disc = 1.0 / dsq - 1.0 / 4.0;
    if (disc < 0.0) {

      float adjust = (float) (Math.sqrt(dsq) / 1.99999);
      drawArc(p, x0, y0, x1, y1, a * adjust, b * adjust, theta, isMoreThanHalf, isPositiveArc);
      return; /* Points are too far apart */
    }
    double s = Math.sqrt(disc);
    double sdx = s * dx;
    double sdy = s * dy;
    double cx;
    double cy;
    if (isMoreThanHalf == isPositiveArc) {
      cx = xm - sdy;
      cy = ym + sdx;
    } else {
      cx = xm + sdy;
      cy = ym - sdx;
    }

    double eta0 = Math.atan2((y0p - cy), (x0p - cx));
    double eta1 = Math.atan2((y1p - cy), (x1p - cx));
    double sweep = (eta1 - eta0);
    if (isPositiveArc != (sweep >= 0)) {
      if (sweep > 0) {
        sweep -= 2 * Math.PI;
      } else {
        sweep += 2 * Math.PI;
      }
    }

    cx *= a;
    cy *= b;
    double tcx = cx;
    cx = cx * cosTheta - cy * sinTheta;
    cy = tcx * sinTheta + cy * cosTheta;

    arcToBezier(p, cx, cy, a, b, x0, y0, thetaD, eta0, sweep);
  }

  /**
   * Converts an arc to cubic Bezier segments and records them in p.
   *
   * @param p     The target for the cubic Bezier segments
   * @param cx    The x coordinate center of the ellipse
   * @param cy    The y coordinate center of the ellipse
   * @param a     The radius of the ellipse in the horizontal direction
   * @param b     The radius of the ellipse in the vertical direction
   * @param e1x   E(eta1) x coordinate of the starting point of the arc
   * @param e1y   E(eta2) y coordinate of the starting point of the arc
   * @param theta The angle that the ellipse bounding rectangle makes with the horizontal plane
   * @param start The start angle of the arc on the ellipse
   * @param sweep The angle (positive or negative) of the sweep of the arc on the ellipse
   */
  private static void arcToBezier(Path2D p, double cx, double cy, double a, double b, double e1x,
                                  double e1y, double theta, double start, double sweep) {
    // Taken from equations at:
    // http://spaceroots.org/documents/ellipse/node8.html
    // and http://www.spaceroots.org/documents/ellipse/node22.html

    // Maximum of 45 degrees per cubic Bezier segment
    int numSegments = Math.abs((int) Math.ceil(sweep * 4 / Math.PI));

    double eta1 = start;
    double cosTheta = Math.cos(theta);
    double sinTheta = Math.sin(theta);
    double cosEta1 = Math.cos(eta1);
    double sinEta1 = Math.sin(eta1);
    double ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1);
    double ep1y = (-a * sinTheta * sinEta1) + (b * cosTheta * cosEta1);

    double anglePerSegment = sweep / numSegments;
    for (int i = 0; i < numSegments; i++) {
      double eta2 = eta1 + anglePerSegment;
      double sinEta2 = Math.sin(eta2);
      double cosEta2 = Math.cos(eta2);
      double e2x = cx + (a * cosTheta * cosEta2) - (b * sinTheta * sinEta2);
      double e2y = cy + (a * sinTheta * cosEta2) + (b * cosTheta * sinEta2);
      double ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2;
      double ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2;
      double tanDiff2 = Math.tan((eta2 - eta1) / 2);
      double alpha = Math.sin(eta2 - eta1) * (Math.sqrt(4 + (3 * tanDiff2 * tanDiff2)) - 1) / 3;
      double q1x = e1x + alpha * ep1x;
      double q1y = e1y + alpha * ep1y;
      double q2x = e2x - alpha * ep2x;
      double q2y = e2y - alpha * ep2y;

      p.curveTo((float) q1x, (float) q1y, (float) q2x, (float) q2y, (float) e2x, (float) e2y);
      eta1 = eta2;
      e1x = e2x;
      e1y = e2y;
      ep1x = ep2x;
      ep1y = ep2y;
    }
  }


}
