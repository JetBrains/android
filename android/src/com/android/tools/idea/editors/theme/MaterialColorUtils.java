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

import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.awt.Color;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class MaterialColorUtils {

  private static final ImmutableMap<Color, Color> PRIMARY_DARK_COLORS_MAP = ImmutableMap.<Color, Color>builder()
    .put(MaterialColors.RED_500, MaterialColors.RED_700)
      .put(MaterialColors.PINK_500, MaterialColors.PINK_700)
      .put(MaterialColors.PURPLE_500, MaterialColors.PURPLE_700)
      .put(MaterialColors.DEEP_PURPLE_500, MaterialColors.DEEP_PURPLE_700)
      .put(MaterialColors.INDIGO_500, MaterialColors.INDIGO_700)
      .put(MaterialColors.BLUE_500, MaterialColors.BLUE_700)
      .put(MaterialColors.LIGHT_BLUE_500, MaterialColors.LIGHT_BLUE_700)
      .put(MaterialColors.CYAN_500, MaterialColors.CYAN_700)
      .put(MaterialColors.TEAL_500, MaterialColors.TEAL_700)
      .put(MaterialColors.GREEN_500, MaterialColors.GREEN_700)
      .put(MaterialColors.LIGHT_GREEN_500, MaterialColors.LIGHT_GREEN_700)
      .put(MaterialColors.LIME_500, MaterialColors.LIME_700)
      .put(MaterialColors.YELLOW_500, MaterialColors.YELLOW_700)
      .put(MaterialColors.AMBER_500, MaterialColors.AMBER_700)
      .put(MaterialColors.ORANGE_500, MaterialColors.ORANGE_700)
      .put(MaterialColors.DEEP_ORANGE_500, MaterialColors.DEEP_ORANGE_700)
      .put(MaterialColors.BROWN_500, MaterialColors.BROWN_700)
      .put(MaterialColors.GREY_500, MaterialColors.GREY_700)
      .put(MaterialColors.BLUE_GREY_500, MaterialColors.BLUE_GREY_700)
      .build();

  private static final ImmutableMap<Color, String> MATERIAL_NAMES_MAP = ImmutableMap.<Color, String>builder()
    .put(MaterialColors.RED_500, "Material Red 500")
    .put(MaterialColors.PINK_500, "Material Pink 500")
    .put(MaterialColors.PURPLE_500, "Material Purple 500")
    .put(MaterialColors.DEEP_PURPLE_500, "Material Deep Purple 500")
    .put(MaterialColors.INDIGO_500, "Material Indigo 500")
    .put(MaterialColors.BLUE_500, "Material Blue 500")
    .put(MaterialColors.LIGHT_BLUE_500, "Material Light Blue 500")
    .put(MaterialColors.CYAN_500, "Material Cyan 500")
    .put(MaterialColors.TEAL_500, "Material Teal 500")
    .put(MaterialColors.GREEN_500, "Material Green 500")
    .put(MaterialColors.LIGHT_GREEN_500, "Material Light Green 500")
    .put(MaterialColors.LIME_500, "Material Lime 500")
    .put(MaterialColors.YELLOW_500, "Material Yellow 500")
    .put(MaterialColors.AMBER_500, "Material Amber 500")
    .put(MaterialColors.ORANGE_500, "Material Orange 500")
    .put(MaterialColors.DEEP_ORANGE_500, "Material Deep Orange 500")
    .put(MaterialColors.BROWN_500, "Material Brown 500")
    .put(MaterialColors.GREY_500, "Material Grey 500")
    .put(MaterialColors.BLUE_GREY_500, "Material Blue Grey 500")
    .put(MaterialColors.RED_700, "Material Red 700")
    .put(MaterialColors.PINK_700, "Material Pink 700")
    .put(MaterialColors.PURPLE_700, "Material Purple 700")
    .put(MaterialColors.DEEP_PURPLE_700, "Material Deep Purple 700")
    .put(MaterialColors.INDIGO_700, "Material Indigo 700")
    .put(MaterialColors.BLUE_700, "Material Blue 700")
    .put(MaterialColors.LIGHT_BLUE_700, "Material Light Blue 700")
    .put(MaterialColors.CYAN_700, "Material Cyan 700")
    .put(MaterialColors.TEAL_700, "Material Teal 700")
    .put(MaterialColors.GREEN_700, "Material Green 700")
    .put(MaterialColors.LIGHT_GREEN_700, "Material Light Green 700")
    .put(MaterialColors.LIME_700, "Material Lime 700")
    .put(MaterialColors.YELLOW_700, "Material Yellow 700")
    .put(MaterialColors.AMBER_700, "Material Amber 700")
    .put(MaterialColors.ORANGE_700, "Material Orange 700")
    .put(MaterialColors.DEEP_ORANGE_700, "Material Deep Orange 700")
    .put(MaterialColors.BROWN_700, "Material Brown 700")
    .put(MaterialColors.GREY_700, "Material Grey 700")
    .put(MaterialColors.BLUE_GREY_700, "Material Blue Grey 700")
    .put(MaterialColors.RED_ACCENT_100, "Red accent 100")
    .put(MaterialColors.RED_ACCENT_200, "Red accent 200")
    .put(MaterialColors.RED_ACCENT_400, "Red accent 400")
    .put(MaterialColors.RED_ACCENT_700, "Red accent 700")
    .put(MaterialColors.PINK_ACCENT_100, "Pink accent 100")
    .put(MaterialColors.PINK_ACCENT_200, "Pink accent 200")
    .put(MaterialColors.PINK_ACCENT_400, "Pink accent 400")
    .put(MaterialColors.PINK_ACCENT_700, "Pink accent 700")
    .put(MaterialColors.PURPLE_ACCENT_100, "Purple accent 100")
    .put(MaterialColors.PURPLE_ACCENT_200, "Purple accent 200")
    .put(MaterialColors.PURPLE_ACCENT_400, "Purple accent 400")
    .put(MaterialColors.PURPLE_ACCENT_700, "Purple accent 700")
    .put(MaterialColors.INDIGO_ACCENT_100, "Indigo accent 100")
    .put(MaterialColors.INDIGO_ACCENT_200, "Indigo accent 200")
    .put(MaterialColors.INDIGO_ACCENT_400, "Indigo accent 400")
    .put(MaterialColors.INDIGO_ACCENT_700, "Indigo accent 700")
    .put(MaterialColors.DEEP_PURPLE_ACCENT_100, "Deep purple accent 100")
    .put(MaterialColors.DEEP_PURPLE_ACCENT_200, "Deep purple accent 200")
    .put(MaterialColors.DEEP_PURPLE_ACCENT_400, "Deep purple accent 400")
    .put(MaterialColors.DEEP_PURPLE_ACCENT_700, "Deep purple accent 700")
    .put(MaterialColors.BLUE_ACCENT_100, "Blue accent 100")
    .put(MaterialColors.BLUE_ACCENT_200, "Blue accent 200")
    .put(MaterialColors.BLUE_ACCENT_400, "Blue accent 400")
    .put(MaterialColors.BLUE_ACCENT_700, "Blue accent 700")
    .put(MaterialColors.LIGHT_BLUE_ACCENT_100, "Light blue accent 100")
    .put(MaterialColors.LIGHT_BLUE_ACCENT_200, "Light blue accent 200")
    .put(MaterialColors.LIGHT_BLUE_ACCENT_400, "Light blue accent 400")
    .put(MaterialColors.LIGHT_BLUE_ACCENT_700, "Light blue accent 700")
    .put(MaterialColors.CYAN_ACCENT_100, "Cyan accent 100")
    .put(MaterialColors.CYAN_ACCENT_200, "Cyan accent 200")
    .put(MaterialColors.CYAN_ACCENT_400, "Cyan accent 400")
    .put(MaterialColors.CYAN_ACCENT_700, "Cyan accent 700")
    .put(MaterialColors.TEAL_ACCENT_100, "Teal accent 100")
    .put(MaterialColors.TEAL_ACCENT_200, "Teal accent 200")
    .put(MaterialColors.TEAL_ACCENT_400, "Teal accent 400")
    .put(MaterialColors.TEAL_ACCENT_700, "Teal accent 700")
    .put(MaterialColors.GREEN_ACCENT_100, "Green accent 100")
    .put(MaterialColors.GREEN_ACCENT_200, "Green accent 200")
    .put(MaterialColors.GREEN_ACCENT_400, "Green accent 400")
    .put(MaterialColors.GREEN_ACCENT_700, "Green accent 700")
    .put(MaterialColors.LIGHT_GREEN_ACCENT_100, "Light green accent 100")
    .put(MaterialColors.LIGHT_GREEN_ACCENT_200, "Light green accent 200")
    .put(MaterialColors.LIGHT_GREEN_ACCENT_400, "Light green accent 400")
    .put(MaterialColors.LIGHT_GREEN_ACCENT_700, "Light green accent 700")
    .put(MaterialColors.LIME_ACCENT_100, "Lime accent 100")
    .put(MaterialColors.LIME_ACCENT_200, "Lime accent 200")
    .put(MaterialColors.LIME_ACCENT_400, "Lime accent 400")
    .put(MaterialColors.LIME_ACCENT_700, "Lime accent 700")
    .put(MaterialColors.YELLOW_ACCENT_100, "Yellow accent 100")
    .put(MaterialColors.YELLOW_ACCENT_200, "Yellow accent 200")
    .put(MaterialColors.YELLOW_ACCENT_400, "Yellow accent 400")
    .put(MaterialColors.YELLOW_ACCENT_700, "Yellow accent 700")
    .put(MaterialColors.AMBER_ACCENT_100, "Amber accent 100")
    .put(MaterialColors.AMBER_ACCENT_200, "Amber accent 200")
    .put(MaterialColors.AMBER_ACCENT_400, "Amber accent 400")
    .put(MaterialColors.AMBER_ACCENT_700, "Amber accent 700")
    .put(MaterialColors.ORANGE_ACCENT_100, "Orange accent 100")
    .put(MaterialColors.ORANGE_ACCENT_200, "Orange accent 200")
    .put(MaterialColors.ORANGE_ACCENT_400, "Orange accent 400")
    .put(MaterialColors.ORANGE_ACCENT_700, "Orange accent 700")
    .put(MaterialColors.DEEP_ORANGE_ACCENT_100, "Deep orange accent 100")
    .put(MaterialColors.DEEP_ORANGE_ACCENT_200, "Deep orange accent 200")
    .put(MaterialColors.DEEP_ORANGE_ACCENT_400, "Deep orange accent 400")
    .put(MaterialColors.DEEP_ORANGE_ACCENT_700, "Deep orange accent 700")
    .build();

  private static final ImmutableList<Color> PRIMARY_COLORS_LIST = ImmutableList.copyOf(PRIMARY_DARK_COLORS_MAP.keySet());

  @NotNull
  public static List<Color> suggestPrimaryColors() {
    return PRIMARY_COLORS_LIST;
  }

  /**
   * Method that suggests accent colors for a given primary color based on the material guidelines and classical color theory
   * <a href="http://www.tigercolor.com/color-lab/color-theory/color-harmonies.htm">Complementary, Analogous and Triad combinations</a>
   * <a href="https://en.wikipedia.org/wiki/Monochromatic_color">Monochromatic colors</a>
   * For each hue (complementary, analogous, triad or monochromatic), two colors are suggested as accents:
   * <ul>
   * <li>75% saturation and 100% brightness</li>
   * <li>100% saturation and original brightness of the colour</li>
   * </ul>
   */
  @NotNull
  public static List<Color> suggestAccentColors(@NotNull Color primaryColor) {
    ImmutableList.Builder<Color> builder = ImmutableList.builder();
    if (AccentSuggestionsUtils.isMaterialPrimary(primaryColor)) {
      builder.addAll(AccentSuggestionsUtils.getMonochromaticAccents(primaryColor));
      builder.addAll(AccentSuggestionsUtils.getAssociatedAccents(primaryColor));
      builder.addAll(AccentSuggestionsUtils.getComplementaryAccents(primaryColor));
      builder.addAll(AccentSuggestionsUtils.getTriadAccents(primaryColor));
    }
    else {
      float[] hsv = Color.RGBtoHSB(primaryColor.getRed(), primaryColor.getGreen(), primaryColor.getBlue(), null);

      // If the primaryColor's brightness is too low, we set it to a higher value (0.6) to have a bright accent color
      hsv[2] = Math.max(0.6f, hsv[2]);

      // Monochromatic
      builder.add(Color.getHSBColor(hsv[0], 0.75f, 1));
      builder.add(Color.getHSBColor(hsv[0], 1, hsv[2]));
      // Analogous
      float associatedHue1 = (hsv[0] + 0.125f) % 1;
      builder.add(Color.getHSBColor(associatedHue1, 0.75f, 1));
      builder.add(Color.getHSBColor(associatedHue1, 1, hsv[2]));
      float associatedHue2 = (hsv[0] + 0.875f) % 1;
      builder.add(Color.getHSBColor(associatedHue2, 0.75f, 1));
      builder.add(Color.getHSBColor(associatedHue2, 1, hsv[2]));
      // Complementary
      float complementaryHue = (hsv[0] + 0.5f) % 1;
      builder.add(Color.getHSBColor(complementaryHue, 0.75f, 1));
      builder.add(Color.getHSBColor(complementaryHue, 1, hsv[2]));
      // Triad
      float triadHue1 = (hsv[0] + 0.625f) % 1;
      builder.add(Color.getHSBColor(triadHue1, 0.75f, 1));
      builder.add(Color.getHSBColor(triadHue1, 1, hsv[2]));
      float triadHue2 = (hsv[0] + 0.375f) % 1;
      builder.add(Color.getHSBColor(triadHue2, 0.75f, 1));
      builder.add(Color.getHSBColor(triadHue2, 1, hsv[2]));
    }
    return builder.build();
  }

  @NotNull
  public static List<Color> suggestPrimaryDarkColors(@NotNull Color primaryColor) {
    Color suggestedColor = PRIMARY_DARK_COLORS_MAP.get(primaryColor);
    if (suggestedColor == null) {
      suggestedColor = primaryColor.darker();
    }
    return ImmutableList.of(suggestedColor);
  }

  /**
   * Returns the material name for a given color
   * Returns null if the color is not material
   */
  @Nullable
  public static String getMaterialName(@NotNull Color color) {
    return MATERIAL_NAMES_MAP.get(color);
  }
}