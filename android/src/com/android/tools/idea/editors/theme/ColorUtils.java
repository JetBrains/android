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
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.Set;

public class ColorUtils {

  /* Pairs which contrast needs to be checked. Adding more pairs where will automatically add them to the CONTRAST_MAP */
  private static final @NotNull ImmutableSet<Pair<String, String>> CONTRAST_PAIRS =
    ImmutableSet.<Pair<String, String>>builder()
      .add(Pair.of("textColor", "colorBackground"))
      .add(Pair.of("textColor", "colorPrimary"))
      .add(Pair.of("textColorPrimary", "colorButtonNormal"))
      .add(Pair.of("textColorPrimary", "colorPrimary"))
      .add(Pair.of("textColorPrimary", "colorBackground"))
      .build();

  public static final @NotNull ImmutableSetMultimap<String, String> CONTRAST_MAP;

  // Recommended minimum contrast ratio between colors for safe readability
  private static final double THRESHOLD = 4.5;

  static {
    ImmutableSetMultimap.Builder<String, String> builder = ImmutableSetMultimap.builder();
    for (Pair<String, String> pair : CONTRAST_PAIRS) {
      builder.put(pair.getFirst(), pair.getSecond());
      builder.put(pair.getSecond(), pair.getFirst());
    }
    CONTRAST_MAP = builder.build();
  }

  /**
   * @return the HTML-formatted names of the resources that have a low contrast ratio with the item resource
   * Names are formatted to have the color name in bold
   */
  @NotNull
  public static Set<String> getLowContrastColors(@NotNull ThemeEditorContext context, @NotNull EditedStyleItem item) {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    ResourceResolver styleResourceResolver = context.getConfiguration().getResourceResolver();
    ThemeEditorStyle currentTheme = context.getCurrentTheme();
    Set<String> contrastColorSet = CONTRAST_MAP.get(item.getName());

    assert styleResourceResolver != null && currentTheme != null;

    if (contrastColorSet == null) {
      return Collections.emptySet();
    }
    Color myItemColor = ResourceHelper.resolveColor(styleResourceResolver, item.getSelectedValue(), context.getProject());

    if (myItemColor == null) {
      // The resolution of the item value into a color has failed
      // e.g. if the value is not a valid color such as #00000
      return Collections.emptySet();
    }

    for (String contrastColor : contrastColorSet) {
      ItemResourceValue contrastItem = ThemeEditorUtils.resolveItemFromParents(currentTheme, contrastColor, false);
      if (contrastItem == null) {
        contrastItem = ThemeEditorUtils.resolveItemFromParents(currentTheme, contrastColor, true);
      }
      if (contrastItem == null) {
        continue;
      }
      ResourceHelper.StateList stateList = ResourceHelper.resolveStateList(styleResourceResolver, contrastItem, context.getProject());
      if (stateList != null) {
        for (ResourceHelper.StateListState stateListState : stateList.getStates()) {
          Color color = ResourceHelper
            .resolveColor(styleResourceResolver, styleResourceResolver.findResValue(stateListState.getValue(), false),
                          context.getProject());
          if (color != null && calculateContrastRatio(myItemColor, color) < THRESHOLD) {
            StringBuilder stateNameBuilder = new StringBuilder();
            if (!stateListState.getAttributes().isEmpty()) {
              for (String attribute : stateListState.getAttributes().keySet()) {
                if (!stateListState.getAttributes().get(attribute)) {
                  stateNameBuilder.append("not ");
                }
                stateNameBuilder.append(attribute.startsWith(ResourceHelper.STATE_NAME_PREFIX)
                                        ? attribute.substring(ResourceHelper.STATE_NAME_PREFIX.length())
                                        : attribute);
                stateNameBuilder.append(" <b>");
                stateNameBuilder.append(contrastItem.getName());
                stateNameBuilder.append("</b>");
              }
            }
            else {
              stateNameBuilder.append("default <b>");
              stateNameBuilder.append(contrastItem.getName());
              stateNameBuilder.append("</b>");
            }
            builder.add(stateNameBuilder.toString());
          }
        }
      }
      else {
        Color color = ResourceHelper.resolveColor(styleResourceResolver, contrastItem, context.getProject());
        if (color != null && calculateContrastRatio(myItemColor, color) < THRESHOLD) {
          builder.add("<b>" + contrastItem.getName() + "</b>");
        }
      }

    }
    return builder.build();
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
