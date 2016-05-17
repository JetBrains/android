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

package com.android.tools.adtui.visual.threadgraph;

import com.android.annotations.NonNull;
import com.android.tools.adtui.chart.hchart.HRenderer;
import com.android.tools.adtui.common.AdtUIUtils;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.regex.Pattern;

public class MethodHRenderer implements HRenderer<Method> {

  Font mFont;

  // TODO Use a colorScheme object to retrieve colors. Hard-coded for now.
  private static final JBColor fillJavaColor = new JBColor(new Color(146, 215, 248), new Color(146, 215, 248));
  private static final JBColor bordJavaColor = new JBColor(new Color(115, 190, 233), new Color(115, 190, 233));

  private static final JBColor fillAppColor = new JBColor(new Color(190, 225, 154), new Color(190, 225, 154));
  private static final JBColor bordAppColor = new JBColor(new Color(159, 208, 110), new Color(159, 208, 110));

  // To limit the number of object allocation we reuse the same Rectangle.
  // RoundRectangle kills performance (from 60fps to 6fps) when many nodes are displayed
  // TODO: Either switch to straight angles or find an other way to implement round corners
  @NonNull
  private Rectangle2D.Float mRect;

  private static final Pattern dotPattern = Pattern.compile("\\.");

  public MethodHRenderer() {
    mRect = new Rectangle2D.Float();
  }

  @Override
  public void setFont(Font font) {
    this.mFont = font;
  }

  @Override
  // This method is not thread-safe. In order to limit object allocation, mRect is being re-used.
  public void render(Graphics2D g, Method method, Rectangle2D drawingArea) {
    mRect.x = (float)drawingArea.getX();
    mRect.y = (float)drawingArea.getY();
    mRect.width = (float)drawingArea.getWidth();
    mRect.height = (float)drawingArea.getHeight();

    Color fillColor;
    Color bordColor;
    if (method.getNameSpace().startsWith("java.") ||
        method.getNameSpace().startsWith("sun.") ||
        method.getNameSpace().startsWith("javax.") ||
        method.getNameSpace().startsWith("apple.") ||
        method.getNameSpace().startsWith("com.apple.")) {
      fillColor = fillJavaColor;
      bordColor = bordJavaColor;
    }
    else {
      fillColor = fillAppColor;
      bordColor = bordAppColor;
    }

    // Draw rectangle background
    g.setPaint(fillColor);
    g.fill(mRect);

    // Draw rectangle outline.
    g.setPaint(bordColor);
    g.draw(mRect);

    // Draw text
    FontMetrics fontMetrics = g.getFontMetrics(mFont);
    String text = generateFittingText(method, drawingArea, fontMetrics);
    int textWidth = fontMetrics.stringWidth(text);
    long middle = (long)drawingArea.getCenterX();
    long textPositionX = middle - textWidth / 2;
    int textPositionY = (int)(drawingArea.getY() + fontMetrics.getAscent());

    Font prevFont = g.getFont();
    g.setFont(mFont);
    g.setPaint(AdtUIUtils.DEFAULT_FONT_COLOR);
    g.drawString(text, textPositionX, textPositionY);
    g.setFont(prevFont);
  }

  /**
   * Find the best text for the given rectangle constraints.
   */
  private String generateFittingText(Method method, Rectangle2D rect, FontMetrics fontMetrics) {

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
