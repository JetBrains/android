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

import java.awt.*;

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

  /**
   * Default background color of charts and components.
   */
  public static final Color DEFAULT_BACKGROUND_COLOR = JBColor.background();

  public static final Color DEFAULT_BORDER_COLOR = new JBColor(Gray._96, Gray._192);

  public static final Color GRID_COLOR = new JBColor(Gray._192, Gray._96);

  public static final Color SELECTION_HANDLE = JBColor.GRAY;

  // TODO need Darcula color.
  public static final Color SELECTION_FOREGROUND = new JBColor(new Color(0x88aae2), new Color(0x88aae2));

  // TODO need Darcula color.
  public static final Color SELECTION_BACKGROUND = new JBColor(new Color(0x5588aae2, true), new Color(0x5588aae2, true));

  public static final Color OVERLAY_INFO_BACKGROUND = JBColor.WHITE;

  private AdtUiUtils() {}

  /**
   * Collapses a line of text to fit the availableSpace by truncating the string and pad the end with ellipsis.
   *
   * @param metrics the {@link FontMetrics} used to measure the text's width.
   * @param text the original text.
   * @param availableSpace the available space to render the text.
   * @param characterToShrink the number of characters to trim by on each truncate iteration.
   *
   * @return the fitted text. If the available space is too small to fit an ellipsys, an empty string is returned.
   */
  public static String getFittedString(FontMetrics metrics, String text, float availableSpace, int characterToShrink) {
    int textWidth = metrics.stringWidth(text);
    int ellipsysWidth = metrics.stringWidth(ELLIPSIS);
    if (textWidth <= availableSpace) {
      // Enough space - early return.
      return text;
    } else if (availableSpace < ellipsysWidth) {
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
}
