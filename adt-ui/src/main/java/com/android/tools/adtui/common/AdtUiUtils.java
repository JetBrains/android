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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static com.intellij.util.ui.SwingHelper.ELLIPSIS;

/**
 * ADT-UI utility class to hold constants and function used across the ADT-UI framework.
 */
public final class AdtUiUtils {

  /**
   * Default font to be used in the profiler UI.
   */
  private static final float FONT_DEFAULT_SIZE = 10f;
  private static final JBFont ROBOTO_BOLD = load("/fonts/Roboto-Bold.ttf", FONT_DEFAULT_SIZE);
  private static final JBFont ROBOTO_MEDIUM = load("/fonts/Roboto-Medium.ttf", FONT_DEFAULT_SIZE);
  private static final JBFont ROBOTO_REGULAR = load("/fonts/Roboto-Regular.ttf", FONT_DEFAULT_SIZE);
  public static final JBFont FONT_DEFAULT = ROBOTO_REGULAR;
  public static final JBFont FONT_DEFAULT_TITLE = ROBOTO_MEDIUM.deriveFont(11f);
  public static final JBFont FONT_PROFILER_TITLE = ROBOTO_MEDIUM.deriveFont(11f);
  public static final JBFont FONT_TIMELINE = ROBOTO_MEDIUM.deriveFont(5f);
  public static final JBFont FONT_SECTION_TITLE = ROBOTO_MEDIUM.deriveFont(13f);
  public static final JBFont FONT_NULL_STAGE_TITLE = ROBOTO_MEDIUM.deriveFont(21.0f);
  public static final JBFont FONT_NULL_STAGE_MESSAGE = ROBOTO_REGULAR.deriveFont(13.0f);
  public static final JBFont FONT_ERROR_INLINE_TITLE = ROBOTO_MEDIUM.deriveFont(16.0f);
  public static final JBFont FONT_ERROR_INLINE_MESSAGE = ROBOTO_REGULAR.deriveFont(13.0f);
  public static final JBFont FONT_MONITOR_TITLE = ROBOTO_BOLD.deriveFont(12.0f);


  /**
   * Default font color of charts, and component labels.
   */
  public static final Color DEFAULT_FONT_COLOR = JBColor.foreground();

  public static final Color DEFAULT_BORDER_COLOR = new JBColor(Gray._96, Gray._192);

  public static final Color GRID_COLOR = new JBColor(Gray._192, Gray._96);

  public static final GridBagConstraints GBC_FULL =
    new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.BASELINE, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);

  private AdtUiUtils() {
  }

  private static JBFont load(String fontPath, float size) {
    try (InputStream stream = AdtUiUtils.class.getResource(fontPath).openStream()) {
      Font font = Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(size);
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      ge.registerFont(font);
      return JBFont.create(font);
    } catch (FontFormatException | IOException ex) {
      Logger.getInstance(AdtUiUtils.class).warn("Cannot load font: " + fontPath, ex);
    }

    return JBFont.create(new Font(null, Font.PLAIN, (int)size));
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
   * Creates a static rectangular image with given color, width and height.
   */
  public static Icon buildStaticImage(Color color, int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        image.setRGB(x, y, color.getRGB());
      }
    }
    return new ImageIcon(image);
  }

  /**
   * Does the reverse of {@link JBUI#scale(int) }
   */
  public static int unscale(int i) {
    return Math.round(i / JBUI.scale(1.0f));
  }
}
