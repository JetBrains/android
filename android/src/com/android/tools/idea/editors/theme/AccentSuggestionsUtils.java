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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.common.collect.ImmutableMultimap;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Class that provides suggested material accent colors for material primary colors based on the material specification and
 * <a href="https://en.wikipedia.org/wiki/Color_scheme">color theory</a>
 */
public class AccentSuggestionsUtils {
  private static final ImmutableMultimap<Color, Color> PRIMARY_ACCENT_MAP =
    ImmutableMultimap.<Color, Color>builder()
      .put(MaterialColors.RED_500, MaterialColors.RED_ACCENT_100)
      .put(MaterialColors.RED_500, MaterialColors.RED_ACCENT_200)
      .put(MaterialColors.RED_500, MaterialColors.RED_ACCENT_400)
      .put(MaterialColors.RED_500, MaterialColors.RED_ACCENT_700)
      .put(MaterialColors.PINK_500, MaterialColors.PINK_ACCENT_100)
      .put(MaterialColors.PINK_500, MaterialColors.PINK_ACCENT_200)
      .put(MaterialColors.PINK_500, MaterialColors.PINK_ACCENT_400)
      .put(MaterialColors.PINK_500, MaterialColors.PINK_ACCENT_700)
      .put(MaterialColors.PURPLE_500, MaterialColors.PURPLE_ACCENT_100)
      .put(MaterialColors.PURPLE_500, MaterialColors.PURPLE_ACCENT_200)
      .put(MaterialColors.PURPLE_500, MaterialColors.PURPLE_ACCENT_400)
      .put(MaterialColors.PURPLE_500, MaterialColors.PURPLE_ACCENT_700)
      .put(MaterialColors.INDIGO_500, MaterialColors.INDIGO_ACCENT_100)
      .put(MaterialColors.INDIGO_500, MaterialColors.INDIGO_ACCENT_200)
      .put(MaterialColors.INDIGO_500, MaterialColors.INDIGO_ACCENT_400)
      .put(MaterialColors.INDIGO_500, MaterialColors.INDIGO_ACCENT_700)
      .put(MaterialColors.DEEP_PURPLE_500, MaterialColors.DEEP_PURPLE_ACCENT_100)
      .put(MaterialColors.DEEP_PURPLE_500, MaterialColors.DEEP_PURPLE_ACCENT_200)
      .put(MaterialColors.DEEP_PURPLE_500, MaterialColors.DEEP_PURPLE_ACCENT_400)
      .put(MaterialColors.DEEP_PURPLE_500, MaterialColors.DEEP_PURPLE_ACCENT_700)
      .put(MaterialColors.BLUE_500, MaterialColors.BLUE_ACCENT_100)
      .put(MaterialColors.BLUE_500, MaterialColors.BLUE_ACCENT_200)
      .put(MaterialColors.BLUE_500, MaterialColors.BLUE_ACCENT_400)
      .put(MaterialColors.BLUE_500, MaterialColors.BLUE_ACCENT_700)
      .put(MaterialColors.LIGHT_BLUE_500, MaterialColors.LIGHT_BLUE_ACCENT_100)
      .put(MaterialColors.LIGHT_BLUE_500, MaterialColors.LIGHT_BLUE_ACCENT_200)
      .put(MaterialColors.LIGHT_BLUE_500, MaterialColors.LIGHT_BLUE_ACCENT_400)
      .put(MaterialColors.LIGHT_BLUE_500, MaterialColors.LIGHT_BLUE_ACCENT_700)
      .put(MaterialColors.CYAN_500, MaterialColors.CYAN_ACCENT_100)
      .put(MaterialColors.CYAN_500, MaterialColors.CYAN_ACCENT_200)
      .put(MaterialColors.CYAN_500, MaterialColors.CYAN_ACCENT_400)
      .put(MaterialColors.CYAN_500, MaterialColors.CYAN_ACCENT_700)
      .put(MaterialColors.TEAL_500, MaterialColors.TEAL_ACCENT_100)
      .put(MaterialColors.TEAL_500, MaterialColors.TEAL_ACCENT_200)
      .put(MaterialColors.TEAL_500, MaterialColors.TEAL_ACCENT_400)
      .put(MaterialColors.TEAL_500, MaterialColors.TEAL_ACCENT_700)
      .put(MaterialColors.GREEN_500, MaterialColors.GREEN_ACCENT_100)
      .put(MaterialColors.GREEN_500, MaterialColors.GREEN_ACCENT_200)
      .put(MaterialColors.GREEN_500, MaterialColors.GREEN_ACCENT_400)
      .put(MaterialColors.GREEN_500, MaterialColors.GREEN_ACCENT_700)
      .put(MaterialColors.LIGHT_GREEN_500, MaterialColors.LIGHT_GREEN_ACCENT_100)
      .put(MaterialColors.LIGHT_GREEN_500, MaterialColors.LIGHT_GREEN_ACCENT_200)
      .put(MaterialColors.LIGHT_GREEN_500, MaterialColors.LIGHT_GREEN_ACCENT_400)
      .put(MaterialColors.LIGHT_GREEN_500, MaterialColors.LIGHT_GREEN_ACCENT_700)
      .put(MaterialColors.LIME_500, MaterialColors.LIME_ACCENT_100)
      .put(MaterialColors.LIME_500, MaterialColors.LIME_ACCENT_200)
      .put(MaterialColors.LIME_500, MaterialColors.LIME_ACCENT_400)
      .put(MaterialColors.LIME_500, MaterialColors.LIME_ACCENT_700)
      .put(MaterialColors.YELLOW_500, MaterialColors.YELLOW_ACCENT_100)
      .put(MaterialColors.YELLOW_500, MaterialColors.YELLOW_ACCENT_200)
      .put(MaterialColors.YELLOW_500, MaterialColors.YELLOW_ACCENT_400)
      .put(MaterialColors.YELLOW_500, MaterialColors.YELLOW_ACCENT_700)
      .put(MaterialColors.AMBER_500, MaterialColors.AMBER_ACCENT_100)
      .put(MaterialColors.AMBER_500, MaterialColors.AMBER_ACCENT_200)
      .put(MaterialColors.AMBER_500, MaterialColors.AMBER_ACCENT_400)
      .put(MaterialColors.AMBER_500, MaterialColors.AMBER_ACCENT_700)
      .put(MaterialColors.ORANGE_500, MaterialColors.ORANGE_ACCENT_100)
      .put(MaterialColors.ORANGE_500, MaterialColors.ORANGE_ACCENT_200)
      .put(MaterialColors.ORANGE_500, MaterialColors.ORANGE_ACCENT_400)
      .put(MaterialColors.ORANGE_500, MaterialColors.ORANGE_ACCENT_700)
      .put(MaterialColors.DEEP_ORANGE_500, MaterialColors.DEEP_ORANGE_ACCENT_100)
      .put(MaterialColors.DEEP_ORANGE_500, MaterialColors.DEEP_ORANGE_ACCENT_200)
      .put(MaterialColors.DEEP_ORANGE_500, MaterialColors.DEEP_ORANGE_ACCENT_400)
      .put(MaterialColors.DEEP_ORANGE_500, MaterialColors.DEEP_ORANGE_ACCENT_700)
      .build();

  private static final ImmutableMap<Color, Integer> POSITION_MAP;
  private static final ImmutableList<Color> PRIMARY_COLOR_LIST;

  static {
    int i = 0;
    ImmutableMap.Builder<Color, Integer> mapBuilder = ImmutableMap.builder();
    ImmutableList.Builder<Color> listBuilder = ImmutableList.builder();
    for (Color primaryColor : PRIMARY_ACCENT_MAP.keySet()) {
      mapBuilder.put(primaryColor, i++);
      listBuilder.add(primaryColor);
    }
    POSITION_MAP = mapBuilder.build();
    PRIMARY_COLOR_LIST = listBuilder.build();
  }

  public static boolean isMaterialPrimary(@NotNull Color primaryColor) {
    return POSITION_MAP.containsKey(primaryColor);
  }

  /**
   * Returns the accents corresponding to the two closest material colors in the color wheel
   */
  @NotNull
  public static List<Color> getAssociatedAccents(@NotNull Color primaryColor) {
    Integer position = POSITION_MAP.get(primaryColor);
    if (position != null) {
      int totalColors = PRIMARY_COLOR_LIST.size();
      return ImmutableList.<Color>builder()
        .addAll(PRIMARY_ACCENT_MAP.get(PRIMARY_COLOR_LIST.get((position + 1) % totalColors)))
        .addAll(PRIMARY_ACCENT_MAP.get(PRIMARY_COLOR_LIST.get((position - 1 + totalColors) % totalColors)))
        .build();
    }
    return Collections.emptyList();
  }

  /**
   * Returns the material accents corresponding to the primaryColor
   */
  @NotNull
  public static List<Color> getMonochromaticAccents(@NotNull Color primaryColor) {
    Integer position = POSITION_MAP.get(primaryColor);
    if (position != null) {
      return PRIMARY_ACCENT_MAP.get(PRIMARY_COLOR_LIST.get(position)).asList();
    }
    return Collections.emptyList();
  }


  /**
   * Returns the accents corresponding to the opposite color in the color wheel
   */
  @NotNull
  public static List<Color> getComplementaryAccents(@NotNull Color primaryColor) {
    Integer position = POSITION_MAP.get(primaryColor);
    if (position != null) {
      int totalColors = PRIMARY_COLOR_LIST.size();
      return PRIMARY_ACCENT_MAP.get(PRIMARY_COLOR_LIST.get((position + totalColors / 2) % totalColors)).asList();
    }
    return Collections.emptyList();
  }


  /**
   * Returns the accents corresponding to the <a href="https://en.wikipedia.org/wiki/Color_scheme#Triadic_colors">triad</a>
   * formed by the primaryColor. This is the two other colors that form a equilateral triangle with the primaryColor in the color wheel
   */
  @NotNull
  public static List<Color> getTriadAccents(@NotNull Color primaryColor) {
    Integer position = POSITION_MAP.get(primaryColor);
    if (position != null) {
      int totalColors = PRIMARY_COLOR_LIST.size();
      return ImmutableList.<Color>builder()
        .addAll(PRIMARY_ACCENT_MAP.get(PRIMARY_COLOR_LIST.get((position + totalColors / 2 + 1) % totalColors)))
        .addAll(PRIMARY_ACCENT_MAP.get(PRIMARY_COLOR_LIST.get((position + totalColors / 2 - 1) % totalColors)))
        .build();
    }
    return Collections.emptyList();
  }
}