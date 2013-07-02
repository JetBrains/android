/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;

public class Utilities {
  private static final Dimension ARROW_HEAD_SIZE = new Dimension(18, 9);

  public static Point add(Point p1, Point p2) {
    return new Point(p1.x + p2.x, p1.y + p2.y);
  }

  public static Point diff(Point p1, Point p2) {
    return new Point(p1.x - p2.x, p1.y - p2.y);
  }

  public static Point max(Point p1, Point p2) {
    return new Point(Math.max(p1.x, p2.x), Math.max(p1.y, p2.y));
  }

  public static Point scale(Point p, float k) {
    return new Point((int)(k * p.x), (int)(k * p.y));
  }

  public static Point project(Rectangle r, Point p) {
    Point centre = centre(r);
    Point diff = diff(p, centre);
    boolean horizontal = Math.abs((float)diff.y / diff.x) < Math.abs((float)r.height / r.width);
    float scale = horizontal ? (float)r.width / 2 / diff.x : (float)r.height / 2 / diff.y;
    return add(centre, scale(diff, Math.abs(scale)));
  }

  public static Point centre(@NotNull Rectangle r) {
    return new Point(r.x + r.width / 2, r.y + r.height / 2);
  }

  public static Point centre(@NotNull Component c) {
    return centre(c.getBounds());
  }

  /**
   * Translates a Java file name to a XML file name according
   * to Android naming convention.
   *
   * Doesn't append .xml extension
   *
   * @return XML file name associated with Java file name
   */
  public static String getXmlFileNameFromJavaFileName(String javaFileName) {

    if (javaFileName.endsWith(".java")) {
      // cut off ".java"
      javaFileName = javaFileName.substring(0, javaFileName.length() - 5);
    }

    char[] charsJava = javaFileName.toCharArray();
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < charsJava.length; i++) {
      char currentChar = charsJava[i];
      if (Character.isUpperCase(currentChar) && i != 0) {
        stringBuilder.append('_');
      }
      stringBuilder.append(Character.toLowerCase(currentChar));
    }
    return stringBuilder.toString();
  }

  /**
   * Translates a XML file name to a Java file name according
   * to Android naming convention.
   *
   * Doesn't append .java extension
   *
   * @return Java file name associated with XML file name
   */
  public static String getJavaFileNameFromXmlFileName(String xmlFileName) {

    if (xmlFileName.endsWith(".xml")) {
      // cut off ".xm"
      xmlFileName = xmlFileName.substring(0, xmlFileName.length() - 4);
    }

    char[] charsXml = xmlFileName.toCharArray();
    StringBuilder stringBuilder = new StringBuilder();
    // make the first char upper case
    stringBuilder.append(Character.toUpperCase(charsXml[0]));
    // start looking for '_' at the second char
    for (int i = 1; i < charsXml.length; i++) {
      char currentChar = charsXml[i];
      if (currentChar == '_') {
        // skip '_' and add the next char as upper case
        char toAppend = Character.toUpperCase(charsXml[++i]);
        stringBuilder.append(toAppend);
      } else {
        stringBuilder.append(currentChar);
      }
    }
    return stringBuilder.toString();
  }

  public static Point toAWTPoint(com.android.navigation.Point loc) {
    return new Point(loc.x, loc.y);
  }

  public static com.android.navigation.Point toNavPoint(Point loc) {
    return new com.android.navigation.Point(loc.x, loc.y);
  }

  static void drawArrow(Graphics g1, int x1, int y1, int x2, int y2) {
    // x1 and y1 are coordinates of circle or rectangle
    // x2 and y2 are coordinates of circle or rectangle, to this point is directed the arrow
    Graphics2D g = (Graphics2D)g1.create();
    double dx = x2 - x1;
    double dy = y2 - y1;
    double angle = Math.atan2(dy, dx);
    int len = (int)Math.sqrt(dx * dx + dy * dy);
    AffineTransform t = AffineTransform.getTranslateInstance(x1, y1);
    t.concatenate(AffineTransform.getRotateInstance(angle));
    g.transform(t);
    g.drawLine(0, 0, len, 0);
    int basePosition = len - ARROW_HEAD_SIZE.width;
    int height = ARROW_HEAD_SIZE.height;
    g.fillPolygon(new int[]{len, basePosition, basePosition, len}, new int[]{0, -height, height, 0}, 4);
  }
}
