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
package com.android.tools.idea.apk.viewer;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;

class PercentRenderer extends ColoredTreeCellRenderer {
  private static final JBColor BAR_COLOR = new JBColor(new Color(0xfd8d3c), new Color(0xe6550d));
  private final PercentProvider myProvider;

  private Font myFont;
  private int myMaxPercentWidth;

  private double myFraction;

  public interface PercentProvider {
    double getFraction(@NotNull JTree tree, @NotNull Object value, int row);
  }

  public PercentRenderer(@NotNull PercentProvider provider) {
    myProvider = provider;
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    myFraction = myProvider.getFraction(tree, value, row);
    append(getPercent(myFraction), SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  /**
   * Renders a percentage as a text followed by a bar.
   *
   * ColumnTree requires all renderers to be components (and in some cases requires them to specifically be
   * a {@link ColoredTreeCellRenderer}). However, {@link ColoredTreeCellRenderer}s don't support rendering
   * anything other than an icon and some text. To workaround this, we override the {@link #doPaint(Graphics2D)}
   * method and do our own custom rendering.
   *
   * The renderer first draws a percentage (say 66.6%), followed by a bar representation of that percentage.
   * The text rendering needs to be right aligned, so we first compute the max width for the text, and then
   * draw the text such that it is right aligned. Once the text is drawn, the remainder of the space (minus
   * padding) is assumed to represent 100%, and a bar is drawn to take up the appropriate percentage.
   */
  @Override
  protected void doPaint(Graphics2D g) {
    Font font = getFont();
    if (font != myFont) {
      myFont = font;
      myMaxPercentWidth = getFontMetrics(font).stringWidth("888.8%");
    }

    String percent = getPercent(myFraction);
    int stringWidth = getFontMetrics(font).stringWidth(percent);
    doPaintText(g, myMaxPercentWidth - stringWidth, false);

    int xPadding = 5;
    int yPadding = 2;

    int offset = myMaxPercentWidth + xPadding;
    int width = (int)((getWidth() - myMaxPercentWidth - xPadding*2) * myFraction);

    Color color = g.getColor();
    g.setColor(BAR_COLOR);
    g.fillRect(offset, yPadding, width, getHeight() - yPadding*2);
    g.setColor(color);
  }

  private static String getPercent(double fraction) {
    DecimalFormat formatter = new DecimalFormat("###.#");
    return String.format("%1$s%%", formatter.format(fraction * 100));
  }
}
