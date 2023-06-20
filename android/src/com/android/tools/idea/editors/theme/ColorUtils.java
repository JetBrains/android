/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.android.tools.idea.AndroidTextUtils;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.google.common.collect.ImmutableSet;
import com.intellij.ui.ColorUtil;
import java.awt.Color;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UseJBColor")
public class ColorUtils {
  // Recommended minimum contrast ratio between colors for safe readability
  private static final double THRESHOLD = 4.5;
  private static final String DISABLED_PREFIX = "Disabled";

  /**
   * @param contrastColorsWithDescription all the colors to be tested, and their associated description
   * @param color color to be tested against for contrast issues
   * @param isBackground whether color is a background color or not
   * @return the HTML-formatted contrast warning message or an empty string if there are no contrast conflicts
   */
  @NotNull
  public static String getContrastWarningMessage(@NotNull Map<String, Color> contrastColorsWithDescription,
                                                 @NotNull Color color,
                                                 boolean isBackground) {
    ImmutableSet.Builder<String> lowContrastColorsBuilder = ImmutableSet.builder();
    for (Map.Entry<String, Color> contrastColor : contrastColorsWithDescription.entrySet()) {
      String colorDescription = contrastColor.getKey();
      if (colorDescription.startsWith(DISABLED_PREFIX)) {
        // this color comes from a disabled state list state, we ignore it for contrast comparisons
        continue;
      }
      Color otherColor = contrastColor.getValue();
      if (isBackground) {
        Color backgroundColor = worstContrastColor(otherColor, color);
        color = ColorUtil.alphaBlending(color, backgroundColor);
        otherColor = ColorUtil.alphaBlending(otherColor, color);
      }
      else {
        Color backgroundColor = worstContrastColor(color, otherColor);
        otherColor = ColorUtil.alphaBlending(otherColor, backgroundColor);
        color = ColorUtil.alphaBlending(color, otherColor);
      }
      if (calculateContrastRatio(color, otherColor) < THRESHOLD) {
        lowContrastColorsBuilder.add(colorDescription);
      }
    }

    Set<String> lowContrastColors = lowContrastColorsBuilder.build();
    if (!lowContrastColors.isEmpty()) {
      // Using html for the tooltip because the color names are bold
      // Formatted color names are concatenated into an error message
      return "<html>Not enough contrast with " + AndroidTextUtils.generateCommaSeparatedList(lowContrastColors, "and");
    }

    return "";
  }

  /**
   * Returns the color that, placed underneath the colors background and foreground, would result in the worst contrast
   */
  @NotNull
  public static Color worstContrastColor(@NotNull Color foreground, @NotNull Color background) {
    int backgroundAlpha = background.getAlpha();
    int r = worstContrastComponent(foreground.getRed(), background.getRed(), backgroundAlpha);
    int g = worstContrastComponent(foreground.getGreen(), background.getGreen(), backgroundAlpha);
    int b = worstContrastComponent(foreground.getBlue(), background.getBlue(), backgroundAlpha);
    return new Color(r, g, b);
  }

  private static int worstContrastComponent(int foregroundComponent, int backgroundComponent, int backgroundAlpha) {
    if (backgroundAlpha == 255) {
      // Irrelevant since background is completely opaque in this case
      return 0;
    }
    int component = (255 * foregroundComponent - backgroundAlpha * backgroundComponent) / (255 - backgroundAlpha);
    return IdeResourcesUtil.clamp(component, 0, 255);
  }

  /**
   * Provides the contrast ratio between two colors. For general text, the minimum recommended value is 7
   * For large text, the recommended minimum value is 4.5
   * <a href="http://www.w3.org/TR/WCAG20/#contrast-ratiodef">Source</a>
   */
  public static double calculateContrastRatio(@NotNull Color color1, @NotNull Color color2) {
    double color1Luminance = calculateColorLuminance(color1);
    double color2Luminance = calculateColorLuminance(color2);
    return (Math.max(color1Luminance, color2Luminance) + 0.05) / (Math.min(color2Luminance, color1Luminance) + 0.05);
  }

  private static double calculateColorLuminance(@NotNull Color color) {
    return calculateLuminanceContribution(color.getRed() / 255.0) * 0.2126 +
           calculateLuminanceContribution(color.getGreen() / 255.0) * 0.7152 +
           calculateLuminanceContribution(color.getBlue() / 255.0) * 0.0722;
  }

  private static double calculateLuminanceContribution(double colorValue) {
    if (colorValue <= 0.03928) {
      return colorValue / 12.92;
    }
    return Math.pow(((colorValue + 0.055) / 1.055), 2.4);
  }
}
