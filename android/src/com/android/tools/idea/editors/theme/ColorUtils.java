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

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.utils.Pair;
import com.google.common.collect.*;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;

import java.awt.*;
import java.util.*;
import java.util.List;

@SuppressWarnings("UseJBColor")
public class ColorUtils {
  private static final Logger LOG = Logger.getInstance(ColorUtils.class);
  private static final @NotNull ImmutableSetMultimap<String, String> CONTRAST_MAP;
  private static final @NotNull ImmutableSet<String> BACKGROUND_ATTRIBUTES;

  // Recommended minimum contrast ratio between colors for safe readability
  private static final double THRESHOLD = 4.5;
  private static final String DISABLED_PREFIX = "Disabled";

  static {
    /* Pairs which contrast needs to be checked. Adding more pairs where will automatically add them to the CONTRAST_MAP */
    Pair[] contrastPairs = new Pair[] {
      Pair.of("textColor", "colorBackground"),
      Pair.of("textColor", "colorPrimary"),
      Pair.of("textColorPrimary", "colorButtonNormal"),
      Pair.of("textColorPrimary", "colorPrimary"),
      Pair.of("textColorPrimary", "colorBackground")
    };

    ImmutableSetMultimap.Builder<String, String> contrastMapBuilder = ImmutableSetMultimap.builder();
    ImmutableSet.Builder<String> backgroundAttributesBuilder = ImmutableSet.builder();
    //noinspection unchecked
    for (Pair<String, String> pair : contrastPairs) {
      contrastMapBuilder.put(pair.getFirst(), pair.getSecond());
      contrastMapBuilder.put(pair.getSecond(), pair.getFirst());

      backgroundAttributesBuilder.add(pair.getSecond());
    }
    CONTRAST_MAP = contrastMapBuilder.build();
    BACKGROUND_ATTRIBUTES = backgroundAttributesBuilder.build();
  }

  /**
   * @param styleAttributeName the name of a style attribute we want to check for contrast issues
   * Returns the set of {@link ItemResourceValue} that have to be checked in the current theme for contrast against a particular attribute.
   */
  @NotNull
  public static ImmutableSet<ItemResourceValue> getContrastItems(@NotNull ThemeEditorContext context, @NotNull String styleAttributeName) {
    Set<String> contrastColorSet = CONTRAST_MAP.get(styleAttributeName);
    if (contrastColorSet == null) {
      return ImmutableSet.of();
    }

    ImmutableSet.Builder<ItemResourceValue> contrastItemsBuilder = ImmutableSet.builder();
    ConfiguredThemeEditorStyle currentTheme = context.getCurrentTheme();
    assert currentTheme != null;

    for (String contrastColor : contrastColorSet) {
      ItemResourceValue contrastItem = ThemeEditorUtils.resolveItemFromParents(currentTheme, contrastColor, false);
      if (contrastItem == null) {
        contrastItem = ThemeEditorUtils.resolveItemFromParents(currentTheme, contrastColor, true);
      }
      if (contrastItem != null) {
        contrastItemsBuilder.add(contrastItem);
      }
    }
    return contrastItemsBuilder.build();
  }

  /**
   * @param styleAttributeName the name of a style attribute we want to check for contrast issues
   * @return all the colors the attribute needs to be checked against, each associated with the appropriate description
   */
  @NotNull
  public static ImmutableMap<String, Color> getContrastColorsWithDescription(@NotNull ThemeEditorContext context,
                                                                             @NotNull String styleAttributeName) {
    ImmutableMap.Builder<String, Color> contrastColorsBuilder = ImmutableMap.builder();
    ResourceResolver styleResourceResolver = context.getResourceResolver();
    assert styleResourceResolver != null;
    Project project = context.getProject();

    Set<ItemResourceValue> contrastItems = getContrastItems(context, styleAttributeName);
    for (ItemResourceValue contrastItem : contrastItems) {
      ResourceHelper.StateList stateList = ResourceHelper.resolveStateList(styleResourceResolver, contrastItem, project);
      if (stateList != null) {
        List<ResourceHelper.StateListState> disabledStates = stateList.getDisabledStates();
        for (ResourceHelper.StateListState stateListState : stateList.getStates()) {
          Color stateListColor = ResourceHelper
            .resolveColor(styleResourceResolver, styleResourceResolver.findResValue(stateListState.getValue(), false), project);
          if (stateListColor != null) {
            try {
              stateListColor = ResourceHelper.makeColorWithAlpha(styleResourceResolver, stateListColor, stateListState.getAlpha());
            }
            catch (NumberFormatException e) {
              // If the alpha value is not valid, Android uses 1.0, so nothing more needs to be done, we can use stateListColor directly
              LOG.warn(String.format(ResourceHelper.ALPHA_FLOATING_ERROR_FORMAT, stateList.getDirName(), stateList.getFileName()));
            }
            String disabledPrefix = disabledStates.contains(stateListState) ? DISABLED_PREFIX : "";
            contrastColorsBuilder.put(disabledPrefix +
                                      ThemeEditorUtils.generateWordEnumeration(stateListState.getAttributesNames(false)) +
                                      " <b>" +
                                      contrastItem.getName() +
                                      "</b>", stateListColor);
          }
        }
      }
      else {
        Color resolvedColor = ResourceHelper.resolveColor(styleResourceResolver, contrastItem, project);
        if (resolvedColor != null) {
          contrastColorsBuilder.put("<b>" + contrastItem.getName() + "</b>", resolvedColor);
        }
      }
    }
    return contrastColorsBuilder.build();
  }

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
        color = alphaBlending(color, backgroundColor);
        otherColor = alphaBlending(otherColor, color);
      }
      else {
        Color backgroundColor = worstContrastColor(color, otherColor);
        otherColor = alphaBlending(otherColor, backgroundColor);
        color = alphaBlending(color, otherColor);
      }
      if (calculateContrastRatio(color, otherColor) < THRESHOLD) {
        lowContrastColorsBuilder.add(colorDescription);
      }
    }

    Set<String> lowContrastColors = lowContrastColorsBuilder.build();
    if (!lowContrastColors.isEmpty()) {
      // Using html for the tooltip because the color names are bold
      // Formatted color names are concatenated into an error message
      return "<html>Not enough contrast with " + ThemeEditorUtils.generateWordEnumeration(lowContrastColors);
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
    return ResourceHelper.clamp(component, 0, 255);
  }

  /**
   * Returns the color that is the result of having a foreground color on top of a background color
   */
  @NotNull
  public static Color alphaBlending(@NotNull Color foreground, @NotNull Color background) {
    float foregroundAlpha = foreground.getAlpha() / 255.0f;
    float backgroundAlpha = background.getAlpha() / 255.0f;
    float a = foregroundAlpha + backgroundAlpha * (1 - foregroundAlpha);
    float r = alphaBlendingComponent(foreground.getRed(), foregroundAlpha, background.getRed(), backgroundAlpha);
    float g = alphaBlendingComponent(foreground.getGreen(), foregroundAlpha, background.getGreen(), backgroundAlpha);
    float b = alphaBlendingComponent(foreground.getBlue(), foregroundAlpha, background.getBlue(), backgroundAlpha);
    return new Color(r, g, b, a);
  }

  /**
   * Computes one RGB component for the blending of two colors
   * @see #alphaBlending(Color, Color)
   */
  private static float alphaBlendingComponent(int foregroundComponent,
                                              float foregroundAlpha,
                                              int backgroundComponent,
                                              float backgroundAlpha) {
    float alpha = foregroundAlpha + backgroundAlpha * (1 - foregroundAlpha);
    if (Math.abs(alpha) < 0.001) {
      return 1;
    }
    return (foregroundAlpha * foregroundComponent + backgroundAlpha * backgroundComponent * (1 - foregroundAlpha)) / (255.0f * alpha);
  }

  /**
   * Indicates whether the attribute represents a background color
   * The attribute needs to be in the contrast map
   */
  public static boolean isBackgroundAttribute(String text) {
    return BACKGROUND_ATTRIBUTES.contains(text);
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
