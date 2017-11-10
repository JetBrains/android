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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
   * @param metrics           the {@link FontMetrics} used to measure the text's width.
   * @param text              the original text.
   * @param availableSpace    the available space to render the text.
   * @param characterToShrink the number of characters to trim by on each truncate iteration.
   * @return the fitted text. If the available space is too small to fit an ellipsys, an empty string is returned.
   */
  public static String getFittedString(FontMetrics metrics, String text, float availableSpace, int characterToShrink) {
    int textWidth = metrics.stringWidth(text);
    int ellipsysWidth = metrics.stringWidth(ELLIPSIS);
    if (textWidth <= availableSpace) {
      // Enough space - early return.
      return text;
    }
    else if (availableSpace < ellipsysWidth) {
      // No space to fit "..." - early return.
      return "";
    }

    // This loop test the length of the word we are trying to draw, if it is to big to fit the available space,
    // we add an ellipsis and remove a character. We do this until the word fits in the space available to draw.
    while (textWidth > availableSpace) {
      text = text.substring(0, Math.max(0, text.length() - characterToShrink));
      textWidth = metrics.stringWidth(text) + ellipsysWidth;
    }

    return text + ELLIPSIS;
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

  /**
   * Returns the resulting Pattern that matches those containing the filter string.
   *
   * @param filter      the filter string
   * @param isMatchCase if the Pattern is case sensitive
   * @param isRegex     if the Pattern is a regex match
   * @return the Pattern correspondent to the parameters
   */
  @Nullable
  public static Pattern getFilterPattern(@Nullable String filter, boolean isMatchCase, boolean isRegex) {
    Pattern pattern = null;

    if (filter != null && !filter.isEmpty()) {
      int flags = isMatchCase ? 0 : Pattern.CASE_INSENSITIVE;
      if (isRegex) {
        try {
          pattern = Pattern.compile("^.*" + filter + ".*$", flags);
        }
        catch (PatternSyntaxException e) {
          String error = e.getMessage();
          assert (error != null);
        }
      }
      if (pattern == null) {
        pattern = Pattern.compile("^.*" + Pattern.quote(filter) + ".*$", flags);
      }
    }
    return pattern;
  }
}
