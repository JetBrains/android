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
package com.android.tools.adtui.chart.hchart;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.regex.Pattern;

public class JavaMethodHRenderer extends HRenderer<Method> {

  protected boolean isMethodPlatform(Method method) {
    return method.getNameSpace().startsWith("android.");
  }

  protected boolean isMethodVendor(Method method) {
    return method.getNameSpace().startsWith("java.") ||
           method.getNameSpace().startsWith("sun.") ||
           method.getNameSpace().startsWith("javax.") ||
           method.getNameSpace().startsWith("apple.") ||
           method.getNameSpace().startsWith("com.apple.");
  }

  @Override
  protected Color getFillColor(Method m) {
    if (isMethodVendor(m)) {
      return fillVendorColor;
    }
    else if (isMethodPlatform(m)) {
      return fillPlatformColor;
    }
    else {
      return fillAppColor;
    }
  }

  @Override
  protected Color getBordColor(Method m) {
    if (isMethodVendor(m)) {
      return bordVendorColor;
    }
    else if (isMethodPlatform(m)) {
      return bordPlatformColor;
    }
    else {
      return bordAppColor;
    }
  }

  private static final Pattern dotPattern = Pattern.compile("\\.");

  /**
   * Find the best text for the given rectangle constraints.
   */
  @Override
  protected String generateFittingText(Method node, Rectangle2D rect, FontMetrics fontMetrics) {
    // Try: java.lang.String.toString
    String fullyQualified = node.getNameSpace() + Separators.JAVA_CODE + node.getName();
    if (fontMetrics.stringWidth(fullyQualified) < rect.getWidth()) {
      return fullyQualified;
    }

    // Try: j.l.s.toString
    String abbrevPackage = getShortPackageName(node.getNameSpace()) + Separators.JAVA_CODE + node.getName();
    if (fontMetrics.stringWidth(abbrevPackage) < rect.getWidth()) {
      return abbrevPackage;
    }

    // Try: toString
    if (fontMetrics.stringWidth(node.getName()) < rect.getWidth()) {
      return node.getName();
    }

    return "";
  }

  @Override
  protected void renderText(Graphics2D g, String text, Rectangle2D.Float rect, FontMetrics fontMetrics) {
    float textPositionY = (float)(rect.getY() + fontMetrics.getAscent());
    g.drawString(text, rect.x, textPositionY);
  }

  protected String getShortPackageName(String nameSpace) {
    if (nameSpace == null || nameSpace.equals("")) {
      return "";
    }
    StringBuilder b = new StringBuilder();
    String[] elements = dotPattern.split(nameSpace);
    String separator = "";
    for (int i = 0; i < elements.length; i++) {
      b.append(separator);
      b.append(elements[i].charAt(0));
      separator = ".";
    }
    return b.toString();
  }
}
