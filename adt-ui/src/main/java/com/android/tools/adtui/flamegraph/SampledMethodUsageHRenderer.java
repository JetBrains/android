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
package com.android.tools.adtui.flamegraph;

import com.android.tools.adtui.chart.hchart.HRenderer;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.regex.Pattern;

public class SampledMethodUsageHRenderer extends HRenderer<SampledMethodUsage> {

  private static final Color END_COLOR = new JBColor(new Color(0xFF9F00), new Color(0xFF9F00));
  private static final Color START_COLOR = new JBColor(new Color(0xF0CB35), new Color(0xF0CB35));
  private static final Pattern dotPattern = Pattern.compile("\\.");
  private final int mRedDelta;
  private final int mGreenDelta;
  private final int mBlueDelta;

  public SampledMethodUsageHRenderer(){
    super();
    mRedDelta = END_COLOR.getRed() - START_COLOR.getRed();
    mGreenDelta = END_COLOR.getGreen() - START_COLOR.getGreen();
    mBlueDelta = END_COLOR.getBlue() - START_COLOR.getBlue();
  }

  @Override
  protected Color getBordColor(SampledMethodUsage method) {
    return Color.GRAY;
  }

  @Override
  protected Color getFillColor(SampledMethodUsage method) {
    return new Color(
      (int)(START_COLOR.getRed() + method.getPercentage() * mRedDelta),
      (int)(START_COLOR.getGreen() + method.getPercentage() * mGreenDelta),
      (int)(START_COLOR.getBlue() + method.getPercentage() * mBlueDelta));
  }

  /**
   * Find the best text for the given rectangle constraints.
   */
  @Override
  protected String generateFittingText(SampledMethodUsage method, Rectangle2D rect, FontMetrics fontMetrics) {
    if (rect.getWidth() < fontMetrics.stringWidth("...")) {
      return "";
    }

    // Try: java.lang.String.toString
    String fullyQualified = method.getNameSpace() + "." + method.getName();
    if (fontMetrics.stringWidth(fullyQualified) < rect.getWidth()) {
      return fullyQualified;
    }

    // Try: j.l.s.toString
    String abbrevPackage = getShortPackageName(method.getNameSpace()) + "." + method.getName();
    if (fontMetrics.stringWidth(abbrevPackage) < rect.getWidth()) {
      return abbrevPackage;
    }

    // Try: toString
    if (fontMetrics.stringWidth(method.getName()) < rect.getWidth()) {
      return method.getName();
    }

    // TODO
    // Try to show as much as the method name as we can + "..."
    // Try toSr...

    return "";
  }

  @Override
  protected void renderText(Graphics2D g, String text, Rectangle2D.Float rect, FontMetrics fontMetrics) {
    float textPositionY = (float)(rect.getY() + fontMetrics.getAscent());
    g.drawString(text, rect.x, textPositionY);
  }

  private String getShortPackageName(String nameSpace) {
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
