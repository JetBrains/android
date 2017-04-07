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

public class JavaMethodUsageHRenderer extends MethodUsageHRenderer {

  private static final Pattern dotPattern = Pattern.compile("\\.");

  /**
   * Find the best text for the given rectangle constraints.
   */
  @Override
  protected String generateFittingText(MethodUsage method, Rectangle2D rect, FontMetrics fontMetrics) {
    // Try: java.lang.String.toString
    String fullyQualified = method.getNameSpace() + Separators.JAVA_CODE + method.getName();
    if (fontMetrics.stringWidth(fullyQualified) < rect.getWidth()) {
      return fullyQualified;
    }

    // Try: j.l.s.toString
    String abbrevPackage = getShortPackageName(method.getNameSpace()) + Separators.JAVA_CODE + method.getName();
    if (fontMetrics.stringWidth(abbrevPackage) < rect.getWidth()) {
      return abbrevPackage;
    }

    // Try: toString
    if (fontMetrics.stringWidth(method.getName()) < rect.getWidth()) {
      return method.getName();
    }

    return "";
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