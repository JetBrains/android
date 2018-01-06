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

import com.android.tools.adtui.model.DefaultHNode;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.regex.Pattern;

public class JavaMethodHRenderer extends DefaultHRenderer<Method, DefaultHNode<Method>> {

  protected boolean isMethodPlatform(@NotNull Method method) {
    return method.getNameSpace().startsWith("android.");
  }

  protected boolean isMethodVendor(@NotNull Method method) {
    return method.getNameSpace().startsWith("java.") ||
           method.getNameSpace().startsWith("sun.") ||
           method.getNameSpace().startsWith("javax.") ||
           method.getNameSpace().startsWith("apple.") ||
           method.getNameSpace().startsWith("com.apple.");
  }

  @Override
  protected Color getFillColor(@NotNull Method m) {
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
  protected Color getBorderColor(@NotNull Method m) {
    if (isMethodVendor(m)) {
      return borderVendorColor;
    }
    else if (isMethodPlatform(m)) {
      return borderPlatformColor;
    }
    else {
      return borderAppColor;
    }
  }

  private static final Pattern dotPattern = Pattern.compile("\\.");

  /**
   * Find the best text for the given rectangle constraints.
   */
  @Override
  protected String generateFittingText(@NotNull Method m, @NotNull Rectangle2D rect, @NotNull FontMetrics fontMetrics) {
    // Try: java.lang.String.toString
    String fullyQualified = m.getNameSpace() + Separators.JAVA_CODE + m.getName();
    if (fontMetrics.stringWidth(fullyQualified) < rect.getWidth()) {
      return fullyQualified;
    }

    // Try: j.l.s.toString
    String abbrevPackage = getShortPackageName(m.getNameSpace()) + Separators.JAVA_CODE + m.getName();
    if (fontMetrics.stringWidth(abbrevPackage) < rect.getWidth()) {
      return abbrevPackage;
    }

    // Try: toString
    if (fontMetrics.stringWidth(m.getName()) < rect.getWidth()) {
      return m.getName();
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
