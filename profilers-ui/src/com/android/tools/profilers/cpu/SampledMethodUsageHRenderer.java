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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.chart.hchart.HRenderer;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.openapi.util.text.StringUtil;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.regex.Pattern;

// TODO: cleanup/refactor/document/rename. Eventually delete the one in adt-ui
public class SampledMethodUsageHRenderer extends HRenderer<MethodModel> {

  private static final int LEFT_MARGIN_PX = 3;

  protected boolean isMethodPlatform(MethodModel method) {
    return method.getClassName().startsWith("android.");
  }

  protected boolean isMethodVendor(MethodModel method) {
    return method.getClassName().startsWith("java.") ||
           method.getClassName().startsWith("sun.") ||
           method.getClassName().startsWith("javax.") ||
           method.getClassName().startsWith("apple.") ||
           method.getClassName().startsWith("com.apple.");
  }

  @Override
  protected Color getFillColor(MethodModel m) {
    if (isMethodVendor(m)) {
      return ProfilerColors.CPU_TREECHART_VENDOR;
    }
    else if (isMethodPlatform(m)) {
      return ProfilerColors.CPU_TREECHART_PLATFORM;
    }
    else {
      return ProfilerColors.CPU_TREECHART_APP;
    }
  }

  @Override
  protected Color getBordColor(MethodModel m) {
    if (isMethodVendor(m)) {
      return ProfilerColors.CPU_TREECHART_VENDOR_BORDER;
    }
    else if (isMethodPlatform(m)) {
      return ProfilerColors.CPU_TREECHART_PLATFORM_BORDER;
    }
    else {
      return ProfilerColors.CPU_TREECHART_APP_BORDER;
    }
  }

  private static final Pattern dotPattern = Pattern.compile("\\.");

  /**
   * Find the best text for the given rectangle constraints.
   */
  @Override
  protected String generateFittingText(MethodModel node, Rectangle2D rect, FontMetrics fontMetrics) {
    double maxWidth = rect.getWidth() - LEFT_MARGIN_PX;
    // Try: java.lang.String.toString. Add a "." separator between class name and method name.
    // Native methods (e.g. clock_gettime) don't have a class name and, therefore, we don't add a "." before them.
    String separator = StringUtil.isEmpty(node.getClassName()) ? "" : ".";
    String fullyQualified = node.getClassName() + separator + node.getName();
    if (fontMetrics.stringWidth(fullyQualified) < maxWidth) {
      return fullyQualified;
    }

    // Try: j.l.s.toString
    String shortPackage =  getShortPackageName(node.getClassName());
    separator = StringUtil.isEmpty(shortPackage) ? "" : ".";
    String abbrevPackage = shortPackage + separator + node.getName();
    if (fontMetrics.stringWidth(abbrevPackage) < maxWidth) {
      return abbrevPackage;
    }

    String name = node.getName();
    // Try: toString
    if (fontMetrics.stringWidth(name) < maxWidth) {
      return name;
    }

    // Try: t...
    if (!name.isEmpty() && fontMetrics.stringWidth(name.charAt(0) + "...") < maxWidth) {
      return name.charAt(0) + "...";
    }

    return "";
  }

  @Override
  protected void renderText(Graphics2D g, String text, Rectangle2D.Float rect, FontMetrics fontMetrics) {
    float textPositionX = LEFT_MARGIN_PX + rect.x;
    float textPositionY = (float)(rect.getY() + fontMetrics.getAscent());
    g.drawString(text, textPositionX, textPositionY);
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
