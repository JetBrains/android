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
package com.android.tools.adtui.common;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Predicate;

import static com.intellij.util.ui.SwingHelper.ELLIPSIS;

/**
 * ADT-UI utility class to hold constants and function used across the ADT-UI framework.
 */
public final class AdtUiUtils {

  /**
   * Default font to be used in the profiler UI.
   */
  public static final JBFont DEFAULT_FONT = JBFont.create(new Font(null, Font.PLAIN, 10));

  /**
   * Default font color of charts, and component labels.
   */
  public static final Color DEFAULT_FONT_COLOR = JBColor.foreground();

  public static final Color DEFAULT_BORDER_COLOR = new JBColor(Gray._201, Gray._40);
  public static final Border DEFAULT_TOP_BORDER = BorderFactory.createMatteBorder(1, 0, 0, 0, DEFAULT_BORDER_COLOR);
  public static final Border DEFAULT_LEFT_BORDER = BorderFactory.createMatteBorder(0, 1, 0, 0, DEFAULT_BORDER_COLOR);
  public static final Border DEFAULT_BOTTOM_BORDER = BorderFactory.createMatteBorder(0, 0, 1, 0, DEFAULT_BORDER_COLOR);
  public static final Border DEFAULT_RIGHT_BORDER = BorderFactory.createMatteBorder(0, 0, 0, 1, DEFAULT_BORDER_COLOR);
  public static final Border DEFAULT_HORIZONTAL_BORDERS = BorderFactory.createMatteBorder(1, 0, 1, 0, DEFAULT_BORDER_COLOR);
  public static final Border DEFAULT_VERTICAL_BORDERS = BorderFactory.createMatteBorder(0, 1, 0, 1, DEFAULT_BORDER_COLOR);

  public static final GridBagConstraints GBC_FULL =
    new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.BASELINE, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);

  private AdtUiUtils() {
  }

  /**
   * Collapses a line of text to fit the availableSpace by truncating the string and pad the end with ellipsis.
   *
   * @param text              the original text.
   * @param metrics           the {@link FontMetrics} used to measure the text's width.
   * @param availableSpace    the available space to render the text.
   * @return the fitted text. If the available space is too small to fit an ellipsys, an empty string is returned.
   */
  public static String shrinkToFit(String text, FontMetrics metrics, float availableSpace) {
    return shrinkToFit(text, s -> metrics.stringWidth(s) <= availableSpace);
  }

  /**
   * Similar to {@link #shrinkToFit(String, FontMetrics, float)},
   * but takes a predicate method to determine whether the text should fit or not.
   */
  public static String shrinkToFit(String text, Predicate<String> textFitPredicate) {
    if (textFitPredicate.test(text)) {
      // Enough space - early return.
      return text;
    }
    else if (!textFitPredicate.test(ELLIPSIS)) {
      // No space to fit "..." - early return.
      return "";
    }

    int smallestLength = 0;
    int largestLength = text.length();
    int bestLength = smallestLength;
    do {
      int midLength = smallestLength + (largestLength - smallestLength) / 2;
      if (textFitPredicate.test(text.substring(0, midLength) + ELLIPSIS)) {
        bestLength = midLength;
        smallestLength = midLength + 1;
      }
      else {
        largestLength = midLength - 1;
      }
    } while (smallestLength <= largestLength);

    return text.substring(0, bestLength) + ELLIPSIS;
  }

  /**
   * Does the reverse of {@link JBUI#scale(int) }
   */
  public static int unscale(int i) {
    return Math.round(i / JBUI.scale(1.0f));
  }

  /**
   * Returns the resulting sRGB color (no alpha) by overlaying a foregrond color with a given opacity over a background color.
   *
   * @param backgroundRgb     the sRGB color of the background.
   * @param foregroundRbg     the sRGB color of the foreground.
   * @param foregroundOpacity the opaicty of the foreground, in the range of 0.0 - 1.0
   * @return
   */
  public static Color overlayColor(int backgroundRgb, int foregroundRbg, float foregroundOpacity) {
    Color background = new Color(backgroundRgb);
    Color forground = new Color(foregroundRbg);
    return new Color(
      Math.round(background.getRed() * (1 - foregroundOpacity) + forground.getRed() * foregroundOpacity),
      Math.round(background.getGreen() * (1 - foregroundOpacity) + forground.getGreen() * foregroundOpacity),
      Math.round(background.getBlue() * (1 - foregroundOpacity) + forground.getBlue() * foregroundOpacity));
  }
}
